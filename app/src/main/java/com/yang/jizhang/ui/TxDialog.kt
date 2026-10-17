package com.yang.jizhang.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.data.TxEntity

/**
 * iOS 分段控件（支出 / 收入）。
 */
@Composable
fun IosSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            .padding(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .then(
                        if (selected) Modifier
                            .shadow(1.5.dp, RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.surface)
                        else Modifier
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onSelect(index) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 新增 / 编辑交易弹窗（iOS Alert 风格）。
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
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                if (tx == null) "记一笔" else "编辑记录" + (if (tx.source != com.yang.jizhang.data.Source.MANUAL) "（${tx.source}）" else ""),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column {
                IosSegmented(
                    options = listOf("支出", "收入"),
                    selectedIndex = if (isIncome) 1 else 0,
                    onSelect = { isIncome = it == 1 },
                )
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
                Spacer(Modifier.height(12.dp))
                Text("分类", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    CategoryRules.ALL_CATEGORIES.forEach { c ->
                        val selected = category == c
                        Box(
                            Modifier
                                .padding(end = 6.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                )
                                .clickable { category = c }
                                .padding(horizontal = 11.dp, vertical = 7.dp),
                        ) {
                            Text(
                                "${CategoryRules.icon(c)} $c",
                                fontSize = 12.sp,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 40) note = it },
                    label = { Text("备注") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(amountCents, isIncome, category, note.trim()) }) {
                Text("保存", fontWeight = FontWeight.SemiBold)
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
