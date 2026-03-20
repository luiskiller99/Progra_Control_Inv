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
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class PedidoUI(
    val id: String,
    val empleadoEmail: String,
    val fecha: String,
    val estado: String,
    val comentario: String,
    val productos: List<String>,
    val esExtraordinario: Boolean = false,
    val prioridad: String = ""
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
    val empleado_email: String? = null,
    val prioridad: String? = null,
    val comentario: String? = null
)

@Serializable
data class DetallePedidoExtraordinario(
    @SerialName("pedido_extraordinario_id")
    val pedidoExtraordinarioId: String? = null,
    @SerialName("pedido_id")
    val pedidoId: String? = null,
    @SerialName("nombre_articulo")
    val nombreArticulo: String? = null,
    @SerialName("articulo")
    val articulo: String? = null,
    @SerialName("descripcion")
    val descripcion: String? = null,
    @SerialName("cantidad")
    val cantidad: Int? = null
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

                val pedidosExtraordinarios = runCatching {
                    supabase
                        .from("pedidos_extraordinarios")
                        .select()
                        .decodeList<PedidoExtraordinario>()
                }.getOrElse {
                    Log.w("ADMIN_PEDIDOS", "No se pudieron cargar pedidos_extraordinarios", it)
                    emptyList()
                }

                val detallesExtraordinarios = runCatching {
                    supabase
                        .from("pedido_extraordinario_detalle")
                        .select()
                        .decodeList<JsonObject>()
                        .groupBy { detalle ->
                            detalle.stringOrNull("pedido_extraordinario_id")
                                ?: detalle.stringOrNull("pedido_id")
                                ?: ""
                        }
                }.getOrElse {
                    Log.w("ADMIN_PEDIDOS", "No se pudieron cargar detalles extraordinarios", it)
                    emptyMap()
                }

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
                        esExtraordinario = false,
                        prioridad = ""
                    )
                }

                val pedidosExtraordinariosUI = pedidosExtraordinarios
                    .sortedByDescending { it.fecha }
                    .map { pedido ->
                        val productos = detallesExtraordinarios[pedido.id]
                            .orEmpty()
                            .map { detalle ->
                                val nombre = detalle.stringOrNull("nombre")
                                    ?: detalle.stringOrNull("nombre_articulo")
                                    ?: detalle.stringOrNull("articulo")
                                    ?: detalle.stringOrNull("descripcion")
                                    ?: "Artículo extraordinario"
                                val cantidad = detalle.intOrNull("cantidad") ?: 1
                                val unidad = detalle.stringOrNull("unidad").orEmpty().trim()
                                val nombreConUnidad = if (unidad.isBlank()) nombre else "$nombre ($unidad)"
                                "$cantidad x $nombreConUnidad"
                            }

                        PedidoUI(
                            id = pedido.id,
                            empleadoEmail = pedido.empleado_email ?: "Desconocido",
                            fecha = pedido.fecha,
                            estado = pedido.estado,
                            comentario = pedido.comentario.orEmpty(),
                            productos = productos,
                            esExtraordinario = true,
                            prioridad = pedido.prioridad ?: ""
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

    fun aceptarPedido(pedidoId: String) {
        viewModelScope.launch {
            try {
                supabase.postgrest.rpc(
                    "aceptar_pedido",
                    mapOf("p_pedido_id" to pedidoId)
                )
                cargarPedidos()
            } catch (e: Exception) {
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

private fun JsonObject.stringOrNull(key: String): String? {
    val value: JsonElement = this[key] ?: return null
    return value.jsonPrimitive.contentOrNull
}

private fun JsonObject.intOrNull(key: String): Int? =
    this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
