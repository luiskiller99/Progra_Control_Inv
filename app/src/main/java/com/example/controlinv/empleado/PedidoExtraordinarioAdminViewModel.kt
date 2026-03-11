package com.example.controlinv.empleado

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.controlinv.auth.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class PedidoExtraordinarioAdmin(
    val id: String,
    val fecha: String,
    val estado: String,
    val empleado_id: String,
    val empleado_email: String,
    val comentario: String,
    val prioridad: String
)

data class PedidoExtraordinarioUI(
    val id: String,
    val fecha: String,
    val estado: String,
    val empleadoEmail: String,
    val comentario: String,
    val prioridad: String,
    val productos: List<ProductoPedidoUI>
)

class PedidoExtraordinarioAdminViewModel : ViewModel() {
    private val _listaPedidos = MutableStateFlow<List<PedidoExtraordinarioUI>>(emptyList())
    val listaPedidos: StateFlow<List<PedidoExtraordinarioUI>> = _listaPedidos

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
                    .from("pedidos_extraordinarios")
                    .select()
                    .decodeList<PedidoExtraordinarioAdmin>()

                // MISMO PATRÓN QUE EMPLEADO:
                // Se consulta todo el detalle extraordinario y luego se agrupa por pedido_extraordinario_id.
                val detallesExtraordinarios = supabase
                    .from("pedido_extraordinario_detalle")
                    .select()
                    .decodeList<DetallePedidoExtraordinarioEmpleado>()
                    .groupBy { it.pedido_extraordinario_id }

                _listaPedidos.value = pedidos
                    .sortedByDescending { it.fecha }
                    .map { pedido ->
                        val productos = detallesExtraordinarios[pedido.id]
                            .orEmpty()
                            .map { det ->
                                ProductoPedidoUI(
                                    descripcion = det.nombre,
                                    cantidad = det.cantidad
                                )
                            }

                        PedidoExtraordinarioUI(
                            id = pedido.id,
                            fecha = pedido.fecha,
                            estado = pedido.estado,
                            empleadoEmail = pedido.empleado_email,
                            comentario = pedido.comentario,
                            prioridad = pedido.prioridad,
                            productos = productos
                        )
                    }
            } catch (e: Exception) {
                Log.e("ADMIN_EXTRAORDINARIOS", "Error cargando pedidos extraordinarios", e)
            } finally {
                cargando = false
            }
        }
    }

    fun aceptarPedido(pedidoId: String) {
        actualizarEstado(pedidoId, "ACEPTADO")
    }

    fun rechazarPedido(pedidoId: String) {
        actualizarEstado(pedidoId, "RECHAZADO")
    }

    private fun actualizarEstado(pedidoId: String, estado: String) {
        viewModelScope.launch {
            try {
                supabase
                    .from("pedidos_extraordinarios")
                    .update(mapOf("estado" to estado)) {
                        filter { eq("id", pedidoId) }
                        select(Columns.list("id"))
                    }
                cargarPedidos()
            } catch (e: Exception) {
                Log.e("ADMIN_EXTRAORDINARIOS", "Error actualizando pedido extraordinario", e)
            }
        }
    }
}
