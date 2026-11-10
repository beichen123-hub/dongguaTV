package com.beichen.dongguatv.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.beichen.dongguatv.data.Repository
import com.beichen.dongguatv.data.VodItem
import com.beichen.dongguatv.databinding.FragmentHomeBinding
import com.beichen.dongguatv.ui.detail.DetailActivity
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val adapter = VodGridAdapter { item -> openDetail(item) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.grid.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.grid.adapter = adapter
        binding.refresh.setOnRefreshListener { loadHot() }
        loadHot()
    }

    private fun loadHot() {
        binding.refresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = Repository.get(requireContext()).hot()
                adapter.submit(resp.list)
            } catch (e: Exception) {
                // 静默失败，热门接口偶尔超时
            } finally {
                binding.refresh.isRefreshing = false
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
        _binding = null
    }
}
