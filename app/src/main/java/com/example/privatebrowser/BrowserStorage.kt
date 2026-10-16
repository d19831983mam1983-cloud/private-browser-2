package com.example.privatebrowser

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class BrowserStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("private_browser", Context.MODE_PRIVATE)

    fun addHistory(q: String) {
        if (q.isBlank()) return
        val l = getHistory(); l.remove(q); l.add(0, q)
        while (l.size > 200) l.removeAt(l.size - 1)
        val a = JSONArray(); for (s in l) a.put(s)
        prefs.edit().putString("history", a.toString()).apply()
    }

    fun getHistory(): MutableList<String> {
        val a = JSONArray(prefs.getString("history", "[]") ?: "[]")
        val l = mutableListOf<String>()
        for (i in 0 until a.length()) l.add(a.getString(i))
        return l
    }

    fun clearHistory() { prefs.edit().remove("history").apply() }

    fun getPinned(): MutableList<String> {
        val a = JSONArray(prefs.getString("pinned", "[]") ?: "[]")
        val l = mutableListOf<String>()
        for (i in 0 until a.length()) l.add(a.getString(i))
        return l
    }

    fun isPinned(url: String): Boolean = getPinned().contains(url)

    fun pin(url: String): Boolean {
        val l = getPinned(); if (l.contains(url)) return false
        l.add(url); savePinned(l); return true
    }

    fun unpin(url: String): Boolean {
        val l = getPinned(); if (!l.remove(url)) return false
        savePinned(l); return true
    }

    private fun savePinned(l: MutableList<String>) {
        val a = JSONArray(); for (u in l) a.put(u)
        prefs.edit().putString("pinned", a.toString()).apply()
    }

    class SessionData(val urls: List<String>, val titles: List<String>)

    fun saveSession(urls: List<String>, titles: List<String>) {
        val a = JSONArray()
        for (i in urls.indices) {
            val o = JSONObject()
            o.put("url", urls[i]); o.put("title", titles[i]); a.put(o)
        }
        prefs.edit().putString("session", a.toString()).apply()
    }

    fun loadSession(): SessionData {
        val a = JSONArray(prefs.getString("session", "[]") ?: "[]")
        val u = mutableListOf<String>(); val t = mutableListOf<String>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            u.add(o.optString("url", "")); t.add(o.optString("title", ""))
        }
        return SessionData(u, t)
    }
}
