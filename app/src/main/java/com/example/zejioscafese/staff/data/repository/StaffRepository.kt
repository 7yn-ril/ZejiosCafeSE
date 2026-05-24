package com.example.zejioscafese.staff.data.repository

import com.example.zejioscafese.core.supabase.SupabaseProvider
import com.example.zejioscafese.core.supabase.SupabaseSessionHelper
import com.example.zejioscafese.staff.data.model.StaffMember
import com.example.zejioscafese.staff.data.model.StaffStatus
import com.example.zejioscafese.staff.data.remote.dto.StaffMemberDto
import com.example.zejioscafese.staff.data.remote.dto.StaffMemberInsertDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StaffRepository(
    private val clientProvider: () -> SupabaseClient = { SupabaseProvider.client }
) {

    private val supabaseClient: SupabaseClient
        get() = clientProvider()

    suspend fun fetchStaff(includeInactive: Boolean = false): List<StaffMember> {
        return withContext(Dispatchers.IO) {
            SupabaseSessionHelper.withJwtRetry(supabaseClient) {
                val query = supabaseClient
                    .from(STAFF_TABLE)
                    .select {
                        if (!includeInactive) {
                            filter { eq("staff_is_active", true) }
                        }
                        order(column = "staff_employee_id", order = Order.ASCENDING)
                    }
                    .decodeList<StaffMemberDto>()

                query.map(StaffMemberDto::toStaffMember)
            }
        }
    }

    suspend fun createStaff(
        fullName: String,
        role: String,
        email: String?,
        phone: String?,
        existingEmployeeIds: List<String>,
        existingIds: List<String>,
        address: String? = null,
        birthdate: LocalDate? = null,
        gender: String? = null,
        emergencyContactName: String? = null,
        emergencyContactPhone: String? = null,
        emergencyContactRelationship: String? = null
    ): StaffMember {
        val normalizedName = fullName.trim()
        require(normalizedName.isNotEmpty()) { "Staff name cannot be empty." }
        val normalizedRole = role.trim().ifEmpty { "Staff" }
        val normalizedEmail = email?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedPhone = phone?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedAddress = address?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedGender = gender?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedEcName = emergencyContactName?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedEcPhone = emergencyContactPhone?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedEcRelationship = emergencyContactRelationship?.trim()?.takeIf { it.isNotEmpty() }

        val newId = nextId(existingIds)
        val newEmployeeId = nextEmployeeId(existingEmployeeIds)

        return withContext(Dispatchers.IO) {
            SupabaseSessionHelper.withJwtRetry(supabaseClient) {
                runCatching {
                    supabaseClient
                        .from(STAFF_TABLE)
                        .insert(
                            StaffMemberInsertDto(
                                staffId = newId,
                                staffEmployeeId = newEmployeeId,
                                staffFullName = normalizedName,
                                staffRole = normalizedRole,
                                staffEmail = normalizedEmail,
                                staffPhone = normalizedPhone,
                                staffStatus = StaffStatus.ACTIVE.wireValue,
                                staffIsActive = true,
                                staffAddress = normalizedAddress,
                                staffBirthdate = birthdate?.toString(),
                                staffGender = normalizedGender,
                                staffEmergencyContactName = normalizedEcName,
                                staffEmergencyContactPhone = normalizedEcPhone,
                                staffEmergencyContactRelationship = normalizedEcRelationship
                            )
                        )
                }.recoverCatching {
                    supabaseClient
                        .from(STAFF_TABLE)
                        .insert(
                            StaffMemberInsertDto(
                                staffId = newId,
                                staffEmployeeId = newEmployeeId,
                                staffFullName = normalizedName,
                                staffRole = normalizedRole,
                                staffEmail = normalizedEmail,
                                staffPhone = normalizedPhone,
                                staffStatus = StaffStatus.ACTIVE.wireValue,
                                staffIsActive = true
                            )
                        )
                }.getOrThrow()

                StaffMember(
                    id = newId,
                    employeeId = newEmployeeId,
                    fullName = normalizedName,
                    role = normalizedRole,
                    email = normalizedEmail,
                    phone = normalizedPhone,
                    status = StaffStatus.ACTIVE,
                    isActive = true,
                    address = normalizedAddress,
                    birthdate = birthdate,
                    gender = normalizedGender,
                    emergencyContactName = normalizedEcName,
                    emergencyContactPhone = normalizedEcPhone,
                    emergencyContactRelationship = normalizedEcRelationship
                )
            }
        }
    }

    suspend fun updateStaff(
        staffId: String,
        fullName: String,
        role: String,
        email: String?,
        phone: String?,
        address: String? = null,
        birthdate: LocalDate? = null,
        gender: String? = null,
        emergencyContactName: String? = null,
        emergencyContactPhone: String? = null,
        emergencyContactRelationship: String? = null
    ): StaffMember {
        val normalizedName = fullName.trim()
        require(normalizedName.isNotEmpty()) { "Staff name cannot be empty." }
        val normalizedRole = role.trim().ifEmpty { "Staff" }
        val normalizedEmail = email?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedPhone = phone?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedAddress = address?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedGender = gender?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedEcName = emergencyContactName?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedEcPhone = emergencyContactPhone?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedEcRelationship = emergencyContactRelationship?.trim()?.takeIf { it.isNotEmpty() }

        return withContext(Dispatchers.IO) {
            SupabaseSessionHelper.withJwtRetry(supabaseClient) {
                runCatching {
                    supabaseClient
                        .from(STAFF_TABLE)
                        .update(
                            {
                                set("staff_full_name", normalizedName)
                                set("staff_role", normalizedRole)
                                set("staff_email", normalizedEmail)
                                set("staff_phone", normalizedPhone)
                                set("staff_address", normalizedAddress)
                                set("staff_birthdate", birthdate?.toString())
                                set("staff_gender", normalizedGender)
                                set("staff_emergency_contact_name", normalizedEcName)
                                set("staff_emergency_contact_phone", normalizedEcPhone)
                                set("staff_emergency_contact_relationship", normalizedEcRelationship)
                            }
                        ) {
                            filter { eq("staff_id", staffId) }
                        }
                }.recoverCatching {
                    supabaseClient
                        .from(STAFF_TABLE)
                        .update(
                            {
                                set("staff_full_name", normalizedName)
                                set("staff_role", normalizedRole)
                                set("staff_email", normalizedEmail)
                                set("staff_phone", normalizedPhone)
                            }
                        ) {
                            filter { eq("staff_id", staffId) }
                        }
                }.getOrThrow()

                supabaseClient
                    .from(STAFF_TABLE)
                    .select { filter { eq("staff_id", staffId) } }
                    .decodeSingle<StaffMemberDto>()
                    .toStaffMember()
            }
        }
    }

    suspend fun softDeleteStaff(staffId: String) {
        withContext(Dispatchers.IO) {
            SupabaseSessionHelper.withJwtRetry(supabaseClient) {
                supabaseClient
                    .from(STAFF_TABLE)
                    .update(
                        {
                            set("staff_is_active", false)
                        }
                    ) {
                        filter { eq("staff_id", staffId) }
                    }
            }
        }
    }

    suspend fun restoreStaff(staffId: String) {
        withContext(Dispatchers.IO) {
            SupabaseSessionHelper.withJwtRetry(supabaseClient) {
                supabaseClient
                    .from(STAFF_TABLE)
                    .update(
                        {
                            set("staff_is_active", true)
                        }
                    ) {
                        filter { eq("staff_id", staffId) }
                    }
            }
        }
    }

    private fun nextId(existingIds: List<String>): String {
        val maxValue = existingIds
            .mapNotNull { it.removePrefix(STAFF_ID_PREFIX).toIntOrNull() }
            .maxOrNull() ?: 0
        return STAFF_ID_PREFIX + (maxValue + 1).toString().padStart(3, '0')
    }

    private fun nextEmployeeId(existingEmployeeIds: List<String>): String {
        val pattern = Regex("EMP[_-](\\d+)", RegexOption.IGNORE_CASE)
        val maxValue = existingEmployeeIds
            .mapNotNull { pattern.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return EMPLOYEE_ID_PREFIX + (maxValue + 1).toString().padStart(3, '0')
    }

    private companion object {
        const val STAFF_TABLE = "staff"
        const val STAFF_ID_PREFIX = "STAFF-"
        const val EMPLOYEE_ID_PREFIX = "#EMP_"
    }
}
