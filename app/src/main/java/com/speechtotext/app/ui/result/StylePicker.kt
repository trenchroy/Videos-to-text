package com.speechtotext.app.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.speechtotext.app.domain.CaptionPosition
import com.speechtotext.app.domain.CaptionPreset
import com.speechtotext.app.domain.CaptionStyle
import com.speechtotext.app.ui.components.AppCard
import com.speechtotext.app.ui.theme.BackgroundDark
import com.speechtotext.app.ui.theme.CyanNeon
import com.speechtotext.app.ui.theme.GoldAccent
import com.speechtotext.app.ui.theme.OutlineDark
import com.speechtotext.app.ui.theme.SurfaceDark
import com.speechtotext.app.ui.theme.SurfaceElevatedDark
import com.speechtotext.app.ui.theme.TextPrimary
import com.speechtotext.app.ui.theme.TextSecondary

@Composable
fun StylePicker(
    style: CaptionStyle,
    onStyleChange: (CaptionStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = OutlineDark,
        containerColor = SurfaceDark
    ) {
        Text(
            text = "Caption Styles & Presets",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Presets
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CaptionPreset.values()) { preset ->
                val isSelected = style.preset == preset
                FilterChip(
                    selected = isSelected,
                    onClick = { onStyleChange(CaptionStyle.fromPreset(preset)) },
                    label = { Text(preset.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanNeon,
                        selectedLabelColor = Color.Black,
                        containerColor = SurfaceElevatedDark,
                        labelColor = TextPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) CyanNeon else OutlineDark
                    ),
                    modifier = Modifier.testTag("preset_${preset.name}")
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Position (Top, Middle, Bottom)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Position:", style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CaptionPosition.values().forEach { pos ->
                    val isSelected = style.position == pos
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) CyanNeon else SurfaceElevatedDark)
                            .clickable { onStyleChange(style.copy(position = pos)) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = pos.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = if (isSelected) Color.Black else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Font Size Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Size: ${style.fontSizeSp.toInt()}sp",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                modifier = Modifier.width(90.dp)
            )
            Slider(
                value = style.fontSizeSp,
                onValueChange = { onStyleChange(style.copy(fontSizeSp = it)) },
                valueRange = 14f..36f,
                colors = SliderDefaults.colors(
                    thumbColor = CyanNeon,
                    activeTrackColor = CyanNeon,
                    inactiveTrackColor = OutlineDark
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Highlight Color Picker (Swatches)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Active Word Color:", style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
            val colors = listOf(
                Color(0xFFFFD600), // Yellow
                Color(0xFF00E5FF), // Cyan
                Color(0xFFFF4081), // Pink Neon
                Color(0xFF00E676), // Lime Green
                Color(0xFFFFFFFF)  // White
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                colors.forEach { c ->
                    val isSelected = style.activeHighlightColor == c
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onStyleChange(style.copy(activeHighlightColor = c)) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Background Box Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Background Box:", style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
            Switch(
                checked = style.hasBackgroundBox,
                onCheckedChange = { onStyleChange(style.copy(hasBackgroundBox = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BackgroundDark,
                    checkedTrackColor = CyanNeon,
                    uncheckedTrackColor = OutlineDark
                )
            )
        }
    }
}
