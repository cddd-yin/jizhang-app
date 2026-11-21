package com.yang.jizhang.ui


import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yang.jizhang.engine.BillImporter
import com.yang.jizhang.engine.keepalive.ListenerHealth
import com.yang.jizhang.engine.payment.ScreenPaymentMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 设置页：自动记账状态与四项权限引导（原权限引导页收编为常驻 Tab）+ 数据导入导出 + 关于。
 */
@Composable
internal fun SettingsTab(
    contentPad: PaddingValues,
    listenerState: ListenerHealth.DisplayState,
    pendingCount: Int,
    onOpenReview: () -> Unit,
    onExport: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenRecurring: () -> Unit,
    themePreset: JzPreset,
    themeMode: JzMode,
    themeStyle: JzStyle,
    voyagerEnglish: Boolean,
    onVoyagerLanguageChange: (Boolean) -> Unit,
    themeFont: JzFontChoice,
    onFontChange: (JzFontChoice) -> Unit,
    displaySettings: DisplaySettings,
    onDisplayChange: (DisplaySettings) -> Unit,
    customSeed: Int?,
    onThemeChange: (JzPreset, JzMode) -> Unit,
    onStyleChange: (JzStyle) -> Unit,
    onCustomSeed: (Int) -> Unit,
) {
    val context = LocalContext.current
    val language = LocalVoyagerLanguage.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showCategoryManage by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    var listenerEnabled by remember { mutableStateOf(ListenerHealth.isEnabledInSettings(context)) }
    var screenPermission by remember { mutableStateOf(ScreenPaymentMonitor.hasPermission(context)) }
    var screenEnabled by remember { mutableStateOf(ScreenPaymentMonitor.isEnabled(context)) }
    val screenConnected by ScreenPaymentMonitor.connected.collectAsStateWithLifecycle()
    var postNotificationsGranted by remember { mutableStateOf(isPostNotificationsGranted(context)) }
    var batteryIgnored by remember { mutableStateOf(isBatteryOptimizationIgnored(context)) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> postNotificationsGranted = granted }

    // 从系统设置返回时刷新权限状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled = ListenerHealth.isEnabledInSettings(context)
                screenPermission = ScreenPaymentMonitor.hasPermission(context)
                screenEnabled = ScreenPaymentMonitor.isEnabled(context)
                postNotificationsGranted = isPostNotificationsGranted(context)
                batteryIgnored = isBatteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var importing by remember { mutableStateOf(false) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !importing) scope.launch {
            importing = true
            val result = try {
                BillImporter.import(context, uri)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                BillImporter.Result.Invalid(language("写入账本失败，请重试", "Couldn't save to the ledger. Try again."))
            } finally {
                importing = false
            }
            val message = when (result) {
                is BillImporter.Result.Invalid -> result.message
                is BillImporter.Result.Imported ->
                    language("新增 ${result.added} 条，重复 ${result.duplicates} 条，跳过 ${result.skipped} 条", "Imported ${result.added}, duplicates ${result.duplicates}, skipped ${result.skipped}")
            }
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            if (result is BillImporter.Result.Imported && result.added > 0) {
                com.yang.jizhang.util.BudgetAlertHelper.checkAsync(context)
                com.yang.jizhang.widget.WidgetSync.refreshAsync(context)
            }
        }
    }

    // 完整备份恢复（v3.8.0）
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching { com.yang.jizhang.util.BackupHelper.restoreFull(context, uri) }.getOrNull()
            when {
                result == null -> android.widget.Toast.makeText(
                    context, language("恢复失败：请选择本 App 导出的完整备份 JSON", "Couldn't restore. Select a full JSON backup from this app."), android.widget.Toast.LENGTH_LONG
                ).show()
                result.first + result.second + result.third == 0 -> android.widget.Toast.makeText(
                    context, language("恢复完成：数据均已存在，无新增", "All backup data is already in this ledger."), android.widget.Toast.LENGTH_LONG
                ).show()
                else -> {
                    android.widget.Toast.makeText(
                        context,
                        language("恢复完成：账单 ${result.first} 条 · 预算 ${result.second} 个 · 周期规则 ${result.third} 条", "Restored ${result.first} transactions, ${result.second} budgets and ${result.third} recurring rules"),
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                    com.yang.jizhang.util.BudgetAlertHelper.checkAsync(context)
                    com.yang.jizhang.widget.WidgetSync.refreshAsync(context)
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            // 底栏 + FAB 避让（v3.5.0 修复：此前末尾条目被底部导航遮挡）
            .padding(bottom = contentPad.calculateBottomPadding() + 96.dp),
    ) {
        PageTitle(language("设置", "Settings"), Modifier.padding(horizontal = 20.dp, vertical = 12.dp))

        SettingsHeader(language("支付结果页识别", "Payment screen detection"))
        SettingsCard {
            val accessibilityGranted = screenPermission || screenConnected
            val canToggle = accessibilityGranted || screenEnabled
            GuideItem(
                icon = Icons.Outlined.AccessibilityNew,
                title = language("系统无障碍权限", "Accessibility permission"),
                description = language("在系统设置中开启 Voyager · 支付结果页识别", "Enable Voyager payment screen detection in Android settings"),
                granted = accessibilityGranted,
            ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            CardDivider()
            Row(
                Modifier.fillMaxWidth().toggleable(
                    value = screenEnabled,
                    enabled = canToggle,
                    role = Role.Switch,
                    onValueChange = {
                        ScreenPaymentMonitor.setEnabled(context, it)
                        screenEnabled = it
                    },
                ).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(language("识别支付成功页", "Detect payment success screens"), fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            screenEnabled && screenConnected -> language("运行中，识别结果进入待确认账单", "Running. Detections appear in Pending review.")
                            screenEnabled -> language("等待系统连接无障碍服务", "Waiting for Android to connect the service.")
                            else -> language("仅识别微信、支付宝当前可读的结果页，数据留在本机", "Read visible WeChat and Alipay payment results and keep detections on the device.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = screenEnabled, onCheckedChange = null, enabled = canToggle)
            }
        }

        // ── 自动记账状态 ──
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val (dot, label) = when (listenerState) {
                    ListenerHealth.DisplayState.CONNECTED -> incomeColor() to language("通知记账运行中", "Notification tracking is on")
                    ListenerHealth.DisplayState.RECOVERING -> warnColor() to language("支付监听重连中", "Reconnecting to payment notifications")
                    ListenerHealth.DisplayState.NEED_PERMISSION -> expenseColor() to language("通知记账未开启", "Notification tracking is off")
                }
                Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(10.dp))
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    when (listenerState) {
                        ListenerHealth.DisplayState.CONNECTED -> language("支付后自动入账", "Record payments automatically")
                        ListenerHealth.DisplayState.RECOVERING -> language("点权限项修复", "Check permissions below")
                        ListenerHealth.DisplayState.NEED_PERMISSION -> language("开启下方权限", "Grant notification access below")
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── 权限引导 ──
        SettingsHeader(language("通知记账权限", "Notification tracking permissions"))
        SettingsCard {
            GuideItem(
                icon = Icons.Outlined.NotificationsActive,
                title = language("通知使用权", "Notification access"),
                description = language("读取微信 / 支付宝 / QQ 支付通知，自动识别金额并记账", "Read WeChat, Alipay and QQ payment notifications to record transactions"),
                granted = listenerEnabled,
            ) { openListenerSettings(context) }
            CardDivider()
            GuideItem(
                icon = Icons.Outlined.Notifications,
                title = language("发送通知（推荐）", "Show notifications (recommended)"),
                description = language("显示「已记一笔」和待确认提醒（Android 13+ 需授权）", "Show recorded transactions and review reminders"),
                granted = postNotificationsGranted,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            CardDivider()
            Text(
                language("支付监听由系统在后台运行，连接状态在本页查看。", "The system listens for payments in the background. Check its status here."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            CardDivider()
            GuideItem(
                icon = Icons.Outlined.PowerSettingsNew,
                title = language("自启动", "Auto-start"),
                description = language("允许后台自启动，防止支付监听被杀（国产机建议开启）", "Let the app start in the background to keep tracking payments"),
                granted = false,
            ) { openAutoStartSettings(context) }
            CardDivider()
            GuideItem(
                icon = Icons.Outlined.BatteryChargingFull,
                title = language("忽略电池优化", "Battery optimization"),
                description = language("关闭电池优化，防止监听服务在后台被系统回收", "Exclude the app from battery optimization to keep tracking payments"),
                granted = batteryIgnored,
            ) { openBatteryOptimization(context) }
        }

        // ── 外观（v3.7.0）──
        SettingsHeader(language("外观", "Appearance"))
        SettingsCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                if (themeStyle == JzStyle.VOYAGER) {
                    Text(language("Voyager 界面语言", "Voyager language"), fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(7.dp))
                    Row(Modifier.fillMaxWidth().selectableGroup()) {
                        ModeCell("简体中文", !voyagerEnglish, Modifier.weight(1f)) { onVoyagerLanguageChange(false) }
                        ModeCell("English", voyagerEnglish, Modifier.weight(1f)) { onVoyagerLanguageChange(true) }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Text(language("深浅模式", "Color mode"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().selectableGroup()) {
                    JzMode.entries.forEach { m ->
                        ModeCell(
                            label = language.appearance(m.label),
                            selected = themeMode == m,
                            modifier = Modifier.weight(1f),
                        ) { onThemeChange(themePreset, m) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(language("字体", "Typeface"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(7.dp))
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (pair in JzFontChoice.entries.chunked(2)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (font in pair) {
                                val selected = themeFont == font
                                Surface(
                                    modifier = Modifier.weight(1f).selectable(selected, role = Role.RadioButton) { onFontChange(font) },
                                    shape = MaterialTheme.shapes.small,
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
                                        Text(language.appearance(font.label), fontFamily = font.family ?: themeStyle.typography().titleSmall.fontFamily,
                                            fontSize = 13.sp)
                                        if (font == JzFontChoice.ARCHIVE && themeStyle == JzStyle.VOYAGER) {
                                            VoyagerPvWordmark(Modifier.fillMaxWidth().background(ArchiveInk).padding(horizontal = 3.dp))
                                        } else {
                                            Text(language("本月结余", "Monthly balance"), fontFamily = font.family ?: themeStyle.typography().bodyMedium.fontFamily,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(language("界面风格", "Theme"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    JzStyle.entries.chunked(2).forEach { styles ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (style in styles) {
                                StyleCard(style, themeStyle == style, Modifier.weight(1f)) {
                                    onStyleChange(style)
                                }
                            }
                            if (styles.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    language.appearance(themeStyle.desc),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(language("强调色", "Accent color"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Column(Modifier.selectableGroup()) {
                    JzPreset.entries.chunked(3).forEach { rowPresets ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                            rowPresets.forEach { p ->
                                PresetCard(
                                    preset = p,
                                    selected = themePreset == p,
                                    modifier = Modifier.weight(1f),
                                    seedColor = customSeed?.let { Color(it) },
                                ) {
                                    if (p == JzPreset.CUSTOM) showColorPicker = true
                                    else onThemeChange(p, themeMode)
                                }
                            }
                            repeat(3 - rowPresets.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        SettingsHeader(language("性能与省电", "Performance and battery"))
        SettingsCard {
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(
                        value = displaySettings.powerSaving,
                        role = Role.Switch,
                        onValueChange = { onDisplayChange(displaySettings.copy(powerSaving = it)) },
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(language("省电模式", "Battery saver"), fontWeight = FontWeight.SemiBold)
                    Text(
                        language("优先请求 60 Hz 或更低，关闭磨砂和主要动画。后台自动记账照常运行。", "Use 60 Hz or less and reduce blur and motion. Automatic tracking continues."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = displaySettings.powerSaving, onCheckedChange = null)
            }
            CardDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(language("界面刷新率", "Refresh rate"), fontWeight = FontWeight.SemiBold)
                Text(
                    if (displaySettings.powerSaving) language("省电模式已启用，关闭后恢复所选档位。", "Turn off battery saver to use your selected rate.")
                    else language("实际刷新率由设备和系统决定。", "The device and Android determine the actual refresh rate."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RefreshRate.entries.chunked(3).forEach { rates ->
                        Row(Modifier.fillMaxWidth()) {
                            rates.forEach { rate ->
                                ModeCell(
                                    label = language.appearance(rate.label),
                                    selected = displaySettings.refreshRate == rate,
                                    modifier = Modifier.weight(1f),
                                ) { onDisplayChange(displaySettings.copy(refreshRate = rate)) }
                            }
                            repeat(3 - rates.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        // ── 周期记账 ──
        SettingsHeader(language("周期记账", "Recurring transactions"))
        SettingsCard {
            ActionItem(Icons.Outlined.Autorenew, language("周期规则", "Recurring rules"), language("房租、工资、订阅等固定收支按周/月自动入账", "Automatically record rent, salary and subscriptions weekly or monthly")) {
                onOpenRecurring()
            }
        }

        // ── 数据管理 ──
        SettingsHeader(language("数据管理", "Data management"))
        SettingsCard {
            ActionItem(
                Icons.Outlined.Notifications,
                language("待确认账单", "Review transactions"),
                if (pendingCount > 0) language("$pendingCount 条待处理", "Pending: $pendingCount") else language("暂无待确认账单", "Nothing to review"),
                onOpenReview,
            )
            CardDivider()
            ActionItem(
                Icons.Outlined.Upload,
                if (importing) language("正在导入账单…", "Importing transactions…") else language("导入账单", "Import transactions"),
                language("支持 CSV、TSV、TXT、Excel (.xlsx / .xlsm) 和 ZIP", "CSV, TSV, TXT, Excel (.xlsx / .xlsm) and ZIP"),
            ) {
                if (!importing) importPicker.launch(arrayOf("*/*"))
            }
            CardDivider()
            ActionItem(Icons.Outlined.Archive, language("归档账单", "Archived transactions"), language("查看、恢复或彻底删除已归档账单", "View, restore or permanently delete archived transactions"), onOpenArchive)
            ActionItem(Icons.Outlined.Download, language("导出账单 CSV", "Export CSV"), language("导出本月或全部账单，可分享、可再导入", "Share or reimport transactions from this month or all time")) {
                onExport()
            }
            CardDivider()
            ActionItem(Icons.Outlined.SaveAlt, language("完整备份（换机必用）", "Full backup"), language("账单 + 预算 + 周期规则 一个 JSON 全打包", "Save transactions, budgets and recurring rules in one JSON file")) {
                scope.launch {
                    val uri = com.yang.jizhang.util.BackupHelper.exportFull(context)
                    if (uri != null) {
                        com.yang.jizhang.engine.BillExporter.share(context, uri, "application/json")
                        android.widget.Toast.makeText(context, language("完整备份已导出到 下载/Jizhang", "Full backup saved to Downloads/Jizhang"), android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, language("没有可备份的数据", "Nothing to back up"), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            CardDivider()
            ActionItem(Icons.Outlined.Restore, language("恢复备份", "Restore backup"), language("选择完整备份 JSON，幂等合并不产生重复", "Merge a full JSON backup without duplicates")) {
                restorePicker.launch(arrayOf("application/json", "text/json", "text/plain", "*/*"))
            }
            CardDivider()
            val learnerSize = remember { mutableIntStateOf(com.yang.jizhang.util.CategoryLearner.size(context)) }
            ActionItem(
                Icons.Outlined.Psychology,
                language("重置智能分类", "Reset smart categories"),
                language("已学习 ${learnerSize.intValue} 条商户→分类映射，越用越准；重置后回到关键词规则", "Learned ${learnerSize.intValue} merchant rules. Reset to the built-in rules."),
            ) {
                com.yang.jizhang.util.CategoryLearner.clear(context)
                learnerSize.intValue = 0
                android.widget.Toast.makeText(context, language("智能分类已重置", "Smart categories reset"), android.widget.Toast.LENGTH_SHORT).show()
            }
            CardDivider()
            ActionItem(
                Icons.Outlined.Category,
                language("管理分类", "Manage categories"),
                language("内置 11 类 + 自定义 ${com.yang.jizhang.util.CategoryStore.custom(context).size} 类，可增删", "11 built-in and ${com.yang.jizhang.util.CategoryStore.custom(context).size} custom categories"),
            ) {
                showCategoryManage = true
            }
        }

        // ── 关于 ──
        SettingsHeader(language("关于", "About"))
        SettingsCard {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                // 版本号动态取自 PackageManager（v3.8.0 修复：此前硬编码 v3.0.0 一直显示过时）
                val versionName = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                        .getOrDefault("")
                }
                Text(language("记账 v$versionName", "Ledger v$versionName"), fontSize = 15.sp, modifier = Modifier.weight(1f))
            }
            CardDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(language("数据仅存本机", "On-device storage"), fontSize = 15.sp)
                    Text(
                        language("无网络权限，不上传任何数据；导出 CSV 可自行备份", "Your data stays on this device. Export a CSV to make a backup."),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    if (showCategoryManage) {
        CategoryManageDialog(onClose = { showCategoryManage = false })
    }
    if (showColorPicker) {
        ColorPickerDialog(
            currentSeed = customSeed,
            onPick = { argb ->
                onCustomSeed(argb)
                onThemeChange(JzPreset.CUSTOM, themeMode)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
        )
    }
}

@Composable
private fun ModeCell(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier
            .padding(horizontal = 3.dp)
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .selectable(selected = selected, role = Role.RadioButton) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StyleCard(style: JzStyle, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val language = LocalVoyagerLanguage.current
    val theme = MaterialTheme.colorScheme
    val dark = LocalJzDark.current
    val preview = styleColors(style, dark, style.defaultAccent(dark))
    val frame = MaterialTheme.shapes.medium
    Column(
        modifier
            .clip(frame)
            .background(theme.surface)
            .border(BorderStroke(if (selected) 2.dp else 1.dp,
                if (selected) theme.primary else theme.outlineVariant), frame)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(8.dp),
    ) {
        StyleThumbnail(style, preview)
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                language.appearance(style.label),
                style = remember(style) { style.typography().titleSmall },
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            if (selected) Icon(Icons.Outlined.Check, null, tint = theme.primary, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun StyleThumbnail(style: JzStyle, scheme: androidx.compose.material3.ColorScheme) {
    if (style == JzStyle.VOYAGER) {
        Column(Modifier.fillMaxWidth().clip(style.shapes.medium).background(scheme.background).padding(4.dp)) {
            Box(
                Modifier.fillMaxWidth().height(38.dp).clip(style.shapes.medium).clipToBounds().background(Color(0xFF423267)),
                contentAlignment = Alignment.CenterStart,
            ) {
                VoyagerStarMotif(Modifier.matchParentSize())
                Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth(0.31f).height(3.dp).background(VoyagerGold))
                    Box(Modifier.fillMaxWidth(0.45f).height(5.dp).background(VoyagerIvory))
                }
            }
            Box(Modifier.fillMaxWidth().height(8.dp).background(scheme.surfaceVariant))
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp).height(7.dp)
                .clip(CircleShape).background(scheme.surface))
        }
        return
    }
    val flat = style.usesFlatBalance
    val nav = RoundedCornerShape((style.navRadius / 2).dp)
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .background(scheme.background).padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth(if (style.titleSize > 26) 0.42f else 0.3f)
            .height((style.titleSize / 5).dp).background(scheme.onBackground, CircleShape))
        val fill = if (flat) scheme.surface else scheme.primary
        val detail = if (flat) scheme.primary else scheme.onPrimary
        Box(
            Modifier.fillMaxWidth().height(25.dp).clip(style.shapes.medium).background(fill)
                .then(if (flat) Modifier.border(1.dp, scheme.outline, style.shapes.medium) else Modifier)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth(0.4f).height(3.dp).background(detail.copy(alpha = 0.8f), CircleShape))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (style.navInset > 0) 7.dp else 0.dp)
                .clip(nav).background(scheme.surface).border(1.dp, scheme.outline, nav).padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(if (style.indicator == TabIndicator.PILL) 14.dp else 7.dp, 4.dp)
                .background(scheme.primary, CircleShape))
            repeat(3) {
                Box(Modifier.size(5.dp, 4.dp).background(scheme.onSurfaceVariant.copy(alpha = 0.6f), CircleShape))
            }
        }
    }
}

/** 主题预设色卡：渐变圆点 + 名称，选中描边；动态取色为四色环点（Material You） */
@Composable
private fun PresetCard(
    preset: JzPreset,
    selected: Boolean,
    modifier: Modifier = Modifier,
    seedColor: Color? = null,
    onClick: () -> Unit,
) {
    val language = LocalVoyagerLanguage.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .border(
                width = if (selected) 1.5.dp else 0.5.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp),
            )
            .selectable(selected = selected, role = Role.RadioButton) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                if (preset == JzPreset.MATERIAL_YOU && android.os.Build.VERSION.SDK_INT < 31) {
                    android.widget.Toast.makeText(
                        context, language("动态取色需要 Android 12+，将使用深海配色", "Dynamic colors require Android 12 or later. Deep Sea will be used."), android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
                onClick()
            }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    when {
                        preset == JzPreset.STYLE -> androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(LocalJzStyle.current.defaultAccent(false), LocalJzStyle.current.defaultAccent(true)),
                        )
                        preset == JzPreset.MATERIAL_YOU ->
                            androidx.compose.ui.graphics.Brush.sweepGradient(
                                listOf(
                                    Color(0xFF6750A4), Color(0xFF00897B),
                                    Color(0xFFEF6C00), Color(0xFFC2185B), Color(0xFF6750A4),
                                )
                            )
                        preset == JzPreset.CUSTOM && seedColor != null ->
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(seedColor, deriveAccentsFromSeed(seedColor.toArgb()).first)
                            )
                        else ->
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(preset.accentLight, preset.accentDark)
                            )
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(19.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape).padding(2.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            language.appearance(preset.label),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (preset == JzPreset.MATERIAL_YOU) {
            Text(language("跟系统", "System"), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 48.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline,
    )
}

/** 权限条目（带已开启状态） */
@Composable
private fun GuideItem(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    val language = LocalVoyagerLanguage.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted) {
            Text(language("已开启", "On"), fontSize = 12.sp, color = incomeColor(), fontWeight = FontWeight.Medium)
        } else {
            Text(language("去开启 ›", "Enable ›"), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
    }
}

/** 普通操作条目 */
@Composable
private fun ActionItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
    }
}

// ── 权限检测与跳转（自 v2.2 权限引导页移植，逻辑不变） ──

internal fun isPostNotificationsGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

private fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openAutoStartSettings(context: Context) {
    // 各厂商自启动管理页候选（v3.4.0 扩展覆盖：vivo/华为/荣耀/OPPO/一加/魅族/三星/小米）
    // 依次尝试，任一命中即打开；全不命中退回应用详情页
    val candidates = listOf(
        // vivo / iQOO
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        // 华为 / EMUI
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
        // 荣耀 / MagicOS
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        // OPPO / ColorOS
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        // 一加 / OxygenOS
        ComponentName("com.oneplus.security", "com.oneplus.security.chainstart.ChainStartListActivity"),
        // 魅族 / Flyme
        ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"),
        // 三星 / One UI
        ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        // 小米 / 红米 / MIUI
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    )
    for (cn in candidates) {
        try {
            context.startActivity(
                Intent().apply {
                    component = cn
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            return
        } catch (_: Exception) {
            // 该机型无此组件，尝试下一家
        }
    }
    openAppDetails(context)
}

private fun openBatteryOptimization(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (e: Exception) {
        openAppDetails(context)
    }
}

private fun openAppDetails(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (e: Exception) {
        context.startActivity(
            Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    }
}
