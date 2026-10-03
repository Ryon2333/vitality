package com.jiang.vitality.data

data class PrehistoricCreature(
    val id: String,
    val name: String,
    val era: String,
    val group: String,
    val note: String
)

data class FossilDiscovery(
    val creatureId: String,
    val discoveredAt: Long
)

/** A stable, offline catalog. IDs follow list order so exported discoveries remain portable. */
object PaleontologyCatalog {
    private data class Group(val era: String, val kind: String, val names: List<String>)

    private val groups = listOf(
        Group("中生代", "恐龙", listOf(
            "霸王龙", "三角龙", "剑龙", "腕龙", "梁龙", "迷惑龙", "异特龙", "迅猛龙", "恐爪龙", "棘龙",
            "重爪龙", "似鳄龙", "鲨齿龙", "南方巨兽龙", "玛君龙", "食肉牛龙", "阿贝力龙", "角鼻龙", "双脊龙", "腔骨龙",
            "板龙", "始盗龙", "埃雷拉龙", "禽龙", "鸭嘴龙", "副栉龙", "盔龙", "兰伯龙", "慈母龙", "埃德蒙顿龙",
            "甲龙", "包头龙", "结节龙", "肿头龙", "冥河龙", "戟龙", "开角龙", "厚鼻龙", "原角龙", "鹦鹉嘴龙",
            "窃蛋龙", "镰刀龙", "似鸟龙", "似鸡龙", "单爪龙", "阿根廷龙", "泰坦巨龙", "萨尔塔龙", "马门溪龙", "圆顶龙",
            "雷龙", "叉龙", "火山齿龙", "气龙", "中华龙鸟", "尾羽龙", "小盗龙", "近鸟龙", "耀龙", "冠龙"
        )),
        Group("古生代至新生代", "海洋生命", listOf(
            "沧龙", "海王龙", "倾齿龙", "蛇颈龙", "薄片龙", "克柔龙", "上龙", "滑齿龙", "鱼龙", "大眼鱼龙",
            "杯椎鱼龙", "幻龙", "楯齿龙", "奇虾", "房角石", "邓氏鱼", "巨齿鲨", "利兹鱼", "海百合", "菊石"
        )),
        Group("中生代", "天空生命", listOf(
            "风神翼龙", "无齿翼龙", "翼手龙", "喙嘴翼龙", "双型齿翼龙", "古神翼龙", "南翼龙", "帆翼龙", "蛙嘴龙", "森林翼龙",
            "始祖鸟", "孔子鸟"
        )),
        Group("古生代至新生代", "远古兽类", listOf(
            "异齿龙", "丽齿兽", "水龙兽", "犬颌兽", "三尖叉齿兽", "摩根兽", "剑齿虎", "猛犸象", "乳齿象", "披毛犀",
            "洞熊", "巨型短面熊", "恐狼", "大角鹿", "巨犀", "雕齿兽", "大地懒", "袋剑虎", "恐鸟", "渡渡鸟",
            "古马", "始祖象", "恐角兽", "鬣齿兽", "安氏兽", "巴博剑齿虎", "洞狮", "短面袋鼠", "双门齿兽", "袋狮"
        )),
        Group("前寒武纪至古生代", "早期生命", listOf(
            "三叶虫", "怪诞虫", "欧巴宾虫", "微网虫", "皮卡虫", "马尔拉虫", "海蝎", "鲎", "笔石", "腕足动物",
            "叠层石", "鳞木", "封印木", "芦木", "种子蕨", "威瓦西虫", "赫德虾"
        ))
    )

    val all: List<PrehistoricCreature> = groups.flatMap { group ->
        group.names.map { name -> Triple(name, group.era, group.kind) }
    }.mapIndexed { index, (name, era, kind) ->
        PrehistoricCreature(
            id = "paleo-%03d".format(index + 1),
            name = name,
            era = era,
            group = kind,
            note = when (kind) {
                "恐龙" -> "来自陆地生态的一块时间碎片，骨骼记录着中生代的呼吸。"
                "海洋生命" -> "远古海洋曾经比今天更陌生，这次发现来自那片消失的蓝色世界。"
                "天空生命" -> "它曾借助气流穿越史前天空，把轻盈留进漫长的地层。"
                "远古兽类" -> "它生活在不断变化的大陆上，是现代生命谱系的一位远亲。"
                else -> "它来自复杂生命的早期章节，微小痕迹改变了我们理解地球的方式。"
            }
        )
    }

    val byId: Map<String, PrehistoricCreature> = all.associateBy { it.id }
}
