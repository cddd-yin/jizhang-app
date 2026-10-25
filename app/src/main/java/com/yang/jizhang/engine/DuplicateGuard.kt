package com.yang.jizhang.engine

import com.yang.jizhang.data.TxDao
import com.yang.jizhang.data.TxEntity

/**
 * v5.0.1 同一笔消费判定（增强版）。
 *
 * 核心原则：
 * 1. **时间基准用通知事件时间差**（eventTime − tx.time），不用处理时刻 wall clock——
 *    Doze/批量推送会把"真实间隔 5 秒"的双报拖到几分钟后才处理，用处理时刻判定必漏；
 * 2. **证据分级**：商户互相包含=强证据（10 分钟长窗，容纳银行补发延迟）；
 *    单边无商户+跨渠道=双报常态（150s）；双方有商户不相关=明确两笔（永不并）。
 */
object DuplicateGuard {
    private const val SAME_PRINT_MS = 15_000L
    private const val CROSS_CHANNEL_MS = 150_000L
    private const val STRONG_EVIDENCE_MS = 10 * 60_000L
    private const val AMOUNT_ONLY_MS = 60_000L

    /**
     * @param eventTime 本条通知的 postTime（事件时间），判定时间差用
     * @param now 处理时刻，仅用于圈定候选记录范围
     */
    suspend fun findDuplicate(
        dao: TxDao,
        bill: BillParser.Bill,
        eventTime: Long,
        now: Long = System.currentTimeMillis(),
    ): TxEntity? {
        // 候选范围取宽松：事件时间往前的强证据长窗 + 处理延迟余量
        val recent = dao.getByTimeRangeOnce(eventTime - STRONG_EVIDENCE_MS, now + 60_000L)
        if (recent.isEmpty()) return null

        // 同指纹（同渠道同额同商户）快速路径：仍用事件时间差
        val samePrint = recent.firstOrNull { tx ->
            timeAbsDiff(eventTime, tx.time) <= CROSS_CHANNEL_MS &&
                tx.source == bill.channel &&
                tx.isIncome == bill.isIncome && tx.amountCents == bill.amountCents &&
                norm(tx.note) == norm(bill.merchant.ifBlank { bill.channel })
        }
        if (samePrint != null) return samePrint

        val merchantBlank = bill.merchant.isBlank()
        return recent.find { tx ->
            if (tx.amountCents != bill.amountCents || tx.isIncome != bill.isIncome) return@find false
            val dt = timeAbsDiff(eventTime, tx.time)
            val txNoMerchant = tx.note.isBlank() || tx.note == tx.source
            val sameChannel = tx.source == bill.channel

            // 判定矩阵（按证据强度有序）：
            // 1. 商户互相包含 → 强证据：10 分钟窗（"拼多多" ⊂ "拼多多XX旗舰店"；银行补发延迟也能并）
            // 2. 双方都有商户且互不包含 → 明确两笔，永不并
            // 3. 单边无商户 + 跨渠道（电商+支付渠道/银行 双报常态）→ 150s
            // 4. 同渠道 + 双方无商户（连续两笔同额，如两趟地铁）→ 15s 短窗
            // 5. 同渠道无关联 → 60s
            val window = when {
                containsEither(bill.merchant, tx.note) -> STRONG_EVIDENCE_MS
                !merchantBlank && !txNoMerchant -> 0L
                !sameChannel -> CROSS_CHANNEL_MS
                sameChannel && merchantBlank && txNoMerchant -> SAME_PRINT_MS
                else -> AMOUNT_ONLY_MS
            }
            window > 0L && dt <= window
        }
    }

    private fun timeAbsDiff(a: Long, b: Long): Long = Math.abs(a - b)

    private fun norm(s: String) = s.trim()

    private fun containsEither(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        return a.contains(b) || b.contains(a)
    }
}
