package com.beichen.dongguatv.ui.player

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.beichen.dongguatv.data.Repository
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
    private val siteKey by lazy { intent.getStringExtra("siteKey").orEmpty() }
    private val vodId by lazy { intent.getStringExtra("vodId").orEmpty() }
    private val vodPic by lazy { intent.getStringExtra("vodPic").orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
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
        val p = ExoPlayer.Builder(this).build()
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
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

    private fun loadDanmaku() {
        if (vodName.isBlank()) return
        lifecycleScope.launch {
            try {
                val episodeId = repo.matchDanmaku(vodName, episodeIndex + 1) ?: return@launch
                val xml = repo.getDanmakuXml(episodeId) ?: return@launch
                val items = BiliDanmakuParser.parse(xml)
                binding.danmakuView.setSource(items)
            } catch (e: Exception) {
                // 弹幕加载失败不影响播放
            }
        }
    }

    private fun recordHistory() {
        lifecycleScope.launch {
            try {
                repo.recordHistory(vodId, siteKey, vodName, vodPic, episodeIndex)
            } catch (e: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.danmakuView.stopLoop()
        player?.release()
        player = null
    }
}
