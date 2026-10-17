package com.yang.jizhang.notify

/**
 * 支付通知解析器（借鉴 SmartLedger 的成熟解析方案）。
 *
 * 覆盖渠道：
 *  - 微信（含付款码、聚合 [N条] 摘要、公众号营销过滤）
 *  - 支付宝（付款/消费/退款/收款/转入/红包）
 *  - QQ 钱包
 *  - 云闪付 / 京东 / 淘宝（电商收银台银行卡支付）
 *  - 各银行动账（工行/邮储/建行/中行/农行/招行等）
 *
 * 设计要点：
 *  1. 正则按「渠道 × 收支方向」分组，避免互相污染；
 *  2. 电商/聊天 App 必须有「明确支付确认信号」才放行，禁止「见元就记」；
 *  3. 银行类必须带「动账信号」，防营销/余额提醒误记；
 *  4. 卡号末四位遮罩，防被当成金额；
 *  5. 金额解析区分「明确」（带元/¥/小数）与「模糊」（截断），模糊走用户确认。
 */
object NotificationParser {

    /** 解析结果，金额以「分」存储（避免浮点误差） */
    data class Parsed(
        val amountCents: Long,
        val isIncome: Boolean,
        val merchant: String,
        val source: String,
        /** 去重键：渠道|金额|类型|时间窗 */
        val dedupeKey: String,
        /** 是否需要用户确认（金额模糊 / 疑似营销 / 收支不明） */
        val needsConfirm: Boolean,
        val confirmReason: String? = null,
        /** 通知原文片段，确认页展示 */
        val rawSnippet: String? = null,
    )

    private data class AmountHit(
        val valueCents: Long,
        /** 带「元/￥」或明确小数，较可信 */
        val isClear: Boolean
    )

    /** 金额捕获：整数或最多两位小数；兼容千分位逗号（6,064.76）与末尾「35....」截断 */
    private const val AMOUNT = "([\\d,]+(?:\\.[\\d]{1,2})?)"

    // ═══════════════ 收支方向关键词 ═══════════════
    private val EXPENSE_KEYWORDS = listOf(
        "付款", "支付", "消费", "支出", "转出", "扣款", "已付", "成功付款",
        "购买", "缴费", "还款", "充值", "已扣", "交易支出"
    )
    private val INCOME_KEYWORDS = listOf(
        "退款", "退回", "到账", "收款", "转入", "已收", "存入",
        "收入", "入账", "已到账", "退款成功", "红包", "奖励",
        "工资", "奖金", "利息", "返现", "报销"
    )

    // ═══════════════ 微信 ═══════════════
    private val wechatExpensePatterns = listOf(
        Regex("付款[￥¥]([\\d.]+)"),
        Regex("向(.+?)付款[￥¥]([\\d.]+)"),
        Regex("一笔([\\d.]+)元的支出"),
        Regex("已支付[￥¥]([\\d.]+)"),               // 已支付¥0.01 / [2条]微信支付：已支付¥648.00
        Regex("微信支付[：:]\\s*已支付[￥¥]([\\d.]+)"),
        Regex("支付[￥¥]([\\d.]+)"),                  // 支付¥0.01（须带￥/¥）
        Regex("微信支付.*?已支付[￥¥]([\\d.]+)"),
        Regex("付款码.*?已支付[￥¥]([\\d.]+)"),
        Regex("付款码.*?付款[￥¥]?([\\d.]+)"),
        Regex("已付款[￥¥]([\\d.]+)"),
        Regex("付款成功[￥¥]?([\\d.]+)"),
    )
    private val wechatIncomePatterns = listOf(
        Regex("收款[￥¥]([\\d.]+)"),
        Regex("退款[￥¥]([\\d.]+)"),
        Regex("退款.*?[￥¥]([\\d.]+)"),
        Regex("到账[￥¥]([\\d.]+)"),
        Regex("收入([\\d.]+)元"),
        Regex("红包[￥¥]([\\d.]+)"),
    )

    // ═══════════════ 支付宝 ═══════════════
    private val alipayExpensePatterns = listOf(
        Regex("付款([\\d.]+)元"),
        Regex("成功付款([\\d.]+)"),
        Regex("向(.+?)付款([\\d.]+)元"),
        Regex("消费([\\d.]+)元"),
        Regex("一笔([\\d.]+)元的支出"),
        Regex("支出([\\d.]+)元"),
        Regex("扣款([\\d.]+)元"),
        Regex("([\\d.]+)元的支出"),
        Regex("支付宝.*?[￥¥]([\\d.]+)"),
        Regex("[￥¥]([\\d.]+).*支出"),
    )
    private val alipayIncomePatterns = listOf(
        Regex("退款([\\d.]+)元"),
        Regex("退款.*?([\\d.]+)元"),
        Regex("到账([\\d.]+)元"),
        Regex("收款([\\d.]+)元"),
        Regex("转入([\\d.]+)元"),
        Regex("收入([\\d.]+)元"),
        Regex("一笔([\\d.]+)元的收入"),
        Regex("红包.*?([\\d.]+)元"),
    )

    // ═══════════════ QQ 钱包 ═══════════════
    private val qqExpensePatterns = listOf(
        Regex("支付成功.*?([\\d.]+)元"),
        Regex("支付[￥¥]([\\d.]+)"),
        Regex("付款[￥¥]([\\d.]+)"),
        Regex("消费([\\d.]+)元"),
        Regex("支出([\\d.]+)元"),
        Regex("扣款([\\d.]+)元"),
        Regex("QQ钱包.*?[￥¥]([\\d.]+)"),
    )
    private val qqIncomePatterns = listOf(
        Regex("收款([\\d.]+)元"),
        Regex("到账([\\d.]+)元"),
        Regex("退款([\\d.]+)元"),
        Regex("退款.*?([\\d.]+)元"),
        Regex("红包([\\d.]+)元"),
        Regex("转入([\\d.]+)元"),
    )

    // ═══════════════ 云闪付 ═══════════════
    private val unionpayExpensePatterns = listOf(
        Regex("消费人民币([\\d.]+)元"),
        Regex("付款[￥¥]([\\d.]+)"),
        Regex("支出([\\d.]+)元"),
        Regex("消费([\\d.]+)元"),
        Regex("一笔([\\d.]+)元的支出"),
        Regex("扣款([\\d.]+)元"),
    )
    private val unionpayIncomePatterns = listOf(
        Regex("退款.*?([\\d.]+)元"),
        Regex("到账.*?([\\d.]+)元"),
        Regex("转入.*?([\\d.]+)元"),
        Regex("收入([\\d.]+)元"),
        Regex("收款([\\d.]+)元"),
    )

    // ═══════════════ 电商收银台（京东/淘宝/天猫） ═══════════════
    private val jdExpensePatterns = listOf(
        Regex("使用【.+?】支付([\\d.]+)"),
        Regex("成功支付([\\d.]+)元?"),
        Regex("付款成功.*?([\\d.]+)元"),
        Regex("已支付[￥¥]?([\\d.]+)"),
        Regex("支付成功.*?([\\d.]+)元"),
        Regex("付款([\\d.]+)元"),
        Regex("支付([\\d.]+)元"),
    )
    private val jdIncomePatterns = listOf(
        Regex("退款成功.*?([\\d.]+)元"),
        Regex("退款([\\d.]+)元"),
        Regex("退款.*?([\\d.]+)元"),
    )

    // ═══════════════ 银行通用 ═══════════════
    private val bankExpensePatterns = listOf(
        Regex("消费人民币${AMOUNT}元"),
        Regex("使用【.+?】支付$AMOUNT"),
        Regex("支出\\([^)]{0,120}?\\)\\s*$AMOUNT"),
        Regex("消费\\([^)]{0,120}?\\)\\s*$AMOUNT"),
        Regex("支出\\([^\\d]{0,100}?$AMOUNT"),
        Regex("消费[￥¥]$AMOUNT"),
        Regex("交易金额[￥¥]?$AMOUNT"),
        Regex("转出[￥¥]$AMOUNT"),
        Regex("扣款[￥¥]$AMOUNT"),
        Regex("支出${AMOUNT}元"),
        Regex("消费${AMOUNT}元"),
        Regex("一笔${AMOUNT}元的支出"),
    )
    private val bankIncomePatterns = listOf(
        Regex("收入人民币${AMOUNT}元"),
        Regex("存入人民币${AMOUNT}元"),
        Regex("到账人民币${AMOUNT}元"),
        Regex("收入\\([^)]{0,120}\\)\\s*${AMOUNT}元?"),
        Regex("退款.*?${AMOUNT}元"),
        Regex("转入[￥¥]$AMOUNT"),
        Regex("到账[￥¥]$AMOUNT"),
        Regex("收入${AMOUNT}元"),
        Regex("到账${AMOUNT}元"),
    )

    // ═══════════════ 商户名提取 ═══════════════
    private val merchantPatterns = listOf(
        Regex("商户[：:]\\s*(.+?)(?:\\s|$)"),
        Regex("收款方[：:]\\s*(.+?)(?:\\s|$)"),
        Regex("向(.+?)付款"),
        Regex("在(.+?)消费"),
        Regex("于(.+?)消费"),
        Regex("支出\\((?:消费)?(?:抖音支付-|网银在线-|财付通-|支付宝-|微信支付-|云闪付-)?(.+?)\\)"),
        Regex("收入\\((?:退款)?(?:抖音支付-|网银在线-|财付通-|支付宝-|微信支付-|云闪付-)?(.+?)\\)"),
        Regex("消费\\((?:抖音支付-|网银在线-)?(.+?)\\)"),
        Regex("退款至(.+?)(?:\\s|$)"),
        Regex("来自(.+?)(?:\\s|$)"),
    )

    private val creditProductKeywords = listOf(
        "白条", "花呗", "借呗", "金条", "信用购", "有钱花", "分期乐", "任性付"
    )

    /** 电商类 App：易把「省钱金额」等误当成支付 */
    private fun isShoppingApp(packageName: String): Boolean {
        return packageName.contains("jingdong") || packageName.contains("jd.jr") ||
            packageName.contains("jdlite") || packageName.contains("taobao") ||
            packageName.contains("tmall")
    }

    /** 微信 / 支付宝 / QQ：公众号、企业号、营销推送极多，禁止「见元就记」 */
    private fun isStrictChatPayApp(packageName: String): Boolean {
        return packageName.contains("tencent.mm") || packageName.contains("tencent.mobileqq") ||
            packageName.contains("AlipayGphone")
    }

    /** 银行 App（动账类） */
    private fun isBankApp(packageName: String): Boolean {
        return packageName.contains("icbc") || packageName.contains("psbc") ||
            packageName.contains("chinapost") || packageName.contains("ccb") ||
            packageName.contains("bocmbci") || packageName.contains("bocsoft") ||
            packageName.contains("abchina") || packageName.contains("bankabc") ||
            packageName.contains("cmb.pb") || packageName.contains("cmb.b2c") ||
            packageName.contains("pingan") || packageName.contains("spdb") ||
            packageName.contains("cmbc") || packageName.contains("cebbank") ||
            packageName.contains("bankcomm") || packageName.contains("bocomm") ||
            packageName.contains("cib")
    }

    /**
     * 明确的支付/退款确认信号。
     * 注意：不能仅因出现「微信支付」+「元」就放行（企业号「每天5元干饭」会误伤）。
     */
    fun hasStrongPaymentSignal(text: String): Boolean {
        if (text.contains("使用【") && text.contains("支付")) return true
        if (text.contains("成功支付") || text.contains("支付成功")) return true
        if (text.contains("付款成功") || text.contains("成功付款")) return true
        if (text.contains("已支付") || text.contains("已付款")) return true
        if (Regex("付款[￥¥]").containsMatchIn(text)) return true
        if (Regex("支付[￥¥]").containsMatchIn(text)) return true
        if (Regex("向.+?付款[￥¥]").containsMatchIn(text)) return true
        if (Regex("一笔[\\d.]+元的支出").containsMatchIn(text)) return true
        if (Regex("一笔[\\d.]+元的收入").containsMatchIn(text)) return true
        if (Regex("收款[￥¥]").containsMatchIn(text)) return true
        // 付款码：常与「已支付¥xx」或「付款成功」一起出现
        if (text.contains("付款码") && (
                text.contains("已支付") || text.contains("付款成功") ||
                    text.contains("成功付款") || Regex("[￥¥]\\s*[\\d.]+").containsMatchIn(text)
                )
        ) {
            return true
        }
        // 微信支付：必须带确认动词，不能仅有「元」
        if (text.contains("微信支付") && (
                text.contains("已支付") || text.contains("付款") ||
                    text.contains("收款") || text.contains("退款") ||
                    Regex("支付[￥¥]").containsMatchIn(text)
                )
        ) {
            return true
        }
        if (text.contains("支付宝") && (
                text.contains("付款") || text.contains("成功付款") ||
                    text.contains("的支出") || text.contains("的收入") ||
                    text.contains("退款")
                )
        ) {
            return true
        }
        // 退款入账（排除提现提醒）
        if ((text.contains("退款成功") || text.contains("退款到账") ||
                (text.contains("退款") && (text.contains("元") || text.contains("¥") || text.contains("￥")))) &&
            !text.contains("提现") && !text.contains("尚未处理")
        ) {
            return true
        }
        // 银行动账
        if (text.contains("动账") || text.contains("支出(") || text.contains("收入(")) return true
        if ((text.contains("储蓄卡") || text.contains("借记卡") || text.contains("信用卡")) &&
            (text.contains("支付") || text.contains("消费") || text.contains("扣款"))
        ) {
            return true
        }
        return false
    }

    /** 营销 / 额度 / 物流 / 公众号推送等非真实支付通知 */
    fun isPromotionalOrNonPayment(text: String): Boolean {
        // 银行真实动账优先放行
        if (hasBankLedgerSignal(text)) return false
        if (hasStrongPaymentSignal(text)) return false
        val noise = listOf(
            "省钱金额", "已省回", "省回", "倍会费", "PLUS会员", "会员已省",
            "省钱明细", "点击查看", "相当于",
            "提现提醒", "尚未处理", "将于", "过期", "现金打款",
            "可用额度", "信用额度", "剩余额度", "账户剩余", "剩余可用额度",
            "验证码", "登录验证", "登录成功", "账单日", "账单已出",
            "最低还款", "分期付款", "安全锁", "网银登录", "手机银行登录",
            "专属福利", "限时优惠", "年化利率", "立即申请", "点击申请",
            "贷款额度", "借款额度", "预估可借", "尊享礼遇", "积分兑换",
            "账户余额", "当前余额", "可用余额", "余额变动提醒",
            "还款提醒", "还款日", "即将到期", "逾期提醒",
            "开户成功", "绑卡成功", "签约成功", "开通成功",
            "物流", "配送", "已发货", "待收货", "下单关怀", "签收",
            "优惠券", "领券", "领积分", "积分到账", "领券福利", "福利官",
            "猜一局", "大家都在猜", "周末大家都在",
            "提醒：账户", "签到领", "每日签到",
            "干饭", "请你吃", "今日已上新", "已上新", "外卖券",
            "点击领取", "立即领取", "限时领取", "天天特价",
        )
        if (noise.any { text.contains(it) }) return true
        if (Regex("每天\\d+(\\.\\d+)?元").containsMatchIn(text)) return true
        if (Regex("\\d+(\\.\\d+)?元请你").containsMatchIn(text)) return true
        if (Regex("\\d+(\\.\\d+)?元干饭").containsMatchIn(text)) return true
        return false
    }

    /** 银行 App / 动账类通知的真实账务信号（防营销误记） */
    fun hasBankLedgerSignal(text: String): Boolean {
        if (text.contains("动账")) return true
        if (text.contains("支出(") || text.contains("收入(") || text.contains("消费(")) return true
        if (text.contains("交易金额") || text.contains("支出人民币") || text.contains("收入人民币")) return true
        if (text.contains("消费人民币") || text.contains("扣款人民币") || text.contains("存入人民币")) return true
        if (Regex("尾号\\*{0,4}\\d{0,4}.*(?:支出|收入|消费|扣款)").containsMatchIn(text)) return true
        if (Regex("尾号\\d{4}.*(?:支出|收入|消费|扣款)").containsMatchIn(text)) return true
        if (Regex("(?:支出|收入|消费)\\([^)]*\\)\\s*[\\d]").containsMatchIn(text)) return true
        return false
    }

    /** 是否像「[N条]」聚合摘要（金额可能在同组子通知里） */
    fun looksLikeGroupedSummary(text: String): Boolean {
        return Regex("[\\[［【(（]?\\d+条[\\]］】)）]?").containsMatchIn(text) ||
            Regex("共\\d+条").containsMatchIn(text) ||
            Regex("\\d+条新(消息|通知|提醒)").containsMatchIn(text)
    }

    /** 正文里是否已有可解析的明确支付/动账金额 */
    fun hasPayableAmountSignal(text: String): Boolean {
        if (Regex("已支付\\s*[￥¥]\\s*[\\d.]+").containsMatchIn(text)) return true
        if (Regex("付款\\s*[￥¥]\\s*[\\d.]+").containsMatchIn(text)) return true
        if (Regex("支付\\s*[￥¥]\\s*[\\d.]+").containsMatchIn(text)) return true
        if (Regex("成功付款\\s*[\\d.]+").containsMatchIn(text)) return true
        if (Regex("一笔[\\d.]+元的支出").containsMatchIn(text)) return true
        if (Regex("一笔[\\d.]+元的收入").containsMatchIn(text)) return true
        if (Regex("收款\\s*[￥¥]\\s*[\\d.]+").containsMatchIn(text)) return true
        if (Regex("使用【.+?】支付\\s*[\\d.]+").containsMatchIn(text)) return true
        if (Regex("成功支付\\s*[\\d.]+").containsMatchIn(text)) return true
        if (Regex("支出\\([^)]{0,120}\\)\\s*[\\d,]+").containsMatchIn(text)) return true
        if (Regex("收入\\([^)]{0,120}\\)\\s*[\\d,]+").containsMatchIn(text)) return true
        if (Regex("消费\\([^)]{0,120}\\)\\s*[\\d,]+").containsMatchIn(text)) return true
        if (Regex("(?:支出|消费|收入|扣款|存入)人民币\\s*[\\d,]+").containsMatchIn(text)) return true
        if (text.contains("动账") && Regex("[\\d,]+(?:\\.[\\d]{1,2})?").containsMatchIn(text)) return true
        if (Regex("交易金额\\s*[￥¥]?\\s*[\\d,]+").containsMatchIn(text)) return true
        if (Regex("(?:转出|转入|到账|扣款)\\s*[￥¥]\\s*[\\d,]+").containsMatchIn(text)) return true
        return false
    }

    /** 子通知是否像真实账务（用于从聚合组里挑选） */
    fun looksLikeLedgerChild(text: String): Boolean {
        return hasPayableAmountSignal(text) || hasStrongPaymentSignal(text) || hasBankLedgerSignal(text)
    }

    /** 去掉微信等聚合前缀：[2条] / ［10条］ / 【10条】，全角数字→半角，统一货币符 */
    fun normalizeNotificationText(text: String): String {
        var s = text
            .replace(Regex("[\\[［【]\\d+条[\\]］】]"), "")
            .replace(Regex("[（(]\\d+条[）)]"), "")
            .replace(Regex("(?<!\\d)\\d+条(?=微信支付|支付宝|已支付|付款)"), "")
        // 全角数字 → 半角，避免「已支付¥１６.００」无法识别
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                if (ch in '\uFF10'..'\uFF19') (ch - '\uFF10' + '0'.code).toChar() else ch
            )
        }
        s = sb.toString()
        // 统一常见货币符
        s = s.replace('￥', '¥').replace('＄', '¥')
        return s.trim().replace(Regex("\\s+"), " ")
    }

    /** 是否为白条/花呗等信贷支付（应跳过记账） */
    fun isCreditProductPayment(text: String): Boolean {
        Regex("【([^】]+)】").findAll(text).forEach { m ->
            val tool = m.groupValues[1]
            if (creditProductKeywords.any { tool.contains(it) }) return true
        }
        val hasBankCard = text.contains("储蓄卡") || text.contains("借记卡") ||
            text.contains("信用卡") || text.contains("银行卡") ||
            Regex("尾号\\d{4}").containsMatchIn(text) ||
            Regex("【[^】]*银行[^】]*】").containsMatchIn(text)
        if (hasBankCard) return false
        val creditPayHints = listOf(
            "白条支付", "使用白条", "花呗支付", "使用花呗",
            "借呗", "金条支付", "使用金条", "信用购"
        )
        return creditPayHints.any { text.contains(it) } ||
            (creditProductKeywords.any { text.contains(it) } &&
                (text.contains("支付") || text.contains("付款") || text.contains("消费")))
    }

    /** 遮罩卡号末四位，避免被金额正则误吃 */
    private fun maskCardTailNumbers(text: String): String {
        return text
            .replace(Regex("尾号\\d{4}"), "尾号****")
            .replace(Regex("(?<=储蓄卡|信用卡|借记卡|银行卡)\\s*[(（]\\d{4}[)）]"), "")
            .replace(Regex("【([^】]*?)[(（]\\d{4}[)）]】"), "【$1】")
    }

    /** 从捕获组清洗金额：去掉千分位逗号、以及「35....」尾随点号，转成「分」 */
    private fun parseAmountToken(raw: String): Long? {
        val cleaned = raw.trim()
            .replace(",", "")
            .replace("，", "")
            .trimEnd('.', '…', '。', ' ')
        val m = Regex("^(\\d{1,9})(\\.\\d{1,2})?").find(cleaned) ?: return null
        val yuan = m.value.toDoubleOrNull() ?: return null
        if (yuan <= 0 || yuan >= 10_000_000) return null
        return Math.round(yuan * 100)
    }

    /**
     * 按正则提金额；跳过卡号末四位；优先带小数或带「元」的结果。
     * isClear=false：截断无「元」、仅兜底命中等，应走用户确认。
     */
    private fun extractAmount(
        maskedText: String,
        patterns: List<Regex>,
        rawText: String
    ): AmountHit? {
        var fallback: AmountHit? = null
        for (pattern in patterns) {
            val matches = pattern.findAll(maskedText)
            for (match in matches) {
                val amountStr = match.groupValues.lastOrNull {
                    it.isNotBlank() && it.first().isDigit()
                } ?: continue
                val cents = parseAmountToken(amountStr) ?: continue
                if (isCardTailAmount(cents, rawText, amountStr.takeWhile { it.isDigit() || it == '.' })) {
                    continue
                }
                val matchedWhole = match.value
                val hasYuanOrSymbol = matchedWhole.contains("元") ||
                    matchedWhole.contains("￥") || matchedWhole.contains("¥") ||
                    amountStr.contains('.')
                val truncatedDots = rawText.contains("....") || rawText.contains("…") ||
                    Regex("\\d\\.{2,}").containsMatchIn(rawText)
                if (hasYuanOrSymbol && !truncatedDots) {
                    return AmountHit(cents, isClear = true)
                }
                // 动账截断无「元」：支出(...)35.... —— 可解析但不确定
                if (matchedWhole.contains("支出(") || matchedWhole.contains("收入(") ||
                    matchedWhole.contains("消费(")
                ) {
                    return AmountHit(cents, isClear = false)
                }
                if (hasYuanOrSymbol) {
                    return AmountHit(cents, isClear = !truncatedDots)
                }
                if (fallback == null) fallback = AmountHit(cents, isClear = false)
            }
        }
        return fallback
    }

    /** 四位整数且出现在「尾号xxxx / 卡(xxxx)」上下文 → 卡号而非金额 */
    private fun isCardTailAmount(cents: Long, rawText: String, amountStr: String): Boolean {
        if (amountStr.contains('.')) return false
        if (amountStr.length != 4) return false
        if (cents % 100 != 0L) return false
        return rawText.contains("尾号$amountStr") ||
            Regex("(?:储蓄卡|信用卡|借记卡|银行卡)\\s*[(（]$amountStr[)）]").containsMatchIn(rawText) ||
            Regex("【[^】]*[(（]$amountStr[)）]】").containsMatchIn(rawText)
    }

    /**
     * 判断是否收支方向不明（同时含收入与支出关键词，且无结构线索）。
     * 注意：动账类文案易把「收入(工资)」里的「收入」当支出关键词。
     */
    private fun resolveType(rawText: String): Pair<String, Boolean> {
        val isIncome = INCOME_KEYWORDS.any { rawText.contains(it) }
        val isExpense = EXPENSE_KEYWORDS.any { rawText.contains(it) }

        if (isIncome && !isExpense) return "income" to false
        if (isExpense && !isIncome) return "expense" to false

        if (isIncome && isExpense) {
            return when {
                rawText.contains("收入(") || rawText.contains("网上银行收入") ||
                    rawText.contains("工资") || rawText.contains("存入") ||
                    rawText.contains("退款") || rawText.contains("退回") ||
                    (rawText.contains("到账") && !rawText.contains("提现") &&
                        !rawText.contains("支出(") && !rawText.contains("消费(")) ->
                    "income" to false
                rawText.contains("支出(") || rawText.contains("消费(") ||
                    rawText.contains("扣款") || rawText.contains("转出") ->
                    "expense" to false
                else -> "expense" to (!hasStrongPaymentSignal(rawText) && !hasBankLedgerSignal(rawText))
            }
        }

        // 无关键词：仅动账截断场景可继续
        if (!hasBankLedgerSignal(rawText)) return "expense" to true
        return "expense" to true
    }

    // ═══════════════ 核心解析入口 ═══════════════
    fun parse(title: String, content: String, packageName: String): Parsed? {
        val rawText = normalizeNotificationText("$title $content")

        if (isCreditProductPayment(rawText)) return null
        if (isPromotionalOrNonPayment(rawText)) return null

        // 电商 / 微信 / 支付宝 / QQ：必须有明确支付确认，禁止「见元就记」
        if ((isShoppingApp(packageName) || isStrictChatPayApp(packageName)) &&
            !hasStrongPaymentSignal(rawText)
        ) {
            return null
        }
        // 银行 App：必须有动账类信号
        if (isBankApp(packageName) && !hasBankLedgerSignal(rawText) && !hasStrongPaymentSignal(rawText)) {
            return null
        }

        // 去掉尾号/卡号末四位
        val text = maskCardTailNumbers(rawText)

        val (type, typeAmbiguous) = resolveType(rawText)
        val isIncome = type == "income"
        val patterns = if (isIncome) getIncomePatterns(packageName) else getExpensePatterns(packageName)

        val amountHit = extractAmount(text, patterns, rawText) ?: return null
        val amountCents = amountHit.valueCents

        // 商户名（用原文，卡号遮罩不影响商户）
        var merchant: String? = null
        for (pattern in merchantPatterns) {
            val match = pattern.find(rawText)
            if (match != null && match.groupValues.size > 1) {
                merchant = match.groupValues[1].trim()
                if (merchant.isNotBlank()) break
            }
        }
        if (merchant.isNullOrBlank() && isShoppingApp(packageName)) {
            merchant = when {
                packageName.contains("jingdong") || packageName.contains("jd.") -> "京东"
                packageName.contains("tmall") -> "天猫"
                packageName.contains("taobao") -> "淘宝"
                else -> null
            }
        }
        if (isIncome && merchant == null) {
            merchant = when {
                rawText.contains("退款") -> "退款"
                rawText.contains("红包") -> "红包"
                rawText.contains("工资") -> "工资"
                rawText.contains("利息") -> "利息"
                else -> null
            }
        }

        val source = when {
            packageName.contains("tencent.mm") -> "微信"
            packageName.contains("tencent.mobileqq") -> "QQ"
            packageName.contains("AlipayGphone") -> "支付宝"
            packageName.contains("unionpay") -> "云闪付"
            packageName.contains("jingdong") || packageName.contains("jd.") -> "京东"
            packageName.contains("tmall") || packageName.contains("taobao") -> "淘宝"
            isBankApp(packageName) -> "银行卡"
            else -> "通知"
        }

        // 置信度评估
        val (needsConfirm, reason) = assessConfidence(rawText, amountHit.isClear, typeAmbiguous)

        // 去重键：渠道|金额|收支|30秒窗（配合数据库智能去重）
        val windowKey = System.currentTimeMillis() / 30_000
        val dedupeKey = "$source|$amountCents|$isIncome|$windowKey"

        return Parsed(
            amountCents = amountCents,
            isIncome = isIncome,
            merchant = merchant ?: "",
            source = source,
            dedupeKey = dedupeKey,
            needsConfirm = needsConfirm,
            confirmReason = reason,
            rawSnippet = rawText.take(120),
        )
    }

    /** 置信度：明确收支 → 静默入账；其余 → 需确认 */
    private fun assessConfidence(
        rawText: String,
        amountClear: Boolean,
        typeAmbiguous: Boolean
    ): Pair<Boolean, String?> {
        if (!amountClear) return true to "金额可能被截断或不完整"
        if (rawText.contains("....") || rawText.contains("…") ||
            Regex("\\d\\.{2,}").containsMatchIn(rawText)
        ) {
            return true to "通知内容疑似被截断"
        }
        if (typeAmbiguous) return true to "无法确定是收入还是支出"

        // 券 / 满减 / 福利：一律确认
        val couponNoise = listOf(
            "优惠券", "红包券", "满减", "神券", "外卖券", "抵用券",
            "领券", "已到账一张", "待使用", "点击领取", "限时领取",
            "省钱金额", "已省回"
        )
        if (couponNoise.any { rawText.contains(it) }) return true to "疑似优惠/券类推送"
        if ((rawText.contains("券") || rawText.contains("福利")) && !hasStrongPaymentSignal(rawText)) {
            return true to "疑似营销推送"
        }
        return false to null
    }

    private fun getExpensePatterns(packageName: String): List<Regex> {
        return when {
            packageName.contains("tencent.mm") -> wechatExpensePatterns
            packageName.contains("tencent.mobileqq") -> qqExpensePatterns
            packageName.contains("AlipayGphone") -> alipayExpensePatterns
            packageName.contains("unionpay") -> unionpayExpensePatterns
            packageName.contains("jd.jr") || packageName.contains("jingdong") ||
                packageName.contains("jdlite") || packageName.contains("taobao") ||
                packageName.contains("tmall") -> jdExpensePatterns
            isBankApp(packageName) -> bankExpensePatterns
            else -> bankExpensePatterns
        }
    }

    private fun getIncomePatterns(packageName: String): List<Regex> {
        return when {
            packageName.contains("tencent.mm") -> wechatIncomePatterns
            packageName.contains("tencent.mobileqq") -> qqIncomePatterns
            packageName.contains("AlipayGphone") -> alipayIncomePatterns
            packageName.contains("unionpay") -> unionpayIncomePatterns
            packageName.contains("jd.jr") || packageName.contains("jingdong") ||
                packageName.contains("jdlite") || packageName.contains("taobao") ||
                packageName.contains("tmall") -> jdIncomePatterns
            isBankApp(packageName) -> bankIncomePatterns
            else -> bankIncomePatterns
        }
    }
}
