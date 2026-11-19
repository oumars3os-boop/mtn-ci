# 1. Optimisation et Obfuscation de base
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# 2. Obfusquer tout le code du projet sauf les classes Android de base
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# 3. Sécuriser MTN SIM PRO - Obfusquer les modèles et la logique interne
-keepclassmembers class com.example.mtnsimpro.** {
    public <methods>;
}

# 4. Protection spéciale WebView
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 5. Ne pas obfusquer les bibliothèques Jetpack Compose (pour éviter les crashs UI)
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# 6. Supprimer les logs en production pour éviter les fuites d'infos
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# 7. Cacher les noms des fichiers sources et les numéros de lignes
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
