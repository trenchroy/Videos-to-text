package com.speechtotext.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.speechtotext.app.domain.Language
import com.speechtotext.app.ui.components.AppCard
import com.speechtotext.app.ui.components.PrimaryAppButton
import com.speechtotext.app.ui.components.SecondaryAppButton
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToResult: (Long) -> Unit,
    onNavigateToLanguages: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Media Picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onMediaSelected(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshDownloadedLanguages()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(CyanNeon, GoldAccent))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = BackgroundDark,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Speech to Text",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToLanguages,
                        modifier = Modifier.testTag("open_language_manager")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Bhasha Manager",
                            tint = CyanNeon
                        )
                    }
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Feature Highlights Row (Colorful Pills)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(CyanNeon.copy(alpha = 0.15f))
                        .border(1.dp, CyanNeon.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("⚡ Ultra-Fast 16kHz", color = CyanNeon, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF8A2BE2).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF8A2BE2).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("🎬 Long Media", color = Color(0xFFD8B4FE), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(com.speechtotext.app.ui.theme.SuccessGreen.copy(alpha = 0.15f))
                        .border(1.dp, com.speechtotext.app.ui.theme.SuccessGreen.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("🔒 100% Offline", color = com.speechtotext.app.ui.theme.SuccessGreen, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
            }

            // Upload Banner Box with Vibrant Multi-Color Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceDark)
                    .border(
                        width = 2.dp,
                        brush = Brush.horizontalGradient(
                            listOf(
                                CyanNeon,
                                Color(0xFF8A2BE2),
                                GoldAccent,
                                Color(0xFFFF4081)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clickable {
                        filePickerLauncher.launch(
                            arrayOf(
                                "audio/*",
                                "video/*",
                                "video/mp4",
                                "video/quicktime",
                                "video/webm",
                                "video/x-matroska",
                                "audio/mpeg",
                                "audio/mp3",
                                "audio/wav",
                                "audio/x-wav",
                                "audio/mp4",
                                "audio/aac",
                                "audio/ogg",
                                "audio/flac"
                            )
                        )
                    }
                    .padding(vertical = 28.dp, horizontal = 20.dp)
                    .testTag("upload_media_box"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(CyanNeon.copy(alpha = 0.35f), Color(0xFF8A2BE2).copy(alpha = 0.1f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Upload",
                            tint = CyanNeon,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Video / Audio upload karo",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            fontSize = 19.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Long & Short Videos: MP4, MOV, WEBM, MKV, MP3, WAV, FLAC",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Selected Media Card & Audio Preview Player
            uiState.selectedMedia?.let { media ->
                MediaPreviewCard(media = media)
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Language Selector Section
            Text(
                text = "Bhasha Chunein (Language)",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
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
                        label = { Text(lang.nativeName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
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
                        ),
                        modifier = Modifier.testTag("lang_chip_${lang.code}")
                    )
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = onNavigateToLanguages,
                        label = { Text("+ More Languages...", color = CyanNeon) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = SurfaceElevatedDark),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = false,
                            borderColor = CyanNeon.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("more_languages_chip")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Warning if chosen language is not downloaded
            val isModelReady = uiState.selectedLanguage?.let { viewModel.modelManager.isModelReady(it.modelName) } ?: false
            if (uiState.selectedLanguage != null && !isModelReady) {
                AppCard(
                    borderColor = GoldAccent.copy(alpha = 0.6f),
                    containerColor = SurfaceDark
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${uiState.selectedLanguage?.nativeName} model download nahi hai",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Speech recognise karne ke liye pehle model download karein.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    SecondaryAppButton(
                        text = "Language Manager me Download Karein",
                        icon = Icons.Default.CloudDownload,
                        onClick = onNavigateToLanguages,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Progress or Transcription Button
            if (uiState.isTranscribing) {
                AppCard(
                    borderColor = CyanNeon,
                    containerColor = SurfaceElevatedDark
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.statusMessage,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = CyanNeon
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${uiState.progressPercent}%",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CyanNeon
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Stats Row: Speed & Processed Duration
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CyanNeon.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "⚡ Speed: ${String.format(java.util.Locale.US, "%.1fx", uiState.speedMultiplier)}",
                                        color = CyanNeon,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                            Text(
                                text = "${TimeFormat.formatSecondsToDisplay(uiState.processedSeconds)} / ${TimeFormat.formatSecondsToDisplay(uiState.selectedMedia?.durationSeconds ?: 0.0)}",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { uiState.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = CyanNeon,
                            trackColor = OutlineDark
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        SecondaryAppButton(
                            text = "Cancel Karo",
                            icon = Icons.Default.Stop,
                            onClick = viewModel::cancelTranscription,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "cancel_transcribe_button"
                        )
                    }
                }
            } else {
                PrimaryAppButton(
                    text = "Text nikalo",
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    onClick = viewModel::startTranscription,
                    enabled = uiState.selectedMedia != null && isModelReady,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "extract_text_button"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun MediaPreviewCard(media: SelectedMediaInfo) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableFloatStateOf((media.durationSeconds * 1000).toFloat()) }

    val exoPlayer = remember(media.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(media.uri))
            prepare()
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        if (duration > 0) durationMs = duration.toFloat()
                    } else if (state == Player.STATE_ENDED) {
                        isPlaying = false
                    }
                }
            })
        }
    }

    DisposableEffect(media.uri) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = exoPlayer.currentPosition.toFloat()
            delay(200)
        }
    }

    AppCard(
        borderColor = OutlineDark,
        containerColor = SurfaceDark
    ) {
        if (media.durationSeconds >= 300.0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF8A2BE2).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFF8A2BE2).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "🎬 Long Video/Audio Optimized: Background hardware decoder active",
                    color = Color(0xFFD8B4FE),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceElevatedDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (media.isVideo) Icons.Default.Videocam else Icons.Default.Audiotrack,
                    contentDescription = null,
                    tint = if (media.isVideo) GoldAccent else CyanNeon,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.fileName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row {
                    Text(
                        text = TimeFormat.formatSecondsToDisplay(media.durationSeconds),
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${TimeFormat.formatFileSize(media.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
            }
            // Play / Pause Button
            IconButton(
                onClick = {
                    if (isPlaying) {
                        exoPlayer.pause()
                    } else {
                        if (exoPlayer.playbackState == Player.STATE_ENDED) {
                            exoPlayer.seekTo(0)
                        }
                        exoPlayer.play()
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CyanNeon.copy(alpha = 0.2f))
                    .testTag("preview_play_button")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = CyanNeon
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Audio slider
        Slider(
            value = currentPositionMs.coerceIn(0f, durationMs.coerceAtLeast(1f)),
            onValueChange = { newPos ->
                currentPositionMs = newPos
                exoPlayer.seekTo(newPos.toLong())
            },
            valueRange = 0f..durationMs.coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = CyanNeon,
                activeTrackColor = CyanNeon,
                inactiveTrackColor = OutlineDark
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
