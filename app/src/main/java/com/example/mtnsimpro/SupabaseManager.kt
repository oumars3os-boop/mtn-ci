package com.example.mtnsimpro

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Gestionnaire centralisé pour la connexion à votre projet Supabase.
 *
 * Rendez-vous sur votre tableau de bord Supabase (https://app.supabase.com) :
 * Project Settings -> API pour récupérer votre SUPABASE_URL et votre SUPABASE_ANON_KEY.
 */
object SupabaseManager {

    // ⚠️ Remplacez ces deux constantes par les clés de votre projet Supabase
    const val SUPABASE_URL = "https://ikgsdrjgrvrymwnwzltu.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_PyeqRFOgHo1aomCeP9CEiQ_gPx3jLrE"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Postgrest) // Pour requêter la base de données PostgreSQL
            install(Storage)   // Pour envoyer/récupérer des images et fichiers
            install(Auth)      // Pour gérer les connexions / inscriptions utilisateurs
        }
    }
}
