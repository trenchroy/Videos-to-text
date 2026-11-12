package com.speechtotext.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speechtotext.app.domain.Language
import com.speechtotext.app.domain.ModelDownloadStatus
import com.speechtotext.app.ui.theme.CyanNeon
import com.speechtotext.app.ui.theme.ErrorRed
import com.speechtotext.app.ui.theme.GoldAccent
import com.speechtotext.app.ui.theme.OutlineDark
import com.speechtotext.app.ui.theme.SuccessGreen
import com.speechtotext.app.ui.theme.SurfaceDark
import com.speechtotext.app.ui.theme.SurfaceElevatedDark
import com.speechtotext.app.ui.theme.TextPrimary
import com.speechtotext.app.ui.theme.TextSecondary

@Composable
fun ModelDownloadCard(
    language: Language,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier.testTag("model_card_${language.code}"),
        containerColor = if (language.isDownloaded) SurfaceElevatedDark else SurfaceDark,
        borderColor = if (language.isDownloaded) CyanNeon.copy(alpha = 0.5f) else OutlineDark
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = language.nativeName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    if (language.isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned Language",
                            tint = GoldAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = language.englishName,
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${language.sizeText}",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary.copy(alpha = 0.7f))
                    )
                }
            }

            // Status & Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (language.status) {
                    ModelDownloadStatus.READY -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SuccessGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Ready",
                                color = SuccessGreen,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("delete_${language.code}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Model Delete Karo",
                                tint = ErrorRed.copy(alpha = 0.8f)
                            )
                        }
                    }
                    ModelDownloadStatus.DOWNLOADING -> {
                        val progressInt = (language.downloadProgress * 100).toInt()
                        Text(
                            text = "$progressInt%",
                            color = CyanNeon,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("cancel_${language.code}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Download",
                                tint = ErrorRed
                            )
                        }
                    }
                    ModelDownloadStatus.FAILED -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ErrorRed.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Failed",
                                color = ErrorRed,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        IconButton(
                            onClick = onDownload,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("retry_${language.code}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry Download",
                                tint = CyanNeon
                            )
                        }
                    }
                    ModelDownloadStatus.NOT_DOWNLOADED -> {
                        IconButton(
                            onClick = onDownload,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CyanNeon.copy(alpha = 0.15f))
                                .testTag("download_${language.code}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download Model",
                                tint = CyanNeon,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Downloading progress bar
        AnimatedVisibility(visible = language.status == ModelDownloadStatus.DOWNLOADING) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                LinearProgressIndicator(
                    progress = { language.downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = CyanNeon,
                    trackColor = OutlineDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Model download ho raha hai, kripya intezar karein...",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp)
                )
            }
        }
    }
}
