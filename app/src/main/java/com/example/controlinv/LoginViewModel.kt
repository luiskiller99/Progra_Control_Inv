package com.example.controlinv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

sealed class EstadoLogin {
    object Login : EstadoLogin()
    object Admin : EstadoLogin()
    object Empleado : EstadoLogin()
    data class Error(val mensaje: String) : EstadoLogin()
}

@Serializable
data class Profile(
    val id: String,
    val email: String,
    val role: String
)

class LoginViewModel : ViewModel() {

    var estado by mutableStateOf<EstadoLogin>(EstadoLogin.Login)
        private set

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            try {
                // 1️⃣ Login Auth
                supabase.auth.signInWith(Email) {
                    this.email = email.trim()
                    this.password = pass
                }

                val user = supabase.auth.currentUserOrNull()
                    ?: throw Exception("Usuario no autenticado")

                // 2️⃣ Buscar perfil
                val perfil = supabase
                    .from("profiles")
                    .select {
                        filter { eq("id", user.id) }
                    }
                    .decodeSingleOrNull<Profile>()

                // 3️⃣ Crear perfil si no existe
                val finalPerfil = perfil ?: Profile(
                    id = user.id,
                    email = user.email ?: email.trim(),
                    role = "empleado" // 👈 default
                ).also {
                    supabase.from("profiles").insert(it)
                }

                // 4️⃣ Redirigir
                estado = if (finalPerfil.role == "admin") {
                    EstadoLogin.Admin
                } else {
                    EstadoLogin.Empleado
                }

            } catch (e: Exception) {
                estado = EstadoLogin.Error(e.message ?: "Error de login")
            }
        }
    }
}

