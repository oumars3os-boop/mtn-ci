package com.example.mtnsimpro.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

fun Modifier.bentoCard() = this
    .clip(RoundedCornerShape(24.dp))
    .background(DarkSurface.copy(alpha = 0.5f))
    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
    .padding(16.dp)
