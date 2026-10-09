package com.yang.jizhang

import java.util.Calendar

/** 根据交易描述关键词猜测分类 */
object CategoryRules {

    val RULES = listOf(
        listOf("餐饮", "美食", "外卖", "美团", "饿了么", "肯德基", "麦当劳", "星巴克", "奶茶", "咖啡", "餐厅", "面馆", "小吃", "烧烤", "火锅") to "餐饮美食",
        listOf("打车", "滴滴", "出行", "地铁", "公交", "加油", "停车", "高速", "共享单车", "火车", "机票", "航空", "12306") to "交通出行",
        listOf("淘宝", "天猫", "京东", "拼多多", "拼多多", "购物", "超市", "便利店", "商场", "天猫超市", "盒马", "山姆") to "购物消费",
        listOf("水电", "燃气", "物业", "宽带", "话费", "充值", "移动", "联通", "电信", "电费", "水费") to "生活缴费",
        listOf("房租", "租金", "酒店", "住宿", "民宿") to "居住住房",
        listOf("电影", "游戏", "会员", "视频", "音乐", "腾讯视频", "爱奇艺", "网易云", "哔哩", "Steam", "演出", "KTV") to "娱乐休闲",
        listOf("医院", "药", "门诊", "体检", "挂号") to "医疗健康",
        listOf("工资", "薪", "奖金", "分红", "理财", "利息", "收益") to "收入进账",
        listOf("红包", "转账", "收款") to "红包转账",
        listOf("教育", "学费", "课程", "培训", "书本", "打印") to "学习教育",
    )

    /** 返回命中的分类名，未命中返回「其他」 */
    fun guess(vararg texts: String?): String {
        val joined = texts.filterNotNull().joinToString(" ")
        for ((keywords, category) in RULES) {
            for (kw in keywords) {
                if (kw in joined) return category
            }
        }
        return "其他"
    }

    /** 各分类对应的图标（emoji，简洁直观） */
    fun icon(category: String): String = when (category) {
        "餐饮美食" -> "🍜"
        "交通出行" -> "🚌"
        "购物消费" -> "🛒"
        "生活缴费" -> "💡"
        "居住住房" -> "🏠"
        "娱乐休闲" -> "🎮"
        "医疗健康" -> "💊"
        "收入进账" -> "💰"
        "红包转账" -> "🧧"
        "学习教育" -> "📚"
        else -> "📦"
    }

    val ALL_CATEGORIES = listOf(
        "餐饮美食", "交通出行", "购物消费", "生活缴费", "居住住房",
        "娱乐休闲", "医疗健康", "学习教育", "红包转账", "收入进账", "其他",
    )

    /** 月份区间工具：返回 [startMillis, endMillis) */
    fun monthRange(year: Int, month1based: Int): LongArray {
        val cal = Calendar.getInstance().apply {
            set(year, month1based - 1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return longArrayOf(start, cal.timeInMillis)
    }
}
