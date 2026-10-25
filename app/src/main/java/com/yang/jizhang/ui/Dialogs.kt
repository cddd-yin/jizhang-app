package com.yang.jizhang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.jizhang.engine.UncertainBillQueue

/**
 * v3.0.0 全局弹窗：待确认账单（自动记账识别不确定时）与 CSV 导出菜单。
 */

/** 待确认账单弹窗：可改金额/收支后入账 */
@Composable
fun PendingConfirmDialog(
    pending: UncertainBillQueue.Pending,
    onClose: () -> Unit,
    onConfirm: (amountCents: Long, isIncome: Boolean) -> Unit,
    onIgnore: () -> Unit,
) {
    var amountText by remember(pending.id) {
        mutableStateOf(
            "${pending.amountCents / 100}.${(pending.amountCents % 100).toString().padStart(2, '0')}"
        )
    }
    var isIncome by remember(pending.id) { mutableStateOf(pending.isIncome) }
    val amountCents = amountText.toDoubleOrNull()?.let { Math.round(it * 100) } ?: 0L
    val valid = amountCents > 0

    AlertDialog(
        onDismissRequest = onClose,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("待确认账单", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "自动记账识别：${pending.source} · ${pending.reason ?: "金额不确定"}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pending.rawSnippet?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(4.dp)) {
                        listOf("支出" to false, "收入" to true).forEach { (label, income) ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clipSegment(isIncome == income)
                                    .clickable { isIncome = income }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isIncome == income) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { s ->
                        if (s.length <= 10 && (s.isEmpty() || Regex("""^\d*(\.\d{0,2})?$""").matches(s))) {
                            amountText = s
                        }
                    },
                    label = { Text("金额（元）") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(amountCents, isIncome) }) {
                Text("确认入账", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onIgnore) { Text("忽略") }
        },
    )
}

@Composable
private fun Modifier.clipSegment(selected: Boolean): Modifier =
    if (selected) this.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
    else this

/** 导出菜单：本月 / 全部 */
@Composable
fun ExportDialog(
    monthLabel: String,
    onDismiss: () -> Unit,
    onExportMonth: () -> Unit,
    onExportAll: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("导出账单", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                ExportOption("本月账单（$monthLabel）", onExportMonth)
                ExportOption("全部账单", onExportAll)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ExportOption(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
