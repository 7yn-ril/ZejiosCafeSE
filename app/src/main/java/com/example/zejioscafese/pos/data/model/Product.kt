package com.example.zejioscafese.pos.data.model

import androidx.annotation.DrawableRes

data class Product(
    val id: String,
    val name: String,
    val category: String,
    val price: Double,
    val stockLeft: Int,
    val imageUrl: String? = null,
    @DrawableRes val imageResId: Int = android.R.drawable.ic_menu_gallery
)

