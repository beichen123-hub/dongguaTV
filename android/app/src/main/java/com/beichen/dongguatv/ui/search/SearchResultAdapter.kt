package com.beichen.dongguatv.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.beichen.dongguatv.data.NameUtil
import com.beichen.dongguatv.data.VodItem
import com.beichen.dongguatv.databinding.ItemSearchResultBinding
import com.bumptech.glide.Glide

/**
 * 搜索结果适配器（按剧名聚合）。
 *
 * 采集站同一部剧会在多个站点各出一条（「繁花」搜出 152 条其实只有 40 部剧），
 * 直接平铺的话列表全是重复。这里按 NameUtil.key 归一化后聚合：
 * 同一部剧只显示一条，标注「·N源」，进详情页可以换源。
 */
class SearchResultAdapter(
    private val onClick: (VodItem) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.VH>() {

    /** 聚合项：代表条目 + 该剧共有几个源 */
    private data class Group(val primary: VodItem, val count: Int)

    private val groups = ArrayList<Group>(32)

    fun submit(list: List<VodItem>) {
        groups.clear()
        val map = LinkedHashMap<String, MutableList<VodItem>>()
        list.forEach { item ->
            val k = NameUtil.key(item.vod_name).ifBlank { item.vod_name }
            map.getOrPut(k) { mutableListOf() }.add(item)
        }
        map.values.forEach { g -> groups.add(Group(g.first(), g.size)) }
        notifyDataSetChanged()
    }

    class VH(val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val group = groups[position]
        val item = group.primary
        val site = item.site_name.ifBlank { item.site_key }
        with(holder.binding) {
            tvName.text = NameUtil.display(item.vod_name).ifBlank { item.vod_name }
            tvRemarks.text = item.vod_remarks
            tvSite.text = if (group.count > 1) "$site · ${group.count}源" else site
            Glide.with(ivPoster).load(item.vod_pic).into(ivPoster)
            root.setOnClickListener { onClick(item) }
        }
    }

    override fun getItemCount() = groups.size
}
