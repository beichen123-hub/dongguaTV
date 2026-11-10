package com.beichen.dongguatv.data

/**
 * 剧名归一化。
 *
 * 采集站的剧名带一堆营销后缀，同一部剧在不同站点会长这样：
 *   「繁花 国语版」「繁花 沪语版」「繁花[全30集]」「繁花(剧版)」
 * 直接按字符串比较会被当成不同的剧，列表里反复出现同一部。
 * 这里统一：去括号内容 → 去版本词 → 去空白/连接符，得到用于聚合的 key。
 */
object NameUtil {

    private val BRACKET = Regex("[\\[【(（][^\\]】)）]*[\\]】)）]")

    private val VERSION = Regex(
        "(国语版|粤语版|沪语版|英语版|TV版|tv版|未删减版|无删减|完整版|高清版|中字版|" +
                "国语|粤语|沪语|英语|日语|韩语|中字|超清|蓝光|HD|4K|剧版|电影版)"
    )

    private val NOISE = Regex("[\\s·・\\-—_]+")

    fun key(name: String): String {
        if (name.isBlank()) return ""
        var s = BRACKET.replace(name, " ")
        s = VERSION.replace(s, " ")
        s = NOISE.replace(s, "")
        return s.trim().lowercase()
    }

    /** 展示用：把版本后缀去掉，留主标题（列表里更好看） */
    fun display(name: String): String {
        var s = BRACKET.replace(name, " ")
        s = VERSION.replace(s, " ")
        s = Regex("\\s+").replace(s, " ").trim()
        return s.ifBlank { name }
    }
}
