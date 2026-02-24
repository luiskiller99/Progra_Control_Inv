package com.example.controlinv.main.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.example.controlinv.empleado.MiPedidoUI
import com.example.controlinv.empleado.PedidoViewModel
import com.example.controlinv.empleado.PedidoViewModelFactory
import com.example.controlinv.inventario.model.Inventario
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

private fun resolverImagenProducto(item: Inventario): String? {
    val candidato = item.imagen?.takeIf { it.isNotBlank() }
        ?: item.extra1?.takeIf { it.isNotBlank() }
        ?: return null

    if (candidato.startsWith("http://") || candidato.startsWith("https://")) return candidato

    val pathLimpio = candidato.removePrefix("/")
        .removePrefix("productos/")
        .removePrefix("object/public/productos/")

    return "$SUPABASE_URL/storage/v1/object/public/productos/$pathLimpio"
}
private fun idPedidoCorto(id: String): String {
    val soloDigitos = id.filter { it.isDigit() }
    return when {
        soloDigitos.length >= 6 -> soloDigitos.takeLast(6)
        soloDigitos.isNotEmpty() -> soloDigitos.padStart(6, '0')
        else -> ((id.hashCode().toLong() and 0xffffffffL) % 1_000_000L).toString().padStart(6, '0')
    }
}
@Composable
private fun MisPedidosDialog(
    pedidos: List<MiPedidoUI>,
    cargando: Boolean,
    onRecargar: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mis pedidos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onRecargar) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar mis pedidos")
                }
            }
        },
        text = {
            if (cargando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (pedidos.isEmpty()) {
                Text("Aún no tienes pedidos.", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(pedidos, key = { it.id }) { pedido ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("ID: ${idPedidoCorto(pedido.id)}", style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.width(8.dp))
                                Text("Estado: ${pedido.estado}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "Fecha: ${pedido.fecha.replace("T", " ").take(16)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (pedido.productos.isNotEmpty()) {
                                pedido.productos.forEach { prod ->
                                    Text(
                                        "• ${prod.cantidad} x ${prod.descripcion}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            if (pedido.comentario.isNotBlank()) {
                                Text(
                                    "Comentario: ${pedido.comentario}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Divider(modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
@Composable
private fun CarritoResumen(
    carrito: List<ItemCarrito>,
    comentario: String,
    onComentarioChange: (String) -> Unit,
    onSumar: (String) -> Unit,
    onRestar: (String) -> Unit,
    onEliminar: (String) -> Unit,
    onActualizarCantidad: (String, Int) -> Unit,
    onConfirmar: () -> Unit
) {
    Card(
        modifier = Modifier
             .fillMaxWidth()
            .navigationBarsPadding()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text("Carrito (${carrito.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            var editarProductoId by remember { mutableStateOf<String?>(null) }
            var cantidadManual by remember { mutableStateOf("") }

            OutlinedTextField(
                value = comentario,
                onValueChange = onComentarioChange,
                label = { Text("Comentario del pedido") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

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
                        Text(
                            item.producto.descripcion.orEmpty(),
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                        IconButton(onClick = { onRestar(productId) }) { Text("-") }
                        TextButton(onClick = {
                            editarProductoId = productId
                            cantidadManual = item.cantidad.toString()
                        }) {
                            Text(item.cantidad.toString())
                        }
                        IconButton(onClick = { onSumar(productId) }) { Text("+") }
                        IconButton(onClick = { onEliminar(productId) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                        }
                    }
                    Divider()
                }
            }

            Spacer(Modifier.height(8.dp))

            if (editarProductoId != null) {
                AlertDialog(
                    onDismissRequest = { editarProductoId = null },
                    title = { Text("Actualizar cantidad") },
                    text = {
                        OutlinedTextField(
                            value = cantidadManual,
                            onValueChange = { input ->
                                cantidadManual = input.filter { it.isDigit() }.take(4)
                            },
                            label = { Text("Cantidad") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val id = editarProductoId
                            val cantidad = cantidadManual.toIntOrNull()
                            if (id != null && cantidad != null) {
                                onActualizarCantidad(id, cantidad)
                            }
                            editarProductoId = null
                        }) { Text("Guardar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { editarProductoId = null }) { Text("Cancelar") }
                    }
                )
            }

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
@Composable
private fun ProductoCard(
    item: Inventario,
    onAgregar: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = resolverImagenProducto(item) ?: R.drawable.placeholder_producto,
                contentDescription = item.descripcion,
                modifier = Modifier
                     .size(64.dp)
                    .padding(end = 8.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        append(item.codigo.orEmpty())
                        if (!item.clasificacion.isNullOrBlank()) {
                            append("  •  ")
                            append(item.clasificacion)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item.descripcion.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Stock: ${item.cantidad ?: 0}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = onAgregar,
                modifier = Modifier.height(42.dp)
            ) {
                Text(text = "Agregar", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedidoEmpleadoScreen(
    onLogout: () -> Unit
) {
    val pedidoViewModel: PedidoViewModel = viewModel(factory = PedidoViewModelFactory(supabase))
    var textoBusqueda by remember { mutableStateOf("") }
    var mostrarMisPedidos by remember { mutableStateOf(false) }
    var comentarioPedido by remember { mutableStateOf("") }
    var mostrarMisPedidosDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        pedidoViewModel.refrescarInventario()
        val id = supabase.auth.currentUserOrNull()?.id
        pedidoViewModel.cargarMisPedidos(id)
    }

    if (pedidoViewModel.cargando) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }

    val userId = supabase.auth.currentUserOrNull()?.id

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Inventario") },
                actions = {
                    IconButton(onClick = {
                        mostrarMisPedidosDialog = true
                        pedidoViewModel.cargarMisPedidos(userId)
                    }) {
                        Icon(Icons.Default.List, contentDescription = "Mis pedidos")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Salir")
                    }
                }
            )
        },
        bottomBar = {
            CarritoResumen(
                carrito = pedidoViewModel.carrito,
                comentario = comentarioPedido,
                onComentarioChange = { comentarioPedido = it },
                onSumar = { id: String ->
                    val item = pedidoViewModel.carrito.find { it.producto.id == id }
                    if (item != null) pedidoViewModel.agregarAlCarrito(item.producto, 1)
                },
                onRestar = { id: String -> pedidoViewModel.restarDelCarrito(id) },
                onEliminar = { id: String -> pedidoViewModel.quitarDelCarrito(id) },
                onActualizarCantidad = { id: String, cantidad: Int ->
                    pedidoViewModel.actualizarCantidadCarrito(id, cantidad)
                },
                onConfirmar = {
                    if (userId == null) {
                        scope.launch { snackbarHostState.showSnackbar("No hay usuario autenticado") }
                        return@CarritoResumen
                    }

                    val emailUsuario = supabase.auth.currentUserOrNull()?.email ?: "desconocido@local"
                    pedidoViewModel.confirmarPedido(
                        userId = userId,
                        email = emailUsuario,
                        comentario = comentarioPedido,
                        onOk = {
                            comentarioPedido = ""
                            scope.launch { snackbarHostState.showSnackbar("Pedido creado correctamente") }
                        },
                        onError = { error -> scope.launch { snackbarHostState.showSnackbar(error) } }
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
                text = "Catálogo de productos",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )

            Button(
                onClick = {
                    mostrarMisPedidos = !mostrarMisPedidos
                    if (mostrarMisPedidos) pedidoViewModel.cargarMisPedidos(userId)
                },
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
            ) {
                Text(if (mostrarMisPedidos) "Ocultar mis pedidos" else "Mis pedidos")
            }

            if (mostrarMisPedidos) {
                MisPedidosSection(
                    pedidos = pedidoViewModel.misPedidos,
                    cargando = pedidoViewModel.cargandoMisPedidos,
                    onRecargar = { pedidoViewModel.cargarMisPedidos(userId) }
                )
            }

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
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(pedidoViewModel.inventario, key = { it.id ?: "" }) { item ->
                    ProductoCard(
                        item = item,
                        onAgregar = { pedidoViewModel.agregarAlCarrito(item, 1) }
                    )
                }
            }

            if (mostrarMisPedidosDialog) {
                MisPedidosDialog(
                    pedidos = pedidoViewModel.misPedidos,
                    cargando = pedidoViewModel.cargandoMisPedidos,
                    onRecargar = { pedidoViewModel.cargarMisPedidos(userId) },
                    onDismiss = { mostrarMisPedidosDialog = false }
                )
            }
        }
    }

}
@Composable
fun MisPedidosSection(
    pedidos: List<MiPedidoUI>,
    cargando: Boolean,
    onRecargar: () -> Unit
) {
    if (cargando) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )
        return
    }

    if (pedidos.isEmpty()) {
        Text(
            "No tienes pedidos aún",
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        pedidos.forEach { pedido ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        "Estado: ${pedido.estado}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        pedido.fecha.replace("T", " ").substring(0, 16),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}