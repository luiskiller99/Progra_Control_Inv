package com.example.controlinv.inventario.model

import kotlinx.serialization.Serializable

@Serializable
data class Inventario(
    val id: String? = null,
    val codigo: String?,
    val descripcion: String?,
    val cantidad: Int?,
    val clasificacion: String?,
    val extra1: String? = null,
    val extra2: String? = null
)
