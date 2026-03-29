package com.example.zejioscafese.pos.data.model

data class Product(
    val id: String,
    val name: String,
    val category: String,
    val price: Double,
    val imageResId: Int = android.R.drawable.ic_menu_gallery
)

