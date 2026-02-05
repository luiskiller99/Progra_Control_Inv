package com.example.controlinv.Main
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.gotrue.auth
import kotlinx.serialization.Serializable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.example.controlinv.InventarioLogsScreen
import com.example.controlinv.R
import com.example.controlinv.inventario.InventarioViewModel
import com.example.controlinv.inventario.logout
import com.example.controlinv.empleado.ItemCarrito
import com.example.controlinv.empleado.PedidoViewModel
import com.example.controlinv.auth.EstadoLogin
import com.example.controlinv.auth.LoginViewModel
import com.example.controlinv.auth.supabase
import com.example.controlinv.empleado.PedidoViewModelFactory
import com.example.controlinv.empleado.PedidosAdminScreen
import kotlinx.coroutines.launch
import androidx.compose.material.*
private val colCodigo = 90.dp
private val colDescripcion = 180.dp
private val colCantidad = 80.dp
private val colClase = 100.dp
private val colAcciones = 90.dp
@Serializable
data class Inventario(
    val id: String? = null,
    val codigo: String? ,
    val descripcion: String? ,
    val cantidad: Int? ,
    val clasificacion: String? ,
    val extra1: String? = null,
    val extra2: String? = null
)
enum class AdminTab  {
    INVENTARIO,
    PEDIDOS,
    LOGS
}
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val loginVM: LoginViewModel = viewModel()
            when (val estado = loginVM.estado) {
                is EstadoLogin.Login -> {
                    LoginScreen { email, pass ->
                        loginVM.login(email, pass)
                    }
                }
                is EstadoLogin.Admin -> {
                    var adminTab by remember { mutableStateOf(AdminTab.INVENTARIO) }
                    Scaffold { padding ->
                        Column(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(35.dp) // 👈 AQUÍ controlas el tamaño REAL
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { adminTab = AdminTab.INVENTARIO }) {
                                    Text("Inventario")
                                }
                                TextButton(onClick = { adminTab = AdminTab.PEDIDOS }) {
                                    Text("Pedidos")
                                }
                                TextButton(onClick = { adminTab = AdminTab.LOGS }) {
                                    Text("Ediciones")
                                }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { loginVM.logout() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = "Salir"
                                    )
                                }
                            }
                            when (adminTab) {
                                AdminTab.INVENTARIO -> InventarioScreen()
                                AdminTab.PEDIDOS -> PedidosAdminScreen()
                                AdminTab.LOGS -> InventarioLogsScreen()
                            }
                        }
                    }
                }
                is EstadoLogin.Empleado -> {
                    PedidoEmpleadoScreen(
                        onLogout = {
                            loginVM.logout()
                        }
                    )
                }
                is EstadoLogin.Error -> {
                    LoginScreen { email, pass ->
                        loginVM.login(email, pass)
                    }

                    LaunchedEffect(estado) {
                        Toast.makeText(
                            this@MainActivity,
                            estado.mensaje,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedidoEmpleadoScreen(
    onLogout: () -> Unit
) {
    val pedidoViewModel: PedidoViewModel = viewModel(
        factory = PedidoViewModelFactory(supabase)
    )
    if (pedidoViewModel.cargando) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
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

                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(pedidoViewModel.inventario, key = { it.id ?: "" }) { item ->
                        ProductoCard(
                            item = item,
                            onAgregar = { cant ->
                                pedidoViewModel.agregarAlCarrito(item, cant)
                            }
                        )
                    }
                }

                Divider()

                val userId = supabase.auth.currentUserOrNull()?.id
            val context = LocalContext.current
            CarritoResumen(
                carrito = pedidoViewModel.carrito,
                onSumar = { id ->
                    val item = pedidoViewModel.carrito.find { it.producto.id == id }
                    if (item != null) {
                        pedidoViewModel.agregarAlCarrito(item.producto, 1)
                    }
                },
                onRestar = { id ->
                    pedidoViewModel.restarDelCarrito(id)
                },
                onEliminar = { id ->
                    pedidoViewModel.quitarDelCarrito(id)
                },
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
        }
}
@Composable
fun LoginScreen(onLogin: (String, String) -> Unit) {

    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Usuario") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { onLogin(email.trim(), pass) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ingresar")
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventarioScreen(
    viewModel: InventarioViewModel = viewModel()
) {
    val scrollHorizontal = rememberScrollState()
    var eliminarId by remember { mutableStateOf<String?>(null) }
    var creando by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { creando = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar")
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxSize()
        ) {

            if (viewModel.cargando) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            BuscadorInventario(viewModel)

            Row(
                modifier = Modifier
                    .horizontalScroll(scrollHorizontal)
                    .fillMaxWidth()
            ) {
                Column {

                    InventarioHeader()

                    LazyColumn {
                        items(viewModel.inventario, key = { it.id ?: "" }) { item ->
                            InventarioRow(
                                item = item,
                                onGuardar = viewModel::guardarFila,
                                onDelete = { eliminarId = item.id },
                                onDiscard = { viewModel.descartarFila(item.id!!) }
                            )
                            Divider()
                        }
                    }
                }
            }
        }
    }

    if (creando) {
        NuevoInventarioDialog(
            onSave = {
                viewModel.agregar(it)
                creando = false
            },
            onDismiss = { creando = false }
        )
    }

    if (eliminarId != null) {
        AlertDialog(
            onDismissRequest = { eliminarId = null },
            title = { Text("Eliminar producto") },
            text = { Text("¿Seguro que deseas eliminar este producto?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.eliminar(eliminarId!!)
                        eliminarId = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { eliminarId = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun BuscadorInventario(viewModel: InventarioViewModel) {
    var texto by remember { mutableStateOf("") }

    OutlinedTextField(
        value = texto,
        onValueChange = {
            texto = it
            viewModel.filtrar(it) // ✅ CORRECTO
        },
        label = { Text("Buscar producto") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        }
    )
}
@Composable
fun NuevoInventarioDialog(
    onSave: (Inventario) -> Unit,
    onDismiss: () -> Unit
) {
    var codigo by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var cantidad by remember { mutableStateOf("") }
    var clasificacion by remember { mutableStateOf("") }

    val valido = codigo.isNotBlank() &&
            descripcion.isNotBlank() &&
            cantidad.toIntOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar producto") },

        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        Inventario(
                            codigo = codigo.trim(),
                            descripcion = descripcion.trim(),
                            cantidad = cantidad.toInt(),
                            clasificacion = clasificacion.trim()
                        )
                    )
                },
                enabled = valido
            ) {
                Text("Guardar")
            }
        },

        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },

        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                OutlinedTextField(
                    value = codigo,
                    onValueChange = { codigo = it },
                    label = { Text("Código") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción") }
                )

                OutlinedTextField(
                    value = cantidad,
                    onValueChange = { cantidad = it },
                    label = { Text("Cantidad") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                OutlinedTextField(
                    value = clasificacion,
                    onValueChange = { clasificacion = it },
                    label = { Text("Clasificación") },
                    singleLine = true
                )
            }
        }
    )
}
@Composable
fun InventarioHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Código", Modifier.width(colCodigo))
        Text("Descripción", Modifier.width(colDescripcion))
        Text("Cantidad", Modifier.width(colCantidad))
        Text("Clase", Modifier.width(colClase))
        Text("Acciones", Modifier.width(colAcciones))
        //Spacer(Modifier.width(colAcciones))/**VER*/
    }
    Divider()
}
@Composable
fun InventarioRow(
    item: Inventario,
    onGuardar: (Inventario) -> Unit,
    onDelete: () -> Unit,
    onDiscard: () -> Unit
) {
    var codigo by remember { mutableStateOf(item.codigo ?: "") }
    var descripcion by remember { mutableStateOf(item.descripcion ?: "") }
    var cantidad by remember { mutableStateOf(item.cantidad?.toString() ?: "") }
    var clase by remember { mutableStateOf(item.clasificacion ?: "") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        CampoTabla(codigo, {
            codigo = it
        }, colCodigo)

        CampoTabla(descripcion, {
            descripcion = it
        }, colDescripcion)

        CampoTabla(cantidad, {
            cantidad = it
        }, colCantidad)

        CampoTabla(clase, {
            clase = it
        }, colClase)


        Row(
            modifier = Modifier.width(colAcciones),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            // ✔ Guardar fila
            IconButton(onClick = {
                onGuardar(
                    item.copy(
                        codigo = codigo,
                        descripcion = descripcion,
                        cantidad = cantidad.toIntOrNull() ?: item.cantidad,
                        clasificacion = clase
                    )
                )
            }) {
                Icon(Icons.Default.Check, contentDescription = "Guardar")
            }


            // 🗑 Eliminar fila
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar")
            }

            // ↩ Descartar fila
            IconButton(onClick = onDiscard) {
                Text("↩")
            }
        }

    }

    Divider()
}
@Composable
fun CampoTabla(
    valor: String,
    onChange: (String) -> Unit,
    width: Dp
) {
    Surface(
        modifier = Modifier.width(width),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small
    ) {
        BasicTextField(
            value = valor,
            onValueChange = onChange,
            singleLine = true,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        )
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

            // 🖼️ IMAGEN
            AsyncImage(
                model = item.extra1 ?: R.drawable.placeholder_producto,
                contentDescription = item.descripcion,
                modifier = Modifier
                    .size(80.dp)
                    .padding(end = 12.dp)
            )

            // 📦 INFO PRODUCTO
            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    item.codigo ?: "",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    item.descripcion ?: "",
                    maxLines = 2
                )

                Text("Stock: ${item.cantidad ?: 0}")

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {

                    OutlinedTextField(
                        value = cantidad,
                        onValueChange = { cantidad = it },
                        modifier = Modifier.width(80.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true
                    )

                    Spacer(Modifier.width(12.dp))

                    Button(onClick = {
                        onAgregar(cantidad.toIntOrNull() ?: 0)
                    }) {
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {

        Text(
            "Carrito (${carrito.size})",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(8.dp))

        carrito.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    "${item.producto.descripcion}",
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = { onRestar(item.producto.id!!) }) {
                    Text("➖")
                }

                Text("${item.cantidad}")

                IconButton(onClick = { onSumar(item.producto.id!!) }) {
                    Text("➕")
                }

                IconButton(onClick = { onEliminar(item.producto.id!!) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onConfirmar,
            enabled = carrito.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Confirmar pedido")
        }
    }
}

