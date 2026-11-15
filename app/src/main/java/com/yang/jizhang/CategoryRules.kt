package com.yang.jizhang

import java.util.Calendar
import java.util.Locale

/** 根据交易描述关键词猜测分类 */
object CategoryRules {

    private val RULES = listOf(
        // ═══ 餐饮 ═══
        listOf(
            "麦当劳", "肯德基", "KFC", "星巴克", "瑞幸", "喜茶", "奈雪",
            "外卖", "餐饮", "餐厅", "饭店", "食堂",
            "奶茶", "咖啡", "蛋糕", "面包", "烧烤", "火锅", "小吃",
            "必胜客", "汉堡王", "海底捞", "西贝", "呷哺", "真功夫",
            "蜜雪冰城", "茶百道", "古茗", "沪上阿姨", "霸王茶姬", "面馆"
        ) to "餐饮美食",
        // ═══ 交通 ═══
        listOf(
            "滴滴", "高德", "打车", "出租车", "地铁", "公交", "一卡通",
            "加油", "加油站", "中石油", "中石化", "壳牌", "停车",
            "高速", "ETC", "过路费", "火车", "12306", "机票", "高铁", "铁路",
            "哈啰", "青桔", "美团单车",
            "曹操出行", "T3出行", "花小猪", "首汽", "神州", "共享单车",
            "航空", "出行"
        ) to "交通出行",
        // ═══ 购物 ═══
        listOf(
            "淘宝", "天猫", "京东", "拼多多", "苏宁", "国美",
            "唯品会", "得物", "闲鱼", "转转", "亚马逊",
            "良品铺子",
            "商城", "超市", "便利店", "711", "全家", "罗森",
            "优衣库", "ZARA", "H&M", "耐克", "阿迪", "李宁", "安踏",
            "抖音商城", "抖音超市", "快手小店", "小红书商城",
            "华为商城", "小米商城", "Apple Store", "数码", "电器",
            "叮咚买菜", "盒马", "每日优鲜", "永辉", "大润发", "沃尔玛",
            "山姆", "Costco", "开市客", "物美", "华润万家", "联华", "购物",
            "商场"
        ) to "购物消费",
        // ═══ 生活缴费 ═══
        listOf(
            "物业", "物业费", "水电", "电费", "水费", "燃气", "天然气",
            "宽带", "话费", "中国移动", "中国联通", "中国电信", "网费", "流量包",
            "缴费"
        ) to "生活缴费",
        // ═══ 居住 ═══
        listOf(
            "房租", "租金", "房贷",
            "装修", "家具", "家电", "建材", "五金",
            "家政", "保洁", "房屋维修", "管道", "开锁",
            "酒店", "住宿", "民宿"
        ) to "居住住房",
        // ═══ 娱乐 ═══
        listOf(
            "王者荣耀", "和平精英", "原神", "网易游戏", "腾讯游戏",
            "Steam", "游戏", "点券", "钻石", "皮肤",
            "电影", "影院", "猫眼", "淘票票",
            "爱奇艺", "优酷", "腾讯视频", "B站", "哔哩哔哩", "哔哩",
            "网易云", "QQ音乐", "Spotify",
            "KTV", "酒吧", "迪厅", "游乐", "景区", "门票",
            "直播", "打赏", "视频会员", "音乐会员", "演出"
        ) to "娱乐休闲",
        // ═══ 医疗 ═══
        listOf(
            "医院", "诊所", "药店", "药房", "大药房", "老百姓",
            "挂号", "门诊", "住院", "体检", "疫苗",
            "口腔", "牙科", "眼科", "皮肤科",
            "美团买药", "京东健康", "阿里健康", "叮当快药", "购药", "处方药"
        ) to "医疗健康",
        // ═══ 教育 ═══
        listOf(
            "学校", "培训", "教育", "课程", "学费", "辅导",
            "书店", "图书", "当当", "文具",
            "考试", "教材", "网课",
            "得到课程", "知乎盐选", "极客时间", "慕课", "书本", "打印"
        ) to "学习教育",
        // ═══ 收入进账 ═══
        listOf(
            "工资", "薪资", "薪水", "奖金", "分红", "理财", "利息", "收益",
            "报销", "返现", "奖励"
        ) to "收入进账",
        // ═══ 红包转账 ═══
        listOf(
            "红包", "转账", "收款"
        ) to "红包转账",
    )

    private const val OTHER = "其他"
    private val shoppingPlatforms = listOf("淘宝", "天猫", "拼多多", "得物", "闲鱼", "唯品会", "转转", "亚马逊")
    private val multiServicePlatforms = listOf("美团", "大众点评", "饿了么", "淘宝闪购", "京东", "抖音", "快手", "高德", "携程", "飞猪")
    private val serviceRules = listOf(
        listOf("买药", "在线问诊", "处方药", "非处方药", "布洛芬", "感冒灵", "药品订单", "药房", "药店", "京东健康") to "医疗健康",
        listOf("买菜", "美团闪购", "京东商城", "小象超市", "歪马送酒", "生鲜配送", "商超配送", "日用百货", "咖啡机", "咖啡滤纸",
            "手机壳", "游戏手柄", "电饭煲", "洗衣液", "纸巾", "抽纸", "充电宝", "充电器", "洗发水") to "购物消费",
        listOf("酒店预订", "酒店订单", "民宿预订", "酒店住宿", "客房", "保洁上门") to "居住住房",
        listOf("打车订单", "网约车", "单车骑行", "美团单车", "骑行卡", "机票订单", "火车票", "高铁票") to "交通出行",
        listOf("电影票", "演唱会门票", "景区门票", "电影购票") to "娱乐休闲",
        listOf("外卖订单", "餐品", "堂食", "到店餐饮", "美团外卖", "饿了么外卖", "餐饮团购", "外卖") to "餐饮美食",
        listOf("手机充值", "话费充值", "燃气缴费") to "生活缴费",
    ).flatMap { (words, category) -> words.map { Rule(it, category) } }

    private val specificRules = RULES.flatMap { (words, category) ->
        words.filterNot { it in shoppingPlatforms || it in multiServicePlatforms }.map { word ->
            Rule(word.lowercase(Locale.ROOT), category)
        }
    }

    private data class Rule(val word: String, val category: String) {
        private val asciiWord = if (word.all { it in 'a'..'z' || it in '0'..'9' || it == ' ' || it == '&' }) {
            Regex("(?<![a-z0-9])" + Regex.escape(word) + "(?![a-z0-9])")
        } else null

        fun matches(text: String) = asciiWord?.containsMatchIn(text) ?: (word in text)
    }

    /** 平台汇总商户无法代表单笔消费，不参与记忆（含之前已学习的记录）。 */
    fun isPlatformMerchant(merchant: String): Boolean {
        val cleaned = merchant.trim().replace(Regex("[（(](?:官方|平台|商户|收款)[）)]"), "").trim()
        return (shoppingPlatforms + multiServicePlatforms).any { platform ->
            cleaned == platform || (cleaned.startsWith(platform) &&
                cleaned.removePrefix(platform) in setOf("平台", "支付", "收款", "官方", "订单", "订单支付", "官方旗舰店"))
        } || cleaned in setOf("北京三快在线科技有限公司", "北京三快科技有限公司", "上海汉涛信息咨询有限公司")
    }

    fun guess(vararg texts: String?, isIncome: Boolean? = null): String =
        classify(merchant = texts.firstOrNull(), details = texts.drop(1).filterNotNull().joinToString(" "), isIncome = isIncome)

    fun classify(
        merchant: String?,
        details: String?,
        channel: String? = null,
        isIncome: Boolean? = null,
        learned: String? = null,
    ): String {
        val party = merchant.orEmpty().lowercase(Locale.ROOT)
        val order = details.orEmpty().lowercase(Locale.ROOT)
        val combined = "$party $order"
        if (isIncome == true) {
            return learned ?: strongest(specificRules.filter { it.category in setOf("收入进账", "红包转账") }, combined)
                ?: "收入进账"
        }
        val services = serviceRules.filter { it.matches(combined) }
        val eligible = specificRules.filter { isIncome != false || (it.category != "收入进账" && it.word != "收款") }
        // 配送/平台标签较弱。例如“美团闪购·某药房”应识别为买药。
        val decisive = services.filterNot { it.word in setOf("美团闪购", "京东商城", "外卖", "美团外卖", "饿了么外卖", "外卖订单") }
        val noisyMerchant = isPlatformMerchant(party)
        val merchantCategory = if (noisyMerchant) null else strongest(eligible, party)
        val detailsCategory = strongest(eligible, order)
        val serviceCategory = strongest(decisive, combined)
        if (serviceCategory != null) return serviceCategory
        if (decisive.map { it.category }.distinct().size > 1) return merchantCategory ?: OTHER
        val delivery = strongest(services, combined)
        return learned.takeUnless { noisyMerchant }
            ?: merchantCategory ?: detailsCategory ?: delivery
            ?: if ("淘宝闪购" !in combined && shoppingPlatforms.any { it in "$combined ${channel.orEmpty()}" }) {
                "购物消费"
            } else OTHER
    }

    /** 同等级证据指向多个类别时，由最长的具体词组决定；完全平票则留给兜底。 */
    private fun strongest(rules: List<Rule>, text: String): String? {
        val matches = rules.filter { it.matches(text) }
        val length = matches.maxOfOrNull { it.word.length } ?: return null
        return matches.filter { it.word.length == length }.map { it.category }.distinct().singleOrNull()
    }

    /** 各分类对应的图标（emoji，简洁直观；自定义分类走 🏷️） */
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
        "其他" -> "📦"
        else -> "🏷️"
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
