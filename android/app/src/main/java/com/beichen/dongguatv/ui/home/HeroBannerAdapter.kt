package com.beichen.dongguatv.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.beichen.dongguatv.data.TmdbItem
import com.beichen.dongguatv.databinding.ItemHeroBannerBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop

/**
 * 首页 Hero 轮播适配器（对标 Web heroCarousel：本周 TOP N + 大背景 + 简介 + 播放按钮）。
 * 自动翻页由 HomeFragment 控制，这里只负责把单页画出来。
 */
class HeroBannerAdapter(
    private val items: List<TmdbItem>,
    private val onClick: (TmdbItem) -> Unit
) : RecyclerView.Adapter<HeroBannerAdapter.VH>() {

    class VH(val binding: ItemHeroBannerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemHeroBannerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context
        with(holder.binding) {
            tvTag.text = "本周 TOP ${position + 1}"
            tvTitle.text = item.displayName
            tvOverview.text = item.overview.ifBlank { "精彩内容，不容错过" }
            Glide.with(ctx)
                .load(item.backdropUrl())
                .transform(CenterCrop())
                .placeholder(com.beichen.dongguatv.R.color.colorSurfaceVariant)
                .error(com.beichen.dongguatv.R.color.colorSurfaceVariant)
                .into(ivBackdrop)
            root.setOnClickListener { onClick(item) }
            btnPlay.setOnClickListener { onClick(item) }
        }
    }

    override fun getItemCount() = items.size
}
