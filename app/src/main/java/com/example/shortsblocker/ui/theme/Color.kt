package com.example.shortsblocker.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val IndigoPrimary = Color(0xFF6366F1)
val VioletSecondary = Color(0xFF8B5CF6)
val EmeraldSuccess = Color(0xFF10B981)
val RoseError = Color(0xFFF43F5E)
val AmberWarning = Color(0xFFF59E0B)

val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate400 = Color(0xFF94A3B8)
val Slate100 = Color(0xFFF1F5F9)

val DarkColorScheme = darkColorScheme(
    primary = IndigoPrimary,
    secondary = VioletSecondary,
    tertiary = EmeraldSuccess,
    background = Slate900,
    surface = Slate800,
    error = RoseError,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Slate100,
    onSurface = Slate100
)

val LightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    secondary = VioletSecondary,
    tertiary = EmeraldSuccess,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    error = RoseError,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Slate900,
    onSurface = Slate900
)
