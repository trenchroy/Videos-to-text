package com.speechtotext.app.ui.result

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.speechtotext.app.domain.CaptionPosition
import com.speechtotext.app.domain.CaptionStyle
import com.speechtotext.app.ui.theme.BackgroundDark
import com.speechtotext.app.ui.theme.CyanNeon
import com.speechtotext.app.ui.theme.GoldAccent
import com.speechtotext.app.ui.theme.OutlineDark
import com.speechtotext.app.ui.theme.SurfaceDark
import com.speechtotext.app.ui.theme.SurfaceElevatedDark
import com.speechtotext.app.ui.theme.TextPrimary
import com.speechtotext.app.ui.theme.TextSecondary
import com.speechtotext.app.util.CaptionLine
import com.speechtotext.app.util.TimeFormat
import kotlinx.coroutines.delay

@Composable
fun CaptionPreview(
    mediaUriString: String?,
    totalDurationSeconds: Double,
    captionLines: List<CaptionLine>,
    style: CaptionStyle,
    modifier: Modifier = Modifier,
    initialSeekSeconds: Float? = null
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionSec by remember { mutableFloatStateOf(initialSeekSeconds ?: 0f) }
    val maxDurationSec = totalDurationSeconds.toFloat().coerceAtLeast(1f)

    val exoPlayer = remember(mediaUriString) {
        if (!mediaUriString.isNullOrBlank()) {
            try {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(mediaUriString)))
                    prepare()
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaying = playing
                        }
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) {
                                isPlaying = false
                            }
                        }
                    })
                }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    LaunchedEffect(initialSeekSeconds) {
        if (initialSeekSeconds != null) {
            currentPositionSec = initialSeekSeconds
            exoPlayer?.seekTo((initialSeekSeconds * 1000).toLong())
            exoPlayer?.play()
            isPlaying = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
        }
    }

    // Playback loop for timing updates
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (exoPlayer != null) {
                currentPositionSec = exoPlayer.currentPosition / 1000f
            } else {
                currentPositionSec += 0.05f
                if (currentPositionSec >= maxDurationSec) {
                    currentPositionSec = 0f
                    isPlaying = false
                }
            }
            delay(50)
        }
    }

    // Find active caption line
    val activeLine = captionLines.find { line ->
        currentPositionSec >= line.start && currentPositionSec <= line.end
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceDark)
            .border(1.dp, OutlineDark, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        // Video/Canvas Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black),
            contentAlignment = when (style.position) {
                CaptionPosition.TOP -> Alignment.TopCenter
                CaptionPosition.MIDDLE -> Alignment.Center
                CaptionPosition.BOTTOM -> Alignment.BottomCenter
            }
        ) {
            // Media3 PlayerView if video is available
            if (exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Audio visual background placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(SurfaceElevatedDark, Color.Black)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Audio Preview",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary.copy(alpha = 0.5f))
                    )
                }
            }

            // Caption Overlay
            if (activeLine != null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .then(
                            if (style.hasBackgroundBox) {
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(style.backgroundColor)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (activeLine.words.isNotEmpty()) {
                            activeLine.words.forEachIndexed { idx, wordTiming ->
                                val isWordActive = currentPositionSec >= wordTiming.start && currentPositionSec <= wordTiming.end
                                val wordText = if (style.isAllUppercase) wordTiming.word.uppercase() else wordTiming.word
                                Text(
                                    text = "$wordText ",
                                    color = if (isWordActive) style.activeHighlightColor else style.textColor,
                                    fontSize = style.fontSizeSp.sp,
                                    fontWeight = if (isWordActive) FontWeight.ExtraBold else FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            val lineText = if (style.isAllUppercase) activeLine.text.uppercase() else activeLine.text
                            Text(
                                text = lineText,
                                color = style.textColor,
                                fontSize = style.fontSizeSp.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Playback Timeline Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) {
                        exoPlayer?.pause()
                        isPlaying = false
                    } else {
                        if (currentPositionSec >= maxDurationSec) {
                            currentPositionSec = 0f
                            exoPlayer?.seekTo(0)
                        }
                        exoPlayer?.play()
                        isPlaying = true
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CyanNeon.copy(alpha = 0.2f))
                    .testTag("preview_toggle_play")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = CyanNeon,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Slider(
                value = currentPositionSec.coerceIn(0f, maxDurationSec),
                onValueChange = { newSec ->
                    currentPositionSec = newSec
                    exoPlayer?.seekTo((newSec * 1000).toLong())
                },
                valueRange = 0f..maxDurationSec,
                colors = SliderDefaults.colors(
                    thumbColor = CyanNeon,
                    activeTrackColor = CyanNeon,
                    inactiveTrackColor = OutlineDark
                ),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${TimeFormat.formatSecondsToDisplay(currentPositionSec.toDouble())} / ${TimeFormat.formatSecondsToDisplay(maxDurationSec.toDouble())}",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
            )
        }
    }
}
