package com.example.controlinv

import kotlinx.serialization.Serializable

@Serializable
data class DetallePedido(
    val pedido_id: String,
    val producto_id: String,
    val cantidad: Int,

    // para UI
    var producto: Inventario? = null
)
