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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // ── 全局状态 ──
    val today = remember { Calendar.getInstance() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var year by rememberSaveable { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var month by rememberSaveable { mutableIntStateOf(today.get(Calendar.MONTH) + 1) }
    var editing by remember { mutableStateOf<TxEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }

    val range = remember(year, month) { CategoryRules.monthRange(year, month) }
    val txs by dao.watchRange(range[0], range[1] - 1)
        .collectAsStateWithLifecycle(initialValue = emptyList())

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

    // 监听状态轮询
    val listenerState = remember { mutableStateOf(ListenerStatus.displayState(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            listenerState.value = ListenerStatus.displayState(context)
            delay(3_000)
        }
    }

    // 待确认账单轮询
    var pending by remember { mutableStateOf<List<PendingConfirmStore.Pending>>(PendingConfirmStore.all()) }
    LaunchedEffect(Unit) {
        while (true) {
            pending = PendingConfirmStore.all()
            delay(1_000)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
        when (tab) {
            0 -> HomeTab(
                dao = dao,
                contentPad = pad,
                listenerState = listenerState.value,
                onEdit = { editing = it },
                onGoRecords = { tab = 1 },
            )
            1 -> RecordsTab(
                txs = txs,
                contentPad = pad,
                year = year,
                month = month,
                onMonthChange = { y, m -> year = y; month = m },
                onItemClick = { editing = it },
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
            )
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
                }
                PendingConfirmStore.remove(p.id)
            },
            onIgnore = { PendingConfirmStore.remove(p.id) },
        )
    }

    // 记一笔 / 编辑（全屏计算器，一个入口两用）
    if (adding || editing != null) {
        TxEditor(
            tx = editing,
            onClose = { adding = false; editing = null },
            onSave = { amount, income, category, note ->
                val tx = editing
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
                }
                adding = false
                editing = null
            },
            onDelete = {
                val tx = editing
                if (tx != null) scope.launch { dao.delete(tx) }
                editing = null
            },
        )
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
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
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
            Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(
            item.label,
            fontSize = 11.sp,
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
