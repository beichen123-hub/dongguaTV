package com.beichen.dongguatv.ui.player

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.beichen.dongguatv.R
import com.beichen.dongguatv.data.Repository
import com.beichen.dongguatv.data.SourceResolver
import com.beichen.dongguatv.databinding.ActivityPlayerBinding
import com.beichen.dongguatv.player.danmaku.BiliDanmakuParser
import com.beichen.dongguatv.player.danmaku.DanmakuConfig
import com.beichen.dongguatv.player.danmaku.DanmakuPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private val repo by lazy { Repository.get(this) }

    private var player: ExoPlayer? = null
    private var playbackSpeed: Float = 1.0f
    private lateinit var danmakuConfig: DanmakuConfig

    // 动态插进 Media3 exo_basic_controls（设置按钮旁边）的两个按钮
    private var ctrlDanmakuToggle: ImageButton? = null
    private var ctrlDanmakuSettings: ImageButton? = null

    private val url by lazy { intent.getStringExtra("url").orEmpty() }
    private val vodName by lazy { intent.getStringExtra("vodName").orEmpty() }
    private val episodeIndex by lazy { intent.getIntExtra("episodeIndex", 0) }
    private val startPositionMs by lazy { intent.getLongExtra("startPositionMs", 0L) }
    private val siteKey by lazy { intent.getStringExtra("siteKey").orEmpty() }
    private val vodId by lazy { intent.getStringExtra("vodId").orEmpty() }
    private val vodPic by lazy { intent.getStringExtra("vodPic").orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.panelDanmaku.visibility == View.VISIBLE) {
                    closeDanmakuPanel()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        binding.tvPlayerTitle.text = if (vodName.isNotBlank()) {
            "$vodName · 第${episodeIndex + 1}集"
        } else {
            "第${episodeIndex + 1}集"
        }

        // 横屏全屏沉浸：隐藏系统栏，划一下临时呼出
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        binding.playerView.keepScreenOn = true

        // 读取并应用本地弹幕配置
        danmakuConfig = DanmakuPrefs.load(this)
        binding.danmakuView.applyConfig(danmakuConfig)

        injectDanmakuControlsIntoPlayerBar()
        setupDanmakuPanel()
        setupPlayer()
        loadDanmaku()
        recordHistory()
    }

    /**
     * 把「弹幕开关」和「弹幕设置」直接插进 Media3 控制栏右下角 exo_basic_controls
     * （exo_settings 齿轮设置按钮旁边），跟随控制栏自动显隐，彻底告别右上角突兀浮标。
     */
    private fun injectDanmakuControlsIntoPlayerBar() {
        binding.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                binding.topBar.visibility = visibility
                if (binding.fallbackDanmakuBar.tag == "active") {
                    binding.fallbackDanmakuBar.visibility = visibility
                }
            }
        )

        binding.playerView.post {
            val basicId = resources.getIdentifier("exo_basic_controls", "id", packageName)
            val settingsId = resources.getIdentifier("exo_settings", "id", packageName)
            val basicBar = if (basicId != 0) binding.playerView.findViewById<ViewGroup>(basicId) else null
            if (basicBar != null) {
                val toggleBtn = buildControllerIconButton("弹幕开关") { toggleDanmaku() }
                val settingsBtn = buildControllerIconButton("弹幕设置") { openDanmakuPanel() }.apply {
                    setImageResource(R.drawable.ic_danmaku_settings)
                }
                ctrlDanmakuToggle = toggleBtn
                ctrlDanmakuSettings = settingsBtn

                val settingsView = if (settingsId != 0) basicBar.findViewById<View>(settingsId) else null
                val insertAt = if (settingsView != null) {
                    basicBar.indexOfChild(settingsView).coerceAtLeast(0)
                } else {
                    0
                }
                // 按顺序紧挨在「设置」左侧：弹幕开关 | 弹幕设置 | 原生设置
                basicBar.addView(settingsBtn, insertAt)
                basicBar.addView(toggleBtn, insertAt)
                refreshToggleIcons()
            } else {
                // 极端兼容兜底
                binding.fallbackDanmakuBar.tag = "active"
                binding.fallbackDanmakuBar.visibility = View.VISIBLE
                binding.btnFallbackToggle.setOnClickListener { toggleDanmaku() }
                binding.btnFallbackSettings.setOnClickListener { openDanmakuPanel() }
                refreshToggleIcons()
            }
        }
    }

    private fun buildControllerIconButton(desc: String, onClick: () -> Unit): ImageButton {
        val density = resources.displayMetrics.density
        val size = (44 * density).toInt()
        val pad = (10 * density).toInt()
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
        return ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            setPadding(pad, pad, pad, pad)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundResource(tv.resourceId)
            contentDescription = desc
            setOnClickListener { onClick() }
        }
    }

    private fun toggleDanmaku() {
        updateDanmakuConfig(danmakuConfig.copy(enabled = !danmakuConfig.enabled))
        Toast.makeText(
            this,
            if (danmakuConfig.enabled) "弹幕已开启" else "弹幕已关闭",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun refreshToggleIcons() {
        val icon = if (danmakuConfig.enabled) R.drawable.ic_danmaku_on else R.drawable.ic_danmaku_off
        ctrlDanmakuToggle?.setImageResource(icon)
        binding.btnFallbackToggle.setImageResource(icon)
    }

    // ==================== 弹幕设置右侧液态玻璃面板 ====================

    private fun setupDanmakuPanel() {
        binding.panelScrim.setOnClickListener { closeDanmakuPanel() }
        binding.btnClosePanel.setOnClickListener { closeDanmakuPanel() }

        binding.btnDanmakuReset.setOnClickListener {
            danmakuConfig = DanmakuPrefs.reset(this)
            applyConfigAndSyncUi()
            Toast.makeText(this, "已恢复默认弹幕设置", Toast.LENGTH_SHORT).show()
        }

        binding.swDanmakuEnable.setOnCheckedChangeListener { _, checked ->
            if (danmakuConfig.enabled != checked) {
                updateDanmakuConfig(danmakuConfig.copy(enabled = checked))
            }
        }

        // 显示区域：1/4、半屏、3/4、全屏
        val areaButtons = listOf(
            binding.area25 to 0.25f,
            binding.area50 to 0.50f,
            binding.area75 to 0.75f,
            binding.area100 to 1.00f
        )
        areaButtons.forEach { (view, ratio) ->
            view.setOnClickListener {
                updateDanmakuConfig(danmakuConfig.copy(areaRatio = ratio))
            }
        }

        // 不透明度：20% ~ 100%（SeekBar 0..80 + 20）
        binding.seekAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val pct = 20 + progress
                binding.tvAlphaVal.text = "${pct}%"
                if (fromUser) {
                    updateDanmakuConfig(danmakuConfig.copy(alphaRatio = pct / 100f))
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // 字体大小：小 / 标准 / 大 / 超大
        val fontButtons = listOf(
            binding.fontSmall to 0.80f,
            binding.fontNormal to 1.00f,
            binding.fontLarge to 1.25f,
            binding.fontXL to 1.45f
        )
        fontButtons.forEach { (view, scale) ->
            view.setOnClickListener {
                updateDanmakuConfig(danmakuConfig.copy(fontScale = scale))
            }
        }

        // 弹幕速度：慢 / 正常 / 快 / 极快
        val speedButtons = listOf(
            binding.speedSlow to 0.75f,
            binding.speedNormal to 1.00f,
            binding.speedFast to 1.30f,
            binding.speedVeryFast to 1.60f
        )
        speedButtons.forEach { (view, spd) ->
            view.setOnClickListener {
                updateDanmakuConfig(danmakuConfig.copy(speedScale = spd))
            }
        }

        binding.swShowTop.setOnCheckedChangeListener { _, checked ->
            if (danmakuConfig.showTop != checked) {
                updateDanmakuConfig(danmakuConfig.copy(showTop = checked))
            }
        }
        binding.swShowBottom.setOnCheckedChangeListener { _, checked ->
            if (danmakuConfig.showBottom != checked) {
                updateDanmakuConfig(danmakuConfig.copy(showBottom = checked))
            }
        }

        applyConfigAndSyncUi()
    }

    private fun updateDanmakuConfig(newCfg: DanmakuConfig) {
        danmakuConfig = newCfg
        DanmakuPrefs.save(this, newCfg)
        applyConfigAndSyncUi()
    }

    private fun applyConfigAndSyncUi() {
        binding.danmakuView.applyConfig(danmakuConfig)
        binding.danmakuView.setSpeed(danmakuConfig.speedScale * playbackSpeed)
        refreshToggleIcons()

        if (binding.swDanmakuEnable.isChecked != danmakuConfig.enabled) {
            binding.swDanmakuEnable.isChecked = danmakuConfig.enabled
        }
        if (binding.swShowTop.isChecked != danmakuConfig.showTop) {
            binding.swShowTop.isChecked = danmakuConfig.showTop
        }
        if (binding.swShowBottom.isChecked != danmakuConfig.showBottom) {
            binding.swShowBottom.isChecked = danmakuConfig.showBottom
        }

        val alphaPct = (danmakuConfig.alphaRatio * 100f).roundToInt().coerceIn(20, 100)
        binding.tvAlphaVal.text = "${alphaPct}%"
        if (binding.seekAlpha.progress != alphaPct - 20) {
            binding.seekAlpha.progress = alphaPct - 20
        }

        highlightOption(
            listOf(
                binding.area25 to 0.25f,
                binding.area50 to 0.50f,
                binding.area75 to 0.75f,
                binding.area100 to 1.00f
            ),
            danmakuConfig.areaRatio
        )
        highlightOption(
            listOf(
                binding.fontSmall to 0.80f,
                binding.fontNormal to 1.00f,
                binding.fontLarge to 1.25f,
                binding.fontXL to 1.45f
            ),
            danmakuConfig.fontScale
        )
        highlightOption(
            listOf(
                binding.speedSlow to 0.75f,
                binding.speedNormal to 1.00f,
                binding.speedFast to 1.30f,
                binding.speedVeryFast to 1.60f
            ),
            danmakuConfig.speedScale
        )
    }

    private fun highlightOption(options: List<Pair<TextView, Float>>, current: Float) {
        val activeColor = getColor(R.color.colorPrimaryLight)
        val normalColor = getColor(R.color.textSecondary)
        options.forEach { (tv, target) ->
            val selected = abs(target - current) < 0.05f
            tv.setBackgroundResource(
                if (selected) R.drawable.bg_glass_chip_active else R.drawable.bg_glass_chip
            )
            tv.setTextColor(if (selected) activeColor else normalColor)
        }
    }

    private fun openDanmakuPanel() {
        binding.playerView.hideController()
        binding.panelScrim.visibility = View.VISIBLE
        binding.panelDanmaku.visibility = View.VISIBLE
        binding.panelDanmaku.translationX = 340f * resources.displayMetrics.density
        binding.panelDanmaku.animate()
            .translationX(0f)
            .setDuration(220)
            .start()
    }

    private fun closeDanmakuPanel() {
        binding.panelScrim.visibility = View.GONE
        binding.panelDanmaku.animate()
            .translationX(340f * resources.displayMetrics.density)
            .setDuration(180)
            .withEndAction { binding.panelDanmaku.visibility = View.GONE }
            .start()
    }

    // ==================== 播放器初始化与兜底 ====================

    private fun setupPlayer() {
        if (url.isBlank()) return
        binding.loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val direct = SourceResolver.resolve(url)
            if (direct == null) {
                playInWebView()
            } else {
                startNative(direct)
            }
        }
    }

    private fun startNative(playUrl: String) {
        val p = ExoPlayer.Builder(this).build()
        p.setMediaItem(MediaItem.fromUri(playUrl))
        p.prepare()
        if (startPositionMs > 0) p.seekTo(startPositionMs)
        p.playWhenReady = true
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) binding.loading.visibility = View.GONE
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                playbackSpeed = playbackParameters.speed
                binding.danmakuView.setSpeed(danmakuConfig.speedScale * playbackSpeed)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    binding.danmakuView.onSeek(newPosition.positionMs)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                binding.loading.visibility = View.GONE
                Toast.makeText(
                    this@PlayerActivity,
                    "播放失败：${error.errorCodeName}（换条线路试试）",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
        binding.playerView.player = p
        binding.danmakuView.bindTimeProvider { p.currentPosition }
        binding.danmakuView.startLoop()
        player = p
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun playInWebView() {
        binding.loading.visibility = View.GONE
        binding.playerView.visibility = View.GONE
        binding.danmakuView.visibility = View.GONE
        binding.fallbackDanmakuBar.visibility = View.GONE
        binding.webPlayer.visibility = View.VISIBLE
        binding.webPlayer.setBackgroundColor(Color.BLACK)
        binding.webPlayer.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        binding.webPlayer.webChromeClient = WebChromeClient()
        binding.webPlayer.loadUrl(url)
        Toast.makeText(
            this, "该源需走网页播放器（无弹幕）", Toast.LENGTH_LONG
        ).show()
    }

    private fun loadDanmaku() {
        if (vodName.isBlank()) return
        lifecycleScope.launch {
            try {
                val episodeId = repo.matchDanmaku(vodName, episodeIndex + 1) ?: return@launch
                val xml = repo.getDanmakuXml(episodeId) ?: return@launch
                val items = withContext(Dispatchers.IO) {
                    BiliDanmakuParser.parse(xml)
                }
                binding.danmakuView.setSource(items)
            } catch (_: Exception) {
            }
        }
    }

    private fun recordHistory(positionMs: Long = startPositionMs) {
        lifecycleScope.launch {
            try {
                repo.recordHistory(vodId, siteKey, vodName, vodPic, episodeIndex, positionMs)
            } catch (_: Exception) {
            }
        }
    }

    override fun onPause() {
        super.onPause()
        player?.let {
            if (it.isPlaying) it.pause()
            val pos = it.currentPosition
            if (pos > 0) recordHistory(pos)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.danmakuView.stopLoop()
        player?.release()
        player = null
        binding.webPlayer.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
    }
}
