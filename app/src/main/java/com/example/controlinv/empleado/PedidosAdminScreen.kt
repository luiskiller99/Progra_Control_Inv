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

enum class PedidoFiltro {
    ENVIADO,
    ACEPTADO,
    RECHAZADO
}
@Composable
fun PedidosAdminScreen(
    viewModel: PedidoAdminViewModel = viewModel()
) {
    val pedidos by viewModel.listaPedidos.collectAsState()
    var filtro by remember { mutableStateOf(PedidoFiltro.ENVIADO) }
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
            LazyColumn(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxSize()
            ) {
                val pedidosFiltrados = when (filtro) {
                    PedidoFiltro.ENVIADO -> pedidos.filter { it.estado == "ENVIADO" }
                    PedidoFiltro.ACEPTADO -> pedidos.filter { it.estado == "ACEPTADO" }
                    PedidoFiltro.RECHAZADO -> pedidos.filter { it.estado == "RECHAZADO" }
                }
                items(pedidosFiltrados, key = { it.id }) { pedido ->
                    PedidoItem(
                        pedido = pedido,
                        mostrarAcciones = filtro == PedidoFiltro.ENVIADO,
                        onAceptar = {
                            if (pedido.estado == "ENVIADO") {
                                viewModel.aceptarPedido(pedido.id)
                            }
                        },
                        onRechazar = {
                            if (pedido.estado == "ENVIADO") {
                                viewModel.rechazarPedido(pedido.id)
                            }
                        }
                    )
                }
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
            Text(
                pedido.empleadoEmail ?: "Empleado desconocido",
                style = MaterialTheme.typography.titleMedium
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

            Spacer(Modifier.height(8.dp))

            // 📦 Detalle
            Text("Productos:", style = MaterialTheme.typography.labelMedium)
            //Text("* ${pedido.productos} *", style = MaterialTheme.typography.labelMedium)


            pedido.productos.forEach { productoTexto ->
                Text(
                    text = "• $productoTexto • ",
                    style = MaterialTheme.typography.bodySmall
                )
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

