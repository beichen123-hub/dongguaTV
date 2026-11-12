package com.beichen.dongguatv.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.beichen.dongguatv.R
import com.beichen.dongguatv.data.Episode

class EpisodeAdapter(
    private val onClick: (Episode) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.VH>() {

    private val items = mutableListOf<Episode>()
    private var activeIndex: Int = -1

    fun submit(list: List<Episode>, currentIndex: Int = -1) {
        items.clear()
        items.addAll(list)
        activeIndex = currentIndex
        notifyDataSetChanged()
    }

    fun setActiveIndex(index: Int) {
        activeIndex = index
        notifyDataSetChanged()
    }

    class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode, parent, false) as TextView
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ep = items[position]
        val isCurrent = ep.index == activeIndex
        val ctx = holder.tv.context
        holder.tv.text = ep.name
        holder.tv.setBackgroundResource(
            if (isCurrent) R.drawable.bg_glass_chip_active else R.drawable.bg_glass_chip
        )
        holder.tv.setTextColor(
            ContextCompat.getColor(
                ctx,
                if (isCurrent) R.color.colorPrimaryLight else R.color.textPrimary
            )
        )
        holder.tv.setOnClickListener { onClick(ep) }
    }

    override fun getItemCount() = items.size
}
