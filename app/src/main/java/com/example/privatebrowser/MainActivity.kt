package com.example.privatebrowser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.webkit.CookieManager
import android.webkit.URLUtil
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
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
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

        setContentView(ui.build())
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
        val u = ArrayList<String>()
        val t2 = ArrayList<String>()
        for (t in tabs) { u.add(t.url); t2.add(t.title) }
        storage.saveSession(u, t2)
    }

    private fun restoreSession() {
        val saved = storage.loadSession()
        var opened = 0
        for (u in saved.urls) if (u.isNotBlank()) { addTab(u, false); opened++ }
        for (u in storage.getPinned()) {
            var has = false
            for (t in tabs) if (t.url == u) has = true
            if (!has) { addTab(u, false); opened++ }
        }
        if (opened == 0) addTab(home, false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun addTab(url: String, animate: Boolean) {
        val id = nextTabId; nextTabId++
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
        wv.setDownloadListener { u, ua, cd, mt, _ -> requestDownload(u, ua, cd, mt) }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val s = r.url.scheme ?: return true
                if (s == "http") { v.loadUrl(r.url.buildUpon().scheme("https").build().toString()); return true }
                return s != "https"
            }
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                val h = r.url.host ?: return null
                for (b in blockedHosts) if (h == b || h.endsWith(".$b"))
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                return null
            }
            override fun onPageStarted(v: WebView, u: String, f: Bitmap?) {
                tab.url = u
                if (tab.id == activeTabId && !ui.urlBox.hasFocus()) ui.urlBox.setText(u)
            }
            override fun onPageFinished(v: WebView, u: String) {
                tab.title = v.title ?: u
                renderTabStrip(); saveSession()
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                if (tab.id != activeTabId) return
                ui.bar.progress = p
                ui.bar.visibility = if (p in 1..99) View.VISIBLE else View.GONE
            }
            override fun onReceivedTitle(v: WebView?, t: String?) {
                if (t != null) { tab.title = t; renderTabStrip() }
            }
        }
        tab.webView = wv
        tabs.add(tab)
        ui.webContainer.addView(wv, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        switchToTab(tab.id, animate)
        wv.loadUrl(url)
        renderTabStrip()
    }

    private fun requestDownload(url: String, ua: String?, cd: String?, mt: String?) {
        try {
            val req = DownloadManager.Request(Uri.parse(url))
            val fn = URLUtil.guessFileName(url, cd, mt)
            req.setMimeType(mt)
            if (ua != null) req.addRequestHeader("User-Agent", ua)
            val ck = CookieManager.getInstance().getCookie(url)
            if (ck != null) req.addRequestHeader("Cookie", ck)
            req.setTitle(fn)
            req.setDescription("Скачивание...")
            req.allowScanningByMediaScanner()
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fn)
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Toast.makeText(this, "Скачивание: $fn", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun switchToTab(id: Int, animate: Boolean) {
        activeTabId = id
        for (t in tabs) {
            if (t.id == id) {
                t.webView.visibility = View.VISIBLE
                if (animate) { t.webView.alpha = 0f; t.webView.animate().alpha(1f).setDuration(440).start() }
            } else t.webView.visibility = View.GONE
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
        if (tabs.isEmpty()) { addTab(home, false); return }
        if (activeTabId == id) switchToTab(tabs[tabs.size - 1].id, true)
        renderTabStrip(); saveSession()
    }

    private fun closeAllTabs() {
        val kept = ArrayList<BrowserTab>()
        for (t in tabs) {
            if (storage.isPinned(t.url)) kept.add(t)
            else { ui.webContainer.removeView(t.webView); t.webView.destroy() }
        }
        tabs.clear(); tabs.addAll(kept)
        if (tabs.isEmpty()) addTab(home, true)
        else switchToTab(tabs[0].id, true)
        renderTabStrip(); saveSession()
        Toast.makeText(this, "Закрыты незакреплённые вкладки", Toast.LENGTH_SHORT).show()
    }

    private fun confirmCloseAll() {
        AlertDialog.Builder(this)
            .setTitle("Закрыть вкладки?")
            .setMessage("Закреплённые вкладки останутся открытыми.")
            .setPositiveButton("Закрыть") { _, _ -> closeAllTabs() }
            .setNegativeButton("Отмена", null).show()
    }

    private fun renderTabStrip() {
        ui.tabStrip.removeAllViews()
        for (tab in tabs) {
            val isActive = tab.id == activeTabId
            val isPinned = storage.isPinned(tab.url)
            val pill = ui.makeTabPill(isActive)
            pill.setOnClickListener { switchToTab(tab.id, true) }
            pill.setOnLongClickListener { showTabMenu(tab); true }
            val tv = TextView(this)
            val prefix = if (isPinned) "[P] " else ""
            var title = tab.title
            if (title.length > 16) title = title.substring(0, 16) + "..."
            tv.text = prefix + title
            tv.setTextColor(Color.WHITE); tv.textSize = 13f; tv.maxLines = 1
            pill.addView(tv)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = ui.dp(8)
            ui.tabStrip.addView(pill, lp)
            ui.animatePillIn(pill)
        }
        ui.tabsCountBadge.text = tabs.size.toString()
        ui.tabStripWrap.post { ui.tabStripWrap.smoothScrollTo(ui.tabStrip.width, 0) }
    }

    private fun showTabMenu(tab: BrowserTab) {
        val isPinned = storage.isPinned(tab.url)
        val pinLabel = if (isPinned) "Открепить вкладку" else "Закрепить вкладку"
        val items = arrayOf(pinLabel, "Копировать название", "Копировать адрес", "Закрыть вкладку", "Закрыть все незакреплённые")
        AlertDialog.Builder(this).setTitle(tab.title).setItems(items) { _, w ->
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            when (w) {
                0 -> {
                    if (isPinned) { storage.unpin(tab.url); Toast.makeText(this, "Откреплена", Toast.LENGTH_SHORT).show() }
                    else { storage.pin(tab.url); Toast.makeText(this, "Закреплена", Toast.LENGTH_SHORT).show() }
                    renderTabStrip()
                }
                1 -> { cm.setPrimaryClip(ClipData.newPlainText("title", tab.title)); Toast.makeText(this, "Название скопировано", Toast.LENGTH_SHORT).show() }
                2 -> { cm.setPrimaryClip(ClipData.newPlainText("url", tab.url)); Toast.makeText(this, "Адрес скопирован", Toast.LENGTH_SHORT).show() }
                3 -> closeTab(tab.id)
                4 -> confirmCloseAll()
            }
        }.show()
    }

    private fun showTabsDialog() {
        val titles = ArrayList<String>()
        for (tab in tabs) {
            val prefix = if (tab.id == activeTabId) "● " else "   "
            val pinMark = if (storage.isPinned(tab.url)) "[P] " else ""
            titles.add(prefix + pinMark + tab.title)
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle("Вкладки (${tabs.size})")
            .setItems(titles.toTypedArray()) { _, w -> switchToTab(tabs[w].id, true) }
            .setPositiveButton("Новая вкладка") { _, _ -> ui.animateNewTabGhost(ui.bottomBar) { addTab(home, true) } }
            .setNeutralButton("Закрыть все") { _, _ -> confirmCloseAll() }
            .setNegativeButton("Отмена", null).create()
        animateDialog(dlg); dlg.show()
    }

    private fun showHistoryDialog() {
        val history = storage.getHistory()
        if (history.isEmpty()) { Toast.makeText(this, "История пуста", Toast.LENGTH_SHORT).show(); return }
        val dlg = AlertDialog.Builder(this)
            .setTitle("История поиска (${history.size})")
            .setItems(history.toTypedArray()) { _, w -> ui.urlBox.setText(history[w]); open(history[w]) }
            .setNeutralButton("Очистить") { _, _ -> storage.clearHistory(); Toast.makeText(this, "История очищена", Toast.LENGTH_SHORT).show() }
            .setNegativeButton("Отмена", null).create()
        animateDialog(dlg); dlg.show()
    }

    private fun showSettingsDialog() {
        val items = arrayOf(
            "История поиска", "Загрузки", "Очистить кэш и cookies",
            "Очистить историю поиска", "Открыть домашнюю (Google)",
            "Новая вкладка", "Закрыть все незакреплённые", "О приложении"
        )
        val dlg = AlertDialog.Builder(this).setTitle("Настройки").setItems(items) { _, w ->
            when (w) {
                0 -> showHistoryDialog()
                1 -> try { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } catch (e: Exception) { Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show() }
                2 -> { wipeAll(); Toast.makeText(this, "Очищено", Toast.LENGTH_SHORT).show() }
                3 -> { storage.clearHistory(); Toast.makeText(this, "История очищена", Toast.LENGTH_SHORT).show() }
                4 -> activeTab?.webView?.loadUrl(home)
                5 -> addTab(home, true)
                6 -> confirmCloseAll()
                7 -> AlertDialog.Builder(this).setTitle("Приватный браузер").setMessage("Версия 1.3").setPositiveButton("OK", null).show()
            }
        }.create()
        animateDialog(dlg); dlg.show()
    }

    private fun animateDialog(dlg: AlertDialog) {
        dlg.setOnShowListener {
            val dv = dlg.window?.decorView
            if (dv != null) {
                dv.alpha = 0f; dv.scaleX = 0.6f; dv.scaleY = 0.6f
                dv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(520).setInterpolator(OvershootInterpolator()).start()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val d = intent.dataString
        if (d != null) open(d) else activeTab?.webView?.loadUrl(home)
    }

    private fun open(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        val target = when {
            t.startsWith("https://") -> t
            t.startsWith("http://") -> "https://" + t.substring(7)
            !t.contains(" ") && t.contains(".") -> "https://$t"
            else -> searchUrl + Uri.encode(t)
        }
        storage.addHistory(t)
        ui.urlBox.clearFocus()
        activeTab?.webView?.loadUrl(target)
    }

    private fun wipeAll() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        for (t in tabs) { t.webView.clearCache(true); t.webView.clearHistory() }
        WebStorage.getInstance().deleteAllData()
    }

    override fun onPause() { super.onPause(); saveSession() }

    override fun onDestroy() {
        saveSession(); wipeAll()
        for (t in tabs) t.webView.destroy()
        super.onDestroy()
    }
}
