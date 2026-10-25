package com.yang.jizhang.util

import android.content.Context
import org.json.JSONArray

/**
 * 自定义分类（v3.12.0）：在内置 11 类之外增删个人分类（养宠 / 汽车 / 母婴…）。
 * 存储：SharedPreferences 内 JSON 数组；顺序 = 内置在前、自定义按添加序在后。
 * 删除自定义分类不迁移存量账单（记录仍显示原分类名，图标自动走 🏷️）。
 */
object CategoryStore {
    private const val PREFS = "jizhang_prefs"
    private const val KEY = "custom_categories"
    private const val MAX_CUSTOM = 20
    private const val MAX_NAME_LEN = 6

    fun custom(context: Context): List<String> {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            JSONArray(raw).let { arr -> (0 until arr.length()).map { arr.optString(it) } }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun all(context: Context): List<String> =
        com.yang.jizhang.CategoryRules.ALL_CATEGORIES + custom(context)

    fun isBuiltin(name: String): Boolean = name in com.yang.jizhang.CategoryRules.ALL_CATEGORIES

    /** @return null 成功；失败返回原因文案 */
    fun add(context: Context, name: String): String? {
        val n = name.trim()
        if (n.isEmpty() || n.length > MAX_NAME_LEN) return "名称需 1-$MAX_NAME_LEN 个字"
        if (all(context).contains(n)) return "该分类已存在"
        val ctx = context.applicationContext
        val list = custom(ctx).toMutableList()
        if (list.size >= MAX_CUSTOM) return "最多 $MAX_CUSTOM 个自定义分类"
        list.add(n)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, JSONArray(list).toString()).apply()
        return null
    }

    fun remove(context: Context, name: String) {
        val ctx = context.applicationContext
        val list = custom(ctx).toMutableList()
        if (list.remove(name)) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, JSONArray(list).toString()).apply()
        }
    }
}
