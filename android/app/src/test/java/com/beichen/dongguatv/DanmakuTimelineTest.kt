package com.beichen.dongguatv

import com.beichen.dongguatv.player.danmaku.DanmakuTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 弹幕时间轴纯逻辑单测。
 * 钉死两条曾经出过真机 bug 的规则：
 *  1) 可见窗口必须能从任意播放秒正确切入（弹幕异步加载/续播不会刷历史弹幕）；
 *  2) 同一轨道相邻滚动弹幕必须留出「前一条完全进入屏幕」的时间，且轨道不够时丢弃而不是堆叠。
 */
class DanmakuTimelineTest {

    @Test
    fun window_start_skips_all_history_before_current_second() {
        val times = floatArrayOf(0f, 1f, 2f, 10f, 10.5f, 11f, 40f, 41f)
        // 播到第 40 秒时才加载完弹幕，窗口起点应直接落在 40 附近，不回头遍历 0~10 秒
        val start = DanmakuTimeline.windowStart(times, 40f - 12f)
        assertEquals(6, start)
        assertTrue(abs(times[start] - 40f) < 0.001f)
    }

    @Test
    fun window_start_handles_empty_and_out_of_range() {
        assertEquals(0, DanmakuTimeline.windowStart(floatArrayOf(), 5f))
        assertEquals(0, DanmakuTimeline.windowStart(floatArrayOf(1f, 2f), -10f))
        assertEquals(2, DanmakuTimeline.windowStart(floatArrayOf(1f, 2f), 99f))
    }

    @Test
    fun assign_tracks_never_overlaps_on_same_track() {
        // 6 条滚动弹幕全挤在 0.05 秒内，只有 2 条轨道
        val times = floatArrayOf(0f, 0.01f, 0.02f, 0.03f, 0.04f, 0.05f)
        val widths = FloatArray(6) { 300f }
        val types = IntArray(6) { DanmakuTimeline.TYPE_SCROLL }
        val speed = 300f   // px/s
        val gap = 30f
        val tracks = DanmakuTimeline.assignTracks(times, widths, types, 2, speed, gap, 4.5f)

        // 最多只有 2 条能进场，其余必须被丢弃（-1），不能再出现满屏堆叠
        assertEquals(2, tracks.count { it >= 0 })
        assertEquals(0, tracks[0])
        assertEquals(1, tracks[1])
        assertTrue(tracks[2] < 0)

        // 轨道占用时长 = (300 + 30) / 300 = 1.1s，间隔不足的后续弹幕不得复用
        val laterTimes = floatArrayOf(0f, 1.2f)
        val laterWidths = FloatArray(2) { 300f }
        val laterTypes = IntArray(2) { DanmakuTimeline.TYPE_SCROLL }
        val later = DanmakuTimeline.assignTracks(laterTimes, laterWidths, laterTypes, 1, speed, gap, 4.5f)
        assertEquals(0, later[1])
    }

    @Test
    fun top_and_bottom_use_independent_track_pools() {
        val times = floatArrayOf(1f, 1f, 1f, 1f)
        val widths = FloatArray(4) { 200f }
        val types = intArrayOf(
            DanmakuTimeline.TYPE_TOP,
            DanmakuTimeline.TYPE_TOP,
            DanmakuTimeline.TYPE_BOTTOM,
            DanmakuTimeline.TYPE_BOTTOM
        )
        val tracks = DanmakuTimeline.assignTracks(times, widths, types, 2, 300f, 30f, 4.5f)
        // 顶部占 0、1；底部也从 0、1 开始，互不抢占
        assertEquals(0, tracks[0])
        assertEquals(1, tracks[1])
        assertEquals(0, tracks[2])
        assertEquals(1, tracks[3])
        assertTrue(tracks.all { it >= 0 })
    }

    @Test
    fun scroll_x_moves_left_over_time() {
        val x0 = DanmakuTimeline.scrollX(1000f, 10f, 10f, 300f)
        val x1 = DanmakuTimeline.scrollX(1000f, 11f, 10f, 300f)
        assertTrue(abs(x0 - 1000f) < 0.001f)
        assertTrue(abs(x1 - 700f) < 0.001f)
        assertTrue(x1 < x0)
    }
}
