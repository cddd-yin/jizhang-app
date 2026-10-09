package com.yang.jizhang.notify

/**
 * 解析支付宝 / 微信 / QQ 推送的通知文本，提取金额与收支方向。
 *
 * 各 App 通知样例（节选）：
 *  - 微信：  「微信支付」/「你已成功支付 ¥25.00」
 *  - 支付宝：「支付宝通知」/「付款成功 ¥xx」
 *  - QQ：   「QQ钱包」/「支付成功，金额 ¥xx」
 */
object NotificationParser {

    data class Parsed(
        val amountCents: Long,
        val isIncome: Boolean,
        val merchant: String,
        val dedupeKey: String,
    )

    // 收入方向关键词
    private val INCOME_WORDS = listOf("收款", "到账", "收到红包", "退款", "退回", "入账", "收入")
    // 支出方向关键词
    private val EXPENSE_WORDS = listOf("支付", "付款", "扣款", "消费", "转出", "付钱", "支出")
    // 明显不是交易的通知，直接忽略
    private val IGNORE_WORDS = listOf(
        "验证码", "广告", "推荐", "优惠活动", "好友申请", "群聊", "点赞", "评论",
        "签到", "积分", "运营", "客服", "登录", "下载", "升级", "版本更新",
    )

    private val YUAN_REGEX = Regex("""[¥￥]\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
    private val ELEMENT_REGEX = Regex("""([0-9,]+(?:\.[0-9]{1,2})?)\s*元""")
    private val COMMA = ","

    fun parse(pkg: String, sourceName: String, title: String?, text: String?): Parsed? {
        val full = listOfNotNull(title, text).joinToString(" ").trim()
        if (full.length !in 4..400) return null

        for (w in IGNORE_WORDS) if (w in full) return null

        // 必须包含收支语义词，避免误记聊天消息
        val hasIncome = INCOME_WORDS.any { it in full }
        val hasExpense = EXPENSE_WORDS.any { it in full }
        if (!hasIncome && !hasExpense) return null

        val amountText = YUAN_REGEX.find(full)?.groupValues?.get(1)
            ?: ELEMENT_REGEX.find(full)?.groupValues?.get(1)
            ?: return null
        val amountCents = amountText.replace(COMMA, "").let {
            val parts = it.split(".")
            when (parts.size) {
                2 -> parts[0].toLong() * 100 + parts[1].padEnd(2, '0').take(2).toLong()
                else -> it.toLong() * 100
            }
        }
        if (amountCents <= 0) return null

        // 时间戳取到「分」，配合金额与包名做去重
        val minute = System.currentTimeMillis() / 60_000
        val key = "$sourceName|$amountCents|$minute|${(full).hashCode()}"

        return Parsed(
            amountCents = amountCents,
            isIncome = hasIncome && !hasExpense, // 同时出现时默认按支出记
            merchant = (title ?: "").ifBlank { sourceName },
            dedupeKey = key,
        )
    }
}
