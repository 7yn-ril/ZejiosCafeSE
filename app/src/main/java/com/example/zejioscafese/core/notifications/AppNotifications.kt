package com.example.zejioscafese.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.zejioscafese.MainActivity
import com.example.zejioscafese.R

object AppNotifications {

    const val CHANNEL_ORDERS = "zejios_orders"
    const val CHANNEL_INVENTORY = "zejios_inventory"

    private const val NOTIF_ID_NEW_ORDER_BASE = 1_000
    private const val NOTIF_ID_LOW_STOCK_BASE = 2_000
    private const val NOTIF_ID_OUT_OF_STOCK_BASE = 3_000

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val ordersChannel = NotificationChannel(
            CHANNEL_ORDERS,
            "New Orders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when a new order is placed."
            enableLights(true)
            enableVibration(true)
        }

        val inventoryChannel = NotificationChannel(
            CHANNEL_INVENTORY,
            "Inventory Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Low stock and out-of-stock ingredient alerts."
            enableLights(true)
            enableVibration(true)
        }

        manager.createNotificationChannel(ordersChannel)
        manager.createNotificationChannel(inventoryChannel)
    }

    fun notifyNewOrder(context: Context, orderNumber: String, customerName: String, itemCount: Int) {
        val title = "New order $orderNumber"
        val body = buildString {
            append(customerName.ifBlank { "Walk-in" })
            append(" • ")
            append(itemCount)
            append(if (itemCount == 1) " item" else " items")
        }
        post(
            context = context,
            channelId = CHANNEL_ORDERS,
            id = NOTIF_ID_NEW_ORDER_BASE + (orderNumber.hashCode() and 0xFFFF),
            title = title,
            body = body
        )
    }

    fun notifyLowStock(context: Context, ingredientName: String, currentStock: String, unit: String) {
        post(
            context = context,
            channelId = CHANNEL_INVENTORY,
            id = NOTIF_ID_LOW_STOCK_BASE + (ingredientName.hashCode() and 0xFFFF),
            title = "Low stock: $ingredientName",
            body = "Only $currentStock $unit left. Restock soon to avoid disruption."
        )
    }

    fun notifyOutOfStock(context: Context, ingredientName: String) {
        post(
            context = context,
            channelId = CHANNEL_INVENTORY,
            id = NOTIF_ID_OUT_OF_STOCK_BASE + (ingredientName.hashCode() and 0xFFFF),
            title = "Out of stock: $ingredientName",
            body = "This ingredient has run out. Affected menu items may be unavailable."
        )
    }

    private fun post(
        context: Context,
        channelId: String,
        id: Int,
        title: String,
        body: String
    ) {
        if (!hasPostPermission(context)) return

        val contentIntent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(
                if (channelId == CHANNEL_ORDERS) NotificationCompat.CATEGORY_EVENT
                else NotificationCompat.CATEGORY_STATUS
            )
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun hasPostPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
