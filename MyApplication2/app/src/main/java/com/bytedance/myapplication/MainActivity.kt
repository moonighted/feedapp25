package com.bytedance.myapplication

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
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
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bytedance.myapplication.databinding.ActivityMainBinding
import com.bytedance.myapplication.databinding.ItemFeedCardBinding
import com.bytedance.myapplication.databinding.ItemLoadingFooterBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

// ================= Model (数据模型) =================

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

// ================= Repository (数据仓库) =================

class FeedRepository {
    suspend fun fetchFeed(page: Int): List<FeedCard> {
        delay(600)
        val list = mutableListOf<FeedCard>()
        val startId = (page - 1) * 10
        var currentCount = 0

        while (currentCount < 10) {
            val useSingleColumn = Random.nextBoolean()

            if (useSingleColumn) {
                list.add(createCard(page, startId + currentCount, true))
                currentCount++
            } else {
                list.add(createCard(page, startId + currentCount, false))
                currentCount++
                list.add(createCard(page, startId + currentCount, false))
                currentCount++
            }
        }
        return list
    }

    private fun createCard(page: Int, index: Int, isSingle: Boolean): FeedCard {
        val width = if (isSingle) 600 else 400
        val height = if (isSingle) 340 else 530
        val randomColor = listOf("2c3e50", "e74c3c", "3498db", "9b59b6", "1abc9c", "f39c12").random()

        return FeedCard(
            title = "P$page-$index ${if(isSingle) "[大图推荐]" else "[小图精选]"} \n这是一段模拟的标题文字用于测试排版",
            imageUrl = "https://via.placeholder.com/${width}x${height}/$randomColor/ffffff?text=Image+$index",
            spanType = if (isSingle) FeedCard.SPAN_SINGLE else FeedCard.SPAN_DOUBLE
        )
    }
}

// ================= ViewModel (业务逻辑) =================

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

// ================= Adapter (适配器) =================

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

    val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            if (position >= items.size) return 2
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

            val params = binding.ivCover.layoutParams as ConstraintLayout.LayoutParams
            params.dimensionRatio = if (data.spanType == FeedCard.SPAN_SINGLE) "16:9" else "3:4"
            binding.ivCover.layoutParams = params

            Glide.with(binding.root)
                .load(data.imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(ColorDrawable(Color.LTGRAY))
                .error(ColorDrawable(Color.DKGRAY))
                .into(binding.ivCover)

            binding.root.setOnLongClickListener {
                AlertDialog.Builder(it.context)
                    .setTitle("删除确认")
                    .setMessage("确定要删除这条内容吗？")
                    // ⚠️ 这里必须用 layoutPosition，否则旧版本会报错
                    .setPositiveButton("删除") { _, _ -> onDelete(layoutPosition) }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }
        }
    }

    class FooterViewHolder(val binding: ItemLoadingFooterBinding) : RecyclerView.ViewHolder(binding.root)
}

// ================= Activity (主界面) =================

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: FeedViewModel by viewModels()
    private lateinit var adapter: FeedAdapter

    private val exposureStates = mutableMapOf<String, Int>()

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

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadData(isRefresh = true)
            log("🔄 下拉刷新...")
        }

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lastPos = layoutManager.findLastVisibleItemPosition()
                if (lastPos >= adapter.itemCount - 2) {
                    viewModel.loadData(isRefresh = false)
                }

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

    private fun checkExposure() {
        val layoutManager = binding.recyclerView.layoutManager as GridLayoutManager
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()

        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return

        val rvRect = Rect()
        binding.recyclerView.getGlobalVisibleRect(rvRect)

        val list = viewModel.feedList.value ?: return

        val currentFrameVisibleIds = mutableSetOf<String>()

        for (i in first..last) {
            if (i >= list.size) continue

            val view = layoutManager.findViewByPosition(i) ?: continue
            val itemData = list[i]

            val itemRect = Rect()
            val isVisible = view.getGlobalVisibleRect(itemRect)

            var ratio = 0f
            if (isVisible) {
                if (itemRect.intersect(rvRect)) {
                    val visibleArea = itemRect.width() * itemRect.height()
                    val totalArea = view.width * view.height
                    if (totalArea > 0) {
                        ratio = visibleArea.toFloat() / totalArea.toFloat()
                    }
                }
            }

            if (ratio > 0f) {
                currentFrameVisibleIds.add(itemData.id)
            }

            handleExposure(itemData.id, i, ratio)
        }

        val previouslyExposedIds = exposureStates.filter { it.value > 0 }.keys.toSet()

        for (id in previouslyExposedIds) {
            if (!currentFrameVisibleIds.contains(id)) {
                val index = list.indexOfFirst { it.id == id }
                handleExposure(id, index, 0f)
            }
        }
    }

    private fun handleExposure(id: String, index: Int, ratio: Float) {
        val oldState = exposureStates[id] ?: 0
        var newState = oldState

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
            binding.tvLog.text = "[$time] $msg\n$old".take(3000)
        }
    }
}