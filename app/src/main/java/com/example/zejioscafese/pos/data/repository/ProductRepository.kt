package com.example.zejioscafese.pos.data.repository

import com.example.zejioscafese.core.supabase.SupabaseProvider
import com.example.zejioscafese.core.supabase.SupabaseSessionHelper
import com.example.zejioscafese.pos.data.local.ProductImageResolver
import com.example.zejioscafese.pos.data.model.Product
import com.example.zejioscafese.pos.data.remote.dto.ProductVariantStockDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class ProductRepository(
    private val clientProvider: () -> SupabaseClient = { SupabaseProvider.client }
) {

    private val supabaseClient: SupabaseClient
        get() = clientProvider()

    suspend fun fetchProducts(): List<Product> {
        SupabaseSessionHelper.ensureValidSession(supabaseClient)

        return supabaseClient
            .from("product_variant_stock_view")
            .select {
                order(column = "category_name", order = Order.ASCENDING)
                order(column = "product_name", order = Order.ASCENDING)
                order(column = "variant_name", order = Order.ASCENDING)
            }
            .decodeList<ProductVariantStockDto>()
            .asSequence()
            .filter { it.productIsActive && it.variantIsActive }
            .map(ProductVariantStockDto::toProduct)
            .map { product ->
                ProductImageResolver.resolve(product.sourceProductName ?: product.name)
                    ?.let { assetImageUrl ->
                        product.copy(imageUrl = assetImageUrl)
                    } ?: product
            }
            .toList()
    }
}
