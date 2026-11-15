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
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
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
import com.yang.jizhang.data.PendingBill
import com.yang.jizhang.engine.AutoBookEngine
import com.yang.jizhang.engine.keepalive.ChannelIds
import com.yang.jizhang.engine.keepalive.GuardianService
import com.yang.jizhang.engine.keepalive.ListenerHealth
import com.yang.jizhang.engine.BillExporter
import kotlinx.coroutines.CancellationException
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
    val db = remember { (context.applicationContext as JizhangApp).db }
    val dao = db.txDao()
    val pendingDao = db.pendingBillDao()
    val pendingFlow = remember(pendingDao) { pendingDao.watchAll() }
    val pending by pendingFlow.collectAsStateWithLifecycle(initialValue = null)
    var reviewOpen by rememberSaveable { mutableStateOf(false) }
    var reviewId by rememberSaveable { mutableStateOf<Long?>(null) }
    var reviewBusy by remember { mutableStateOf(false) }
    var reviewFeedback by remember { mutableStateOf<String?>(null) }
    var reviewError by remember { mutableStateOf<String?>(null) }
    val openReview = {
        reviewFeedback = null
        reviewError = null
        reviewId = null
        reviewOpen = true
    }

    // ── 主题（v3.7.0：5 套预设 × 3 档深浅，设置页即点即生效并落盘） ──
    var themePreset by remember { mutableStateOf(ThemePrefs.preset(context)) }
    var themeMode by remember { mutableStateOf(ThemePrefs.mode(context)) }
    var themeStyle by remember { mutableStateOf(ThemePrefs.style(context)) }
    var customSeed by remember { mutableStateOf(ThemePrefs.customSeed(context)) }

    var displaySettings by remember { mutableStateOf(DisplayPrefs.read(context)) }
    val powerSaving = displaySettings.powerSaving
    val useBlur = !powerSaving && themeStyle.blurRadius > 0
    LaunchedEffect(displaySettings) {
        (context as? MainActivity)?.applyDisplaySettings(displaySettings)
    }

    // ── 全局状态 ──
    val today = remember { Calendar.getInstance() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var year by rememberSaveable { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var month by rememberSaveable { mutableIntStateOf(today.get(Calendar.MONTH) + 1) }
    var editing by remember { mutableStateOf<TxEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showBudgets by remember { mutableStateOf(false) }
    var showArchive by rememberSaveable { mutableStateOf(false) }
    var showRecurring by remember { mutableStateOf(false) }
    var shareDraft by remember { mutableStateOf<ShareDraftItem?>(null) }
    var editorKey by remember { mutableIntStateOf(0) }

    val range = remember(year, month) { CategoryRules.monthRange(year, month) }
    val txs by dao.watchRange(range[0], range[1] - 1)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val activeTxs = remember(txs) { txs.filter { it.archivedAt == null } }

    // 主题应用：模式换算 + 全树包裹（弹窗/弹层一并生效）
    val darkTheme = when (themeMode) {
        JzMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        JzMode.LIGHT -> false
        JzMode.DARK -> true
    }
    // v5.1.1：强制深/浅色时状态栏图标色跟 App 主题（修浅底浅图标不可见）
    androidx.compose.runtime.DisposableEffect(darkTheme) {
        (context as? MainActivity)?.applyStatusBarStyle(darkTheme)
        onDispose { }
    }
    JizhangTheme(
        darkTheme = darkTheme, preset = themePreset, style = themeStyle,
        customSeed = customSeed, powerSaving = powerSaving,
    ) {

    // ── 交互基础（v3.3.0）──
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    // 系统返回键：优先关闭弹层（记账页/预算管理/周期记账），而不是退出 App
    androidx.activity.compose.BackHandler(
        enabled = adding || editing != null || showBudgets || showRecurring || showArchive
    ) {
        when {
            adding || editing != null -> { adding = false; editing = null; shareDraft = null }
            showBudgets -> showBudgets = false
            showRecurring -> showRecurring = false
            showArchive -> showArchive = false
        }
    }
    val archiveWithUndo: (TxEntity) -> Unit = { tx ->
        scope.launch {
            val stamp = System.currentTimeMillis()
            try {
                if (dao.archive(tx.id, stamp) == 0) return@launch
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                snackbarHostState.showSnackbar("归档失败，请重试")
                return@launch
            }
            val result = snackbarHostState.showSnackbar(
                message = "账单已归档",
                actionLabel = "撤销",
                duration = androidx.compose.material3.SnackbarDuration.Long,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                try {
                    dao.restoreArchived(tx.id, stamp)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    snackbarHostState.showSnackbar("恢复失败，请在归档页重试")
                }
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
                    showArchive = false
                    editing = null
                    shareDraft = null
                    adding = true
                    editorKey++ // 已打开的记账页强制重建，磁贴重复触发也生效
                }
                UiBus.sharePending?.let { draft ->
                    UiBus.sharePending = null
                    showArchive = false
                    editing = null
                    shareDraft = draft
                    adding = true
                    editorKey++ // 记账页已开时又收到分享 → 丢弃旧输入，换上新草稿
                }
                val reviewingBill = reviewId?.let { id -> pending?.any { it.id == id } != false } == true
                if (!reviewBusy && !reviewingBill && !adding && editing == null && !showExport && !showBudgets && !showRecurring && !showArchive) {
                    UiBus.pendingBillRequest?.let { id ->
                        UiBus.pendingBillRequest = null
                        openReview()
                        reviewId = id
                    }
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
    val listenerState = remember { mutableStateOf(ListenerHealth.displayState(context)) }
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                listenerState.value = ListenerHealth.displayState(context)
                delay(3_000)
            }
        }
    }

    // 液态玻璃（v5.0.0，iOS 27 Liquid Glass）：内容作模糊源，底栏作玻璃层
    val hazeState = remember { dev.chrisbanes.haze.HazeState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { adding = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = when (themeStyle) {
                    JzStyle.GLASS, JzStyle.COMPACT, JzStyle.IOS -> CircleShape
                    JzStyle.VOYAGER -> MaterialTheme.shapes.extraLarge
                    else -> MaterialTheme.shapes.large
                },
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "记一笔", modifier = Modifier.size(26.dp))
            }
        },
        bottomBar = { JzBottomBar(tab, hazeState, themeStyle) { tab = it } },
    ) { pad ->
        val source = if (useBlur) Modifier.hazeSource(hazeState) else Modifier
        Box(Modifier.fillMaxSize().then(source)) {
        androidx.compose.animation.AnimatedContent(
            targetState = tab,
            transitionSpec = {
                if (powerSaving) {
                    androidx.compose.animation.EnterTransition.None.togetherWith(androidx.compose.animation.ExitTransition.None)
                        .using(null)
                } else if (themeStyle == JzStyle.VOYAGER) {
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200))
                        .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)))
                        .using(null)
                } else (
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
                    pendingCount = pending.orEmpty().size,
                    onOpenReview = openReview,
                    onEdit = { editing = it },
                    onGoRecords = { tab = 1 },
                    onOpenBudgets = { showBudgets = true },
                    onArchive = archiveWithUndo,
                )
                1 -> RecordsTab(
                    txs = activeTxs,
                    contentPad = pad,
                    year = year,
                    month = month,
                    onMonthChange = { y, m -> year = y; month = m },
                    onItemClick = { editing = it },
                    onArchive = archiveWithUndo,
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
                    pendingCount = pending.orEmpty().size,
                    onOpenReview = openReview,
                    onExport = { showExport = true },
                    onOpenArchive = { showArchive = true },
                    onOpenRecurring = { showRecurring = true },
                    themePreset = themePreset,
                    themeMode = themeMode,
                    themeStyle = themeStyle,
                    displaySettings = displaySettings,
                    onDisplayChange = { settings ->
                        displaySettings = settings
                        DisplayPrefs.save(context, settings)
                    },
                    customSeed = customSeed,
                    onThemeChange = { p, m ->
                        themePreset = p
                        themeMode = m
                        ThemePrefs.save(context, p, m)
                        runCatching { com.yang.jizhang.widget.WidgetSync.refreshAsync(context) }
                    },
                    onStyleChange = { s ->
                        themeStyle = s
                        ThemePrefs.saveStyle(context, s)
                        if (themePreset == JzPreset.STYLE) com.yang.jizhang.widget.WidgetSync.refreshAsync(context)
                    },
                    onCustomSeed = { argb ->
                        customSeed = argb
                        ThemePrefs.saveCustomSeed(context, argb)
                        runCatching { com.yang.jizhang.widget.WidgetSync.refreshAsync(context) }
                    },
                )
            }
        }
        }
    }

    if (reviewOpen && !adding && editing == null && !showExport && !showBudgets && !showRecurring && !showArchive) {
        val bills = pending
        val selected = bills?.firstOrNull { it.id == reviewId }
        fun submitReview(bill: PendingBill, update: suspend () -> String) {
            if (reviewBusy) return
            reviewBusy = true
            reviewError = null
            scope.launch {
                try {
                    reviewFeedback = update()
                    reviewId = null
                    runCatching { ChannelIds.cancelReview(context, bill.id) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    reviewError = "操作未完成，请重试"
                } finally {
                    reviewBusy = false
                }
            }
        }

        if (selected != null) {
            PendingConfirmDialog(
                pending = selected,
                busy = reviewBusy,
                error = reviewError,
                onClose = { reviewError = null; reviewId = null },
                onConfirm = { amount, income ->
                    submitReview(selected) {
                        if (AutoBookEngine.confirm(context, selected.id, amount, income)) "已入账"
                        else "这条账单已处理"
                    }
                },
                onIgnore = {
                    submitReview(selected) {
                        if (pendingDao.markHandled(selected.id, System.currentTimeMillis()) > 0) "已忽略这条账单"
                        else "这条账单已处理"
                    }
                },
            )
        } else if (bills != null) {
            PendingBillsDialog(
                bills = bills,
                feedback = reviewFeedback ?: if (reviewId != null && !reviewBusy) "这条账单已处理" else null,
                onSelect = { if (!reviewBusy) { reviewFeedback = null; reviewId = it } },
                onClose = { if (!reviewBusy) { reviewOpen = false; reviewId = null } },
            )
        }
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
                onArchive = {
                    val tx = editing
                    editing = null
                    if (tx != null) archiveWithUndo(tx)
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
                    val uri = BillExporter.export(context, range[0], range[1] - 1)
                    handleExportResult(context, uri)
                }
            },
            onExportAll = {
                showExport = false
                scope.launch {
                    val uri = BillExporter.exportAll(context)
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

    if (showArchive) {
        ArchivedBillsScreen(dao, onClose = { showArchive = false })
    }

    } // JizhangTheme
}

internal fun handleExportResult(context: Context, uri: Uri?) {
    if (uri != null) {
        BillExporter.share(context, uri)
    } else {
        Toast.makeText(context, "没有可导出的账单记录", Toast.LENGTH_LONG).show()
    }
}

/** 供重连横幅/设置页共用：强制重连监听 */
internal fun forceReconnect(context: Context) {
    if (!ListenerHealth.isEnabledInSettings(context)) return
    if (!ListenerHealth.forceReconnect(context)) ListenerHealth.requestRebind(context)
    GuardianService.start(context)
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
private fun JzBottomBar(
    selected: Int,
    hazeState: dev.chrisbanes.haze.HazeState,
    style: JzStyle = JzStyle.GLASS,
    onSelect: (Int) -> Unit,
) {
    val items = remember {
        listOf(
            JzTabItem("账本", Icons.Outlined.Home),
            JzTabItem("明细", Icons.AutoMirrored.Outlined.ReceiptLong),
            JzTabItem("统计", Icons.Outlined.PieChart),
            JzTabItem("设置", Icons.Outlined.Settings),
        )
    }
    val scheme = MaterialTheme.colorScheme
    val floating = style.navInset > 0
    val blur = !LocalPowerSaving.current && style.blurRadius > 0
    val fontScale = LocalDensity.current.fontScale
    val navHeight = style.navHeight.dp + (fontScale - 1f).coerceAtLeast(0f).times(22).dp
    val shape = when {
        floating -> RoundedCornerShape(style.navRadius.dp)
        else -> RoundedCornerShape(topStart = style.navRadius.dp, topEnd = style.navRadius.dp)
    }
    val frame = if (floating) {
        Modifier.navigationBarsPadding().padding(horizontal = style.navInset.dp, vertical = 8.dp)
    } else Modifier
    Surface(
        color = if (blur) scheme.surface.copy(alpha = 0.78f) else scheme.surface,
        shape = shape,
        border = if (floating || style.usesFlatBalance)
            BorderStroke(1.dp, scheme.outlineVariant) else null,
        shadowElevation = if (floating) 5.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().then(frame),
    ) {
        val effect = if (blur) Modifier.hazeEffect(
            hazeState,
            style = dev.chrisbanes.haze.HazeStyle(
                backgroundColor = scheme.surface,
                tints = emptyList(),
                blurRadius = style.blurRadius.dp,
            ),
        ) else Modifier
        Row(
            Modifier
                .fillMaxWidth()
                .then(effect)
                .then(if (floating) Modifier else Modifier.navigationBarsPadding())
                .height(navHeight)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, item ->
                JzTabCell(item, style, selected == i) { onSelect(i) }
            }
        }
    }
}

@Composable
private fun RowScope.JzTabCell(item: JzTabItem, style: JzStyle, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // 选中弹簧弹跳（v3.13.0）
    val scale = if (LocalPowerSaving.current || style == JzStyle.VOYAGER) 1f else androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
        ),
        label = "tabScale",
    ).value
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (style.indicator == TabIndicator.VOYAGER) {
            Box(
                Modifier.size(6.dp).graphicsLayer { rotationZ = 45f }
                    .background(if (selected) tint else androidx.compose.ui.graphics.Color.Transparent),
            )
        }
        Box(
            Modifier
                .size(width = 52.dp, height = 28.dp)
                .then(
                    if (selected && (style.indicator == TabIndicator.PILL || style.indicator == TabIndicator.VOYAGER)) Modifier.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        CircleShape,
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                item.icon,
                contentDescription = null,
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
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = if (selected) MaterialTheme.colorScheme.onSurface else tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (style.indicator == TabIndicator.UNDERLINE) {
            Box(Modifier.padding(top = 3.dp).size(24.dp, 2.dp)
                .background(if (selected) tint else androidx.compose.ui.graphics.Color.Transparent))
        }
    }
}
