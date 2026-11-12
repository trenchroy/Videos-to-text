package com.speechtotext.app.ui.result

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speechtotext.app.domain.WordTiming
import com.speechtotext.app.ui.components.AppCard
import com.speechtotext.app.ui.theme.BackgroundDark
import com.speechtotext.app.ui.theme.CyanNeon
import com.speechtotext.app.ui.theme.GoldAccent
import com.speechtotext.app.ui.theme.OutlineDark
import com.speechtotext.app.ui.theme.SurfaceDark
import com.speechtotext.app.ui.theme.SurfaceElevatedDark
import com.speechtotext.app.ui.theme.TextPrimary
import com.speechtotext.app.ui.theme.TextSecondary
import com.speechtotext.app.util.ChineseDisplayMode
import com.speechtotext.app.util.ExportFormat
import com.speechtotext.app.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    transcriptId: Long,
    viewModel: ResultViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showExportSheet by remember { mutableStateOf(false) }
    var exportTargetFormat by remember { mutableStateOf<ExportFormat?>(null) }
    var seekTargetSeconds by remember { mutableStateOf<Float?>(null) }

    // Word editing dialog state
    var editingWordIndex by remember { mutableStateOf<Int?>(null) }
    var editWordText by remember { mutableStateOf("") }
    var editWordStart by remember { mutableStateOf("") }
    var editWordEnd by remember { mutableStateOf("") }

    // Document creation picker
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri != null && exportTargetFormat != null) {
            viewModel.exportToUri(uri, exportTargetFormat!!)
        }
    }

    LaunchedEffect(transcriptId) {
        viewModel.loadTranscript(transcriptId)
    }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearInfo()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }

    val transcript = uiState.transcript
    val isHindi = transcript?.languageCode?.startsWith("hi", ignoreCase = true) == true
    val isChinese = transcript?.let { it.languageCode.startsWith("cn", ignoreCase = true) || it.languageCode.startsWith("zh", ignoreCase = true) } == true
    val isJapanese = transcript?.languageCode?.startsWith("ja", ignoreCase = true) == true
    val isCjk = isChinese || isJapanese

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = transcript?.title ?: "Transcript",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("result_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Peeche jao",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    // Copy
                    IconButton(
                        onClick = viewModel::copyTranscript,
                        modifier = Modifier.testTag("copy_transcript_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy text",
                            tint = CyanNeon
                        )
                    }
                    // Share
                    IconButton(
                        onClick = viewModel::shareTranscript,
                        modifier = Modifier.testTag("share_transcript_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = CyanNeon
                        )
                    }
                    // Export
                    IconButton(
                        onClick = { showExportSheet = true },
                        modifier = Modifier.testTag("open_export_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Export file",
                            tint = GoldAccent
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tabs: Plain Text, Captions (SRT), Live Preview
            val tabs = listOf("Plain Text", "Captions (SRT)", "Preview")
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = BackgroundDark,
                contentColor = CyanNeon,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = CyanNeon
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == index) CyanNeon else TextSecondary
                            )
                        },
                        modifier = Modifier.testTag("result_tab_$index")
                    )
                }
            }

            // Tab Content
            when (selectedTabIndex) {
                0 -> {
                    // Plain Text Tab
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Colorful Stats Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceDark)
                                .border(1.dp, OutlineDark, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = CyanNeon,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "100% Sahi Sync",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = CyanNeon,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Text(
                                text = "📝 ${transcript?.words?.size ?: 0} words",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary)
                            )
                            Text(
                                text = "⏱️ ${TimeFormat.formatSecondsToDisplay(transcript?.durationSeconds ?: 0.0)}",
                                style = MaterialTheme.typography.labelSmall.copy(color = GoldAccent)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Language-specific Output Toggles
                        if (isHindi) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Hindi Output:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                                Spacer(modifier = Modifier.width(10.dp))
                                FilterChip(
                                    selected = !uiState.isHinglishMode,
                                    onClick = { viewModel.setHinglishMode(false) },
                                    label = { Text("Hindi (Devanagari)", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanNeon,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                FilterChip(
                                    selected = uiState.isHinglishMode,
                                    onClick = { viewModel.setHinglishMode(true) },
                                    label = { Text("Hinglish (Roman)", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanNeon,
                                        selectedLabelColor = Color.Black
                                    ),
                                    modifier = Modifier.testTag("toggle_hinglish")
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        } else if (isChinese) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Chinese:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                                Spacer(modifier = Modifier.width(8.dp))
                                ChineseDisplayMode.values().forEach { mode ->
                                    val isSelected = uiState.chineseMode == mode
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setChineseMode(mode) },
                                        label = { Text(mode.label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyanNeon,
                                            selectedLabelColor = Color.Black
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Rule-based Cleanup & Undo Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = viewModel::applyRuleBasedCleanup,
                                colors = ButtonDefaults.textButtonColors(contentColor = GoldAccent),
                                modifier = Modifier.testTag("cleanup_button")
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Filler Words Hatao (Cleanup)")
                            }
                            if (uiState.canUndoCleanup) {
                                TextButton(
                                    onClick = viewModel::undoCleanup,
                                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                                ) {
                                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Undo")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Editable Full Text Field
                        OutlinedTextField(
                            value = viewModel.getDisplayText(),
                            onValueChange = { newText ->
                                if (!uiState.isHinglishMode && uiState.chineseMode == ChineseDisplayMode.HANZI) {
                                    viewModel.updateFullText(newText)
                                }
                            },
                            readOnly = uiState.isHinglishMode || uiState.chineseMode != ChineseDisplayMode.HANZI,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("transcript_text_input"),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = OutlineDark,
                                focusedContainerColor = SurfaceDark,
                                unfocusedContainerColor = SurfaceDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                lineHeight = 26.sp,
                                textAlign = if (transcript?.isRtl == true) TextAlign.End else TextAlign.Start
                            )
                        )
                    }
                }
                1 -> {
                    // Captions (SRT) Tab
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Max Words / Characters per Line Selector
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isCjk) "Chars per line:" else "Words per line:",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val options = if (isCjk) listOf(0 to "Auto", 4 to "4", 8 to "8", 12 to "12", 16 to "16")
                            else listOf(0 to "Auto", 1 to "1", 2 to "2", 3 to "3", 5 to "5")

                            options.forEach { (count, label) ->
                                val isSelected = if (isCjk) uiState.cjkCharsPerLine == count else uiState.maxWordsPerLine == count
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (isCjk) viewModel.setCjkCharsPerLine(count)
                                        else viewModel.setMaxWordsPerLine(count)
                                    },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanNeon,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // List of Caption Lines
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(uiState.captionLines) { lineIdx, line ->
                                AppCard(
                                    borderColor = OutlineDark,
                                    containerColor = SurfaceDark
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "#${line.index}  ${TimeFormat.formatToSrtTime(line.start)}  -->  ${TimeFormat.formatToSrtTime(line.end)}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = CyanNeon
                                            )
                                        )
                                        // Play from this timestamp button
                                        IconButton(
                                            onClick = {
                                                seekTargetSeconds = line.start.toFloat()
                                                selectedTabIndex = 2
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play from here",
                                                tint = GoldAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Words clickable to edit
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        line.words.forEachIndexed { wordIdx, wordTiming ->
                                            val globalWordIndex = transcript?.words?.indexOf(wordTiming) ?: -1
                                            Text(
                                                text = "${wordTiming.word} ",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = TextPrimary,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .clickable {
                                                        if (globalWordIndex >= 0) {
                                                            editingWordIndex = globalWordIndex
                                                            editWordText = wordTiming.word
                                                            editWordStart = wordTiming.start.toString()
                                                            editWordEnd = wordTiming.end.toString()
                                                        }
                                                    }
                                                    .padding(2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Preview Tab (Video overlay with sync & styles)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            CaptionPreview(
                                mediaUriString = transcript?.sourceUri,
                                totalDurationSeconds = transcript?.durationSeconds ?: 0.0,
                                captionLines = uiState.captionLines,
                                style = uiState.captionStyle,
                                initialSeekSeconds = seekTargetSeconds
                            )
                        }
                        item {
                            StylePicker(
                                style = uiState.captionStyle,
                                onStyleChange = viewModel::updateCaptionStyle
                            )
                        }
                    }
                }
            }
        }
    }

    // Export Bottom Sheet
    if (showExportSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
            sheetState = sheetState,
            containerColor = SurfaceElevatedDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Export Format Chunein",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(14.dp))

                ExportFormat.values().forEach { format ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showExportSheet = false
                                exportTargetFormat = format
                                val defaultName = "${transcript?.title ?: "transcript"}.${format.extension}"
                                createDocLauncher.launch(defaultName)
                            }
                            .padding(vertical = 12.dp, horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = if (format == ExportFormat.DOCX || format == ExportFormat.XLSX || format == ExportFormat.PDF) GoldAccent else CyanNeon,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = format.label,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Edit Individual Word Dialog
    if (editingWordIndex != null) {
        AlertDialog(
            onDismissRequest = { editingWordIndex = null },
            title = { Text("Word Edit Karein") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editWordText,
                        onValueChange = { editWordText = it },
                        label = { Text("Word") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editWordStart,
                        onValueChange = { editWordStart = it },
                        label = { Text("Start Time (seconds)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editWordEnd,
                        onValueChange = { editWordEnd = it },
                        label = { Text("End Time (seconds)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val s = editWordStart.toDoubleOrNull() ?: 0.0
                        val e = editWordEnd.toDoubleOrNull() ?: 0.0
                        viewModel.updateWord(editingWordIndex!!, editWordText, s, e)
                        editingWordIndex = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = BackgroundDark)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingWordIndex = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceElevatedDark
        )
    }
}
