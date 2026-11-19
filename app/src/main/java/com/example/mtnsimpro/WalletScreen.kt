package com.example.mtnsimpro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mtnsimpro.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun WalletScreen(
    viewModel: IdentityViewModel,
    modifier: Modifier = Modifier,
    onAutofill: (IdentityProfile) -> Unit,
) {
    val profiles by viewModel.profiles.collectAsState()

    Column(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PRESSE-PAPIERS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(16.dp),
                border = border(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Text(
                    text = "${profiles.size} DOCUMENTS",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (profiles.isEmpty()) {
            EmptyWallet()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(profiles) { profile ->
                    ProfileCard(
                        profile = profile, 
                        onDelete = { viewModel.deleteProfile(profile.id) },
                    ) {
                        onAutofill(profile)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyWallet() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier.bentoCard(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                color = DarkSurface,
                shape = RoundedCornerShape(20.dp),
                border = border(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Base de données vide", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "PRESSE-PAPIERS VIDE", 
                color = TextSecondary, 
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun ProfileCard(profile: IdentityProfile, onDelete: () -> Unit, onAutofill: () -> Unit) {
    val data = profile.data
    val context = LocalContext.current
    
    Box(
        modifier = Modifier.fillMaxWidth().bentoCard().padding(0.dp) // Reset padding inside bentoCard if needed
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.2f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        color = PrimaryBlue.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        border = border(1.dp, PrimaryBlue.copy(alpha = 0.2f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "${data.firstName ?: ""} ${data.lastName ?: ""}".uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            data.documentType?.uppercase() ?: "DOCUMENT",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            // Grid of fields (Bento Grid Style)
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    FieldItem(Modifier.weight(1f), "NOM", data.lastName ?: "N/A", onCopy = { copyToClipboard(context, it) })
                    Spacer(modifier = Modifier.width(8.dp))
                    FieldItem(Modifier.weight(1f), "PRÉNOM", data.firstName ?: "N/A", onCopy = { copyToClipboard(context, it) })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    FieldItem(Modifier.weight(1f), "DATE DE NAISSANCE", data.birthDate ?: "N/A", Icons.Default.Event, onCopy = { copyToClipboard(context, it) })
                    Spacer(modifier = Modifier.width(8.dp))
                    FieldItem(Modifier.weight(1f), "LIEU DE NAISSANCE", data.birthPlace ?: "N/A", Icons.Default.Place, onCopy = { copyToClipboard(context, it) })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    FieldItem(Modifier.weight(1f), "NUMÉRO D'IDENTITÉ", data.idNumber ?: "N/A", highlight = true, onCopy = { copyToClipboard(context, it) })
                    Spacer(modifier = Modifier.width(8.dp))
                    FieldItem(Modifier.weight(1f), "DATE D'EXPIRATION OU DÉLIVRANCE", data.expiryDate ?: "N/A", Icons.Default.Event, onCopy = { copyToClipboard(context, it) })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    FieldItem(Modifier.weight(1f), "SEXE", data.gender ?: "N/A", onCopy = { copyToClipboard(context, it) })
                    if (!data.address.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        FieldItem(Modifier.weight(1f), "ADRESSE", data.address, Icons.Default.Place, onCopy = { copyToClipboard(context, it) })
                    }
                }
            }

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.2f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "DT: ${profile.scannedAt.take(10)}", 
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = TextSecondary,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Row {
                    Button(
                        onClick = onAutofill,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("REMPLIR", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { 
                            val fullInfo = listOfNotNull(
                                data.lastName, data.firstName, data.birthDate, 
                                data.birthPlace, data.idNumber, data.expiryDate, 
                                data.gender, data.address
                            ).joinToString("\n")
                            copyToClipboard(context, fullInfo)
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = border(1.dp, PrimaryBlue.copy(alpha = 0.2f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(10.dp), tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("COPIER", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                }
            }
        }
    }
}

@Composable
fun FieldItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector? = null,
    highlight: Boolean = false,
    onCopy: (String) -> Unit
) {
    var copied by remember { mutableStateOf(value = false) }
    
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Surface(
        modifier = modifier.clickable { 
            onCopy(value)
            copied = true
        },
        color = if (copied) SuccessGreen.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp),
        border = border(1.dp, if (copied) SuccessGreen.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        label, 
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 7.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (copied) {
                    Text("COPIÉ !", fontSize = 6.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(8.dp), tint = Color.White.copy(alpha = 0.3f))
                }
            }
            Text(
                value.uppercase(),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = if (highlight) PrimaryBlue else if (copied) SuccessGreen else Color.White,
                fontWeight = if (highlight || copied) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

fun border(width: androidx.compose.ui.unit.Dp, color: Color) = androidx.compose.foundation.BorderStroke(width, color)
