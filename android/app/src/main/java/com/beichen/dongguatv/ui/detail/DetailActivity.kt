package com.beichen.dongguatv.ui.detail

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.beichen.dongguatv.R
import com.beichen.dongguatv.data.NameUtil
import com.beichen.dongguatv.data.PlayLine
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

    // 换源要能改，所以这几个不能是 val
    private var siteKey: String = ""
    private var vodId: String = ""
    private var vodName: String = ""

    private var detail: VodDetail? = null
    private var playLines: List<PlayLine> = emptyList()
    private var currentLine: Int = 0

    /** 上次看到第几集（0 起），用于「继续播放」 */
    private var resumeIndex: Int = 0
    private val episodeAdapter = EpisodeAdapter { ep ->
        play(ep.index)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        siteKey = intent.getStringExtra("siteKey").orEmpty()
        vodId = intent.getStringExtra("vodId").orEmpty()
        vodName = intent.getStringExtra("vodName").orEmpty()
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

        binding.btnPlay.setOnClickListener { play(resumeIndex) }
        binding.btnFavorite.setOnClickListener { toggleFavorite() }
        binding.btnFollow.setOnClickListener { toggleFollow() }
        binding.tvError.setOnClickListener { loadDetail() }

        loadDetail()
    }

    private fun loadDetail() {
        binding.loading.visibility = android.view.View.VISIBLE
        binding.tvError.visibility = android.view.View.GONE
        resumeIndex = 0
        binding.btnPlay.text = "立即播放"
        lifecycleScope.launch {
            try {
                val resp = repo.detail(siteKey, vodId)
                val d = resp.list.firstOrNull()
                if (d == null) {
                    showError("该资源在「$siteKey」不可用，返回换一个源试试")
                    return@launch
                }
                detail = d
                bindDetail(d)
                playLines = PlayUrlParser.parse(d.vod_play_url, d.vod_play_from)
                binding.tabLines.removeAllTabs()
                playLines.forEachIndexed { i, line ->
                    binding.tabLines.addTab(binding.tabLines.newTab().setText(line.name))
                }
                if (playLines.isNotEmpty()) {
                    episodeAdapter.submit(playLines[0].episodes)
                }
                if (playLines.isEmpty() || playLines.all { it.episodes.isEmpty() }) {
                    showError("解析不到播放地址，换个源试试")
                }
                // 有播放记录就把主按钮改成「继续播放 第 N 集」
                val lastEp = repo.historyOf(d.vod_id, siteKey)?.episodeIndex ?: 0
                if (lastEp > 0) {
                    resumeIndex = lastEp
                    binding.btnPlay.text = "继续播放 第${lastEp + 1}集"
                }
                loadSourceChips(d)
            } catch (e: Exception) {
                showError("加载失败：${e.message ?: "网络异常"}\n点这里重试")
            } finally {
                binding.loading.visibility = android.view.View.GONE
            }
        }
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = android.view.View.VISIBLE
    }

    private fun bindDetail(d: VodDetail) {
        binding.tvTitle.text = d.vod_name
        binding.tvMeta.text = listOf(d.vod_year, d.vod_area)
            .filter { it.isNotBlank() }.joinToString(" · ")
        binding.tvRemarks.text = d.vod_remarks
        binding.tvContent.text = d.vod_content.ifBlank { "暂无简介" }
        Glide.with(this).load(d.vod_pic)
            .placeholder(R.color.colorSurfaceVariant)
            .error(R.color.colorSurfaceVariant)
            .into(binding.ivPoster)
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

    /**
     * 换源：搜同名剧，把其它站点的可用源列成一行 chips。
     * 一条源播不动时，直接在这里换，比退回列表重新搜快得多。
     */
    private fun loadSourceChips(item: VodDetail) {
        binding.sourceRow.removeAllViews()
        binding.sourceRow.visibility = android.view.View.GONE
        lifecycleScope.launch {
            try {
                val key = NameUtil.key(item.vod_name)
                val others = repo.search(item.vod_name).list
                    .filter { NameUtil.key(it.vod_name) == key }
                    .distinctBy { it.site_key }
                if (others.size <= 1) return@launch
                binding.sourceRow.visibility = android.view.View.VISIBLE
                others.forEach { vod ->
                    binding.sourceRow.addView(buildSourceChip(vod, vod.site_key == siteKey))
                }
            } catch (e: Exception) {
                // 换源列表拉不到不影响看剧
            }
        }
    }

    private fun buildSourceChip(vod: VodItem, isCurrent: Boolean): android.view.View {
        val density = resources.displayMetrics.density
        val chip = com.google.android.material.button.MaterialButton(this)
        chip.text = vod.site_name.ifBlank { vod.site_key }
        chip.textSize = 12f
        chip.isAllCaps = false
        chip.minWidth = 0
        chip.insetTop = 0
        chip.insetBottom = 0
        chip.setPaddingRelative((10 * density).toInt(), 0, (10 * density).toInt(), 0)
        val lp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.marginEnd = (8 * density).toInt()
        chip.layoutParams = lp
        val primary = androidx.core.content.ContextCompat.getColor(this, R.color.colorPrimary)
        val surface = androidx.core.content.ContextCompat.getColor(this, R.color.colorSurface)
        val onSurface = androidx.core.content.ContextCompat.getColor(this, R.color.textPrimary)
        chip.backgroundTintList =
            android.content.res.ColorStateList.valueOf(if (isCurrent) primary else surface)
        chip.setTextColor(if (isCurrent) android.graphics.Color.WHITE else onSurface)
        chip.strokeWidth = (1 * density).toInt()
        chip.strokeColor = android.content.res.ColorStateList.valueOf(primary)
        chip.setOnClickListener { if (!isCurrent) switchSource(vod) }
        return chip
    }

    private fun switchSource(vod: VodItem) {
        siteKey = vod.site_key
        vodId = vod.vod_id
        vodName = vod.vod_name
        title = vodName
        playLines = emptyList()
        currentLine = 0
        binding.tabLines.removeAllTabs()
        episodeAdapter.submit(emptyList())
        loadDetail()
        android.widget.Toast.makeText(
            this, "已切到 ${vod.site_name.ifBlank { vod.site_key }}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
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
        // 首次追剧就把「当前已更新到第几集」记下来，否则下次检查会把已有集数全当新更新、发一堆通知
        val total = playLines.maxOfOrNull { it.episodes.size } ?: 1
        lifecycleScope.launch {
            repo.toggleFollow(toItem(d), total)
            binding.btnFollow.text =
                if (repo.isFollowed(d.vod_id, siteKey)) "已追剧" else "追剧"
        }
    }

    private fun toItem(d: VodDetail) = VodItem(
        vod_id = d.vod_id, vod_name = d.vod_name,
        vod_pic = d.vod_pic, vod_remarks = d.vod_remarks, site_key = siteKey
    )
}
