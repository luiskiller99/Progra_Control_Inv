package com.example.controlinv

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.collections.mapOf
/*APP LUISKILLER99*/
data class ItemCarrito(
    val producto: Inventario,
    var cantidad: Int
)

class PedidoViewModel(
    private val supabase: io.github.jan.supabase.SupabaseClient
) : ViewModel() {


    var inventario by mutableStateOf<List<Inventario>>(emptyList())
        private set
    var carrito = mutableStateListOf<ItemCarrito>()
        private set
    var cargando by mutableStateOf(false)
        private set
    init {
        cargarInventario()
    }
    fun confirmarPedido(
        userId: String,
        onOk: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val itemsJson = JsonArray(
                carrito.map {
                    JsonObject(
                        mapOf(
                            "producto_id" to JsonPrimitive(it.producto.id!!),
                            "cantidad" to JsonPrimitive(it.cantidad)
                        )
                    )
                }
            )

            supabase.postgrest.rpc(
                "crear_pedido",
                JsonObject(
                    mapOf(
                        "p_empleado_id" to JsonPrimitive(userId),//empleadoId
                        "p_items" to itemsJson
                    )
                )
            )


            carrito.clear()
            onOk()
        }
    }
    private fun cargarInventario() {
        viewModelScope.launch {
            cargando = true
            inventario = supabase
                .from("inventario")
                .select()
                .decodeList()
            cargando = false
        }
    }
    fun agregarAlCarrito(item: Inventario, cantidad: Int) {
        if (cantidad <= 0) return

        val existente = carrito.find { it.producto.id == item.id }
        if (existente != null) {
            existente.cantidad += cantidad
        } else {
            carrito.add(ItemCarrito(item, cantidad))
        }
    }
    fun limpiarCarrito() {
        carrito.clear()
    }
}
