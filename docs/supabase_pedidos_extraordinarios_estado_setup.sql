-- Setup para aceptar y rechazar pedidos extraordinarios desde admin.
-- Ejecuta esto en SQL Editor de Supabase.

create or replace function public.aceptar_pedido_extraordinario(
  p_pedido_id uuid
) returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.pedidos_extraordinarios
  set estado = 'ACEPTADO'
  where id = p_pedido_id
    and estado = 'ENVIADO';

  if not found then
    raise exception 'No se pudo aceptar el pedido extraordinario';
  end if;
end;
$$;

create or replace function public.rechazar_pedido_extraordinario(
  p_pedido_id uuid
) returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.pedidos_extraordinarios
  set estado = 'RECHAZADO'
  where id = p_pedido_id
    and estado = 'ENVIADO';

  if not found then
    raise exception 'No se pudo rechazar el pedido extraordinario';
  end if;
end;
$$;
