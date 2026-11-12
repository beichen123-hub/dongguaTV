package com.beichen.dongguatv.player.danmaku

/**
 * 弹幕时间轴纯逻辑（不依赖 Android，可在 JVM 单元测试中直接验证）。
 *
 * 设计要点：把「轨道分配」变成对整条时间轴的一次性确定性预计算，
 * 运行时每帧只按当前播放秒筛选可见窗口并绘制，不保留任何跨帧游标状态。
 * 这样弹幕异步加载、seek、倍速、配置变更都不会引发游标错乱或满屏炸屏。
 */
object DanmakuTimeline {

    const val TYPE_SCROLL = 0
    const val TYPE_TOP = 1
    const val TYPE_BOTTOM = 2

    /**
     * 二分查找第一个 timeSec >= fromSec 的下标。
     * times 必须按升序排列；返回 times.size 表示全部早于窗口。
     */
    fun windowStart(times: FloatArray, fromSec: Float): Int {
        var lo = 0
        var hi = times.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] < fromSec) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * 按时间升序为每条弹幕预分配轨道，返回等长轨道数组，-1 表示该时刻轨道全忙、此条丢弃。
     *
     * 滚动弹幕：同一轨道前后两条至少间隔「前一条完全进入屏幕」的时间
     * (width + gap) / pxPerSec，避免文字追尾重叠。
     * 顶部 / 底部固定弹幕：各自独立轨道池，占用 fixedDurationSec。
     * 顶部/底部屏蔽由渲染层处理，这里照常分配，保证开关切换无需重算。
     */
    fun assignTracks(
        times: FloatArray,
        widths: FloatArray,
        types: IntArray,
        trackCount: Int,
        pxPerSec: Float,
        gapPx: Float,
        fixedDurationSec: Float
    ): IntArray {
        val n = times.size
        val result = IntArray(n) { -1 }
        if (trackCount <= 0 || pxPerSec <= 0f) return result

        val scrollEnd = FloatArray(trackCount) { -1f }
        val topEnd = FloatArray(trackCount) { -1f }
        val bottomEnd = FloatArray(trackCount) { -1f }

        for (i in 0 until n) {
            val t = times[i]
            when (types[i]) {
                TYPE_SCROLL -> {
                    val pool = scrollEnd
                    var picked = -1
                    for (k in 0 until trackCount) {
                        if (pool[k] <= t) {
                            picked = k
                            break
                        }
                    }
                    if (picked >= 0) {
                        pool[picked] = t + (widths[i] + gapPx) / pxPerSec
                        result[i] = picked
                    }
                }
                TYPE_TOP -> {
                    var picked = -1
                    for (k in 0 until trackCount) {
                        if (topEnd[k] <= t) {
                            picked = k
                            break
                        }
                    }
                    if (picked >= 0) {
                        topEnd[picked] = t + fixedDurationSec
                        result[i] = picked
                    }
                }
                TYPE_BOTTOM -> {
                    var picked = -1
                    for (k in 0 until trackCount) {
                        if (bottomEnd[k] <= t) {
                            picked = k
                            break
                        }
                    }
                    if (picked >= 0) {
                        bottomEnd[picked] = t + fixedDurationSec
                        result[i] = picked
                    }
                }
            }
        }
        return result
    }

    /** 滚动弹幕在 sec 时刻的 x 坐标（右边界为 viewWidth，随时间向左移动） */
    fun scrollX(viewWidth: Float, sec: Float, timeSec: Float, pxPerSec: Float): Float =
        viewWidth - (sec - timeSec) * pxPerSec
}
