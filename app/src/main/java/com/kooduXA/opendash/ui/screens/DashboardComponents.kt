package com.kooduXA.opendash.ui.screens

import androidx.compose.ui.graphics.Color

// Color Schemes
data class DashboardColors(
    val primary: Color,
    val darkSurface: Color,
    val cardSurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val backgroundColor: Color,
    val statusConnected: Color,
    val statusDisconnected: Color,
    val recordingIndicator: Color
)

val DayColors = DashboardColors(
    primary = Color(0xFF00E676),
    darkSurface = Color(0xFF1A1A1A),
    cardSurface = Color(0xFF2D2D2D),
    textPrimary = Color.White,
    textSecondary = Color(0xFFCCCCCC),
    backgroundColor = Color.Black,
    statusConnected = Color(0xFF00E676),
    statusDisconnected = Color.Red,
    recordingIndicator = Color.Red
)

val NightColors = DashboardColors(
    primary = Color(0xFF00C853),
    darkSurface = Color(0xFF0A0A0A),
    cardSurface = Color(0xFF1A1A1A),
    textPrimary = Color(0xFFE0E0E0),
    textSecondary = Color(0xFF888888),
    backgroundColor = Color(0xFF050505),
    statusConnected = Color(0xFF00C853),
    statusDisconnected = Color(0xFFFF6B6B),
    recordingIndicator = Color(0xFFFF5252)
)

