-- Setup para soportar comentarios en creación de pedidos desde empleado.
-- Ejecuta esto en SQL Editor de Supabase.

-- 1) Agregar columna comentario en pedidos (si no existe)
alter table if exists public.pedidos
add column if not exists comentario text;

-- 2) Actualizar función crear_pedido para recibir comentario opcional
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
begin
  insert into pedidos (empleado_id, empleado_email, estado, comentario)
  values (p_empleado_id, p_empleado_email, 'ENVIADO', nullif(trim(p_comentario), ''))
  returning id into v_pedido_id;

  for v_item in select * from json_array_elements(p_items)
  loop
    select cantidad into v_stock
    from inventario
    where id = (v_item->>'producto_id')::uuid;

    if v_stock is null then
      raise exception 'Producto no encontrado';
    end if;

    if v_stock < (v_item->>'cantidad')::int then
      raise exception 'Stock insuficiente';
    end if;

    update inventario
    set
      cantidad = cantidad - (v_item->>'cantidad')::int,
      reservado = reservado + (v_item->>'cantidad')::int
    where id = (v_item->>'producto_id')::uuid;

    insert into pedido_detalle (pedido_id, producto_id, cantidad)
    values (
      v_pedido_id,
      (v_item->>'producto_id')::uuid,
      (v_item->>'cantidad')::int
    );
  end loop;

  return v_pedido_id;
end;
$$;

-- 3) (Opcional) índice para listar pedidos admin más rápido
create index if not exists idx_pedidos_fecha_estado
on public.pedidos(fecha desc, estado);
