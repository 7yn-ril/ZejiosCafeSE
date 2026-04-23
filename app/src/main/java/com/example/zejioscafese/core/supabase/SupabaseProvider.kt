package com.example.zejioscafese.core.supabase

import com.example.zejioscafese.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlin.time.Duration.Companion.seconds

object SupabaseProvider {

    val client by lazy {
        val supabaseUrl = BuildConfig.SUPABASE_URL.trim()
        val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY.trim()

        require(supabaseUrl.isNotEmpty()) {
            "Missing SUPABASE_URL in local.properties"
        }
        require(supabaseAnonKey.isNotEmpty()) {
            "Missing SUPABASE_ANON_KEY in local.properties"
        }

        createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseAnonKey
        ) {
            requestTimeout = 30.seconds

            install(Auth) {
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
                autoSaveToStorage = true
            }
            install(Postgrest) {
                defaultSchema = "public"
            }
        }
    }
}
