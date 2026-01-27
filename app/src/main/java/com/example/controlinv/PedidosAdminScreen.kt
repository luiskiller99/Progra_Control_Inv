package com.example.controlinv
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.Serializable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedidosAdminScreen(
    onBack: () -> Unit,
    onPedidoClick: (Pedido) -> Unit,
    viewModel: PedidoAdminViewModel = viewModel()
)
{
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pedidos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->

        if (viewModel.cargando) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        if (viewModel.pedidos.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No hay pedidos")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                items(viewModel.pedidos, key = { it.id }) { pedido ->
                    PedidoItem(
                        pedido = pedido,
                        onAceptar = {
                            viewModel.aceptarPedido(pedido.id)
                        },
                        onRechazar = {
                            viewModel.rechazarPedido(pedido.id)
                        }
                    )

                    Divider()
                }
            }
        }
    }
}

@Composable
fun PedidoItem(
    pedido: Pedido,
    onAceptar: () -> Unit,
    onRechazar: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // 📧 Email
            Text(
                pedido.emailEmpleado ?: "Empleado desconocido",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(4.dp))

            // 📅 Fecha + Estado
            Text(
                "Fecha: ${pedido.created_at}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Estado: ${pedido.estado}",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(8.dp))

            // 📦 Detalle
            Text("Productos:", style = MaterialTheme.typography.labelMedium)

            pedido.detalles.forEach {
                Text(
                    "• ${it.producto?.descripcion ?: "Producto"} x ${it.cantidad}",
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


