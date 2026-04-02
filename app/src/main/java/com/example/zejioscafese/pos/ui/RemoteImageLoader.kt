package com.example.zejioscafese.pos.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import androidx.annotation.DrawableRes
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object RemoteImageLoader {

    private val executor = Executors.newFixedThreadPool(4)
    private val cache = object : LruCache<String, Bitmap>(cacheSizeInKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(
        imageView: ImageView,
        imageUrl: String?,
        @DrawableRes fallbackResId: Int
    ) {
        imageView.setImageResource(fallbackResId)
        val normalizedUrl = imageUrl.orEmpty().trim()
        imageView.tag = normalizedUrl

        if (normalizedUrl.isEmpty()) return

        cache.get(normalizedUrl)?.let { cachedBitmap ->
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        executor.execute {
            val bitmap = normalizedUrl.downloadBitmap() ?: return@execute
            cache.put(normalizedUrl, bitmap)
            imageView.post {
                if (imageView.tag == normalizedUrl) {
                    imageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun String.downloadBitmap(): Bitmap? {
        return runCatching {
            val connection = (URL(this).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                doInput = true
            }
            connection.inputStream.use(BitmapFactory::decodeStream)
        }.getOrNull()
    }

    private fun cacheSizeInKb(): Int {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return maxMemoryKb / 8
    }
}
