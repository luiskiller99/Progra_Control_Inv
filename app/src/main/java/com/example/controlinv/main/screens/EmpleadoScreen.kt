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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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

    if (candidato.startsWith("http://") || candidato.startsWith("https://")) return candidato

    val pathLimpio = candidato.removePrefix("/")
        .removePrefix("productos/")
        .removePrefix("object/public/productos/")

    return "$SUPABASE_URL/storage/v1/object/public/productos/$pathLimpio"
}

@Composable
private fun CarritoResumen(
    carrito: List<ItemCarrito>,
    comentario: String,
    onComentarioChange: (String) -> Unit,
    onSumar: (String) -> Unit,
    onRestar: (String) -> Unit,
    onEliminar: (String) -> Unit,
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
                        Text(item.cantidad.toString())
                        IconButton(onClick = { onSumar(productId) }) { Text("+") }
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
                    text = item.codigo.orEmpty(),
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
    var comentarioPedido by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        pedidoViewModel.refrescarInventario()
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
        }
    }
}
