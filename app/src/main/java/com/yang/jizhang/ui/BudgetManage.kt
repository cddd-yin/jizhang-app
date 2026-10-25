package com.yang.jizhang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.data.BudgetDao
import com.yang.jizhang.data.BudgetEntity
import com.yang.jizhang.data.TxDao
import com.yang.jizhang.util.Periods
import kotlinx.coroutines.launch

/**
 * 预算管理（v3.1.0）：总预算 + 分类预算，每月/每周周期，各自启用开关。
 * 首页 BudgetCard 点此进入。
 */
@Composable
fun BudgetManageScreen(
    budgetDao: BudgetDao,
    txDao: TxDao,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val budgets by budgetDao.watchAll().collectAsStateWithLifecycle(initialValue = emptyList())

    // 取最早窗口起点以来的流水，按各预算的当前窗口分别求已用
    val earliestStart = remember(budgets) {
        budgets.minOfOrNull { Periods.currentWindow(it.period)[0] } ?: 0L
    }
    val txsSince by txDao.watchSince(earliestStart).collectAsStateWithLifecycle(initialValue = emptyList())

    var editing by remember { mutableStateOf<BudgetEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    data class BudgetProgress(val budget: BudgetEntity, val spent: Long)

    val rows = remember(budgets, txsSince) {
        budgets.map { b ->
            val win = Periods.currentWindow(b.period)
            val spent = txsSince
                .filter { !it.isIncome && it.time in win[0] until win[1] }
                .filter { b.category.isEmpty() || it.category == b.category }
                .sumOf { it.amountCents }
            BudgetProgress(b, spent)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text("预算管理", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp,
                    // Android 15 强制 edge-to-edge：列表末端避开手势条/导航栏
                    bottom = androidx.compose.foundation.layout.WindowInsets.navigationBars
                        .asPaddingValues().calculateBottomPadding() + 32.dp,
                ),
            ) {
                item(key = "hint") {
                    Text(
                        "为总支出或单个分类设预算，每月 / 每周自动重置；超支的会变红显示在账本首页。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                }
                items(rows.size, key = { rows[it].budget.id }) { i ->
                    val (b, spent) = rows[i]
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    ) {
                        Column(
                            Modifier.clickable { editing = b }.padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (b.category.isEmpty()) {
                                    Box(
                                        Modifier.size(40.dp).clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center,
                                    ) { Text("¥", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                                } else {
                                    CategoryCircle(b.category, size = 40.dp)
                                }
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            b.category.ifEmpty { "总预算" },
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            Periods.label(b.period),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Switch(
                                            checked = b.enabled,
                                            onCheckedChange = { on ->
                                                scope.launch { budgetDao.update(b.copy(enabled = on)) }
                                            },
                                            modifier = Modifier.height(26.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(5.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "已用 ${fmtCents(spent)} / ${fmtCents(b.amountCents)}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "${if (b.amountCents > 0) (spent * 100 / b.amountCents) else 0}%",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (spent > b.amountCents) expenseColor() else MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    // v5.1.0 内置算法建议（口径同 Firefly III left-to-spend + 外推）
                                    val advice = remember(b, spent) {
                                        com.yang.jizhang.util.BudgetAdvisor.advise(b, spent)
                                    }
                                    Text(
                                        advice.message,
                                        fontSize = 11.sp,
                                        color = when (advice.pace) {
                                            com.yang.jizhang.util.BudgetAdvisor.Pace.OVER,
                                            com.yang.jizhang.util.BudgetAdvisor.Pace.FAST,
                                            -> expenseColor()
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    val frac = if (b.amountCents > 0) (spent.toFloat() / b.amountCents).coerceIn(0f, 1f) else 0f
                                    val over = spent > b.amountCents
                                    Box(
                                        Modifier.fillMaxWidth().height(5.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    ) {
                                        Box(
                                            Modifier.fillMaxWidth(frac).height(5.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(if (over) expenseColor() else MaterialTheme.colorScheme.primary),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (i < rows.lastIndex) {
                        Spacer(Modifier.height(2.dp))
                    }
                }
                item(key = "add") {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) {
                        Text(
                            "＋ 新建预算",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { adding = true }
                                .padding(vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        BudgetEditDialog(
            budget = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { category, period, cents ->
                scope.launch {
                    val b = editing
                    if (b == null) {
                        budgetDao.insert(BudgetEntity(category = category, period = period, amountCents = cents))
                    } else {
                        budgetDao.update(b.copy(category = category, period = period, amountCents = cents))
                    }
                }
                adding = false
                editing = null
            },
            onDelete = editing?.let { b ->
                {
                    scope.launch { budgetDao.delete(b) }
                    editing = null
                }
            },
        )
    }
}

/** 预算编辑弹窗：类型（总/分类）+ 分类 + 周期（每月/每周）+ 金额 */
@Composable
private fun BudgetEditDialog(
    budget: BudgetEntity?,
    onDismiss: () -> Unit,
    onSave: (category: String, period: String, cents: Long) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var category by remember { mutableStateOf(budget?.category ?: "") } // "" = 总预算
    var period by remember { mutableStateOf(budget?.period ?: Periods.MONTHLY) }
    var amountText by remember {
        mutableStateOf(
            budget?.let { b -> (b.amountCents / 100).toString() + "." + (b.amountCents % 100).toString().padStart(2, '0') } ?: ""
        )
    }
    val cents = amountText.toDoubleOrNull()?.let { Math.round(it * 100) } ?: 0L
    val valid = cents > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (budget == null) "新建预算" else "编辑预算", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                // 周期
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(4.dp)) {
                        listOf(Periods.MONTHLY to "每月", Periods.WEEKLY to "每周").forEach { (p, label) ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (period == p) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { period = p }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (period == p) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("适用范围", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                (listOf("" to "总预算") + com.yang.jizhang.util.CategoryStore.all(
                    androidx.compose.ui.platform.LocalContext.current
                ).map { it to it }).chunked(4).forEach { rowCats ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        rowCats.forEach { (cat, label) ->
                            Text(
                                label,
                                fontSize = 12.sp,
                                fontWeight = if (category == cat) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (category == cat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (category == cat) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                                        else androidx.compose.ui.graphics.Color.Transparent
                                    )
                                    .clickable { category = cat }
                                    .padding(vertical = 6.dp, horizontal = 2.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                        repeat(4 - rowCats.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { s ->
                        if (s.length <= 10 && (s.isEmpty() || Regex("""^\d*(\.\d{0,2})?$""").matches(s))) amountText = s
                    },
                    label = { Text("预算金额（元）") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(category, period, cents) }) {
                Text("保存", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("删除", color = expenseColor()) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
