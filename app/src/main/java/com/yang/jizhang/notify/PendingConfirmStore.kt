package com.yang.jizhang.notify

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 待确认账单暂存（进程内）。确认前不写数据库，避免误记后还要删。
 * 用户打开 App 后在明细页弹「待确认」卡片处理。
 */
object PendingConfirmStore {

    data class Pending(
        val id: Long,
        val amountCents: Long,
        val isIncome: Boolean,
        val merchant: String,
        val source: String,
        val dedupeKey: String,
        val transactionTime: Long,
        val reason: String?,
        val rawSnippet: String?
    )

    private val seq = AtomicLong(1)
    private val map = ConcurrentHashMap<Long, Pending>()

    fun put(
        parsed: NotificationParser.Parsed,
        transactionTime: Long
    ): Long {
        val id = seq.getAndIncrement()
        map[id] = Pending(
            id = id,
            amountCents = parsed.amountCents,
            isIncome = parsed.isIncome,
            merchant = parsed.merchant,
            source = parsed.source,
            dedupeKey = parsed.dedupeKey,
            transactionTime = transactionTime,
            reason = parsed.confirmReason,
            rawSnippet = parsed.rawSnippet,
        )
        // 防止堆积：最多保留 30 条
        if (map.size > 30) {
            map.keys.sorted().take(map.size - 30).forEach { map.remove(it) }
        }
        return id
    }

    fun get(id: Long): Pending? = map[id]

    fun remove(id: Long) {
        map.remove(id)
    }

    fun all(): List<Pending> = map.values.sortedBy { it.id }
}
