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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.mutableStateListOf
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

private enum class EmpleadoTab {
    CATALOGO,
    CARRITO,
    PEDIDOS
}

private data class ItemPedidoExtraordinarioUI(
    val nombre: String,
    val cantidad: Int
)

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
private fun EmpleadoTabs(
    tabSeleccionado: EmpleadoTab,
    onTabChange: (EmpleadoTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        TextButton(onClick = { onTabChange(EmpleadoTab.CATALOGO) }) {
            Text(if (tabSeleccionado == EmpleadoTab.CATALOGO) "• Catálogo" else "Catálogo")
        }
        TextButton(onClick = { onTabChange(EmpleadoTab.CARRITO) }) {
            Text(if (tabSeleccionado == EmpleadoTab.CARRITO) "• Carrito" else "Carrito")
        }
        TextButton(onClick = { onTabChange(EmpleadoTab.PEDIDOS) }) {
            Text(if (tabSeleccionado == EmpleadoTab.PEDIDOS) "• Pedidos" else "Pedidos")
        }
    }
}

@Composable
private fun PedidosTabContent(
    pedidos: List<MiPedidoUI>,
    cargando: Boolean,
    onRecargar: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Mis pedidos",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRecargar) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar pedidos")
            }
        }

        if (cargando) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }

        if (!cargando && pedidos.isEmpty()) {
            Text(
                "Aún no tienes pedidos.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(8.dp)
            )
            return
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(pedidos, key = { it.id }) { pedido ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
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
                            Spacer(Modifier.height(4.dp))
                            pedido.productos.forEach { prod ->
                                Text(
                                    "• ${prod.cantidad} x ${prod.descripcion}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (pedido.comentario.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Comentario: ${pedido.comentario}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarritoTabContent(
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
                    .heightIn(max = 240.dp)
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

            Spacer(Modifier.height(10.dp))
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
private fun PedidoExtraordinarioVisualCard(
    nombreArticuloExtra: String,
    onNombreArticuloExtraChange: (String) -> Unit,
    cantidadExtra: String,
    onCantidadExtraChange: (String) -> Unit,
    itemsExtraordinarios: List<ItemPedidoExtraordinarioUI>,
    onAgregarExtraordinario: () -> Unit,
    onQuitarExtraordinario: (Int) -> Unit,
    onConfirmarExtraordinario: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text("Pedido extraordinario", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Este bloque es visual para definir el flujo. Aún no se envía a Supabase.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = nombreArticuloExtra,
                onValueChange = onNombreArticuloExtraChange,
                label = { Text("Nombre de artículo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = cantidadExtra,
                onValueChange = { input -> onCantidadExtraChange(input.filter { it.isDigit() }.take(4)) },
                label = { Text("Cantidad") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onAgregarExtraordinario,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Agregar")
            }

            if (itemsExtraordinarios.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                ) {
                    items(itemsExtraordinarios.size, key = { index -> "extra-$index" }) { index ->
                        val item = itemsExtraordinarios[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                item.nombre,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                item.cantidad.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { onQuitarExtraordinario(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar extraordinario")
                            }
                        }
                        Divider()
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onConfirmarExtraordinario,
                enabled = itemsExtraordinarios.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Confirmar pedido extraordinario")
            }
        }
    }
}

@Composable
private fun CatalogoTabContent(
    textoBusqueda: String,
    onTextoBusquedaChange: (String) -> Unit,
    inventario: List<Inventario>,
    onAgregarAlCarrito: (Inventario) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = "Catálogo de productos",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = textoBusqueda,
            onValueChange = onTextoBusquedaChange,
            label = { Text("Buscar producto") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(inventario, key = { it.id ?: "" }) { item ->
                ProductoCard(
                    item = item,
                    onAgregar = { onAgregarAlCarrito(item) }
                )
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
    var tabActual by remember { mutableStateOf(EmpleadoTab.CATALOGO) }
    var textoBusqueda by remember { mutableStateOf("") }
    var comentarioPedido by remember { mutableStateOf("") }
    var articuloExtraordinario by remember { mutableStateOf("") }
    var cantidadExtraordinaria by remember { mutableStateOf("") }
    val pedidosExtraordinarios = remember { mutableStateListOf<ItemPedidoExtraordinarioUI>() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        pedidoViewModel.refrescarInventario()
        val id = supabase.auth.currentUserOrNull()?.id
        pedidoViewModel.cargarMisPedidos(id)
    }

    val userId = supabase.auth.currentUserOrNull()?.id

    LaunchedEffect(tabActual, userId) {
        if (tabActual == EmpleadoTab.PEDIDOS) {
            pedidoViewModel.cargarMisPedidos(userId)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (tabActual) {
                            EmpleadoTab.CATALOGO -> "Catálogo"
                            EmpleadoTab.CARRITO -> "Carrito"
                            EmpleadoTab.PEDIDOS -> "Pedidos"
                        }
                    )
                },
                actions = {
                    if (tabActual == EmpleadoTab.PEDIDOS) {
                        IconButton(onClick = { pedidoViewModel.cargarMisPedidos(userId) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualizar pedidos")
                        }
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Salir")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (pedidoViewModel.cargando) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            EmpleadoTabs(
                tabSeleccionado = tabActual,
                onTabChange = { tabActual = it }
            )

            when (tabActual) {
                EmpleadoTab.CATALOGO -> {
                    CatalogoTabContent(
                        textoBusqueda = textoBusqueda,
                        onTextoBusquedaChange = { texto ->
                            textoBusqueda = texto
                            pedidoViewModel.filtrarInventario(texto)
                        },
                        inventario = pedidoViewModel.inventario,
                        onAgregarAlCarrito = { pedidoViewModel.agregarAlCarrito(it, 1) }
                    )
                }

                EmpleadoTab.CARRITO -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            CarritoTabContent(
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
                                        return@CarritoTabContent
                                    }

                                    val emailUsuario = supabase.auth.currentUserOrNull()?.email ?: "desconocido@local"
                                    pedidoViewModel.confirmarPedido(
                                        userId = userId,
                                        email = emailUsuario,
                                        comentario = comentarioPedido,
                                        onOk = {
                                            comentarioPedido = ""
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Pedido creado correctamente")
                                            }
                                        },
                                        onError = { error ->
                                            scope.launch { snackbarHostState.showSnackbar(error) }
                                        }
                                    )
                                }
                            )
                        }

                        item { Spacer(Modifier.height(8.dp)) }

                        item {
                            PedidoExtraordinarioVisualCard(
                                nombreArticuloExtra = articuloExtraordinario,
                                onNombreArticuloExtraChange = { articuloExtraordinario = it },
                                cantidadExtra = cantidadExtraordinaria,
                                onCantidadExtraChange = { cantidadExtraordinaria = it },
                                itemsExtraordinarios = pedidosExtraordinarios,
                                onAgregarExtraordinario = {
                                    val nombre = articuloExtraordinario.trim()
                                    val cantidad = cantidadExtraordinaria.toIntOrNull()
                                    when {
                                        nombre.isBlank() -> {
                                            scope.launch { snackbarHostState.showSnackbar("Ingresa nombre de artículo extraordinario") }
                                        }

                                        cantidad == null || cantidad <= 0 -> {
                                            scope.launch { snackbarHostState.showSnackbar("Ingresa una cantidad válida") }
                                        }

                                        else -> {
                                            pedidosExtraordinarios.add(
                                                ItemPedidoExtraordinarioUI(nombre = nombre, cantidad = cantidad)
                                            )
                                            articuloExtraordinario = ""
                                            cantidadExtraordinaria = ""
                                        }
                                    }
                                },
                                onQuitarExtraordinario = { index ->
                                    if (index in pedidosExtraordinarios.indices) {
                                        pedidosExtraordinarios.removeAt(index)
                                    }
                                },
                                onConfirmarExtraordinario = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Pedido extraordinario listo (solo visual, pendiente Supabase)"
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                EmpleadoTab.PEDIDOS -> {
                    PedidosTabContent(
                        pedidos = pedidoViewModel.misPedidos,
                        cargando = pedidoViewModel.cargandoMisPedidos,
                        onRecargar = { pedidoViewModel.cargarMisPedidos(userId) }
                    )
                }
            }
        }
    }
}
