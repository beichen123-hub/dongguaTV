package com.beichen.dongguatv.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.beichen.dongguatv.data.VodItem
import com.beichen.dongguatv.databinding.ItemVodGridBinding
import com.bumptech.glide.Glide

class VodGridAdapter(
    private val onClick: (VodItem) -> Unit
) : RecyclerView.Adapter<VodGridAdapter.VH>() {

    private val items = mutableListOf<VodItem>()

    fun submit(list: List<VodItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemVodGridBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemVodGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        with(holder.binding) {
            tvName.text = item.vod_name
            tvRemarks.text = item.vod_remarks
            Glide.with(ivPoster).load(item.vod_pic)
                .into(ivPoster)
            root.setOnClickListener { onClick(item) }
        }
    }

    override fun getItemCount() = items.size
}
