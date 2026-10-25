package com.yang.jizhang.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.yang.jizhang.data.AppDatabase
import com.yang.jizhang.ui.fmtMoney
import com.yang.jizhang.util.Periods
import kotlinx.coroutines.runBlocking

/**
 * 桌面小组件（v3.1.0，Jetpack Glance）：本月支出 / 收入 / 结余 + 一键「记一笔」。
 * 刷新时机：系统周期（30 分钟）+ 打开 App + 记账保存后。
 */
class MonthWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val win = Periods.currentWindow(Periods.MONTHLY)
        val txs = AppDatabase.get(context).txDao().getByTimeRangeOnce(win[0], win[1] - 1)
        val expense = txs.filter { !it.isIncome }.sumOf { it.amountCents }
        val income = txs.filter { it.isIncome }.sumOf { it.amountCents }
        val balance = income - expense
        val mainActivity = ComponentName(context, "com.yang.jizhang.ui.MainActivity")
        // 主题联动（v3.8.0）：小组件标题用当前预设主色
        val accent = com.yang.jizhang.ui.ThemePrefs.preset(context).accentDark

        provideContent {
            GlanceTheme {
                Content(expense, income, balance, mainActivity, accent)
            }
        }
    }

    @Composable
    private fun Content(
        expense: Long,
        income: Long,
        balance: Long,
        mainActivity: ComponentName,
        accent: Color,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(12.dp).cornerRadius(16.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text(
                    "蓝鲸记账 · 本月",
                    style = TextStyle(
                        color = ColorProvider(accent),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                FilledButton(
                    "记一笔",
                    actionStartActivity(mainActivity),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        "支出",
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 10.sp),
                    )
                    Text(
                        "¥${fmtMoney(expense)}",
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        "收入",
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 10.sp),
                    )
                    Text(
                        "¥${fmtMoney(income)}",
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        "结余",
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 10.sp),
                    )
                    Text(
                        "¥${fmtMoney(balance)}",
                        style = TextStyle(
                            color = ColorProvider(if (balance >= 0) Color(0xFF42D392) else Color(0xFFF97066)),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}

/** App 内刷新小组件的统一入口（失败静默，不影响主流程） */
object WidgetSync {
    suspend fun refresh(context: Context) {
        try {
            MonthWidget().updateAll(context)
        } catch (_: Exception) {
        }
    }

    fun refreshAsync(context: Context) {
        val app = context.applicationContext
        Thread {
            try {
                runBlocking { refresh(app) }
            } catch (_: Exception) {
            }
        }.start()
    }
}
