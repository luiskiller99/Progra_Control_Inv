package com.example.controlinv
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.collections.mapOf
import android.util.Log
import android.widget.Toast
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    @SerialName("profiles")
    val profile: Profile? = null,

    val pedido_detalle: List<DetallePedido> = emptyList()
)

@Serializable
data class Profile(
    val id: String,
    val email: String,
    val role: String
)


class PedidoAdminViewModel : ViewModel() {
    private val _listaPedidos = MutableStateFlow<List<PedidoUI>>(emptyList())
    val listaPedidos: StateFlow<List<PedidoUI>> = _listaPedidos

    var cargando by mutableStateOf(false)
        private set
    var pedidos by mutableStateOf<List<Pedido>>(emptyList())
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
    /*fun cargarPedidos() {
        viewModelScope.launch {
            cargando = true

            // 1️⃣ Pedidos
            val pedidosDB = supabase
                .from("pedidos")
                .select()
                .decodeList<Pedido>()

            // 2️⃣ Perfiles

            val perfiles = supabase
                .from("profiles")
                .select()
                .decodeList<Profile>()
                .associateBy { it.id }

            // 3️⃣ Inventario
            val inventario = supabase
                .from("inventario")
                .select()
                .decodeList<Inventario>()
                .associateBy { it.id }

            // 4️⃣ Detalles
            val detalles = supabase
                .from("pedido_detalle")
                .select()
                .decodeList<DetallePedido>()
                .groupBy { it.pedido_id }

            // 5️⃣ Armar pedidos finales
            pedidos = pedidosDB.map { pedido ->
                val det = detalles[pedido.id].orEmpty().map {
                    it.copy(producto = inventario[it.producto_id])
                }
                pedido.copy(
                    emailEmpleado = perfiles[pedido.empleado_id]?.email ?: "Empleado desconocido",
                    detalles = det
                )
            }

            cargando = false
        }

     */
    fun cargarPedidos() {
        viewModelScope.launch {
            try {
                cargando = true

                // 1️⃣ Pedidos
                val pedidos = supabase
                    .from("pedidos")
                    .select()
                    .decodeList<Pedido>()

                // 2️⃣ Profiles
                val profiles = supabase
                    .from("profiles")
                    .select(Columns.list("id", "email"))
                    .decodeList<Profile>()
                    .associateBy { it.id }

                // 3️⃣ UI
                val pedidosUI = pedidos.map { pedido ->

                    val emailEmpleado = profiles[pedido.empleado_id]?.email
                        ?: "Empleado desconocido"

                    PedidoUI(
                        id = pedido.id,
                        empleadoEmail = emailEmpleado,
                        fecha = pedido.fecha,
                        estado = pedido.estado,
                        productos = pedido.pedido_detalle.map {
                            "${it.cantidad} x ${it.producto_id}"
                        }
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
