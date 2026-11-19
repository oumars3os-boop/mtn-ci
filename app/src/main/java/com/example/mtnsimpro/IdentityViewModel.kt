package com.example.mtnsimpro

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface IdentityUiState {
    data object Initial : IdentityUiState
    data object Loading : IdentityUiState
    data class Success(val profile: IdentityProfile) : IdentityUiState
    data class Error(val errorMessage: String) : IdentityUiState
}

class IdentityViewModel(application: Application) : AndroidViewModel(application) {
    private val profileManager = ProfileManager(application)
    private val mistralApiKey = "XhwjJVYyq45XTW9abYYruQGh31vUgx7C"
    private val mistralModel = "pixtral-12b-2409"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _uiState = MutableStateFlow<IdentityUiState>(IdentityUiState.Initial)
    val uiState: StateFlow<IdentityUiState> = _uiState.asStateFlow()

    private val _profiles = MutableStateFlow(profileManager.loadProfiles())
    val profiles: StateFlow<List<IdentityProfile>> = _profiles.asStateFlow()

    private val _galleryRequest = MutableStateFlow(false)
    val galleryRequest: StateFlow<Boolean> = _galleryRequest.asStateFlow()

    fun requestGalleryFromWebView() {
        _galleryRequest.value = true
    }

    fun onGalleryOpened() {
        _galleryRequest.value = false
    }

    fun scanDocument(bitmap: Bitmap, mode: String = "identity") {
        _uiState.value = IdentityUiState.Loading

        viewModelScope.launch {
            try {
                // Application du traitement d'image avancé pour l'identité
                val processedBitmap = if (mode == "identity") {
                    ImageProcessor.prepareForOcr(bitmap)
                } else {
                    bitmap
                }

                if (mode == "identity") {
                    performIdentityScan(processedBitmap)
                } else {
                    performGeneralScan(processedBitmap)
                }
            } catch (_: Exception) {
                _uiState.value = IdentityUiState.Error("Unknown error")
            }
        }
    }

    private suspend fun callMistral(prompt: String, bitmap: Bitmap? = null): String? = withContext(Dispatchers.IO) {
        val messages = JSONArray()
        val userMessage = JSONObject()
        userMessage.put("role", "user")
        
        val content = JSONArray()
        
        // Text part
        val textPart = JSONObject()
        textPart.put("type", "text")
        textPart.put("text", prompt)
        content.put(textPart)
        
        // Image part if provided
        if (bitmap != null) {
            val imagePart = JSONObject()
            imagePart.put("type", "image_url")
            val imageUrl = JSONObject()
            imageUrl.put("url", "data:image/jpeg;base64,${bitmapToBase64(bitmap)}")
            imagePart.put("image_url", imageUrl)
            content.put(imagePart)
        }
        
        userMessage.put("content", content)
        messages.put(userMessage)
        
        val jsonRequest = JSONObject()
        jsonRequest.put("model", mistralModel)
        jsonRequest.put("messages", messages)
        jsonRequest.put("response_format", JSONObject().put("type", "json_object"))
        
        val body = jsonRequest.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.mistral.ai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $mistralApiKey")
            .post(body)
            .build()
            
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val responseBody = response.body?.string() ?: return@withContext null
            val jsonResponse = JSONObject(responseBody)
            jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    private suspend fun performIdentityScan(bitmap: Bitmap) {
        val prompt = """
            Tu es un expert en extraction de données d'identité (OCR) de haute précision. 
            L'image a été prétraitée (Rotation, Recadrage, Noir et Blanc) pour optimiser ta lecture.
            
            Analyse cette image et extrais les informations suivantes avec précision.
            IMPORTANT: Ne copie que la valeur brute. Enlève IMPÉRATIVEMENT les étiquettes ou préfixes comme "Nom:", "Prénoms:", "N°:", etc. 
            Retourne UNIQUEMENT un objet JSON. 
            Champs requis: firstName, lastName, idNumber, documentType.
            Champs optionnels: middleName, birthDate (JJ/MM/AAAA), expiryDate (JJ/MM/AAAA), nationality, gender, birthPlace, address, imsi.
            Si un champ est illisible ou absent, utilise une chaîne vide.
            Types de documents autorisés: CNI, Passport, Driver License, Consular Card, Other.
        """.trimIndent()

        val jsonText = callMistral(prompt, bitmap)?.trim()?.removeSurrounding("```json", "```")?.trim()
        
        if (jsonText != null) {
            val json = JSONObject(jsonText)
            val data = ExtractedIdentity(
                firstName = json.optString("firstName", "").takeIf { it.isNotEmpty() },
                lastName = json.optString("lastName", "").takeIf { it.isNotEmpty() },
                middleName = json.optString("middleName", "").takeIf { it.isNotEmpty() },
                birthDate = json.optString("birthDate", "").takeIf { it.isNotEmpty() },
                birthPlace = json.optString("birthPlace", "").takeIf { it.isNotEmpty() },
                expiryDate = json.optString("expiryDate", "").takeIf { it.isNotEmpty() },
                idNumber = json.optString("idNumber", "").takeIf { it.isNotEmpty() },
                nationality = json.optString("nationality", "").takeIf { it.isNotEmpty() },
                gender = json.optString("gender", "").takeIf { it.isNotEmpty() },
                address = json.optString("address", "").takeIf { it.isNotEmpty() },
                documentType = json.optString("documentType", "Other"),
                imsi = json.optString("imsi", "").takeIf { it.isNotEmpty() },
            )
            
            val profile = createProfile("identity", data, bitmapToBase64(bitmap))
            updateProfiles(listOf(profile) + _profiles.value)
            _uiState.value = IdentityUiState.Success(profile)
        } else {
            _uiState.value = IdentityUiState.Error("Failed to extract identity data via Mistral")
        }
    }

    private suspend fun performGeneralScan(bitmap: Bitmap) {
        val prompt = """
            Analyse ce document général (facture, contrat, reçu, lettre, etc.). 
            Extraits le titre du document, un résumé court, les dates importantes et les personnes ou entreprises citées.
            Retourne UNIQUEMENT un objet JSON with:
            title, summary, keyDates (Array of strings), keyPeople (Array of strings), docType.
        """.trimIndent()

        val jsonText = callMistral(prompt, bitmap)?.trim()?.removeSurrounding("```json", "```")?.trim()
        
        if (jsonText != null) {
            val json = JSONObject(jsonText)
            val data = ExtractedIdentity(
                title = json.optString("title", "").takeIf { it.isNotEmpty() },
                summary = json.optString("summary", "").takeIf { it.isNotEmpty() },
                keyDates = json.optJSONArray("keyDates")?.let { arr -> 
                    List(arr.length()) { i -> arr.getString(i) } 
                } ?: emptyList(),
                keyPeople = json.optJSONArray("keyPeople")?.let { arr -> 
                    List(arr.length()) { i -> arr.getString(i) } 
                } ?: emptyList(),
                documentType = json.optString("docType", "").takeIf { it.isNotEmpty() },
            )
            
            val profile = createProfile("general", data, bitmapToBase64(bitmap))
            updateProfiles(listOf(profile) + _profiles.value)
            _uiState.value = IdentityUiState.Success(profile)
        } else {
            _uiState.value = IdentityUiState.Error("Failed to analyze general document via Mistral")
        }
    }

    private fun createProfile(type: String, data: ExtractedIdentity, image: String?): IdentityProfile {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        return IdentityProfile(
            type = type,
            data = data,
            image = image,
            scannedAt = sdf.format(Date()),
            imsi = data.imsi ?: data.idNumber // Use idNumber as fallback if imsi is null
        )
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
    }

    private fun updateProfiles(newProfiles: List<IdentityProfile>) {
        val oldList = _profiles.value
        _profiles.value = newProfiles
        profileManager.saveProfiles(newProfiles)

        // Sauvegarder automatiquement les nouveaux profils scannés sur Supabase
        if (newProfiles.size > oldList.size) {
            val newlyAdded = newProfiles.firstOrNull()
            if (newlyAdded != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    UpdateManager.saveIdentityToSupabase(newlyAdded)
                }
            }
        }
    }

    fun deleteProfile(id: String) {
        updateProfiles(_profiles.value.filter { it.id != id })
    }

    fun clearAllProfiles() {
        updateProfiles(emptyList())
    }

    fun resetScanState() {
        _uiState.value = IdentityUiState.Initial
    }

    suspend fun mapFieldIntelligently(label: String, identity: ExtractedIdentity): JSONObject? {
        return try {
            val prompt = """
                Tu es un assistant expert en remplissage de formulaires administratifs. 
                On te donne une étiquette de champ de formulaire (label) et les données extraites d'une pièce d'identité.
                Ton but est de trouver la donnée qui correspond le mieux à cette étiquette.
                
                DONNÉES d'IDENTITÉ:
                ${JSONObject().apply {
                    put("firstName", identity.firstName)
                    put("lastName", identity.lastName)
                    put("middleName", identity.middleName)
                    put("birthDate", identity.birthDate)
                    put("birthPlace", identity.birthPlace)
                    put("expiryDate", identity.expiryDate)
                    put("idNumber", identity.idNumber)
                    put("nationality", identity.nationality)
                    put("gender", identity.gender)
                    put("address", identity.address)
                    put("documentType", identity.documentType)
                    put("imsi", identity.imsi)
                }}
                
                ÉTIQUETTE DU CHAMP: "$label"
                
                Retourne un objet JSON avec value, confidence et reason.
            """.trimIndent()

            val jsonText = callMistral(prompt, null)?.trim()?.removeSurrounding("```json", "```")?.trim()
            if (jsonText != null) JSONObject(jsonText) else null
        } catch (_: Exception) {
            null
        }
    }
}
