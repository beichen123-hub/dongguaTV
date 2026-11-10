package com.beichen.dongguatv.ui.player

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.beichen.dongguatv.data.Repository
import com.beichen.dongguatv.data.SourceResolver
import com.beichen.dongguatv.databinding.ActivityPlayerBinding
import com.beichen.dongguatv.player.danmaku.BiliDanmakuParser
import com.beichen.dongguatv.player.danmaku.DanmakuView
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private val repo by lazy { Repository.get(this) }

    private var player: ExoPlayer? = null
    private var danmakuOn: Boolean = true

    private val url by lazy { intent.getStringExtra("url").orEmpty() }
    private val vodName by lazy { intent.getStringExtra("vodName").orEmpty() }
    private val episodeIndex by lazy { intent.getIntExtra("episodeIndex", 0) }
    /** 上次看到这一集的第几毫秒，>0 就接着播 */
    private val startPositionMs by lazy { intent.getLongExtra("startPositionMs", 0L) }
    private val siteKey by lazy { intent.getStringExtra("siteKey").orEmpty() }
    private val vodId by lazy { intent.getStringExtra("vodId").orEmpty() }
    private val vodPic by lazy { intent.getStringExtra("vodPic").orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // 横屏全屏沉浸：藏状态栏/导航栏，划一下临时唤出
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, binding.root).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        binding.playerView.keepScreenOn = true
        binding.btnDanmaku.setOnClickListener {
            danmakuOn = binding.danmakuView.toggle()
            binding.btnDanmaku.text = if (danmakuOn) "弹幕开" else "弹幕关"
        }

        setupPlayer()
        loadDanmaku()
        recordHistory()
    }

    private fun setupPlayer() {
        if (url.isBlank()) return
        binding.loading.visibility = View.VISIBLE
        // 采集源多数给的是分享页，先解析出 m3u8 直链再喂 ExoPlayer
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

            // 拖动进度条后弹幕时间轴要跟着重排，否则往前拖就没有弹幕了
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    binding.danmakuView.onSeek(newPosition.positionMs)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                binding.loading.visibility = View.GONE
                android.widget.Toast.makeText(
                    this@PlayerActivity,
                    "播放失败：${error.errorCodeName}（换条线路试试）",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        })
        binding.playerView.player = p
        // 弹幕时间轴绑定到播放器
        binding.danmakuView.bindTimeProvider { p.currentPosition }
        binding.danmakuView.startLoop()
        player = p
    }

    /** 解析不出直链的源：用内置浏览器打开原播放页，页面自带 hls.js 能播（无弹幕） */
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun playInWebView() {
        binding.loading.visibility = View.GONE
        binding.playerView.visibility = View.GONE
        binding.danmakuView.visibility = View.GONE
        binding.btnDanmaku.visibility = View.GONE
        binding.webPlayer.visibility = View.VISIBLE
        binding.webPlayer.setBackgroundColor(android.graphics.Color.BLACK)
        binding.webPlayer.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        binding.webPlayer.webChromeClient = android.webkit.WebChromeClient()
        binding.webPlayer.loadUrl(url)
        android.widget.Toast.makeText(
            this, "这个源要给播放页才能播，已切内置浏览器（无弹幕）", android.widget.Toast.LENGTH_LONG
        ).show()
    }

    private fun loadDanmaku() {
        if (vodName.isBlank()) return
        lifecycleScope.launch {
            try {
                val episodeId = repo.matchDanmaku(vodName, episodeIndex + 1) ?: return@launch
                val xml = repo.getDanmakuXml(episodeId) ?: return@launch
                // 4 万条 XML 解析必须离开主线程，否则进播放器会卡 1-2 秒
                val items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    BiliDanmakuParser.parse(xml)
                }
                binding.danmakuView.setSource(items)
            } catch (e: Exception) {
                // 弹幕加载失败不影响播放
            }
        }
    }

    private fun recordHistory(positionMs: Long = startPositionMs) {
        lifecycleScope.launch {
            try {
                repo.recordHistory(vodId, siteKey, vodName, vodPic, episodeIndex, positionMs)
            } catch (e: Exception) {}
        }
    }

    override fun onPause() {
        super.onPause()
        // 退出/切后台时把进度存下来，下次「继续播放」才能真的接着播
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
        // WebView 兜底模式要显式销毁，否则音频可能还在后台响
        binding.webPlayer.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
    }
}
