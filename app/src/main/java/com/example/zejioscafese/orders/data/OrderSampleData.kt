package com.example.zejioscafese.orders.data

import com.example.zejioscafese.orders.model.CafeOrder
import com.example.zejioscafese.orders.model.CafeOrderStatus

object OrderSampleData {

    val orders = listOf(
        CafeOrder("#ORD-1024", "Sarah Johnson", "12", "Cappuccino, Croissant", 2, "10:24 AM", CafeOrderStatus.PENDING, 12.50, "SJ"),
        CafeOrder("#ORD-1023", "Mike Chen", "5", "Espresso, Muffin, Latte", 3, "10:18 AM", CafeOrderStatus.PREPARING, 18.75, "MC"),
        CafeOrder("#ORD-1022", "Emily Davis", "8", "Americano, Bagel", 2, "10:05 AM", CafeOrderStatus.COMPLETED, 9.50, "ED"),
        CafeOrder("#ORD-1021", "James Wilson", "3", "Mocha, Sandwich, Cookie", 3, "09:52 AM", CafeOrderStatus.PENDING, 16.25, "JW"),
        CafeOrder("#ORD-1020", "Lisa Martinez", "15", "Flat White, Cake", 2, "09:45 AM", CafeOrderStatus.PREPARING, 14.00, "LM"),
        CafeOrder("#ORD-1019", "Robert Brown", "7", "Tea, Scone", 2, "09:30 AM", CafeOrderStatus.COMPLETED, 8.75, "RB")
    )
}
