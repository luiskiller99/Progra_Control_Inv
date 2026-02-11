package com.example.controlinv.inventario

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.controlinv.auth.SUPABASE_KEY
import com.example.controlinv.auth.SUPABASE_URL
import com.example.controlinv.auth.supabase
import com.example.controlinv.inventario.model.Inventario
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import kotlinx.coroutines.launch
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
                val urlImagen = if (imagenBytes != null) {
                    subirImagenProducto(imagenBytes, extension)
                } else {
                    null
                }

                if (imagenBytes != null && urlImagen == null) {
                    onError("No se pudo subir la imagen a Supabase. Intenta de nuevo.")
                    return@launch
                }

                val creado = insertarInventario(item.copy(imagen = urlImagen ?: item.imagen))
                inventarioCompleto = inventarioCompleto + creado
                inventario = inventarioCompleto.map { it.copy() }
                onOk()
            } catch (e: Exception) {
                Log.e("INVENTARIO", "Error agregando inventario", e)
                onError("No se pudo guardar el producto: ${e.message}")
                onError("No se pudo guardar el producto.")
            }
        }
    }
    fun eliminar(id: String) {
        viewModelScope.launch {
            eliminarInventario(id)
            inventarioCompleto = inventarioCompleto.filter { it.id != id }
            inventario = inventario.filter { it.id != id }
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

    private suspend fun subirImagenProducto(
        imagenBytes: ByteArray,
        extension: String
    ): Result<String> {
        return try {
            val bucket = "productos"
            val extensionNormalizada = when (extension.lowercase()) {
                "jpeg" -> "jpg"
                else -> extension.lowercase()
            }
            val filePath = "inventario/${UUID.randomUUID()}.$extensionNormalizada"
            val endpoint = "$SUPABASE_URL/storage/v1/object/$bucket/$filePath"

            val token = supabase.auth.currentSessionOrNull()?.accessToken
            val authToken = token ?: SUPABASE_KEY

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("apikey", SUPABASE_KEY)
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("Content-Type", "image/$extensionNormalizada")
                setRequestProperty("x-upsert", "true")
            }

            connection.outputStream.use { it.write(imagenBytes) }

            val code = connection.responseCode
            if (code in 200..299) {
                Result.success("$SUPABASE_URL/storage/v1/object/public/$bucket/$filePath")
            } else {
                val errorBody = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                Result.failure(Exception("HTTP $code ${errorBody ?: "sin detalle"}"))
            }
        } catch (e: Exception) {
            Log.e("INVENTARIO", "Error subiendo imagen", e)
            Result.failure(e)
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