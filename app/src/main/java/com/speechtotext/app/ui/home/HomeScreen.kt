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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.speechtotext.app.domain.Language
import com.speechtotext.app.engine.TargetLanguage
import com.speechtotext.app.ui.components.AppCard
import com.speechtotext.app.ui.components.PrimaryAppButton
import com.speechtotext.app.ui.components.SecondaryAppButton
import com.speechtotext.app.ui.theme.BackgroundDark
import com.speechtotext.app.ui.theme.CyanNeon
import com.speechtotext.app.ui.theme.ErrorRed
import com.speechtotext.app.ui.theme.GoldAccent
import com.speechtotext.app.ui.theme.OutlineDark
import com.speechtotext.app.ui.theme.SuccessGreen
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
    var showAllTargetsSheet by remember { mutableStateOf(false) }

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
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = BackgroundDark,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Video Translator",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Offline Speech to Text & Captions",
                                style = MaterialTheme.typography.labelSmall.copy(color = CyanNeon)
                            )
                        }
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
                    Text("⚡ Auto Detect VAD", color = CyanNeon, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF8A2BE2).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF8A2BE2).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("🎬 Long Media Support", color = Color(0xFFD8B4FE), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SuccessGreen.copy(alpha = 0.15f))
                        .border(1.dp, SuccessGreen.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("🔒 100% Offline ML Kit", color = SuccessGreen, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
            }

            // Upload Banner Box with Vibrant Multi-Color Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
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
                        shape = RoundedCornerShape(22.dp)
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
                    .padding(vertical = 22.dp, horizontal = 18.dp)
                    .testTag("upload_media_box"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
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
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Video / Audio upload karo",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            fontSize = 18.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "MP4, MOV, WEBM, MKV, MP3, WAV, FLAC (Offline)",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, textAlign = TextAlign.Center)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Selected Media Card & Audio Preview Player
            uiState.selectedMedia?.let { media ->
                MediaPreviewCard(media = media)
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ==========================================
            // TWO SELECTORS SHOWN ONE ABOVE THE OTHER
            // ==========================================

            // 1. ORIGINAL LANGUAGE SELECTOR
            AppCard(
                borderColor = OutlineDark,
                containerColor = SurfaceDark
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Original language (video ki bhasha)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }

                    if (uiState.isAutoDetect) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyanNeon.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Auto",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = CyanNeon,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Options: "Auto detect" plus every downloaded Vosk language
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // "Auto detect" Chip
                    item {
                        FilterChip(
                            selected = uiState.isAutoDetect,
                            onClick = viewModel::selectOriginalAuto,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            label = { Text("Auto detect", fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanNeon,
                                selectedLabelColor = BackgroundDark,
                                containerColor = SurfaceElevatedDark,
                                labelColor = TextPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = uiState.isAutoDetect,
                                borderColor = if (uiState.isAutoDetect) CyanNeon else OutlineDark
                            ),
                            modifier = Modifier.testTag("auto_detect_chip")
                        )
                    }

                    // Downloaded Vosk Languages
                    items(uiState.downloadedLanguages) { lang ->
                        val isSelected = !uiState.isAutoDetect && uiState.selectedOriginalLanguage?.code == lang.code
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectOriginalLanguage(lang) },
                            label = { Text(lang.nativeName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanNeon,
                                selectedLabelColor = BackgroundDark,
                                containerColor = SurfaceElevatedDark,
                                labelColor = TextPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) CyanNeon else OutlineDark
                            ),
                            modifier = Modifier.testTag("orig_lang_chip_${lang.code}")
                        )
                    }

                    // "+ More Languages..."
                    item {
                        FilterChip(
                            selected = false,
                            onClick = onNavigateToLanguages,
                            label = { Text("+ More...", color = CyanNeon) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = SurfaceElevatedDark),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = CyanNeon.copy(alpha = 0.4f)
                            )
                        )
                    }
                }

                // Auto Detect Status Banners
                if (uiState.isAutoDetect) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val det = uiState.detection

                    if (det.isDetecting) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CyanNeon.copy(alpha = 0.1f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = CyanNeon,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = det.statusText.ifEmpty { "15-20s sample me offline models scan ho rahe hain..." },
                                style = MaterialTheme.typography.bodySmall.copy(color = CyanNeon)
                            )
                        }
                    } else if (det.detectedLanguage != null) {
                        // High confidence or confirmed result
                        if (!det.isLowConfidence || det.isConfirmed) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SuccessGreen.copy(alpha = 0.15f))
                                    .border(1.dp, SuccessGreen.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Detected: ${det.detectedLanguage.nativeName} (${det.confidencePercent}% sure)",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                }
                                TextButton(
                                    onClick = viewModel::changeDetectedLanguageManually,
                                    modifier = Modifier.testTag("change_detected_button")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = GoldAccent)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Galat hai? Badlo", color = GoldAccent, fontSize = 12.sp)
                                }
                            }
                        } else {
                            // Low confidence warning: show top 2 choices and ask to confirm
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(GoldAccent.copy(alpha = 0.12f))
                                    .border(1.dp, GoldAccent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "⚠️ Low confidence detection! Inme se sahi bhasha chunein:",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = GoldAccent)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    det.detectedLanguage?.let { firstLang ->
                                        OutlinedButton(
                                            onClick = { viewModel.confirmDetectedLanguage(firstLang) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("${firstLang.nativeName} (${det.confidencePercent}%)", fontSize = 12.sp)
                                        }
                                    }
                                    det.secondChoice?.let { secondLang ->
                                        OutlinedButton(
                                            onClick = { viewModel.confirmDetectedLanguage(secondLang) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("${secondLang.nativeName} (${det.secondChoicePercent}%)", fontSize = 12.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(
                                    onClick = viewModel::changeDetectedLanguageManually,
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Manual select karein", color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    } else if (det.notEnoughLanguages) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ErrorRed.copy(alpha = 0.12f))
                                .border(1.dp, ErrorRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Auto detect ke liye kam se kam 2 bhasha download karo",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = ErrorRed)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SecondaryAppButton(
                                text = "Language Manager me Download Karein",
                                icon = Icons.Default.CloudDownload,
                                onClick = onNavigateToLanguages,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else if (det.errorMessage != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ErrorRed.copy(alpha = 0.15f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = det.errorMessage,
                                style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // SWAP BUTTON ROW (Between the two selectors, disabled while Original is on Auto)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = OutlineDark.copy(alpha = 0.4f)
                )
                IconButton(
                    onClick = viewModel::swapLanguages,
                    enabled = !uiState.isAutoDetect,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (!uiState.isAutoDetect) SurfaceElevatedDark else SurfaceDark)
                        .border(
                            1.dp,
                            if (!uiState.isAutoDetect) CyanNeon.copy(alpha = 0.6f) else OutlineDark,
                            CircleShape
                        )
                        .testTag("swap_languages_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Swap Languages",
                        tint = if (!uiState.isAutoDetect) CyanNeon else TextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 2. TARGET LANGUAGE SELECTOR (Jis mein translate karna hai)
            AppCard(
                borderColor = OutlineDark,
                containerColor = SurfaceDark
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Target language (jis mein translate karna hai)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }

                    TextButton(onClick = { showAllTargetsSheet = true }) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp), tint = CyanNeon)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("All (50+)", color = CyanNeon, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Popular target chips row
                val quickTargets = uiState.availableTargetLanguages.take(8)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(quickTargets) { target ->
                        val isSelected = uiState.selectedTargetLanguage.code.equals(target.code, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectTargetLanguage(target) },
                            label = { Text(target.displayName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldAccent,
                                selectedLabelColor = BackgroundDark,
                                containerColor = SurfaceElevatedDark,
                                labelColor = TextPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) GoldAccent else OutlineDark
                            ),
                            modifier = Modifier.testTag("target_chip_${target.code}")
                        )
                    }

                    item {
                        FilterChip(
                            selected = false,
                            onClick = { showAllTargetsSheet = true },
                            label = { Text("+ More Target...", color = GoldAccent) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = SurfaceElevatedDark),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = GoldAccent.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Same Language Warning Banner (Blocks translation)
            if (uiState.isSameLanguageWarning) {
                AppCard(
                    borderColor = ErrorRed,
                    containerColor = ErrorRed.copy(alpha = 0.12f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Original aur Target bhasha same hai!",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = ErrorRed)
                            )
                            Text(
                                text = "Translation ke liye kripya alag target language chunein.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Pivot Through English Notice
            if (uiState.pivotNotice != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceElevatedDark)
                        .border(1.dp, CyanNeon.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.pivotNotice!!,
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Progress or Action Button
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
                val buttonText = if (uiState.isAutoDetect && uiState.selectedOriginalLanguage == null) {
                    "Auto Detect & Translate Nikalo"
                } else {
                    "Text Nikalo & Translate Karein"
                }

                PrimaryAppButton(
                    text = buttonText,
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    onClick = viewModel::startTranscription,
                    enabled = uiState.selectedMedia != null && !uiState.isSameLanguageWarning,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "extract_text_button"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Modal Bottom Sheet to Pick From All 50+ Target Languages
    if (showAllTargetsSheet) {
        val sheetState = rememberModalBottomSheetState()
        var searchQuery by remember { mutableStateOf("") }
        val filteredTargets = remember(searchQuery) {
            if (searchQuery.isBlank()) uiState.availableTargetLanguages
            else uiState.availableTargetLanguages.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.code.contains(searchQuery, ignoreCase = true)
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showAllTargetsSheet = false },
            sheetState = sheetState,
            containerColor = SurfaceElevatedDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Target Language Chunein (Translate)",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Bhasha search karein...", color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyanNeon) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = OutlineDark,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    filteredTargets.forEach { target ->
                        val isSelected = uiState.selectedTargetLanguage.code.equals(target.code, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) GoldAccent.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    viewModel.selectTargetLanguage(target)
                                    showAllTargetsSheet = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = target.displayName,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) GoldAccent else TextPrimary
                                )
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${TimeFormat.formatFileSize(media.sizeBytes)} • ${TimeFormat.formatSecondsToDisplay(media.durationSeconds)}",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
            IconButton(
                onClick = {
                    if (isPlaying) {
                        exoPlayer.pause()
                    } else {
                        exoPlayer.play()
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CyanNeon.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = CyanNeon,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Audio seek progress slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = TimeFormat.formatSecondsToDisplay(currentPositionMs / 1000.0),
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
            )
            Slider(
                value = currentPositionMs.coerceIn(0f, durationMs.coerceAtLeast(1f)),
                onValueChange = { newPos ->
                    currentPositionMs = newPos
                    exoPlayer.seekTo(newPos.toLong())
                },
                valueRange = 0f..durationMs.coerceAtLeast(1f),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = CyanNeon,
                    activeTrackColor = CyanNeon,
                    inactiveTrackColor = OutlineDark
                )
            )
            Text(
                text = TimeFormat.formatSecondsToDisplay(durationMs / 1000.0),
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
            )
        }
    }
}
