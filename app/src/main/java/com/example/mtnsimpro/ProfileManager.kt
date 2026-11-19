package com.example.mtnsimpro

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ProfileManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("id_master_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val storageKey = Constants.STORAGE_KEY // Same key as React STORAGE_KEY

    fun saveProfiles(profiles: List<IdentityProfile>) {
        val json = gson.toJson(profiles)
        prefs.edit { putString(storageKey, json) }
    }

    fun loadProfiles(): List<IdentityProfile> {
        val json = prefs.getString(storageKey, null) ?: return emptyList()
        val type = object : TypeToken<List<IdentityProfile>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Suppress("unused")
    fun clearProfiles() {
        prefs.edit { remove(storageKey) }
    }
}
