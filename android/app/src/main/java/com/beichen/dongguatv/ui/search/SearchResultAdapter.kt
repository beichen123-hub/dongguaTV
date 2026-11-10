package com.beichen.dongguatv.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.beichen.dongguatv.data.VodItem
import com.beichen.dongguatv.databinding.ItemSearchResultBinding
import com.bumptech.glide.Glide

class SearchResultAdapter(
    private val onClick: (VodItem) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.VH>() {

    private val items = mutableListOf<VodItem>()

    fun submit(list: List<VodItem>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    class VH(val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        with(holder.binding) {
            tvName.text = item.vod_name
            tvRemarks.text = item.vod_remarks
            tvSite.text = item.site_name.ifBlank { item.site_key }
            Glide.with(ivPoster).load(item.vod_pic).into(ivPoster)
            root.setOnClickListener { onClick(item) }
        }
    }

    override fun getItemCount() = items.size
}
