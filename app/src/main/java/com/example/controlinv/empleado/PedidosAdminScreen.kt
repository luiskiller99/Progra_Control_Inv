package com.example.controlinv.empleado

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun idPedidoCorto(id: String?): String {
    if (id.isNullOrBlank()) return "000000"
    val soloDigitos = id.filter { it.isDigit() }
    if (soloDigitos.length >= 6) return soloDigitos.takeLast(6)
    val hash6 = (id.hashCode().toLong() and 0xffffffffL) % 1_000_000L
    return hash6.toString().padStart(6, '0')
}


private data class ProductoExportado(
    val codigo: String,
    val descripcion: String,
    val cantidad: String
)

private fun parseProducto(productoTexto: String): ProductoExportado {
    val regex = Regex("""(\d+)\s*x\s*\[(.*?)]\s*(.*)""")
    val match = regex.find(productoTexto.trim())
    return if (match != null) {
        ProductoExportado(
            codigo = match.groupValues[2].ifBlank { "N/A" },
            descripcion = match.groupValues[3].ifBlank { "Producto" },
            cantidad = match.groupValues[1]
        )
    } else {
        ProductoExportado(codigo = "N/A", descripcion = productoTexto, cantidad = "")
    }
}

private fun escaparCsv(texto: String): String =
    "\"" + texto.replace("\"", "\"\"") + "\""

@RequiresApi(Build.VERSION_CODES.Q)
private fun exportarPedidosCsv(context: Context, pedidos: List<PedidoUI>) {
    val pedidosAceptados = pedidos.filter { it.estado.equals("ACEPTADO", ignoreCase = true) }
    if (pedidosAceptados.isEmpty()) {
        Toast.makeText(context, "No hay pedidos aceptados para exportar", Toast.LENGTH_SHORT).show()
        return
    }

    val fecha = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val nombreArchivo = "pedidos_bodega_$fecha.csv"

    val contenido = buildString {
        appendLine("empleado,id_pedido,estado,codigo,descripcion,cantidad")
        pedidosAceptados.forEach { pedido ->
            val empleado = pedido.empleadoEmail
            if (pedido.productos.isEmpty()) {
                appendLine(
                    listOf(
                        escaparCsv(empleado),
                        escaparCsv(idPedidoCorto(pedido.id)),
                        escaparCsv(pedido.estado),
                        escaparCsv(""),
                        escaparCsv(""),
                        escaparCsv("")
                    ).joinToString(",")
                )
            } else {
                pedido.productos.forEach { productoTexto ->
                    val p = parseProducto(productoTexto)
                    appendLine(
                        listOf(
                            escaparCsv(empleado),
                            escaparCsv(idPedidoCorto(pedido.id)),
                            escaparCsv(pedido.estado),
                            escaparCsv(p.codigo),
                            escaparCsv(p.descripcion),
                            escaparCsv(p.cantidad)
                        ).joinToString(",")
                    )
                }
            }
        }
    }

    runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, nombreArchivo)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("No se pudo crear el archivo")

        resolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
            writer?.write(contenido)
        }
    }.onSuccess {
        Toast.makeText(context, "CSV guardado en Descargas", Toast.LENGTH_LONG).show()
    }.onFailure {
        Toast.makeText(context, "Error al exportar: ${it.message}", Toast.LENGTH_LONG).show()
    }
}

enum class PedidoFiltro {
    ENVIADO,
    ACEPTADO,
    RECHAZADO
}

private fun PedidoUI.esPendiente(): Boolean =
    estado.equals("ENVIADO", ignoreCase = true) || estado.equals("PENDIENTE", ignoreCase = true)

private fun PedidoUI.esAceptado(): Boolean =
    estado.equals("ACEPTADO", ignoreCase = true) || estado.equals("APROBADO", ignoreCase = true)

private fun PedidoUI.esRechazado(): Boolean =
    estado.equals("RECHAZADO", ignoreCase = true)

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun PedidosAdminScreen(
    viewModel: PedidoAdminViewModel = viewModel()
) {
    val pedidos by viewModel.listaPedidos.collectAsState()
    val context = LocalContext.current
    var filtro by remember { mutableStateOf(PedidoFiltro.ENVIADO) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (viewModel.cargando) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
        if (pedidos.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No hay pedidos")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { filtro = PedidoFiltro.ENVIADO }) {
                        Text("Pendientes")
                    }
                    TextButton(onClick = { filtro = PedidoFiltro.ACEPTADO}) {
                        Text("Aceptados")
                    }
                    TextButton(onClick = { filtro = PedidoFiltro.RECHAZADO }) {
                        Text("Rechazados")
                    }
                }
                val pedidosFiltrados = when (filtro) {
                    PedidoFiltro.ENVIADO -> pedidos.filter { it.esPendiente() }
                    PedidoFiltro.ACEPTADO -> pedidos.filter { it.esAceptado() }
                    PedidoFiltro.RECHAZADO -> pedidos.filter { it.esRechazado() }
                }

                if (pedidosFiltrados.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No hay pedidos para este filtro")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        items(pedidosFiltrados, key = { it.id }) { pedido ->
                            PedidoItem(
                                pedido = pedido,
                                mostrarAcciones = filtro == PedidoFiltro.ENVIADO,
                                onAceptar = {
                                    if (pedido.esPendiente()) {
                                        viewModel.aceptarPedido(
                                            pedidoId = pedido.id,
                                            esExtraordinario = pedido.esExtraordinario
                                        )
                                    }
                                },
                                onRechazar = {
                                    if (pedido.esPendiente()) {
                                        viewModel.rechazarPedido(
                                            pedidoId = pedido.id,
                                            esExtraordinario = pedido.esExtraordinario
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { exportarPedidosCsv(context, pedidos) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Download, contentDescription = "Descargar pedidos")
        }
    }

}

@Composable
fun PedidoItem(
    pedido: PedidoUI,
    mostrarAcciones: Boolean,
    onAceptar: () -> Unit,
    onRechazar: () -> Unit
) {
    val fechaCorta = pedido.fecha
        .replace("T", " ")
        .substring(0, 16)

    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // 📧 Email
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    pedido.empleadoEmail ?: "Empleado desconocido",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                "ID: ${idPedidoCorto(pedido.id)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            // 📅 Fecha + Estado
            Text(
                text = "Fecha: $fechaCorta",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Estado: ${pedido.estado}",
                style = MaterialTheme.typography.bodySmall
            )
            if (pedido.comentario.isNotBlank()) {
                Text(
                    "Comentario: ${pedido.comentario}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(8.dp))

            // 📦 Detalle
            Text("Productos:", style = MaterialTheme.typography.labelMedium)
            //Text("* ${pedido.productos} *", style = MaterialTheme.typography.labelMedium)


            if (pedido.productos.isEmpty()) {
                Text(
                    text = if (pedido.esExtraordinario) {
                        "• Sin detalle de artículos extraordinarios •"
                    } else {
                        "• Sin detalle de productos •"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                pedido.productos.forEach { productoTexto ->
                    Text(
                        text = "• $productoTexto • ",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }


            if (mostrarAcciones) {
                Spacer(Modifier.height(12.dp))

                // ✅❌ Botones
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onRechazar,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Rechazar")
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(onClick = onAceptar) {
                        Text("Aceptar")
                    }
                }
            }
        }
    }
}
