package com.example.privatebrowser

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var ui: BrowserUI
    private lateinit var storage: BrowserStorage

    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabId = 0
    private var nextTabId = 1

    private val searchUrl = "https://duckduckgo.com/?q="
    private val home = "https://www.google.com"

    private val blockedHosts = listOf(
        "doubleclick.net", "googlesyndication.com", "google-analytics.com",
        "googletagmanager.com", "googleadservices.com", "adservice.google.com",
        "connect.facebook.net", "scorecardresearch.com", "hotjar.com",
        "mc.yandex.ru", "amplitude.com", "segment.io", "criteo.com",
        "taboola.com", "outbrain.com", "adnxs.com", "moatads.com"
    )

    private val activeTab: BrowserTab?
        get() {
            for (t in tabs) if (t.id == activeTabId) return t
            return null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = BrowserStorage(this)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        ui = BrowserUI(this)
        ui.onOpen = { open(it) }
        ui.onTabsDialog = { showTabsDialog() }
        ui.onConfirmCloseAll = { confirmCloseAll() }
        ui.onNewTab = { v -> ui.animateNewTabGhost(v) { addTab(home, true) } }
        ui.onSettings = { v -> ui.pulseButton(v) { showSettingsDialog() } }
        ui.onUrlLongPress = { text ->
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("url", text))
            Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
        }

        val root = ui.build()
        setContentView(root)
        ui.animatePanelsIn()

        restoreSession()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val t = activeTab
                if (t != null && t.webView.canGoBack()) t.webView.goBack() else finish()
            }
        })
    }

    private fun saveSession() {
        val urls = ArrayList<String>()
        val titles = ArrayList<String>()
        for (t in tabs) {
            urls.add(t.url)
            titles.add(t.title)
        }
        storage.saveSession(urls, titles)
    }

    private fun restoreSession() {
        val saved = storage.loadSession()
        var opened = 0
        for (i in saved.urls.indices) {
            val u = saved.urls[i]
            if (u.isNotBlank()) {
                addTab(u, false)
                opened++
            }
        }
        for (u in storage.getPinned()) {
            var already = false
            for (t in tabs) if (t.url == u) already = true
            if (!already) {
                addTab(u, false)
                opened++
            }
        }
        if (opened == 0) addTab(home, false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun addTab(initialUrl: String, animate: Boolean) {
        val id = nextTabId
        nextTabId = nextTabId + 1
        val tab = BrowserTab(id)

        val wv = WebView(this)
        wv.setBackgroundColor(Color.parseColor("#0B0F1A"))
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        wv.settings.allowFileAccess = false
        wv.settings.allowContentAccess = false
        wv.settings.setGeolocationEnabled(false)
        wv.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        wv.settings.saveFormData = false
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, false)
        wv.visibility = View.GONE

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val scheme = r.url.scheme
                if (scheme == null) return true
                if (scheme == "http") {
                    v.loadUrl(r.url.buildUpon().scheme("https").build().toString())
                    return true
                }
                return scheme != "https"
            }

            override fun shouldInterceptRequest(
                v: WebView, r: WebResourceRequest
            ): WebResourceResponse? {
                val host = r.url.host ?: return null
                for (b in blockedHosts) {
                    if (host == b || host.endsWith("." + b)) {
                        return WebResourceResponse(
                            "text/plain", "utf-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                }
                return null
            }

            override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) {
                tab.url = url
                if (tab.id == activeTabId && !ui.urlBox.hasFocus()) ui.urlBox.setText(url)
            }

            override fun onPageFinished(v: WebView, url: String) {
                val title = v.title
                if (title != null) tab.title = title else tab.title = url
                renderTabStrip()
                saveSession()
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                if (tab.id != activeTabId) return
                ui.bar.progress = p
                if (p in 1..99) ui.bar.visibility = View.VISIBLE else ui.bar.visibility = View.GONE
            }

            override fun onReceivedTitle(v: WebView?, title: String?) {
                if (title != null) {
                    tab.title = title
                    renderTabStrip()
                }
            }
        }

        tab.webView = wv
        tabs.add(tab)
        ui.webContainer.addView(wv, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        switchToTab(tab.id, animate)
        wv.loadUrl(initialUrl)
        renderTabStrip()
    }

    private fun switchToTab(id: Int, animate: Boolean) {
        activeTabId = id
        for (t in tabs) {
            if (t.id == id) {
                t.webView.visibility = View.VISIBLE
                if (animate) {
                    t.webView.alpha = 0f
                    t.webView.animate().alpha(1f).setDuration(220).start()
                }
            } else {
                t.webView.visibility = View.GONE
            }
        }
        val t = activeTab
        if (t != null && !ui.urlBox.hasFocus()) ui.urlBox.setText(t.url)
        renderTabStrip()
    }

    private fun closeTab(id: Int) {
        var target: BrowserTab? = null
        for (t in tabs) if (t.id == id) target = t
        if (target == null) return
        ui.webContainer.removeView(target.webView)
        target.webView.destroy()
        tabs.remove(target)

        if (tabs.isEmpty()) {
            addTab(home, false)
            return
        }
        if (activeTabId == id) switchToTab(tabs[tabs.size - 1].id, true)
        renderTabStrip()
        saveSession()
    }

    private fun closeAllTabs() {
        for (t in tabs) {
            ui.webContainer.removeView(t.webView)
            t.webView.destroy()
        }
        tabs.clear()
        addTab(home, true)
        Toast.makeText(this, "Все вкладки закрыты", Toast.LENGTH_SHORT).show()
    }

    private fun confirmCloseAll() {
        AlertDialog.Builder(this)
            .setTitle("Закрыть все вкладки?")
            .setMessage("Будет закрыто вкладок: " + tabs.size)
            .setPositiveButton("Закрыть все") { _, _ -> closeAllTabs() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun renderTabStrip() {
        ui.tabStrip.removeAllViews()
        for (tab in tabs) {
            val isActive = tab.id == activeTabId
            val isPinned = storage.isPinned(tab.url)

            val pill = ui.makeTabPill(isActive)
            pill.setOnClickListener { switchToTab(tab.id, true) }
            pill.setOnLongClickListener {
                showTabMenu(tab)
                true
            }

            val titleView = TextView(this)
            var prefix = ""
            if (isPinned) prefix = "[P] "
            var title = tab.title
            if (title.length > 16) title = title.substring(0, 16) + "..."
            titleView.text = prefix + title
            titleView.setTextColor(Color.WHITE)
            titleView.textSize = 13f
            titleView.maxLines = 1
            pill.addView(titleView)

            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = ui.dp(8)
            ui.tabStrip.addView(pill, lp)

            ui.animatePillIn(pill)
        }

        ui.tabsCountBadge.text = tabs.size.toString()

        ui.tabStripWrap.post {
            ui.tabStripWrap.smoothScrollTo(ui.tabStrip.width, 0)
        }
    }

    private fun showTabMenu(tab: BrowserTab) {
        val isPinned = storage.isPinned(tab.url)
        val pinLabel = if (isPinned) "Открепить вкладку" else "Закрепить вкладку"

        val items = arrayOf(
            pinLabel,
            "Копировать название",
            "Копировать адрес",
            "Закрыть вкладку",
            "Закрыть все вкладки"
        )
        AlertDialog.Builder(this)
            .setTitle(tab.title)
            .setItems(items) { _, which ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                if (which == 0) {
                    if (isPinned) {
                        storage.unpin(tab.url)
                        Toast.makeText(this, "Вкладка откреплена", Toast.LENGTH_SHORT).show()
                    } else {
                        storage.pin(tab.url)
                        Toast.makeText(this, "Вкладка закреплена", Toast.LENGTH_SHORT).show()
                    }
                    renderTabStrip()
                } else if (which == 1) {
                    cm.setPrimaryClip(ClipData.newPlainText("title", tab.title))
                    Toast.makeText(this, "Название скопировано", Toast.LENGTH_SHORT).show()
                } else if (which == 2) {
                    cm.setPrimaryClip(ClipData.newPlainText("url", tab.url))
                    Toast.makeText(this, "Адрес скопирован", Toast.LENGTH_SHORT).show()
                } else if (which == 3) {
                    closeTab(tab.id)
                } else if (which == 4) {
                    confirmCloseAll()
                }
            }
            .show()
    }

    private fun showTabsDialog() {
        val titles = ArrayList<String>()
        for (tab in tabs) {
            val prefix = if (tab.id == activeTabId) "● " else "   "
            val pinMark = if (storage.isPinned(tab.url)) "[P] " else ""
            titles.add(prefix + pinMark + tab.title)
        }

        val dlg = AlertDialog.Builder(this)
            .setTitle("Вкладки (" + tabs.size + ")")
            .setItems(titles.toTypedArray()) { _, which ->
                switchToTab(tabs[which].id, true)
            }
            .setPositiveButton("Новая вкладка") { _, _ ->
                ui.animateNewTabGhost(ui.bottomBar) { addTab(home, true) }
            }
            .setNeutralButton("Закрыть все") { _, _ -> confirmCloseAll() }
            .setNegativeButton("Отмена", null)
            .create()
        dlg.setOnShowListener {
            val dv = dlg.window?.decorView
            if (dv != null) {
                dv.alpha = 0f
                dv.scaleX = 0.6f
                dv.scaleY = 0.6f
                dv.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(260)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }
        }
        dlg.show()
    }

    private fun showHistoryDialog() {
        val history = storage.getHistory()
        if (history.isEmpty()) {
            Toast.makeText(this, "История пуста", Toast.LENGTH_SHORT).show()
            return
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle("История поиска (" + history.size + ")")
            .setItems(history.toTypedArray()) { _, which ->
                ui.urlBox.setText(history[which])
                open(history[which])
            }
            .setNeutralButton("Очистить") { _, _ ->
                storage.clearHistory()
                Toast.makeText(this, "История очищена", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .create()
        dlg.setOnShowListener {
            val dv = dlg.window?.decorView
            if (dv != null) {
                dv.alpha = 0f
                dv.scaleX = 0.6f
                dv.scaleY = 0.6f
                dv.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(260)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }
        }
        dlg.show()
    }

    private fun showSettingsDialog() {
        val items = arrayOf(
            "История поиска",
            "Очистить кэш и cookies",
            "Очистить историю поиска",
            "Открыть домашнюю (Google)",
            "Новая вкладка",
            "Закрыть все вкладки",
            "О приложении"
        )
        val dlg = AlertDialog.Builder(this)
            .setTitle("Настройки")
            .setItems(items) { _, which ->
                if (which == 0) {
                    showHistoryDialog()
                } else if (which == 1) {
                    wipeAll()
                    Toast.makeText(this, "Очищено", Toast.LENGTH_SHORT).show()
                } else if (which == 2) {
                    storage.clearHistory()
                    Toast.makeText(this, "История очищена", Toast.LENGTH_SHORT).show()
                } else if (which == 3) {
                    val t = activeTab
                    if (t != null) t.webView.loadUrl(home)
                } else if (which == 4) {
                    addTab(home, true)
                } else if (which == 5) {
                    confirmCloseAll()
                } else if (which == 6) {
                    AlertDialog.Builder(this)
                        .setTitle("Приватный браузер")
                        .setMessage("Версия 1.1")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .create()
        dlg.setOnShowListener {
            val dv = dlg.window?.decorView
            if (dv != null) {
                dv.alpha = 0f
                dv.scaleX = 0.6f
                dv.scaleY = 0.6f
                dv.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(260)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }
        }
        dlg.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val data = intent.dataString
        if (data != null) {
            open(data)
        } else {
            val t = activeTab
            if (t != null) t.webView.loadUrl(home)
        }
    }

    private fun open(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        val target: String
        if (t.startsWith("https://")) {
            target = t
        } else if (t.startsWith("http://")) {
            target = "https://" + t.substring(7)
        } else if (!t.contains(" ") && t.contains(".")) {
            target = "https://" + t
        } else {
            target = searchUrl + android.net.Uri.encode(t)
        }
        storage.addHistory(t)
        ui.urlBox.clearFocus()
        val tab = activeTab
        if (tab != null) tab.webView.loadUrl(target)
    }

    private fun wipeAll() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        for (t in tabs) {
            t.webView.clearCache(true)
            t.webView.clearHistory()
        }
        WebStorage.getInstance().deleteAllData()
    }

    override fun onPause() {
        super.onPause()
        saveSession()
    }

    override fun onDestroy() {
        saveSession()
        wipeAll()
        for (t in tabs) t.webView.destroy()
        super.onDestroy()
    }
}
