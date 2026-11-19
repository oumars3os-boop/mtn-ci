package com.example.mtnsimpro

import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateManager {

    /**
     * Vérifie si une nouvelle version de l'application est disponible sur Supabase.
     * Table attendue dans Supabase : `app_config`
     */
    suspend fun checkAppUpdate(): AppConfigInfo = withContext(Dispatchers.IO) {
        try {
            val config = SupabaseManager.client.from("app_config")
                .select()
                .decodeSingleOrNull<AppConfig>()

            val latestVersionCode = config?.latestVersionCode ?: 1
            val currentVersionCode = BuildConfig.VERSION_CODE
            val isUpdateAvailable = currentVersionCode < latestVersionCode

            AppConfigInfo(
                isUpdateAvailable = isUpdateAvailable,
                latestVersionCode = latestVersionCode,
                latestVersionName = config?.latestVersionName ?: "1.0.0",
                updateUrl = config?.updateUrl,
                isMandatory = config?.isMandatory ?: false,
                updateNotes = config?.updateNotes
            )
        } catch (e: Exception) {
            e.printStackTrace()
            AppConfigInfo(isUpdateAvailable = false)
        }
    }

    /**
     * Enregistre un profil/scan d'identité sur Supabase dans la table `identities`
     */
    suspend fun saveIdentityToSupabase(profile: IdentityProfile): Boolean = withContext(Dispatchers.IO) {
        try {
            SupabaseManager.client.from("identities").insert(profile)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

data class AppConfigInfo(
    val isUpdateAvailable: Boolean = false,
    val latestVersionCode: Int = 1,
    val latestVersionName: String = "1.0.0",
    val updateUrl: String? = null,
    val isMandatory: Boolean = false,
    val updateNotes: String? = null
)
