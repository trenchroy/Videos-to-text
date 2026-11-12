package com.speechtotext.app.ui.record

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speechtotext.app.engine.MicRecordState
import com.speechtotext.app.ui.components.AppCard
import com.speechtotext.app.ui.components.WaveformView
import com.speechtotext.app.ui.theme.BackgroundDark
import com.speechtotext.app.ui.theme.CyanGlow
import com.speechtotext.app.ui.theme.CyanNeon
import com.speechtotext.app.ui.theme.ErrorRed
import com.speechtotext.app.ui.theme.GoldAccent
import com.speechtotext.app.ui.theme.OutlineDark
import com.speechtotext.app.ui.theme.SurfaceDark
import com.speechtotext.app.ui.theme.SurfaceElevatedDark
import com.speechtotext.app.ui.theme.TextPrimary
import com.speechtotext.app.ui.theme.TextSecondary
import com.speechtotext.app.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    viewModel: RecordViewModel,
    onNavigateToResult: (Long) -> Unit,
    onNavigateToLanguages: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
        } else {
            // Permission denied Hinglish message
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadLanguages()
        viewModel.navigationEvent.collect { transcriptId ->
            onNavigateToResult(transcriptId)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    val isRecording = uiState.recordState == MicRecordState.RECORDING
    val isPaused = uiState.recordState == MicRecordState.PAUSED
    val isIdle = uiState.recordState == MicRecordState.IDLE

    // Pulse animation for recording indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Live Mic Recording",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = TextPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundDark
    ) { paddingValues ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Language Selection Chips
            if (isIdle) {
                Text(
                    text = "Recording Bhasha",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(uiState.downloadedLanguages) { lang ->
                        val isSelected = uiState.selectedLanguage?.code == lang.code
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectLanguage(lang) },
                            label = { Text(lang.nativeName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanNeon,
                                selectedLabelColor = BackgroundDark,
                                containerColor = SurfaceDark,
                                labelColor = TextPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) CyanNeon else OutlineDark
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = false,
                            onClick = onNavigateToLanguages,
                            label = { Text("+ More...", color = CyanNeon) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = SurfaceElevatedDark),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = CyanNeon.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDark)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .scale(if (isRecording) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(if (isRecording) ErrorRed else GoldAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRecording) "Recording chal rahi hai (${uiState.selectedLanguage?.nativeName})"
                        else "Recording paused hai",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isRecording) ErrorRed else GoldAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Timer Display
            val formattedTime = TimeFormat.formatSecondsToDisplay(uiState.durationSeconds)
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    color = if (isRecording) CyanNeon else TextPrimary
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Live Waveform Visualizer
            WaveformView(
                amplitude = uiState.amplitude,
                isRecording = isRecording,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .padding(12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Live Partial Text Card
            AppCard(
                borderColor = if (isRecording) CyanNeon.copy(alpha = 0.5f) else OutlineDark,
                containerColor = SurfaceDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Live Transcription:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = CyanNeon,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                val displayText = buildString {
                    if (uiState.accumulatedText.isNotBlank()) {
                        append(uiState.accumulatedText)
                        append(" ")
                    }
                    if (uiState.livePartialText.isNotBlank()) {
                        append(uiState.livePartialText)
                    }
                }

                if (displayText.isBlank()) {
                    Text(
                        text = if (isRecording) "Aap boliye, yahan live text dikhega..." else "Mic button daba kar bolna shuru karein.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary.copy(alpha = 0.6f)
                        )
                    )
                } else {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = TextPrimary,
                            lineHeight = 24.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Control Buttons
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!isIdle) {
                    // Pause / Resume Button
                    IconButton(
                        onClick = {
                            if (isRecording) viewModel.pauseRecording() else viewModel.resumeRecording()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevatedDark)
                            .border(1.dp, OutlineDark, CircleShape)
                            .testTag("record_pause_resume_button")
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isRecording) "Pause" else "Resume",
                            tint = GoldAccent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(32.dp))
                }

                // Main Big Record / Stop Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            if (isIdle) Brush.radialGradient(listOf(CyanNeon, CyanNeon.copy(alpha = 0.8f)))
                            else Brush.radialGradient(listOf(ErrorRed, ErrorRed.copy(alpha = 0.8f)))
                        )
                        .clickable {
                            if (isIdle) {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasPermission) {
                                    viewModel.startRecording()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                viewModel.stopRecording()
                            }
                        }
                        .testTag("main_record_stop_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isIdle) Icons.Default.Mic else Icons.Default.Stop,
                        contentDescription = if (isIdle) "Record Karo" else "Roko",
                        tint = BackgroundDark,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (isIdle) "Tap to Record" else "Tap to Stop & Save",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
