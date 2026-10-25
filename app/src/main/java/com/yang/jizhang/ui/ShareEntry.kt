package com.yang.jizhang.ui

import com.yang.jizhang.CategoryRules

/**
 * 分享/划词记账入口（v3.1.0）：
 * 任意 App 选中文本「在记账中记账」（PROCESS_TEXT）或分享文本（ACTION_SEND）
 * → 解析金额与收支 → 预填记账页草稿。
 */

/** 预填草稿 */
data class ShareDraftItem(
    val amountCents: Long,
    val isIncome: Boolean,
    val category: String,
    val note: String,
)

/** MainActivity → AppRoot 的轻量总线（进程内，无跨进程需求） */
object UiBus {
    @Volatile var sharePending: ShareDraftItem? = null
    @Volatile var openEditorRequest: Boolean = false
}

private val PATTERN_SYMBOL = Regex("[￥¥]\\s*([\\d,]+(?:\\.\\d{1,2})?)")
private val PATTERN_YUAN = Regex("([\\d,]+(?:\\.\\d{1,2})?)\\s*元")
private val PATTERN_KEYWORD = Regex("(?:实付|支付|付款|消费|金额|收款|到账|退款)[\\s:：]*[￥¥]?\\s*([\\d,]+(?:\\.\\d{1,2})?)")
private val PATTERN_BARE = Regex("(\\d+(?:\\.\\d{1,2})?)")
private val INCOME_HINTS = listOf("收入", "收款", "到账", "退款", "红包", "报销", "工资", "入账")

/** 从分享文本中提取金额；提不出正数金额返回 null。context 用于查分类自学习映射。 */
fun parseShareText(context: android.content.Context?, raw: String): ShareDraftItem? {
    val text = raw.trim().replace('\n', ' ')
    if (text.isEmpty()) return null
    val amountStr = listOf(PATTERN_SYMBOL, PATTERN_YUAN, PATTERN_KEYWORD)
        .firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
        ?.replace(",", "")
        ?: run {
            // 短文本（用户手选的金额）才允许裸数字兜底，避免把日期/单号当金额
            if (text.length <= 30) PATTERN_BARE.find(text)?.groupValues?.get(1) else null
        }
        ?: return null
    val cents = amountStr.toDoubleOrNull()?.let { Math.round(it * 100) } ?: 0L
    if (cents <= 0) return null

    val isIncome = INCOME_HINTS.any { text.contains(it) }
    var category = (context?.let { com.yang.jizhang.util.CategoryLearner.hint(it, text) })
        ?: CategoryRules.guess(text)
    if (isIncome && category == "餐饮美食") category = "收入进账"
    return ShareDraftItem(
        amountCents = cents,
        isIncome = isIncome,
        category = category,
        note = text.take(24),
    )
}
