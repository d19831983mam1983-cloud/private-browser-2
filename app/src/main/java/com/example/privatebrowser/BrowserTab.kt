package com.example.privatebrowser

import android.webkit.WebView

class BrowserTab(val id: Int) {
    lateinit var webView: WebView
    var title: String = "Новая вкладка"
    var url: String = ""
}
