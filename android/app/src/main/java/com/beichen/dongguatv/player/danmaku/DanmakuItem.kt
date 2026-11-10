package com.beichen.dongguatv.player.danmaku

/** 弹幕类型：B 站 XML p 字段第二位 */
enum class DanmakuType(val raw: Int) {
    SCROLL(1),   // 滚动（右→左）
    BOTTOM(4),   // 底部固定
    TOP(5);      // 顶部固定
    companion object {
        fun fromRaw(v: Int): DanmakuType = when (v) {
            4 -> BOTTOM
            5 -> TOP
            else -> SCROLL
        }
    }
}

/** 单条弹幕。颜色为 ARGB int（白=0xFFFFFFFF）。 */
data class DanmakuItem(
    val timeSec: Float,        // 出现时间（秒）
    val type: DanmakuType,
    val color: Int,            // ARGB
    val text: String,
    val fontSize: Float,       // sp
)
