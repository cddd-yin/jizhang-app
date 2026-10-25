package com.yang.jizhang.util

import android.content.Context
import org.json.JSONObject
import java.util.LinkedHashMap

/**
 * 分类自学习（v3.11.0）：记住「商户/备注 → 分类」的手动修正，越用越准。
 *
 * 学习来源：手动记账与编辑账单时用户选定的分类；
 * 应用点：自动记账入库、CSV 导入、分享草稿解析时优先于静态关键词规则。
 *
 * 存储：SharedPreferences 内 JSON（LinkedHashMap 保持插入序，容量 200，
 * 超限淘汰最旧）；映射是商户精确匹配或商户包含 key（通知里商户常带分店名后缀）。
 */
object CategoryLearner {
    private const val PREFS = "jizhang_prefs"
    private const val KEY = "category_hints"
    private const val MAX_ENTRIES = 200
    private const val MIN_MERCHANT_LEN = 2
    /** 学习值不应是这些泛化词（对任何商户都成立的分类没有学习意义） */
    private val GENERIC_NOTES = setOf("手动", "外部", "导入", "周期", "支付宝", "微信", "QQ", "备注", "其他")

    private fun load(ctx: Context): LinkedHashMap<String, String> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return LinkedHashMap()
        return try {
            val obj = JSONObject(raw)
            LinkedHashMap<String, String>().apply {
                obj.keys().forEach { k -> put(k, obj.optString(k)) }
            }
        } catch (_: Exception) {
            LinkedHashMap()
        }
    }

    private fun save(ctx: Context, map: LinkedHashMap<String, String>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, obj.toString()).apply()
    }

    /** 学习一条映射；商户为空/过短/泛化词/含金额符号时不学 */
    fun learn(context: Context, merchant: String?, category: String) {
        val m = merchant?.trim() ?: return
        if (m.length < MIN_MERCHANT_LEN || m in GENERIC_NOTES) return
        if (m.contains("¥") || m.contains("￥") || Regex("""\d{3,}""").containsMatchIn(m)) return
        val ctx = context.applicationContext
        val map = load(ctx)
        map.remove(m) // 重插到末尾 = 最近使用优先
        map[m] = category
        while (map.size > MAX_ENTRIES) {
            map.remove(map.keys.first())
        }
        save(ctx, map)
    }

    /** 查映射：先精确，再包含（key 被商户文本包含） */
    fun hint(context: Context, merchant: String?): String? {
        val m = merchant?.trim() ?: return null
        if (m.length < MIN_MERCHANT_LEN) return null
        val map = load(context.applicationContext)
        map[m]?.let { return it }
        // 包含匹配取最长 key（更具体优先）
        return map.entries
            .filter { m.contains(it.key) }
            .maxByOrNull { it.key.length }
            ?.value
    }

    fun size(context: Context): Int = load(context.applicationContext).size

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
