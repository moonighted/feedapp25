package com.bytedance.myapplication.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bytedance.myapplication.model.FeedCard

abstract class FeedItemBinder<T : FeedCard, VH : RecyclerView.ViewHolder> {
    abstract fun createViewHolder(parent: ViewGroup): VH
    abstract fun bindViewHolder(holder: VH, item: T, position: Int)

    open fun onActive(holder: VH) {}
    open fun onInactive(holder: VH) {}
}