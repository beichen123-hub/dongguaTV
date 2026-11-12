package com.beichen.dongguatv.player.danmaku

import android.content.Context

/**
 * 弹幕外观与行为配置（持久化到本地 SharedPreferences）。
 */
data class DanmakuConfig(
    val enabled: Boolean = true,
    /** 显示区域占屏幕高度比例：0.25f(1/4屏) / 0.50f(半屏) / 0.75f(3/4屏) / 1.0f(全屏) */
    val areaRatio: Float = 0.50f,
    /** 不透明度：0.20f ~ 1.00f */
    val alphaRatio: Float = 0.85f,
    /** 字号缩放：0.75f ~ 1.50f */
    val fontScale: Float = 1.00f,
    /** 弹幕速度：0.70f ~ 1.60f */
    val speedScale: Float = 1.00f,
    /** 显示顶部固定弹幕 */
    val showTop: Boolean = true,
    /** 显示底部固定弹幕 */
    val showBottom: Boolean = true
)

object DanmakuPrefs {
    private const val PREF_NAME = "danmaku_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_AREA = "area_ratio"
    private const val KEY_ALPHA = "alpha_ratio"
    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_SPEED = "speed_scale"
    private const val KEY_SHOW_TOP = "show_top"
    private const val KEY_SHOW_BOTTOM = "show_bottom"

    fun load(context: Context): DanmakuConfig {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return DanmakuConfig(
            enabled = sp.getBoolean(KEY_ENABLED, true),
            areaRatio = sp.getFloat(KEY_AREA, 0.50f).coerceIn(0.25f, 1.0f),
            alphaRatio = sp.getFloat(KEY_ALPHA, 0.85f).coerceIn(0.20f, 1.0f),
            fontScale = sp.getFloat(KEY_FONT_SCALE, 1.00f).coerceIn(0.75f, 1.50f),
            speedScale = sp.getFloat(KEY_SPEED, 1.00f).coerceIn(0.70f, 1.60f),
            showTop = sp.getBoolean(KEY_SHOW_TOP, true),
            showBottom = sp.getBoolean(KEY_SHOW_BOTTOM, true)
        )
    }

    fun save(context: Context, cfg: DanmakuConfig) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, cfg.enabled)
            .putFloat(KEY_AREA, cfg.areaRatio)
            .putFloat(KEY_ALPHA, cfg.alphaRatio)
            .putFloat(KEY_FONT_SCALE, cfg.fontScale)
            .putFloat(KEY_SPEED, cfg.speedScale)
            .putBoolean(KEY_SHOW_TOP, cfg.showTop)
            .putBoolean(KEY_SHOW_BOTTOM, cfg.showBottom)
            .apply()
    }

    fun reset(context: Context): DanmakuConfig {
        val def = DanmakuConfig()
        save(context, def)
        return def
    }
}
