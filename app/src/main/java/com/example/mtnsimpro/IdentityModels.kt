package com.example.mtnsimpro

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ExtractedIdentity(
    val firstName: String? = null,
    val lastName: String? = null,
    val middleName: String? = null,
    val birthDate: String? = null,
    val birthPlace: String? = null,
    val expiryDate: String? = null,
    val idNumber: String? = null,
    val nationality: String? = null,
    val gender: String? = null,
    val address: String? = null,
    val documentType: String? = null, // CNI, Passport, Driver License, Consular Card, Other
    val title: String? = null,
    val summary: String? = null,
    val keyDates: List<String> = emptyList(),
    val keyPeople: List<String> = emptyList(),
    val imsi: String? = null,
    val civ: String? = null,
    val ras: String? = null,
    val kgo: String? = null
)

@Serializable
data class NoteLigne(
    val numero: Int,
    val texte: String,
    val estColle: Boolean = false
)

@Serializable
data class IdentityProfile(
    val id: String = UUID.randomUUID().toString(),
    val type: String, // "identity" or "general"
    val data: ExtractedIdentity,
    val scannedAt: String,
    val image: String? = null, // Reference or Base64
    val imsi: String? = null
)

@Serializable
data class AppConfig(
    val id: Int? = null,
    val latestVersionCode: Int = 1,
    val latestVersionName: String = "1.0.0",
    val updateUrl: String? = null,
    val isMandatory: Boolean = false,
    val updateNotes: String? = null
)
