-- Setup para pedidos extraordinarios separados de pedidos normales.
-- Ejecuta este script en SQL Editor de Supabase.

create table if not exists public.pedidos_extraordinarios (
  id uuid primary key default gen_random_uuid(),
  empleado_id uuid not null,
  empleado_email text not null,
  estado text not null default 'ENVIADO',
  prioridad text not null check (prioridad in ('BAJA', 'MEDIA', 'ALTA')),
  comentario text not null,
  fecha timestamptz not null default now()
);

create table if not exists public.pedido_extraordinario_detalle (
  id bigint generated always as identity primary key,
  pedido_extraordinario_id uuid not null references public.pedidos_extraordinarios(id) on delete cascade,
  nombre text not null,
  cantidad int not null check (cantidad > 0)
);

create index if not exists idx_pedidos_extraordinarios_fecha_estado
on public.pedidos_extraordinarios(fecha desc, estado);

create index if not exists idx_pedido_extra_detalle_pedido
on public.pedido_extraordinario_detalle(pedido_extraordinario_id);

alter table if exists public.pedidos_extraordinarios enable row level security;
alter table if exists public.pedido_extraordinario_detalle enable row level security;

drop policy if exists "Auth can insert pedidos extraordinarios" on public.pedidos_extraordinarios;
create policy "Auth can insert pedidos extraordinarios"
on public.pedidos_extraordinarios
for insert
to authenticated
with check (true);

drop policy if exists "Auth can read own pedidos extraordinarios" on public.pedidos_extraordinarios;
create policy "Auth can read own pedidos extraordinarios"
on public.pedidos_extraordinarios
for select
to authenticated
using (empleado_id = auth.uid()::uuid);

drop policy if exists "Auth can insert detalle extraordinario" on public.pedido_extraordinario_detalle;
create policy "Auth can insert detalle extraordinario"
on public.pedido_extraordinario_detalle
for insert
to authenticated
with check (true);

drop policy if exists "Auth can read detalle extraordinario" on public.pedido_extraordinario_detalle;
create policy "Auth can read detalle extraordinario"
on public.pedido_extraordinario_detalle
for select
to authenticated
using (
  exists (
    select 1
    from public.pedidos_extraordinarios pe
    where pe.id = pedido_extraordinario_id
      and pe.empleado_id = auth.uid()::uuid
  )
);

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

    if v_nombre is null then
      raise exception 'Nombre inválido en item extraordinario';
    end if;

    if v_cantidad is null or v_cantidad <= 0 then
      raise exception 'Cantidad inválida en item extraordinario';
    end if;

    insert into public.pedido_extraordinario_detalle (
      pedido_extraordinario_id,
      nombre,
      cantidad
    ) values (
      v_pedido_id,
      v_nombre,
      v_cantidad
    );
  end loop;

  return v_pedido_id;
end;
$$;

revoke all on function public.crear_pedido_extraordinario(uuid, text, text, json, text) from public;
grant execute on function public.crear_pedido_extraordinario(uuid, text, text, json, text) to authenticated;
