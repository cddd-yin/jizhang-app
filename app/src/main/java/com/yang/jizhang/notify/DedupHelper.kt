package com.yang.jizhang.notify

import android.util.Log
import com.yang.jizhang.data.TxDao
import com.yang.jizhang.data.TxEntity

/**
 * 智能去重：避免同一笔交易被多渠道（微信 + 银行）或重复通知记两次。
 *
 * 规则：
 * - 同金额 + 同收支：
 *   - 商户互相包含：120s 窗口内去重
 *   - 同渠道且双方无商户：仅 10s（避免连续两笔同额消费被吞）
 *   - 其他：60s
 */
object DedupHelper {

    private const val TAG = "DedupHelper"
    private const val BASE_WINDOW_MS = 60_000L
    private const val MERCHANT_WINDOW_MS = 120_000L
    private const val SAME_CHANNEL_NO_MERCHANT_MS = 10_000L

    /**
     * @return 已有的重复交易（若有），null 表示不重复
     */
    suspend fun findDuplicate(
        dao: TxDao,
        parsed: NotificationParser.Parsed,
        now: Long = System.currentTimeMillis()
    ): TxEntity? {
        // 先按 dedupeKey 唯一索引兜底（同渠道同金额同30秒窗）
        val keyed = dao.findByDedupeKey(parsed.dedupeKey)
        if (keyed != null) return keyed

        val recentList = dao.getByTimeRangeOnce(now - MERCHANT_WINDOW_MS, now)
        return recentList.find { existing ->
            if (existing.amountCents != parsed.amountCents || existing.isIncome != parsed.isIncome) {
                return@find false
            }
            val timeDiff = now - existing.time
            if (timeDiff < 0) return@find false

            val sameMerchant = merchantMatch(parsed.merchant, existing.note)
            val sameChannel = parsed.source == existing.source
            val bothNoMerchant = parsed.merchant.isBlank() && existing.note.isBlank()

            val window = when {
                sameMerchant -> MERCHANT_WINDOW_MS
                // 同渠道、无商户：短窗，防止 30s 内两笔 ¥15 被误并
                sameChannel && bothNoMerchant -> SAME_CHANNEL_NO_MERCHANT_MS
                else -> BASE_WINDOW_MS
            }
            timeDiff < window
        }
    }

    fun merchantMatch(new: String, old: String): Boolean {
        if (new.isBlank() || old.isBlank()) return false
        return new.contains(old) || old.contains(new)
    }
}
