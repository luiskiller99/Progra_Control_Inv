package com.example.controlinv.main

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.controlinv.auth.EstadoLogin
import com.example.controlinv.auth.LoginViewModel
import com.example.controlinv.empleado.PedidosAdminScreen
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
                        Scaffold { padding ->
                            Column(
                                modifier = Modifier
                                    .padding(padding)
                                    .fillMaxSize()
                            ) {
                                AdminTabs(
                                    adminTab = adminTab,
                                    onChangeTab = { adminTab = it },
                                    onLogout = { loginVM.logout() }
                                )

                                when (adminTab) {
                                    AdminTab.INVENTARIO -> InventarioScreen()
                                    AdminTab.PEDIDOS -> PedidosAdminScreen()
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
    onLogout: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(35.dp)
            .padding(horizontal = 8.dp),
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
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onLogout) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Salir")
        }
    }
}
