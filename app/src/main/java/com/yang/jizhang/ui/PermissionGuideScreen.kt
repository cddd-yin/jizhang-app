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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yang.jizhang.notify.keepalive.ListenerStatus

/**
 * 权限引导页：通知使用权 / 通知权限(Android 13+) / 自启动 / 电池优化。
 * 借鉴 SmartLedger 的权限引导方案，按 iOS 风格重排。
 *
 * 核心目标：让「花完钱自动记账」在国产 ROM 上也能稳定跑，
 * 一次性把通知使用权、通知权限、自启动、电池优化四项引导到位。
 */
@Composable
fun PermissionGuideScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var listenerEnabled by remember { mutableStateOf(ListenerStatus.isEnabledInSettings(context)) }
    var postNotificationsGranted by remember { mutableStateOf(isPostNotificationsGranted(context)) }
    var batteryIgnored by remember { mutableStateOf(isBatteryOptimizationIgnored(context)) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> postNotificationsGranted = granted }

    // 从系统设置返回时刷新状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled = ListenerStatus.isEnabledInSettings(context)
                postNotificationsGranted = isPostNotificationsGranted(context)
                batteryIgnored = isBatteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // 顶部导航：返回 + 标题
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Outlined.ArrowBackIosNew,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "自动记账设置",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(48.dp))
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "开启以下权限后，每次支付完成会自动记入账单，全程无需手动操作。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GuideItem(
                icon = Icons.Outlined.NotificationsActive,
                title = "通知使用权（必须）",
                description = "读取微信 / 支付宝 / QQ 支付通知，自动识别金额并记账",
                granted = listenerEnabled,
                onClick = { openNotificationListenerSettings(context) },
            )
            GuideItem(
                icon = Icons.Outlined.Notifications,
                title = "发送通知（推荐）",
                description = "显示「已记一笔」和待确认账单通知（Android 13+ 必须）",
                granted = postNotificationsGranted,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            GuideItem(
                icon = Icons.Outlined.PowerSettingsNew,
                title = "自启动",
                description = "允许后台自启动，防止支付监听被杀（国产机建议开启）",
                granted = false,
                onClick = { openAutoStartSettings(context) },
            )
            GuideItem(
                icon = Icons.Outlined.BatteryChargingFull,
                title = "忽略电池优化",
                description = "关闭电池优化，防止监听服务在后台被系统回收",
                granted = batteryIgnored,
                onClick = { openBatteryOptimization(context) },
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    if (listenerEnabled) {
                        onClose()
                    } else {
                        openNotificationListenerSettings(context)
                    }
                },
                enabled = listenerEnabled,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    if (listenerEnabled) "完成，开始自动记账" else "去开启通知使用权",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
            TextButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("暂不设置，先手动记账", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 单个权限条目卡片 */
@Composable
private fun GuideItem(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = iosCard(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickableNoRipple(onClick)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (granted) incomeColor().copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    )
                    .size(38.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (granted) incomeColor()
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (granted) {
                Text(
                    "已开启",
                    fontSize = 12.sp,
                    color = incomeColor(),
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 无涟漪 clickable（与 iOS 风格一致） */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = onClick,
            )
    )

internal fun isPostNotificationsGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

private fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openNotificationListenerSettings(context: Context) {
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

private fun openAutoStartSettings(context: Context) {
    // vivo / iQOO
    try {
        context.startActivity(
            Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        return
    } catch (_: Exception) {
    }
    // 小米 / 红米
    try {
        context.startActivity(
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        return
    } catch (_: Exception) {
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
