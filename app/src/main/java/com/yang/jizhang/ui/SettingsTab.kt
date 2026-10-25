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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteSweep
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yang.jizhang.data.Source
import com.yang.jizhang.engine.BillImporter
import com.yang.jizhang.engine.keepalive.ListenerHealth
import kotlinx.coroutines.launch

/**
 * 设置页：自动记账状态与四项权限引导（原权限引导页收编为常驻 Tab）+ 数据导入导出 + 关于。
 */
@Composable
fun SettingsTab(
    contentPad: PaddingValues,
    listenerState: ListenerHealth.DisplayState,
    onExport: () -> Unit,
    onOpenRecurring: () -> Unit,
    themePreset: JzPreset,
    themeMode: JzMode,
    onThemeChange: (JzPreset, JzMode) -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showCategoryManage by remember { mutableStateOf(false) }

    var listenerEnabled by remember { mutableStateOf(ListenerHealth.isEnabledInSettings(context)) }
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
                postNotificationsGranted = isPostNotificationsGranted(context)
                batteryIgnored = isBatteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val n = runCatching { BillImporter.import(context, uri, Source.IMPORT) }.getOrDefault(0)
            android.widget.Toast.makeText(
                context,
                if (n > 0) "导入成功，新增 $n 条" else "导入失败：请选择支付宝/微信导出的账单 CSV",
                android.widget.Toast.LENGTH_LONG,
            ).show()
            if (n > 0) {
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
                    context, "恢复失败：请选择本 App 导出的完整备份 JSON", android.widget.Toast.LENGTH_LONG
                ).show()
                result.first + result.second + result.third == 0 -> android.widget.Toast.makeText(
                    context, "恢复完成：数据均已存在，无新增", android.widget.Toast.LENGTH_LONG
                ).show()
                else -> {
                    android.widget.Toast.makeText(
                        context,
                        "恢复完成：账单 ${result.first} 条 · 预算 ${result.second} 个 · 周期规则 ${result.third} 条",
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
        Text(
            "设置",
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )

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
                    ListenerHealth.DisplayState.CONNECTED -> incomeColor() to "自动记账运行中"
                    ListenerHealth.DisplayState.RECOVERING -> warnColor() to "支付监听重连中"
                    ListenerHealth.DisplayState.NEED_PERMISSION -> expenseColor() to "自动记账未开启"
                }
                Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(10.dp))
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    when (listenerState) {
                        ListenerHealth.DisplayState.CONNECTED -> "支付后自动入账"
                        ListenerHealth.DisplayState.RECOVERING -> "点权限项修复"
                        ListenerHealth.DisplayState.NEED_PERMISSION -> "开启下方权限"
                        else -> ""
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── 权限引导 ──
        SettingsHeader("自动记账权限")
        SettingsCard {
            GuideItem(
                icon = Icons.Outlined.NotificationsActive,
                title = "通知使用权（必须）",
                description = "读取微信 / 支付宝 / QQ 支付通知，自动识别金额并记账",
                granted = listenerEnabled,
            ) { openListenerSettings(context) }
            CardDivider()
            GuideItem(
                icon = Icons.Outlined.Notifications,
                title = "发送通知（推荐）",
                description = "显示「已记一笔」和待确认账单通知（Android 13+ 必须）",
                granted = postNotificationsGranted,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            CardDivider()
            GuideItem(
                icon = Icons.Outlined.PowerSettingsNew,
                title = "自启动",
                description = "允许后台自启动，防止支付监听被杀（国产机建议开启）",
                granted = false,
            ) { openAutoStartSettings(context) }
            CardDivider()
            GuideItem(
                icon = Icons.Outlined.BatteryChargingFull,
                title = "忽略电池优化",
                description = "关闭电池优化，防止监听服务在后台被系统回收",
                granted = batteryIgnored,
            ) { openBatteryOptimization(context) }
        }

        // ── 外观（v3.7.0）──
        SettingsHeader("外观")
        SettingsCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("深浅模式", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    JzMode.entries.forEach { m ->
                        ModeCell(
                            label = m.label,
                            selected = themeMode == m,
                            modifier = Modifier.weight(1f),
                        ) { onThemeChange(themePreset, m) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("配色风格", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                // 2 行 × 3 列主题色卡（当前主色描边高亮，即点即生效）
                JzPreset.entries.chunked(3).forEach { rowPresets ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        rowPresets.forEach { p ->
                            PresetCard(
                                preset = p,
                                selected = themePreset == p,
                                modifier = Modifier.weight(1f),
                            ) { onThemeChange(p, themeMode) }
                        }
                        repeat(3 - rowPresets.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        // ── 周期记账 ──
        SettingsHeader("周期记账")
        SettingsCard {
            ActionItem(Icons.Outlined.Autorenew, "周期规则", "房租、工资、订阅等固定收支按周/月自动入账") {
                onOpenRecurring()
            }
        }

        // ── 数据管理 ──
        SettingsHeader("数据管理")
        SettingsCard {
            ActionItem(Icons.Outlined.Upload, "导入账单 CSV", "支付宝 / 微信官方导出的账单，自动去重") {
                importPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
            }
            CardDivider()
            ActionItem(Icons.Outlined.Download, "导出账单 CSV", "导出本月或全部账单，可分享、可再导入") {
                onExport()
            }
            CardDivider()
            ActionItem(Icons.Outlined.SaveAlt, "完整备份（换机必用）", "账单 + 预算 + 周期规则 一个 JSON 全打包") {
                scope.launch {
                    val uri = com.yang.jizhang.util.BackupHelper.exportFull(context)
                    if (uri != null) {
                        com.yang.jizhang.engine.BillExporter.share(context, uri, "application/json")
                        android.widget.Toast.makeText(context, "完整备份已导出到 下载/Jizhang", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, "没有可备份的数据", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            CardDivider()
            ActionItem(Icons.Outlined.Restore, "恢复备份", "选择完整备份 JSON，幂等合并不产生重复") {
                restorePicker.launch(arrayOf("application/json", "text/json", "text/plain", "*/*"))
            }
            CardDivider()
            val learnerSize = remember { mutableIntStateOf(com.yang.jizhang.util.CategoryLearner.size(context)) }
            ActionItem(
                Icons.Outlined.Psychology,
                "重置智能分类",
                "已学习 ${learnerSize.intValue} 条商户→分类映射，越用越准；重置后回到关键词规则",
            ) {
                com.yang.jizhang.util.CategoryLearner.clear(context)
                learnerSize.intValue = 0
                android.widget.Toast.makeText(context, "智能分类已重置", android.widget.Toast.LENGTH_SHORT).show()
            }
            CardDivider()
            ActionItem(
                Icons.Outlined.Category,
                "管理分类",
                "内置 11 类 + 自定义 ${com.yang.jizhang.util.CategoryStore.custom(context).size} 类，可增删",
            ) {
                showCategoryManage = true
            }
        }

        // ── 关于 ──
        SettingsHeader("关于")
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
                Text("记账 v$versionName", fontSize = 15.sp, modifier = Modifier.weight(1f))
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
                    Text("数据仅存本机", fontSize = 15.sp)
                    Text(
                        "无网络权限，不上传任何数据；导出 CSV 可自行备份",
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
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable {
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

/** 主题预设色卡：渐变圆点 + 名称，选中描边；动态取色为四色环点（Material You） */
@Composable
private fun PresetCard(
    preset: JzPreset,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
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
            .clickable {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                if (preset == JzPreset.MATERIAL_YOU && android.os.Build.VERSION.SDK_INT < 31) {
                    android.widget.Toast.makeText(
                        context, "动态取色需要 Android 12+，将使用深海配色", android.widget.Toast.LENGTH_SHORT,
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
                    if (preset == JzPreset.MATERIAL_YOU) {
                        // Material You：四色环形示意「跟随系统壁纸」
                        androidx.compose.ui.graphics.Brush.sweepGradient(
                            listOf(
                                Color(0xFF6750A4), Color(0xFF00897B),
                                Color(0xFFEF6C00), Color(0xFFC2185B), Color(0xFF6750A4),
                            )
                        )
                    } else {
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
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            preset.label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (preset == JzPreset.MATERIAL_YOU) {
            Text("跟系统", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsHeader(text: String) {
    Text(
        text,
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
            Text("已开启", fontSize = 12.sp, color = incomeColor(), fontWeight = FontWeight.Medium)
        } else {
            Text("去开启 ›", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
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
