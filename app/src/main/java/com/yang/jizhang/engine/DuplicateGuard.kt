package com.yang.jizhang.engine

import com.yang.jizhang.data.TxDao
import com.yang.jizhang.data.TxEntity

/**
 * v4.0.0 引擎重写：去重守卫（替代旧 DedupHelper 的三层 if-else）。
 *
 * 指纹制：账单指纹 = 渠道|方向|金额|商户。
 * - 同指纹 15s 内 → 必为重复（同通知重发/聚合重投）
 * - 不同指纹但同金额同方向：
 *   - 商户互相包含（同一交易的微信+银行双通知）→ 150s 窗
 *   - 双方无商户（note==source 视为无） → 15s 短窗（防连续同额误并）
 *   - 其余 → 60s
 */
object DuplicateGuard {
    private const val SAME_PRINT_MS = 15_000L
    private const val CROSS_CHANNEL_MS = 150_000L
    private const val AMOUNT_ONLY_MS = 60_000L

    suspend fun findDuplicate(dao: TxDao, bill: BillParser.Bill, now: Long = System.currentTimeMillis()): TxEntity? {
        // 幂等键（入库层唯一索引）快速路径
        val recent = dao.getByTimeRangeOnce(now - CROSS_CHANNEL_MS, now + 60_000L)
        if (recent.isEmpty()) return null

        val print = bill.fingerprint
        val samePrint = recent.firstOrNull { tx ->
            txTimeNear(tx.time, now, SAME_PRINT_MS) && tx.source == bill.channel &&
                tx.isIncome == bill.isIncome && tx.amountCents == bill.amountCents &&
                norm(tx.note) == norm(bill.merchant.ifBlank { bill.channel })
        }
        if (samePrint != null) return samePrint

        val merchantBlank = bill.merchant.isBlank()
        return recent.find { tx ->
            if (tx.amountCents != bill.amountCents || tx.isIncome != bill.isIncome) return@find false
            val dt = now - tx.time
            if (dt < 0 || dt > CROSS_CHANNEL_MS) return@find false
            val txNoMerchant = tx.note.isBlank() || tx.note == tx.source
            val sameChannel = tx.source == bill.channel
            // 窗口矩阵（v4.5.1 修跨渠道双报）：
            // - 商户互相包含 → 同一交易双报（如"拼多多" ⊂ "拼多多XX店"）→ 150s
            // - 跨渠道（电商+微信/支付宝/银行 同报一笔）→ 一律 150s，无商户也放行
            //   （电商通知与扣款通知可能间隔 >15s，此前双方无商户只给 15s 会漏成两笔）
            // - 同渠道双方无商户 → 15s 短窗（防连续两笔同额消费被吞，如两趟地铁）
            // - 同渠道无商户关联 → 60s
            val window = when {
                containsEither(bill.merchant, tx.note) -> CROSS_CHANNEL_MS
                !sameChannel -> CROSS_CHANNEL_MS
                sameChannel && merchantBlank && txNoMerchant -> SAME_PRINT_MS
                else -> AMOUNT_ONLY_MS
            }
            dt <= window
        }
    }

    private fun txTimeNear(t: Long, now: Long, window: Long) = t in (now - window)..(now + window)

    private fun norm(s: String) = s.trim()

    private fun containsEither(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        return a.contains(b) || b.contains(a)
    }
}
