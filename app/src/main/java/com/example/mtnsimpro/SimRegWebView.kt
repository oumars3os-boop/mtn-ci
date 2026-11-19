package com.example.mtnsimpro

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mtnsimpro.ui.theme.PrimaryBlue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.webkit.CookieManager
import android.os.Bundle
import org.json.JSONArray
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SimRegWebView(
    viewModel: IdentityViewModel,
    modifier: Modifier = Modifier,
    selectedProfile: IdentityProfile? = null,
    onWebViewCreated: (WebView) -> Unit = {},
    onFieldFocused: (Boolean) -> Unit = {},
    onPrenomDetected: (Boolean) -> Unit = {},
    onCameraDetected: (Boolean) -> Unit = {},
) {
    val androidUA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isAutofillingProcess by remember { mutableStateOf(false) }

    // Demande automatique des permissions au chargement
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        permissionsLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO
            )
        )
    }
    
    // States for the new Pink Top-Left Button (IMSI Tool)
    var showTopLeftMenu by remember { mutableStateOf(false) }
    var isImsiFieldFocused by remember { mutableStateOf(false) }
    val imsiListState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    // Règle 3 : Sauvegarde persistante (Mémorisation)
    val prefs = remember { context.getSharedPreferences("pink_panel_prefs", android.content.Context.MODE_PRIVATE) }
    val gson = remember { com.google.gson.Gson() }
    
    val clipboardHistory = remember { 
        val saved = prefs.getString("history", null)
        val list = if (saved != null) {
            val type = object : com.google.gson.reflect.TypeToken<List<NoteLigne>>() {}.type
            try { gson.fromJson<List<NoteLigne>>(saved, type) } catch(_: Exception) { emptyList() }
        } else emptyList()
        mutableStateListOf<NoteLigne>().apply { addAll(list) }
    }

    LaunchedEffect(clipboardHistory.toList()) {
        prefs.edit().putString("history", gson.toJson(clipboardHistory.toList())).apply()
    }

    val webViewState = rememberSaveable { Bundle() }
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        filePathCallback?.onReceiveValue(if (uris.isNotEmpty()) uris.toTypedArray() else null)
        filePathCallback = null
    }

    val singleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        filePathCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        filePathCallback = null
    }

    LaunchedEffect(selectedProfile, webViewInstance) {
        if ((selectedProfile != null) && (webViewInstance != null)) {
            autofillSimReg(webViewInstance!!, selectedProfile, viewModel, scope) {
                isAutofillingProcess = it
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        @Suppress("DEPRECATION")
                        databaseEnabled = true
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(false)
                        allowFileAccess = true
                        allowContentAccess = true
                        loadWithOverviewMode = false
                        useWideViewPort = false
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = androidUA
                        mediaPlaybackRequiresUserGesture = false
                        setGeolocationEnabled(true)
                        setGeolocationDatabasePath(context.filesDir.path)
                        
                        // Optimisations de vitesse
                        cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)
                        @Suppress("DEPRECATION")
                        lightTouchEnabled = true
                    }
                    
                    // Force l'accélération matérielle
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    
                    // Pré-chargement des cookies pour éviter les attentes réseau
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    
                    // Pré-chargement DNS et cookies
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.grant(request.resources)
                        }

                        override fun onGeolocationPermissionsShowPrompt(
                            origin: String?,
                            callback: android.webkit.GeolocationPermissions.Callback?
                        ) {
                            callback?.invoke(origin, true, false)
                        }

                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallbackIn: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = filePathCallbackIn
                            val mimeTypes = fileChooserParams?.acceptTypes?.filter { it.isNotEmpty() }?.toTypedArray() ?: arrayOf("*/*")
                            try {
                                if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                                    filePickerLauncher.launch(mimeTypes)
                                } else {
                                    singleFilePickerLauncher.launch(mimeTypes)
                                }
                            } catch (_: Exception) {
                                filePathCallback?.onReceiveValue(null)
                                filePathCallback = null
                                return false
                            }
                            return true
                        }
                    }
                    
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        @Suppress("unused")
                        fun onFormReady() {}

                        @JavascriptInterface
                        @Suppress("unused")
                        fun onTextCaptured(text: String) {
                            // Règle 5 : Restriction stricte au champ IMSI
                            if (!isImsiFieldFocused) return
                            if (text.isBlank()) return

                            scope.launch(Dispatchers.Main) {
                                // Règle 6 : Copie auto dans le presse-papiers
                                try {
                                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("SimReg", text))
                                } catch (_: Exception) {}

                                // Règle 7 & 9 : Détection auto (10 chiffres) + Blocage doublons
                                if (text.all { it.isDigit() }) {
                                    if (clipboardHistory.none { it.texte == text }) {
                                        // Règle 8 : Capacité 50 lignes
                                        if (clipboardHistory.size >= 50) {
                                            clipboardHistory.removeAt(clipboardHistory.size - 1)
                                        }
                                        clipboardHistory.add(0, NoteLigne(numero = 1, texte = text, estColle = false))
                                        // Mettre à jour les numéros pour le reste de la liste
                                        for (i in 1 until clipboardHistory.size) {
                                            clipboardHistory[i] = clipboardHistory[i].copy(numero = i + 1)
                                        }
                                    }
                                }
                            }
                        }

                        @JavascriptInterface
                        @Suppress("unused")
                        fun onPrenomDetected(detected: Boolean) {
                            scope.launch(Dispatchers.Main) {
                                onPrenomDetected(detected)
                            }
                        }

                        @JavascriptInterface
                        @Suppress("unused")
                        fun onCameraDetected(detected: Boolean) {
                            scope.launch(Dispatchers.Main) {
                                onCameraDetected(detected)
                            }
                        }

                        @JavascriptInterface
                        @Suppress("unused")
                        fun onFieldFocused(focused: Boolean, elementId: String?, elementName: String?, placeholder: String?) {
                            val isImsi = elementId?.contains("input-7") == true || 
                                         elementId?.contains("input-3") == true || 
                                         elementName?.contains("input-7") == true ||
                                         elementName?.contains("input-3") == true ||
                                         placeholder?.contains("imsi", ignoreCase = true) == true ||
                                         elementId?.contains("imsi", ignoreCase = true) == true
                            
                            if (isImsi) isImsiFieldFocused = focused
                            onFieldFocused(focused)
                        }

                        @JavascriptInterface
                        @Suppress("unused")
                        fun triggerGallery() {
                            viewModel.requestGalleryFromWebView()
                        }
                    }, "AndroidHook")
                    
                    if (webViewState.isEmpty) loadUrl(Constants.TARGET_URL) else restoreState(webViewState)
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    onWebViewCreated(this)
                    webViewInstance = this
                }
            },
            update = { 
                webViewInstance = it
                onWebViewCreated(it)
                it.saveState(webViewState)
            }
        )

        // Règle 2 : Positionnement (Pousse vers le HAUT avec top=2.dp)
        if (isImsiFieldFocused || showTopLeftMenu) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 0.dp, start = 8.dp) // Règle 2 : COLLE vers le HAUT
            ) {
                Column {
                    Surface(
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { showTopLeftMenu = !showTopLeftMenu },
                        color = Color(0xFFE91E63),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Bolt, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }

                    AnimatedVisibility(visible = showTopLeftMenu || isImsiFieldFocused) {
                        Card(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .width(90.dp) // Règle 2 : LARGEUR AGRANDIE (90.dp)
                                .height(157.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(2.dp)) {
                                if (clipboardHistory.isEmpty()) {
                                    Text("Vide", color = Color.Gray, fontSize = 8.sp, modifier = Modifier.padding(4.dp))
                                } else {
                                    androidx.compose.foundation.lazy.LazyColumn(
                                        state = imsiListState,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        items(
                                            count = clipboardHistory.size,
                                            key = { index -> clipboardHistory[index].texte }
                                        ) { index ->
                                            val note = clipboardHistory[index]
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { 
                                                        val js = """
                                                            (function() {
                                                                function setValue(t, v) {
                                                                    try {
                                                                        const vs = Object.getOwnPropertyDescriptor(t, 'value')?.set;
                                                                        const p = Object.getPrototypeOf(t);
                                                                        const pvs = Object.getOwnPropertyDescriptor(p, 'value')?.set;
                                                                        if (pvs && vs !== pvs) pvs.call(target, v); else target.value = v;
                                                                    } catch (e) { t.value = v; }
                                                                    t.dispatchEvent(new Event('input', { bubbles: true }));
                                                                    t.dispatchEvent(new Event('change', { bubbles: true }));
                                                                }
                                                                function inject(d, v) {
                                                                    const el = d.getElementById('ion-input-7') || d.getElementById('ion-input-3') || d.activeElement;
                                                                    if (el) {
                                                                        setValue(el, v);
                                                                        if (el.shadowRoot) {
                                                                            const i = el.shadowRoot.querySelector('input, textarea');
                                                                            if (i) setValue(i, v);
                                                                        }
                                                                        return true;
                                                                    }
                                                                    return false;
                                                                }
                                                                function sI(d, v) {
                                                                    if (inject(d, v)) return true;
                                                                    const f = d.querySelectorAll('iframe');
                                                                    for (let i = 0; i < f.length; i++) {
                                                                        try { if (sI(f[i].contentDocument || f[i].contentWindow.document, v)) return true; } catch(e) {}
                                                                    }
                                                                    return false;
                                                                }
                                                                sI(document, '${note.texte}');
                                                            })();
                                                        """.trimIndent()
                                                        webViewInstance?.evaluateJavascript(js, null)
                                                        clipboardHistory[index] = note.copy(estColle = true)
                                                        showTopLeftMenu = false 
                                                    }
                                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Règle 3 & 5 : Numérotation Chronologique (1, 2, 3...)
                                                Text(
                                                    text = "${index + 1}.",
                                                    color = PrimaryBlue,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.width(18.dp)
                                                )
                                                Text(
                                                    text = note.texte,
                                                    color = if (note.estColle) Color(0xFF4ADE80) else Color.White,
                                                    fontSize = 9.sp,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Visible
                                                )
                                            }
                                            if (index < clipboardHistory.size - 1) HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "IMSI",
                                            color = Color(0xFFE91E63), // ROSE
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(start = 2.dp)
                                        )
                                        IconButton(onClick = { showTopLeftMenu = false }, modifier = Modifier.size(16.dp)) {
                                            Icon(Icons.Default.Close, null, tint = Color(0xFFE91E63), modifier = Modifier.size(10.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = isAutofillingProcess, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Bolt, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("REMPLISSAGE EN COURS...", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 14.sp)
                    Text("OPTIMISATION DES DONNÉES...", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }


    }
}

fun autofillSimReg(webView: WebView, profile: IdentityProfile, viewModel: IdentityViewModel, scope: CoroutineScope, onLoadingChange: (Boolean) -> Unit) {
    val data = profile.data
    onLoadingChange(true)
    val jsHelper = """
        function setFieldValue(s, v, d = document) {
            const el = d.querySelector(s);
            if (el) {
                const sV = (t, val) => {
                    try {
                        const vS = Object.getOwnPropertyDescriptor(t, 'value')?.set;
                        const p = Object.getPrototypeOf(t);
                        const pVS = Object.getOwnPropertyDescriptor(p, 'value')?.set;
                        if (pVS && vS !== pVS) pVS.call(t, val); else t.value = val;
                    } catch (err) { t.value = val; }
                    t.dispatchEvent(new Event('input', { bubbles: true }));
                    t.dispatchEvent(new Event('change', { bubbles: true }));
                };
                sV(el, v);
                if (el.shadowRoot) {
                    const i = el.shadowRoot.querySelector('input, textarea, select');
                    if (i) sV(i, v);
                }
                return true;
            }
            const f = d.querySelectorAll('iframe');
            for (let i = 0; i < f.length; i++) {
                try {
                    const fD = f[i].contentDocument || f[i].contentWindow.document;
                    if (setFieldValue(s, v, fD)) return true;
                } catch(e) {}
            }
            return false;
        }
    """.trimIndent()
    val preciseMappings = mapOf(
        "[name='text_a88575f9-9fc4-484e-824c-1d22e455bd9c']" to data.firstName,
        "[name='text-7a6fdcab-2654-4c81-b86c-86630c376b60']" to data.lastName,
        "#date_50123028-0ff9-401f-9bb8-4113b400bf21" to data.birthDate,
        "[name='text_474ae0d7-fa54-415c-9e4b-e419c15f0eb6']" to data.birthPlace,
        "#date-71450376-37fe-45e9-8bdf-8ee7f0c7c261" to data.expiryDate
    )
    var script = jsHelper + "\n"
    preciseMappings.forEach { (s, v) -> if (!v.isNullOrEmpty()) script += "setFieldValue(\"$s\", \"$v\");\n" }
    webView.evaluateJavascript(script, null)
    scope.launch(Dispatchers.Main) {
        webView.evaluateJavascript("(function() { const fields = Array.from(document.querySelectorAll('input:not([type=\"hidden\"]), select, textarea')); return JSON.stringify(fields.map(f => ({ id: f.id, name: f.name, placeholder: f.placeholder, label: document.querySelector('label[for=\"' + f.id + '\"]')?.textContent || \"\" }))); })()") { result ->
            val jsonString = result?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"")
            if (!jsonString.isNullOrEmpty()) {
                try {
                    val fields = JSONArray(jsonString)
                    scope.launch(Dispatchers.IO) {
                        try {
                            for (i in 0 until fields.length()) {
                                val field = fields.getJSONObject(i)
                                val label = field.optString("label").ifEmpty { field.optString("placeholder").ifEmpty { field.optString("name") } }
                                if (label.isNotEmpty()) {
                                    val mapping = viewModel.mapFieldIntelligently(label, profile.data)
                                    if (mapping != null) {
                                        val v = mapping.optString("value")
                                        if (v.isNotEmpty() && mapping.optDouble("confidence", 0.0) > 0.4) {
                                            val sel = if (field.optString("id").isNotEmpty()) "#${field.optString("id")}" else "[name='${field.optString("name")}']"
                                            scope.launch(Dispatchers.Main) { webView.evaluateJavascript("setFieldValue(\"$sel\", \"$v\");", null) }
                                        }
                                    }
                                }
                            }
                        } finally { scope.launch(Dispatchers.Main) { onLoadingChange(false) } }
                    }
                } catch (_: Exception) { onLoadingChange(false) }
            } else onLoadingChange(false)
        }
    }
}
