package com.yang.jizhang.engine

import android.util.Log

/**
 * v4.0.0 引擎重写：支付通知解析管线（替代旧 NotificationParser 的 148 条散正则大杂烩）。
 *
 * 架构：通知 → 噪声过滤 → 渠道规格（ChannelSpec）逐个认领 → 规格内
 * 「方向规则 × 金额提取」成对匹配 → 置信度评分。
 * 置信度：符号金额（¥12.5/12.50元）0.95；带金额词的纯数字 0.75；裸数字 0.5。
 * needsReview = 置信度 < 0.7 或金额疑似被截断。
 */
object BillParser {

    data class Bill(
        val amountCents: Long,
        val isIncome: Boolean,
        val merchant: String,
        val channel: String,
        val confidence: Float,
        val needsReview: Boolean,
        val reviewReason: String?,
        val fingerprint: String,
        val rawSnippet: String?,
    )

    /** 方向规则：命中词 + 收支 */
    private data class Direction(val keyword: String, val income: Boolean)

    /** 一条金额线索 */
    private data class AmountHit(val cents: Long, val clear: Boolean, val span: IntRange)

    /** 渠道规格：认领包名 → 输出账单 */
    private class ChannelSpec(
        val name: String,
        val packages: Set<String>,
        val directions: List<Direction>,
    ) {
        fun claim(pkg: String): Boolean = pkg in packages
    }

    // ═══ 渠道定义（方向词从高到低特异度排列） ═══
    private val channels = listOf(
        ChannelSpec(
            "微信", setOf("com.tencent.mm"),
            listOf(
                Direction("已支付", false), Direction("付款", false), Direction("已扣款", false),
                Direction("收款", true), Direction("已收钱", true), Direction("退款", true),
                Direction("到账", true), Direction("红包", true), Direction("入账", true),
            ),
        ),
        ChannelSpec(
            "支付宝", setOf("com.eg.android.AlipayGphone"),
            listOf(
                Direction("消费", false), Direction("付款", false), Direction("支付", false),
                Direction("支出", false), Direction("购买", false),
                Direction("收款", true), Direction("退款", true), Direction("到账", true),
                Direction("收入", true), Direction("余额宝", true), Direction("红包", true),
            ),
        ),
        ChannelSpec(
            "QQ钱包", setOf("com.tencent.mobileqq"),
            listOf(
                Direction("支付", false), Direction("付款", false),
                Direction("收款", true), Direction("退款", true),
            ),
        ),
        ChannelSpec(
            "云闪付", setOf("com.unionpay"),
            listOf(
                Direction("消费", false), Direction("支付", false),
                Direction("退款", true), Direction("返还", true), Direction("入账", true),
            ),
        ),
    )

    /** 银行/其他 App 的动账通知走通用规格（尾号+支出/收入关键词） */
    private val genericChannel = ChannelSpec(
        "银行", emptySet(),
        listOf(
            Direction("支出", false), Direction("消费", false), Direction("转出", false),
            Direction("扣款", false), Direction("缴费", false),
            Direction("收入", true), Direction("入账", true), Direction("转入", true),
            Direction("到账", true), Direction("退款", true),
        ),
    )

    // ═══ 噪声过滤（营销/验证码/信贷，命中即弃） ═══
    private val noiseKeywords = listOf(
        "验证码", "校验码", "动态码", "口令",
        "优惠券", "红包封面", "领红包", "抽奖", "中奖", "积分", "会员日", "活动", "立减", "满减", "折扣",
        "花呗", "白条", "借呗", "备用金", "分期", "还款提醒", "账单提醒", "出账", "最低还款",
        "客服", "公告", "快递", "取件码",
    )
    private val noiseRegexes = listOf(
        Regex("""验证码\s*是?\s*\d{4,8}"""),
        Regex("""\d{4}[-/年]\d{1,2}[-/月]\d{1,2}日?账单"""),
    )

    private val symbolAmount = Regex("""[￥¥]\s*([\d,]+(?:\.\d{1,2})?)""")
    private val yuanAmount = Regex("""([\d,]+(?:\.\d{1,2})?)\s*元""")
    private val keywordAmount = Regex(
        """(?:金额|实付|实收|交易|消费|支付|付款|收款|到账|退款)[:：]?\s*[￥¥]?\s*([\d,]+(?:\.\d{1,2})?)"""
    )

    /**
     * 解析入口。
     * @param pkg 通知来源包名
     * @param title 标题
     * @param text 正文（已含聚合子文本拼接）
     */
    fun parse(pkg: String, title: String, text: String): Bill? {
        val full = "$title $text".replace('\n', ' ').trim()
        if (full.isEmpty()) return null
        if (noiseKeywords.any { full.contains(it) } || noiseRegexes.any { it.containsMatchIn(full) }) {
            Log.d("BillParser", "noise dropped: $title")
            return null
        }

        val channel = channels.firstOrNull { it.claim(pkg) }
            ?: if (hasBankLedgerSignal(full)) genericChannel else return null

        // 方向：找最高特异度（先出现）的方向词
        var dir: Direction? = null
        for (d in channel.directions) {
            if (full.contains(d.keyword)) { dir = d; break }
        }
        if (dir == null) return null

        // 金额：符号 > 元后缀 > 金额词，取首个有效值
        val hit = extractAmount(full) ?: return null
        if (hit.cents <= 0) return null

        // 置信度
        var confidence = when {
            hit.clear -> 0.95f
            else -> 0.75f
        }
        var reason: String? = null
        if (!hit.clear && hit.span.last >= full.length - 1) {
            confidence = 0.6f
            reason = "金额疑似被截断"
        }
        val truncated = full.length >= 200
        if (truncated) confidence -= 0.1f

        val merchant = extractMerchant(full)

        return Bill(
            amountCents = hit.cents,
            isIncome = dir.income,
            merchant = merchant,
            channel = channel.name,
            confidence = confidence,
            needsReview = confidence < 0.7f,
            reviewReason = if (confidence < 0.7f) (reason ?: "识别置信度低") else null,
            fingerprint = "${channel.name}|${dir.income}|${hit.cents}|$merchant",
            rawSnippet = full.take(60),
        )
    }

    /** 银行动账信号：尾号 xx 的账户 + 动词，才让通用规格认领 */
    private fun hasBankLedgerSignal(text: String): Boolean =
        text.contains("尾号") && (text.contains("支出") || text.contains("收入") ||
            text.contains("消费") || text.contains("到账"))

    /** 三级金额提取 */
    private fun extractAmount(text: String): AmountHit? {
        symbolAmount.find(text)?.let {
            val c = toCents(it.groupValues[1]) ?: return@let
            return AmountHit(c, true, it.range)
        }
        keywordAmount.find(text)?.let {
            val c = toCents(it.groupValues[1]) ?: return@let
            return AmountHit(c, false, it.range)
        }
        yuanAmount.find(text)?.let {
            val c = toCents(it.groupValues[1]) ?: return@let
            return AmountHit(c, false, it.range)
        }
        return null
    }

    private fun toCents(raw: String): Long? {
        val d = raw.replace(",", "").toDoubleOrNull() ?: return null
        return Math.round(d * 100)
    }

    /** 商户提取：向|给|在 之后的名词，或「xx商户」格式 */
    private fun extractMerchant(text: String): String {
        Regex("""(?:向|给|在|与)\s*([\u4e00-\u9fa5A-Za-z0-9·]{2,12}?)\s*(?:支付|付款|转账|消费)""")
            .find(text)?.let { return it.groupValues[1] }
        Regex("""([\u4e00-\u9fa5A-Za-z0-9·]{2,12})(?:商户|店铺|商店)""")
            .find(text)?.let { return it.groupValues[1] }
        return ""
    }

    /** 供监听器预筛：是否值得进入完整解析（省 CPU） */
    fun mightBeBill(pkg: String, title: String, text: String): Boolean {
        if (noiseKeywords.any { title.contains(it) }) return false
        val known = channels.any { it.claim(pkg) }
        if (known) return true
        val full = "$title $text"
        return hasBankLedgerSignal(full)
    }
}
