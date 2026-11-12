package com.beichen.dongguatv.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    private val hotKeywords = listOf(
        "庆余年", "繁花", "狂飙", "三体",
        "唐朝诡事录", "墨雨云间", "周处除三害", "沙丘2"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val baseTop = (12 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.searchHeader) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, top + baseTop, v.paddingRight, v.paddingBottom)
            insets
        }

        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        buildHotTags()

        binding.etQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val empty = s.isNullOrBlank()
                binding.btnClearQuery.visibility = if (empty) View.GONE else View.VISIBLE
                if (empty && adapter.itemCount == 0) {
                    binding.hotTagsWrap.visibility = View.VISIBLE
                    binding.tvEmpty.visibility = View.GONE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearQuery.setOnClickListener {
            binding.etQuery.setText("")
            adapter.submit(emptyList())
            binding.hotTagsWrap.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
        }

        binding.etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else false
        }
        binding.btnSearch.setOnClickListener { doSearch() }
    }

    private fun buildHotTags() {
        val container = binding.hotTagsGrid
        container.removeAllViews()
        val density = resources.displayMetrics.density
        // 每行 4 个液态玻璃热搜标签
        hotKeywords.chunked(4).forEach { rowWords ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (10 * density).toInt() }
            }
            rowWords.forEachIndexed { idx, word ->
                val tv = TextView(requireContext()).apply {
                    text = word
                    textSize = 12f
                    maxLines = 1
                    gravity = android.view.Gravity.CENTER
                    setTextColor(requireContext().getColor(R.color.textPrimary))
                    setBackgroundResource(R.drawable.bg_glass_chip)
                    setPadding(0, (9 * density).toInt(), 0, (9 * density).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        if (idx < rowWords.size - 1) marginEnd = (8 * density).toInt()
                    }
                    setOnClickListener {
                        binding.etQuery.setText(word)
                        binding.etQuery.setSelection(word.length)
                        doSearch()
                    }
                }
                row.addView(tv)
            }
            container.addView(row)
        }
    }

    private fun doSearch() {
        val wd = binding.etQuery.text?.toString()?.trim().orEmpty()
        if (wd.isBlank()) return
        hideKeyboard()
        binding.hotTagsWrap.visibility = View.GONE
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

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etQuery.windowToken, 0)
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
