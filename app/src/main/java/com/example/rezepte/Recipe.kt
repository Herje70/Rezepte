package com.example.rezepte

data class Recipe(
    val id: String,
    val name: String,
    val mimeType: String,
    val driveUrl: String? = null,
    val category: String? = null
)
