package com.example.controlinv.data

import com.example.controlinv.Inventario
import com.example.controlinv.supabase
import io.github.jan.supabase.postgrest.from

class InventarioRepository {

    suspend fun actualizar(item: Inventario) {
        if (item.id == null) return
        supabase.from("inventario")
            .update(item) {
                filter { eq("id", item.id) }
            }
    }

    suspend fun insertar(item: Inventario): Inventario {
        return supabase.from("inventario")
            .insert(item) { select() }
            .decodeSingle()
    }

    suspend fun eliminar(id: String) {
        supabase.from("inventario")
            .delete {
                filter { eq("id", id) }
            }
    }
}
