package com.beichen.dongguatv.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.beichen.dongguatv.MainActivity
import com.beichen.dongguatv.R
import com.beichen.dongguatv.data.ApiClient
import com.beichen.dongguatv.data.NameUtil
import com.beichen.dongguatv.data.Repository
import com.beichen.dongguatv.data.TmdbConst
import com.beichen.dongguatv.data.TmdbItem
import com.beichen.dongguatv.data.VodItem
import com.beichen.dongguatv.databinding.FragmentHomeBinding
import com.beichen.dongguatv.databinding.SectionVodRowBinding
import com.beichen.dongguatv.ui.detail.DetailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * 首页 —— Emby / Web 同款推荐流。
 *
 * 数据源与原版 Web 首页完全一致（TMDb 走国内代理 daili.smdhb.com）：
 *   Hero 轮播 = trending/all/week（取前 5 部有背景图的）
 *   四个榜单 = 热门电影 / 热门剧集 / 华语剧 / 日韩剧
 * 点击卡片 → 用片名调后端 tv.bcpan.icu 聚合搜索 → 命中同名源直接进详情页。
 *
 * 后端只有 search/detail/check/config 四个接口，没有推荐接口，
 * 所以推荐元数据跟 Web 一样从 TMDb 拉，播放源全部走用户自己的后端。
 *
 * 兜底：TMDb 代理完全不可用时，降级为内置热词搜后端（老方案），保证不白屏。
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val tmdbApi get() = ApiClient.tmdbApi
    private var bannerJob: Job? = null
    private var pageCallback: ViewPager2.OnPageChangeCallback? = null
    private var heroCount = 0

    /** 点击防抖：搜索没回来之前再点不叠加请求 */
    @Volatile private var searching = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 顶部悬浮栏避让状态栏
        val padBaseTop = (8 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.topFloatingBar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, top + padBaseTop, v.paddingRight, v.paddingBottom)
            insets
        }

        binding.tvSearchEntry.setOnClickListener {
            (requireActivity() as? MainActivity)?.switchToTab(1)
        }
        binding.refresh.setOnRefreshListener { loadHome() }
        loadHome()
    }

    // ==================== 数据加载 ====================

    private fun loadHome() {
        val appContext = requireContext().applicationContext
        binding.shelfContainer.removeAllViews()
        binding.tvEmpty.visibility = View.GONE
        binding.loading.visibility = View.VISIBLE
        binding.refresh.isRefreshing = true

        // 榜单定义：标题 + 拉取方式（与 Web fetchAllLists 一一对应）
        data class Shelf(val title: String, val fetch: suspend () -> List<TmdbItem>)

        val shelves = listOf(
            Shelf("全球热门 · 电影榜") { tmdbApi.trendingMovie(TmdbConst.API_KEY).results },
            Shelf("全球必追 · 剧集榜") { tmdbApi.trendingTv(TmdbConst.API_KEY).results },
            Shelf("华语强档 · 国产剧") { tmdbApi.discoverTv("CN", TmdbConst.API_KEY).results },
            Shelf("日韩潮流 · 剧集/动漫") { tmdbApi.discoverTv("KR|JP", TmdbConst.API_KEY).results }
        )

        // Hero 轮播：trending/all 里挑有背景图的前 5 部（Web fetchHero 同逻辑）
        viewLifecycleOwner.lifecycleScope.launch {
            val hero = withContext(Dispatchers.IO) {
                runCatching { tmdbApi.trendingAll(TmdbConst.API_KEY).results }
                    .getOrNull()
                    ?.filter { it.backdrop_path.isNotBlank() && it.displayName.isNotBlank() }
                    ?.take(5)
                    .orEmpty()
            }
            if (_binding == null) return@launch
            setupBanner(hero)
        }

        // 四个榜单错峰并发（每路隔 250ms，规避 TMDb 429 限流，Web 端同款做法）
        val total = shelves.size
        val done = AtomicInteger(0)
        val okCount = AtomicInteger(0)

        shelves.forEachIndexed { index, shelf ->
            viewLifecycleOwner.lifecycleScope.launch {
                delay(index * 250L)
                val items = withContext(Dispatchers.IO) {
                    runCatching { shelf.fetch() }.getOrNull().orEmpty()
                }.filter { it.poster_path.isNotBlank() && it.displayName.isNotBlank() }
                    .take(18)

                if (_binding == null) return@launch
                if (items.isNotEmpty()) {
                    okCount.incrementAndGet()
                    addShelf(shelf.title, items)
                }
                if (done.incrementAndGet() == total) {
                    binding.loading.visibility = View.GONE
                    binding.refresh.isRefreshing = false
                    // TMDb 一个榜单都没拉到 → 整个代理挂了，降级老方案（热词搜后端）
                    if (okCount.get() == 0) fallbackByKeywords(appContext)
                }
            }
        }
    }

    /** 加一个榜单行：标题 + 横向海报卡列表 */
    private fun addShelf(title: String, items: List<TmdbItem>) {
        val ctx = context ?: return
        val rowBinding = SectionVodRowBinding.inflate(
            layoutInflater, binding.shelfContainer, false
        )
        rowBinding.tvTitle.text = title
        rowBinding.rvRow.layoutManager =
            LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        rowBinding.rvRow.adapter = ShelfPosterAdapter(items) { searchAndOpen(it) }
        binding.shelfContainer.addView(rowBinding.root)
    }

    /**
     * 兜底降级：TMDb 代理不可用时，用内置热词并发搜用户自己的后端拼推荐流，
     * 保证首页永远有内容（这就是上一版的行为，仅作为断网兜底保留）。
     */
    private fun fallbackByKeywords(appContext: android.content.Context) {
        Toast.makeText(appContext, "推荐榜单拉取失败，已降级为关键词推荐", Toast.LENGTH_SHORT).show()
        val keywords = listOf("繁花", "狂飙", "三体", "庆余年", "墨雨云间")
        val done = AtomicInteger(0)
        val loaded = AtomicInteger(0)
        keywords.forEach { keyword ->
            viewLifecycleOwner.lifecycleScope.launch {
                val list: List<VodItem> = withContext(Dispatchers.IO) {
                    runCatching { Repository.get(appContext).search(keyword).list }
                        .getOrDefault(emptyList())
                }
                if (_binding == null) return@launch
                if (list.isNotEmpty()) {
                    loaded.incrementAndGet()
                    addKeywordRow(keyword, list)
                }
                if (done.incrementAndGet() == keywords.size && loaded.get() == 0) {
                    binding.tvEmpty.text = getString(R.string.home_no_result)
                    binding.tvEmpty.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun addKeywordRow(keyword: String, list: List<VodItem>) {
        val ctx = context ?: return
        val unique = list.distinctBy { NameUtil.key(it.vod_name) }.take(14)
        if (unique.isEmpty()) return
        val rowBinding = SectionVodRowBinding.inflate(
            layoutInflater, binding.shelfContainer, false
        )
        rowBinding.tvTitle.text = keyword
        rowBinding.rvRow.layoutManager =
            LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        rowBinding.rvRow.adapter = HorizontalVodAdapter(unique) { openDetail(it) }
        binding.shelfContainer.addView(rowBinding.root)
    }

    // ==================== Hero 轮播 ====================

    private fun setupBanner(items: List<TmdbItem>) {
        if (items.isEmpty()) {
            binding.bannerWrap.visibility = View.GONE
            return
        }
        binding.bannerWrap.visibility = View.VISIBLE

        binding.vpHero.adapter = HeroBannerAdapter(items) { searchAndOpen(it) }
        binding.vpHero.offscreenPageLimit = 1
        buildDots(items.size)

        heroCount = items.size
        buildDots(items.size)

        // 下拉刷新会重建 banner，先注销旧回调避免累积导致圆点错乱
        pageCallback?.let { binding.vpHero.unregisterOnPageChangeCallback(it) }
        pageCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position, heroCount)
            }
        }
        binding.vpHero.registerOnPageChangeCallback(pageCallback!!)

        // 自动轮播：6 秒一页（Web 端 carousel 同款间隔），仅在可见时滚动
        bannerJob?.cancel()
        bannerJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    delay(6000)
                    if (_binding == null) break
                    binding.vpHero.setCurrentItem(
                        (binding.vpHero.currentItem + 1) % items.size, true
                    )
                }
            }
        }
    }

    /** 底部圆点指示器 */
    private fun buildDots(count: Int) {
        val container = binding.dotsContainer
        container.removeAllViews()
        val margin = (4 * resources.displayMetrics.density).toInt()
        repeat(count) { i ->
            val dot = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = margin; marginEnd = margin }
                setImageResource(
                    if (i == 0) R.drawable.dot_active else R.drawable.dot_inactive
                )
            }
            container.addView(dot)
        }
    }

    private fun updateDots(active: Int, count: Int) {
        val container = binding.dotsContainer
        for (i in 0 until count) {
            (container.getChildAt(i) as? ImageView)?.setImageResource(
                if (i == active) R.drawable.dot_active else R.drawable.dot_inactive
            )
        }
    }

    // ==================== 点击播放入口 ====================

    /**
     * TMDb 只有元数据没有播放源。点击卡片后拿片名去用户后端聚合搜索，
     * 命中同名（NameUtil 归一化）的源里挑第一条直接进详情页 —— 详情页自带换源 chips。
     */
    private fun searchAndOpen(item: TmdbItem) {
        val title = item.displayName
        if (searching) return
        searching = true
        Toast.makeText(context, "正在搜索《$title》的播放源…", Toast.LENGTH_SHORT).show()

        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    runCatching { Repository.get(appContext).search(title).list }
                        .getOrDefault(emptyList())
                }
                val key = NameUtil.key(title)
                val match = list
                    .filter { NameUtil.key(it.vod_name) == key && it.vod_id.isNotBlank() }
                    .firstOrNull()
                if (_binding == null) return@launch
                if (match == null) {
                    Toast.makeText(context, "《$title》暂无可用播放线路", Toast.LENGTH_SHORT).show()
                } else {
                    openDetail(match)
                }
            } finally {
                searching = false
            }
        }
    }

    private fun openDetail(item: VodItem) {
        startActivity(Intent(requireContext(), DetailActivity::class.java).apply {
            putExtra("siteKey", item.site_key)
            putExtra("vodId", item.vod_id)
            putExtra("vodName", item.vod_name)
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bannerJob?.cancel()
        bannerJob = null
        _binding = null
    }
}
