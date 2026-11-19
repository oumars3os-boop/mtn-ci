package com.example.mtnsimpro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mtnsimpro.ui.theme.DarkSurface
import com.example.mtnsimpro.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: IdentityViewModel,
    modifier: Modifier = Modifier,
    securityViewModel: SecurityViewModel = viewModel()
) {
    var isProtected by remember { mutableStateOf(value = true) }
    var showAdminDialog by remember { mutableStateOf(false) }
    var adminPassword by remember { mutableStateOf("") }
    var isAdminMode by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CONFIGURATION SYSTÈME",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 24.dp)
        )

        // Protection Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PROTECTION ANTI-RAFRAÎCHISSEMENT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Empêche la fermeture accidentelle",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = isProtected,
                    onCheckedChange = { isProtected = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF22C55E)
                    )
                )
            }
        }

        // Database Card
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = "PERMISSIONS DATABASE",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            Button(
                onClick = { 
                    viewModel.clearAllProfiles()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF87171).copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.2f))
            ) {
                Text(
                    "RÉINITIALISER LE STOCKAGE", 
                    color = Color(0xFFF87171),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }

        // Admin Button - Hidden behind long press
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { showAdminDialog = true }
                    )
                }
                .background(DarkSurface, RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AdminPanelSettings, null, modifier = Modifier.size(18.dp), tint = TextSecondary.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ACCÈS MAÎTRE", style = MaterialTheme.typography.labelSmall, color = TextSecondary.copy(alpha = 0.5f))
            }
        }

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "STATUS: ANDROID_HOOK_OK",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Text(
                    text = "L'application est configurée pour fonctionner par-dessus les autres applications.",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
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
                    if (securityViewModel.verifyAdminPassword(adminPassword)) {
                        isAdminMode = true
                        showAdminDialog = false
                    }
                }) {
                    Text("Valider")
                }
            }
        )
    }

    if (isAdminMode) {
        AlertDialog(
            onDismissRequest = { isAdminMode = false },
            title = { Text("CONSOLE ADMINISTRATEUR") },
            text = {
                Column {
                    val deviceId by securityViewModel.deviceId.collectAsState()
                    val key = securityViewModel.getCalculatedKey()
                    Text("ID APPAREIL: $deviceId", fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("CLÉ D'ACTIVATION:", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(key, color = Color.Green, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { securityViewModel.resetActivation() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
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
