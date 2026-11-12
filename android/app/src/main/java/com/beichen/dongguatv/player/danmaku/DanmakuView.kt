package com.beichen.dongguatv.player.danmaku

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 自绘弹幕组件（零第三方依赖）。
 * 支持滚动/顶部/底部弹幕、时间轴同步、倍速跟随，以及实时可调的：
 * 显示区域占比（1/4~全屏）、透明度、字体大小、弹幕速度、顶/底屏蔽。
 */
class DanmakuView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var source: List<DanmakuItem> = emptyList()
    private var visible: Boolean = true
    private var speed: Float = 1f
    private var displayAreaRatio: Float = 0.50f
    private var alphaRatio: Float = 0.85f
    private var fontScale: Float = 1.0f
    private var showTop: Boolean = true
    private var showBottom: Boolean = true
    private var timeProvider: () -> Long = { 0L }

    // 轨道
    private var trackHeight: Float = 0f
    private var trackCount: Int = 0
    private var scrollNextAvail: FloatArray = FloatArray(0)
    private var fixedNextAvail: FloatArray = FloatArray(0)

    // 活跃弹幕
    private val activeScroll = ArrayList<ActiveScroll>(64)
    private val activeFixed = ArrayList<ActiveFixed>(32)
    private var spawnIndex: Int = 0

    // 绘制参数
    private val density = resources.displayMetrics.density
    private val baseFontSize: Float get() = 16f * fontScale * density
    private val scrollSpeedPxPerSec: Float get() = 140f * density * speed
    private val fixedDurationSec: Float get() = 5f

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 2.2f
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var lastDrawSec: Float = -1f
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val sec = timeProvider() / 1000f
            if (visible && (sec != lastDrawSec || hasActive())) {
                lastDrawSec = sec
                spawnNew(sec)
                expireOld(sec)
                invalidate()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun bindTimeProvider(provider: () -> Long) { timeProvider = provider }

    fun setSource(items: List<DanmakuItem>) {
        source = items
        reset()
    }

    /** 实时应用弹幕配置，无需重启播放器 */
    fun applyConfig(cfg: DanmakuConfig) {
        val areaChanged = displayAreaRatio != cfg.areaRatio
        val fontChanged = fontScale != cfg.fontScale
        visible = cfg.enabled
        displayAreaRatio = cfg.areaRatio.coerceIn(0.25f, 1.0f)
        alphaRatio = cfg.alphaRatio.coerceIn(0.15f, 1.0f)
        fontScale = cfg.fontScale.coerceIn(0.75f, 1.50f)
        speed = cfg.speedScale.coerceIn(0.70f, 1.60f)
        showTop = cfg.showTop
        showBottom = cfg.showBottom

        if (!visible) {
            activeScroll.clear()
            activeFixed.clear()
        } else {
            if (!showTop) activeFixed.removeAll { it.item.type == DanmakuType.TOP }
            if (!showBottom) activeFixed.removeAll { it.item.type == DanmakuType.BOTTOM }
            if (areaChanged || fontChanged) {
                ensureTracks()
                // 裁剪超出新显示区域的弹幕，所见即所得
                activeScroll.removeAll { it.track >= trackCount }
                activeFixed.removeAll { it.track >= trackCount }
            }
        }
        lastDrawSec = -1f
        invalidate()
    }

    fun setSpeed(s: Float) { speed = s }
    fun show() { visible = true; lastDrawSec = -1f; invalidate() }
    fun hide() {
        visible = false
        activeScroll.clear(); activeFixed.clear()
        invalidate()
    }
    fun toggle(): Boolean {
        if (visible) hide() else show()
        return visible
    }

    fun onSeek(positionMs: Long) {
        reset()
        val sec = positionMs / 1000f
        spawnIndex = source.indexOfFirst { it.timeSec >= sec }.let { if (it < 0) source.size else it }
        lastDrawSec = -1f
        invalidate()
    }

    fun startLoop() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stopLoop() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        activeScroll.clear(); activeFixed.clear()
        invalidate()
    }

    private fun reset() {
        activeScroll.clear(); activeFixed.clear()
        spawnIndex = 0
        ensureTracks()
    }

    private fun effectiveAreaHeight(): Float = height * displayAreaRatio

    private fun ensureTracks() {
        if (height == 0) return
        trackHeight = baseFontSize * 1.45f
        val availHeight = max(trackHeight, effectiveAreaHeight())
        val newCount = max(1, (availHeight / trackHeight).toInt())
        if (scrollNextAvail.size != newCount) {
            scrollNextAvail = FloatArray(newCount) { -1f }
            fixedNextAvail = FloatArray(newCount) { -1f }
        }
        trackCount = newCount
    }

    private fun spawnNew(sec: Float) {
        if (source.isEmpty() || height == 0) return
        ensureTracks()
        while (spawnIndex < source.size && source[spawnIndex].timeSec <= sec) {
            val item = source[spawnIndex]
            // seek 刚过去很久的历史弹幕不堆积
            if (sec - item.timeSec <= 2.0f) {
                spawnOne(item)
            }
            spawnIndex++
        }
    }

    private fun spawnOne(item: DanmakuItem) {
        when (item.type) {
            DanmakuType.SCROLL -> {
                val w = measureText(item)
                val track = pickScrollTrack(item.timeSec)
                if (track < 0) return
                scrollNextAvail[track] = item.timeSec + w / scrollSpeedPxPerSec + 0.35f
                activeScroll.add(ActiveScroll(item, track, w))
            }
            DanmakuType.TOP -> {
                if (!showTop) return
                val track = pickFixedTrack(item.timeSec)
                if (track < 0) return
                fixedNextAvail[track] = item.timeSec + fixedDurationSec
                activeFixed.add(ActiveFixed(item, track))
            }
            DanmakuType.BOTTOM -> {
                if (!showBottom) return
                val track = pickFixedTrack(item.timeSec)
                if (track < 0) return
                fixedNextAvail[track] = item.timeSec + fixedDurationSec
                activeFixed.add(ActiveFixed(item, track))
            }
        }
    }

    private fun pickScrollTrack(t: Float): Int {
        for (i in 0 until trackCount) {
            if (scrollNextAvail[i] <= t) return i
        }
        return -1
    }

    private fun pickFixedTrack(t: Float): Int {
        for (i in 0 until trackCount) {
            if (fixedNextAvail[i] <= t) return i
        }
        return -1
    }

    private fun measureText(item: DanmakuItem): Float {
        fillPaint.textSize = item.fontSize * fontScale * density
        return fillPaint.measureText(item.text)
    }

    private fun expireOld(sec: Float) {
        val it1 = activeScroll.iterator()
        while (it1.hasNext()) {
            val a = it1.next()
            if (scrollX(a, sec) < -a.width) it1.remove()
        }
        val it2 = activeFixed.iterator()
        while (it2.hasNext()) {
            val a = it2.next()
            if (sec - a.item.timeSec > fixedDurationSec) it2.remove()
        }
    }

    private fun scrollX(a: ActiveScroll, sec: Float): Float {
        val elapsed = (sec - a.item.timeSec) * scrollSpeedPxPerSec
        return width - elapsed
    }

    private fun hasActive(): Boolean = activeScroll.isNotEmpty() || activeFixed.isNotEmpty()

    override fun onDraw(canvas: Canvas) {
        if (!visible || height == 0) return
        val sec = timeProvider() / 1000f
        for (a in activeScroll) {
            if (a.track >= trackCount) continue
            val x = scrollX(a, sec)
            if (x < -a.width || x > width) continue
            val y = a.track * trackHeight + baseFontSize
            drawDanmaku(canvas, a.item, x, y)
        }
        val areaBottom = effectiveAreaHeight()
        for (a in activeFixed) {
            if (a.track >= trackCount) continue
            val w = measureText(a.item)
            val x = (width - w) / 2f
            val y = when (a.item.type) {
                DanmakuType.TOP -> a.track * trackHeight + baseFontSize
                DanmakuType.BOTTOM -> areaBottom - (trackCount - 1 - a.track) * trackHeight - 4f * density
                else -> a.track * trackHeight + baseFontSize
            }
            drawDanmaku(canvas, a.item, x, y)
        }
    }

    private fun drawDanmaku(canvas: Canvas, item: DanmakuItem, x: Float, y: Float) {
        val sizePx = item.fontSize * fontScale * density
        strokePaint.textSize = sizePx
        fillPaint.textSize = sizePx

        val strokeAlpha = (210f * alphaRatio).roundToInt().coerceIn(0, 255)
        strokePaint.color = Color.argb(strokeAlpha, 0, 0, 0)
        canvas.drawText(item.text, x, y, strokePaint)

        val textAlpha = (Color.alpha(item.color) * alphaRatio).roundToInt().coerceIn(0, 255)
        fillPaint.color = Color.argb(
            textAlpha,
            Color.red(item.color),
            Color.green(item.color),
            Color.blue(item.color)
        )
        canvas.drawText(item.text, x, y, fillPaint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ensureTracks()
    }

    private class ActiveScroll(val item: DanmakuItem, val track: Int, val width: Float)
    private class ActiveFixed(val item: DanmakuItem, val track: Int)
}
