package com.example.controlinv.admin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.collections.mapOf
import android.util.Log
import com.example.controlinv.DetallePedido
import com.example.controlinv.Inventario
import com.example.controlinv.supabase
import kotlinx.coroutines.flow.MutableStateFlow
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
    val _listaPedidos = MutableStateFlow<List<PedidoUI>>(emptyList())
    var cargando by mutableStateOf(false)
        private set
    init {
        cargarPedidos()
    }
    fun aceptarPedido(pedidoId: String) = viewModelScope.launch {
        supabase.from("pedidos")
            .update(mapOf("estado" to "ACEPTADO")) {
                filter { eq("id", pedidoId) }
            }
        cargarPedidos()
    }
    fun rechazarPedido(pedidoId: String) = viewModelScope.launch {
/*
        // 1️⃣ Obtener detalles del pedido
        val detalles = supabase
            .from("pedido_detalle")
            .select()
            .match(mapOf("pedido_id" to pedidoId))
            .decodeList<DetallePedido>()

        // 2️⃣ Devolver stock usando RPC
        detalles.forEach { det ->
            supabase.rpc(
                "devolver_stock",
                mapOf(
                    "p_producto_id" to det.producto_id,
                    "p_cantidad" to det.cantidad
                )
            )
        }

        // 3️⃣ Cambiar estado del pedido
        supabase.from("pedidos")
            .update(mapOf("estado" to "RECHAZADO"))
            .match(mapOf("id" to pedidoId))

        cargarPedidos()*/
    }
    /**fun cargarPedidos() {
        viewModelScope.launch {
            cargando = true

            try {
                // 1️⃣ Pedidos
                val pedidos = supabase
                    .from("pedidos")
                    .select()
                    .decodeList<Pedido>()

                // 2️⃣ Detalles
                val detalles = supabase
                    .from("pedido_detalle")
                    .select()
                    .decodeList<DetallePedido>()
                    .groupBy { it.pedido_id }

                // 3️⃣ Inventario
                val inventario = supabase
                    .from("inventario")
                    .select()
                    .decodeList<Inventario>()
                    .associateBy { it.id }

                // 4️⃣ Profiles (⚠️ PROTEGIDO)
                val profiles = try {
                    supabase
                        .from("profiles")
                        .select(Columns.raw("id,email"))
                        .decodeList<Profile>()
                        .associateBy { it.id }
                } catch (e: Exception) {
                    emptyMap()
                }

                // 5️⃣ UI
                val pedidosUI = pedidos.map { pedido ->

                    val productos = detalles[pedido.id]
                        .orEmpty()
                        .map { det ->
                            val nombre = inventario[det.producto_id]?.descripcion
                                ?: "Producto desconocido"
                            "${det.cantidad} x $nombre"
                        }

                    PedidoUI(
                        id = pedido.id,
                        empleadoEmail = pedido.empleado_email ?: "Empleado desconocido",
                        fecha = pedido.fecha.toString(),
                        estado = pedido.estado,
                        productos = productos
                    )
                }


                _listaPedidos.value = pedidosUI

            } catch (e: Exception) {
                Log.e("PEDIDOS", "Error cargando pedidos", e)
                _listaPedidos.value = emptyList()
            } finally {
                cargando = false
            }
        }
    }*/
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
                            val nombre = inventario[det.producto_id]?.descripcion
                                ?: "Producto desconocido"
                            "${det.cantidad} x $nombre"
                        }

                    PedidoUI(
                        id = pedido.id,
                        empleadoEmail = pedido.empleado_email ?: "Empleado desconocido", // 🔥 DIRECTO
                        fecha = pedido.fecha.toString(),
                        estado = pedido.estado,
                        productos = productos
                    )
                }

                _listaPedidos.value = pedidosUI
                cargando = false

            } catch (e: Exception) {
                cargando = false
                Log.e("PEDIDOS", "Error cargando pedidos", e)
            }
        }
    }

}
