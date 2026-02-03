package com.example.controlinv.inventario
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.controlinv.Inventario
import com.example.controlinv.auth.supabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
class InventarioViewModel : ViewModel() {
    var hayCambios by mutableStateOf(false)
        private set
    var cargando by mutableStateOf(false)
        private set
    private var inventarioCompleto = listOf<Inventario>()
    var inventario by mutableStateOf<List<Inventario>>(emptyList())
        private set

    init {
        cargarInventario()
    }
    private fun cargarInventario() {
        viewModelScope.launch {
            cargando = true

            inventarioCompleto = supabase
                .from("inventario")
                .select()
                .decodeList()

            // Copia para edición
            inventario = inventarioCompleto.map { it.copy() }

            cargando = false
        }
    }
    fun filtrar(texto: String) {
        inventario = if (texto.isBlank()) {
            inventarioCompleto.map { it.copy() }
        } else {
            inventarioCompleto
                .filter {
                    it.codigo?.contains(texto, true) == true ||
                            it.descripcion?.contains(texto, true) == true ||
                            it.clasificacion?.contains(texto, true) == true
                }
                .map { it.copy() }
        }
    }
    fun agregar(item: Inventario) {
        viewModelScope.launch {
            val creado = insertarInventario(item)
            inventarioCompleto = inventarioCompleto + creado
            inventario = inventarioCompleto.map { it.copy() }
        }
    }
    fun eliminar(id: String) {
        viewModelScope.launch {
            eliminarInventario(id)
            inventarioCompleto = inventarioCompleto.filter { it.id != id }
            inventario = inventario.filter { it.id != id }
            hayCambios = false
        }
    }
    fun guardarFila(itemNuevo: Inventario) {
        val itemAnterior = inventario.find { it.id == itemNuevo.id } ?: return

        viewModelScope.launch {
            try {
                // 1️⃣ actualizar inventario
                supabase
                    .from("inventario")
                    .update(itemNuevo) {
                        filter { eq("id", itemNuevo.id!!) }
                    }

                // 2️⃣ obtener email del admin logueado
                val adminEmail =
                    supabase.auth.currentUserOrNull()?.email ?: "desconocido@local"

                // 3️⃣ guardar log (ANTES vs DESPUÉS)
                /**AQUI ES DONDE SE CAE*/
                // convertir objetos a JSON
                val itemAnteriorJson = Json.encodeToString(itemAnterior)
                val itemNuevoJson = Json.encodeToString(itemNuevo)
                // 4️⃣ insertar log
                supabase
                    .from("inventario_logs")
                    .insert(
                        mapOf(
                            "producto_id" to itemNuevo.id,
                            "admin_email" to adminEmail,
                            "item_anterior" to itemAnteriorJson,
                            "item_nuevo" to itemNuevoJson
                        )
                    )

                //**aqui termina codigo donde se cae*/
                // 4️⃣ refrescar lista
                cargarInventario()

            } catch (e: Exception) {
                Log.e("INVENTARIO", "Error guardando inventario", e)
            }
        }
    }
    fun descartarFila(id: String) {
        val original = inventarioCompleto.find { it.id == id } ?: return
        inventario = inventario.map {
            if (it.id == id) original.copy() else it
        }
    }
}
/*
suspend fun actualizarInventario(
    itemNuevo: Inventario,
    itemAnterior: Inventario,
    adminEmail: String
) {
    if (itemNuevo.id == null) return

    // 1️⃣ Actualizar inventario
    supabase
        .from("inventario")
        .update(itemNuevo) {
            filter { eq("id", itemNuevo.id) }
        }

    // 2️⃣ Guardar log (JSON PURO)
    supabase
        .from("inventario_logs")
        .insert(
            mapOf(
                "producto_id" to itemNuevo.id,
                "admin_email" to adminEmail,
                "item_anterior" to Json.encodeToString(itemAnterior),
                "item_nuevo" to Json.encodeToString(itemNuevo)
            )
        )
}*/
suspend fun insertarInventario(item: Inventario): Inventario {
    return supabase
        .from("inventario")
        .insert(item) {
            select()
        }
        .decodeSingle()
}
suspend fun eliminarInventario(id: String) {
    supabase.from("inventario")
        .delete {
            filter{
                eq("id", id)
            }
        }

}
suspend fun logout() {
    supabase.auth.signOut()
}