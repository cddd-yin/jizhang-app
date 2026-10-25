package com.yang.jizhang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.data.TxEntity

/**
 * 记一笔 / 编辑账单（全屏，Expenso 计算器键盘同款交互）：
 * 支出/收入分段 → 选分类 → 填备注 → 键盘敲金额 → 右上角 ✓ 保存。
 * draft：分享/划词入口预填草稿（仅添加模式生效）。
 */
@Composable
fun TxEditor(
    tx: TxEntity?,
    onClose: () -> Unit,
    onSave: (amountCents: Long, isIncome: Boolean, category: String, note: String) -> Unit,
    onDelete: () -> Unit,
    draft: ShareDraftItem? = null,
) {
    var isIncome by remember { mutableStateOf(tx?.isIncome ?: (draft?.isIncome ?: false)) }
    var category by remember {
        mutableStateOf(
            tx?.category
                ?: draft?.category
                ?: if (tx?.isIncome == true) "收入进账" else "餐饮美食"
        )
    }
    var note by remember { mutableStateOf(tx?.note ?: draft?.note ?: "") }
    var amountText by remember {
        mutableStateOf(
            tx?.let { t ->
                (t.amountCents / 100).toString() + "." + (t.amountCents % 100).toString().padStart(2, '0')
            } ?: draft?.let { d ->
                (d.amountCents / 100).toString() + "." + (d.amountCents % 100).toString().padStart(2, '0')
            }
            ?: ""
        )
    }

    val cents = parseCents(amountText)
    val valid = cents > 0

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶栏：关闭 | 标题 | 删除(编辑时) | 保存
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    if (tx == null) "记一笔" else "编辑账单",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                if (tx != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "删除",
                            tint = expenseColor(),
                        )
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (valid) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                        .clickable(enabled = valid) {
                            onSave(cents, isIncome, category, note.trim())
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = "保存", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(6.dp))

                // ── 支出 / 收入分段 ──
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(5.dp)) {
                        SegOption(
                            "支出", Icons.Outlined.ArrowDownward, !isIncome,
                            Modifier.weight(1f),
                        ) {
                            isIncome = false
                            if (category == "收入进账") category = "餐饮美食"
                        }
                        SegOption(
                            "收入", Icons.Outlined.ArrowUpward, isIncome,
                            Modifier.weight(1f),
                        ) {
                            isIncome = true
                            if (category == "餐饮美食") category = "收入进账"
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── 分类选择 ──
                Text(
                    "分类",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                CategoryRules.ALL_CATEGORIES.chunked(4).forEach { rowCats ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        rowCats.forEach { cat ->
                            CategoryPick(
                                category = cat,
                                selected = cat == category,
                                modifier = Modifier.weight(1f),
                            ) { category = cat }
                        }
                        // 补齐最后一行空位
                        repeat(4 - rowCats.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ── 备注 ──
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = note,
                        onValueChange = { if (it.length <= 30) note = it },
                        singleLine = true,
                        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                        decorationBox = { inner ->
                            Box {
                                if (note.isEmpty()) {
                                    Text(
                                        "备注（可选）",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }

                Spacer(Modifier.height(14.dp))
            }

            // ── 金额显示 + 键盘 ──
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 0.dp, bottomStart = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "¥",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isIncome) incomeColor() else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            amountText.ifEmpty { "0.00" },
                            fontSize = 38.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.5).sp,
                            color = if (isIncome) incomeColor() else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        if (amountText.isNotEmpty()) {
                            Text(
                                "清空",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { amountText = "" }
                                    .padding(6.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Keypad(
                        onDigit = { d -> amountText = appendDigit(amountText, d) },
                        onDot = { if (!amountText.contains('.')) amountText = amountText.ifEmpty { "0" } + "." },
                        onBackspace = { amountText = amountText.dropLast(1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SegOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
            .clickable {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryPick(
    category: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(
        modifier.clickable {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            onClick()
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (selected) categoryHue(category) else categoryHue(category).copy(alpha = 0.16f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(CategoryRules.icon(category), fontSize = 21.sp)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            category,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 键盘：3×4 数字区 + 退格（按压带触感反馈） */
@Composable
private fun Keypad(
    onDigit: (Char) -> Unit,
    onDot: () -> Unit,
    onBackspace: () -> Unit,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val keys = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('.', '0', '⌫'),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                when (k) {
                                    '.' -> onDot()
                                    '⌫' -> onBackspace()
                                    else -> onDigit(k)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == '⌫') {
                            Icon(
                                Icons.Outlined.Backspace,
                                contentDescription = "退格",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            Text(k.toString(), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

private fun appendDigit(current: String, d: Char): String {
    if (current == "0") return d.toString()
    val dot = current.indexOf('.')
    if (dot >= 0) {
        if (current.length - dot - 1 >= 2) return current // 最多两位小数
    } else if (current.length >= 8) {
        return current // 整数位上限 8 位
    }
    return current + d
}

private fun parseCents(s: String): Long {
    if (s.isEmpty()) return 0
    val parts = s.split(".")
    val yuan = parts[0].toLongOrNull() ?: 0L
    val fen = when {
        parts.size == 1 -> 0L
        parts[1].isEmpty() -> 0L
        parts[1].length == 1 -> (parts[1].toLongOrNull() ?: 0L) * 10
        else -> parts[1].take(2).toLongOrNull() ?: 0L
    }
    return yuan * 100 + fen
}
