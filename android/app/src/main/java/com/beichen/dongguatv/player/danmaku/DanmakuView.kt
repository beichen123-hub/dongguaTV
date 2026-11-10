package com.beichen.dongguatv.player.danmaku

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.max

/**
 * 自绘弹幕组件。零第三方依赖。
 * - 滚动/顶部/底部三种弹幕
 * - 时间轴由外部播放器提供（bindTimeProvider）
 * - 倍速跟随播放器
 * - 轨道自动分配，防重叠
 */
class DanmakuView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var source: List<DanmakuItem> = emptyList()
    private var visible: Boolean = true
    private var speed: Float = 1f
    private var timeProvider: () -> Long = { 0L }

    // 轨道
    private var trackHeight: Float = 0f
    private var trackCount: Int = 0
    private var scrollNextAvail: FloatArray = FloatArray(0)  // 每轨道"可放下一条的时间(秒)"
    private var fixedNextAvail: FloatArray = FloatArray(0)   // 顶部+底部共用

    // 活跃弹幕
    private val activeScroll = ArrayList<ActiveScroll>(64)
    private val activeFixed = ArrayList<ActiveFixed>(32)
    private var spawnIndex: Int = 0   // source 已 spawn 到第几个

    // 绘制参数
    private val density = resources.displayMetrics.density
    private var baseFontSize: Float = 16f * density
    private val scrollSpeedPxPerSec: Float get() = 140f * density * speed  // 滚动速度
    private val fixedDurationSec: Float get() = 5f   // 顶/底弹幕停留秒数

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = density * 1.2f
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 0, 0, 0)
    }

    // 上一次 frame 时间，用于判断是否需要继续刷新
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

    /** 绑定播放器时间源（毫秒）。PlayerActivity: danmakuView.bindTimeProvider { player.currentPosition } */
    fun bindTimeProvider(provider: () -> Long) { timeProvider = provider }

    /** 设置弹幕源（已解析的列表） */
    fun setSource(items: List<DanmakuItem>) {
        source = items
        reset()
    }

    fun setSpeed(s: Float) { speed = s }
    fun show() { visible = true }
    fun hide() {
        visible = false
        activeScroll.clear(); activeFixed.clear()
        invalidate()
    }
    fun toggle(): Boolean {
        if (visible) hide() else show()
        return visible
    }

    /** seek 时清屏重排 */
    fun onSeek(positionMs: Long) {
        reset()
        // spawnIndex 跳到 >= position 的第一条
        val sec = positionMs / 1000f
        spawnIndex = source.indexOfFirst { it.timeSec >= sec }.let { if (it < 0) source.size else it }
    }

    fun startLoop() {
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

    private fun ensureTracks() {
        if (height == 0) return
        trackHeight = baseFontSize * 1.5f
        trackCount = max(1, (height / trackHeight).toInt())
        if (scrollNextAvail.size != trackCount) {
            scrollNextAvail = FloatArray(trackCount) { -1f }
            fixedNextAvail = FloatArray(trackCount) { -1f }
        }
    }

    private fun spawnNew(sec: Float) {
        if (source.isEmpty() || height == 0) return
        ensureTracks()
        while (spawnIndex < source.size && source[spawnIndex].timeSec <= sec) {
            val item = source[spawnIndex]
            spawnOne(item, sec)
            spawnIndex++
        }
    }

    private fun spawnOne(item: DanmakuItem, nowSec: Float) {
        when (item.type) {
            DanmakuType.SCROLL -> {
                val w = measureText(item)
                val track = pickScrollTrack(item.timeSec, w)
                if (track < 0) return  // 满了，丢弃
                // 滚动弹幕完全进入屏幕的时间 = 出现时间 + w / speed
                scrollNextAvail[track] = item.timeSec + w / scrollSpeedPxPerSec
                activeScroll.add(ActiveScroll(item, track, w))
            }
            DanmakuType.TOP, DanmakuType.BOTTOM -> {
                val track = pickFixedTrack(item.timeSec, item.type)
                if (track < 0) return
                fixedNextAvail[track] = item.timeSec + fixedDurationSec
                activeFixed.add(ActiveFixed(item, track))
            }
        }
    }

    private fun pickScrollTrack(t: Float, w: Float): Int {
        for (i in 0 until trackCount) {
            if (scrollNextAvail[i] <= t) return i
        }
        return -1
    }

    private fun pickFixedTrack(t: Float, type: DanmakuType): Int {
        for (i in 0 until trackCount) {
            if (fixedNextAvail[i] <= t) return i
        }
        return -1
    }

    private fun measureText(item: DanmakuItem): Float {
        textPaint.textSize = item.fontSize * density
        return textPaint.measureText(item.text)
    }

    private fun expireOld(sec: Float) {
        // 滚动：x < -w 即出屏
        val it1 = activeScroll.iterator()
        while (it1.hasNext()) {
            val a = it1.next()
            val x = scrollX(a, sec)
            if (x < -a.width) it1.remove()
        }
        // 固定：超过 duration
        val it2 = activeFixed.iterator()
        while (it2.hasNext()) {
            val a = it2.next()
            if (sec - a.item.timeSec > fixedDurationSec) it2.remove()
        }
    }

    private fun scrollX(a: ActiveScroll, sec: Float): Float {
        // 从右进入，匀速左移
        val elapsed = (sec - a.item.timeSec) * scrollSpeedPxPerSec
        return width - elapsed
    }

    private fun hasActive(): Boolean = activeScroll.isNotEmpty() || activeFixed.isNotEmpty()

    override fun onDraw(canvas: Canvas) {
        if (!visible || height == 0) return
        val sec = timeProvider() / 1000f
        // 滚动弹幕
        for (a in activeScroll) {
            val x = scrollX(a, sec)
            if (x < -a.width || x > width) continue
            val y = a.track * trackHeight + baseFontSize
            drawDanmaku(canvas, a.item, x, y)
        }
        // 固定弹幕
        for (a in activeFixed) {
            val w = measureText(a.item)
            val x = (width - w) / 2f
            val y = when (a.item.type) {
                DanmakuType.TOP -> a.track * trackHeight + baseFontSize
                DanmakuType.BOTTOM -> height - (trackCount - 1 - a.track) * trackHeight
                else -> a.track * trackHeight + baseFontSize
            }
            drawDanmaku(canvas, a.item, x, y)
        }
    }

    private fun drawDanmaku(canvas: Canvas, item: DanmakuItem, x: Float, y: Float) {
        textPaint.textSize = item.fontSize * density
        // 描边（黑底）
        val oldColor = textPaint.color
        textPaint.color = Color.argb(220, 0, 0, 0)
        canvas.drawText(item.text, x, y, textPaint)
        // 正文
        textPaint.color = item.color
        canvas.drawText(item.text, x, y, textPaint)
        textPaint.color = oldColor
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ensureTracks()
    }

    private class ActiveScroll(val item: DanmakuItem, val track: Int, val width: Float)
    private class ActiveFixed(val item: DanmakuItem, val track: Int)
}
