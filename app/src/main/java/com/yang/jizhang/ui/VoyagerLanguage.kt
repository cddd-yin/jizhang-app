package com.yang.jizhang.ui

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

internal enum class VoyagerLanguage(val locale: Locale) {
    CHINESE(Locale.SIMPLIFIED_CHINESE),
    ENGLISH(Locale.ENGLISH);

    operator fun invoke(chinese: String, english: String): String =
        if (this == ENGLISH) english else chinese

    fun month(year: Int, month: Int): String = if (this == ENGLISH) {
        "${Month.of(month).getDisplayName(TextStyle.FULL, locale)} $year"
    } else {
        "$year 年 $month 月"
    }

    fun category(name: String): String = if (this == ENGLISH) {
        when (name) {
            "餐饮美食" -> "Food & Dining"
            "交通出行" -> "Transport"
            "购物消费" -> "Shopping"
            "生活缴费" -> "Utilities"
            "居住住房" -> "Housing"
            "娱乐休闲" -> "Entertainment"
            "医疗健康" -> "Health"
            "学习教育" -> "Education"
            "红包转账" -> "Transfers & Gifts"
            "收入进账" -> "Income"
            "其他" -> "Other"
            else -> name
        }
    } else {
        name
    }
}

internal val LocalVoyagerLanguage = staticCompositionLocalOf { VoyagerLanguage.CHINESE }

internal fun VoyagerLanguage.appearance(value: String): String =
    if (this == VoyagerLanguage.ENGLISH) appearanceEnglish[value] ?: value else value

private val appearanceEnglish = mapOf(
    "跟随系统" to "System",
    "浅色" to "Light",
    "深色" to "Dark",
    "默认" to "Default",
    "宋体" to "Song",
    "楷体" to "Kai",
    "玻璃" to "Glass",
    "紧凑" to "Compact",
    "苹果 iOS" to "Apple iOS",
    "纸质账本" to "Paper Ledger",
    "墨黑" to "Ink",
    "随界面风格" to "From theme",
    "动态取色" to "Dynamic color",
    "自定义" to "Custom",
    "天码" to "Sky",
    "深海" to "Deep Sea",
    "薄荷" to "Mint",
    "樱花" to "Blossom",
    "琥珀" to "Amber",
    "最高可用" to "Highest available",
    "月账页、五线谱和档案内页" to "Monthly ledger, sheet music and archival pages",
)
