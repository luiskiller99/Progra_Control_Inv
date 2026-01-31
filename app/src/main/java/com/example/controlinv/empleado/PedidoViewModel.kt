package com.example.controlinv.empleado
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.controlinv.Inventario
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.collections.mapOf
data class ItemCarrito(
    val producto: Inventario,
    var cantidad: Int
)
class PedidoViewModel(
    private val supabase: SupabaseClient
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
        email: String,
        onOk: () -> Unit,
        onError: (String) -> Unit
    ) {
            viewModelScope.launch {
                try {
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
                            "p_empleado_id" to JsonPrimitive(userId),
                            "p_empleado_email" to JsonPrimitive(email),
                            "p_items" to itemsJson
                        )
                    )
                )
                carrito.clear()
                onOk()
                } catch (e: Exception) {
                    val mensaje = e.message ?: "Error desconocido al crear pedido"
                    onError(mensaje)
                }
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

        val index = carrito.indexOfFirst { it.producto.id == item.id }

        if (index >= 0) {
            val actual = carrito[index]
            carrito[index] = actual.copy(
                cantidad = actual.cantidad + cantidad
            )
        } else {
            carrito.add(ItemCarrito(item, cantidad))
        }
    }
    fun quitarDelCarrito(productoId: String) {
        carrito.removeAll { it.producto.id == productoId }
    }

    fun restarDelCarrito(productoId: String) {
        val index = carrito.indexOfFirst { it.producto.id == productoId }
        if (index >= 0) {
            val actual = carrito[index]
            if (actual.cantidad > 1) {
                carrito[index] = actual.copy(
                    cantidad = actual.cantidad - 1
                )
            } else {
                carrito.removeAt(index)
            }
        }
    }

}
