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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.Normalizer


data class ItemCarrito(
    val producto: Inventario,
    var cantidad: Int,
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
        comentario: String,
        onOk: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // 1) Validamos que cada item tenga producto_id y cantidad > 0
                val itemsValidos = carrito.filter { it.producto.id != null && it.cantidad > 0 }
                if (itemsValidos.isEmpty()) {
                    onError("El carrito no tiene items válidos")
                    return@launch
                }

                // Validación local adicional: no enviar pedidos que dejarían stock negativo
                val inventarioPorId = inventarioOriginal.associateBy { it.id }
                val itemSinStock = itemsValidos.firstOrNull { item ->
                    val stockActual = inventarioPorId[item.producto.id]?.cantidad ?: 0
                    item.cantidad > stockActual
                }
                if (itemSinStock != null) {
                    val nombre = itemSinStock.producto.descripcion ?: "producto"
                    onError("Stock insuficiente para $nombre")
                    recargarInventario()
                    return@launch
                }

                // 2) Armamos el JSON que espera la función RPC crear_pedido en Supabase
                val itemsJson = JsonArray(
                    itemsValidos.map {
                        JsonObject(
                            mapOf(
                                "producto_id" to JsonPrimitive(it.producto.id!!),
                                "cantidad" to JsonPrimitive(it.cantidad)
                            )
                        )
                    }
                )

                // 3) Ejecutamos la RPC: ella valida stock, reserva inventario y crea detalle
                supabase.postgrest.rpc(
                    function = "crear_pedido",
                    parameters = JsonObject(
                        mapOf(
                            "p_empleado_id" to JsonPrimitive(userId),
                            "p_empleado_email" to JsonPrimitive(email),
                            "p_comentario" to if (comentario.isBlank()) JsonNull else JsonPrimitive(comentario),
                            "p_items" to itemsJson
                        )
                    )
                )

                // 4) Refrescamos catálogo para mostrar stock actualizado
                recargarInventario()
                Log.i("PEDIDO", "Pedido creado correctamente")
                carrito.clear()
                onOk()
            } catch (e: Exception) {
                Log.e("PEDIDO", "Error creando pedido", e)
                val mensajeOriginal = e.message ?: ""
                // Caso conocido: el pedido sí se crea, pero falla parseo del UUID retornado
                val esErrorParseoRetornoUuid =
                    mensajeOriginal.contains("Unexpected JSON token", ignoreCase = true) &&
                        mensajeOriginal.contains("JSON input", ignoreCase = true)

                recargarInventario()

                if (esErrorParseoRetornoUuid) {
                    Log.w("PEDIDO", "Pedido creado pero falló parseo del UUID retornado por RPC")
                    carrito.clear()
                    onOk()
                    return@launch
                }

                val mensajeUsuario = when {
                    mensajeOriginal.contains("Stock insuficiente", ignoreCase = true) ->
                        "No hay stock suficiente para uno o más productos del carrito."
                    else -> "No se pudo crear el pedido: ${mensajeOriginal.ifBlank { "error desconocido" }}"
                }
                onError(mensajeUsuario)
            }
        }
    }

    fun refrescarInventario() {
        viewModelScope.launch {
            recargarInventario()
        }
    }

    private fun cargarInventario() {
        viewModelScope.launch {
            recargarInventario()
        }
    }

    private suspend fun recargarInventario() {
        try {
            cargando = true
            val lista = supabase
                .from("inventario")
                .select()
                .decodeList<Inventario>()

            inventarioOriginal = lista
            inventario = lista
        } finally {
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
        val consulta = normalizarTexto(texto)
        if (consulta.isBlank()) {
            inventario = inventarioOriginal
            return
        }

        val terminos = consulta.split(" ").filter { it.isNotBlank() }

        inventario = inventarioOriginal.filter { item ->
            val descripcion = normalizarTexto(item.descripcion)
            val clasificacion = normalizarTexto(item.clasificacion)
            val baseBusqueda = "$descripcion $clasificacion"

            terminos.all { termino -> baseBusqueda.contains(termino) }
        }
    }

    private fun normalizarTexto(valor: String?): String {
        if (valor.isNullOrBlank()) return ""
        return Normalizer.normalize(valor.lowercase(), Normalizer.Form.NFD)
            .replace("\p{M}+".toRegex(), "")
            .trim()
    }
}
