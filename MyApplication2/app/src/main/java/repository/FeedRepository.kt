package com.bytedance.myapplication.repository

import com.bytedance.myapplication.model.FeedCard
import com.bytedance.myapplication.util.LocalCacheManager
import kotlinx.coroutines.delay
import kotlin.random.Random

class FeedRepository(private val cacheManager: LocalCacheManager) {

    suspend fun fetchFeed(page: Int): Pair<List<FeedCard>, Boolean> {
        try {
            // 模拟10%概率网络失败，强制使用缓存 (仅第一页)
            val shouldFail = Random.nextInt(10) > 8
            if (shouldFail && page == 1) throw Exception("模拟网络错误")

            delay(600)
            val list = mutableListOf<FeedCard>()
            val startId = (page - 1) * 10
            var currentCount = 0

            while (currentCount < 10) {
                // 30%概率是视频卡片
                val isVideo = Random.nextInt(10) < 3
                val isSingle = isVideo || Random.nextBoolean()

                if (isSingle) {
                    list.add(createCard(page, startId + currentCount, true, isVideo))
                    currentCount++
                } else {
                    list.add(createCard(page, startId + currentCount, false, false))
                    currentCount++
                    list.add(createCard(page, startId + currentCount, false, false))
                    currentCount++
                }
            }

            if (page == 1) cacheManager.saveCache(list)
            return Pair(list, false)

        } catch (e: Exception) {
            if (page == 1) {
                val cached = cacheManager.loadCache()
                if (cached.isNotEmpty()) return Pair(cached, true)
            }
            throw e
        }
    }

    private fun createCard(page: Int, index: Int, isSingle: Boolean, isVideo: Boolean): FeedCard {
        val width = if (isSingle) 600 else 400
        val height = if (isSingle) 340 else 530

        return FeedCard(
            title = "P$page-$index ${if(isVideo) "[视频]" else if(isSingle) "[大图]" else "[小图]"} \n${if(isVideo) "自动播放测试" else "模拟标题文字"}",
            imageUrl = "https://picsum.photos/seed/$index/$width/$height",
            spanType = if (isSingle) FeedCard.SPAN_SINGLE else FeedCard.SPAN_DOUBLE,
            cardType = if (isVideo) FeedCard.TYPE_VIDEO else FeedCard.TYPE_IMAGE
        )
    }
}