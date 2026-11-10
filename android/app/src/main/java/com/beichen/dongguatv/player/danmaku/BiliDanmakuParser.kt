package com.beichen.dongguatv.player.danmaku

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * 解析 B 站标准弹幕 XML：
 *   <i><d p="12.34,1,16777215,25,0,0,abc,123">文字</d>...</i>
 * p 字段：time, type, color, fontSize, pool, userIdHash, id
 * color 为十进制 RGB（无 alpha），转成 ARGB（alpha=0xFF）。
 */
object BiliDanmakuParser {

    private const val DEFAULT_FONT_SIZE = 16f

    fun parse(xml: String): List<DanmakuItem> {
        val list = ArrayList<DanmakuItem>(512)
        return try {
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
            list.sortedBy { it.timeSec }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseItem(p: String, text: String): DanmakuItem? {
        val parts = p.split(",")
        if (parts.size < 3) return null
        return try {
            val timeSec = parts[0].toFloat()
            val type = DanmakuType.fromRaw(parts[1].toInt())
            // B 站颜色是十进制 RGB（0-16777215），转 ARGB
            val rgb = parts[2].toLong().toInt() and 0xFFFFFF
            val argb = (0xFF shl 24) or rgb
            val fontSize = if (parts.size > 3) {
                parts[3].toFloatOrNull() ?: DEFAULT_FONT_SIZE
            } else DEFAULT_FONT_SIZE
            DanmakuItem(timeSec, type, argb, text, fontSize)
        } catch (e: Exception) { null }
    }
}
