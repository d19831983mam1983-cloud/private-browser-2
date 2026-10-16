package com.example.privatebrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Хранилище в закрытой памяти приложения: история поиска и закреплённые вкладки
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("browser_store", Context.MODE_PRIVATE)

    class PinnedItem(val url: String, val title: String)

    var historyEnabled: Boolean
        get() = prefs.getBoolean("history_on", true)
        set(v) {
            prefs.edit().putBoolean("history_on", v).apply()
        }

    fun history(): MutableList<String> {
        val list = ArrayList<String>()
        try {
            val arr = JSONArray(prefs.getString("history", "[]") ?: "[]")
            for (i in 0 until arr.length()) list.add(arr.getString(i))
        } catch (e: Exception) {
        }
        return list
    }

    private fun saveHistory(list: List<String>) {
        val arr = JSONArray()
        for (s in list) arr.put(s)
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun addHistory(q: String) {
        if (!historyEnabled) return
        val list = history()
        list.remove(q)
        list.add(0, q)
        while (list.size > 100) list.removeAt(list.size - 1)
        saveHistory(list)
    }

    fun removeHistory(q: String) {
        val list = history()
        list.remove(q)
        saveHistory(list)
    }

    fun clearHistory() {
        prefs.edit().remove("history").apply()
    }

    fun pinned(): List<PinnedItem> {
        val list = ArrayList<PinnedItem>()
        try {
            val arr = JSONArray(prefs.getString("pinned", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(PinnedItem(o.getString("url"), o.optString("title", "")))
            }
        } catch (e: Exception) {
        }
        return list
    }

    fun savePinned(items: List<PinnedItem>) {
        val arr = JSONArray()
        for (p in items) {
            val o = JSONObject()
            o.put("url", p.url)
            o.put("title", p.title)
            arr.put(o)
        }
        prefs.edit().putString("pinned", arr.toString()).apply()
    }
}
