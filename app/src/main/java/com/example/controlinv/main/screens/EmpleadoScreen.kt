package com.example.controlinv.main.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.controlinv.R
import com.example.controlinv.auth.SUPABASE_URL
import com.example.controlinv.auth.supabase
import com.example.controlinv.empleado.ItemCarrito
import com.example.controlinv.empleado.PedidoViewModel
import com.example.controlinv.empleado.PedidoViewModelFactory
import com.example.controlinv.inventario.model.Inventario
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

private fun resolverImagenProducto(item: Inventario): String? {
    val candidato = item.imagen?.takeIf { it.isNotBlank() }
        ?: item.extra1?.takeIf { it.isNotBlank() }
        ?: return null

    if (candidato.startsWith("http")) return candidato

    val pathLimpio = candidato
        .removePrefix("/")
        .removePrefix("productos/")
        .removePrefix("object/public/productos/")

    return "$SUPABASE_URL/storage/v1/object/public/productos/$pathLimpio"
}

@Composable
private fun ProductoCard(
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
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = resolverImagenProducto(item) ?: R.drawable.placeholder_producto,
                contentDescription = item.descripcion,
                modifier = Modifier.size(64.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.codigo.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item.descripcion.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Stock: ${item.cantidad ?: 0}",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.width(8.dp))

            OutlinedTextField(
                value = cantidad,
                onValueChange = { cantidad = it },
                modifier = Modifier.width(60.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                singleLine = true
            )

            Spacer(Modifier.width(6.dp))

            Button(
                onClick = { onAgregar(cantidad.toIntOrNull() ?: 0) }
            ) {
                Text("Agregar")
            }
        }
    }
}

@Composable
private fun CarritoResumen(
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
        Column(modifier = Modifier.padding(12.dp)) {

            Text("Carrito (${carrito.size})")

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 200.dp)
            ) {
                items(carrito, key = { it.producto.id ?: "sin-id" }) { item ->
                    val productId = item.producto.id ?: return@items

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            item.producto.descripcion.orEmpty(),
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(onClick = { onRestar(productId) }) {
                            Text("-")
                        }

                        Text(item.cantidad.toString())

                        IconButton(onClick = { onSumar(productId) }) {
                            Text("+")
                        }

                        IconButton(onClick = { onEliminar(productId) }) {
                            Icon(Icons.Default.Delete, contentDescription = null)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedidoEmpleadoScreen(
    onLogout: () -> Unit
) {
    val pedidoViewModel: PedidoViewModel =
        viewModel(factory = PedidoViewModelFactory(supabase))

    var textoBusqueda by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val userId = supabase.auth.currentUserOrNull()?.id

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    pedidoViewModel.carrito.find { it.producto.id == id }?.let {
                        pedidoViewModel.agregarAlCarrito(it.producto, 1)
                    }
                },
                onRestar = { pedidoViewModel.restarDelCarrito(it) },
                onEliminar = { pedidoViewModel.quitarDelCarrito(it) },
                onConfirmar = {
                    if (userId == null) {
                        scope.launch {
                            snackbarHostState.showSnackbar("No hay usuario autenticado")
                        }
                        return@CarritoResumen
                    }

                    val email =
                        supabase.auth.currentUserOrNull()?.email ?: "desconocido@local"

                    pedidoViewModel.confirmarPedido(
                        userId = userId,
                        email = email,
                        onOk = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Pedido creado correctamente")
                            }
                        },
                        onError = { error ->
                            scope.launch {
                                snackbarHostState.showSnackbar(error)
                            }
                        }
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

            OutlinedTextField(
                value = textoBusqueda,
                onValueChange = {
                    textoBusqueda = it
                    pedidoViewModel.filtrarInventario(it)
                },
                label = { Text("Buscar producto") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(pedidoViewModel.inventario, key = { it.id ?: "" }) { item ->
                    ProductoCard(
                        item = item,
                        onAgregar = { cant ->
                            pedidoViewModel.agregarAlCarrito(item, cant)
                        }
                    )
                }
            }
        }
    }
}
