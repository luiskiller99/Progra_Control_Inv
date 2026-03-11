package com.example.controlinv.empleado

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class ExtraordinarioFiltro {
    PENDIENTE,
    ACEPTADO,
    RECHAZADO
}

private fun PedidoExtraordinarioUI.esPendiente(): Boolean =
    estado.equals("ENVIADO", ignoreCase = true) || estado.equals("PENDIENTE", ignoreCase = true)

private fun PedidoExtraordinarioUI.esAceptado(): Boolean =
    estado.equals("ACEPTADO", ignoreCase = true) || estado.equals("APROBADO", ignoreCase = true)

private fun PedidoExtraordinarioUI.esRechazado(): Boolean =
    estado.equals("RECHAZADO", ignoreCase = true)

@Composable
fun PedidosExtraordinariosAdminScreen(
    viewModel: PedidoExtraordinarioAdminViewModel = viewModel()
) {
    val pedidos by viewModel.listaPedidos.collectAsState()
    var filtro by remember { mutableStateOf(ExtraordinarioFiltro.PENDIENTE) }

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
                Text("No hay pedidos extraordinarios")
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
                    TextButton(onClick = { filtro = ExtraordinarioFiltro.PENDIENTE }) {
                        Text("Pendientes")
                    }
                    TextButton(onClick = { filtro = ExtraordinarioFiltro.ACEPTADO }) {
                        Text("Aceptados")
                    }
                    TextButton(onClick = { filtro = ExtraordinarioFiltro.RECHAZADO }) {
                        Text("Rechazados")
                    }
                }

                val pedidosFiltrados = when (filtro) {
                    ExtraordinarioFiltro.PENDIENTE -> pedidos.filter { it.esPendiente() }
                    ExtraordinarioFiltro.ACEPTADO -> pedidos.filter { it.esAceptado() }
                    ExtraordinarioFiltro.RECHAZADO -> pedidos.filter { it.esRechazado() }
                }

                if (pedidosFiltrados.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No hay pedidos extraordinarios para este filtro")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(pedidosFiltrados, key = { it.id }) { pedido ->
                            PedidoExtraordinarioItem(
                                pedido = pedido,
                                mostrarAcciones = filtro == ExtraordinarioFiltro.PENDIENTE,
                                onAceptar = { if (pedido.esPendiente()) viewModel.aceptarPedido(pedido.id) },
                                onRechazar = { if (pedido.esPendiente()) viewModel.rechazarPedido(pedido.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PedidoExtraordinarioItem(
    pedido: PedidoExtraordinarioUI,
    mostrarAcciones: Boolean,
    onAceptar: () -> Unit,
    onRechazar: () -> Unit
) {
    val fechaCorta = pedido.fecha.replace("T", " ").take(16)

    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                pedido.empleadoEmail.ifBlank { "Empleado desconocido" },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "ID: ${pedido.id.take(8)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            Text("Fecha: $fechaCorta", style = MaterialTheme.typography.bodySmall)
            Text("Estado: ${pedido.estado}", style = MaterialTheme.typography.bodySmall)
            Text("Prioridad: ${pedido.prioridad}", style = MaterialTheme.typography.bodySmall)

            if (pedido.comentario.isNotBlank()) {
                Text("Comentario: ${pedido.comentario}", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))
            Text("Productos:", style = MaterialTheme.typography.labelMedium)

            if (pedido.productos.isEmpty()) {
                Text(
                    text = "• Sin detalle de artículos extraordinarios •",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                pedido.productos.forEach { producto ->
                    Text(
                        text = "• ${producto.cantidad} x ${producto.descripcion}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
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
