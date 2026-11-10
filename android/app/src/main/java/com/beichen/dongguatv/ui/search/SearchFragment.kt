package com.beichen.dongguatv.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.beichen.dongguatv.R
import com.beichen.dongguatv.data.Repository
import com.beichen.dongguatv.data.VodItem
import com.beichen.dongguatv.databinding.FragmentSearchBinding
import com.beichen.dongguatv.ui.detail.DetailActivity
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val adapter = SearchResultAdapter { item -> openDetail(item) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.tvEmpty.visibility = View.VISIBLE

        binding.etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { doSearch(); true } else false
        }
        binding.btnSearch.setOnClickListener { doSearch() }
    }

    private fun doSearch() {
        val wd = binding.etQuery.text?.toString()?.trim().orEmpty()
        if (wd.isBlank()) return
        binding.tvEmpty.visibility = View.GONE
        binding.loading.visibility = View.VISIBLE
        adapter.submit(emptyList())
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = Repository.get(requireContext()).search(wd)
                adapter.submit(resp.list)
                if (resp.list.isEmpty()) {
                    binding.tvEmpty.text = "没有找到「$wd」"
                    binding.tvEmpty.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                binding.tvEmpty.text = getString(R.string.msg_load_fail)
                binding.tvEmpty.visibility = View.VISIBLE
            } finally {
                binding.loading.visibility = View.GONE
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
