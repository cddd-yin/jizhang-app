package com.yang.jizhang.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 自定义颜色方案（v5.2.0）：18 色精选色板，选种子色即生成整套皮肤
 * （深色模式提亮 / 浅色模式压深自动派生，亮色系自动配深前景字）。
 */
@Composable
fun ColorPickerDialog(
    currentSeed: Int?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = remember {
        listOf(
            0xFF5B5BD6, 0xFF7C4DFF, 0xFF9C27B0, 0xFFE24C7B, 0xFFFF5C8A, 0xFFFF7043,
            0xFFB25E09, 0xFFF2B705, 0xFF7CB342, 0xFF0FA968, 0xFF00897B, 0xFF0084D1,
            0xFF00A6F4, 0xFF283593, 0xFF3F51B5, 0xFF607D8B, 0xFF455A64, 0xFF8D6E63,
        ).map { it.toInt() }
    }
    var picked by remember { mutableIntStateOf(currentSeed ?: palette[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("自定义颜色", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                // 当前选中预览
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(picked)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("¥", fontSize = 18.sp, color = if (lumaPreview(Color(picked)) > 0.62f) Color(0xFF10130E) else Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(
                        "以这个颜色生成整套皮肤：深色提亮、浅色压深，自动配对比前景",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(14.dp))
                palette.chunked(6).forEach { rowColors ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        rowColors.forEach { c ->
                            val selected = c == picked
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(
                                        width = if (selected) 3.dp else 0.dp,
                                        color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape,
                                    )
                                    .clickable { picked = c },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = if (lumaPreview(Color(c)) > 0.62f) Color(0xFF10130E) else Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(picked) }) {
                Text("用这个颜色", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun lumaPreview(c: Color): Float = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
