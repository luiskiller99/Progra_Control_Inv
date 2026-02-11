package com.example.controlinv.main.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.controlinv.inventario.InventarioViewModel
import com.example.controlinv.inventario.model.Inventario

private val colCodigo = 90.dp
private val colDescripcion = 180.dp
private val colCantidad = 80.dp
private val colClase = 100.dp
private val colAcciones = 90.dp

@Composable
fun InventarioScreen(
    viewModel: InventarioViewModel = viewModel()
) {
    val context = LocalContext.current
    val scrollHorizontal = rememberScrollState()
    var eliminarId by remember { mutableStateOf<String?>(null) }
    var creando by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { creando = true }) {
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
            onSave = { item, imagenBytes, extension ->
                viewModel.agregar(
                    item = item,
                    imagenBytes = imagenBytes,
                    extension = extension,
                    onOk = { creando = false },
                    onError = { mensaje -> Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show() }
                )
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
            viewModel.filtrar(it)
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
    onSave: (Inventario, ByteArray?, String) -> Unit,
    onDismiss: () -> Unit
) {
    var codigo by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var cantidad by remember { mutableStateOf("") }
    var clasificacion by remember { mutableStateOf("") }
    var imagenSeleccionada by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val launcherImagen = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        imagenSeleccionada = uri
    }

    val valido = codigo.isNotBlank() &&
            descripcion.isNotBlank() &&
            cantidad.toIntOrNull() != null &&
            imagenSeleccionada != null

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
                        ),
                        imagenSeleccionada?.let { uri ->
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        },
                        obtenerExtensionImagen(imagenSeleccionada?.let { context.contentResolver.getType(it) })
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

                Button(onClick = { launcherImagen.launch("image/*") }) {
                    Text(if (imagenSeleccionada == null) "Seleccionar imagen" else "Cambiar imagen")
                }

                if (imagenSeleccionada != null) {
                    Text("Imagen seleccionada")
                    AsyncImage(
                        model = imagenSeleccionada,
                        contentDescription = "Vista previa",
                        modifier = Modifier.size(100.dp)
                    )
                } else {
                    Text("Debes seleccionar una imagen para guardar el producto")
                }
            }
        }
    )
}

private fun obtenerExtensionImagen(mimeType: String?): String {
    return when (mimeType?.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
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
        CampoTabla(codigo, { codigo = it }, colCodigo)
        CampoTabla(descripcion, { descripcion = it }, colDescripcion)
        CampoTabla(cantidad, { cantidad = it }, colCantidad)
        CampoTabla(clase, { clase = it }, colClase)

        Row(
            modifier = Modifier.width(colAcciones),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
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

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar")
            }

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
