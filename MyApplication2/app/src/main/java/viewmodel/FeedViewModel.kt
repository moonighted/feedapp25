package com.bytedance.myapplication.viewmodel

import android.content.Context
import androidx.lifecycle.*
import com.bytedance.myapplication.model.FeedCard
import com.bytedance.myapplication.repository.FeedRepository
import com.bytedance.myapplication.util.LocalCacheManager
import kotlinx.coroutines.launch

class FeedViewModel(private val repository: FeedRepository) : ViewModel() {

    private val _feedList = MutableLiveData<List<FeedCard>>()
    val feedList: LiveData<List<FeedCard>> = _feedList

    private val _viewState = MutableLiveData<String>() // 用于UI显示的各种状态消息
    val viewState: LiveData<String> = _viewState

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val currentData = mutableListOf<FeedCard>()
    private var page = 1

    fun loadData(isRefresh: Boolean) {
        if (_isLoading.value == true) return
        _isLoading.value = true

        if (isRefresh) page = 1

        viewModelScope.launch {
            try {
                val (newData, isFromCache) = repository.fetchFeed(page)

                if (isFromCache) {
                    _viewState.value = "⚠️ 网络失败，已加载本地缓存"
                } else if (isRefresh) {
                    _viewState.value = "✅ 刷新成功"
                }

                if (isRefresh) currentData.clear()
                currentData.addAll(newData)
                _feedList.value = currentData.toList()
                if (!isFromCache) page++

            } catch (e: Exception) {
                _viewState.value = "❌ 加载失败: ${e.message}"
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

    // ViewModel 工厂，用于注入 Repository
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FeedViewModel(FeedRepository(LocalCacheManager(context))) as T
        }
    }
}