package com.beichen.dongguatv.ui.detail

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.beichen.dongguatv.data.PlayUrlParser
import com.beichen.dongguatv.data.Repository
import com.beichen.dongguatv.data.VodDetail
import com.beichen.dongguatv.data.VodItem
import com.beichen.dongguatv.databinding.ActivityDetailBinding
import com.beichen.dongguatv.ui.player.PlayerActivity
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val repo by lazy { Repository.get(this) }
    private val siteKey by lazy { intent.getStringExtra("siteKey").orEmpty() }
    private val vodId by lazy { intent.getStringExtra("vodId").orEmpty() }
    private val vodName by lazy { intent.getStringExtra("vodName").orEmpty() }

    private var detail: VodDetail? = null
    private var playLines: List<PlayUrlParser.PlayLine> = emptyList()
    private var currentLine: Int = 0
    private val episodeAdapter = EpisodeAdapter { ep ->
        play(ep.index)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = vodName
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.gridEpisodes.layoutManager = GridLayoutManager(this, 4)
        binding.gridEpisodes.adapter = episodeAdapter

        binding.tabLines.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentLine = tab.position
                episodeAdapter.submit(playLines.getOrNull(currentLine)?.episodes.orEmpty())
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        binding.btnPlay.setOnClickListener { play(0) }
        binding.btnFavorite.setOnClickListener { toggleFavorite() }
        binding.btnFollow.setOnClickListener { toggleFollow() }

        loadDetail()
    }

    private fun loadDetail() {
        binding.loading.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            try {
                val resp = repo.detail(siteKey, vodId)
                val d = resp.list.firstOrNull() ?: return@launch
                detail = d
                bindDetail(d)
                playLines = PlayUrlParser.parse(d.vod_play_url)
                binding.tabLines.removeAllTabs()
                playLines.forEachIndexed { i, line ->
                    binding.tabLines.addTab(binding.tabLines.newTab().setText(line.name))
                }
                if (playLines.isNotEmpty()) {
                    episodeAdapter.submit(playLines[0].episodes)
                }
            } catch (e: Exception) {
                // 加载失败静默
            } finally {
                binding.loading.visibility = android.view.View.GONE
            }
        }
    }

    private fun bindDetail(d: VodDetail) {
        binding.tvTitle.text = d.vod_name
        binding.tvMeta.text = listOf(d.vod_year, d.vod_area)
            .filter { it.isNotBlank() }.joinToString(" · ")
        binding.tvRemarks.text = d.vod_remarks
        binding.tvContent.text = d.vod_content.ifBlank { "暂无简介" }
        Glide.with(this).load(d.vod_pic).into(binding.ivPoster)
        // 初始化收藏/追剧按钮状态
        lifecycleScope.launch {
            val fav = repo.isFavorite(d.vod_id, siteKey)
            binding.btnFavorite.text = if (fav) "已收藏" else "收藏"
            val fol = repo.isFollowed(d.vod_id, siteKey)
            binding.btnFollow.text = if (fol) "已追剧" else "追剧"
        }
    }

    private fun play(episodeIndex: Int) {
        val line = playLines.getOrNull(currentLine) ?: playLines.firstOrNull() ?: return
        val ep = line.episodes.getOrNull(episodeIndex) ?: return
        val d = detail ?: return
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("url", ep.url)
            putExtra("vodName", d.vod_name)
            putExtra("episodeIndex", ep.index)
            putExtra("siteKey", siteKey)
            putExtra("vodId", d.vod_id)
            putExtra("vodPic", d.vod_pic)
        })
    }

    private fun toggleFavorite() {
        val d = detail ?: return
        lifecycleScope.launch {
            repo.toggleFavorite(toItem(d))
            binding.btnFavorite.text =
                if (repo.isFavorite(d.vod_id, siteKey)) "已收藏" else "收藏"
        }
    }

    private fun toggleFollow() {
        val d = detail ?: return
        lifecycleScope.launch {
            repo.toggleFollow(toItem(d))
            binding.btnFollow.text =
                if (repo.isFollowed(d.vod_id, siteKey)) "已追剧" else "追剧"
        }
    }

    private fun toItem(d: VodDetail) = VodItem(
        vod_id = d.vod_id, vod_name = d.vod_name,
        vod_pic = d.vod_pic, vod_remarks = d.vod_remarks, site_key = siteKey
    )
}
