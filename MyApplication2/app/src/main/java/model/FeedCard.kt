package com.bytedance.myapplication.model

import java.util.UUID

data class FeedCard(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val imageUrl: String,
    val spanType: Int, // 1: 双列, 2: 单列
    val cardType: Int  // 0: 图文, 1: 视频
) {
    companion object {
        const val SPAN_SINGLE = 2
        const val SPAN_DOUBLE = 1

        const val TYPE_IMAGE = 0
        const val TYPE_VIDEO = 1
    }
}