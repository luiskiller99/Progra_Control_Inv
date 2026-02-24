-- Configuración recomendada para que el flujo de eliminación de inventario
-- sea consistente con el código actual de la app.
--
-- Objetivo:
-- 1) NO permitir borrar productos que ya están en pedidos (pedido_detalle).
-- 2) Permitir que la app consulte cuántos pedidos referencian un producto.
-- 3) Asegurar performance y políticas de acceso.

-- 1) Índice para consultas por producto_id (usado al intentar eliminar)
create index if not exists idx_pedido_detalle_producto_id
on public.pedido_detalle(producto_id);

-- 2) FK explícita en pedido_detalle -> inventario con ON DELETE RESTRICT
--    (si ya existe una FK equivalente, este bloque no es necesario)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'pedido_detalle_producto_id_fkey'
      AND conrelid = 'public.pedido_detalle'::regclass
  ) THEN
    ALTER TABLE public.pedido_detalle
      ADD CONSTRAINT pedido_detalle_producto_id_fkey
      FOREIGN KEY (producto_id)
      REFERENCES public.inventario(id)
      ON DELETE RESTRICT;
  END IF;
END $$;

-- 3) RLS: permitir lectura de pedido_detalle a usuarios autenticados
--    para que la app pueda contar relaciones antes de eliminar.
alter table if exists public.pedido_detalle enable row level security;

drop policy if exists "Auth can read pedido_detalle" on public.pedido_detalle;
create policy "Auth can read pedido_detalle"
on public.pedido_detalle
for select
to authenticated
using (true);

-- 4) (Opcional) Si quieres permitir borrado de inventario solo a admins
--    puedes crear policy específica en inventario según tu modelo de auth.
--    Ejemplo abierto para authenticated:
alter table if exists public.inventario enable row level security;

drop policy if exists "Auth can delete inventario" on public.inventario;
create policy "Auth can delete inventario"
on public.inventario
for delete
to authenticated
using (true);
