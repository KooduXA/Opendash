package com.kooduXA.opendash.ui.screens

import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.kooduXA.opendash.VideoPlayer
import androidx.compose.material3.ripple
import androidx.compose.ui.res.stringResource // Added import
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kooduXA.opendash.R // Added import


@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToGallery: () -> Unit,
    onNavigateToFiles: () -> Unit
) {

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val uiState by viewModel.uiState.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()

    // Toggle controls visibility on tap (Optional)
    var areControlsVisible by remember { mutableStateOf(true) }


    // Auto-hide only applies to Landscape now
    var showControls by remember { mutableStateOf(true) }

    LaunchedEffect(showControls, isLandscape) {
        if (isLandscape && showControls) {
            kotlinx.coroutines.delay(5000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Force black background to avoid "white" flashes
    ) { // <--- Add ) { here
        if (isLandscape) {
            // LANDSCAPE LAYOUT (Z-Stack: Video behind, Controls in front)
            Box(modifier = Modifier.fillMaxSize()) {
                // LAYER 1: VIDEO (Background)
                VideoContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center),
                    onTap = {
                        // Toggle controls visibility when video is tapped
                        areControlsVisible = !areControlsVisible
                        if (areControlsVisible) showControls = true
                    }
                )

                // LAYER 2: CONTROLS (Overlay with Fade Animation)
                AnimatedVisibility(
                    visible = areControlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(Modifier.fillMaxSize()) {
                        // --- TOP LEFT: Status Pill ---
                        StatusPill(
                            uiState = uiState,
                            viewModel = viewModel,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .statusBarsPadding()
                        )

                        // --- TOP RIGHT: Rec Badge ---
                        if (isRecording) {
                            RecordingBadge(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .statusBarsPadding()
                            )
                        }

                        // --- RIGHT SIDE: CAMERA ACTIONS (Audio, Shutter, Photo) ---
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 1. Audio Toggle
                            val isAudio by viewModel.isAudioEnabled.collectAsState()
                            GlassIconButton(
                                icon = if (isAudio) Icons.Default.Mic else Icons.Default.MicOff,
                                onClick = { viewModel.toggleAudio() },
                                tint = if (isAudio) Color.White else Color.Yellow
                            )

                            // 2. Big Shutter Button
                            BigShutterButton(viewModel)

                            // 3. Photo Button
                            GlassIconButton(
                                icon = Icons.Rounded.PhotoCamera,
                                onClick = { viewModel.takePhoto() }
                            )
                        }

                        // --- LEFT SIDE: MENU & NAVIGATION (Files, Gallery, Settings, Reconnect) ---
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            // 1. Files Button
                            SmallCircleButton(
                                icon = Icons.Rounded.Folder,
                                onClick = onNavigateToFiles
                            )

                            // 2. Local Gallery Button
                            SmallCircleButton(
                                icon = Icons.Default.PhoneAndroid,
                                onClick = onNavigateToGallery
                            )

                            // 3. Settings Button
                            SmallCircleButton(
                                icon = Icons.Default.Settings,
                                onClick = onNavigateToSettings
                            )

                            // 4. Reconnect Button
                            SmallCircleButton(
                                icon = Icons.Default.Refresh,
                                onClick = { viewModel.connect() }
                            )
                        }
                    }
                }
            }
        } else {
            // PORTRAIT LAYOUT (Column: Video on top, Controls on bottom)
            Column(modifier = Modifier.fillMaxSize()) {
                // VIDEO SECTION
                Box(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxWidth()
                        .background(Color.Black)
                ) {
                    VideoContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                        onTap = null
                    )

                    // REC BADGE - Top Right of Video Area
                    if (isRecording) {
                        RecordingBadge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                        )
                    }

                    // Status Pill (Top Left)
                    StatusPill(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                    )
                }

                // BOTTOM SECTION: CONTROLS (Permanent)
                Box(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E)) // Solid dark gray background
                ) {
                    PortraitControls(
                        viewModel,
                        onNavigateToSettings,
                        onNavigateToGallery,
                        onNavigateToFiles
                    )
                }
            }

        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun VideoContent(
    uiState: DashboardUiState,
    viewModel: DashboardViewModel,
    modifier: Modifier,
    onTap: (() -> Unit)?
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (uiState is DashboardUiState.Streaming) {
            VideoPlayer(
                url = uiState.url,
                modifier = Modifier.fillMaxSize(),
                //onVideoTap = { onTap?.invoke() }
            )
        } else {
            // Placeholder
            if (uiState is DashboardUiState.Loading) {
                CircularProgressIndicator(color = Color(0xFF00E676))
            } else if (uiState is DashboardUiState.Error) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.WifiOff, null, tint = Color.Red)
                    Text((uiState as DashboardUiState.Error).message, color = Color.White)
                    Button(onClick = { viewModel.connect() }) { Text(stringResource(R.string.dashboard_retry_button)) }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VideocamOff, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.dashboard_not_connected), color = Color.Gray)
                    Button(
                        onClick = { viewModel.connect() },
                        modifier = Modifier.padding(top = 16.dp)
                    ) { Text(stringResource(R.string.dashboard_connect_to_camera_button)) }
                }
            }
        }
    }
}

// ============================================================================================
// PORTRAIT CONTROLS (Permanent Bottom Panel)
// ============================================================================================
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun PortraitControls(
    viewModel: DashboardViewModel,
    onSettings: () -> Unit,
    onGallery: () -> Unit,
    onFiles: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Row 1: Secondary Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ControlItem(Icons.Rounded.Folder, stringResource(R.string.dashboard_files_button), onFiles)
            ControlItem(Icons.Default.Settings, stringResource(R.string.dashboard_settings_button), onSettings)
        }

        // Row 2: Main Actions (Shutter)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo Button
            GlassIconButton(Icons.Rounded.PhotoCamera, onClick = { viewModel.takePhoto() })

            // Record Button (Big)
            BigShutterButton(viewModel)

            // Audio Toggle
            val isAudio by viewModel.isAudioEnabled.collectAsState()
            GlassIconButton(
                if (isAudio) Icons.Default.Mic else Icons.Default.MicOff,
                onClick = { viewModel.toggleAudio() },
                tint = if (isAudio) Color.White else Color.Yellow
            )
        }

        // Row 3: Bottom Toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ControlItem(Icons.Rounded.PhoneAndroid, stringResource(R.string.dashboard_local_gallery_button), onGallery)
            ControlItem(Icons.Default.Refresh, stringResource(R.string.dashboard_reconnect_button), { viewModel.connect() })
        }
    }
}

// ============================================================================================
// LANDSCAPE HUD (Overlay)
// ============================================================================================
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun LandscapeHud(
    viewModel: DashboardViewModel,
    uiState: DashboardUiState,
    onSettings: () -> Unit,
    onGallery: () -> Unit,
    onFiles: () -> Unit
) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            StatusPill(uiState, viewModel, Modifier.padding(16.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(100.dp)
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            GlassIconButton(Icons.Default.Settings, onClick = onSettings)
            GlassIconButton(Icons.Rounded.Folder, onClick = onFiles)
            BigShutterButton(viewModel)
            GlassIconButton(Icons.Rounded.PhotoCamera, onClick = { viewModel.takePhoto() })
            GlassIconButton(Icons.Rounded.PhoneAndroid, onClick = onGallery)
        }
    }
}

// ============================================================================================
// HELPER COMPONENTS
// ============================================================================================
@Composable
fun ControlItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Icon(icon, null, tint = Color.LightGray, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun BigShutterButton(viewModel: DashboardViewModel) {
    val isRecording by viewModel.isRecording.collectAsState()
    val haptic = LocalHapticFeedback.current

    // Animation Setup (Pulse Effect)
    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseAlpha"
    )

    Box(contentAlignment = Alignment.Center) {
        // --- LAYER 1: PULSING RING ---
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(pulseScale)
                    .background(Color.Red.copy(alpha = pulseAlpha), CircleShape)
            )
        }

        // --- LAYER 2: BUTTON ---
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .border(4.dp, if (isRecording) Color.Red else Color.White, CircleShape)
                .clip(CircleShape)
                .background(if (isRecording) Color.Transparent else Color.Black.copy(0.3f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    // FIX: Use 'ripple' instead of 'rememberRipple'
                    indication = ripple(bounded = true, color = Color.Red),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleRecording()
                    }
                )
        ) {
            if (isRecording) {
                // STOP ICON (Square)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.Red, RoundedCornerShape(8.dp))
                )
            } else {
                // RECORD ICON (Circle)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.Red, CircleShape)
                )
            }
        }
    }
}

@Composable
fun GlassIconButton(icon: ImageVector, onClick: () -> Unit, tint: Color = Color.White) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(Color.DarkGray.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun StatusPill(uiState: DashboardUiState, viewModel: DashboardViewModel, modifier: Modifier) {
    val hasSdCard by viewModel.hasSdCard.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val duration by viewModel.recordingDuration.collectAsState()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (color, text) = when (uiState) {
            is DashboardUiState.Streaming -> Color(0xFF00E676) to stringResource(R.string.dashboard_status_live)
            is DashboardUiState.Loading -> Color.Yellow to stringResource(R.string.dashboard_status_scan)
            is DashboardUiState.Error -> Color.Red to stringResource(R.string.dashboard_status_error)
            else -> Color.Gray to stringResource(R.string.dashboard_status_idle)
        }
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelSmall)

        if (!hasSdCard) {
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.SdCardAlert, null, tint = Color.Red, modifier = Modifier.size(16.dp))
        }

        if (isRecording) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(8.dp).background(Color.Red, CircleShape))
            Spacer(Modifier.width(4.dp))
            Text(duration, color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun RecordingBadge(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "RecBlink")

    // Blink Animation (Opacity 1.0 -> 0.0)
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RecAlpha"
    )

    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Blinking Red Dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(Color.Red.copy(alpha = alpha), CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.dashboard_recording_badge),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            letterSpacing = 2.sp
        )
    }
}

@Composable
fun SmallCircleButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xFF1E1E1E))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
    }
}