package com.example.controlinv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class InventarioViewModel : ViewModel() {
    var hayCambios by mutableStateOf(false)
        private set


    var cargando by mutableStateOf(false)
        private set

    /** Lista original (lo que viene de la BD) */
    private var inventarioCompleto = listOf<Inventario>()

    /** Lista editable (lo que se muestra en pantalla) */
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

    /** =========================
     * FILTRO (no toca la BD)
     * ========================= */
    fun filtrar(texto: String) {
        inventario = if (texto.isBlank()) {
            inventarioCompleto.map { it.copy() }
        } else {
            inventarioCompleto
                .filter {
                    it.codigo?.contains(texto, true) == true ||
                            it.descripcion?.contains(texto, true) == true
                }
                .map { it.copy() }
        }
    }

    /** =========================
     * AGREGAR (sí guarda en BD)
     * ========================= */
    fun agregar(item: Inventario) {
        viewModelScope.launch {
            val creado = insertarInventario(item)
            inventarioCompleto = inventarioCompleto + creado
            inventario = inventarioCompleto.map { it.copy() }
        }
    }
    /** =========================
     * ELIMINAR
     * ========================= */
    fun eliminar(id: String) {
        viewModelScope.launch {
            eliminarInventario(id)
            inventarioCompleto = inventarioCompleto.filter { it.id != id }
            inventario = inventario.filter { it.id != id }
            hayCambios = false
        }
    }
    /** Guardar UNA fila */
    fun guardarFila(item: Inventario) {
        viewModelScope.launch {
            if (item.id == null) return@launch

            actualizarInventario(item)

            // sincroniza memoria
            inventarioCompleto = inventarioCompleto.map {
                if (it.id == item.id) item else it
            }
            inventario = inventarioCompleto.map { it.copy() }
        }
    }

    /** Descartar cambios de UNA fila */
    fun descartarFila(id: String) {
        val original = inventarioCompleto.find { it.id == id } ?: return
        inventario = inventario.map {
            if (it.id == id) original.copy() else it
        }
    }


}


