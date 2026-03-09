package com.example.controlinv.empleado

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.controlinv.auth.supabase
import com.example.controlinv.inventario.model.Inventario
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
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
    val comentario: String,
    val productos: List<String>,
    val esExtraordinario: Boolean = false
)

@Serializable
data class Pedido(
    val id: String,
    val fecha: String,
    val estado: String,
    val empleado_id: String,
    val empleado_email: String? = null,
    val comentario: String? = null
)

@Serializable
data class PedidoExtraordinario(
    val id: String,
    val fecha: String,
    val estado: String,
    val empleado_id: String,
    val empleado_email: String,
    val prioridad: String,
    val comentario: String
)

@Serializable
data class DetallePedidoExtraordinario(
    val pedido_extraordinario_id: String,
    val nombre: String,
    val cantidad: Int
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

                val pedidosExtraordinarios = supabase
                    .from("pedidos_extraordinarios")
                    .select()
                    .decodeList<PedidoExtraordinario>()

                val detalles = supabase
                    .from("pedido_detalle")
                    .select()
                    .decodeList<DetallePedido>()
                    .groupBy { it.pedido_id }

                val detallesExtraordinarios = supabase
                    .from("pedido_extraordinario_detalle")
                    .select()
                    .decodeList<DetallePedidoExtraordinario>()
                    .groupBy { it.pedido_extraordinario_id }

                val inventario = supabase
                    .from("inventario")
                    .select()
                    .decodeList<Inventario>()
                    .associateBy { it.id }

                val pedidosUI = pedidos.sortedByDescending { it.fecha }.map { pedido ->
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
                        comentario = pedido.comentario ?: "",
                        productos = productos,
                        esExtraordinario = false
                    )
                }

                val pedidosExtraordinariosUI = pedidosExtraordinarios
                    .sortedByDescending { it.fecha }
                    .map { pedido ->
                        val productos = detallesExtraordinarios[pedido.id]
                            .orEmpty()
                            .map { det ->
                                "${det.cantidad} x [N/A] ${det.nombre}"
                            }

                        PedidoUI(
                            id = pedido.id,
                            empleadoEmail = pedido.empleado_email,
                            fecha = pedido.fecha,
                            estado = pedido.estado,
                            comentario = pedido.comentario,
                            productos = productos,
                            esExtraordinario = true
                        )
                    )
                }

                _listaPedidos.value = (pedidosUI + pedidosExtraordinariosUI)
                    .sortedByDescending { it.fecha }
            } catch (e: Exception) {
                Log.e("ADMIN_PEDIDOS", "Error cargando pedidos", e)
            } finally {
                cargando = false
            }
        }
    }

    fun aceptarPedido(pedidoId: String, esExtraordinario: Boolean) {
        viewModelScope.launch {
            try {
                if (esExtraordinario) {
                    supabase
                        .from("pedidos_extraordinarios")
                        .update(mapOf("estado" to "ACEPTADO")) {
                            filter { eq("id", pedidoId) }
                            select(Columns.list("id"))
                        }
                } else {
                    supabase.postgrest.rpc(
                        "aceptar_pedido",
                        mapOf("p_pedido_id" to pedidoId)
                    )
                }
                cargarPedidos()
            } catch (e: Exception) {
                Log.e("ADMIN", "Error aceptando pedido", e)
            }
        }
    }

    fun rechazarPedido(pedidoId: String, esExtraordinario: Boolean) {
        viewModelScope.launch {
            try {
                if (esExtraordinario) {
                    supabase
                        .from("pedidos_extraordinarios")
                        .update(mapOf("estado" to "RECHAZADO")) {
                            filter { eq("id", pedidoId) }
                            select(Columns.list("id"))
                        }
                } else {
                    supabase.postgrest.rpc(
                        "rechazar_pedido",
                        mapOf("p_pedido_id" to pedidoId)
                    )
                }
                cargarPedidos()
            } catch (e: Exception) {
                Log.e("ADMIN", "Error rechazando pedido", e)
            }
        }
    }
}
