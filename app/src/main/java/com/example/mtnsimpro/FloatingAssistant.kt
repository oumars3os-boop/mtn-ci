package com.example.mtnsimpro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mtnsimpro.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun FloatingAssistant(
    profiles: List<IdentityProfile>,
    forceOpen: Boolean = false,
    onInject: (String, String) -> Unit
) {
    if (profiles.isEmpty()) return

    val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
    var isOpen by remember { mutableStateOf(value = false) }

    LaunchedEffect(isKeyboardVisible, forceOpen) {
        isOpen = isKeyboardVisible || forceOpen
    }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var activeProfileId by remember { mutableStateOf(profiles.firstOrNull()?.id) }
    val activeProfile = profiles.find { it.id == activeProfileId } ?: profiles.first()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .align(Alignment.TopEnd)
                .padding(top = 3.dp, end = 3.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
        ) {
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(
                    visible = isOpen,
                    enter = fadeIn(animationSpec = tween(durationMillis = 100)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 100))
                ) {
                    AssistantPanel(
                        activeProfile = activeProfile,
                        onClose = { isOpen = false },
                        onInject = onInject
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    modifier = Modifier
                        .size(25.dp)
                        .shadow(6.dp, CircleShape)
                        .clickable { isOpen = !isOpen },
                    color = if (isOpen) DarkSurface else PrimaryBlue,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isOpen) Icons.Default.Layers else Icons.Default.Psychology,
                            contentDescription = "Assistant",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AssistantPanel(
    activeProfile: IdentityProfile,
    onClose: () -> Unit,
    onInject: (String, String) -> Unit
) {
    Card(
        modifier = Modifier
            .width(126.dp)
            .height(410.dp)
            .shadow(10.dp, RoundedCornerShape(6.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Psychology, null, tint = PrimaryBlue, modifier = Modifier.size(12.dp))
                IconButton(onClick = onClose, modifier = Modifier.size(18.dp)) {
                    Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(10.dp))
                }
            }

            Column(
                modifier = Modifier
                    .padding(2.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CompactField(activeProfile.data.firstName ?: "", "firstName", onInject)
                CompactField(activeProfile.data.lastName ?: "", "lastName", onInject)
                CompactField(activeProfile.data.birthDate ?: "", "birthDate", onInject)
                CompactField(activeProfile.data.birthPlace ?: "", "birthPlace", onInject)
                CompactField(activeProfile.data.idNumber ?: "", "idNumber", onInject)
                CompactField(activeProfile.data.expiryDate ?: "", "expiryDate", onInject)
                CompactField("CIV", "CIV", onInject)
                CompactField("RAS", "RAS", onInject)
                CompactField("KGO", "KGO", onInject)

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = Color.White.copy(alpha = 0.1f)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    Button(
                        onClick = { onInject("", "ALL") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .width(50.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("IDM", fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun CompactField(value: String, fieldType: String, onInject: (String, String) -> Unit) {
    var copied by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clickable {
                if (value.isNotEmpty()) {
                    onInject(value, fieldType)
                    copied = true
                }
            },
        color = if (copied) Color(0xFF22C55E).copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.3f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(0.5.dp, if (copied) Color(0xFF22C55E).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                value.uppercase(),
                fontSize = 11.sp,
                color = if (copied) Color(0xFF4ADE80) else TextPrimary,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            if (copied) {
                Box(modifier = Modifier.fillMaxSize().padding(end = 4.dp), contentAlignment = Alignment.CenterEnd) {
                    Text("OK", fontSize = 7.sp, color = Color(0xFF4ADE80), fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1500)
            copied = false
        }
    }
}