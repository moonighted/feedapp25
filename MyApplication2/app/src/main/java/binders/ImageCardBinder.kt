package com.bytedance.myapplication.adapter.binders

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bytedance.myapplication.adapter.FeedItemBinder
import com.bytedance.myapplication.databinding.ItemFeedCardBinding
import com.bytedance.myapplication.model.FeedCard

class ImageCardBinder(private val onDelete: (Int) -> Unit) : FeedItemBinder<FeedCard, ImageCardBinder.ImageHolder>() {

    override fun createViewHolder(parent: ViewGroup): ImageHolder {
        return ImageHolder(ItemFeedCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun bindViewHolder(holder: ImageHolder, item: FeedCard, position: Int) {
        holder.bind(item, onDelete)
    }

    class ImageHolder(val binding: ItemFeedCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: FeedCard, onDelete: (Int) -> Unit) {
            binding.tvTitle.text = data.title
            binding.tvTag.text = "图文"

            // 确保 XML 里有 @+id/videoContainer
            binding.videoContainer.visibility = View.GONE

            val params = binding.ivCover.layoutParams as ConstraintLayout.LayoutParams
            params.dimensionRatio = if (data.spanType == FeedCard.SPAN_SINGLE) "16:9" else "3:4"
            binding.ivCover.layoutParams = params

            Glide.with(binding.root)
                .load(data.imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(ColorDrawable(Color.LTGRAY))
                .into(binding.ivCover)

            try { Glide.with(binding.root).load(data.imageUrl).preload() } catch (_: Exception) {}

            binding.root.setOnLongClickListener {
                AlertDialog.Builder(it.context)
                    .setTitle("删除")
                    .setPositiveButton("删除") { _, _ ->
                        // ⚠️ 修复：改回兼容性更好的 layoutPosition
                        onDelete(layoutPosition)
                    }
                    .setNegativeButton("取消", null).show()
                true
            }
        }
    }
}