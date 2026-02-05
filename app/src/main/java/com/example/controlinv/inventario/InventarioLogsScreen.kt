package com.example.controlinv
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.controlinv.auth.supabase
import com.example.controlinv.inventario.InventarioLogRaw
import io.github.jan.supabase.postgrest.from
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
                .padding(8.dp)
        ) {
            items(logs) { log ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("Admin: ${log.admin_email}")
                        Text("Producto ID: ${log.producto_id}")
                        Text("Antes:")
                        Text(log.item_anterior)
                        Text("Después:")
                        Text(log.item_nuevo)
                        Text(
                            log.fecha,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}




