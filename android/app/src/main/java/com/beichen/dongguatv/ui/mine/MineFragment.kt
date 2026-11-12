package com.beichen.dongguatv.ui.mine

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.beichen.dongguatv.data.Repository
import com.beichen.dongguatv.databinding.FragmentMineBinding
import com.beichen.dongguatv.ui.favorite.FavoriteActivity
import com.beichen.dongguatv.ui.follow.FollowActivity
import com.beichen.dongguatv.ui.history.HistoryActivity
import kotlinx.coroutines.launch

class MineFragment : Fragment() {

    private var _binding: FragmentMineBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val baseTop = (12 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.mineRoot) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, top + baseTop, v.paddingRight, v.paddingBottom)
            insets
        }
        val repo = Repository.get(requireContext())
        repo.observeFavorites().observe(viewLifecycleOwner) {
            binding.tvFavCount.text = it.size.toString()
        }
        repo.observeHistory().observe(viewLifecycleOwner) {
            binding.tvHisCount.text = it.size.toString()
        }
        repo.observeFollows().observe(viewLifecycleOwner) {
            binding.tvFollowCount.text = "${it.size} 部"
        }
        binding.cardFavorite.setOnClickListener {
            startActivity(Intent(requireContext(), FavoriteActivity::class.java))
        }
        binding.cardHistory.setOnClickListener {
            startActivity(Intent(requireContext(), HistoryActivity::class.java))
        }
        binding.cardFollow.setOnClickListener {
            startActivity(Intent(requireContext(), FollowActivity::class.java))
        }
        binding.cardDiag.setOnClickListener { runSelfCheck() }
    }

    /**
     * 服务自检：播不了的时候先看这里，能区分是「后端挂了」「弹幕挂了」还是「源的问题」，
     * 不用靠猜。
     */
    private fun runSelfCheck() {
        val ctx = requireContext()
        Toast.makeText(ctx, "检测中…", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val sb = StringBuilder()

            sb.append("版本：")
            try {
                val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                sb.append("${pkg.versionName} (${pkg.versionCode})\n")
            } catch (e: Exception) {
                sb.append("读取失败\n")
            }

            val repo = Repository.get(ctx)

            // 影视后端：拿一次搜索当探针
            val t0 = System.currentTimeMillis()
            var searchCount = -1
            var searchErr = ""
            try {
                searchCount = repo.search("繁花").list.size
            } catch (e: Exception) {
                searchErr = e.javaClass.simpleName + ": " + (e.message ?: "").take(60)
            }
            val cost = System.currentTimeMillis() - t0
            if (searchCount >= 0) {
                sb.append("影视后端：正常，搜索「繁花」返回 $searchCount 条（${cost}ms）\n")
            } else {
                sb.append("影视后端：失败（${cost}ms）\n  $searchErr\n")
            }

            // 弹幕服务：match 一次
            var matchId: String? = null
            var matchErr = ""
            try {
                matchId = repo.matchDanmaku("繁花", 1)
            } catch (e: Exception) {
                matchErr = e.javaClass.simpleName + ": " + (e.message ?: "").take(60)
            }
            if (matchId != null) {
                sb.append("弹幕服务：正常，匹配到 episodeId=$matchId\n")
            } else {
                sb.append("弹幕服务：未匹配到\n")
                if (matchErr.isNotBlank()) sb.append("  $matchErr\n")
                sb.append("  （端口 19321 没开、或服务没起来都会这样）\n")
            }

            sb.append("\n播放失败多半是「源」的问题：回详情页点其它源试试。")

            if (!isAdded) return@launch
            AlertDialog.Builder(requireContext())
                .setTitle("服务自检")
                .setMessage(sb.toString())
                .setPositiveButton("好", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
