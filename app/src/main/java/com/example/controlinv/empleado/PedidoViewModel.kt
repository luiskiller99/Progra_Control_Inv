package com.example.controlinv.empleado

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.controlinv.inventario.model.Inventario
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class ItemCarrito(
    val producto: Inventario,
    var cantidad: Int,
)

@Serializable
private data class PedidoCreado(
    val id: String
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

    private var inventarioOriginal: List<Inventario> = emptyList()

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
                val itemsValidos = carrito.filter { it.producto.id != null && it.cantidad > 0 }
                if (itemsValidos.isEmpty()) {
                    onError("El carrito no tiene items válidos")
                    return@launch
                }

                val itemsJson = JsonArray(
                    itemsValidos.map {
                        JsonObject(
                            mapOf(
                                "producto_id" to JsonPrimitive(it.producto.id!!),
                                "cantidad" to JsonPrimitive(it.cantidad),
                            )
                        )
                    }
                )

                runCatching {
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
                }.onFailure { rpcError ->
                    Log.w("PEDIDO", "RPC crear_pedido falló, intento fallback", rpcError)
                    crearPedidoFallback(userId = userId, email = email, items = itemsValidos)
                }

                carrito.clear()
                onOk()
            } catch (e: Exception) {
                Log.e("PEDIDO", "Error creando pedido", e)
                val mensaje = e.message ?: "Error desconocido al crear pedido"
                onError(mensaje)
            }
        }
    }

    private suspend fun crearPedidoFallback(
        userId: String,
        email: String,
        items: List<ItemCarrito>
    ) {
        val pedido = supabase
            .from("pedidos")
            .insert(
                mapOf(
                    "empleado_id" to userId,
                    "empleado_email" to email,
                    "estado" to "ENVIADO"
                )
            ) {
                select()
            }
            .decodeSingle<PedidoCreado>()

        val detalles = items.map {
            mapOf(
                "pedido_id" to pedido.id,
                "producto_id" to it.producto.id!!,
                "cantidad" to it.cantidad
            )
        }

        supabase
            .from("pedido_detalle")
            .insert(detalles)
    }

    private fun cargarInventario() {
        viewModelScope.launch {
            cargando = true
            val lista = supabase
                .from("inventario")
                .select()
                .decodeList<Inventario>()

            inventarioOriginal = lista
            inventario = lista

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

    fun filtrarInventario(texto: String) {
        if (texto.isBlank()) {
            inventario = inventarioOriginal
        } else {
            inventario = inventarioOriginal.filter {
                it.descripcion?.contains(texto, ignoreCase = true) == true ||
                    it.clasificacion?.contains(texto, ignoreCase = true) == true
            }
        }
    }
}
