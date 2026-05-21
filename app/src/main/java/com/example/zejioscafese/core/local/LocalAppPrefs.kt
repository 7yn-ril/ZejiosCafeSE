package com.example.zejioscafese.core.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object LocalAppPrefs {

    private const val PREFS_NAME = "zejios_app_state"

    private const val KEY_PROFILE_NAME = "profile_name"
    private const val KEY_PROFILE_EMAIL = "profile_email"
    private const val KEY_PROFILE_ROLE = "profile_role"
    private const val KEY_PROFILE_PHONE = "profile_phone"
    private const val KEY_PROFILE_ADDRESS = "profile_address"
    private const val KEY_PROFILE_SAVED = "profile_saved"

    private const val KEY_STAFF_LIST = "staff_list"

    data class StoredProfile(
        val name: String,
        val email: String,
        val role: String,
        val phone: String,
        val address: String
    )

    data class StoredStaff(
        val name: String,
        val employeeId: String,
        val role: String
    )

    fun loadProfile(context: Context): StoredProfile? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PROFILE_SAVED, false)) {
            return null
        }
        return StoredProfile(
            name = prefs.getString(KEY_PROFILE_NAME, "").orEmpty(),
            email = prefs.getString(KEY_PROFILE_EMAIL, "").orEmpty(),
            role = prefs.getString(KEY_PROFILE_ROLE, "").orEmpty(),
            phone = prefs.getString(KEY_PROFILE_PHONE, "").orEmpty(),
            address = prefs.getString(KEY_PROFILE_ADDRESS, "").orEmpty()
        )
    }

    fun saveProfile(context: Context, profile: StoredProfile) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROFILE_NAME, profile.name)
            .putString(KEY_PROFILE_EMAIL, profile.email)
            .putString(KEY_PROFILE_ROLE, profile.role)
            .putString(KEY_PROFILE_PHONE, profile.phone)
            .putString(KEY_PROFILE_ADDRESS, profile.address)
            .putBoolean(KEY_PROFILE_SAVED, true)
            .apply()
    }

    fun loadStaff(context: Context): List<StoredStaff> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_STAFF_LIST, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        StoredStaff(
                            name = obj.optString("name"),
                            employeeId = obj.optString("employeeId"),
                            role = obj.optString("role")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveStaff(context: Context, staff: List<StoredStaff>) {
        val array = JSONArray()
        staff.forEach { member ->
            val obj = JSONObject()
                .put("name", member.name)
                .put("employeeId", member.employeeId)
                .put("role", member.role)
            array.put(obj)
        }
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STAFF_LIST, array.toString())
            .apply()
    }
}
