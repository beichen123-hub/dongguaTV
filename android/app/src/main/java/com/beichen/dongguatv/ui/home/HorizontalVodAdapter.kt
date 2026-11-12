package com.beichen.dongguatv.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.beichen.dongguatv.R
import com.beichen.dongguatv.data.NameUtil
import com.beichen.dongguatv.data.VodItem
import com.beichen.dongguatv.databinding.ItemVodHorizontalBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners

/**
 * 首页横向推荐行适配器。
 * 海报用 Glide 加载，带占位/失败兜底图，避免图床挂掉时整行空白。
 */
class HorizontalVodAdapter(
    private val items: List<VodItem>,
    private val onClick: (VodItem) -> Unit
) : RecyclerView.Adapter<HorizontalVodAdapter.VH>() {

    class VH(val binding: ItemVodHorizontalBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemVodHorizontalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context
        with(holder.binding) {
            tvName.text = NameUtil.display(item.vod_name).ifBlank { item.vod_name }
            tvRemarks.text = item.vod_remarks
            tvRemarks.visibility =
                if (item.vod_remarks.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
            Glide.with(ctx)
                .load(item.vod_pic)
                .transform(CenterCrop())
                .placeholder(R.color.colorSurfaceVariant)
                .error(R.color.colorSurfaceVariant)
                .into(ivPoster)
            root.setOnClickListener { onClick(item) }
        }
    }

    override fun getItemCount() = items.size
}
