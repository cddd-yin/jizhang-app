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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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

/** iOS 分组背景色 */
@Composable
private fun iosBackground(): Color = MaterialTheme.colorScheme.background

/** iOS 卡片色 */
@Composable
private fun iosCard(): Color = MaterialTheme.colorScheme.surface

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
        containerColor = iosBackground(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            IosTopBar(
                hazeState = hazeState,
                dark = dark,
                onAdd = { adding = true },
                onImport = {
                    picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
                },
            )
        },
        bottomBar = { IosTabBar(hazeState, dark, tab) { tab = it } },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .background(iosBackground())
                .hazeSource(hazeState),
        ) {
            // 监听状态横幅：未开启 → 引导；已断开 → 提示重连；正常 → 静默
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

/** iOS 毛玻璃材质 */
@Composable
private fun glassStyle(dark: Boolean, container: Color? = null) =
    HazeMaterials.thin(containerColor = container ?: if (dark) Color(0xFF1C1C1E) else Color.White)

/** iOS 导航栏：大标题 + 右侧「+」「导入」 */
@Composable
private fun IosTopBar(
    hazeState: HazeState,
    dark: Boolean,
    onAdd: () -> Unit,
    onImport: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .hazeEffect(state = hazeState, style = glassStyle(dark))
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "账单",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAdd) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "记一笔",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
            IconButton(onClick = onImport) {
                Icon(
                    Icons.Rounded.UploadFile,
                    contentDescription = "导入账单 CSV",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** iOS 底部 Tab Bar：全宽毛玻璃 + 极细分割线 */
@Composable
private fun IosTabBar(hazeState: HazeState, dark: Boolean, tab: Int, onSelect: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .hazeEffect(state = hazeState, style = glassStyle(dark)),
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(52.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IosTab("明细", Icons.AutoMirrored.Outlined.ReceiptLong, tab == 0) { onSelect(0) }
            IosTab("统计", Icons.Outlined.PieChart, tab == 1) { onSelect(1) }
        }
    }
}

@Composable
private fun RowScope.IosTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = tint, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SetupBanner(needPermission: Boolean, onClick: () -> Unit) {
    val tint = if (needPermission) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    val container = if (needPermission) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.tertiaryContainer
    Surface(
        color = container.copy(alpha = 0.9f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (needPermission) "未开启通知监听，点此设置以自动记账"
                else "支付监听已断开，点此重新连接",
                fontSize = 13.sp,
                color = tint,
                modifier = Modifier.weight(1f),
            )
            Text("›", fontSize = 18.sp, color = tint, fontWeight = FontWeight.SemiBold)
        }
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
        shape = RoundedCornerShape(16.dp),
        containerColor = iosCard(),
        title = {
            Text("待确认账单", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        },
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

/** iOS 月份汇总卡片 */
@Composable
private fun MonthCard(
    year: Int, month: Int, expense: Long, income: Long,
    onPrev: () -> Unit, onNext: () -> Unit,
) {
    val card = iosCard()
    Surface(
        color = card,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$year 年 $month 月",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f),
                )
                IosChevron("‹", onPrev)
                Spacer(Modifier.width(4.dp))
                IosChevron("›", onNext)
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("支出", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(fmtCents(expense), fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("收入", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        fmtCents(income),
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun IosChevron(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
            .sortedByDescending { it.second.first().time }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPad.calculateTopPadding() + 4.dp,
            bottom = contentPad.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item(key = "month_card") {
            MonthCard(year, month, expense, income, onPrev, onNext)
        }
        if (txs.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🪙", fontSize = 44.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "本月暂无记录",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点击右上角 + 记一笔",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        groups.forEach { (label, list, sums) ->
            item(key = "head_${list.first().id}") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (sums.first > 0) {
                        Text("支 ${fmtCents(sums.first)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (sums.second > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text("收 ${fmtCents(sums.second)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            item(key = "group_${list.first().id}") {
                Surface(
                    color = iosCard(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column {
                        list.forEachIndexed { index, tx ->
                            TxRow(tx, showDivider = index < list.lastIndex) { onItemClick(tx) }
                        }
                    }
                }
            }
        }
    }
}

/** iOS 分组列表行 */
@Composable
private fun TxRow(tx: TxEntity, showDivider: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 14.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .size(38.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(CategoryRules.icon(tx.category), fontSize = 19.sp)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    tx.note.ifBlank { tx.category },
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
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
                fontSize = 17.sp,
                color = if (tx.isIncome) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            )
        }
    }
}
