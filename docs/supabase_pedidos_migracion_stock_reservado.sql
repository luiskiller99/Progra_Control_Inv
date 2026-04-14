-- Migración integral de pedidos normales (inventario con stock reservado)
-- Ejecutar en Supabase SQL Editor en una sola transacción.

begin;

-- 1) Eliminar funciones obsoletas o conflictivas de flujo normal
--    (aceptar_pedido se reemplaza por aprobar_pedido)
drop function if exists public.aceptar_pedido(uuid);

-- 2) Guardas de integridad para evitar negativos
do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'inventario_cantidad_non_negative'
      and conrelid = 'public.inventario'::regclass
  ) then
    execute 'alter table public.inventario
             add constraint inventario_cantidad_non_negative
             check (cantidad >= 0)';
  end if;

  if not exists (
    select 1
    from pg_constraint
    where conname = 'inventario_reservado_non_negative'
      and conrelid = 'public.inventario'::regclass
  ) then
    execute 'alter table public.inventario
             add constraint inventario_reservado_non_negative
             check (reservado >= 0)';
  end if;
end;
$$;

-- 3) Crear pedido: solo reserva, no descuenta cantidad
create or replace function public.crear_pedido(
  p_empleado_id uuid,
  p_empleado_email text,
  p_items json,
  p_comentario text default null
) returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_pedido_id uuid;
  v_item json;
  v_producto_id uuid;
  v_cantidad int;
  v_cantidad_actual int;
  v_reservado_actual int;
  v_disponible int;
begin
  if p_items is null or json_array_length(p_items) = 0 then
    raise exception 'El pedido no contiene items';
  end if;

  insert into public.pedidos (empleado_id, empleado_email, estado, comentario)
  values (
    p_empleado_id,
    p_empleado_email,
    'ENVIADO',
    nullif(trim(coalesce(p_comentario, '')), '')
  )
  returning id into v_pedido_id;

  for v_item in select * from json_array_elements(p_items)
  loop
    v_producto_id := (v_item->>'producto_id')::uuid;
    v_cantidad := (v_item->>'cantidad')::int;

    if v_producto_id is null then
      raise exception 'Producto inválido';
    end if;

    if v_cantidad is null or v_cantidad <= 0 then
      raise exception 'Cantidad inválida';
    end if;

    -- Lock de fila para evitar sobreventa por concurrencia
    select i.cantidad, i.reservado
      into v_cantidad_actual, v_reservado_actual
    from public.inventario i
    where i.id = v_producto_id
    for update;

    if not found then
      raise exception 'Producto no encontrado';
    end if;

    v_disponible := v_cantidad_actual - v_reservado_actual;

    if v_disponible < v_cantidad then
      raise exception 'Stock insuficiente';
    end if;

    update public.inventario
    set reservado = reservado + v_cantidad
    where id = v_producto_id;

    insert into public.pedido_detalle (pedido_id, producto_id, cantidad)
    values (v_pedido_id, v_producto_id, v_cantidad);
  end loop;

  return v_pedido_id;
exception
  when others then
    -- Si algo falla durante el loop, se revierte toda la transacción automáticamente.
    raise;
end;
$$;

-- 4) Aprobar pedido: descuenta cantidad y libera reservado
create or replace function public.aprobar_pedido(
  p_pedido_id uuid
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_estado text;
  v_item record;
  v_cantidad_actual int;
  v_reservado_actual int;
begin
  select estado
    into v_estado
  from public.pedidos
  where id = p_pedido_id
  for update;

  if not found then
    raise exception 'Pedido no encontrado';
  end if;

  if v_estado <> 'ENVIADO' then
    raise exception 'El pedido no está en estado ENVIADO';
  end if;

  for v_item in
    select producto_id, cantidad
    from public.pedido_detalle
    where pedido_id = p_pedido_id
  loop
    select i.cantidad, i.reservado
      into v_cantidad_actual, v_reservado_actual
    from public.inventario i
    where i.id = v_item.producto_id
    for update;

    if not found then
      raise exception 'Producto no encontrado en inventario';
    end if;

    if v_reservado_actual < v_item.cantidad then
      raise exception 'Reservado insuficiente para aprobar';
    end if;

    if v_cantidad_actual < v_item.cantidad then
      raise exception 'Cantidad insuficiente para aprobar';
    end if;

    update public.inventario
    set
      cantidad = cantidad - v_item.cantidad,
      reservado = reservado - v_item.cantidad
    where id = v_item.producto_id;
  end loop;

  update public.pedidos
  set estado = 'APROBADO'
  where id = p_pedido_id;
end;
$$;

-- 5) Rechazar pedido: solo libera reservado
create or replace function public.rechazar_pedido(
  p_pedido_id uuid
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_estado text;
  v_item record;
  v_reservado_actual int;
begin
  select estado
    into v_estado
  from public.pedidos
  where id = p_pedido_id
  for update;

  if not found then
    raise exception 'Pedido no encontrado';
  end if;

  if v_estado <> 'ENVIADO' then
    raise exception 'El pedido no está en estado ENVIADO';
  end if;

  for v_item in
    select producto_id, cantidad
    from public.pedido_detalle
    where pedido_id = p_pedido_id
  loop
    select i.reservado
      into v_reservado_actual
    from public.inventario i
    where i.id = v_item.producto_id
    for update;

    if not found then
      raise exception 'Producto no encontrado en inventario';
    end if;

    if v_reservado_actual < v_item.cantidad then
      raise exception 'Reservado insuficiente para rechazar';
    end if;

    update public.inventario
    set reservado = reservado - v_item.cantidad
    where id = v_item.producto_id;
  end loop;

  update public.pedidos
  set estado = 'RECHAZADO'
  where id = p_pedido_id;
end;
$$;

commit;
