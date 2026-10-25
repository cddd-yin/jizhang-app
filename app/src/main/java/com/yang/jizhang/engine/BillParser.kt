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

    /** 一条金额线索 */
    private data class AmountHit(val cents: Long, val clear: Boolean, val span: IntRange)

    // ═══ 渠道定义（v4.0.1：方向判定改为收入词优先 + 冲突仲裁，见 detectDirection） ═══
    private class ChannelSpec(
        val name: String,
        val packages: Set<String>,
        val incomeWords: List<String>,
        val expenseWords: List<String>,
    ) {
        fun claim(pkg: String): Boolean = pkg in packages
    }

    private val channels = listOf(
        ChannelSpec(
            "微信", setOf("com.tencent.mm"),
            incomeWords = listOf("已收钱", "收款", "退款", "到账", "红包", "入账", "向你付款"),
            expenseWords = listOf("已支付", "付款", "已扣款", "支付"),
        ),
        ChannelSpec(
            "支付宝", setOf("com.eg.android.AlipayGphone"),
            incomeWords = listOf("收款", "退款", "到账", "收入", "余额宝", "红包", "向你付款"),
            expenseWords = listOf("消费", "付款", "支付", "支出", "购买"),
        ),
        ChannelSpec(
            "QQ钱包", setOf("com.tencent.mobileqq"),
            incomeWords = listOf("收款", "退款", "向你付款"),
            expenseWords = listOf("支付", "付款"),
        ),
        ChannelSpec(
            "云闪付", setOf("com.unionpay"),
            incomeWords = listOf("退款", "返还", "入账", "到账"),
            expenseWords = listOf("消费", "支付"),
        ),
        // 电商渠道（v4.5.0）：支付成功推送自动入账，营销/物流噪声在噪声表拦截
        ChannelSpec(
            "淘宝", setOf("com.taobao.taobao"),
            incomeWords = listOf("退款", "退回"),
            expenseWords = listOf("付款成功", "支付成功", "已付款", "成功付款"),
        ),
        ChannelSpec(
            "京东", setOf("com.jingdong.app.mall", "com.jd.jrapp"),
            incomeWords = listOf("退款", "退回"),
            expenseWords = listOf("支付成功", "付款成功", "实付", "已付款"),
        ),
        ChannelSpec(
            "拼多多", setOf("com.xunmeng.pinduoduo"),
            incomeWords = listOf("退款", "退回"),
            expenseWords = listOf("支付成功", "付款成功", "已付款", "免密支付"),
        ),
        ChannelSpec(
            "得物", setOf("com.shizhuang.duapp"),
            incomeWords = listOf("退款", "退回"),
            expenseWords = listOf("支付成功", "付款成功", "已付款"),
        ),
    )

    /** 银行/其他 App 的动账通知走通用规格（尾号+支出/收入关键词） */
    private val genericChannel = ChannelSpec(
        "银行卡", emptySet(),
        incomeWords = listOf("收入", "入账", "转入", "到账", "退款"),
        expenseWords = listOf("支出", "消费", "转出", "扣款", "缴费"),
    )

    /**
     * 方向判定（v4.0.1，参考同类项目冲突仲裁）：
     * 双向命中时"转账给X"是支出、"转账收款"是收入；仅收入命中→收入；
     * 仅支出命中→支出；都不命中→null。
     * 收入词先查：中文通知"退款/交易退款"常与"支付/交易"同现，先查收入词才不会判反。
     */
    private fun detectDirection(spec: ChannelSpec, text: String): Boolean? {
        val hasIncome = spec.incomeWords.any { text.contains(it) }
        val hasExpense = spec.expenseWords.any { text.contains(it) }
        return when {
            hasIncome && hasExpense ->
                if (text.contains("转账给") || text.contains("转账支付")) false else true
            hasIncome -> true
            hasExpense -> false
            else -> null
        }
    }

    // ═══ 噪声过滤（营销/验证码/信贷/未成交/物流，命中即弃；电商营销词为 v4.5.0 补充） ═══
    private val noiseKeywords = listOf(
        "验证码", "校验码", "动态码", "口令",
        "待支付", "待付款", "未支付", "自动取消", "超时未支付",
        "小荷包", "亲情卡", "亲密付",
        "优惠券", "红包封面", "领红包", "抽奖", "中奖", "积分", "会员日", "活动", "立减", "满减", "折扣",
        "花呗", "白条", "借呗", "备用金", "分期", "还款提醒", "账单提醒", "出账", "最低还款",
        "客服", "公告", "快递", "取件码", "登录提醒", "版本更新",
        // 电商营销/物流噪声（v4.5.0）
        "已发货", "物流", "发货提醒", "降价", "秒杀", "预售", "购物车", "拼团成功", "开团",
        "催付", "爆款", "直播福利", "签到", "种草", "求拼单", "好友助力",
    )
    private val noiseRegexes = listOf(
        Regex("""验证码\s*是?\s*\d{4,8}"""),
        Regex("""\d{4}[-/年]\d{1,2}[-/月]\d{1,2}日?账单"""),
    )

    /** 聚合通知前缀 [N条] 清理（v4.0.1 吸收同类项目，防误读条数为金额） */
    private val aggregatePrefix = Regex("""\[\s*\d+\s*条\s*]""")

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
        // 聚合前缀 [N条] 先清掉，防条数混入金额
        val full = aggregatePrefix.replace("$title $text".replace('\n', ' '), " ").trim()
        if (full.isEmpty()) return null
        if (noiseKeywords.any { full.contains(it) } || noiseRegexes.any { it.containsMatchIn(full) }) {
            Log.d("BillParser", "noise dropped: $title")
            return null
        }

        val channel = channels.firstOrNull { it.claim(pkg) }
            ?: if (hasBankLedgerSignal(full)) genericChannel else return null

        val isIncome = detectDirection(channel, full) ?: return null

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

        val merchant = extractMerchant(full, isIncome)

        return Bill(
            amountCents = hit.cents,
            isIncome = isIncome,
            merchant = merchant,
            channel = channel.name,
            confidence = confidence,
            needsReview = confidence < 0.7f,
            reviewReason = if (confidence < 0.7f) (reason ?: "识别置信度低") else null,
            fingerprint = "${channel.name}|$isIncome|${hit.cents}|$merchant",
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
        // v4.0.1：百万上限防误读（积分/条数等大数混入金额位）
        if (d >= 1_000_000.0) return null
        return Math.round(d * 100)
    }

    /** 商户提取（v4.0.1 补"XX向你付款/商户名称:"两组模式，参考同类项目） */
    private fun extractMerchant(text: String, isIncome: Boolean): String {
        val regexes = listOf(
            Regex("""(?:向|给|在|与)\s*([\u4e00-\u9fa5A-Za-z0-9·]{2,12}?)\s*(?:支付|付款|转账|消费)"""),
            Regex("""([\u4e00-\u9fa5A-Za-z0-9·]{2,12})(?:向你付款|已收款|收款到账)"""),
            Regex("""商户名称[:：]\s*([\u4e00-\u9fa5A-Za-z0-9·]{2,20})"""),
            Regex("""([\u4e00-\u9fa5A-Za-z0-9·]{2,12})(?:商户|店铺|商店)"""),
        )
        for (r in regexes) {
            r.find(text)?.let {
                val c = it.groupValues[1].trim()
                if (c.isNotBlank() && !c.contains("金额") && !c.contains("元")) return c
            }
        }
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
