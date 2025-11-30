package com.bytedance.myapplication.util

import android.content.Context
import android.content.SharedPreferences
import com.bytedance.myapplication.model.FeedCard
import org.json.JSONArray
import org.json.JSONObject

class LocalCacheManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("feed_cache", Context.MODE_PRIVATE)

    fun saveCache(list: List<FeedCard>) {
        val jsonArray = JSONArray()
        list.forEach { card ->
            val jsonObj = JSONObject()
            jsonObj.put("id", card.id)
            jsonObj.put("title", card.title)
            jsonObj.put("imageUrl", card.imageUrl)
            jsonObj.put("spanType", card.spanType)
            jsonObj.put("cardType", card.cardType)
            jsonArray.put(jsonObj)
        }
        prefs.edit().putString("cache_data", jsonArray.toString()).apply()
    }

    fun loadCache(): List<FeedCard> {
        val jsonStr = prefs.getString("cache_data", "")
        val list = mutableListOf<FeedCard>()
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(FeedCard(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        imageUrl = obj.getString("imageUrl"),
                        spanType = obj.getInt("spanType"),
                        cardType = obj.getInt("cardType")
                    ))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return list
    }
}