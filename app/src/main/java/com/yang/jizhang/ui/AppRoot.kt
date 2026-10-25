package com.yang.jizhang.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.JizhangApp
import com.yang.jizhang.data.Source
import com.yang.jizhang.data.TxEntity
import com.yang.jizhang.notify.PendingConfirmStore
import com.yang.jizhang.notify.keepalive.KeepAliveService
import com.yang.jizhang.notify.keepalive.ListenerStatus
import com.yang.jizhang.util.CsvExporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * v3.0.0 根骨架：四 Tab（账本/明细/统计/设置）+ 记一笔 FAB + 全局接线。
 * 月度浏览状态（year/month）在明细与统计间共享；账本恒显当前月。
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { (context.applicationContext as JizhangApp).db.txDao() }

    // ── 主题（v3.7.0：5 套预设 × 3 档深浅，设置页即点即生效并落盘） ──
    var themePreset by remember { mutableStateOf(ThemePrefs.preset(context)) }
    var themeMode by remember { mutableStateOf(ThemePrefs.mode(context)) }

    // ── 全局状态 ──
    val today = remember { Calendar.getInstance() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var year by rememberSaveable { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var month by rememberSaveable { mutableIntStateOf(today.get(Calendar.MONTH) + 1) }
    var editing by remember { mutableStateOf<TxEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showBudgets by remember { mutableStateOf(false) }
    var showRecurring by remember { mutableStateOf(false) }
    var shareDraft by remember { mutableStateOf<ShareDraftItem?>(null) }
    var editorKey by remember { mutableIntStateOf(0) }

    val range = remember(year, month) { CategoryRules.monthRange(year, month) }
    val txs by dao.watchRange(range[0], range[1] - 1)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // 主题应用：模式换算 + 全树包裹（弹窗/弹层一并生效）
    val darkTheme = when (themeMode) {
        JzMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        JzMode.LIGHT -> false
        JzMode.DARK -> true
    }
    JizhangTheme(darkTheme = darkTheme, preset = themePreset) {

    // ── 交互基础（v3.3.0）──
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    // 系统返回键：优先关闭弹层（记账页/预算管理/周期记账），而不是退出 App
    androidx.activity.compose.BackHandler(
        enabled = adding || editing != null || showBudgets || showRecurring
    ) {
        when {
            adding || editing != null -> { adding = false; editing = null; shareDraft = null }
            showBudgets -> showBudgets = false
            showRecurring -> showRecurring = false
        }
    }
    // 左滑删除账单 + Snackbar 撤销
    val deleteWithUndo: (TxEntity) -> Unit = { tx ->
        scope.launch {
            runCatching { dao.delete(tx) }
            val result = snackbarHostState.showSnackbar(
                message = "已删除「${tx.note.ifBlank { tx.category }}」",
                actionLabel = "撤销",
                duration = androidx.compose.material3.SnackbarDuration.Short,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                runCatching { dao.insertIgnore(tx) }
            }
        }
    }

    // ── 轮询全部改为 STARTED 感知：退到后台/锁屏时暂停，省电且不空转（v3.5.0）──
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    // 周期记账补账：回到前台即补（含错过的期次），期间每 6 小时复查
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                runCatching { com.yang.jizhang.util.RecurringHelper.processDue(context) }
                delay(6 * 3600_000L)
            }
        }
    }

    // 分享/划词草稿 与 快捷磁贴「记一笔」请求
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                if (UiBus.openEditorRequest) {
                    UiBus.openEditorRequest = false
                    editing = null
                    shareDraft = null
                    adding = true
                    editorKey++ // 已打开的记账页强制重建，磁贴重复触发也生效
                }
                UiBus.sharePending?.let { draft ->
                    UiBus.sharePending = null
                    editing = null
                    shareDraft = draft
                    adding = true
                    editorKey++ // 记账页已开时又收到分享 → 丢弃旧输入，换上新草稿
                }
                delay(300)
            }
        }
    }

    // 桌面小组件：打开 App 时刷新一次
    LaunchedEffect(Unit) {
        runCatching { com.yang.jizhang.widget.WidgetSync.refresh(context) }
    }

    // Android 13+ 首次进入运行时申请通知权限（否则「已记一笔」等通知不可见）
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isPostNotificationsGranted(context)
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 监听状态轮询（STARTED 感知）
    val listenerState = remember { mutableStateOf(ListenerStatus.displayState(context)) }
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                listenerState.value = ListenerStatus.displayState(context)
                delay(3_000)
            }
        }
    }

    // 待确认账单轮询（STARTED 感知）
    var pending by remember { mutableStateOf<List<PendingConfirmStore.Pending>>(PendingConfirmStore.all()) }
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                pending = PendingConfirmStore.all()
                delay(1_000)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { adding = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "记一笔", modifier = Modifier.size(26.dp))
            }
        },
        bottomBar = { JzBottomBar(tab) { tab = it } },
    ) { pad ->
        androidx.compose.animation.AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) +
                        androidx.compose.animation.slideInVertically(
                            androidx.compose.animation.core.tween(220),
                        ) { it / 22 }
                    ).togetherWith(
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)),
                )
            },
            label = "tabSwitch",
        ) { t ->
            when (t) {
                0 -> HomeTab(
                    dao = dao,
                    budgetDao = (context.applicationContext as JizhangApp).db.budgetDao(),
                    contentPad = pad,
                    listenerState = listenerState.value,
                    onEdit = { editing = it },
                    onGoRecords = { tab = 1 },
                    onOpenBudgets = { showBudgets = true },
                    onDelete = deleteWithUndo,
                )
                1 -> RecordsTab(
                    txs = txs,
                    contentPad = pad,
                    year = year,
                    month = month,
                    onMonthChange = { y, m -> year = y; month = m },
                    onItemClick = { editing = it },
                    onDelete = deleteWithUndo,
                )
                2 -> StatsTab(
                    txs = txs,
                    contentPad = pad,
                    dao = dao,
                    year = year,
                    month = month,
                    onMonthChange = { y, m -> year = y; month = m },
                )
                else -> SettingsTab(
                    contentPad = pad,
                    listenerState = listenerState.value,
                    onExport = { showExport = true },
                    onOpenRecurring = { showRecurring = true },
                    themePreset = themePreset,
                    themeMode = themeMode,
                    onThemeChange = { p, m ->
                        themePreset = p
                        themeMode = m
                        ThemePrefs.save(context, p, m)
                        runCatching { com.yang.jizhang.widget.WidgetSync.refreshAsync(context) }
                    },
                )
            }
        }
    }

    // 待确认账单弹窗（依次确认）
    pending.firstOrNull()?.let { p ->
        PendingConfirmDialog(
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
                    com.yang.jizhang.util.BudgetAlertHelper.checkAsync(context)
                }
                PendingConfirmStore.remove(p.id)
            },
            onIgnore = { PendingConfirmStore.remove(p.id) },
        )
    }

    // 记一笔 / 编辑（全屏计算器，一个入口两用；editorKey 变化强制重建以应用新草稿）
    if (adding || editing != null) {
        androidx.compose.runtime.key(editorKey) {
            TxEditor(
                tx = editing,
                draft = shareDraft,
                onClose = { adding = false; editing = null; shareDraft = null },
            onSave = { amount, income, category, note ->
                val tx = editing
                // 分类自学习（v3.11.0）：记住这笔的「商户→分类」，下次自动记账直接用
                com.yang.jizhang.util.CategoryLearner.learn(context, note, category)
                scope.launch {
                        if (tx == null) {
                            dao.insertIgnore(
                                TxEntity(
                                    amountCents = amount,
                                    isIncome = income,
                                    category = category,
                                    note = note,
                                    source = Source.MANUAL,
                                    time = System.currentTimeMillis(),
                                    dedupeKey = "MANUAL|${System.nanoTime()}",
                                )
                            )
                        } else {
                            dao.update(
                                tx.copy(amountCents = amount, isIncome = income, category = category, note = note)
                            )
                        }
                        runCatching { com.yang.jizhang.widget.WidgetSync.refresh(context) }
                        com.yang.jizhang.util.BudgetAlertHelper.checkAsync(context)
                    }
                    adding = false
                    editing = null
                    shareDraft = null
                },
                onDelete = {
                    val tx = editing
                    editing = null
                    if (tx != null) deleteWithUndo(tx) // 编辑页删除同样可撤销
                },
            )
        }
    }

    // 导出菜单
    if (showExport) {
        ExportDialog(
            monthLabel = "$year 年 $month 月",
            onDismiss = { showExport = false },
            onExportMonth = {
                showExport = false
                scope.launch {
                    val uri = CsvExporter.export(context, range[0], range[1] - 1)
                    handleExportResult(context, uri)
                }
            },
            onExportAll = {
                showExport = false
                scope.launch {
                    val uri = CsvExporter.exportAll(context)
                    handleExportResult(context, uri)
                }
            },
        )
    }

    // 预算管理 / 周期记账（全屏管理页，覆盖主界面）
    if (showBudgets) {
        BudgetManageScreen(
            budgetDao = (context.applicationContext as JizhangApp).db.budgetDao(),
            txDao = dao,
            onClose = { showBudgets = false },
        )
    }
    if (showRecurring) {
        RecurringManageScreen(
            recurringDao = (context.applicationContext as JizhangApp).db.recurringDao(),
            onClose = { showRecurring = false },
        )
    }

    } // JizhangTheme
}

internal fun handleExportResult(context: Context, uri: Uri?) {
    if (uri != null) {
        CsvExporter.shareFile(context, uri)
    } else {
        Toast.makeText(context, "没有可导出的账单记录", Toast.LENGTH_LONG).show()
    }
}

/** 供重连横幅/设置页共用：强制重连监听 */
internal fun forceReconnect(context: Context) {
    ListenerStatus.requestRebind(context, force = true)
    KeepAliveService.start(context)
}

/** 供状态横幅共用：跳系统通知使用权设置 */
internal fun openListenerSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (e: Exception) {
        context.startActivity(
            Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    }
}

// ── 底部导航（Expenso 胶囊高亮同款） ──

private data class JzTabItem(val label: String, val icon: ImageVector)

@Composable
private fun JzBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = remember {
        listOf(
            JzTabItem("账本", Icons.Outlined.Home),
            JzTabItem("明细", Icons.AutoMirrored.Outlined.ReceiptLong),
            JzTabItem("统计", Icons.Outlined.PieChart),
            JzTabItem("设置", Icons.Outlined.Settings),
        )
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 0.dp, bottomStart = 0.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, item ->
                JzTabCell(item, selected == i) { onSelect(i) }
            }
        }
    }
}

@Composable
private fun RowScope.JzTabCell(item: JzTabItem, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // 选中弹簧弹跳（v3.13.0）
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
        ),
        label = "tabScale",
    )
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(width = 52.dp, height = 28.dp)
                .then(
                    if (selected) Modifier.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        CircleShape,
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                item.icon,
                contentDescription = item.label,
                tint = tint,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
        Text(
            item.label,
            fontSize = 11.sp,
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
