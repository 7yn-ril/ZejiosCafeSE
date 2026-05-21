package com.example.zejioscafese.pos.data.model

import java.time.LocalDate

data class Discount(
    val id: String,
    val name: String,
    val percent: Double,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val isBuiltIn: Boolean = false
) {
    fun isApplicableOn(date: LocalDate): Boolean {
        if (startDate != null && date.isBefore(startDate)) return false
        if (endDate != null && date.isAfter(endDate)) return false
        return true
    }

    fun amountFor(subtotal: Double): Double {
        if (percent <= 0.0 || subtotal <= 0.0) return 0.0
        return (subtotal * percent / 100.0).coerceAtMost(subtotal)
    }

    fun displayLabel(): String {
        val rounded = if (percent % 1.0 == 0.0) percent.toInt().toString() else "%.1f".format(percent)
        return "$name ($rounded%)"
    }

    companion object {
        const val PWD_ID = "DSC-PWD"
        const val SENIOR_ID = "DSC-SENIOR"
    }
}
