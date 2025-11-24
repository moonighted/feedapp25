// ⚠️ 包名已修正为 com.bytedance.myapplication
package com.bytedance.myapplication

import android.app.AlertDialog
import android.graphics.Rect
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
// ⚠️ 修正 Binding 类的引用包名
import com.bytedance.myapplication.databinding.ActivityMainBinding
import com.bytedance.myapplication.databinding.ItemFeedCardBinding
import com.bytedance.myapplication.databinding.ItemLoadingFooterBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

// ================= 数据模型层 (Model) =================

/**
 * 卡片数据模型
 */
data class FeedCard(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val imageUrl: String,
    val type: Int,
    val spanType: Int,
    var isVideoPlaying: Boolean = false
) {
    companion object {
        const val TYPE_IMAGE_TEXT = 1
        const val TYPE_VIDEO = 2

        const val SPAN_SINGLE_COLUMN = 2 // 占满整行 (Grid Span Count = 2)
        const val SPAN_DOUBLE_COLUMN = 1 // 占一半
    }
}

// ================= 仓库层 (Repository) =================

class FeedRepository {
    // 模拟服务端数据下发
    suspend fun fetchFeed(page: Int): List<FeedCard> {
        delay(1000) // 模拟网络耗时
        val list = mutableListOf<FeedCard>()
        val start = (page - 1) * 10

        for (i in 0 until 10) {
            val isSingleColumn = Random.nextBoolean()
            val isVideo = Random.nextInt(10) > 7 // 30%概率是视频

            list.add(FeedCard(
                title = "Feed流内容展示 - 第${page}页 - 序号${start + i} \n${if(isSingleColumn) "【单列大图模式】" else "【双列小图】"}",
                imageUrl = "https://picsum.photos/seed/${start + i}/400/${if(isSingleColumn) 200 else 600}", // 随机宽高比
                type = if (isVideo) FeedCard.TYPE_VIDEO else FeedCard.TYPE_IMAGE_TEXT,
                spanType = if (isSingleColumn) FeedCard.SPAN_SINGLE_COLUMN else FeedCard.SPAN_DOUBLE_COLUMN
            ))
        }
        return list
    }
}

// ================= ViewModel层 =================

class FeedViewModel : ViewModel() {
    private val repo = FeedRepository()

    private val _feedList = MutableLiveData<List<FeedCard>>()
    val feedList: LiveData<List<FeedCard>> = _feedList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val currentData = mutableListOf<FeedCard>()
    private var page = 1

    init {
        loadData(isRefresh = true)
    }

    fun loadData(isRefresh: Boolean) {
        if (_isLoading.value == true) return
        _isLoading.value = true

        if (isRefresh) page = 1

        viewModelScope.launch {
            try {
                val newData = repo.fetchFeed(page)
                if (isRefresh) {
                    currentData.clear()
                }
                currentData.addAll(newData)
                _feedList.value = currentData.toList()
                page++
            } catch (e: Exception) {
                // Handle Error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteItem(position: Int) {
        if (position in currentData.indices) {
            currentData.removeAt(position)
            _feedList.value = currentData.toList()
        }
    }
}

// ================= Adapter 层 =================

class FeedAdapter(
    private val onDelete: (Int) -> Unit,
    private val onLog: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<FeedCard>()

    companion object {
        const val VIEW_TYPE_ITEM = 1
        const val VIEW_TYPE_FOOTER = 2
    }

    fun submitList(newList: List<FeedCard>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < items.size) VIEW_TYPE_ITEM else VIEW_TYPE_FOOTER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_ITEM) {
            FeedViewHolder(
                ItemFeedCardBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                onDelete, onLog
            )
        } else {
            FooterViewHolder(
                ItemLoadingFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is FeedViewHolder && position < items.size) {
            holder.bind(items[position])
        }
    }

    override fun getItemCount(): Int = items.size + 1

    // GridLayoutManager SpanSizeLookup
    val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            if (position >= items.size) return 2 // Footer 占满
            return items[position].spanType
        }
    }

    class FeedViewHolder(
        val binding: ItemFeedCardBinding,
        val onDelete: (Int) -> Unit,
        val onLog: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var timer: CountDownTimer? = null

        fun bind(data: FeedCard) {
            binding.tvTitle.text = data.title

            // 调整图片比例
            val ratio = if (data.spanType == FeedCard.SPAN_SINGLE_COLUMN) "16:9" else "3:4"
            (binding.ivCover.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)?.dimensionRatio = ratio

            Glide.with(binding.root)
                .load(data.imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(binding.ivCover)

            // 长按删除
            binding.root.setOnLongClickListener {
                AlertDialog.Builder(it.context)
                    .setTitle("操作")
                    .setMessage("确定要删除这张卡片吗？")
                    .setPositiveButton("删除") { _, _ -> onDelete(layoutPosition) }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }

            // 视频处理
            if (data.type == FeedCard.TYPE_VIDEO) {
                binding.tvTag.text = "视频"
                binding.tvVideoCountdown.visibility = if (data.isVideoPlaying) View.VISIBLE else View.GONE
            } else {
                binding.tvTag.text = "图文"
                binding.tvVideoCountdown.visibility = View.GONE
            }
        }

        fun startVideoSimulation() {
            if (binding.tvVideoCountdown.visibility == View.VISIBLE) return
            binding.tvVideoCountdown.visibility = View.VISIBLE
            onLog("▶️ 开始播放视频: Item $layoutPosition")

            timer?.cancel()
            timer = object : CountDownTimer(10000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    binding.tvVideoCountdown.text = "播放中: ${millisUntilFinished/1000}s"
                }
                override fun onFinish() {
                    binding.tvVideoCountdown.text = "播放结束"
                }
            }.start()
        }

        fun stopVideoSimulation() {
            if (binding.tvVideoCountdown.visibility == View.GONE) return
            binding.tvVideoCountdown.visibility = View.GONE
            timer?.cancel()
            onLog("⏹️ 停止播放: Item $layoutPosition")
        }
    }

    class FooterViewHolder(binding: ItemLoadingFooterBinding) : RecyclerView.ViewHolder(binding.root)
}

// ================= UI 层 (Activity) =================

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: FeedViewModel by viewModels()
    private lateinit var adapter: FeedAdapter

    private val exposureStates = mutableMapOf<String, ExposureState>()
    enum class ExposureState { NONE, PARTIAL, FULL }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
        setupListeners()
    }

    private fun setupRecyclerView() {
        adapter = FeedAdapter(
            onDelete = { pos -> viewModel.deleteItem(pos) },
            onLog = { msg -> logToConsole(msg) }
        )

        val layoutManager = GridLayoutManager(this, 2)
        layoutManager.spanSizeLookup = adapter.spanSizeLookup

        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter

        // 曝光监测监听
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                checkExposure()
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    checkExposure()
                }
            }
        })
    }

    private fun setupObservers() {
        viewModel.feedList.observe(this) { list ->
            adapter.submitList(list)
            binding.swipeRefreshLayout.isRefreshing = false
        }

        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading && adapter.itemCount == 0) binding.swipeRefreshLayout.isRefreshing = true
        }
    }

    private fun setupListeners() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadData(isRefresh = true)
            logToConsole("🔄 下拉刷新触发")
        }

        // Load More
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as GridLayoutManager
                val lastPos = layoutManager.findLastVisibleItemPosition()
                if (lastPos >= adapter.itemCount - 2) {
                    viewModel.loadData(isRefresh = false)
                }
            }
        })
    }

    private fun checkExposure() {
        val layoutManager = binding.recyclerView.layoutManager as GridLayoutManager
        val firstPos = layoutManager.findFirstVisibleItemPosition()
        val lastPos = layoutManager.findLastVisibleItemPosition()

        if (firstPos == RecyclerView.NO_POSITION || lastPos == RecyclerView.NO_POSITION) return

        val rvRect = Rect()
        binding.recyclerView.getGlobalVisibleRect(rvRect)

        for (i in firstPos..lastPos) {
            val view = layoutManager.findViewByPosition(i) ?: continue
            val viewHolder = binding.recyclerView.getChildViewHolder(view) as? FeedAdapter.FeedViewHolder ?: continue

            val itemData = viewModel.feedList.value?.getOrNull(i) ?: continue

            val itemRect = Rect()
            val isVisible = view.getGlobalVisibleRect(itemRect)

            if (isVisible) {
                if (itemRect.intersect(rvRect)) {
                    val visibleArea = itemRect.width() * itemRect.height()
                    val totalArea = view.width * view.height
                    val ratio = visibleArea.toFloat() / totalArea.toFloat()

                    handleExposureEvent(itemData, ratio, viewHolder)
                }
            } else {
                handleExposureEvent(itemData, 0f, viewHolder)
            }
        }
    }

    private fun handleExposureEvent(item: FeedCard, ratio: Float, holder: FeedAdapter.FeedViewHolder) {
        val currentState = exposureStates[item.id] ?: ExposureState.NONE

        if (ratio <= 0f) {
            if (currentState != ExposureState.NONE) {
                logToConsole("👻 卡片消失: ${item.id.take(4)}...")
                exposureStates[item.id] = ExposureState.NONE
                if (item.type == FeedCard.TYPE_VIDEO) holder.stopVideoSimulation()
            }
            return
        }

        if (currentState == ExposureState.NONE) {
            logToConsole("👀 卡片露出: ${item.id.take(4)}...")
            exposureStates[item.id] = ExposureState.PARTIAL
        }

        if (ratio >= 0.5f) {
            if (item.type == FeedCard.TYPE_VIDEO) holder.startVideoSimulation()

            if (currentState != ExposureState.FULL && ratio >= 1.0f) {
                logToConsole("🌟 卡片完整展示 (100%): ${item.id.take(4)}...")
                exposureStates[item.id] = ExposureState.FULL
            }
        } else {
            if (item.type == FeedCard.TYPE_VIDEO) holder.stopVideoSimulation()
        }
    }

    private fun logToConsole(msg: String) {
        runOnUiThread {
            val oldText = binding.tvLog.text.toString()
            val newLog = "${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())} $msg\n$oldText"
            binding.tvLog.text = newLog.take(2000)
        }
    }
}