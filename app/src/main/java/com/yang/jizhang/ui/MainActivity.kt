package com.yang.jizhang.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.JizhangApp
import com.yang.jizhang.data.Source
import com.yang.jizhang.data.TxEntity
import com.yang.jizhang.importer.CsvImporter
import com.yang.jizhang.notify.PendingConfirmStore
import com.yang.jizhang.notify.keepalive.KeepAliveService
import com.yang.jizhang.notify.keepalive.ListenerStatus
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { JizhangTheme { AppRoot() } }
    }
}

fun fmtCents(cents: Long): String {
    val abs = Math.abs(cents)
    return "¥${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}

private fun dayKey(millis: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return c.get(Calendar.YEAR) * 10000 + c.get(Calendar.MONTH) * 100 + c.get(Calendar.DAY_OF_MONTH)
}

/** 蓝白背景渐变：云白 → 天蓝，暗色为深海军蓝 */
private fun bgBrush(dark: Boolean): Brush = if (dark) {
    Brush.verticalGradient(listOf(Color(0xFF060A12), Color(0xFF0B1220), Color(0xFF080D18)))
} else {
    Brush.verticalGradient(listOf(Color(0xFFFBFDFF), Color(0xFFEDF4FF), Color(0xFFE1ECFD)))
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { (context.applicationContext as JizhangApp).db.txDao() }
    val dark = isSystemInDarkTheme()

    val today = remember { Calendar.getInstance() }
    var year by rememberSaveable { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var month by rememberSaveable { mutableIntStateOf(today.get(Calendar.MONTH) + 1) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<TxEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    val hazeState = remember { HazeState() }

    val range = remember(year, month) { CategoryRules.monthRange(year, month) }
    val txs by dao.watchRange(range[0], range[1] - 1)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val n = runCatching { CsvImporter.import(context, uri, Source.IMPORT) }.getOrDefault(0)
            Toast.makeText(
                context,
                if (n > 0) "导入成功，新增 $n 条" else "导入失败：请选择支付宝/微信导出的账单 CSV",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val expenseTotal = txs.filter { !it.isIncome }.sumOf { it.amountCents }
    val incomeTotal = txs.filter { it.isIncome }.sumOf { it.amountCents }

    // 监听状态（设置已开 + binder 已连 → 正常）
    val listenerState = remember {
        mutableStateOf(ListenerStatus.displayState(context))
    }
    LaunchedEffect(Unit) {
        // 每隔几秒刷新一次状态，让横幅跟随真实连接
        while (true) {
            listenerState.value = ListenerStatus.displayState(context)
            kotlinx.coroutines.delay(3_000)
        }
    }

    // 待确认账单：从通知确认页/弹窗进入时拉取
    var pending by remember { mutableStateOf<List<PendingConfirmStore.Pending>>(PendingConfirmStore.all()) }
    LaunchedEffect(Unit) {
        while (true) {
            pending = PendingConfirmStore.all()
            kotlinx.coroutines.delay(1_000)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GlassTopBar(hazeState, dark, onImport = {
                picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
            })
        },
        bottomBar = { GlassBottomBar(hazeState, dark, tab) { tab = it } },
        floatingActionButton = { GlassFab(hazeState, dark) { adding = true } },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .background(bgBrush(dark))
                .hazeSource(hazeState)
        ) {
            // 监听状态横幅：未开启 → 引导；已断开 → 提示重连；正常 → 低调提示
            when (listenerState.value) {
                ListenerStatus.DisplayState.NEED_PERMISSION -> {
                    SetupBanner(needPermission = true) {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                }
                ListenerStatus.DisplayState.RECOVERING -> {
                    SetupBanner(needPermission = false) {
                        ListenerStatus.requestRebind(context, force = true)
                        KeepAliveService.start(context)
                    }
                }
                ListenerStatus.DisplayState.CONNECTED -> { /* 静默 */ }
            }
            when (tab) {
                0 -> TxList(
                    txs = txs,
                    contentPad = pad,
                    expense = expenseTotal,
                    income = incomeTotal,
                    year = year, month = month,
                    onPrev = { if (month == 1) { year--; month = 12 } else month-- },
                    onNext = { if (month == 12) { year++; month = 1 } else month++ },
                    onItemClick = { editing = it },
                )
                else -> StatsTab(
                    txs = txs,
                    contentPad = pad,
                )
            }
        }
    }

    // 待确认账单弹窗（依次确认）
    pending.firstOrNull()?.let { p ->
        ConfirmDialog(
            pending = p,
            onClose = { PendingConfirmStore.remove(p.id) },
            onConfirm = { amountCents, income ->
                scope.launch {
                    dao.insertIgnore(
                        TxEntity(
                            amountCents = amountCents,
                            isIncome = income,
                            category = CategoryRules.guess(p.merchant, p.source),
                            note = p.merchant.ifBlank { p.source },
                            source = p.source,
                            time = p.transactionTime,
                            dedupeKey = p.dedupeKey,
                        )
                    )
                }
                PendingConfirmStore.remove(p.id)
            },
            onIgnore = { PendingConfirmStore.remove(p.id) },
        )
    }

    if (adding) {
        TxDialog(
            tx = null,
            onClose = { adding = false },
            onSave = { amount, income, category, note ->
                scope.launch {
                    dao.insertIgnore(
                        TxEntity(
                            amountCents = amount, isIncome = income, category = category,
                            note = note, source = Source.MANUAL,
                            time = System.currentTimeMillis(),
                            dedupeKey = "MANUAL|${System.nanoTime()}",
                        )
                    )
                }
                adding = false
            },
        )
    }
    editing?.let { tx ->
        TxDialog(
            tx = tx,
            onClose = { editing = null },
            onSave = { amount, income, category, note ->
                scope.launch { dao.update(tx.copy(amountCents = amount, isIncome = income, category = category, note = note)) }
                editing = null
            },
            onDelete = {
                scope.launch { dao.delete(tx) }
                editing = null
            },
        )
    }
}

/** iOS 超薄毛玻璃材质 */
@Composable
private fun glassStyle(dark: Boolean, container: Color? = null) =
    HazeMaterials.thin(containerColor = container ?: if (dark) Color(0xFF1C1C1E) else Color.White)

@Composable
private fun GlassTopBar(hazeState: HazeState, dark: Boolean, onImport: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .hazeEffect(state = hazeState, style = glassStyle(dark))
            .statusBarsPadding()
            .height(54.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("蓝鲸记账", fontWeight = FontWeight.Bold, fontSize = 21.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onImport) {
            Icon(Icons.Rounded.UploadFile, contentDescription = "导入账单 CSV", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun GlassBottomBar(hazeState: HazeState, dark: Boolean, tab: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(Modifier.fillMaxWidth().padding(start = 30.dp, end = 30.dp, bottom = 30.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .shadow(elevation = 18.dp, shape = shape, ambientColor = Color(0x332E6BE6), spotColor = Color(0x442E6BE6))
                .clip(shape)
                .hazeEffect(state = hazeState, style = glassStyle(dark))
                .border(0.5.dp, Color.White.copy(alpha = if (dark) 0.12f else 0.65f), shape)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassTab("明细", Icons.AutoMirrored.Outlined.ReceiptLong, tab == 0) { onSelect(0) }
            GlassTab("统计", Icons.Outlined.PieChart, tab == 1) { onSelect(1) }
        }
    }
}

@Composable
private fun GlassTab(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val pill = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(pill)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.height(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 13.sp, color = tint, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun GlassFab(hazeState: HazeState, dark: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .clip(shape)
            .hazeEffect(state = hazeState, style = glassStyle(dark, MaterialTheme.colorScheme.primary))
            .border(0.5.dp, Color.White.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, tint = Color.White)
        Spacer(Modifier.width(5.dp))
        Text("记一笔", color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SetupBanner(needPermission: Boolean, onClick: () -> Unit) {
    Surface(
        color = (if (needPermission) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer)
            .copy(alpha = 0.92f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
    ) {
        Text(
            if (needPermission) "未开启通知监听，点此设置以实时记账"
            else "支付监听已断开，点此重新连接",
            fontSize = 13.sp,
            color = if (needPermission) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** 待确认账单弹窗（自动记账识别结果不确定时弹出，可改金额/收支后入账） */
@Composable
private fun ConfirmDialog(
    pending: PendingConfirmStore.Pending,
    onClose: () -> Unit,
    onConfirm: (amountCents: Long, isIncome: Boolean) -> Unit,
    onIgnore: () -> Unit,
) {
    var amountText by remember(pending.id) {
        mutableStateOf("${pending.amountCents / 100}.${(pending.amountCents % 100).toString().padStart(2, '0')}")
    }
    var isIncome by remember(pending.id) { mutableStateOf(pending.isIncome) }
    val amountCents = amountText.toDoubleOrNull()?.let { Math.round(it * 100) } ?: 0L
    val valid = amountCents > 0

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("待确认账单") },
        text = {
            Column {
                Text(
                    "自动记账识别：${pending.source} · ${pending.reason ?: "金额不确定"}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pending.rawSnippet?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                }
                Spacer(Modifier.height(12.dp))
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
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(amountCents, isIncome) }) { Text("确认入账") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onIgnore) { Text("忽略") }
            }
        },
    )
}

/** 月份切换 + 汇总：蓝白半透明圆角卡片 */
@Composable
private fun MonthCard(
    year: Int, month: Int, expense: Long, income: Long,
    onPrev: () -> Unit, onNext: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(24.dp)
    Surface(
        color = (if (dark) Color(0xFF131A28) else Color.White).copy(alpha = 0.72f),
        shape = shape,
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = if (dark) 0.10f else 0.8f)),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$year 年 $month 月",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable { onPrev() }
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) { Text("‹", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable { onNext() }
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) { Text("›", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("支出", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmtCents(expense), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("收入", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmtCents(income), fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun TxList(
    txs: List<TxEntity>,
    contentPad: PaddingValues,
    expense: Long, income: Long,
    year: Int, month: Int,
    onPrev: () -> Unit, onNext: () -> Unit,
    onItemClick: (TxEntity) -> Unit,
) {
    val dayFormat = remember { SimpleDateFormat("M月d日 EEEE", Locale.CHINA) }
    val groups = remember(txs) {
        txs.groupBy { dayKey(it.time) }
            .map { (key, list) ->
                Triple(
                    dayFormat.format(list.first().time),
                    list,
                    Pair(
                        list.filter { !it.isIncome }.sumOf { it.amountCents },
                        list.filter { it.isIncome }.sumOf { it.amountCents },
                    )
                )
            }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPad.calculateTopPadding() + 6.dp,
            bottom = contentPad.calculateBottomPadding() + 118.dp,
        ),
    ) {
        item(key = "month_card") {
            MonthCard(year, month, expense, income, onPrev, onNext)
        }
        if (txs.isEmpty()) {
            item {
                Text(
                    "本月暂无记录\n点击右下角「记一笔」开始",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 80.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        groups.forEach { (label, list, sums) ->
            item(key = "head_${list.first().id}") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (sums.first > 0) Text("支 ${fmtCents(sums.first)}  ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (sums.second > 0) Text("收 ${fmtCents(sums.second)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            items(list, key = { it.id }) { tx ->
                TxRow(tx, onClick = { onItemClick(tx) })
            }
        }
    }
}

/** 蓝白列表卡片：半透明白圆角 + 分类图标底座 */
@Composable
private fun TxRow(tx: TxEntity, onClick: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(20.dp)
    Surface(
        color = (if (dark) Color(0xFF131A28) else Color.White).copy(alpha = 0.78f),
        shape = shape,
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = if (dark) 0.08f else 0.8f)),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.18f else 0.10f))
                    .size(42.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(CategoryRules.icon(tx.category), fontSize = 20.sp)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    tx.note.ifBlank { tx.category },
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${tx.category} · ${tx.source}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                (if (tx.isIncome) "+" else "-") + fmtCents(tx.amountCents),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = if (tx.isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
