package com.example.mtnsimpro

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Base64
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest

object SecurityUtils {
    // Signature officielle attendue (SHA-256 encodé en Base64)
    // NOTE: Remplacez par votre signature réelle générée lors du build release.
    private const val OFFICIAL_SIGNATURE = "PASTE_YOUR_OFFICIAL_SIGNATURE_SHA256_BASE64_HERE"

    /**
     * 1. Vérification de l'intégrité de la signature de l'APK
     */
    @SuppressLint("PackageManagerGetSignatures")
    fun checkAppSignature(context: Context): Boolean {
        try {
            val packageManager = context.packageManager
            val packageName = context.packageName
            
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                packageInfo.signatures
            }

            if (signatures.isNullOrEmpty()) return false

            for (signature in signatures) {
                val md = MessageDigest.getInstance("SHA-256")
                md.update(signature.toByteArray())
                val currentSignature = Base64.encodeToString(md.digest(), Base64.NO_WRAP)
                if (currentSignature == OFFICIAL_SIGNATURE) return true
            }
        } catch (_: Exception) {}
        
        // Par défaut retourne true en debug, mettre false en production finale
        return true 
    }

    /**
     * 2. Détection du Root
     */
    fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine() != null
        } catch (_: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }

    /**
     * 3. Détection de l'Émulateur
     */
    fun isRunningOnEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
    }

    /**
     * 4. Détection du Debugging (ADB et Debugger attaché)
     */
    fun isDebuggingEnabled(context: Context): Boolean {
        val adbEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) != 0
        val isDebuggerAttached = android.os.Debug.isDebuggerConnected()
        return adbEnabled || isDebuggerAttached
    }

    /**
     * 5. Détection de Xposed/Frida (basique)
     */
    fun isTampered(context: Context): Boolean {
        val packageManager = context.packageManager
        val installedPackages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val suspiciousApps = arrayOf("de.robv.android.xposed.installer", "com.saurik.substrate", "com.topjohnwu.magisk")
        
        for (app in installedPackages) {
            if (suspiciousApps.contains(app.packageName)) return true
        }
        return false
    }
}
