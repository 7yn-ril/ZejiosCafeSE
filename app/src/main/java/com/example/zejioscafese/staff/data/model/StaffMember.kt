package com.example.zejioscafese.staff.data.model

import java.time.LocalDate

data class StaffMember(
    val id: String,
    val employeeId: String,
    val fullName: String,
    val role: String,
    val email: String?,
    val phone: String?,
    val status: StaffStatus,
    val isActive: Boolean,
    val address: String? = null,
    val birthdate: LocalDate? = null,
    val gender: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val emergencyContactRelationship: String? = null
)

enum class StaffStatus(val wireValue: String) {
    ACTIVE("active"),
    ON_BREAK("on_break"),
    OFF_DUTY("off_duty");

    companion object {
        fun fromWire(value: String?): StaffStatus =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: ACTIVE
    }
}
