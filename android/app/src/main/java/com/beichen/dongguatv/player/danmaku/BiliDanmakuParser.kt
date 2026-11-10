package com.beichen.dongguatv.player.danmaku

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * 解析 B 站标准弹幕 XML：
 *   <i><d p="12.34,1,25,16777215,1751533608,0,abc,123">文字</d>...</i>
 *
 * p 字段顺序（B 站标准，共 8 段）：
 *   [0] time      出现时间（秒，浮点）
 *   [1] mode      1/2/3=滚动  4=底部  5=顶部
 *   [2] fontSize  字号（B 站标准值 25）
 *   [3] color     十进制 RGB（0-16777215，无 alpha）
 *   [4] timestamp 发送时间戳
 *   [5] pool      弹幕池
 *   [6] uidHash
 *   [7] rowID
 *
 * 注意：曾把 [2] 当 color、[3] 当 fontSize，导致字号变成 5672703px 直接爆屏。
 * 弹幕池里常见 4 万条以上，解析后按时间均匀抽稀，避免渲染压力。
 */
object BiliDanmakuParser {

    /** 单集最多保留的弹幕数（超量均匀抽稀） */
    private const val MAX_ITEMS = 6000

    /** B 站标准字号基准值，对应 16sp */
    private const val BASE_FONT_SIZE = 25f
    private const val DEFAULT_FONT_SP = 16f

    fun parse(xml: String): List<DanmakuItem> {
        val list = ArrayList<DanmakuItem>(1024)
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "d") {
                    val p = parser.getAttributeValue(null, "p")
                    val text = parser.nextText()
                    if (p != null && text.isNotBlank()) {
                        parseItem(p, text)?.let(list::add)
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            return emptyList()
        }
        val sorted = list.sortedBy { it.timeSec }
        if (sorted.size <= MAX_ITEMS) return sorted
        val step = sorted.size / MAX_ITEMS + 1
        return sorted.filterIndexed { i, _ -> i % step == 0 }
    }

    private fun parseItem(p: String, text: String): DanmakuItem? {
        val parts = p.split(",")
        if (parts.size < 4) return null
        return try {
            val timeSec = parts[0].toFloatOrNull() ?: return null
            val mode = parts[1].toIntOrNull() ?: 1
            val rawFontSize = parts[2].toFloatOrNull() ?: BASE_FONT_SIZE
            val rgb = parts[3].toLongOrNull()?.toInt() ?: 0xFFFFFF

            // B 站颜色是十进制 RGB，无 alpha
            val argb = (0xFF shl 24) or (rgb and 0xFFFFFF)
            // 字号：25(B站标准) → 16sp；异常值兜底
            val fontSp = if (rawFontSize in 1f..100f) {
                rawFontSize / BASE_FONT_SIZE * DEFAULT_FONT_SP
            } else DEFAULT_FONT_SP

            DanmakuItem(
                timeSec = timeSec,
                type = DanmakuType.fromRaw(mode),
                color = argb,
                text = text.trim(),
                fontSize = fontSp
            )
        } catch (e: Exception) {
            null
        }
    }
}
