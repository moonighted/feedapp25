package com.bytedance.myapplication.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bytedance.myapplication.adapter.binders.ImageCardBinder
import com.bytedance.myapplication.adapter.binders.VideoCardBinder
import com.bytedance.myapplication.databinding.ItemLoadingFooterBinding
import com.bytedance.myapplication.model.FeedCard

class FeedAdapter(
    private val onDelete: (Int) -> Unit,
    private val onLog: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<FeedCard>()

    // 插件注册表
    private val binders = mutableMapOf<Int, FeedItemBinder<FeedCard, RecyclerView.ViewHolder>>()

    init {
        // 在这里注册不同的卡片样式
        registerBinder(FeedCard.TYPE_IMAGE, ImageCardBinder(onDelete))
        registerBinder(FeedCard.TYPE_VIDEO, VideoCardBinder(onDelete, onLog))
    }

    private fun registerBinder(type: Int, binder: FeedItemBinder<*, *>) {
        @Suppress("UNCHECKED_CAST")
        binders[type] = binder as FeedItemBinder<FeedCard, RecyclerView.ViewHolder>
    }

    fun submitList(newList: List<FeedCard>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    fun getItem(position: Int): FeedCard? = items.getOrNull(position)

    override fun getItemViewType(position: Int): Int {
        return if (position < items.size) items[position].cardType else -1 // -1 for Footer
    }

    override fun getItemCount(): Int = if (items.isEmpty()) 0 else items.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binder = binders[viewType]
        return binder?.createViewHolder(parent)
            ?: FooterViewHolder(ItemLoadingFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val binder = binders[getItemViewType(position)]
        if (binder != null && position < items.size) {
            binder.bindViewHolder(holder, items[position], position)
        } else if (holder is FooterViewHolder) {
            holder.binding.progressBar.visibility = View.VISIBLE
        }
    }

    fun onHolderActive(holder: RecyclerView.ViewHolder) {
        val type = holder.itemViewType
        binders[type]?.onActive(holder)
    }

    fun onHolderInactive(holder: RecyclerView.ViewHolder) {
        val type = holder.itemViewType
        binders[type]?.onInactive(holder)
    }

    val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            if (position >= items.size) return 2
            return items[position].spanType
        }
    }

    class FooterViewHolder(val binding: ItemLoadingFooterBinding) : RecyclerView.ViewHolder(binding.root)
}