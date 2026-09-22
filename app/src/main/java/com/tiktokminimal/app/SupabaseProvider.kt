
package com.tiktokminimal.app

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseProvider {
    val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth) {
                scheme = "tiktokminimal"
                host = "auth"
                flowType = FlowType.PKCE
                defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
            }
            install(Postgrest)
            install(Storage)
            install(Realtime)
        }
    }

    fun isConfigured(): Boolean =
        BuildConfig.SUPABASE_URL.startsWith("https://") &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()
}
