package com.example.controlinv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class Pedido(
    val id: String,
    val empleado_id: String,
    val estado: String,
    val created_at: String,
    val profiles: Profile? = null
)

@Serializable
data class PedidoDetalle(
    val id: String,
    val producto_id: String,
    val cantidad: Int,
    val inventario: Inventario? = null
)


class PedidosAdminViewModel : ViewModel() {

    var pedidos by mutableStateOf<List<Pedido>>(emptyList())
        private set

    var cargando by mutableStateOf(false)
        private set

    init {
        cargarPedidos()
    }

    fun cargarPedidos() {
        viewModelScope.launch {
            try {
                cargando = true

                val lista = supabase
                    .from("pedidos")
                    .select()
                    .decodeList<Pedido>()

                // 👇 ORDENAMOS AQUÍ (no en Supabase)
                pedidos = lista.sortedByDescending { it.created_at }

                cargando = false

            } catch (e: Exception) {
                cargando = false
                e.printStackTrace()
            }
        }
    }


}

