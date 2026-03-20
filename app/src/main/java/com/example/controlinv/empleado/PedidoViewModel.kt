package com.example.controlinv.empleado

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.controlinv.inventario.model.Inventario
import com.example.controlinv.auth.SUPABASE_KEY
import com.example.controlinv.auth.SUPABASE_URL
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer
import java.net.HttpURLConnection
import java.net.URL


data class ItemCarrito(
    val producto: Inventario,
    var cantidad: Int,
)

data class ProductoPedidoUI(
    val descripcion: String,
    val cantidad: Int
)

data class ItemPedidoExtraordinarioInput(
    val nombre: String,
    val cantidad: Int,
    val unidad: String
)

data class MiPedidoUI(
    val id: String,
    val fecha: String,
    val estado: String,
    val comentario: String,
    val productos: List<ProductoPedidoUI>,
    val esExtraordinario: Boolean = false,
    val prioridad: String = ""
)


@Serializable
data class PedidoExtraordinarioEmpleado(
    val id: String,
    val fecha: String,
    val estado: String,
    val empleado_id: String,
    val prioridad: String? = null,
    val comentario: String? = null
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
    var cargandoMisPedidos by mutableStateOf(false)
        private set

    var misPedidos by mutableStateOf<List<MiPedidoUI>>(emptyList())
        private set

    private var inventarioOriginal: List<Inventario> = emptyList()

    init {
        cargarInventario()
    }


    private fun escapeJson(texto: String): String =
        texto.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun enviarAvisoCorreoPedido(
        empleadoEmail: String,
        comentario: String,
        productos: List<String>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val url = URL("$SUPABASE_URL/functions/v1/notificar-pedido")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("apikey", SUPABASE_KEY)
                    setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                val productosJson = productos.joinToString(",") {
                    "\"${escapeJson(it)}\""
                }
                /**"emanuel.acuna@holcim.com" ,
                "xavier.lezcanochavarria@holcim.com"*/
                val payload = """
                    {
                      "to": [
                        "emanuel.acuna@holcim.com" 
                      ],
                      "empleado_email": "${escapeJson(empleadoEmail)}",
                      "comentario": "${escapeJson(comentario)}",
                      "productos": [$productosJson]
                    }
                """.trimIndent()

                conn.outputStream.use { it.write(payload.toByteArray()) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
                    Log.e("PEDIDO", "Notificación correo falló HTTP $code: $err")
                }
                conn.disconnect()
            }.onFailure {
                Log.e("PEDIDO", "No se pudo notificar por correo", it)
            }
        }
    }


    fun confirmarPedidoExtraordinario(
        userId: String,
        email: String,
        prioridad: String,
        comentario: String,
        items: List<ItemPedidoExtraordinarioInput>,
        onOk: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            var pedidoIdCreado: String? = null
            try {
                val itemsValidos = items.map {
                    ItemPedidoExtraordinarioInput(
                        nombre = it.nombre.trim(),
                        cantidad = it.cantidad,
                        unidad = it.unidad.trim()
                    )
                }.filter { it.nombre.isNotBlank() && it.cantidad > 0 && it.unidad.isNotBlank() }

                if (itemsValidos.isEmpty()) {
                    onError("Agrega al menos un artículo extraordinario válido")
                    return@launch
                }

                val prioridadNormalizada = prioridad.trim().uppercase()
                if (prioridadNormalizada !in setOf("ALTA", "MEDIA", "BAJA")) {
                    onError("Selecciona una prioridad válida")
                    return@launch
                }

                val comentarioNormalizado = comentario.trim()
                if (comentarioNormalizado.isBlank()) {
                    onError("Ingresa un comentario para el pedido extraordinario")
                    return@launch
                }

                val pedidoCreado = supabase
                    .from("pedidos_extraordinarios")
                    .insert(
                        mapOf(
                            "empleado_id" to userId,
                            "empleado_email" to email,
                            "estado" to "ENVIADO",
                            "prioridad" to prioridadNormalizada,
                            "comentario" to comentarioNormalizado
                        )
                    ) {
                        select()
                    }
                    .decodeSingle<JsonObject>()

                val pedidoId = pedidoCreado.stringOrNull("id")
                if (pedidoId.isNullOrBlank()) {
                    onError("No se pudo obtener el pedido extraordinario creado")
                    return@launch
                }
                pedidoIdCreado = pedidoId

                val siguienteDetalleId = supabase
                    .from("pedido_extraordinario_detalle")
                    .select()
                    .decodeList<JsonObject>()
                    .maxOfOrNull { detalle -> detalle.longOrNull("id") ?: 0L }
                    ?.plus(1L) ?: 1L

                supabase
                    .from("pedido_extraordinario_detalle")
                    .insert(
                        itemsValidos.mapIndexed { index, item ->
                            mapOf(
                                "id" to (siguienteDetalleId + index),
                                "pedido_extraordinario_id" to pedidoId,
                                "nombre" to item.nombre,
                                "cantidad" to item.cantidad,
                                "unidad" to item.unidad
                            )
                        }
                    )

                val resumenProductos = itemsValidos.map { item ->
                    "${item.cantidad} x ${item.nombre} (${item.unidad})"
                }
                enviarAvisoCorreoPedido(
                    empleadoEmail = email,
                    comentario = "Pedido extraordinario ($prioridadNormalizada): $comentarioNormalizado",
                    productos = resumenProductos
                )

                onOk()
            } catch (e: Exception) {
                pedidoIdCreado?.let { pedidoId ->
                    runCatching {
                        supabase.from("pedidos_extraordinarios").delete {
                            filter { eq("id", pedidoId) }
                        }
                    }.onFailure { cleanupError ->
                        Log.w("PEDIDO", "No se pudo revertir pedido extraordinario incompleto", cleanupError)
                    }
                }
                Log.e("PEDIDO", "Error creando pedido extraordinario", e)
                val mensajeOriginal = e.message ?: ""
                val mensajeUsuario = if (mensajeOriginal.isBlank()) {
                    "No se pudo crear el pedido extraordinario"
                } else {
                    "No se pudo crear el pedido extraordinario: $mensajeOriginal"
                }
                onError(mensajeUsuario)
            }
        }
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

                val resumenProductos = itemsValidos.map { item ->
                    val codigo = item.producto.codigo?.takeIf { it.isNotBlank() } ?: "N/A"
                    val descripcion = item.producto.descripcion ?: "Producto"
                    "${item.cantidad} x [$codigo] $descripcion"
                }
                enviarAvisoCorreoPedido(
                    empleadoEmail = email,
                    comentario = comentario,
                    productos = resumenProductos
                )

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


    fun actualizarCantidadCarrito(id: String, cantidad: Int) {
        val index = carrito.indexOfFirst { it.producto.id == id }
        if (index < 0) return

        when {
            cantidad <= 0 -> carrito.removeAt(index)
            else -> {
                val actual = carrito[index]
                carrito[index] = actual.copy(cantidad = cantidad)
            }
        }
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


    fun cargarMisPedidos(userId: String?) {
        if (userId.isNullOrBlank()) {
            misPedidos = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                cargandoMisPedidos = true
                val pedidos = supabase
                    .from("pedidos")
                    .select {
                        filter { eq("empleado_id", userId) }
                    }
                    .decodeList<Pedido>()

                val detalles = supabase
                    .from("pedido_detalle")
                    .select()
                    .decodeList<DetallePedido>()
                    .groupBy { it.pedido_id }

                val inventarioPorId = supabase
                    .from("inventario")
                    .select()
                    .decodeList<Inventario>()
                    .associateBy { it.id }

                val pedidosNormalesUI = pedidos
                    .sortedByDescending { it.fecha }
                    .map { pedido ->
                        val productos = detalles[pedido.id]
                            .orEmpty()
                            .map { det ->
                                val descripcion = inventarioPorId[det.producto_id]?.descripcion
                                    ?: "Producto ${det.producto_id.take(6)}"
                                ProductoPedidoUI(
                                    descripcion = descripcion,
                                    cantidad = det.cantidad
                                )
                            }

                        MiPedidoUI(
                            id = pedido.id,
                            fecha = pedido.fecha,
                            estado = pedido.estado,
                            comentario = pedido.comentario.orEmpty(),
                            productos = productos,
                            esExtraordinario = false,
                            prioridad = ""
                        )
                    }

                val pedidosExtraordinarios = runCatching {
                    supabase
                        .from("pedidos_extraordinarios")
                        .select {
                            filter { eq("empleado_id", userId) }
                        }
                        .decodeList<PedidoExtraordinarioEmpleado>()
                }.getOrElse {
                    Log.w("PEDIDO", "No se pudieron cargar pedidos extraordinarios", it)
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
                    Log.w("PEDIDO", "No se pudieron cargar detalles extraordinarios", it)
                    emptyMap()
                }

                val pedidosExtraordinariosUI = pedidosExtraordinarios
                    .sortedByDescending { it.fecha }
                    .map { pedido ->
                        val productos = detallesExtraordinarios[pedido.id]
                            .orEmpty()
                            .map { detalle ->
                                val descripcion = detalle.stringOrNull("nombre")
                                    ?: detalle.stringOrNull("nombre_articulo")
                                    ?: detalle.stringOrNull("articulo")
                                    ?: detalle.stringOrNull("descripcion")
                                    ?: "Artículo extraordinario"
                                val cantidad = detalle.intOrNull("cantidad") ?: 1
                                val unidad = detalle.stringOrNull("unidad").orEmpty().trim()
                                val descripcionConUnidad = if (unidad.isBlank()) descripcion else "$descripcion ($unidad)"
                                ProductoPedidoUI(
                                    descripcion = descripcionConUnidad,
                                    cantidad = cantidad
                                )
                            }

                        MiPedidoUI(
                            id = pedido.id,
                            fecha = pedido.fecha,
                            estado = pedido.estado,
                            comentario = pedido.comentario.orEmpty(),
                            productos = productos,
                            esExtraordinario = true,
                            prioridad = pedido.prioridad.orEmpty()
                        )
                    }

                misPedidos = (pedidosNormalesUI + pedidosExtraordinariosUI)
                    .sortedByDescending { it.fecha }
            } catch (e: Exception) {
                Log.e("PEDIDO", "Error cargando mis pedidos", e)
            } finally {
                cargandoMisPedidos = false
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
            val unidad = normalizarTexto(item.unidad)
            val baseBusqueda = "$descripcion $clasificacion $unidad"

            terminos.all { termino -> baseBusqueda.contains(termino) }
        }
    }

    private fun normalizarTexto(valor: String?): String {
        if (valor.isNullOrBlank()) return ""
        return Normalizer.normalize(valor.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .trim()
    }
}

private fun JsonObject.stringOrNull(key: String): String? {
    val value: JsonElement = this[key] ?: return null
    return value.jsonPrimitive.contentOrNull
}

private fun JsonObject.intOrNull(key: String): Int? =
    this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

private fun JsonObject.longOrNull(key: String): Long? =
    this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
