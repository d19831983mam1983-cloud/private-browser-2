package com.example.privatebrowser

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webContainer: FrameLayout
    private lateinit var tabStrip: LinearLayout
    private lateinit var urlBox: EditText
    private lateinit var bar: ProgressBar
    private lateinit var tabsCountBadge: TextView
    private lateinit var backBtn: ImageButton

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
        get() = tabs.firstOrNull { it.id == activeTabId }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F1A"))
            fitsSystemWindows = true
        }

        // ---------- ВЕРХНЯЯ ПАНЕЛЬ ----------
        val topBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(8))
            background = gradientGlass(dp(0))
            elevation = dp(8).toFloat()
        }

        val urlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = gradientGlass(dp(26))
            setPadding(dp(6), dp(6), dp(6), dp(6))
            elevation = dp(6).toFloat()
        }

        val homeBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_home)
            background = null
            setColorFilter(Color.WHITE)
            setOnClickListener { activeTab?.webView?.loadUrl(home) }
        }
        urlRow.addView(homeBtn, LinearLayout.LayoutParams(dp(40), dp(40)))

        urlBox = EditText(this).apply {
            hint = "Поиск в DuckDuckGo или адрес"
            setHintTextColor(Color.parseColor("#CCFFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 15f
            setSingleLine()
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = gradientGlass(dp(22))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    open(text.toString()); true
                } else false
            }
        }
        urlBox.setOnLongClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("url", urlBox.text.toString()))
            Toast.makeText(this@MainActivity, "Скопировано", Toast.LENGTH_SHORT).show()
            true
        }
        urlRow.addView(urlBox, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
            marginStart = dp(6); marginEnd = dp(6)
        })

        val wipeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = null
            setColorFilter(Color.WHITE)
            setOnClickListener {
                wipeAll()
                activeTab?.webView?.loadUrl(home)
                Toast.makeText(this@MainActivity, "Данные стёрты", Toast.LENGTH_SHORT).show()
            }
        }
        urlRow.addView(wipeBtn, LinearLayout.LayoutParams(dp(40), dp(40)))

        topBlock.addView(urlRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(
                Color.parseColor("#5B8DEF")
            )
        }
        topBlock.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, dp(2)).apply {
            topMargin = dp(4); marginStart = dp(14); marginEnd = dp(14)
        })

        val tabStripWrap = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(8), 0, 0)
        }
        tabStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tabStripWrap.addView(tabStrip)
        topBlock.addView(tabStripWrap, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        root.addView(topBlock, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // ---------- WEBVIEW ----------
        webContainer = FrameLayout(this)
        root.addView(webContainer, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // ---------- НИЖНЯЯ ПАНЕЛЬ (3 кнопки по центру) ----------
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = gradientGlass(dp(0))
            elevation = dp(10).toFloat()
        }

        backBtn = navButton(R.drawable.ic_back) {
            activeTab?.webView?.let { if (it.canGoBack()) it.goBack() }
        }

        val tabsBtn = navButton(R.drawable.ic_tabs) { showTabsDialog() }
        val tabsWrapper = FrameLayout(this)
        tabsWrapper.addView(tabsBtn, FrameLayout.LayoutParams(dp(56), dp(56)))
        tabsCountBadge = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 10f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#5B8DEF"))
            }
            setPadding(dp(4), dp(1), dp(4), dp(1))
        }
        tabsWrapper.addView(tabsCountBadge, FrameLayout.LayoutParams(dp(20), dp(20)).apply {
            gravity = Gravity.TOP or Gravity.END
        })

        val settingsBtn = navButton(R.drawable.ic_settings) { showSettingsDialog() }

        bottomBar.addView(backBtn, LinearLayout.LayoutParams(dp(56), dp(56)))
        bottomBar.addView(View(this), LinearLayout.LayoutParams(dp(32), 1))
        bottomBar.addView(tabsWrapper, LinearLayout.LayoutParams(dp(56), dp(56)))
        bottomBar.addView(View(this), LinearLayout.LayoutParams(dp(32), 1))
        bottomBar.addView(settingsBtn, LinearLayout.LayoutParams(dp(56), dp(56)))

        root.addView(bottomBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        setContentView(root)

        addTab(home)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val t = activeTab
                if (t != null && t.webView.canGoBack()) t.webView.goBack() else finish()
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun addTab(initialUrl: String) {
        val tab = BrowserTab(nextTabId++)

        val wv = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F1A"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                allowFileAccess = false
                allowContentAccess = false
                setGeolocationEnabled(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                @Suppress("DEPRECATION")
                saveFormData = false
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            visibility = View.GONE
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val scheme = r.url.scheme ?: return true
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
                if (blockedHosts.any { host == it || host.endsWith(".$it") }) {
                    return WebResourceResponse(
                        "text/plain", "utf-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
                return null
            }

            override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) {
                tab.url = url
                if (tab.id == activeTabId && !urlBox.hasFocus()) urlBox.setText(url)
            }

            override fun onPageFinished(v: WebView, url: String) {
                tab.title = v.title ?: url
                renderTabStrip()
                updateNavButtons()
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                if (tab.id != activeTabId) return
                bar.progress = p
                bar.visibility = if (p in 1..99) View.VISIBLE else View.GONE
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
        webContainer.addView(wv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        switchToTab(tab.id)
        wv.loadUrl(initialUrl)
        renderTabStrip()
    }

    private fun switchToTab(id: Int) {
        activeTabId = id
        tabs.forEach { t ->
            t.webView.visibility = if (t.id == id) View.VISIBLE else View.GONE
        }
        val t = activeTab ?: return
        if (!urlBox.hasFocus()) urlBox.setText(t.url)
        renderTabStrip()
        updateNavButtons()
    }

    private fun closeTab(id: Int) {
        val t = tabs.firstOrNull { it.id == id } ?: return
        webContainer.removeView(t.webView)
        t.webView.destroy()
        tabs.remove(t)

        if (tabs.isEmpty()) {
            addTab(home)
            return
        }
        if (activeTabId == id) switchToTab(tabs.last().id)
        renderTabStrip()
    }

    private fun renderTabStrip() {
        tabStrip.removeAllViews()
        tabs.forEach { tab ->
            val isActive = tab.id == activeTabId

            val pill = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = resources.getDrawable(
                    if (isActive) R.drawable.bg_tab_active else R.drawable.bg_tab_inactive,
                    theme
                )
                setPadding(dp(14), dp(8), dp(6), dp(8))
                setOnClickListener { switchToTab(tab.id) }
                setOnLongClickListener {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("tab", tab.title))
                    Toast.makeText(this@MainActivity, "Название скопировано", Toast.LENGTH_SHORT).show()
                    true
                }
            }

            val titleView = TextView(this).apply {
                text = if (tab.title.length > 18) tab.title.take(18) + "…" else tab.title
                setTextColor(Color.WHITE)
                textSize = 13f
                maxLines = 1
                setPadding(0, 0, dp(6), 0)
            }
            pill.addView(titleView)

            val closeBtn = ImageButton(this).apply {
                setImageResource(R.drawable.ic_close)
                background = null
                setColorFilter(Color.parseColor("#DDFFFFFF"))
                setPadding(dp(2), dp(2), dp(2), dp(2))
                setOnClickListener { closeTab(tab.id) }
            }
            pill.addView(closeBtn, LinearLayout.LayoutParams(dp(22), dp(22)))

            val lp = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                marginEnd = dp(8)
            }
            tabStrip.addView(pill, lp)
        }

        tabsCountBadge.text = tabs.size.toString()
    }

    private fun updateNavButtons() {
        val t = activeTab ?: return
        backBtn.alpha = if (t.webView.canGoBack()) 1f else 0.35f
    }

    private fun showTabsDialog() {
        val titles = tabs.map {
            (if (it.id == activeTabId) "● " else "   ") + it.title
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Вкладки (${tabs.size})")
            .setItems(titles) { _, which ->
                switchToTab(tabs[which].id)
            }
            .setPositiveButton("Новая вкладка") { _, _ -> addTab(home) }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Настройки")
            .setItems(arrayOf(
                "Очистить кэш и cookies",
                "Открыть домашнюю (Google)",
                "Новая вкладка",
                "О приложении"
            )) { _, which ->
                when (which) {
                    0 -> {
                        wipeAll()
                        Toast.makeText(this, "Очищено", Toast.LENGTH_SHORT).show()
                    }
                    1 -> activeTab?.webView?.loadUrl(home)
                    2 -> addTab(home)
                    3 -> AlertDialog.Builder(this)
                        .setTitle("Приватный браузер")
                        .setMessage("Версия 1.0\nПоиск: DuckDuckGo\nДомашняя: Google\nБез трекеров, без истории, без рекламы.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val data = intent.dataString
        if (data != null) open(data) else activeTab?.webView?.loadUrl(home)
    }

    private fun open(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        val target = when {
            t.startsWith("https://") -> t
            t.startsWith("http://") -> "https://" + t.removePrefix("http://")
            !t.contains(" ") && t.contains(".") -> "https://$t"
            else -> searchUrl + android.net.Uri.encode(t)
        }
        urlBox.clearFocus()
        activeTab?.webView?.loadUrl(target)
    }

    private fun wipeAll() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        tabs.forEach { it.webView.clearCache(true); it.webView.clearHistory() }
        WebStorage.getInstance().deleteAllData()
    }

    override fun onDestroy() {
        wipeAll()
        tabs.forEach { it.webView.destroy() }
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun navButton(iconRes: Int, onClick: () -> Unit): ImageButton {
        return ImageButton(this).apply {
            setImageResource(iconRes)
            setColorFilter(Color.WHITE)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#66FFFFFF"), Color.parseColor("#33FFFFFF"))
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(1), Color.parseColor("#88FFFFFF"))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun gradientGlass(radiusDp: Int): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#66FFFFFF"),
                Color.parseColor("#33FFFFFF"),
                Color.parseColor("#22FFFFFF")
            )
        ).apply {
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), Color.parseColor("#66FFFFFF"))
        }
    }
}
