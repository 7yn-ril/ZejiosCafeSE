package com.example.zejioscafese.staff.data.remote.dto

import com.example.zejioscafese.staff.data.model.StaffMember
import com.example.zejioscafese.staff.data.model.StaffStatus
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StaffMemberDto(
    @SerialName("staff_id")
    val staffId: String,
    @SerialName("staff_employee_id")
    val staffEmployeeId: String,
    @SerialName("staff_full_name")
    val staffFullName: String,
    @SerialName("staff_role")
    val staffRole: String,
    @SerialName("staff_email")
    val staffEmail: String? = null,
    @SerialName("staff_phone")
    val staffPhone: String? = null,
    @SerialName("staff_status")
    val staffStatus: String = "active",
    @SerialName("staff_is_active")
    val staffIsActive: Boolean = true,
    @SerialName("staff_address")
    val staffAddress: String? = null,
    @SerialName("staff_birthdate")
    val staffBirthdate: String? = null,
    @SerialName("staff_gender")
    val staffGender: String? = null,
    @SerialName("staff_emergency_contact_name")
    val staffEmergencyContactName: String? = null,
    @SerialName("staff_emergency_contact_phone")
    val staffEmergencyContactPhone: String? = null,
    @SerialName("staff_emergency_contact_relationship")
    val staffEmergencyContactRelationship: String? = null
) {
    fun toStaffMember(): StaffMember = StaffMember(
        id = staffId,
        employeeId = staffEmployeeId,
        fullName = staffFullName,
        role = staffRole,
        email = staffEmail?.takeIf { it.isNotBlank() },
        phone = staffPhone?.takeIf { it.isNotBlank() },
        status = StaffStatus.fromWire(staffStatus),
        isActive = staffIsActive,
        address = staffAddress?.takeIf { it.isNotBlank() },
        birthdate = staffBirthdate.toLocalDateOrNull(),
        gender = staffGender?.takeIf { it.isNotBlank() },
        emergencyContactName = staffEmergencyContactName?.takeIf { it.isNotBlank() },
        emergencyContactPhone = staffEmergencyContactPhone?.takeIf { it.isNotBlank() },
        emergencyContactRelationship = staffEmergencyContactRelationship?.takeIf { it.isNotBlank() }
    )

    private fun String?.toLocalDateOrNull(): LocalDate? {
        if (this.isNullOrBlank()) return null
        return try {
            LocalDate.parse(this)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

@Serializable
data class StaffMemberInsertDto(
    @SerialName("staff_id")
    val staffId: String,
    @SerialName("staff_employee_id")
    val staffEmployeeId: String,
    @SerialName("staff_full_name")
    val staffFullName: String,
    @SerialName("staff_role")
    val staffRole: String,
    @SerialName("staff_email")
    val staffEmail: String? = null,
    @SerialName("staff_phone")
    val staffPhone: String? = null,
    @SerialName("staff_status")
    val staffStatus: String = "active",
    @SerialName("staff_is_active")
    val staffIsActive: Boolean = true,
    @SerialName("staff_address")
    val staffAddress: String? = null,
    @SerialName("staff_birthdate")
    val staffBirthdate: String? = null,
    @SerialName("staff_gender")
    val staffGender: String? = null,
    @SerialName("staff_emergency_contact_name")
    val staffEmergencyContactName: String? = null,
    @SerialName("staff_emergency_contact_phone")
    val staffEmergencyContactPhone: String? = null,
    @SerialName("staff_emergency_contact_relationship")
    val staffEmergencyContactRelationship: String? = null
)
