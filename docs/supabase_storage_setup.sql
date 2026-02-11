-- Ejecutar en Supabase SQL Editor
-- Nota: `CREATE POLICY IF NOT EXISTS` no está soportado en este entorno,
-- por eso usamos DROP POLICY IF EXISTS + CREATE POLICY.

-- 1) Crear bucket para imágenes de productos
insert into storage.buckets (id, name, public)
values ('productos', 'productos', true)
on conflict (id) do nothing;

-- 2) Permitir lectura pública de imágenes
drop policy if exists "Public read productos" on storage.objects;
create policy "Public read productos"
on storage.objects for select
to public
using (bucket_id = 'productos');

-- 3) Permitir a usuarios autenticados subir imágenes
drop policy if exists "Authenticated upload productos" on storage.objects;
create policy "Authenticated upload productos"
on storage.objects for insert
to authenticated
with check (bucket_id = 'productos');

-- 4) (Opcional) permitir actualización y borrado a autenticados
drop policy if exists "Authenticated update productos" on storage.objects;
create policy "Authenticated update productos"
on storage.objects for update
to authenticated
using (bucket_id = 'productos')
with check (bucket_id = 'productos');

drop policy if exists "Authenticated delete productos" on storage.objects;
create policy "Authenticated delete productos"
on storage.objects for delete
to authenticated
using (bucket_id = 'productos');
