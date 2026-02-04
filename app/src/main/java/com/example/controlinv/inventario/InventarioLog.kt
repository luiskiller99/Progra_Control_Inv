package com.example.controlinv

import com.example.controlinv.Main.Inventario
import kotlinx.serialization.Serializable

@Serializable
data class InventarioLog(
    val id: String,
    val producto_id: String,
    val admin_email: String,
    val item_anterior: Inventario,
    val item_nuevo: Inventario,
    val fecha: String
)
