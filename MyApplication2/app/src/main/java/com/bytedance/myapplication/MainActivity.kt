package com.bytedance.myapplication

import android.graphics.Rect
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bytedance.myapplication.adapter.FeedAdapter
import com.bytedance.myapplication.databinding.ActivityMainBinding
import com.bytedance.myapplication.model.FeedCard
import com.bytedance.myapplication.viewmodel.FeedViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: FeedViewModel by viewModels { FeedViewModel.Factory(this) }
    private lateinit var adapter: FeedAdapter

    // 曝光状态记录
    private val exposureStates = mutableMapOf<String, Int>()
    private var activeItemId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()

        viewModel.loadData(true)
    }

    private fun setupRecyclerView() {
        adapter = FeedAdapter(
            onDelete = { pos -> viewModel.deleteItem(pos) },
            onLog = { msg -> log(msg) }
        )

        val layoutManager = GridLayoutManager(this, 2)
        layoutManager.spanSizeLookup = adapter.spanSizeLookup
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.setItemViewCacheSize(10)

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadData(isRefresh = true)
        }

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lastPos = layoutManager.findLastVisibleItemPosition()
                if (lastPos >= adapter.itemCount - 2) {
                    viewModel.loadData(isRefresh = false)
                }
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

        viewModel.viewState.observe(this) { msg ->
            if (msg.isNotEmpty()) log(msg)
        }
    }

    private fun checkExposure() {
        val layoutManager = binding.recyclerView.layoutManager as GridLayoutManager
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()

        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return

        val rvRect = Rect()
        binding.recyclerView.getGlobalVisibleRect(rvRect)

        val currentFrameVisibleIds = mutableSetOf<String>()
        val screenCenterY = rvRect.height() / 2
        var bestCandidateId: String? = null
        var minDistanceToCenter = Int.MAX_VALUE

        for (i in first..last) {
            val itemData = adapter.getItem(i) ?: continue
            val view = layoutManager.findViewByPosition(i) ?: continue
            val viewHolder = binding.recyclerView.getChildViewHolder(view)

            val itemRect = Rect()
            val isVisible = view.getGlobalVisibleRect(itemRect)

            var ratio = 0f
            if (isVisible && itemRect.intersect(rvRect)) {
                val visibleArea = itemRect.width() * itemRect.height()
                val totalArea = view.width * view.height
                if (totalArea > 0) ratio = visibleArea.toFloat() / totalArea.toFloat()

                if (itemData.cardType == FeedCard.TYPE_VIDEO && ratio > 0.6f) {
                    val centerY = itemRect.centerY()
                    val distance = Math.abs(centerY - screenCenterY)
                    if (distance < minDistanceToCenter) {
                        minDistanceToCenter = distance
                        bestCandidateId = itemData.id
                    }
                }
            }

            if (ratio > 0f) currentFrameVisibleIds.add(itemData.id)

            handleExposure(itemData.id, i, ratio)

            if (itemData.id != bestCandidateId) {
                adapter.onHolderInactive(viewHolder)
            }
        }

        if (bestCandidateId != null && bestCandidateId != activeItemId) {
            val pos = (0 until adapter.itemCount).find { adapter.getItem(it)?.id == bestCandidateId }
            if (pos != null) {
                val view = layoutManager.findViewByPosition(pos)
                if (view != null) {
                    adapter.onHolderActive(binding.recyclerView.getChildViewHolder(view))
                    activeItemId = bestCandidateId
                }
            }
        } else if (bestCandidateId == null) {
            activeItemId = null
        }

        val previouslyExposedIds = exposureStates.filter { it.value > 0 }.keys.toSet()
        for (id in previouslyExposedIds) {
            if (!currentFrameVisibleIds.contains(id)) {
                handleExposure(id, -1, 0f)
            }
        }
    }

    private fun handleExposure(id: String, index: Int, ratio: Float) {
        val oldState = exposureStates[id] ?: 0
        var newState = oldState

        if (ratio <= 0f) {
            if (oldState > 0) {
                log("👻 [消失] ID:${id.take(4)}")
                newState = 0
            }
        } else {
            if (oldState == 0) newState = 1
            if (ratio >= 0.5f && oldState < 2) {
                log("🌗 [>50%] Item $index")
                newState = 2
            }
            if (ratio >= 1.0f && oldState < 3) {
                log("🌟 [完整] Item $index")
                newState = 3
            }
        }

        if (newState != oldState) {
            exposureStates[id] = newState
        }
    }

    private fun log(msg: String) {
        runOnUiThread {
            val time = java.text.SimpleDateFormat("mm:ss").format(java.util.Date())
            val old = binding.tvLog.text.toString()
            binding.tvLog.text = "[$time] $msg\n$old".take(3000)
        }
    }
}