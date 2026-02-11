package com.example.controlinv.main.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.controlinv.R
import com.example.controlinv.auth.supabase
import com.example.controlinv.empleado.ItemCarrito
import com.example.controlinv.empleado.PedidoViewModel
import com.example.controlinv.empleado.PedidoViewModelFactory
import com.example.controlinv.inventario.model.Inventario
import io.github.jan.supabase.gotrue.auth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedidoEmpleadoScreen(
    onLogout: () -> Unit
) {
    val pedidoViewModel: PedidoViewModel = viewModel(
        factory = PedidoViewModelFactory(supabase)
    )
    var textoBusqueda by remember { mutableStateOf("") }

    if (pedidoViewModel.cargando) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }

    val userId = supabase.auth.currentUserOrNull()?.id

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventario") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Salir")
                    }
                }
            )
        },
        bottomBar = {
            CarritoResumen(
                carrito = pedidoViewModel.carrito,
                onSumar = { id ->
                    val item = pedidoViewModel.carrito.find { it.producto.id == id }
                    if (item != null) {
                        pedidoViewModel.agregarAlCarrito(item.producto, 1)
                    }
                },
                onRestar = { id -> pedidoViewModel.restarDelCarrito(id) },
                onEliminar = { id -> pedidoViewModel.quitarDelCarrito(id) },
                onConfirmar = {
                    if (userId == null) return@CarritoResumen
                    val emailUsuario =
                        supabase.auth.currentUserOrNull()?.email ?: "desconocido@local"

                    pedidoViewModel.confirmarPedido(
                        userId = userId,
                        email = emailUsuario,
                        onOk = {},
                        onError = {}
                    )
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Text(
                "Catálogo de productos",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )

            OutlinedTextField(
                value = textoBusqueda,
                onValueChange = { texto ->
                    textoBusqueda = texto
                    pedidoViewModel.filtrarInventario(texto)
                },
                label = { Text("Buscar producto") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            )

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(pedidoViewModel.inventario, key = { it.id ?: "" }) { item ->
                    ProductoCard(
                        item = item,
                        onAgregar = { cant -> pedidoViewModel.agregarAlCarrito(item, cant) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProductoCard(
    item: Inventario,
    onAgregar: (Int) -> Unit
) {
    var cantidad by remember { mutableStateOf("1") }

    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp)
        ) {
            AsyncImage(
                model = item.imagen ?: item.extra1 ?: R.drawable.placeholder_producto,
                contentDescription = item.descripcion,
                modifier = Modifier
                    .size(80.dp)
                    .padding(end = 12.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(item.codigo ?: "", style = MaterialTheme.typography.titleMedium)
                Text(item.descripcion ?: "", maxLines = 2)
                Text("Stock: ${item.cantidad ?: 0}")

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    OutlinedTextField(
                        value = cantidad,
                        onValueChange = { cantidad = it },
                        modifier = Modifier.width(80.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true
                    )

                    Spacer(Modifier.width(12.dp))

                    Button(onClick = { onAgregar(cantidad.toIntOrNull() ?: 0) }) {
                        Text("Agregar")
                    }
                }
            }
        }
    }
}

@Composable
fun CarritoResumen(
    carrito: List<ItemCarrito>,
    onSumar: (String) -> Unit,
    onRestar: (String) -> Unit,
    onEliminar: (String) -> Unit,
    onConfirmar: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text("Carrito (${carrito.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
            ) {
                items(carrito, key = { it.producto.id ?: it.producto.codigo ?: "sin-id" }) { item ->
                    val productId = item.producto.id ?: return@items
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${item.producto.descripcion}", modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRestar(productId) }) { Text("➖") }
                        Text("${item.cantidad}")
                        IconButton(onClick = { onSumar(productId) }) { Text("➕") }
                        IconButton(onClick = { onEliminar(productId) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                        }
                    }
                    Divider()
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onConfirmar,
                enabled = carrito.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Confirmar pedido")
            }
        }
    }
}
