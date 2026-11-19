package com.example.mtnsimpro

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mtnsimpro.ui.theme.DarkSurface
import com.example.mtnsimpro.ui.theme.PrimaryBlue
import java.io.ByteArrayOutputStream

enum class Screen {
    Scan,
    Navigate,
    Archive,
    System
}

@Composable
fun MainScreen(
    viewModel: IdentityViewModel = viewModel(),
    securityViewModel: SecurityViewModel = viewModel(),
) {
    val isActivated by securityViewModel.isActivated.collectAsState()
    val isRemoteBlocked by securityViewModel.isRemoteBlocked.collectAsState()
    val deviceId by securityViewModel.deviceId.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                securityViewModel.checkRemoteBlock()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (isRemoteBlocked) {
        RemoteBlockedScreen(deviceId = deviceId)
        return
    }

    if (!isActivated) {
        SecurityScreen(viewModel = securityViewModel)
        return
    }

    var selectedTab by remember { mutableStateOf(Screen.Navigate) }
    val profiles by viewModel.profiles.collectAsState()
    var selectedProfileForAutofill by remember { mutableStateOf<IdentityProfile?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isPrenomPresent by remember { mutableStateOf(false) }
    var isCameraVisible by remember { mutableStateOf(false) }

    // ========== NOUVELLE FONCTIONNALITÉ : GESTION DE LA PRÉVISUALISATION DE L'IMAGE ==========
    var lastInjectedImageBase64 by remember { mutableStateOf<String?>(null) }
    var selectedImageBase64 by remember { mutableStateOf<String?>(null) }
    var showCameraPreview by remember { mutableStateOf(false) }
    var selectionRect by remember { mutableStateOf(Rect(0f, 0f, 200f, 200f)) }
    var isDragging by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                // ========== NOUVEAU : AFFICHER LA PRÉVISUALISATION AU LIEU D'INJECTER DIRECTEMENT ==========
                selectedImageBase64 = "data:image/jpeg;base64,$base64Image"
                showCameraPreview = true
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val focusJs = """
        (function() {
            if (!window.isVirtualCameraSetup) {
                window.isVirtualCameraSetup = true;
                const canvas = document.createElement('canvas');
                canvas.width = 640; canvas.height = 480;
                const ctx = canvas.getContext('2d');
                let currentImg = null;
                let originalImageDataUrl = null;

                function dataURLToBlob(dataUrl) {
                    const parts = dataUrl.split(';base64,');
                    const contentType = parts[0].split(':')[1];
                    const raw = window.atob(parts[1]);
                    const rawLength = raw.length;
                    const uInt8Array = new Uint8Array(rawLength);
                    for (let i = 0; i < rawLength; ++i) {
                        uInt8Array[i] = raw.charCodeAt(i);
                    }
                    return new Blob([uInt8Array], { type: contentType });
                }

                const originalGUM = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
                const originalEnumerateDevices = navigator.mediaDevices.enumerateDevices?.bind(navigator.mediaDevices);

                navigator.mediaDevices.enumerateDevices = async function() {
                    const originalDevices = originalEnumerateDevices ? await originalEnumerateDevices() : [];
                    return [
                        {
                            deviceId: 'default',
                            kind: 'videoinput',
                            label: "Integrated Camera (046d:082b)",
                            groupId: 'default-group'
                        },
                        ...originalDevices.filter(d => d.kind !== 'videoinput')
                    ];
                };

                window.setVirtualCameraImage = function(data) {
                    const img = new Image();
                    img.onload = () => {
                        currentImg = img;
                        originalImageDataUrl = data;
                        if (window.AndroidHook && window.AndroidHook.onImageInjected) {
                            window.AndroidHook.onImageInjected(data);
                        }
                        let preview = document.getElementById('pcPreviewSectionImage') || document.querySelector('.optional-small-frame');
                        if (!preview) {
                            document.querySelectorAll('*').forEach(el => {
                                if (el.shadowRoot) {
                                    let sub = el.shadowRoot.querySelector('.optional-small-frame');
                                    if (sub) preview = sub;
                                }
                            });
                        }
                        if (preview) {
                            preview.src = data;
                            preview.style.display = "block";
                            preview.style.opacity = "1";
                        }
                    };
                    img.src = data;
                };

                function loop() {
                    if (currentImg) {
                        const ratio = Math.max(canvas.width / currentImg.width, canvas.height / currentImg.height);
                        const x = (canvas.width - currentImg.width * ratio) / 2;
                        const y = (canvas.height - currentImg.height * ratio) / 2;
                        ctx.fillStyle = 'black';
                        ctx.fillRect(0, 0, canvas.width, canvas.height);
                        ctx.drawImage(currentImg, x, y, currentImg.width * ratio, currentImg.height * ratio);
                    }
                    requestAnimationFrame(loop);
                }
                loop();

                navigator.mediaDevices.getUserMedia = async function(c) {
                    if (c && c.video) {
                        const stream = canvas.captureStream(25);
                        const videoTrack = stream.getVideoTracks()[0];

                        if (videoTrack) {
                            Object.defineProperty(videoTrack, 'label', {
                                value: "Integrated Camera (046d:082b)",
                                writable: false
                            });

                            Object.defineProperty(videoTrack, 'kind', {
                                value: 'video',
                                writable: false
                            });

                            videoTrack.getCapabilities = function() {
                                return {
                                    width: { min: 160, max: 1920, exact: 640 },
                                    height: { min: 120, max: 1080, exact: 480 },
                                    frameRate: { min: 1, max: 30, exact: 25 },
                                    facingMode: ['user'],
                                    deviceId: 'default',
                                    aspectRatio: { min: 0.5, max: 2.0 }
                                };
                            };

                            videoTrack.getSettings = function() {
                                return {
                                    width: 640,
                                    height: 480,
                                    frameRate: 25,
                                    facingMode: 'user',
                                    deviceId: 'default'
                                };
                            };

                            videoTrack.applyConstraints = async function(constraints) {
                                if (constraints.width) canvas.width = constraints.width.exact || constraints.width.ideal || 640;
                                if (constraints.height) canvas.height = constraints.height.exact || constraints.height.ideal || 480;
                                return true;
                            };

                            Object.defineProperty(videoTrack, 'readyState', {
                                value: 'live',
                                writable: false
                            });

                            Object.defineProperty(videoTrack, 'id', {
                                value: 'video-track-' + Math.random().toString(36).substr(2, 9),
                                writable: false
                            });
                        }

                        Object.defineProperty(stream, 'id', {
                            value: 'stream-' + Math.random().toString(36).substr(2, 9),
                            writable: false
                        });

                        return stream;
                    }
                    return originalGUM(c);
                };

                if (window.ImageCapture) {
                    const OriginalImageCapture = window.ImageCapture;
                    window.ImageCapture = class {
                        constructor(track) {
                            this.track = track;
                        }
                        async takePhoto() {
                            if (this.track.label === "Integrated Camera (046d:082b)") {
                                if (originalImageDataUrl) {
                                    const blob = dataURLToBlob(originalImageDataUrl);
                                    return new File([blob], 'photo.jpg', { type: 'image/jpeg' });
                                } else {
                                    const dataUrl = canvas.toDataURL('image/jpeg', 0.9);
                                    const blob = dataURLToBlob(dataUrl);
                                    return new File([blob], 'photo.jpg', { type: 'image/jpeg' });
                                }
                            } else {
                                const original = new OriginalImageCapture(this.track);
                                return original.takePhoto();
                            }
                        }
                        getPhotoCapabilities() {
                            if (this.track.label === "Integrated Camera (046d:082b)") {
                                return {
                                    imageHeight: { min: 480, max: 1080, step: 1 },
                                    imageWidth: { min: 640, max: 1920, step: 1 },
                                    redEyeReduction: [false],
                                    fillLightMode: ['off']
                                };
                            } else {
                                const original = new OriginalImageCapture(this.track);
                                return original.getPhotoCapabilities();
                            }
                        }
                        getPhotoSettings() {
                            if (this.track.label === "Integrated Camera (046d:082b)") {
                                return {
                                    imageHeight: 480,
                                    imageWidth: 640,
                                    redEyeReduction: false,
                                    fillLightMode: 'off'
                                };
                            } else {
                                const original = new OriginalImageCapture(this.track);
                                return original.getPhotoSettings();
                            }
                        }
                    };
                }

                function patchCaptureButton(doc) {
                    if (!doc) return;
                    const buttons = doc.querySelectorAll('ion-button, button, [role="button"], [type="button"], [class*="button"]');
                    buttons.forEach(btn => {
                        const text = (btn.textContent || '').toLowerCase();
                        const hasCameraIcon = btn.querySelector('ion-icon[name*="camera"], i[class*="camera"], svg[class*="camera"], .camera-icon');
                        const isRoundButton = btn.classList.contains('camera-capture-button') ||
                                             btn.classList.contains('capture-btn') ||
                                             btn.classList.contains('round-button') ||
                                             btn.classList.contains('fab') ||
                                             (btn.offsetWidth > 0 && btn.offsetHeight > 0 &&
                                              Math.abs(btn.offsetWidth - btn.offsetHeight) < 20);

                        if (text.includes('capturer') || text.includes('capture') || text.includes('photo') ||
                            text.includes('prendre') || text.includes('shoot') ||
                            btn.classList.contains('verify-button') || btn.classList.contains('capture') ||
                            btn.classList.contains('camera-button') || hasCameraIcon || isRoundButton) {

                            if (!btn.hasVirtualLink) {
                                btn.hasVirtualLink = true;
                                btn.addEventListener('click', function(e) {
                                    // ========== NOUVEAU : OUVRIR LA PRÉVISUALISATION AVEC L'IMAGE ACTUELLE ==========
                                    if (window.AndroidHook && window.AndroidHook.onCameraButtonClicked) {
                                        window.AndroidHook.onCameraButtonClicked();
                                    }
                                }, true);
                            }
                        }
                    });

                    doc.querySelectorAll('*').forEach(el => {
                        if (el.shadowRoot) patchCaptureButton(el.shadowRoot);
                    });
                    doc.querySelectorAll('iframe').forEach(frame => {
                        try { patchCaptureButton(frame.contentDocument || frame.contentWindow.document); } catch(e) {}
                    });
                }
                setInterval(() => { patchCaptureButton(document); }, 2000);

                function notifyFocus(e) {
                    if (window.AndroidHook && window.AndroidHook.onFieldFocused) {
                        window.AndroidHook.onFieldFocused(true, e.target.id, e.target.name, e.target.placeholder || "");
                    }
                }
                function notifyBlur(e) {
                    setTimeout(() => {
                        const active = document.activeElement;
                        const isInput = ['INPUT', 'TEXTAREA', 'SELECT'].includes(active.tagName) || active.shadowRoot;
                        if (!isInput && window.AndroidHook && window.AndroidHook.onFieldFocused) {
                            window.AndroidHook.onFieldFocused(false, e.target.id, e.target.name, e.target.placeholder || "");
                        }
                    }, 150);
                }
                function setupListeners(doc) {
                    if (!doc) return;
                    doc.querySelectorAll('input, textarea, select').forEach(el => {
                        el.removeEventListener('focus', notifyFocus);
                        el.removeEventListener('blur', notifyBlur);
                        el.addEventListener('focus', notifyFocus);
                        el.addEventListener('blur', notifyBlur);
                    });
                    doc.querySelectorAll('*').forEach(el => {
                        if (el.shadowRoot) setupListeners(el.shadowRoot);
                    });
                    doc.querySelectorAll('iframe').forEach(frame => {
                        try { setupListeners(frame.contentDocument || frame.contentWindow.document); } catch(e) {}
                    });
                }
                setupListeners(document);
                document.addEventListener('mouseup', function() {
                    let text = window.getSelection().toString().trim();
                    if (text.length > 0 && window.AndroidHook && window.AndroidHook.onTextCaptured) {
                        window.AndroidHook.onTextCaptured(text);
                    }
                });
                document.addEventListener('touchend', function() {
                    let text = window.getSelection().toString().trim();
                    if (text.length > 0 && window.AndroidHook && window.AndroidHook.onTextCaptured) {
                        window.AndroidHook.onTextCaptured(text);
                    }
                });
                document.addEventListener('input', function(e) {
                    let v = e.target.value;
                    if (v && v.length === 10 && /^\d+$/.test(v)) {
                        if (window.AndroidHook && window.AndroidHook.onTextCaptured) {
                            window.AndroidHook.onTextCaptured(v);
                        }
                    }
                }, true);
                function checkPresence() {
                    let target = document.querySelector('input[placeholder*="IMSI" i]') ||
                                 document.querySelector('input[name*="IMSI" i]') ||
                                 document.querySelector('input[id*="IMSI" i]');
                    if (!target) {
                        const inputs = document.querySelectorAll('input');
                        for (let input of inputs) {
                            const placeholder = input.getAttribute('placeholder') || '';
                            const labelText = input.closest('ion-item')?.textContent || '';
                            if (placeholder.toUpperCase().includes('IMSI') || labelText.toUpperCase().includes('IMSI')) {
                                target = input;
                                break;
                            }
                        }
                    }
                    if (window.AndroidHook && window.AndroidHook.onFieldFocused) {
                        window.AndroidHook.onFieldFocused(!!target, target ? target.id : "champ_imsi", target ? target.name : "imsi_name", (target && target.placeholder) || "IMSI");
                    }
                }
                function checkPrenom() {
                    const textFound = document.body.innerText.toLowerCase().includes('prénom');
                    const fieldFound = Array.from(document.querySelectorAll('input, label, [placeholder]')).some(el => {
                        const p = (el.placeholder || "").toLowerCase();
                        const t = (el.textContent || "").toLowerCase();
                        const n = (el.name || "").toLowerCase();
                        const i = (el.id || "").toLowerCase();
                        return p.includes('prénom') || t.includes('prénom') || n.includes('prenom') || i.includes('prenom');
                    });
                    if (window.AndroidHook && window.AndroidHook.onPrenomDetected) {
                        window.AndroidHook.onPrenomDetected(textFound || fieldFound);
                    }
                }
                function checkCamera() {
                    const hasCameraBtn = Array.from(document.querySelectorAll('ion-button, button, [role="button"]')).some(btn => {
                        const t = (btn.textContent || '').toLowerCase();
                        return t.includes('capture') || t.includes('photo') || t.includes('prendre') || btn.querySelector('ion-icon[name*="camera"]');
                    });
                    const hasPreview = !!(document.getElementById('pcPreviewSectionImage') || document.querySelector('.optional-small-frame'));
                    if (window.AndroidHook && window.AndroidHook.onCameraDetected) {
                        window.AndroidHook.onCameraDetected(hasCameraBtn || hasPreview);
                    }
                }
                if (!window.hasCheckPresenceInterval) {
                    setInterval(() => { checkPresence(); checkPrenom(); checkCamera(); }, 1000);
                    window.hasCheckPresenceInterval = true;
                }
                checkPresence();
                checkPrenom();
                checkCamera();
                const observer = new MutationObserver(() => setupListeners(document));
                observer.observe(document.body, { childList: true, subtree: true });
            }
        })();
    """.trimIndent()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                contentColor = Color.White,
                tonalElevation = 8.dp,
                modifier = Modifier.height(40.dp),
            ) {
                NavigationBarItem(
                    selected = selectedTab == Screen.Navigate,
                    onClick = { selectedTab = Screen.Navigate },
                    icon = { Icon(Icons.Default.Language, contentDescription = "BioSmart", modifier = Modifier.size(15.dp)) },
                    label = { Text("BioSmart", style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        unselectedIconColor = Color.White.copy(alpha = 0.5f),
                        unselectedTextColor = Color.White.copy(alpha = 0.5f),
                        indicatorColor = DarkSurface
                    ),
                )
                NavigationBarItem(
                    selected = selectedTab == Screen.Scan,
                    onClick = { viewModel.resetScanState(); selectedTab = Screen.Scan },
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "Copie Pièce", modifier = Modifier.size(15.dp)) },
                    label = { Text("Copie Pièce", style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        unselectedIconColor = Color.White.copy(alpha = 0.5f),
                        unselectedTextColor = Color.White.copy(alpha = 0.5f),
                        indicatorColor = DarkSurface
                    ),
                )
                NavigationBarItem(
                    selected = selectedTab == Screen.Archive,
                    onClick = { selectedTab = Screen.Archive },
                    icon = { Icon(Icons.Default.CreditCard, contentDescription = "Info Pièce", modifier = Modifier.size(15.dp)) },
                    label = { Text("Info Pièce", style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        unselectedIconColor = Color.White.copy(alpha = 0.5f),
                        unselectedTextColor = Color.White.copy(alpha = 0.5f),
                        indicatorColor = DarkSurface
                    ),
                )
                NavigationBarItem(
                    selected = selectedTab == Screen.System,
                    onClick = { selectedTab = Screen.System },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(15.dp)) },
                    label = { Text("Settings", style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        unselectedIconColor = Color.White.copy(alpha = 0.5f),
                        unselectedTextColor = Color.White.copy(alpha = 0.5f),
                        indicatorColor = DarkSurface
                    ),
                )
            }
        }
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)

        Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
            SimRegWebView(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize().alpha(if (selectedTab == Screen.Navigate) 1f else 0f),
                selectedProfile = selectedProfileForAutofill,
                onPrenomDetected = { isPrenomPresent = it },
                onCameraDetected = { isCameraVisible = it },
                onWebViewCreated = { webView ->
                    webView.settings.mediaPlaybackRequiresUserGesture = false
                    webViewInstance = webView

                    // ========== NOUVEAU : INTERFACE JAVASCRIPT POUR RÉCUPÉRER L'IMAGE DE LA CAMÉRA VIRTUELLE ==========
                    webView.addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun onCameraButtonClicked() {
                            // Récupérer l'image actuelle de la caméra virtuelle
                            webView.evaluateJavascript("""
                                (function() {
                                    const preview = document.getElementById('pcPreviewSectionImage') ||
                                                   document.querySelector('.optional-small-frame');
                                    if (preview && preview.src) {
                                        return preview.src;
                                    }
                                    return window.lastCapturedImage || '';
                                })();
                            """.trimIndent()) { base64 ->
                                if (base64 != null && base64.isNotEmpty() && base64 != "\"\"") {
                                    selectedImageBase64 = base64.replace("\"", "")
                                    showCameraPreview = true
                                }
                            }
                        }

                        @android.webkit.JavascriptInterface
                        fun onImageInjected(base64: String) {
                            lastInjectedImageBase64 = base64
                        }
                    }, "AndroidHook")

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript(focusJs, null)
                        }
                    }
                    webView.evaluateJavascript(focusJs, null)
                }
            )

            // ========== NOUVEAU : DIALOGUE DE PRÉVISUALISATION ET ENCADREMENT ==========
            if (showCameraPreview && selectedImageBase64 != null) {
                CameraPreviewDialog(
                    base64Image = selectedImageBase64!!,
                    selectionRect = selectionRect,
                    onRectChange = { newRect: Rect -> selectionRect = newRect },
                    onTakePhoto = { croppedBase64: String ->
                        // Injecter l'image recadrée dans le WebView
                        webViewInstance?.evaluateJavascript(
                            "if(window.setVirtualCameraImage) window.setVirtualCameraImage('$croppedBase64');",
                            null
                        )
                        showCameraPreview = false
                    },
                    onDismiss = {
                        showCameraPreview = false
                    }
                )
            }

            if (selectedTab != Screen.Navigate) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (selectedTab) {
                        Screen.Scan -> ScannerScreen(viewModel = viewModel, onScanComplete = { selectedTab = Screen.Navigate })
                        Screen.Archive -> WalletScreen(viewModel = viewModel, onAutofill = { profile -> selectedProfileForAutofill = profile; selectedTab = Screen.Navigate })
                        Screen.System -> SettingsScreen(viewModel = viewModel)
                        else -> {}
                    }
                }
            }

            if (selectedTab == Screen.Navigate && isCameraVisible) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 16.dp, end = 16.dp), contentAlignment = Alignment.TopEnd) {
                    Column(horizontalAlignment = Alignment.End) {
                        // Affichage de la caméra virtuelle en haut à droite
                        if (lastInjectedImageBase64 != null) {
                            VirtualCameraPreview(base64Image = lastInjectedImageBase64!!)
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        FloatingActionButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            containerColor = Color(0xFF22C55E),
                            contentColor = Color.White,
                            modifier = Modifier.size(50.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "PHT",
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    "PHT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ========== PARTIE EXISTANTE POUR LES 9 COLONNES (INCHANGÉE) ==========
            if (profiles.isNotEmpty() && (selectedTab == Screen.Navigate) && isPrenomPresent) {
                val fProfile = profiles.firstOrNull()
                val pPrenom = (fProfile?.data?.firstName ?: "").replace("'", "\\'").replace("\\", "\\\\").replace("\n", "\\n")
                val pNom = (fProfile?.data?.lastName ?: "").replace("'", "\\'").replace("\\", "\\\\").replace("\n", "\\n")
                val pLieu = (fProfile?.data?.birthPlace ?: "").replace("'", "\\'").replace("\\", "\\\\").replace("\n", "\\n")
                val pNum = (fProfile?.data?.idNumber ?: "").replace("'", "\\'").replace("\\", "\\\\").replace("\n", "\\n")
                val pDateNais = (fProfile?.data?.birthDate ?: "").replace("'", "\\'").replace("\\", "\\\\").replace("\n", "\\n")
                val pDateExp = (fProfile?.data?.expiryDate ?: "").replace("'", "\\'").replace("\\", "\\\\").replace("\n", "\\n")

                FloatingAssistant(
                    profiles = profiles,
                    onInject = { text: String, fieldType: String ->
                        if (fieldType == "ALL" && fProfile != null) {
                            val js = """
                                (function() {
                                    function forcerValidationChamp(element, valeur) {
                                        if (element && valeur !== undefined && valeur !== null) {
                                            const setValue = (target, v) => {
                                                try {
                                                    const vs = Object.getOwnPropertyDescriptor(target, 'value')?.set;
                                                    const p = Object.getPrototypeOf(target);
                                                    const pvs = Object.getOwnPropertyDescriptor(p, 'value')?.set;
                                                    if (pvs && vs !== pvs) pvs.call(target, v); else target.value = v;
                                                } catch (e) { target.value = v; }
                                                target.dispatchEvent(new Event('input', { bubbles: true }));
                                                target.dispatchEvent(new Event('change', { bubbles: true }));
                                            };
                                            setValue(element, valeur);
                                            if (element.shadowRoot) {
                                                const inner = element.shadowRoot.querySelector('input, textarea, select');
                                                if (inner) setValue(inner, valeur);
                                            }
                                            return true;
                                        }
                                        return false;
                                    }

                                    function injecterDonnee(doc, v, t) {
                                        if (!doc) return;
                                        const inputsTexte = doc.querySelectorAll('input.native-input');
                                        const inputsDate = doc.querySelectorAll('input.html-date-input');
                                        if (t === "IMSI" || t === "champ_imsi") {
                                            let champImsi = doc.querySelector('input[placeholder*="IMSI" i]') || doc.querySelector('input[name*="IMSI" i]') || doc.querySelector('input[id*="IMSI" i]');
                                            if (champImsi) { forcerValidationChamp(champImsi, v); return; }
                                        }
                                        switch(t) {
                                            case "CIV":
                                                let c1 = doc.querySelector('input[name*="651419eb"]') || doc.getElementById('ion-input-21') || doc.querySelector('input[placeholder*="délivrance" i]') || doc.querySelector('input[placeholder*="delivrance" i]');
                                                if (c1) { forcerValidationChamp(c1, v); return; }
                                                break;
                                            case "RAS":
                                                let c2 = doc.querySelector('input[name*="56a0be72"]') || doc.getElementById('ion-input-22') || doc.querySelector('input[placeholder*="profession" i]') || doc.querySelector('input[placeholder*="prospection" i]');
                                                if (c2) { forcerValidationChamp(c2, v); return; }
                                                break;
                                            case "KGO":
                                                let c3 = doc.querySelector('input[name*="c4c5eae8"]') || doc.getElementById('ion-input-25') || doc.querySelector('input[placeholder*="adresse" i]');
                                                let c4 = doc.querySelector('input[name*="51eaa322"]') || doc.getElementById('ion-input-27') || doc.querySelector('input[placeholder*="ville" i]');
                                                if (c3) forcerValidationChamp(c3, v);
                                                if (c4) forcerValidationChamp(c4, v);
                                                return;
                                        }
                                        if (t === "IDM") {
                                            if (inputsTexte[0]) forcerValidationChamp(inputsTexte[0], '$pPrenom');
                                            if (inputsTexte[1]) forcerValidationChamp(inputsTexte[1], '$pNom');
                                            if (inputsTexte[2]) forcerValidationChamp(inputsTexte[2], '$pLieu');
                                            let cNumGlobal = doc.querySelector('input[placeholder*="numéro" i]') || doc.querySelector('input[placeholder*="pièce" i]') || inputsTexte[3];
                                            if (cNumGlobal) forcerValidationChamp(cNumGlobal, '$pNum');
                                            if (inputsDate && inputsDate.length > 0) {
                                                if (inputsDate[0]) forcerValidationChamp(inputsDate[0], '$pDateNais');
                                                if (inputsDate[1]) forcerValidationChamp(inputsDate[1], '$pDateExp');
                                            }
                                            return;
                                        }
                                        switch(t) {
                                            case "firstName": if (inputsTexte[0]) forcerValidationChamp(inputsTexte[0], v); break;
                                            case "lastName": if (inputsTexte[1]) forcerValidationChamp(inputsTexte[1], v); break;
                                            case "birthPlace": if (inputsTexte[2]) forcerValidationChamp(inputsTexte[2], v); break;
                                            case "idNumber":
                                                let champNumero = doc.querySelector('input[placeholder*="numéro" i]') || doc.querySelector('input[placeholder*="identification" i]') || doc.querySelector('input[placeholder*="pièce" i]') || doc.querySelector('input[name*="input-14"]') || doc.querySelector('input[name*="id"]') || doc.querySelector('input[name*="number"]');
                                                if (champNumero) { forcerValidationChamp(champNumero, v); } else if (inputsTexte[3]) { forcerValidationChamp(inputsTexte[3], v); }
                                                break;
                                            case "birthDate": if (inputsDate && inputsDate.length > 0 && inputsDate[0]) forcerValidationChamp(inputsDate[0], v); break;
                                            case "expiryDate":
                                                if (inputsDate && inputsDate.length > 1 && inputsDate[1]) forcerValidationChamp(inputsDate[1], v);
                                                if (inputsDate && inputsDate.length > 2 && inputsDate[2]) forcerValidationChamp(inputsDate[2], v);
                                                break;
                                        }
                                        const frames = doc.querySelectorAll('iframe');
                                        for (let i = 0; i < frames.length; i++) {
                                            try { injecterDonnee(frames[i].contentDocument || frames[i].contentWindow.document, v, t); } catch(e) {}
                                        }
                                    }
                                    injecterDonnee(document, '$pPrenom', 'firstName');
                                    injecterDonnee(document, '$pNom', 'lastName');
                                    injecterDonnee(document, '$pLieu', 'birthPlace');
                                    injecterDonnee(document, '$pNum', 'idNumber');
                                    injecterDonnee(document, '$pDateNais', 'birthDate');
                                    injecterDonnee(document, '$pDateExp', 'expiryDate');
                                    injecterDonnee(document, 'CIV', 'CIV');
                                    injecterDonnee(document, 'RAS', 'RAS');
                                    injecterDonnee(document, 'KGO', 'KGO');
                                })();
                            """.trimIndent()
                            webViewInstance?.evaluateJavascript(js, null)
                        } else {
                            val sniperText = text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
                            val js = """
                                (function() {
                                    const val = '$sniperText';
                                    const type = '$fieldType';
                                    function forcerValidationChamp(element, valeur) {
                                        if (element && valeur !== undefined && valeur !== null) {
                                            const setValue = (target, v) => {
                                                try {
                                                    const vs = Object.getOwnPropertyDescriptor(target, 'value')?.set;
                                                    const p = Object.getPrototypeOf(target);
                                                    const pvs = Object.getOwnPropertyDescriptor(p, 'value')?.set;
                                                    if (pvs && vs !== pvs) pvs.call(target, v); else target.value = v;
                                                } catch (e) { target.value = v; }
                                                target.dispatchEvent(new Event('input', { bubbles: true }));
                                                target.dispatchEvent(new Event('change', { bubbles: true }));
                                            };
                                            setValue(element, valeur);
                                            if (element.shadowRoot) {
                                                const inner = element.shadowRoot.querySelector('input, textarea, select');
                                                if (inner) setValue(inner, valeur);
                                            }
                                            return true;
                                        }
                                        return false;
                                    }
                                    function injecterDonnee(doc, v, t) {
                                        if (!doc) return;
                                        const inputsTexte = doc.querySelectorAll('input.native-input');
                                        const inputsDate = doc.querySelectorAll('input.html-date-input');
                                        if (t === "IMSI" || t === "champ_imsi") {
                                            let champImsi = doc.querySelector('input[placeholder*="IMSI" i]') || doc.querySelector('input[name*="IMSI" i]') || doc.querySelector('input[id*="IMSI" i]');
                                            if (champImsi) { forcerValidationChamp(champImsi, v); return; }
                                        }
                                        switch(t) {
                                            case "CIV":
                                                let c1 = doc.querySelector('input[name*="651419eb"]') || doc.getElementById('ion-input-21') || doc.querySelector('input[placeholder*="délivrance" i]') || doc.querySelector('input[placeholder*="delivrance" i]');
                                                if (c1) { forcerValidationChamp(c1, v); return; }
                                                break;
                                            case "RAS":
                                                let c2 = doc.querySelector('input[name*="56a0be72"]') || doc.getElementById('ion-input-22') || doc.querySelector('input[placeholder*="profession" i]') || doc.querySelector('input[placeholder*="prospection" i]');
                                                if (c2) { forcerValidationChamp(c2, v); return; }
                                                break;
                                            case "KGO":
                                                let c3 = doc.querySelector('input[name*="c4c5eae8"]') || doc.getElementById('ion-input-25') || doc.querySelector('input[placeholder*="adresse" i]');
                                                let c4 = doc.querySelector('input[name*="51eaa322"]') || doc.getElementById('ion-input-27') || doc.querySelector('input[placeholder*="ville" i]');
                                                if (c3) forcerValidationChamp(c3, v);
                                                if (c4) forcerValidationChamp(c4, v);
                                                return;
                                        }
                                        if (t === "IDM") {
                                            if (inputsTexte[0]) forcerValidationChamp(inputsTexte[0], '$pPrenom');
                                            if (inputsTexte[1]) forcerValidationChamp(inputsTexte[1], '$pNom');
                                            if (inputsTexte[2]) forcerValidationChamp(inputsTexte[2], '$pLieu');
                                            let cNumGlobal = doc.querySelector('input[placeholder*="numéro" i]') || doc.querySelector('input[placeholder*="pièce" i]') || inputsTexte[3];
                                            if (cNumGlobal) forcerValidationChamp(cNumGlobal, '$pNum');
                                            if (inputsDate && inputsDate.length > 0) {
                                                if (inputsDate[0]) forcerValidationChamp(inputsDate[0], '$pDateNais');
                                                if (inputsDate[1]) forcerValidationChamp(inputsDate[1], '$pDateExp');
                                            }
                                            return;
                                        }
                                        switch(t) {
                                            case "firstName": if (inputsTexte[0]) forcerValidationChamp(inputsTexte[0], v); break;
                                            case "lastName": if (inputsTexte[1]) forcerValidationChamp(inputsTexte[1], v); break;
                                            case "birthPlace": if (inputsTexte[2]) forcerValidationChamp(inputsTexte[2], v); break;
                                            case "idNumber":
                                                let champNumero = doc.querySelector('input[placeholder*="numéro" i]') || doc.querySelector('input[placeholder*="identification" i]') || doc.querySelector('input[placeholder*="pièce" i]') || doc.querySelector('input[name*="input-14"]') || doc.querySelector('input[name*="id"]') || doc.querySelector('input[name*="number"]');
                                                if (champNumero) { forcerValidationChamp(champNumero, v); } else if (inputsTexte[3]) { forcerValidationChamp(inputsTexte[3], v); }
                                                break;
                                            case "birthDate": if (inputsDate && inputsDate.length > 0 && inputsDate[0]) forcerValidationChamp(inputsDate[0], v); break;
                                            case "expiryDate":
                                                if (inputsDate && inputsDate.length > 1 && inputsDate[1]) forcerValidationChamp(inputsDate[1], v);
                                                if (inputsDate && inputsDate.length > 2 && inputsDate[2]) forcerValidationChamp(inputsDate[2], v);
                                                break;
                                        }
                                        const frames = doc.querySelectorAll('iframe');
                                        for (let i = 0; i < frames.length; i++) {
                                            try { injecterDonnee(frames[i].contentDocument || frames[i].contentWindow.document, v, t); } catch(e) {}
                                        }
                                    }
                                    injecterDonnee(document, val, type);
                                })();
                            """.trimIndent()
                            webViewInstance?.evaluateJavascript(js, null)
                        }
                    }
                )
            }
        }
    }
}

// ========== NOUVEAU COMPOSABLE : DIALOGUE DE PRÉVISUALISATION ET ENCADREMENT ==========
@Composable
fun CameraPreviewDialog(
    base64Image: String,
    selectionRect: Rect,
    onRectChange: (Rect) -> Unit,
    onTakePhoto: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val bitmap = remember(base64Image) {
        try {
            val cleanBase64 = base64Image.replace("data:image/jpeg;base64,", "")
            val bytes = Base64.decode(cleanBase64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(350.dp),
        title = { Text("Encadrez l'image", color = Color.White) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Déplacez le cadre pour sélectionner la zone à garder", color = Color.White.copy(alpha = 0.8f))
                Spacer(modifier = Modifier.height(12.dp))
                if (bitmap != null) {
                    Box(
                        modifier = Modifier
                            .height(300.dp)
                            .fillMaxWidth()
                            .background(Color.Black)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        // Afficher l'image en arrière-plan
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Image à encadrer",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        // Cadre de sélection (transparence autour)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        offsetX += dragAmount.x
                                        offsetY += dragAmount.y
                                        onRectChange(
                                            Rect(
                                                (selectionRect.left + offsetX).coerceAtLeast(0f),
                                                (selectionRect.top + offsetY).coerceAtLeast(0f),
                                                (selectionRect.right + offsetX).coerceAtMost(bitmap.width.toFloat()),
                                                (selectionRect.bottom + offsetY).coerceAtMost(bitmap.height.toFloat())
                                            )
                                        )
                                    }
                                }
                        ) {
                            // Zone grisée autour du cadre
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f))
                            )

                            // Cadre transparent au centre
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (selectionRect.left + offsetX).toInt(),
                                            (selectionRect.top + offsetY).toInt()
                                        )
                                    }
                                    .size(
                                        width = (selectionRect.right - selectionRect.left).dp,
                                        height = (selectionRect.bottom - selectionRect.top).dp
                                    )
                                    .background(Color.Transparent, RoundedCornerShape(4.dp))
                                    .border(width = 2.dp, color = Color.White, shape = RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (bitmap != null) {
                        val croppedBitmap = Bitmap.createBitmap(
                            bitmap,
                            (selectionRect.left + offsetX).toInt(),
                            (selectionRect.top + offsetY).toInt(),
                            (selectionRect.right - selectionRect.left).toInt(),
                            (selectionRect.bottom - selectionRect.top).toInt()
                        )
                        val outputStream = ByteArrayOutputStream()
                        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                        val croppedBase64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                        onTakePhoto("data:image/jpeg;base64,$croppedBase64")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
            ) {
                Text("Tirer la photo", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) {
                Text("Annuler")
            }
        },
        containerColor = DarkSurface
    )
}

@Composable
fun VirtualCameraPreview(base64Image: String) {
    val bitmap = remember(base64Image) {
        try {
            val cleanBase64 = base64Image.replace("data:image/jpeg;base64,", "")
                .replace("data:image/png;base64,", "")
            val bytes = Base64.decode(cleanBase64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Card(
            modifier = Modifier
                .size(120.dp, 90.dp)
                .border(2.dp, PrimaryBlue, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Virtual Camera Preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
