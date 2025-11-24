package com.bytedance.myapplication

import android.app.AlertDialog
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bytedance.myapplication.databinding.ActivityMainBinding
import com.bytedance.myapplication.databinding.ItemFeedCardBinding
import com.bytedance.myapplication.databinding.ItemLoadingFooterBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

// ================= Model  =================

data class FeedCard(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val imageUrl: String,
    val spanType: Int // 1: 双列(占1格), 2: 单列(占2格)
) {
    companion object {
        const val SPAN_SINGLE = 2
        const val SPAN_DOUBLE = 1
    }
}

// ================= Repository  =================

class FeedRepository {
    // 模拟网络请求
    suspend fun fetchFeed(page: Int): List<FeedCard> {
        delay(800) // 模拟网络延迟
        val list = mutableListOf<FeedCard>()
        val start = (page - 1) * 10

        for (i in 0 until 10) {
            val isSingle = Random.nextBoolean()
            list.add(FeedCard(
                title = "模拟数据 - 第${page}页 - 序号${start + i}\n${if(isSingle) "[单列大图]" else "[双列小图]"}",
                imageUrl = "https://picsum.photos/seed/${start + i}/400/${if(isSingle) 220 else 500}",
                spanType = if (isSingle) FeedCard.SPAN_SINGLE else FeedCard.SPAN_DOUBLE
            ))
        }
        return list
    }
}

// ================= ViewModel  =================

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
                e.printStackTrace()
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

// ================= Adapter  =================

class FeedAdapter(
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<FeedCard>()

    companion object {
        const val TYPE_ITEM = 1
        const val TYPE_FOOTER = 2
    }

    fun submitList(newList: List<FeedCard>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < items.size) TYPE_ITEM else TYPE_FOOTER
    }

    override fun getItemCount(): Int = if (items.isEmpty()) 0 else items.size + 1

    // 核心混排逻辑：告诉 LayoutManager 每个 item 占几格
    val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            if (position >= items.size) return 2 // Footer 占满一行
            return items[position].spanType
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_ITEM) {
            FeedViewHolder(
                ItemFeedCardBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                onDelete
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
        } else if (holder is FooterViewHolder) {
            // Footer 逻辑简单处理，实际可根据加载状态显示不同文案
            holder.binding.progressBar.visibility = View.VISIBLE
            holder.binding.tvEnd.visibility = View.GONE
        }
    }

    class FeedViewHolder(
        private val binding: ItemFeedCardBinding,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(data: FeedCard) {
            binding.tvTitle.text = data.title
            binding.tvTag.text = if (data.spanType == FeedCard.SPAN_SINGLE) "推荐 · 单列" else "精选 · 双列"

            // 动态调整图片比例
            val params = binding.ivCover.layoutParams as ConstraintLayout.LayoutParams
            params.dimensionRatio = if (data.spanType == FeedCard.SPAN_SINGLE) "16:9" else "3:4"
            binding.ivCover.layoutParams = params

            Glide.with(binding.root)
                .load(data.imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(binding.ivCover)

            // 长按删除
            binding.root.setOnLongClickListener {
                AlertDialog.Builder(it.context)
                    .setTitle("删除确认")
                    .setMessage("确定要删除这条内容吗？")
                    .setPositiveButton("删除") { _, _ -> onDelete(layoutPosition) } // 使用 layoutPosition
                    .setNegativeButton("取消", null)
                    .show()
                true
            }
        }
    }

    class FooterViewHolder(val binding: ItemLoadingFooterBinding) : RecyclerView.ViewHolder(binding.root)
}

// ================= Activity  =================

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: FeedViewModel by viewModels()
    private lateinit var adapter: FeedAdapter

    // 记录卡片曝光状态
    private val exposureStates = mutableMapOf<String, Int>()
    // 0: 未曝光, 1: 露出, 2: >50%, 3: 完整

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        adapter = FeedAdapter { pos ->
            viewModel.deleteItem(pos)
            log("🗑 删除操作: 第${pos}项")
        }

        val layoutManager = GridLayoutManager(this, 2)
        layoutManager.spanSizeLookup = adapter.spanSizeLookup
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter

        // 下拉刷新
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadData(isRefresh = true)
            log("🔄 下拉刷新...")
        }

        // 无限加载 + 曝光监测
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // 1. 无限加载逻辑
                val lastPos = layoutManager.findLastVisibleItemPosition()
                if (lastPos >= adapter.itemCount - 2) {
                    viewModel.loadData(isRefresh = false)
                }

                // 2. 曝光监测逻辑
                checkExposure()
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

    // 曝光检测算法
    private fun checkExposure() {
        val layoutManager = binding.recyclerView.layoutManager as GridLayoutManager
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()

        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return

        val rvRect = Rect()
        binding.recyclerView.getGlobalVisibleRect(rvRect) // 获取列表在屏幕上的区域

        val list = viewModel.feedList.value ?: return

        for (i in first..last) {
            if (i >= list.size) continue // 排除 Footer

            val view = layoutManager.findViewByPosition(i) ?: continue
            val itemData = list[i]

            val itemRect = Rect()
            val isVisible = view.getGlobalVisibleRect(itemRect) // 获取 Item 在屏幕上的区域

            if (isVisible) {
                // 计算重叠面积
                if (itemRect.intersect(rvRect)) {
                    val visibleArea = itemRect.width() * itemRect.height()
                    val totalArea = view.width * view.height
                    val ratio = visibleArea.toFloat() / totalArea.toFloat()

                    handleExposure(itemData.id, i, ratio)
                }
            } else {
                handleExposure(itemData.id, i, 0f)
            }
        }
    }

    private fun handleExposure(id: String, index: Int, ratio: Float) {
        val oldState = exposureStates[id] ?: 0
        var newState = oldState

        // 状态定义: 0=无, 1=露出(>0), 2=过半(>=0.5), 3=完整(=1.0)

        if (ratio <= 0f) {
            if (oldState > 0) {
                log("👻 [消失] item:$index (ID前4位:${id.take(4)})")
                newState = 0
            }
        } else {
            if (oldState == 0) {
                log("👀 [露出] item:$index")
                newState = 1
            }

            if (ratio >= 0.5f && oldState < 2) {
                log("🌗 [露出超过50%] item:$index")
                newState = 2
            }

            if (ratio >= 1.0f && oldState < 3) {
                log("🌟 [完整露出] item:$index")
                newState = 3
            }
        }

        if (newState != oldState) {
            exposureStates[id] = newState
        }
    }

    private fun log(msg: String) {
        runOnUiThread {
            val time = java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())
            val old = binding.tvLog.text.toString()
            binding.tvLog.text = "[$time] $msg\n$old".take(3000) // 限制日志长度
        }
    }
}