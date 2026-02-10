package com.example.controlinv

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.controlinv.auth.supabase
import com.example.controlinv.inventario.InventarioLogRaw
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val rojoAntes = Color(0xFFD32F2F)
private val rojoFondo = Color(0x1AD32F2F)
private val verdeDespues = Color(0xFF2E7D32)
private val verdeFondo = Color(0x1A2E7D32)

/** Intenta formatear un string JSON de inventario a líneas legibles (campo: valor). */
private fun formatearItemLog(raw: String): String {
    return try {
        val obj = Json.parseToJsonElement(raw).jsonObject
        buildString {
            obj.entries.forEach { (key, value) ->
                val nombre = when (key) {
                    "id" -> "ID"
                    "codigo" -> "Código"
                    "descripcion" -> "Descripción"
                    "cantidad" -> "Cantidad"
                    "clasificacion" -> "Clasificación"
                    "extra1" -> "Extra 1"
                    "extra2" -> "Extra 2"
                    else -> key
                }
                val texto = value.jsonPrimitive.content
                if (texto != "null" && texto.isNotBlank()) {
                    append("$nombre: $texto\n")
                }
            }
        }.trim().ifEmpty { raw }
    } catch (_: Exception) {
        raw
    }
}

/** Formatea la fecha ISO para mostrarla más corta. */
private fun formatearFecha(iso: String): String {
    return try {
        iso.replace("T", " ").substring(0, minOf(19, iso.length))
    } catch (_: Exception) {
        iso
    }
}

@Composable
fun InventarioLogsScreen() {
    val logs = remember { mutableStateListOf<InventarioLogRaw>() }

    LaunchedEffect(Unit) {
        try {
            logs.clear()
            val result = supabase
                .from("inventario_logs")
                .select()
                .decodeList<InventarioLogRaw>()
                .sortedByDescending { it.fecha }
            logs.addAll(result)
        } catch (e: Exception) {
            Log.e("LOGS", "Error cargando logs", e)
        }
    }

    if (logs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No hay ediciones registradas")
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs, key = { "${it.producto_id}-${it.fecha}" }) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Admin: ${log.admin_email}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                formatearFecha(log.fecha),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "Producto ID: ${log.producto_id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(2.dp, rojoAntes, MaterialTheme.shapes.small)
                                    .background(rojoFondo)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    "Valor anterior",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = rojoAntes
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    formatearItemLog(log.item_anterior),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(2.dp, verdeDespues, MaterialTheme.shapes.small)
                                    .background(verdeFondo)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    "Valor nuevo",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = verdeDespues
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    formatearItemLog(log.item_nuevo),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}




