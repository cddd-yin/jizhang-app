package com.yang.jizhang.engine

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * v4.0.0 引擎重写：待确认账单队列（替代旧 PendingConfirmStore，接口对齐便于 UI 平移）。
 * 内存队列，上限 30 条先进先出淘汰；App 被杀则丢弃（待确认的都是低置信度，宁可漏不误记）。
 */
object UncertainBillQueue {
    private val seq = AtomicLong(1)
    private val map = ConcurrentHashMap<Long, Pending>()

    data class Pending(
        val id: Long,
        val amountCents: Long,
        val isIncome: Boolean,
        val merchant: String,
        val source: String,
        val dedupeKey: String,
        val transactionTime: Long,
        val reason: String?,
        val rawSnippet: String?,
    )

    fun put(bill: BillParser.Bill, transactionTime: Long): Long {
        val id = seq.getAndIncrement()
        map[id] = Pending(
            id = id,
            amountCents = bill.amountCents,
            isIncome = bill.isIncome,
            merchant = bill.merchant,
            source = bill.channel,
            dedupeKey = "REVIEW|$id",
            transactionTime = transactionTime,
            reason = bill.reviewReason,
            rawSnippet = bill.rawSnippet,
        )
        trim()
        return id
    }

    fun all(): List<Pending> = map.values.sortedBy { it.id }

    fun remove(id: Long) {
        map.remove(id)
    }

    private fun trim() {
        if (map.size > 30) {
            map.keys.sorted().take(map.size - 30).forEach { map.remove(it) }
        }
    }
}
