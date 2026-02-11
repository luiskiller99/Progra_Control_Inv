package com.example.controlinv.empleado

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.controlinv.Main.Inventario
import com.example.controlinv.auth.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class PedidoUI(
    val id: String,
    val empleadoEmail: String,
    val fecha: String,
    val estado: String,
    val productos: List<String>
)

@Serializable
data class Pedido(
    val id: String,
    val fecha: String,
    val estado: String,
    val empleado_id: String,
    val empleado_email: String? = null
)

class PedidoAdminViewModel : ViewModel() {
    private val _listaPedidos = MutableStateFlow<List<PedidoUI>>(emptyList())
    val listaPedidos: StateFlow<List<PedidoUI>> = _listaPedidos
    var cargando by mutableStateOf(false)
        private set
    init {
        cargarPedidos()
    }
    fun cargarPedidos() {
        viewModelScope.launch {
            try {
                cargando = true

                val pedidos = supabase
                    .from("pedidos")
                    .select()
                    .decodeList<Pedido>()

                val detalles = supabase
                    .from("pedido_detalle")
                    .select()
                    .decodeList<DetallePedido>()
                    .groupBy { it.pedido_id }

                val inventario = supabase
                    .from("inventario")
                    .select()
                    .decodeList<Inventario>()
                    .associateBy { it.id }

                val pedidosUI = pedidos.map { pedido ->

                    val productos = detalles[pedido.id]
                        .orEmpty()
                        .map { det ->
                            val producto = inventario[det.producto_id]
                            val codigo = producto?.codigo ?: "N/A"
                            val nombre = producto?.descripcion ?: "Producto"
                            "${det.cantidad} x [$codigo] $nombre"
                        }

                    PedidoUI(
                        id = pedido.id,
                        empleadoEmail = pedido.empleado_email ?: "Desconocido",
                        fecha = pedido.fecha.toString(),
                        estado = pedido.estado,
                        productos = productos
                    )
                }

                _listaPedidos.value = pedidosUI
                cargando = false

            } catch (e: Exception) {
                cargando = false
                Log.e("ADMIN_PEDIDOS", "Error cargando pedidos", e)
            }
        }
    }
    fun aceptarPedido(pedidoId: String) {
        viewModelScope.launch {
            try {
                supabase.postgrest.rpc(
                "aceptar_pedido",
                mapOf("p_pedido_id" to pedidoId)
                )
                cargarPedidos()} catch (e: Exception) {
                    Log.e("ADMIN", "Error aceptando pedido", e)
                }
        }
    }
    fun rechazarPedido(pedidoId: String) {
        viewModelScope.launch {
            try {
                supabase.postgrest.rpc(
                    "rechazar_pedido",
                    mapOf("p_pedido_id" to pedidoId)
                )
                cargarPedidos()

            } catch (e: Exception) {
                Log.e("ADMIN", "Error rechazando pedido", e)
            }
        }
    }
}

