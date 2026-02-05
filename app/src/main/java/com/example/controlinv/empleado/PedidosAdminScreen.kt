package com.example.controlinv.empleado
import android.R.attr.padding
import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
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
    ACEPTADOS,
    RECHAZADOS
}
@SuppressLint("SuspiciousIndentation")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedidosAdminScreen(
    viewModel: PedidoAdminViewModel = viewModel()
) {
    val pedidos by viewModel._listaPedidos.collectAsState()
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
                TextButton(onClick = { filtro = PedidoFiltro.ACEPTADOS }) {
                    Text("Aceptados")
                }
                TextButton(onClick = { filtro = PedidoFiltro.RECHAZADOS }) {
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
                    PedidoFiltro.ACEPTADOS -> pedidos.filter { it.estado == "ACEPTADO" }
                    PedidoFiltro.RECHAZADOS -> pedidos.filter { it.estado == "RECHAZADO" } }
                items(pedidosFiltrados,key = { it.id }) { pedido ->

                //items(pedidos, key = { it.id }) { pedido ->
                    if (filtro == PedidoFiltro.ENVIADO) {
                        Row {
                            Button(onClick = { viewModel.aceptarPedido(pedido.id) }) {
                                Text("Aceptar")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { viewModel.rechazarPedido(pedido.id) }) {
                                Text("Rechazar")
                            }
                        }
                    }

                    Divider()
                }
            }
        }

}

@Composable
fun PedidoItem(
    pedido: PedidoUI,
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

            pedido.productos.forEach { productoTexto ->
                Text(
                    text = "• $productoTexto",
                    style = MaterialTheme.typography.bodySmall
                )
            }



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

