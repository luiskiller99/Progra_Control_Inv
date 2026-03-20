package com.example.controlinv.main

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.controlinv.auth.EstadoLogin
import com.example.controlinv.auth.LoginViewModel
import com.example.controlinv.empleado.ExportacionPedidosTipo
import com.example.controlinv.empleado.PedidoAdminViewModel
import com.example.controlinv.empleado.PedidosAdminScreen
import com.example.controlinv.empleado.exportarPedidosCsv
import com.example.controlinv.inventario.InventarioLogsScreen
import com.example.controlinv.main.screens.InventarioScreen
import com.example.controlinv.main.screens.LoginScreen
import com.example.controlinv.main.screens.PedidoEmpleadoScreen
import com.example.controlinv.ui.theme.ControlInvTheme

enum class AdminTab {
    INVENTARIO,
    PEDIDOS,
    LOGS
}

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ControlInvTheme {
                val loginVM: LoginViewModel = viewModel()
                when (val estado = loginVM.estado) {
                    is EstadoLogin.Login -> {
                        LoginScreen { email, pass ->
                            loginVM.login(email, pass)
                        }
                    }

                    is EstadoLogin.Admin -> {
                        var adminTab by remember { mutableStateOf(AdminTab.INVENTARIO) }
                        val pedidoAdminViewModel: PedidoAdminViewModel = viewModel()
                        val pedidos by pedidoAdminViewModel.listaPedidos.collectAsState()
                        val context = LocalContext.current
                        Scaffold { padding ->
                            Column(
                                modifier = Modifier
                                    .padding(padding)
                                    .fillMaxSize()
                            ) {
                                AdminTabs(
                                    adminTab = adminTab,
                                    onChangeTab = { adminTab = it },
                                    onExportar = { tipo ->
                                        exportarPedidosCsv(
                                            context = context,
                                            pedidos = pedidos,
                                            tipo = tipo
                                        )
                                    },
                                    onLogout = { loginVM.logout() }
                                )

                                when (adminTab) {
                                    AdminTab.INVENTARIO -> InventarioScreen()
                                    AdminTab.PEDIDOS -> PedidosAdminScreen(viewModel = pedidoAdminViewModel)
                                    AdminTab.LOGS -> InventarioLogsScreen()
                                }
                            }
                        }
                    }

                    is EstadoLogin.Empleado -> {
                        PedidoEmpleadoScreen(onLogout = { loginVM.logout() })
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
}

@Composable
private fun AdminTabs(
    adminTab: AdminTab,
    onChangeTab: (AdminTab) -> Unit,
    onExportar: (ExportacionPedidosTipo) -> Unit,
    onLogout: () -> Unit
) {
    var mostrarMenuDescarga by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(35.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onChangeTab(AdminTab.INVENTARIO) }) {
                Text("Inventario")
            }
            TextButton(onClick = { onChangeTab(AdminTab.PEDIDOS) }) {
                Text("Pedidos")
            }
            TextButton(onClick = { onChangeTab(AdminTab.LOGS) }) {
                Text("Ediciones")
            }
            if (adminTab == AdminTab.PEDIDOS) {
                IconButton(onClick = { mostrarMenuDescarga = true }) {
                    Icon(Icons.Default.Download, contentDescription = "Descargar pedidos")
                }
                DropdownMenu(
                    expanded = mostrarMenuDescarga,
                    onDismissRequest = { mostrarMenuDescarga = false }
                ) {
                    ExportacionPedidosTipo.entries.forEach { tipo ->
                        DropdownMenuItem(
                            text = { Text(tipo.etiqueta) },
                            onClick = {
                                mostrarMenuDescarga = false
                                onExportar(tipo)
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        IconButton(onClick = onLogout) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Salir")
        }
    }
}
