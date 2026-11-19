package com.example.mtnsimpro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mtnsimpro.ui.theme.*

@Composable
fun SecurityScreen(viewModel: SecurityViewModel) {
    var activationKey by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    var isAdminMode by remember { mutableStateOf(false) }
    var showAdminDialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val deviceId by viewModel.deviceId.collectAsState()
    val calculatedKey = viewModel.getCalculatedKey()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .bentoCard(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "ACTIVATION REQUISE",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Device ID Box
            Surface(
                color = Color.Black.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp).clickable {
                    copyToClipboard(context, deviceId)
                }) {
                    Text(
                        "ID APPAREIL (Cliquer pour copier)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        deviceId,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = activationKey,
                onValueChange = { activationKey = it.uppercase() },
                label = { Text("Clé d'activation") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.VpnKey, null) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedLabelColor = PrimaryBlue
                )
            )

            if (error != null) {
                Text(
                    error!!,
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (!viewModel.attemptActivation(activationKey)) {
                        error = "Clé invalide. Contactez l'administrateur."
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ACTIVER L'APPLICATION", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { showAdminDialog = true }
                        )
                    }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AdminPanelSettings, null, modifier = Modifier.size(16.dp), tint = TextSecondary.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ACCÈS MAÎTRE", color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp)
                }
            }
        }
    }

    if (showAdminDialog) {
        var passwordVisible by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                showAdminDialog = false
            },
            title = { Text("Authentification Maître") },
            text = {
                OutlinedTextField(
                    value = adminPassword,
                    onValueChange = { adminPassword = it },
                    label = { Text("Code de sécurité") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = null)
                        }
                    },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (viewModel.verifyAdminPassword(adminPassword)) {
                        isAdminMode = true
                        showAdminDialog = false
                        // Explicitly clear password for security
                        adminPassword = ""
                    } else {
                        error = "Mot de passe admin incorrect"
                    }
                }) {
                    Text("Valider")
                }
            }
        )
    }

    if (isAdminMode) {
        var remoteDeviceId by remember { mutableStateOf("") }
        var generatedRemoteKey by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { isAdminMode = false },
            title = { Text("CONSOLE ADMINISTRATEUR") },
            text = {
                Column {
                    Text("TÉLÉPHONE ACTUEL", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("ID: $deviceId", fontSize = 10.sp, modifier = Modifier.clickable { copyToClipboard(context, deviceId) })
                    Text("CLÉ: $calculatedKey", color = Color.Green, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { copyToClipboard(context, calculatedKey) })

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("GÉNÉRATION À DISTANCE", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                    Text("Entrez l'ID du téléphone distant :", fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))

                    OutlinedTextField(
                        value = remoteDeviceId,
                        onValueChange = {
                            remoteDeviceId = it
                            generatedRemoteKey = if (it.length > 5) viewModel.getCalculatedKey(it) else null
                        },
                        label = { Text("ID Appareil distant") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )

                    if (generatedRemoteKey != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = PrimaryBlue.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp).clickable { copyToClipboard(context, generatedRemoteKey!!) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("CLÉ GÉNÉRÉE (Cliquer pour copier)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    generatedRemoteKey!!,
                                    color = Color.Yellow,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.resetActivation() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("RÉINITIALISER L'ACTIVATION")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isAdminMode = false }) { Text("Fermer") }
            }
        )
    }
}

@Composable
fun RemoteBlockedScreen(deviceId: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF7F1D1D)), // Fond rouge sombre
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Yello tu n'es plus autorisé à utiliser l'application pour des raisons de sécurité",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "ID APPAREIL: $deviceId",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}