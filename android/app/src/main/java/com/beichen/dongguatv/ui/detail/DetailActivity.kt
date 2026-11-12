package com.beichen.dongguatv.ui.detail

import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.beichen.dongguatv.R
import com.beichen.dongguatv.data.Episode
import com.beichen.dongguatv.data.NameUtil
import com.beichen.dongguatv.data.PlayUrlParser
import com.beichen.dongguatv.data.Repository
import com.beichen.dongguatv.data.VodDetail
import com.beichen.dongguatv.data.VodItem
import com.beichen.dongguatv.databinding.ActivityDetailBinding
import com.beichen.dongguatv.ui.player.PlayerActivity
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val repo by lazy { Repository.get(this) }

    private var siteKey: String = ""
    private var vodId: String = ""
    private var vodName: String = ""

    private var detail: VodDetail? = null
    private var episodes: List<Episode> = emptyList()
    private var reverseOrder: Boolean = false
    private var synopsisExpanded: Boolean = false

    /** 上次看到第几集（0 起），用于「继续播放」与当前集高亮 */
    private var resumeIndex: Int = 0
    private var resumePositionMs: Long = 0

    private val episodeAdapter = EpisodeAdapter { ep ->
        play(ep.index)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        siteKey = intent.getStringExtra("siteKey").orEmpty()
        vodId = intent.getStringExtra("vodId").orEmpty()
        vodName = intent.getStringExtra("vodName").orEmpty()

        // 悬浮返回按钮根据状态栏高度动态留白
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnBack) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val lp = v.layoutParams as android.widget.FrameLayout.LayoutParams
            lp.topMargin = top + (10 * resources.displayMetrics.density).toInt()
            v.layoutParams = lp
            insets
        }
        binding.btnBack.setOnClickListener { finish() }

        binding.gridEpisodes.layoutManager = GridLayoutManager(this, 4)
        binding.gridEpisodes.adapter = episodeAdapter

        binding.btnPlay.setOnClickListener { play(resumeIndex) }
        binding.btnFavorite.setOnClickListener { toggleFavorite() }
        binding.btnFollow.setOnClickListener { toggleFollow() }
        binding.tvError.setOnClickListener { loadDetail() }

        binding.btnSortOrder.setOnClickListener {
            reverseOrder = !reverseOrder
            binding.tvSortOrder.text = if (reverseOrder) "倒序" else "正序"
            renderEpisodes()
        }

        binding.cardSynopsis.setOnClickListener {
            if (binding.tvToggleSynopsis.visibility == View.VISIBLE) {
                synopsisExpanded = !synopsisExpanded
                binding.tvContent.maxLines = if (synopsisExpanded) 60 else 3
                binding.tvToggleSynopsis.text = if (synopsisExpanded) "收起" else "展开"
            }
        }

        // Android 12+ 给背景海报加真高斯模糊，铺垫 Emby 液态玻璃质感
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.ivBackdrop.setRenderEffect(
                RenderEffect.createBlurEffect(42f, 42f, Shader.TileMode.CLAMP)
            )
        }

        loadDetail()
    }

    override fun onResume() {
        super.onResume()
        // 从播放器回来后刷新一次续播进度与高亮集数
        val d = detail ?: return
        lifecycleScope.launch {
            refreshResumeState(d.vod_id)
            renderEpisodes()
        }
    }

    private fun loadDetail() {
        binding.loading.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
        resumeIndex = 0
        resumePositionMs = 0
        binding.btnPlay.text = "立即播放"

        lifecycleScope.launch {
            try {
                val resp = repo.detail(siteKey, vodId)
                val d = resp.list.firstOrNull()
                if (d == null) {
                    showError("该资源在「$siteKey」不可用，点击其它播放源试试")
                    return@launch
                }
                detail = d
                bindDetail(d)

                // 只在后台自动优选最稳的一条线路（m3u8 优先，其次集数最多），不再显示冗余的 1 处线路 Tab
                val lines = PlayUrlParser.parse(d.vod_play_url, d.vod_play_from)
                val bestLine = PlayUrlParser.pickBestLine(lines)
                episodes = bestLine?.episodes.orEmpty()

                if (episodes.isEmpty()) {
                    showError("解析不到播放地址，换个源试试")
                }
                refreshResumeState(d.vod_id)
                renderEpisodes()
                loadSourceChips(d)
            } catch (e: Exception) {
                showError("加载失败：${e.message ?: "网络异常"}\n点这里重试")
            } finally {
                binding.loading.visibility = View.GONE
            }
        }
    }

    private suspend fun refreshResumeState(vodId: String) {
        val hist = repo.historyOf(vodId, siteKey)
        if (hist != null && (hist.episodeIndex > 0 || hist.positionMs > 0)) {
            resumeIndex = hist.episodeIndex.coerceIn(0, (episodes.size - 1).coerceAtLeast(0))
            resumePositionMs = hist.positionMs
            binding.btnPlay.text = "继续播放 第${resumeIndex + 1}集"
        } else {
            resumeIndex = 0
            resumePositionMs = 0
            binding.btnPlay.text = "立即播放"
        }
    }

    private fun renderEpisodes() {
        val list = if (reverseOrder) episodes.reversed() else episodes
        val highlightIdx = if (resumePositionMs > 0 || resumeIndex > 0) resumeIndex else -1
        episodeAdapter.submit(list, highlightIdx)
        binding.tvEpisodeTotal.text = if (episodes.isNotEmpty()) "共 ${episodes.size} 集" else ""
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }

    private fun bindDetail(d: VodDetail) {
        binding.tvTitle.text = NameUtil.display(d.vod_name).ifBlank { d.vod_name }
        val meta = listOf(d.vod_year, d.vod_area).filter { it.isNotBlank() }.joinToString(" · ")
        if (meta.isNotBlank()) {
            binding.tvMeta.text = meta
            binding.tvMeta.visibility = View.VISIBLE
        } else {
            binding.tvMeta.visibility = View.GONE
        }
        binding.tvRemarks.text = d.vod_remarks.ifBlank { "全片源已就绪" }

        // 彻底清洗 <p>、&nbsp;、<br> 等采集站残留 HTML 标签
        val cleanSummary = HtmlCompat.fromHtml(
            d.vod_content.ifBlank { "暂无简介" },
            HtmlCompat.FROM_HTML_MODE_COMPACT
        ).toString().replace('\u00A0', ' ').trim()
        synopsisExpanded = false
        binding.tvContent.maxLines = 3
        binding.tvContent.text = cleanSummary.ifBlank { "暂无简介" }
        binding.tvContent.post {
            val truncated = (binding.tvContent.layout?.lineCount ?: 0) > 3 ||
                    cleanSummary.length > 80
            binding.tvToggleSynopsis.visibility = if (truncated) View.VISIBLE else View.GONE
            binding.tvToggleSynopsis.text = "展开"
        }

        Glide.with(this).load(d.vod_pic)
            .placeholder(R.color.colorSurfaceVariant)
            .error(R.color.colorSurfaceVariant)
            .into(binding.ivPoster)
        Glide.with(this).load(d.vod_pic)
            .placeholder(R.color.colorBackground)
            .error(R.color.colorBackground)
            .into(binding.ivBackdrop)

        lifecycleScope.launch {
            syncFavFollowUi(d.vod_id)
        }
    }

    private suspend fun syncFavFollowUi(id: String) {
        val fav = repo.isFavorite(id, siteKey)
        binding.tvFavText.text = if (fav) "已收藏" else "收藏"
        binding.ivFavIcon.setImageResource(
            if (fav) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
        )
        binding.btnFavorite.setBackgroundResource(
            if (fav) R.drawable.bg_glass_chip_active else R.drawable.bg_glass_chip
        )

        val fol = repo.isFollowed(id, siteKey)
        binding.tvFollowText.text = if (fol) "已追剧" else "追剧"
        binding.btnFollow.setBackgroundResource(
            if (fol) R.drawable.bg_glass_chip_active else R.drawable.bg_glass_chip
        )
    }

    private fun play(episodeIndex: Int) {
        val ep = episodes.getOrNull(episodeIndex) ?: episodes.firstOrNull() ?: return
        val d = detail ?: return
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("url", ep.url)
            putExtra("vodName", NameUtil.display(d.vod_name).ifBlank { d.vod_name })
            putExtra("episodeIndex", ep.index)
            putExtra("siteKey", siteKey)
            putExtra("vodId", d.vod_id)
            putExtra("vodPic", d.vod_pic)
            putExtra(
                "startPositionMs",
                if (ep.index == resumeIndex) resumePositionMs else 0L
            )
        })
    }

    /**
     * 播放源（即原图 2 处，唯一的切换入口）：搜同名剧，把各个采集站的源列成液态玻璃胶囊。
     */
    private fun loadSourceChips(item: VodDetail) {
        binding.sourceRow.removeAllViews()
        binding.sourceSection.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val key = NameUtil.key(item.vod_name)
                val others = repo.search(item.vod_name).list
                    .filter { NameUtil.key(it.vod_name) == key }
                    .distinctBy { it.site_key }
                if (others.isEmpty()) return@launch
                binding.sourceSection.visibility = View.VISIBLE
                others.forEach { vod ->
                    binding.sourceRow.addView(buildSourceChip(vod, vod.site_key == siteKey))
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun buildSourceChip(vod: VodItem, isCurrent: Boolean): View {
        val density = resources.displayMetrics.density
        val chip = TextView(this)
        chip.text = vod.site_name.ifBlank { vod.site_key }
        chip.textSize = 12f
        chip.setPadding(
            (14 * density).toInt(),
            (8 * density).toInt(),
            (14 * density).toInt(),
            (8 * density).toInt()
        )
        chip.setBackgroundResource(
            if (isCurrent) R.drawable.bg_glass_chip_active else R.drawable.bg_glass_chip
        )
        chip.setTextColor(
            ContextCompat.getColor(
                this,
                if (isCurrent) R.color.colorPrimaryLight else R.color.textPrimary
            )
        )
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.marginEnd = (8 * density).toInt()
        chip.layoutParams = lp
        chip.setOnClickListener { if (!isCurrent) switchSource(vod) }
        return chip
    }

    private fun switchSource(vod: VodItem) {
        siteKey = vod.site_key
        vodId = vod.vod_id
        vodName = vod.vod_name
        episodes = emptyList()
        episodeAdapter.submit(emptyList())
        loadDetail()
        Toast.makeText(
            this,
            "已切换至 ${vod.site_name.ifBlank { vod.site_key }}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleFavorite() {
        val d = detail ?: return
        lifecycleScope.launch {
            repo.toggleFavorite(toItem(d))
            syncFavFollowUi(d.vod_id)
        }
    }

    private fun toggleFollow() {
        val d = detail ?: return
        val total = episodes.size.coerceAtLeast(1)
        lifecycleScope.launch {
            repo.toggleFollow(toItem(d), total)
            syncFavFollowUi(d.vod_id)
        }
    }

    private fun toItem(d: VodDetail) = VodItem(
        vod_id = d.vod_id, vod_name = d.vod_name,
        vod_pic = d.vod_pic, vod_remarks = d.vod_remarks, site_key = siteKey
    )
}
