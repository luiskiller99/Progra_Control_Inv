package com.example.controlinv.empleado

import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val formatoFechaFiltro: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

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

private fun formatearPrioridadAdmin(prioridad: String): String =
    prioridad.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun fechaPedidoLocal(fecha: String): LocalDate? =
    runCatching { LocalDate.parse(fecha.trim().take(10)) }.getOrNull()

enum class ExportacionPedidosTipo(
    val etiqueta: String,
    val sufijoArchivo: String
) {
    TODOS("Todos", "todos"),
    PENDIENTES("Pendientes", "pendientes"),
    EXTRAORDINARIOS("Extraordinarios", "extraordinarios"),
    ACEPTADOS("Aceptados", "aceptados"),
    RECHAZADOS("Rechazados", "rechazados")
}

private fun filtrarPedidosPorTipo(
    pedidos: List<PedidoUI>,
    filtro: PedidoFiltro
): List<PedidoUI> = when (filtro) {
    PedidoFiltro.ENVIADO -> pedidos.filter {
        !it.esExtraordinario && it.estado.equals("ENVIADO", ignoreCase = true)
    }
    PedidoFiltro.EXTRAORDINARIO -> pedidos.filter { it.esExtraordinario }
    PedidoFiltro.ACEPTADO -> pedidos.filter { it.estado.equals("ACEPTADO", ignoreCase = true) }
    PedidoFiltro.RECHAZADO -> pedidos.filter { it.estado.equals("RECHAZADO", ignoreCase = true) }
}

private fun filtrarPedidosPorTipo(
    pedidos: List<PedidoUI>,
    tipo: ExportacionPedidosTipo
): List<PedidoUI> = when (tipo) {
    ExportacionPedidosTipo.TODOS -> pedidos
    ExportacionPedidosTipo.PENDIENTES -> filtrarPedidosPorTipo(pedidos, PedidoFiltro.ENVIADO)
    ExportacionPedidosTipo.EXTRAORDINARIOS -> filtrarPedidosPorTipo(pedidos, PedidoFiltro.EXTRAORDINARIO)
    ExportacionPedidosTipo.ACEPTADOS -> filtrarPedidosPorTipo(pedidos, PedidoFiltro.ACEPTADO)
    ExportacionPedidosTipo.RECHAZADOS -> filtrarPedidosPorTipo(pedidos, PedidoFiltro.RECHAZADO)
}

@RequiresApi(Build.VERSION_CODES.Q)
fun exportarPedidosCsv(
    context: Context,
    pedidos: List<PedidoUI>,
    tipo: ExportacionPedidosTipo
) {
    val pedidosExportables = filtrarPedidosPorTipo(pedidos, tipo)
    if (pedidosExportables.isEmpty()) {
        Toast.makeText(
            context,
            "No hay pedidos en ${tipo.etiqueta.lowercase()} para exportar",
            Toast.LENGTH_SHORT
        ).show()
        return
    }

    val fecha = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val nombreArchivo = "pedidos_${tipo.sufijoArchivo}_$fecha.csv"

    val contenido = buildString {
        appendLine("empleado,id_pedido,fecha,estado,tipo,prioridad,comentario,codigo,descripcion,cantidad")
        pedidosExportables.forEach { pedido ->
            val empleado = pedido.empleadoEmail
            val tipoPedido = if (pedido.esExtraordinario) "EXTRAORDINARIO" else "NORMAL"
            val prioridad = if (pedido.esExtraordinario) formatearPrioridadAdmin(pedido.prioridad) else ""
            if (pedido.productos.isEmpty()) {
                appendLine(
                    listOf(
                        escaparCsv(empleado),
                        escaparCsv(idPedidoCorto(pedido.id)),
                        escaparCsv(pedido.fecha.replace("T", " ").take(16)),
                        escaparCsv(pedido.estado),
                        escaparCsv(tipoPedido),
                        escaparCsv(prioridad),
                        escaparCsv(pedido.comentario),
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
                            escaparCsv(pedido.fecha.replace("T", " ").take(16)),
                            escaparCsv(pedido.estado),
                            escaparCsv(tipoPedido),
                            escaparCsv(prioridad),
                            escaparCsv(pedido.comentario),
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
        Toast.makeText(
            context,
            "Archivo ${tipo.etiqueta.lowercase()} guardado en Descargas",
            Toast.LENGTH_LONG
        ).show()
    }.onFailure {
        Toast.makeText(context, "Error al exportar: ${it.message}", Toast.LENGTH_LONG).show()
    }
}

enum class PedidoFiltro {
    ENVIADO,
    EXTRAORDINARIO,
    ACEPTADO,
    RECHAZADO
}

private enum class AccionPedidoExtraordinario {
    ACEPTAR,
    RECHAZAR
}

private data class ConfirmacionExtraordinario(
    val pedidoId: String,
    val accion: AccionPedidoExtraordinario
)

@Composable
private fun CompactDateFilterBar(
    fechaInicio: LocalDate?,
    fechaFin: LocalDate?,
    onFechaInicioChange: (LocalDate?) -> Unit,
    onFechaFinChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Fecha:",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(end = 8.dp)
        )
        CompactDateField(
            label = "Desde",
            value = fechaInicio,
            onValueChange = onFechaInicioChange
        )
        Spacer(Modifier.width(8.dp))
        CompactDateField(
            label = "Hasta",
            value = fechaFin,
            onValueChange = onFechaFinChange
        )
        if (fechaInicio != null || fechaFin != null) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                onFechaInicioChange(null)
                onFechaFinChange(null)
            }) {
                Text("Limpiar")
            }
        }
    }
}

@Composable
private fun CompactDateField(
    label: String,
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit
) {
    val context = LocalContext.current
    val calendar = remember(value) {
        Calendar.getInstance().apply {
            value?.let {
                set(it.year, it.monthValue - 1, it.dayOfMonth)
            }
        }
    }

    OutlinedButton(
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    onValueChange(LocalDate.of(year, month + 1, dayOfMonth))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    ) {
        Text(
            text = "$label ${value?.format(formatoFechaFiltro) ?: "--/--/----"}",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun PedidosAdminScreen(
    viewModel: PedidoAdminViewModel = viewModel()
) {
    val pedidos by viewModel.listaPedidos.collectAsState()
    var filtro by remember { mutableStateOf(PedidoFiltro.ENVIADO) }
    var fechaInicio by remember { mutableStateOf<LocalDate?>(null) }
    var fechaFin by remember { mutableStateOf<LocalDate?>(null) }
    var confirmacionExtraordinario by remember { mutableStateOf<ConfirmacionExtraordinario?>(null) }

    val fechaMin = when {
        fechaInicio != null && fechaFin != null && fechaInicio!! <= fechaFin!! -> fechaInicio
        fechaInicio != null && fechaFin != null -> fechaFin
        else -> fechaInicio ?: fechaFin
    }
    val fechaMax = when {
        fechaInicio != null && fechaFin != null && fechaInicio!! <= fechaFin!! -> fechaFin
        fechaInicio != null && fechaFin != null -> fechaInicio
        else -> fechaFin ?: fechaInicio
    }

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
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                ) {
                    TextButton(onClick = { filtro = PedidoFiltro.ENVIADO }) {
                        Text("Pendientes")
                    }
                    TextButton(onClick = { filtro = PedidoFiltro.EXTRAORDINARIO }) {
                        Text("Extraordinarios")
                    }
                    TextButton(onClick = { filtro = PedidoFiltro.ACEPTADO }) {
                        Text("Aceptados")
                    }
                    TextButton(onClick = { filtro = PedidoFiltro.RECHAZADO }) {
                        Text("Rechazados")
                    }
                }

                CompactDateFilterBar(
                    fechaInicio = fechaInicio,
                    fechaFin = fechaFin,
                    onFechaInicioChange = { fechaInicio = it },
                    onFechaFinChange = { fechaFin = it }
                )

                val pedidosFiltrados = filtrarPedidosPorTipo(pedidos, filtro).filter { pedido ->
                    val fechaPedido = fechaPedidoLocal(pedido.fecha)
                    val cumpleInicio = fechaMin?.let { inicio ->
                        fechaPedido?.let { !it.isBefore(inicio) } ?: false
                    } ?: true
                    val cumpleFin = fechaMax?.let { fin ->
                        fechaPedido?.let { !it.isAfter(fin) } ?: false
                    } ?: true
                    cumpleInicio && cumpleFin
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
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(pedidosFiltrados, key = { it.id }) { pedido ->
                            val mostrarAcciones = pedido.estado.equals("ENVIADO", ignoreCase = true) &&
                                (
                                    (filtro == PedidoFiltro.ENVIADO && !pedido.esExtraordinario) ||
                                        (filtro == PedidoFiltro.EXTRAORDINARIO && pedido.esExtraordinario)
                                    )
                            PedidoItem(
                                pedido = pedido,
                                mostrarAcciones = mostrarAcciones,
                                onAceptar = {
                                    if (pedido.estado.equals("ENVIADO", ignoreCase = true)) {
                                        if (pedido.esExtraordinario) {
                                            confirmacionExtraordinario = ConfirmacionExtraordinario(
                                                pedidoId = pedido.id,
                                                accion = AccionPedidoExtraordinario.ACEPTAR
                                            )
                                        } else {
                                            viewModel.aceptarPedido(pedido.id)
                                        }
                                    }
                                },
                                onRechazar = {
                                    if (pedido.estado.equals("ENVIADO", ignoreCase = true)) {
                                        if (pedido.esExtraordinario) {
                                            confirmacionExtraordinario = ConfirmacionExtraordinario(
                                                pedidoId = pedido.id,
                                                accion = AccionPedidoExtraordinario.RECHAZAR
                                            )
                                        } else {
                                            viewModel.rechazarPedido(pedido.id)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        val confirmacion = confirmacionExtraordinario
        if (confirmacion != null) {
            val accionTexto = if (confirmacion.accion == AccionPedidoExtraordinario.ACEPTAR) {
                "aceptar"
            } else {
                "rechazar"
            }
            AlertDialog(
                onDismissRequest = { confirmacionExtraordinario = null },
                title = { Text("Confirmar pedido extraordinario") },
                text = {
                    Text("¿Está seguro de $accionTexto este pedido extraordinario?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            when (confirmacion.accion) {
                                AccionPedidoExtraordinario.ACEPTAR ->
                                    viewModel.aceptarPedidoExtraordinario(confirmacion.pedidoId)

                                AccionPedidoExtraordinario.RECHAZAR ->
                                    viewModel.rechazarPedidoExtraordinario(confirmacion.pedidoId)
                            }
                            confirmacionExtraordinario = null
                        }
                    ) {
                        Text("Sí, $accionTexto")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { confirmacionExtraordinario = null }) {
                        Text("Cancelar")
                    }
                }
            )
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
            Text(
                pedido.empleadoEmail,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "ID: ${idPedidoCorto(pedido.id)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Fecha: $fechaCorta",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Estado: ${pedido.estado}",
                style = MaterialTheme.typography.bodySmall
            )
            if (pedido.esExtraordinario) {
                Text(
                    "Tipo: Extraordinario",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (pedido.esExtraordinario && pedido.prioridad.isNotBlank()) {
                Text(
                    "Prioridad: ${formatearPrioridadAdmin(pedido.prioridad)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (pedido.comentario.isNotBlank()) {
                Text(
                    "Comentario: ${pedido.comentario}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(8.dp))

            Text("Productos:", style = MaterialTheme.typography.labelMedium)

            pedido.productos.forEach { productoTexto ->
                Text(
                    text = "• $productoTexto • ",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (mostrarAcciones) {
                Spacer(Modifier.height(12.dp))

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
