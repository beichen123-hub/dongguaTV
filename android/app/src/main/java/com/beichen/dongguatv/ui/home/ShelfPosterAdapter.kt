package com.beichen.dongguatv.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.beichen.dongguatv.R
import com.beichen.dongguatv.data.TmdbItem
import com.beichen.dongguatv.databinding.ItemShelfPosterBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import java.util.Locale

/**
 * 首页榜单海报卡适配器（对标 Web hot-card：海报 + 星级评分 + 剧名 + 年份）。
 */
class ShelfPosterAdapter(
    private val items: List<TmdbItem>,
    private val onClick: (TmdbItem) -> Unit
) : RecyclerView.Adapter<ShelfPosterAdapter.VH>() {

    class VH(val binding: ItemShelfPosterBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemShelfPosterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context
        with(holder.binding) {
            tvName.text = item.displayName
            tvYear.text = item.year
            tvRating.text = String.format(Locale.CHINA, "★ %.1f", item.vote_average)
            tvRating.visibility = if (item.vote_average > 0.0) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
            Glide.with(ctx)
                .load(item.posterUrl())
                .transform(CenterCrop())
                .placeholder(R.color.colorSurfaceVariant)
                .error(R.color.colorSurfaceVariant)
                .into(ivPoster)
            root.setOnClickListener { onClick(item) }
        }
    }

    override fun getItemCount() = items.size
}
