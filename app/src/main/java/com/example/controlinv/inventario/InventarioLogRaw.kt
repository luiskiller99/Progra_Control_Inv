package com.example.controlinv.inventario

import kotlinx.serialization.Serializable

@Serializable
data class InventarioLogRaw(
    val producto_id: String,
    val admin_email: String,
    val item_anterior: String,
    val item_nuevo: String,
    val fecha: String
)
