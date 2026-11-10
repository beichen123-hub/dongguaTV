package com.beichen.dongguatv.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.beichen.dongguatv.databinding.ItemSearchResultBinding
import com.bumptech.glide.Glide

/** 通用本地列表项（收藏/历史/追剧共用），复用 item_search_result 布局 */
data class LocalItem(
    val vodId: String,
    val siteKey: String,
    val vodName: String,
    val vodPic: String,
    val subtitle: String,   // remarks 位
    val siteLabel: String,  // 站点标签位
    val id: Long = 0        // entity id（删除用）
)

class LocalListAdapter(
    private val onClick: (LocalItem) -> Unit,
    private val onLongClick: ((LocalItem) -> Unit)? = null
) : RecyclerView.Adapter<LocalListAdapter.VH>() {

    private val items = mutableListOf<LocalItem>()

    fun submit(list: List<LocalItem>) {
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
            tvName.text = item.vodName
            tvRemarks.text = item.subtitle
            tvSite.text = item.siteLabel
            Glide.with(ivPoster).load(item.vodPic).into(ivPoster)
            root.setOnClickListener { onClick(item) }
            onLongClick?.let { cb ->
                root.setOnLongClickListener { cb(item); true }
            }
        }
    }

    override fun getItemCount() = items.size
}
