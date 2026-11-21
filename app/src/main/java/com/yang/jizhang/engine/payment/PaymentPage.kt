package com.yang.jizhang.engine.payment

import java.security.MessageDigest
import java.text.Normalizer

internal enum class PaymentSource(val packageName: String, val label: String) {
    WECHAT("com.tencent.mm", "微信"),
    ALIPAY("com.eg.android.AlipayGphone", "支付宝");

    companion object {
        fun fromPackage(packageName: CharSequence?): PaymentSource? =
            entries.firstOrNull { it.packageName.contentEquals(packageName) }
    }
}

internal data class DetectedPayment(val source: PaymentSource, val cents: Long, val referenceDigest: String? = null)

/** Only keeps the checks and amount needed to recognize a visible result page. */
internal class PaymentPage(private val source: PaymentSource) {
    private var success = false
    private var completionAction = false
    private var rejected = false
    private val prominentAmounts = mutableSetOf<Long>()
    private val labeledAmounts = mutableSetOf<Long>()
    private val referenceDigests = mutableSetOf<String>()
    private var nextValue: Field? = null

    private enum class Field { PAYMENT_AMOUNT, OTHER_AMOUNT, CURRENCY_AMOUNT, REFERENCE }

    fun observe(label: CharSequence?, actionable: Boolean) {
        if (label.isNullOrBlank() || label.length > 160) return
        val normalized = Normalizer.normalize(label, Normalizer.Form.NFKC)
        normalized.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { observeLine(it, actionable) }
    }

    private fun observeLine(text: String, actionable: Boolean) {
        val field = nextValue
        nextValue = null
        val label = text.trimEnd(':').trimEnd()
        when {
            sensitiveLabels.any(label::contains) || label in rejectedLabels -> rejected = true
            !actionable && label in historyTitles -> rejected = true
            label in successLabels -> success = true
            actionable && label in completionActions -> completionAction = true
            label in paymentFields -> nextValue = Field.PAYMENT_AMOUNT
            label in otherAmountFields -> nextValue = Field.OTHER_AMOUNT
            label in referenceFields -> nextValue = Field.REFERENCE
            label == "¥" || label == "CNY" -> nextValue = field ?: Field.CURRENCY_AMOUNT
            else -> observeValue(text, field)
        }
    }

    private fun observeValue(text: String, field: Field?) {
        val inlineReference = labeledReference.matchEntire(text)?.groupValues?.get(1)
        if (inlineReference != null) {
            referenceDigests += digest(inlineReference)
            return
        }
        if (field == Field.REFERENCE && reference.matches(text)) {
            referenceDigests += digest(text)
            return
        }
        labeledAmount.matchEntire(text)?.let {
            labeledAmounts += cents(it.groupValues[1])
            return
        }
        if (field == Field.OTHER_AMOUNT) return
        val amount = currencyAmount.matchEntire(text)?.groups?.get(1) ?: currencySuffix.matchEntire(text)?.groups?.get(1)
        if (field == Field.PAYMENT_AMOUNT) {
            val value = amount?.value ?: number.matchEntire(text)?.value ?: return
            labeledAmounts += cents(value)
        } else if (amount != null) {
            prominentAmounts += cents(amount.value)
        } else if (field == Field.CURRENCY_AMOUNT) {
            number.matchEntire(text)?.let { prominentAmounts += cents(it.value) }
        }
    }

    fun result(): DetectedPayment? {
        if (!success || !completionAction || rejected || referenceDigests.size > 1) return null
        val chosen = labeledAmounts.ifEmpty { prominentAmounts }.singleOrNull()?.takeIf { it > 0 } ?: return null
        return DetectedPayment(source, chosen, referenceDigests.singleOrNull())
    }

    private fun digest(reference: String): String = MessageDigest.getInstance("SHA-256")
        .digest("${source.name}:$reference".toByteArray(Charsets.UTF_8))
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private companion object {
        val successLabels = setOf("支付成功", "付款成功", "交易成功")
        val completionActions = setOf("完成", "返回商家", "返回商户", "返回小程序")
        val sensitiveLabels = setOf("支付密码", "付款密码", "短信验证码", "银行卡密码", "请输入验证码")
        val rejectedLabels = setOf(
            "支付失败", "付款失败", "待支付", "待付款", "支付处理中", "退款成功", "退款详情",
            "订单列表", "账单列表", "付款码", "收款码",
        )
        val historyTitles = setOf("账单详情", "交易详情", "支付详情")
        val paymentFields = setOf("支付金额", "付款金额", "实际支付", "实付金额", "实付款", "实付")
        val otherAmountFields = setOf("优惠金额", "原价", "优惠", "折扣", "红包抵扣", "余额")
        val referenceFields = setOf("交易单号", "交易号", "支付单号", "微信支付单号", "支付宝交易号")
        val reference = Regex("[A-Za-z0-9-]{16,48}")
        val labeledReference = Regex("(?:${referenceFields.joinToString("|")})(?:\\s*:\\s*|\\s+)(${reference.pattern})")
        val number = Regex("(?:[0-9]{1,8}|[0-9]{1,2}(?:,[0-9]{3}){2}|[0-9]{1,3},[0-9]{3})(?:\\.[0-9]{1,2})?")
        val currencyAmount = Regex("(?:¥|CNY)\\s*(${number.pattern})(?:\\s*元)?")
        val currencySuffix = Regex("(${number.pattern})\\s*元")
        val labeledAmount = Regex("(?:${paymentFields.joinToString("|")})\\s*:?\\s*(?:¥|CNY)?\\s*(${number.pattern})(?:\\s*元)?")

        fun cents(amount: String): Long {
            val parts = amount.replace(",", "").split('.', limit = 2)
            return parts[0].toLong() * 100 + parts.getOrElse(1) { "" }.padEnd(2, '0').toLong()
        }
    }
}
