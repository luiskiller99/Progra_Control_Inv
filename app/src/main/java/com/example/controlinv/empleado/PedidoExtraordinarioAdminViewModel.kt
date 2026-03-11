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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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

@Serializable
private data class DetallePedidoExtraordinarioAdmin(
    val pedido_extraordinario_id: String,
    val nombre: String? = null,
    val cantidad: Int
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

                val pedidosIds = pedidos.map { it.id }
                val detallesPorPedido = cargarDetallesPorPedido(pedidosIds)

                val pedidosUI = pedidos
                    .sortedByDescending { it.fecha }
                    .map { pedido ->
                        val productos = detallesPorPedido[pedido.id.lowercase()].orEmpty()
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

                val pedidosSinDetalle = pedidosUI.count { it.productos.isEmpty() }
                Log.i(
                    "ADMIN_EXTRAORDINARIOS",
                    "Pedidos=${pedidosUI.size}, pedidosSinDetalle=$pedidosSinDetalle, detallesTotales=${detallesPorPedido.values.sumOf { it.size }}"
                )

                _listaPedidos.value = pedidosUI
            } catch (e: Exception) {
                Log.e("ADMIN_EXTRAORDINARIOS", "Error cargando pedidos extraordinarios", e)
            } finally {
                cargando = false
            }
        }
    }

    private suspend fun cargarDetallesPorPedido(pedidosIds: List<String>): Map<String, List<ProductoPedidoUI>> {
        if (pedidosIds.isEmpty()) return emptyMap()

        val detallesTipados = supabase
            .from("pedido_extraordinario_detalle")
            .select {
                filter { isIn("pedido_extraordinario_id", pedidosIds) }
            }
            .decodeList<DetallePedidoExtraordinarioAdmin>()

        val productosTipados = detallesTipados
            .groupBy { it.pedido_extraordinario_id.lowercase() }
            .mapValues { (_, detalles) ->
                detalles.map { det ->
                    ProductoPedidoUI(
                        descripcion = det.nombre.orEmpty().ifBlank { "Producto" },
                        cantidad = det.cantidad
                    )
                }
            }

        if (productosTipados.values.sumOf { it.size } > 0) {
            return productosTipados
        }

        Log.w(
            "ADMIN_EXTRAORDINARIOS",
            "Detalle tipado vacío. Intentando parseo flexible de pedido_extraordinario_detalle"
        )

        val detallesRaw = supabase
            .from("pedido_extraordinario_detalle")
            .select {
                filter { isIn("pedido_extraordinario_id", pedidosIds) }
            }
            .decodeList<JsonObject>()

        return detallesRaw
            .mapNotNull { fila -> fila.toProductoPedidoUI() }
            .groupBy { it.first.lowercase() }
            .mapValues { (_, pares) -> pares.map { it.second } }
    }

    private fun JsonObject.toProductoPedidoUI(): Pair<String, ProductoPedidoUI>? {
        val pedidoId = (this["pedido_extraordinario_id"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (pedidoId.isBlank()) return null

        val cantidad = (this["cantidad"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        if (cantidad <= 0) return null

        val descripcion = listOf("nombre", "descripcion", "producto", "producto_nombre")
            .asSequence()
            .mapNotNull { key -> (this[key] as? JsonPrimitive)?.contentOrNull }
            .firstOrNull { it.isNotBlank() }
            ?: "Producto"

        return pedidoId to ProductoPedidoUI(
            descripcion = descripcion,
            cantidad = cantidad
        )
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
