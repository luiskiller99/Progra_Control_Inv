package com.example.controlinv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedidosAdminScreen(
    onBack: () -> Unit,
    viewModel: PedidosAdminViewModel = viewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pedidos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->

        if (viewModel.cargando) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            items(viewModel.pedidos) { pedido ->
                PedidoCard(pedido)
            }
        }
    }
}
@Composable
fun PedidoCard(pedido: Pedido) {
    Card(
        modifier = Modifier
            .padding(12.dp)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {

            Text(
                text = pedido.emailEmpleado?.toString() ?: "Empleado desconocido",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(4.dp))

            Text("Estado: ${pedido.estado}")
            Text("Fecha: ${pedido.created_at}")

            Spacer(Modifier.height(8.dp))

            Row {
                Button(
                    onClick = { /* aceptar */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Aceptar")
                }

                Spacer(Modifier.width(8.dp))

                OutlinedButton(
                    onClick = { /* rechazar */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Rechazar")
                }
            }
        }
    }
}
