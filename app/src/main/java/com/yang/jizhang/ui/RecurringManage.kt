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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.data.RecurringDao
import com.yang.jizhang.data.RecurringEntity
import com.yang.jizhang.util.Periods
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 周期记账（v3.1.0）：房租、订阅等固定收支按周/月自动入账（补账引擎见 RecurringHelper）。
 */
@Composable
fun RecurringManageScreen(
    recurringDao: RecurringDao,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val rules by recurringDao.watchAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<RecurringEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

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
                Text("周期记账", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp,
                    bottom = androidx.compose.foundation.layout.WindowInsets.navigationBars
                        .asPaddingValues().calculateBottomPadding() + 32.dp,
                ),
            ) {
                item(key = "hint") {
                    Text(
                        "到点自动入账（含错过的补账）；App 打开或后台心跳时执行，无需保持界面。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                }
                if (rules.isEmpty()) {
                    item(key = "empty") {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            EmptyState("还没有周期规则", "房租、工资、订阅这类固定收支，设一次每期自动记")
                        }
                    }
                }
                items(rules.size, key = { rules[it].id }) { i ->
                    val r = rules[i]
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    ) {
                        Row(
                            Modifier.clickable { editing = r }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryCircle(r.category)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.title.ifBlank { r.category }, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "${Periods.anchorLabel(r.period, r.anchor)} · 下次 ${
                                        SimpleDateFormat("M月d日", Locale.CHINA).format(
                                            Date(Periods.nextAnchorAt(r.period, r.anchor))
                                        )
                                    }",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    (if (r.isIncome) "+" else "-") + fmtMoney(r.amountCents),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (r.isIncome) incomeColor() else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    if (r.enabled) "已启用" else "已停用",
                                    fontSize = 11.sp,
                                    color = if (r.enabled) incomeColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = r.enabled,
                                onCheckedChange = { on -> scope.launch { recurringDao.update(r.copy(enabled = on)) } },
                                modifier = Modifier.padding(start = 6.dp).height(26.dp),
                            )
                        }
                    }
                }
                item(key = "add") {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) {
                        Text(
                            "＋ 新建周期规则",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
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
        RecurringEditDialog(
            rule = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { title, amountCents, isIncome, category, note, period, anchor ->
                scope.launch {
                    val r = editing
                    if (r == null) {
                        recurringDao.insert(
                            RecurringEntity(
                                title = title, amountCents = amountCents, isIncome = isIncome,
                                category = category, note = note, period = period, anchor = anchor,
                            )
                        )
                    } else {
                        recurringDao.update(
                            r.copy(
                                title = title, amountCents = amountCents, isIncome = isIncome,
                                category = category, note = note, period = period, anchor = anchor,
                            )
                        )
                    }
                }
                adding = false
                editing = null
            },
            onDelete = editing?.let { r ->
                {
                    scope.launch { recurringDao.delete(r) }
                    editing = null
                }
            },
        )
    }
}

/** 规则编辑弹窗：收支 / 名称 / 金额 / 分类 / 周期与锚点 */
@Composable
private fun RecurringEditDialog(
    rule: RecurringEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, amountCents: Long, isIncome: Boolean, category: String, note: String, period: String, anchor: Int) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var isIncome by remember { mutableStateOf(rule?.isIncome ?: false) }
    var title by remember { mutableStateOf(rule?.title ?: "") }
    var note by remember { mutableStateOf(rule?.note ?: "") }
    var category by remember { mutableStateOf(rule?.category ?: "餐饮美食") }
    var period by remember { mutableStateOf(rule?.period ?: Periods.MONTHLY) }
    var anchor by remember { mutableStateOf(rule?.anchor ?: 1) }
    var amountText by remember {
        mutableStateOf(
            rule?.let { r -> (r.amountCents / 100).toString() + "." + (r.amountCents % 100).toString().padStart(2, '0') } ?: ""
        )
    }
    val cents = amountText.toDoubleOrNull()?.let { Math.round(it * 100) } ?: 0L
    val valid = cents > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (rule == null) "新建周期规则" else "编辑周期规则", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // 收支分段
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(4.dp)) {
                        listOf(false to "支出", true to "收入").forEach { (income, label) ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isIncome == income) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { isIncome = income }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isIncome == income) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 20) title = it },
                    label = { Text("名称（如 房租 / 视频会员）") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { s ->
                        if (s.length <= 10 && (s.isEmpty() || Regex("""^\d*(\.\d{0,2})?$""").matches(s))) amountText = s
                    },
                    label = { Text("每期金额（元）") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                // 分类
                Text("分类", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                com.yang.jizhang.util.CategoryStore.all(
                    androidx.compose.ui.platform.LocalContext.current
                ).chunked(4).forEach { rowCats ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        rowCats.forEach { cat ->
                            Text(
                                cat,
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
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                        repeat(4 - rowCats.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                // 周期
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(4.dp)) {
                        listOf(Periods.MONTHLY to "每月", Periods.WEEKLY to "每周").forEach { (p, label) ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (period == p) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable {
                                        period = p
                                        anchor = if (p == Periods.WEEKLY) 1 else 1
                                    }
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
                Spacer(Modifier.height(8.dp))
                // 锚点选择
                Text(
                    if (period == Periods.WEEKLY) "每周几执行" else "每月几号执行",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                if (period == Periods.WEEKLY) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Periods.WEEK_NAMES.forEachIndexed { i, name ->
                            val day = i + 1
                            Text(
                                name,
                                fontSize = 13.sp,
                                fontWeight = if (anchor == day) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (anchor == day) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (anchor == day) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { anchor = day }
                                    .padding(vertical = 7.dp),
                            )
                        }
                    }
                } else {
                    (1..28).chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            week.forEach { day ->
                                Text(
                                    "$day",
                                    fontSize = 12.sp,
                                    fontWeight = if (anchor == day) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (anchor == day) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(
                                            if (anchor == day) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { anchor = day }
                                        .padding(vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "下次执行：" + SimpleDateFormat("M月d日", Locale.CHINA).format(Date(Periods.nextAnchorAt(period, anchor))),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 30) note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid && title.isNotBlank(), onClick = {
                onSave(title.trim(), cents, isIncome, category, note.trim(), period, anchor)
            }) {
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
