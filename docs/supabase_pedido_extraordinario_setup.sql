-- Setup para crear pedidos extraordinarios incluyendo unidad en el detalle.
-- Ejecuta esto en SQL Editor de Supabase.

create or replace function public.crear_pedido_extraordinario(
  p_empleado_id uuid,
  p_empleado_email text,
  p_prioridad text,
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
  v_nombre text;
  v_cantidad int;
  v_unidad text;
  v_prioridad text;
  v_comentario text;
begin
  v_prioridad := upper(trim(coalesce(p_prioridad, '')));
  if v_prioridad not in ('BAJA', 'MEDIA', 'ALTA') then
    raise exception 'Prioridad inválida';
  end if;

  v_comentario := nullif(trim(coalesce(p_comentario, '')), '');
  if v_comentario is null then
    v_comentario := 'PEDIDO EXTRAORDINARIO PRIORIDAD ' || v_prioridad;
  end if;

  insert into public.pedidos_extraordinarios (
    empleado_id,
    empleado_email,
    estado,
    prioridad,
    comentario
  ) values (
    p_empleado_id,
    p_empleado_email,
    'ENVIADO',
    v_prioridad,
    v_comentario
  )
  returning id into v_pedido_id;

  for v_item in select * from json_array_elements(p_items)
  loop
    v_nombre := nullif(trim(v_item->>'nombre'), '');
    v_cantidad := (v_item->>'cantidad')::int;
    v_unidad := nullif(trim(coalesce(v_item->>'unidad', '')), '');

    if v_nombre is null then
      raise exception 'Nombre inválido en item extraordinario';
    end if;

    if v_cantidad is null or v_cantidad <= 0 then
      raise exception 'Cantidad inválida en item extraordinario';
    end if;

    insert into public.pedido_extraordinario_detalle (
      pedido_extraordinario_id,
      nombre,
      cantidad,
      unidad
    ) values (
      v_pedido_id,
      v_nombre,
      v_cantidad,
      v_unidad
    );
  end loop;

  return v_pedido_id;
end;
$$;
