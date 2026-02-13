-- Setup para soportar comentarios opcionales y stock seguro al crear pedidos.
-- Ejecuta esto en SQL Editor de Supabase.

-- 1) Columna comentario opcional en pedidos
alter table if exists public.pedidos
add column if not exists comentario text;

-- 2) Protección extra para no permitir inventario negativo
--    (si ya existe, no falla por IF NOT EXISTS)
alter table if exists public.inventario
add constraint if not exists inventario_cantidad_non_negative check (cantidad >= 0);

-- 3) Función crear_pedido con comentario opcional y bloqueo de fila de inventario
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
  v_stock int;
  v_producto_id uuid;
  v_cantidad int;
begin
  insert into pedidos (empleado_id, empleado_email, estado, comentario)
  values (p_empleado_id, p_empleado_email, 'ENVIADO', nullif(trim(p_comentario), ''))
  returning id into v_pedido_id;

  for v_item in select * from json_array_elements(p_items)
  loop
    v_producto_id := (v_item->>'producto_id')::uuid;
    v_cantidad := (v_item->>'cantidad')::int;

    -- Lock para evitar carreras entre pedidos concurrentes
    select cantidad into v_stock
    from inventario
    where id = v_producto_id
    for update;

    if v_stock is null then
      raise exception 'Producto no encontrado';
    end if;

    if v_cantidad <= 0 then
      raise exception 'Cantidad inválida';
    end if;

    if v_stock < v_cantidad then
      raise exception 'Stock insuficiente';
    end if;

    update inventario
    set
      cantidad = cantidad - v_cantidad,
      reservado = reservado + v_cantidad
    where id = v_producto_id;

    insert into pedido_detalle (pedido_id, producto_id, cantidad)
    values (v_pedido_id, v_producto_id, v_cantidad);
  end loop;

  return v_pedido_id;
end;
$$;

-- 4) Índice opcional para listados admin
create index if not exists idx_pedidos_fecha_estado
on public.pedidos(fecha desc, estado);
