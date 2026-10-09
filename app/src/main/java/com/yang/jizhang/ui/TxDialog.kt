package com.yang.jizhang.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.data.TxEntity

/**
 * 新增 / 编辑交易弹窗。
 * [tx] 为 null 时是新增，否则编辑（可删除）。
 */
@Composable
fun TxDialog(
    tx: TxEntity?,
    onClose: () -> Unit,
    onSave: (amountCents: Long, isIncome: Boolean, category: String, note: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val initialAmount = tx?.let { "%.2f".format(it.amountCents / 100.0) } ?: ""
    var amountText by remember(tx) { mutableStateOf(initialAmount) }
    var isIncome by remember(tx) { mutableStateOf(tx?.isIncome ?: false) }
    var category by remember(tx) { mutableStateOf(tx?.category ?: CategoryRules.ALL_CATEGORIES.first()) }
    var note by remember(tx) { mutableStateOf(tx?.note ?: "") }

    val amountCents = amountText.toDoubleOrNull()?.let { Math.round(it * 100) } ?: 0L
    val valid = amountCents > 0

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (tx == null) "记一笔" else "编辑记录" + (if (tx.source != com.yang.jizhang.data.Source.MANUAL) "（${tx.source}）" else "")) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = !isIncome,
                        onClick = { isIncome = false },
                        label = { Text("支出") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = isIncome,
                        onClick = { isIncome = true },
                        label = { Text("收入") },
                    )
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    CategoryRules.ALL_CATEGORIES.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text("${CategoryRules.icon(c)} $c", fontSize = 12.sp) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 40) note = it },
                    label = { Text("备注") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(amountCents, isIncome, category, note.trim()) }) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )) { Text("删除") }
                }
                TextButton(onClick = onClose) { Text("取消") }
            }
        },
    )
}
