package com.example.zejioscafese.pos.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.example.zejioscafese.R
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object RemoteImageLoader {

    private const val ASSET_PREFIX = "asset:///"
    private val executor = Executors.newFixedThreadPool(4)
    private val cache = object : LruCache<String, Bitmap>(cacheSizeInKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(
        imageView: ImageView,
        imageUrl: String?,
        @DrawableRes fallbackResId: Int
    ) {
        showFallback(imageView, fallbackResId)
        val normalizedUrl = imageUrl.orEmpty().trim()
        val cacheKey = if (normalizedUrl.startsWith(ASSET_PREFIX)) {
            normalizedUrl
        } else {
            normalizedUrl
        }
        imageView.tag = cacheKey

        if (normalizedUrl.isEmpty()) return

        cache.get(cacheKey)?.let { cachedBitmap ->
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.setPadding(0, 0, 0, 0)
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        executor.execute {
            val bitmap = when {
                normalizedUrl.startsWith(ASSET_PREFIX) -> {
                    normalizedUrl.removePrefix(ASSET_PREFIX).loadAssetBitmap(imageView)
                }
                else -> normalizedUrl.downloadBitmap()
            } ?: return@execute

            cache.put(cacheKey, bitmap)
            imageView.post {
                if (imageView.tag == cacheKey) {
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    imageView.setPadding(0, 0, 0, 0)
                    imageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun showFallback(imageView: ImageView, @DrawableRes fallbackResId: Int) {
        val inset = imageView.resources.getDimensionPixelSize(R.dimen.image_placeholder_padding)
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        imageView.setPadding(inset, inset, inset, inset)
        imageView.setImageResource(fallbackResId)
    }

    private fun String.downloadBitmap(): Bitmap? {
        return runCatching {
            val connection = (URL(this).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                doInput = true
            }
            try {
                if (connection.responseCode !in 200..299) {
                    return@runCatching null
                }
                connection.inputStream.use(BitmapFactory::decodeStream)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun String.loadAssetBitmap(imageView: ImageView): Bitmap? {
        return runCatching {
            imageView.context.assets.open(this).use(BitmapFactory::decodeStream)
        }.getOrNull()
    }

    private fun cacheSizeInKb(): Int {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return maxMemoryKb / 8
    }
}
