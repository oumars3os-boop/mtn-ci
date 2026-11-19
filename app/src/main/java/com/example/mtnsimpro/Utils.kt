package com.example.mtnsimpro

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

fun cleanText(text: String): String {
    return text.replace(Regex("^(nom|prénoms?|n[°º]|numéro|né(e)? le|le|à|expire|exp|date|nationalité|sexe|id|naissance)[:\\s-]+", RegexOption.IGNORE_CASE), "").trim()
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("ID Data", cleanText(text))
    clipboard.setPrimaryClip(clip)
}
