package com.beichen.dongguatv.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.beichen.dongguatv.R
import com.beichen.dongguatv.data.NameUtil
import com.beichen.dongguatv.data.Repository
import com.beichen.dongguatv.data.VodItem
import com.beichen.dongguatv.databinding.FragmentHomeBinding
import com.beichen.dongguatv.databinding.SectionVodRowBinding
import com.beichen.dongguatv.ui.detail.DetailActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * 首页推荐流。
 *
 * 后端（tv.bcpan.icu）只有 /api/search、/api/detail、/api/check、/api/config 四个接口，
 * 没有 /api/hot 这类列表接口。所以首页改用「内置热门词并发搜索」拼出推荐流：
 * 每个热词单独一个协程独立加载、独立渲染，谁先回来先显示，不互相阻塞。
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    /** 内置推荐词（后端 CDN 已缓存时 0.3s 返回） */
    private val hotKeywords = listOf("繁花", "狂飙", "三体", "与凤行", "庆余年", "墨雨云间")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvSearchEntry.setOnClickListener {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNav)
                ?.selectedItemId = R.id.nav_search
        }
        binding.refresh.setOnRefreshListener { loadRecommend() }
        loadRecommend()
    }

    private fun loadRecommend() {
        val container = binding.sectionContainer
        container.removeAllViews()
        binding.tvEmpty.visibility = View.GONE
        binding.loading.visibility = View.VISIBLE
        binding.refresh.isRefreshing = true

        val appContext = requireContext().applicationContext
        val total = hotKeywords.size
        val done = AtomicInteger(0)
        val loaded = AtomicInteger(0)

        hotKeywords.forEach { keyword ->
            viewLifecycleOwner.lifecycleScope.launch {
                val list: List<VodItem> = withContext(Dispatchers.IO) {
                    runCatching { Repository.get(appContext).search(keyword).list }
                        .getOrDefault(emptyList())
                }
                if (_binding == null) return@launch

                if (list.isNotEmpty()) {
                    loaded.incrementAndGet()
                    addSection(keyword, list)
                    binding.loading.visibility = View.GONE
                }
                if (done.incrementAndGet() == total) {
                    binding.loading.visibility = View.GONE
                    binding.refresh.isRefreshing = false
                    if (loaded.get() == 0) {
                        binding.tvEmpty.text = getString(R.string.home_no_result)
                        binding.tvEmpty.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    /** 加一行推荐：标题 + 横向海报列表 */
    private fun addSection(keyword: String, list: List<VodItem>) {
        val ctx = context ?: return
        // 按归一化剧名去重：否则「繁花 国语版 / 沪语版 / 剧版」会占满一行
        val unique = list.distinctBy { NameUtil.key(it.vod_name) }.take(14)
        if (unique.isEmpty()) return

        val rowBinding = SectionVodRowBinding.inflate(
            layoutInflater, binding.sectionContainer, false
        )
        rowBinding.tvTitle.text = keyword
        rowBinding.rvRow.layoutManager =
            LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        rowBinding.rvRow.adapter = HorizontalVodAdapter(unique) { openDetail(it) }
        binding.sectionContainer.addView(rowBinding.root)
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
        _binding = null
    }
}
