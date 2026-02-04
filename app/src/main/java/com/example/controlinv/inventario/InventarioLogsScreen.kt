package com.example.controlinv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.controlinv.inventario.InventarioViewModel

@Composable
fun InventarioLogsScreen(
    viewModel: InventarioViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.cargarInventarioLogs()
    }

    val logs = viewModel.inventarioLogs

    Column(modifier = Modifier.fillMaxSize()) {
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("No hay registros de ediciones")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(logs) { log ->
                    Card(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Admin: ${log.admin_email}")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Antes: ${log.item_anterior.descripcion} | Cant: ${log.item_anterior.cantidad}")
                            Text("Después: ${log.item_nuevo.descripcion} | Cant: ${log.item_nuevo.cantidad}")
                            Text("Fecha: ${log.fecha}")
                        }
                    }
                }
            }
        }
    }
}
