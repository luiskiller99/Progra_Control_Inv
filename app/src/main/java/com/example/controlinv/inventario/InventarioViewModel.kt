package com.example.controlinv.inventario

import android.util.Log
import com.example.controlinv.auth.SUPABASE_KEY
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.controlinv.empleado.DetallePedido
import com.example.controlinv.inventario.model.Inventario
import com.example.controlinv.auth.SUPABASE_URL
import com.example.controlinv.auth.supabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class InventarioViewModel : ViewModel() {
    var cargando by mutableStateOf(false)
        private set
    private var inventarioCompleto = listOf<Inventario>()
    var inventario by mutableStateOf<List<Inventario>>(emptyList())
        private set
    private var ultimoErrorSubida: String? = null

    init {
        cargarInventario()
    }

    private fun cargarInventario() {
        viewModelScope.launch {
            cargando = true

            inventarioCompleto = supabase
                .from("inventario")
                .select()
                .decodeList()

            // Copia para edición
            inventario = inventarioCompleto.map { it.copy() }

            cargando = false
        }
    }
    fun filtrar(texto: String) {
        inventario = if (texto.isBlank()) {
            inventarioCompleto.map { it.copy() }
        } else {
            inventarioCompleto
                .filter {
                    it.codigo?.contains(texto, true) == true ||
                            it.descripcion?.contains(texto, true) == true ||
                            it.clasificacion?.contains(texto, true) == true
                }
                .map { it.copy() }
        }
    }
    fun agregar(
        item: Inventario,
        imagenBytes: ByteArray? = null,
        extension: String = "jpg",
        onOk: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val urlImagen: String? = if (imagenBytes != null) {
                    subirImagenProducto(imagenBytes, extension)
                } else {
                    null
                }

                if (imagenBytes != null && urlImagen == null) {
                    val detalle = ultimoErrorSubida ?: "Sin detalle"
                    Log.e("INVENTARIO_UPLOAD", "Fallo subida imagen: $detalle")
                    onError("No se pudo subir la imagen a Supabase: $detalle")
                    return@launch
                }

                val creado = insertarInventario(item.copy(imagen = urlImagen ?: item.imagen))
                inventarioCompleto = inventarioCompleto + creado
                inventario = inventarioCompleto.map { it.copy() }
                onOk()
            } catch (e: Exception) {
                Log.e("INVENTARIO", "Error agregando inventario", e)
                onError("No se pudo guardar el producto: ${e.message}")
            }
        }
    }
    fun eliminar(
        id: String,
        onOk: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val relacionesPedidos = supabase
                    .from("pedido_detalle")
                    .select {
                        filter { eq("producto_id", id) }
                    }
                    .decodeList<DetallePedido>()
                    .size

                if (relacionesPedidos > 0) {
                    onError("No se puede eliminar: el producto está en $relacionesPedidos pedido(s).")
                    return@launch
                }

                val imagenUrl = inventarioCompleto.find { it.id == id }?.imagen

                eliminarInventario(id)
                eliminarImagenProductoStorage(imagenUrl)

                inventarioCompleto = inventarioCompleto.filter { it.id != id }
                inventario = inventario.filter { it.id != id }
                onOk()
            } catch (e: Exception) {
                Log.e("INVENTARIO", "Error eliminando inventario", e)
                val mensaje = when {
                    (e.message ?: "").contains("foreign key", ignoreCase = true) ->
                        "No se puede eliminar: el producto está relacionado con pedidos."
                    else -> "No se pudo eliminar el producto: ${e.message ?: "error desconocido"}"
                }
                onError(mensaje)
            }
        }
    }
    fun guardarFila(itemNuevo: Inventario) {
        val itemAnterior = inventario.find { it.id == itemNuevo.id } ?: return
        viewModelScope.launch {
            try {
                // 1️⃣ actualizar inventario
                supabase
                    .from("inventario")
                    .update(itemNuevo) {
                        filter { eq("id", itemNuevo.id!!) }
                    }
                // 2️⃣ obtener email del admin logueado
                val adminEmail =
                    supabase.auth.currentUserOrNull()?.email ?: "desconocido@local"
                // 3️⃣ guardar log (ANTES vs DESPUÉS)
                // convertir objetos a JSON
                val itemAnteriorJson = Json.encodeToString(itemAnterior)
                val itemNuevoJson = Json.encodeToString(itemNuevo)
                // 4️⃣ insertar log
                supabase
                    .from("inventario_logs")
                    .insert(
                        mapOf(
                            "producto_id" to itemNuevo.id,
                            "admin_email" to adminEmail,
                            "item_anterior" to itemAnteriorJson,
                            "item_nuevo" to itemNuevoJson
                        )
                    )
                // 4️⃣ refrescar lista
                cargarInventario()
            } catch (e: Exception) {
                Log.e("INVENTARIO", "Error guardando inventario", e)
            }
        }
    }
    fun descartarFila(id: String) {
        val original = inventarioCompleto.find { it.id == id } ?: return
        inventario = inventario.map {
            if (it.id == id) original.copy() else it
        }
    }

    private suspend fun eliminarImagenProductoStorage(imagenUrl: String?) {
        if (imagenUrl.isNullOrBlank()) return

        val basePrefix = "$SUPABASE_URL/storage/v1/object/public/productos/"
        val path = when {
            imagenUrl.startsWith(basePrefix) -> imagenUrl.removePrefix(basePrefix)
            imagenUrl.contains("/storage/v1/object/public/productos/") ->
                imagenUrl.substringAfter("/storage/v1/object/public/productos/")
            else -> imagenUrl.removePrefix("/")
                .removePrefix("productos/")
                .removePrefix("object/public/productos/")
        }.substringBefore("?")

        if (path.isBlank()) return

        withContext(Dispatchers.IO) {
            runCatching {
                val token = supabase.auth.currentSessionOrNull()?.accessToken ?: return@runCatching
                val endpoint = "$SUPABASE_URL/storage/v1/object/productos/$path"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    setRequestProperty("apikey", SUPABASE_KEY)
                    setRequestProperty("Authorization", "Bearer $token")
                }
                val code = connection.responseCode
                if (code !in 200..299 && code != 404) {
                    val body = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.w("INVENTARIO_UPLOAD", "No se pudo borrar imagen storage ($code): ${body ?: "sin detalle"}")
                }
            }.onFailure {
                Log.w("INVENTARIO_UPLOAD", "Error borrando imagen storage", it)
            }
        }
    }

    private suspend fun subirImagenProducto(
        imagenBytes: ByteArray,
        extension: String
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
            ultimoErrorSubida = null
            val bucket = "productos"
            val extensionNormalizada = extension.lowercase()
            val mimeType = when (extensionNormalizada) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "application/octet-stream"
            }
            val filePath = "inventario/${UUID.randomUUID()}.$extensionNormalizada"
            val endpoint = "$SUPABASE_URL/storage/v1/object/$bucket/$filePath"

            val authToken = supabase.auth.currentSessionOrNull()?.accessToken
            if (authToken.isNullOrBlank()) {
                ultimoErrorSubida = "No hay sesión autenticada para subir imagen"
                Log.e("INVENTARIO_UPLOAD", ultimoErrorSubida ?: "")
                return null
            }

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("apikey", SUPABASE_KEY)
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("Content-Type", mimeType)
                setRequestProperty("x-upsert", "true")
            }

            connection.outputStream.use { it.write(imagenBytes) }

            val code = connection.responseCode
            if (code in 200..299) {
                Log.i("INVENTARIO_UPLOAD", "Imagen subida correctamente: $filePath")
                "$SUPABASE_URL/storage/v1/object/public/$bucket/$filePath"
            } else {
                val errorBody = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                ultimoErrorSubida = "HTTP $code ${errorBody ?: "sin detalle"}"
                Log.e("INVENTARIO_UPLOAD", "Error upload imagen: ${ultimoErrorSubida}")
                null
            }
            } catch (e: Exception) {
                ultimoErrorSubida = "${e::class.java.simpleName}: ${e.message ?: "sin detalle"}"
                Log.e("INVENTARIO_UPLOAD", "Error subiendo imagen", e)
                null
            }
        }
    }

}
suspend fun insertarInventario(item: Inventario): Inventario {
    return supabase
        .from("inventario")
        .insert(item) {
            select()
        }
        .decodeSingle()
}
suspend fun eliminarInventario(id: String) {
    supabase.from("inventario")
        .delete {
            filter{
                eq("id", id)
            }
        }

}
suspend fun logout() {
    supabase.auth.signOut()
}