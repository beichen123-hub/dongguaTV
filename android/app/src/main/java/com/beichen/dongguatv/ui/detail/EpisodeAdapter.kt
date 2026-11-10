package com.beichen.dongguatv.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.beichen.dongguatv.data.Episode
import com.beichen.dongguatv.R

class EpisodeAdapter(
    private val onClick: (Episode) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.VH>() {

    private val items = mutableListOf<Episode>()

    fun submit(list: List<Episode>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode, parent, false) as TextView
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ep = items[position]
        holder.tv.text = ep.name
        holder.tv.setOnClickListener { onClick(ep) }
    }

    override fun getItemCount() = items.size
}
