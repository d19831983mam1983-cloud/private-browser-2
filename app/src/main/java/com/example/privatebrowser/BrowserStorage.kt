package com.example.privatebrowser

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class BrowserStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("private_browser", Context.MODE_PRIVATE)

    fun addHistory(query: String) {
        if (query.isBlank()) return
        val list = getHistory()
        list.remove(query)
        list.add(0, query)
        while (list.size > 200) list.removeAt(list.size - 1)
        val arr = JSONArray()
        for (s in list) arr.put(s)
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun getHistory(): MutableList<String> {
        val json = prefs.getString("history", "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list.add(arr.getString(i))
        return list
    }

    fun clearHistory() {
        prefs.edit().remove("history").apply()
    }

    fun getPinned(): MutableList<String> {
        val json = prefs.getString("pinned", "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list.add(arr.getString(i))
        return list
    }

    fun isPinned(url: String): Boolean {
        return getPinned().contains(url)
    }

    fun pin(url: String): Boolean {
        val list = getPinned()
        if (list.contains(url)) return false
        list.add(url)
        savePinned(list)
        return true
    }

    fun unpin(url: String): Boolean {
        val list = getPinned()
        if (!list.remove(url)) return false
        savePinned(list)
        return true
    }

    private fun savePinned(list: MutableList<String>) {
        val arr = JSONArray()
        for (u in list) arr.put(u)
        prefs.edit().putString("pinned", arr.toString()).apply()
    }

    class SessionData(val urls: List<String>, val titles: List<String>)

    fun saveSession(urls: List<String>, titles: List<String>) {
        val arr = JSONArray()
        for (i in urls.indices) {
            val o = JSONObject()
            o.put("url", urls[i])
            o.put("title", titles[i])
            arr.put(o)
        }
        prefs.edit().putString("session", arr.toString()).apply()
    }

    fun loadSession(): SessionData {
        val json = prefs.getString("session", "[]") ?: "[]"
        val arr = JSONArray(json)
        val urls = mutableListOf<String>()
        val titles = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            urls.add(o.optString("url", ""))
            titles.add(o.optString("title", ""))
        }
        return SessionData(urls, titles)
    }
}
