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
    init {cargarPedidos() }
    fun aceptarPedido(pedidoId: String) = viewModelScope.launch {
        supabase.from("pedidos")
            .update(mapOf("estado" to "ACEPTADO")) {
                filter { eq("id", pedidoId) }
            }
        cargarPedidos()
    }
    fun rechazarPedido(pedidoId: String) = viewModelScope.launch {}
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
