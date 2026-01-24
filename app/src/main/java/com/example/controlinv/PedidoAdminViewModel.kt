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
@Serializable
data class Pedido(
    val id: String,
    val empleado_id: String,
    val estado: String,
    @SerialName("fecha")
    val created_at: String,
    // NO viene de Supabase, lo llenamos nosotros
    var emailEmpleado: String? = null
    //val profiles: Profile? = null
)
@Serializable
data class PedidoDetalle(
    val id: String,
    val producto_id: String,
    val cantidad: Int,
    val inventario: Inventario? = null
)
class PedidoAdminViewModel : ViewModel() {
    var cargando by mutableStateOf(false)
        private set
    var pedidos by mutableStateOf<List<Pedido>>(emptyList())
        private set
    init {
        cargarPedidos()
    }
    private fun cargarPedidos() {
        viewModelScope.launch {
            cargando = true
            val lista = supabase.from("pedidos").select().decodeList<Pedido>()
            // Obtener todos los IDs únicos de empleados
            val idsEmpleados = lista.map { it.empleado_id }.distinct()
            // Traer perfiles de esos empleados
            val perfiles = supabase
                .from("profiles")
                .select()
                .decodeList<Profile>()
                .associateBy { it.id }
            // Mapear el email en cada pedido
            pedidos = lista.map {
                it.copy(
                    emailEmpleado = perfiles[it.empleado_id]?.email ?: "Empleado desconocido"
                )
            }
            cargando = false
        }
    }
}
