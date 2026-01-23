package com.example.controlinv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
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

                // 1️⃣ Pedidos
                val pedidosDb = supabase
                    .from("pedidos")
                    .select()
                    .decodeList<Pedido>()

                // 2️⃣ IDs únicos de empleados
                val ids = pedidosDb.map { it.empleado_id }.distinct()

                // 3️⃣ Traer perfiles
                val perfiles = supabase
                    .from("profiles")
                    .select()
                    .decodeList<Profile>()
                    .associateBy { it.id }

                // 4️⃣ Unir datos
                pedidos = pedidosDb.map {
                    it.copy(
                        emailEmpleado = perfiles[it.empleado_id]?.email
                    )
                }.sortedByDescending { it.created_at }

                cargando = false

            } catch (e: Exception) {
                cargando = false
                e.printStackTrace()
            }
        }
    }
}

