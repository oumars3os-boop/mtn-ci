package com.example.mtnsimpro

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.example.mtnsimpro.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun ScannerScreen(
    viewModel: IdentityViewModel,
    modifier: Modifier = Modifier,
    onScanComplete: () -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(uiState) {
        if (uiState is IdentityUiState.Success) {
            onScanComplete()
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, it)
                android.graphics.ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            }
            viewModel.scanDocument(bitmap, "identity") // Default to identity for gallery
        }
    }
    
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(DarkBackground).padding(16.dp)) {
        if (hasCameraPermission) {
            HardwareScanner(
                loading = uiState is IdentityUiState.Loading,
                error = (uiState as? IdentityUiState.Error)?.errorMessage,
                onCapture = { bitmap, mode ->
                    viewModel.scanDocument(bitmap, mode)
                },
                onClearError = { /* VM could have a clear error method */ },
                onOpenGallery = { galleryLauncher.launch("image/*") }
            )
        } else {
            PermissionDeniedView(
                onGrantClick = { launcher.launch(Manifest.permission.CAMERA) }
            )
        }
    }
}

@Composable
fun HardwareScanner(
    loading: Boolean,
    error: String?,
    onCapture: (Bitmap, String) -> Unit,
    onClearError: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    var scanMode by remember { mutableStateOf("identity") }
    var captureTriggered by remember { mutableStateOf(value = false) }
    var scanProgress by remember { mutableFloatStateOf(0f) }
    
    // Simulate scan progress
    LaunchedEffect(loading) {
        if (loading) {
            scanProgress = 0f
            while (scanProgress < 1f) {
                delay(300)
                scanProgress += (0.05f + Random.nextFloat() * 0.1f)
            }
            scanProgress = 1f
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(40.dp))
            .background(Color(0xFF151619))
            .border(1.dp, Color(0xFF2D2E32), RoundedCornerShape(40.dp))
    ) {
        // Status Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C1D21))
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (loading) Color.Blue else PrimaryBlue, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "SCANNER_V3.0.4",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            Text(
                "ID: AIS_NODE_${Random.nextInt(1000, 9999)}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = Color(0xFF4B4C52),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }

        // Viewfinder Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(Color.Black)
        ) {
            CameraPreviewView(
                onCapture = { onCapture(it, scanMode) }, 
                triggerCapture = captureTriggered,
            ) {
                captureTriggered = false
            }

            // Decorative corners
            ViewfinderCorners()

            // Scan line animation
            if (loading) {
                ScanLineAnimation()
            }

            // Overlay Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(PrimaryBlue.copy(alpha = 0.05f), Color.Transparent),
                            radius = 500f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    LoadingView(scanProgress)
                } else {
                    IdleView(scanMode)
                }
            }
            
            // Flash Effect
            Column {
                AnimatedVisibility(
                    visible = captureTriggered,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.5f)))
                }
            }
        }

        // Mode Selector
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .fillMaxWidth()
                .background(Color(0xFF1C1D21), RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            ModeButton(
                modifier = Modifier.weight(1f),
                selected = scanMode == "identity",
                label = "IDENTITÉ",
                icon = Icons.Default.CreditCard,
                onClick = { scanMode = "identity" }
            )
            ModeButton(
                modifier = Modifier.weight(1f),
                selected = scanMode == "general",
                label = "DOCUMENT",
                icon = Icons.Default.Description,
                onClick = { scanMode = "general" }
            )
        }

        // Controls
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp)) {
            Button(
                onClick = { captureTriggered = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(16.dp),
                enabled = !loading
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("LANCER LE SCAN", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = onOpenGallery,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF2D2E32)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GALERIE", fontWeight = FontWeight.Black, fontSize = 10.sp)
                }
                Surface(
                    modifier = Modifier.size(56.dp),
                    color = Color(0xFF1C1D21),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF2D2E32))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF4B4C52))
                    }
                }
            }

            // Simulate Scan Button
            if ((!loading) && (error == null)) {
                TextButton(
                    onClick = { 
                        onCapture(createBitmap(1, 1, Bitmap.Config.ARGB_8888), scanMode)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("SIMULATE SCAN", color = Color(0xFF4B4C52), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
            }
        }

        // Footer
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Yellow.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("FLASCORE ENGINE V2", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = Color(0xFF4B4C52))
            }
            Text("PROC: FAST_PATH", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = Color(0xFF4B4C52))
        }
    }

    // Error Message
    if (error != null) {
        ErrorMessage(
            message = error,
            onRetry = { 
                captureTriggered = true 
                // Explicitly read to resolve "Assigned value is never read" lint warning
                android.util.Log.d("Scanner", "Retry triggered: $captureTriggered")
            },
            onCancel = onClearError
        )
    }
}

@Composable
fun IdleView(mode: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(80.dp),
            color = Color(0xFF1C1D21),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (mode == "identity") Icons.Default.CreditCard else Icons.Default.Description,
                    contentDescription = null,
                    tint = Color(0xFF4B4C52),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "PRÊT POUR LA CAPTURE",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            if (mode == "identity") "PLACEZ LA PIÈCE D'IDENTITÉ DANS LE CADRE" else "PLACEZ LE DOCUMENT À PLAT ET BIEN ÉCLAIRÉ",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun LoadingView(progress: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                color = PrimaryBlue,
                modifier = Modifier.size(64.dp),
                strokeWidth = 2.dp,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
            Icon(Icons.Default.Bolt, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "ANALYSE EN COURS",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlue,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.width(192.dp).height(4.dp).clip(CircleShape),
            color = PrimaryBlue,
            trackColor = Color(0xFF1C1D21)
        )
        Text(
            "MOTEUR GEMINI 3 NEURAL",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 8.dp),
            letterSpacing = 2.sp
        )
    }
}

@Composable
fun ErrorMessage(message: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    Card(
        modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF87171).copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.1f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(message.uppercase(), fontSize = 11.sp, color = Color(0xFFFCA5A5), lineHeight = 16.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF87171).copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("RÉESSAYER", color = Color(0xFFF87171), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
                Button(
                    onClick = onCancel,
                    modifier = Modifier.weight(0.4f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1D21)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("ANNULER", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun ModeButton(
    modifier: Modifier = Modifier,
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = if (selected) Color(0xFF2D2E32) else Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) PrimaryBlue else TextSecondary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = if (selected) PrimaryBlue else TextSecondary,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun ScanLineAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = ""
    )

    Box(modifier = Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .offset(y = (offsetY * 300).dp) // Simulated height
                .background(Brush.horizontalGradient(listOf(Color.Transparent, PrimaryBlue, Color.Transparent)))
                .shadow(elevation = 10.dp, ambientColor = PrimaryBlue, spotColor = PrimaryBlue)
        )
    }
}

@Composable
fun ViewfinderCorners() {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        CornerMarker(Modifier.align(Alignment.TopStart))
        CornerMarker(Modifier.align(Alignment.TopEnd), rotate = 90f)
        CornerMarker(Modifier.align(Alignment.BottomStart), rotate = -90f)
        CornerMarker(Modifier.align(Alignment.BottomEnd), rotate = 180f)
    }
}

@Composable
fun CornerMarker(modifier: Modifier = Modifier, rotate: Float = 0f) {
    Box(modifier = modifier.size(32.dp).rotate(rotate)) {
        Box(modifier = Modifier.size(2.dp, 12.dp).background(Color(0xFF4B4C52), RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.size(12.dp, 2.dp).background(Color(0xFF4B4C52), RoundedCornerShape(1.dp)))
    }
}

@Composable
fun CameraPreviewView(
    onCapture: (Bitmap) -> Unit,
    triggerCapture: Boolean,
    onCaptureDone: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    var previewView: PreviewView? by remember { mutableStateOf(null) }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).also {
                previewView = it
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    LaunchedEffect(previewView) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView?.surfaceProvider
        }
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    LaunchedEffect(triggerCapture) {
        if (triggerCapture) {
            previewView?.bitmap?.let { onCapture(it) }
            onCaptureDone()
        }
    }
}

@Composable
fun PermissionDeniedView(onGrantClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "AUTORISATION DE LA CAMÉRA",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "L'accès à la caméra est nécessaire pour scanner automatiquement les pièces d'identité et documents CNI / Passeport.",
            color = TextSecondary,
            textAlign = TextAlign.Center,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                onGrantClick()
                try {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("AUTORISER / PARAMÈTRES", fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
    }
}
