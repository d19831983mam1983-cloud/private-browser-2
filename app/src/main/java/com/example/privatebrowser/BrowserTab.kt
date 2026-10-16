package com.example.privatebrowser

import android.webkit.WebView

class BrowserTab(val id: Int) {
    lateinit var webView: WebView
    var url: String = ""
    var title: String = "Новая вкладка"
    var pinned: Boolean = false
    var needsLoad: Boolean = false
}
