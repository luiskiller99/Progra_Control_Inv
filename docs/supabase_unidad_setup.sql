-- Agrega columna de unidad al inventario (litros, kilos, etc.)
alter table if exists public.inventario
add column if not exists unidad text;

comment on column public.inventario.unidad is 'Unidad de medida del producto (ej: litros, kilos, unidades).';

-- Opcional para pedidos extraordinarios (si guardas detalle en Supabase)
alter table if exists public.pedido_extraordinario_detalle
add column if not exists unidad text;

comment on column public.pedido_extraordinario_detalle.unidad is 'Unidad de medida solicitada para artículo extraordinario.';
