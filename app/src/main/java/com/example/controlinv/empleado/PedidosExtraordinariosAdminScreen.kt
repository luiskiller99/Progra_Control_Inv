package com.example.controlinv.empleado

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class ExtraordinarioFiltro { PENDIENTE, ACEPTADO, RECHAZADO }


private fun idPedidoCortoExtra(id: String?): String {
    if (id.isNullOrBlank()) return "000000"
    val soloDigitos = id.filter { it.isDigit() }
    if (soloDigitos.length >= 6) return soloDigitos.takeLast(6)
    val hash6 = (id.hashCode().toLong() and 0xffffffffL) % 1_000_000L
    return hash6.toString().padStart(6, '0')
}

private fun PedidoExtraordinarioUI.esPendiente(): Boolean =
    estado.equals("ENVIADO", ignoreCase = true) || estado.equals("PENDIENTE", ignoreCase = true)

private fun PedidoExtraordinarioUI.esAceptado(): Boolean =
    estado.equals("ACEPTADO", ignoreCase = true) || estado.equals("APROBADO", ignoreCase = true)

private fun PedidoExtraordinarioUI.esRechazado(): Boolean = estado.equals("RECHAZADO", ignoreCase = true)

@Composable
fun PedidosExtraordinariosAdminScreen(
    viewModel: PedidoExtraordinarioAdminViewModel = viewModel()
) {
    PedidosExtraordinariosAdminContent(viewModel)
}

@Composable
fun PedidosExtraordinariosAdminContent(
    viewModel: PedidoExtraordinarioAdminViewModel = viewModel()
) {
    val pedidos by viewModel.listaPedidos.collectAsState()
    var filtro by remember { mutableStateOf(ExtraordinarioFiltro.PENDIENTE) }

    if (viewModel.cargando) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
    }

    if (pedidos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No hay pedidos extraordinarios")
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row {
            TextButton(onClick = { filtro = ExtraordinarioFiltro.PENDIENTE }) { Text("Pendientes") }
            TextButton(onClick = { filtro = ExtraordinarioFiltro.ACEPTADO }) { Text("Aceptados") }
            TextButton(onClick = { filtro = ExtraordinarioFiltro.RECHAZADO }) { Text("Rechazados") }
        }

        val pedidosFiltrados = when (filtro) {
            ExtraordinarioFiltro.PENDIENTE -> pedidos.filter { it.esPendiente() }
            ExtraordinarioFiltro.ACEPTADO -> pedidos.filter { it.esAceptado() }
            ExtraordinarioFiltro.RECHAZADO -> pedidos.filter { it.esRechazado() }
        }

        if (pedidosFiltrados.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay pedidos extraordinarios para este filtro")
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(pedidosFiltrados, key = { it.id }) { pedido ->
                val pedidoUI = MiPedidoUI(
                    id = pedido.id,
                    fecha = pedido.fecha,
                    estado = pedido.estado,
                    comentario = pedido.comentario,
                    productos = pedido.productos
                )
                PedidoExtraordinarioAdminItem(
                    pedido = pedidoUI,
                    mostrarAcciones = filtro == ExtraordinarioFiltro.PENDIENTE,
                    onAceptar = { if (pedido.esPendiente()) viewModel.aceptarPedido(pedido.id) },
                    onRechazar = { if (pedido.esPendiente()) viewModel.rechazarPedido(pedido.id) }
                )
            }
        }
    }
}

@Composable
private fun PedidoExtraordinarioAdminItem(
    pedido: MiPedidoUI,
    mostrarAcciones: Boolean,
    onAceptar: () -> Unit,
    onRechazar: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ID: ${idPedidoCortoExtra(pedido.id)}", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(8.dp))
                Text("Estado: ${pedido.estado}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Fecha: ${pedido.fecha.replace("T", " ").take(16)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (pedido.productos.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                pedido.productos.forEach { prod ->
                    Text(
                        "• ${prod.cantidad} x ${prod.descripcion}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (pedido.comentario.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Comentario: ${pedido.comentario}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (mostrarAcciones) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onRechazar,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Rechazar") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onAceptar) { Text("Aceptar") }
                }
            }
        }
    }
}
