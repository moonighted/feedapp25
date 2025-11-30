package com.bytedance.myapplication.adapter.binders

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.CountDownTimer
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

class VideoCardBinder(
    private val onDelete: (Int) -> Unit,
    private val onLog: (String) -> Unit
) : FeedItemBinder<FeedCard, VideoCardBinder.VideoHolder>() {

    override fun createViewHolder(parent: ViewGroup): VideoHolder {
        return VideoHolder(ItemFeedCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun bindViewHolder(holder: VideoHolder, item: FeedCard, position: Int) {
        holder.bind(item, onDelete)
    }

    override fun onActive(holder: VideoHolder) {
        holder.startSimulation(onLog)
    }

    override fun onInactive(holder: VideoHolder) {
        holder.stopSimulation(onLog)
    }

    class VideoHolder(val binding: ItemFeedCardBinding) : RecyclerView.ViewHolder(binding.root) {
        private var timer: CountDownTimer? = null

        fun bind(data: FeedCard, onDelete: (Int) -> Unit) {
            binding.tvTitle.text = data.title
            binding.tvTag.text = "视频"
            binding.videoContainer.visibility = View.GONE

            val params = binding.ivCover.layoutParams as ConstraintLayout.LayoutParams
            params.dimensionRatio = "16:9"
            binding.ivCover.layoutParams = params

            Glide.with(binding.root)
                .load(data.imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(ColorDrawable(Color.BLACK))
                .into(binding.ivCover)

            binding.root.setOnLongClickListener {
                AlertDialog.Builder(it.context)
                    .setTitle("删除")
                    .setPositiveButton("删除") { _, _ ->
                        onDelete(layoutPosition)
                    }
                    .setNegativeButton("取消", null).show()
                true
            }
        }

        fun startSimulation(log: (String) -> Unit) {
            if (binding.videoContainer.visibility == View.VISIBLE) return

            log("▶️ [自动播放] Item $layoutPosition")
            binding.videoContainer.visibility = View.VISIBLE
            binding.tvVideoTimer.text = "加载中..."

            timer?.cancel()
            timer = object : CountDownTimer(10000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    binding.tvVideoTimer.text = "▶ 播放中 ${millisUntilFinished / 1000}s"
                }
                override fun onFinish() {
                    binding.tvVideoTimer.text = "↺ 播放结束"
                }
            }.start()
        }

        fun stopSimulation(log: (String) -> Unit) {
            if (binding.videoContainer.visibility == View.GONE) return

            log("⏹ [停止播放] Item $layoutPosition")
            binding.videoContainer.visibility = View.GONE
            timer?.cancel()
            timer = null
        }
    }
}