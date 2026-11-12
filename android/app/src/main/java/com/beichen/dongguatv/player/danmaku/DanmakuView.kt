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
 *
 * 渲染模型：无状态窗口渲染。
 *  - 轨道在 setSource / 配置变更时对整条时间轴一次性预分配（DanmakuTimeline），
 *  - 每帧只按当前播放秒筛选「可见窗口」内的弹幕并计算坐标，
 *  - 不保留 spawnIndex / activeList / 轨道冷却等跨帧游标状态。
 *
 * 由此天然解决：弹幕异步加载时首帧把历史弹幕一次性刷满屏、轨道冷却时间戳
 * 被写死导致后续滚动弹幕全被丢弃、seek 后游标错位等老问题。
 * 显示区域用 canvas.clipRect 硬裁剪，透明度/字号/速度/顶底开关每帧从配置读取，
 * 打开设置面板（即使播放器暂停）也能立即看到效果。
 */
class DanmakuView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var source: List<DanmakuItem> = emptyList()
    private var cfg: DanmakuConfig = DanmakuConfig()
    private var enabled: Boolean = true
    private var playbackSpeed: Float = 1f
    private var timeProvider: () -> Long = { 0L }

    // 预分配结果（与 source 等长）
    private var times = FloatArray(0)
    private var widths = FloatArray(0)
    private var types = IntArray(0)
    private var tracks = IntArray(0)
    private var maxTextWidth = 0f
    private var placementKey = ""

    private var dirty = true
    private var lastDrawSec: Float = -1f

    private val density = resources.displayMetrics.density

    /** 每帧从配置读取，保证设置实时生效 */
    private val pxPerSec: Float get() = 140f * density * cfg.speedScale * max(0.25f, playbackSpeed)
    private val fontSizePx: Float get() = 16f * cfg.fontScale * density
    private val trackHeightPx: Float get() = fontSizePx * 1.45f
    private val areaHeightPx: Float get() = height * cfg.areaRatio
    private val trackCount: Int get() = max(1, (areaHeightPx / trackHeightPx).toInt())

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 2.2f
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val sec = timeProvider() / 1000f
            // 暂停时 sec 不变：靠 dirty 保证配置变更仍刷新一帧，避免空转耗电
            if (enabled && source.isNotEmpty() && (sec != lastDrawSec || dirty)) {
                lastDrawSec = sec
                dirty = false
                invalidate()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun bindTimeProvider(provider: () -> Long) {
        timeProvider = provider
    }

    fun setSource(items: List<DanmakuItem>) {
        source = items.sortedBy { it.timeSec }
        placementKey = ""
        dirty = true
        invalidate()
    }

    /** 实时应用弹幕配置，无需重启播放器 */
    fun applyConfig(newCfg: DanmakuConfig) {
        cfg = newCfg.copy(
            areaRatio = newCfg.areaRatio.coerceIn(0.25f, 1.0f),
            alphaRatio = newCfg.alphaRatio.coerceIn(0.15f, 1.0f),
            fontScale = newCfg.fontScale.coerceIn(0.75f, 1.50f),
            speedScale = newCfg.speedScale.coerceIn(0.70f, 1.60f)
        )
        enabled = cfg.enabled
        placementKey = ""
        lastDrawSec = -1f
        dirty = true
        invalidate()
    }

    /** 播放器倍速（不含用户设置的速度档），最终速度 = cfg.speedScale * playbackSpeed */
    fun setSpeed(s: Float) {
        playbackSpeed = s
        placementKey = ""
        dirty = true
        invalidate()
    }

    fun show() {
        enabled = true
        dirty = true
        invalidate()
    }

    fun hide() {
        enabled = false
        dirty = true
        invalidate()
    }

    fun toggle(): Boolean {
        if (enabled) hide() else show()
        return enabled
    }

    fun onSeek(@Suppress("UNUSED_PARAMETER") positionMs: Long) {
        // 无状态渲染：窗口随 timeProvider 自动对齐，无需重置任何游标
        lastDrawSec = -1f
        dirty = true
        invalidate()
    }

    fun startLoop() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stopLoop() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    /** 轨道预分配：仅在尺寸/配置/数据源变化时重算 */
    private fun ensurePlacement() {
        if (width == 0 || height == 0 || source.isEmpty()) return
        val key = buildString {
            append(width).append('|').append(height).append('|')
            append(cfg.areaRatio).append('|').append(cfg.fontScale).append('|')
            append(cfg.speedScale).append('|').append(playbackSpeed).append('|')
            append(source.size)
        }
        if (key == placementKey) return

        val n = source.size
        times = FloatArray(n)
        widths = FloatArray(n)
        types = IntArray(n)
        maxTextWidth = 0f
        for (i in 0 until n) {
            val item = source[i]
            times[i] = item.timeSec
            types[i] = when (item.type) {
                DanmakuType.SCROLL -> DanmakuTimeline.TYPE_SCROLL
                DanmakuType.TOP -> DanmakuTimeline.TYPE_TOP
                DanmakuType.BOTTOM -> DanmakuTimeline.TYPE_BOTTOM
            }
            fillPaint.textSize = item.fontSize * cfg.fontScale * density
            val w = fillPaint.measureText(item.text)
            widths[i] = w
            if (w > maxTextWidth) maxTextWidth = w
        }
        tracks = DanmakuTimeline.assignTracks(
            times = times,
            widths = widths,
            types = types,
            trackCount = trackCount,
            pxPerSec = pxPerSec,
            gapPx = 24f * density,
            fixedDurationSec = FIXED_DURATION_SEC
        )
        placementKey = key
    }

    override fun onDraw(canvas: Canvas) {
        if (!enabled || width == 0 || height == 0 || source.isEmpty()) return
        ensurePlacement()

        val sec = timeProvider() / 1000f
        val speed = pxPerSec
        val scrollLifeSec = (width + maxTextWidth) / speed + 0.2f
        val windowFrom = sec - max(scrollLifeSec, FIXED_DURATION_SEC) - 0.2f
        val start = DanmakuTimeline.windowStart(times, windowFrom)
        val tracksN = trackCount
        val tHeight = trackHeightPx
        val area = areaHeightPx
        val baseFont = fontSizePx

        // 显示区域硬裁剪：1/4 屏 / 半屏 / 3/4 屏 / 全屏 所见即所得
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), area)

        var drawn = 0
        for (i in start until source.size) {
            val t = times[i]
            if (t > sec) break
            if (drawn >= MAX_DRAW_PER_FRAME) break
            val track = tracks[i]
            if (track < 0 || track >= tracksN) continue

            val item = source[i]
            val age = sec - t
            when (item.type) {
                DanmakuType.SCROLL -> {
                    if (age > scrollLifeSec) continue
                    val x = DanmakuTimeline.scrollX(width.toFloat(), sec, t, speed)
                    if (x > width || x < -widths[i]) continue
                    val y = track * tHeight + baseFont
                    drawDanmaku(canvas, item, x, y)
                    drawn++
                }
                DanmakuType.TOP -> {
                    if (!cfg.showTop || age > FIXED_DURATION_SEC) continue
                    val x = (width - widths[i]) / 2f
                    val y = track * tHeight + baseFont
                    drawDanmaku(canvas, item, x, y)
                    drawn++
                }
                DanmakuType.BOTTOM -> {
                    if (!cfg.showBottom || age > FIXED_DURATION_SEC) continue
                    val x = (width - widths[i]) / 2f
                    // track=0 贴显示区域最底行，往上依次堆叠
                    val y = area - (track + 1) * tHeight + baseFont - 4f * density
                    drawDanmaku(canvas, item, x, y)
                    drawn++
                }
            }
        }
        canvas.restore()
    }

    private fun drawDanmaku(canvas: Canvas, item: DanmakuItem, x: Float, y: Float) {
        val sizePx = item.fontSize * cfg.fontScale * density
        strokePaint.textSize = sizePx
        fillPaint.textSize = sizePx

        val strokeAlpha = (210f * cfg.alphaRatio).roundToInt().coerceIn(0, 255)
        strokePaint.color = Color.argb(strokeAlpha, 0, 0, 0)
        canvas.drawText(item.text, x, y, strokePaint)

        val textAlpha = (Color.alpha(item.color) * cfg.alphaRatio).roundToInt().coerceIn(0, 255)
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
        placementKey = ""
        dirty = true
    }

    companion object {
        const val FIXED_DURATION_SEC = 4.5f
        const val MAX_DRAW_PER_FRAME = 120
    }
}
