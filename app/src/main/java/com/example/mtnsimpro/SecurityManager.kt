package com.example.mtnsimpro

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class SecurityManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    @SuppressLint("HardwareIds")
    fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    /**
     * Calcule la clé de sécurité basée sur l'ID du téléphone.
     */
    fun calculateSecurityKey(deviceId: String): String {
        val salt = "MTN_PRO_SECURE_2024"
        val input = deviceId + salt
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(8).uppercase()
    }

    fun isActivatedLocally(): Boolean {
        return prefs.getBoolean("is_activated", false)
    }

    fun activate(key: String): Boolean {
        val expectedKey = calculateSecurityKey(getDeviceId())
        if (key.uppercase() == expectedKey) {
            prefs.edit { putBoolean("is_activated", true) }
            return true
        }
        return false
    }

    fun resetLocalActivation() {
        prefs.edit { putBoolean("is_activated", false) }
    }

    fun checkAdminPassword(password: String): Boolean {
        return password == "5544894"
    }

    /**
     * Vérifie le statut en temps réel sur GitHub.
     */
    fun checkRemoteStatus(): Pair<Boolean, Boolean> {
        val currentId = getDeviceId()
        android.util.Log.d("SECURITY", "Vérification distance pour ID: $currentId")
        
        return try {
            val blacklistUrl = "https://raw.githubusercontent.com/oumars3os-boop/Liste-noire/refs/heads/main/Liste%20noire"
            val whitelistUrl = "https://raw.githubusercontent.com/oumars3os-boop/Liste-noire/refs/heads/main/Liste%20blanche"
            
            var isBlocked = false
            var isAutoActivated = false

            // 1. Vérification Liste Noire
            val requestBlack = Request.Builder().url(blacklistUrl).build()
            client.newCall(requestBlack).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful && (body.contains(currentId, ignoreCase = true) || body.contains("BLOCK_ALL", ignoreCase = true))) {
                    isBlocked = true
                }
            }

            // 2. Vérification Liste Blanche (si pas déjà bloqué)
            if (!isBlocked) {
                val requestWhite = Request.Builder().url(whitelistUrl).build()
                client.newCall(requestWhite).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful && body.contains(currentId, ignoreCase = true)) {
                        isAutoActivated = true
                    }
                }
            }

            Pair(isBlocked, isAutoActivated)
        } catch (e: Exception) {
            android.util.Log.e("SECURITY", "Erreur réseau sécurité: ${e.message}")
            Pair(false, false)
        }
    }
}
