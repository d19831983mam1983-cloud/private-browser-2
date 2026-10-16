package com.example.privatebrowser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
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
    private lateinit var tabStripWrap: HorizontalScrollView
    private lateinit var urlBox: EditText
    private lateinit var bar: ProgressBar
    private lateinit var tabsCountBadge: TextView
    private lateinit var bottomBar: LinearLayout

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

        urlBox = EditText(this).apply {
            hint = "Поиск или адрес"
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
        urlRow.addView(urlBox, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

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

        tabStripWrap = HorizontalScrollView(this).apply {
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

        // ---------- НИЖНЯЯ ПАНЕЛЬ ----------
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = gradientGlass(dp(0))
            elevation = dp(10).toFloat()
        }

        val tabsBtn = textNavButton("☰") { showTabsDialog() }
        // Долгое нажатие на ☰ — быстрое закрытие всех вкладок
        tabsBtn.setOnLongClickListener {
            confirmCloseAll()
            true
        }

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

        val newTabBtn = textNavButton("＋") { animateNewTabFromButton(it) }
        val settingsBtn = textNavButton("⚙") { animateDialogFromButton(it) { showSettingsDialog() } }

        bottomBar.addView(tabsWrapper, LinearLayout.LayoutParams(dp(56), dp(56)))
        bottomBar.addView(View(this), LinearLayout.LayoutParams(dp(32), 1))
        bottomBar.addView(newTabBtn, LinearLayout.LayoutParams(dp(56), dp(56)))
        bottomBar.addView(View(this), LinearLayout.LayoutParams(dp(32), 1))
        bottomBar.addView(settingsBtn, LinearLayout.LayoutParams(dp(56), dp(56)))

        root.addView(bottomBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        setContentView(root)

        // ---------- Анимация появления верхней панели ----------
        topBlock.alpha = 0f
        topBlock.translationY = -dp(60).toFloat()
        topBlock.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // ---------- Анимация появления нижней панели ----------
        bottomBar.alpha = 0f
        bottomBar.translationY = dp(60).toFloat()
        bottomBar.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(120)
            .setInterpolator(DecelerateInterpolator())
            .start()

        addTab(home, animate = false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val t = activeTab
                if (t != null && t.webView.canGoBack()) t.webView.goBack() else finish()
            }
        })
    }

    // ================== ЗАКРЫТИЕ ВСЕХ ВКЛАДОК ==================

    /** Закрыть все вкладки и открыть одну пустую (Google) */
    private fun closeAllTabs() {
        if (tabs.size <= 1 && tabs.firstOrNull()?.url == home) {
            // Если одна вкладка и она на домашней — просто перезагрузить
            tabs.firstOrNull()?.webView?.loadUrl(home)
            Toast.makeText(this, "Все вкладки закрыты", Toast.LENGTH_SHORT).show()
            return
        }

        // Уничтожаем все WebView
        tabs.forEach { tab ->
            webContainer.removeView(tab.webView)
            tab.webView.destroy()
        }
        tabs.clear()

        // Создаём одну новую
        addTab(home, animate = true)

        // Анимируем ленту — она "схлопывается"
        tabStrip.animate()
            .alpha(0.4f)
            .setDuration(120)
            .withEndAction {
                renderTabStrip()
                tabStrip.animate().alpha(1f).setDuration(200).start()
            }
            .start()

        Toast.makeText(this, "Все вкладки закрыты", Toast.LENGTH_SHORT).show()
    }

    /** Диалог подтверждения перед закрытием всех вкладок */
    private fun confirmCloseAll() {
        AlertDialog.Builder(this)
            .setTitle("Закрыть все вкладки?")
            .setMessage("Будет закрыто вкладок: ${tabs.size}\nОстанется одна пустая.")
            .setPositiveButton("Закрыть все") { _, _ -> closeAllTabs() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ================== АНИМАЦИИ ==================

    private fun animateNewTabFromButton(button: View) {
        val startX = IntArray(2).also { button.getLocationOnScreen(it) }[0]
        val startY = IntArray(2).also { button.getLocationOnScreen(it) }[1]
        val startW = button.width
        val startH = button.height

        val ghost = TextView(this).apply {
            text = "Новая вкладка"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = resources.getDrawable(R.drawable.bg_tab_active, theme)
            alpha = 0.95f
            elevation = dp(12).toFloat()
        }
        val rootView = window.decorView as ViewGroup
        val lp = FrameLayout.LayoutParams(startW, startH).apply {
            leftMargin = startX
            topMargin = startY
        }
        rootView.addView(ghost, lp)

        val endX = IntArray(2).also { tabStrip.getLocationOnScreen(it) }[0] + dp(8)
        val endY = IntArray(2).also { tabStrip.getLocationOnScreen(it) }[1]

        val animX = ValueAnimator.ofInt(startX, endX).apply {
            duration = 420
            interpolator = DecelerateInterpolator()
            addUpdateListener { ghost.translationX = (it.animatedValue as Int - startX).toFloat() }
        }
        val animY = ValueAnimator.ofInt(startY, endY).apply {
            duration = 420
            interpolator = DecelerateInterpolator()
            addUpdateListener { ghost.translationY = (it.animatedValue as Int - startY).toFloat() }
        }
        val animW = ValueAnimator.ofInt(startW, dp(140)).apply {
            duration = 420
            addUpdateListener {
                ghost.layoutParams = (ghost.layoutParams as FrameLayout.LayoutParams).apply {
                    width = it.animatedValue as Int
                }
            }
        }

        button.animate().scaleX(0.85f).scaleY(0.85f).setDuration(120)
            .withEndAction {
                button.animate().scaleX(1f).scaleY(1f).setDuration(180)
                    .setInterpolator(OvershootInterpolator()).start()
            }.start()

        animX.start(); animY.start(); animW.start()

        animX.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                ghost.animate().alpha(0f).setDuration(120).withEndAction {
                    rootView.removeView(ghost)
                }.start()
                addTab(home, animate = true)
            }
        })
    }

    private fun animateDialogFromButton(button: View, show: () -> Unit) {
        button.animate().scaleX(0.85f).scaleY(0.85f).setDuration(120)
            .withEndAction {
                button.animate().scaleX(1f).scaleY(1f).setDuration(200)
                    .setInterpolator(OvershootInterpolator()).start()
            }.start()
        button.postDelayed({ show() }, 140)
    }

    // ================== ВКЛАДКИ ==================

    @SuppressLint("SetJavaScriptEnabled")
    private fun addTab(initialUrl: String, animate: Boolean = true) {
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

        switchToTab(tab.id, animateTab = animate)
        wv.loadUrl(initialUrl)
        renderTabStrip()
    }

    private fun switchToTab(id: Int, animateTab: Boolean = true) {
        activeTabId = id
        tabs.forEach { t ->
            val show = t.id == id
            t.webView.visibility = if (show) View.VISIBLE else View.GONE
            if (show && animateTab) {
                t.webView.alpha = 0f
                t.webView.animate().alpha(1f).setDuration(220).start()
            }
        }
        val t = activeTab ?: return
        if (!urlBox.hasFocus()) urlBox.setText(t.url)
        renderTabStrip()
    }

    private fun closeTab(id: Int) {
        val t = tabs.firstOrNull { it.id == id } ?: return
        webContainer.removeView(t.webView)
        t.webView.destroy()
        tabs.remove(t)

        if (tabs.isEmpty()) {
            addTab(home, animate = false)
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
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setOnClickListener { switchToTab(tab.id) }
                setOnLongClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(tab.title)
                        .setItems(arrayOf(
                            "Копировать название",
                            "Копировать адрес",
                            "Закрыть вкладку",
                            "Закрыть все вкладки"
                        )) { _, which ->
                            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            when (which) {
                                0 -> {
                                    cm.setPrimaryClip(ClipData.newPlainText("title", tab.title))
                                    Toast.makeText(this@MainActivity, "Название скопировано", Toast.LENGTH_SHORT).show()
                                }
                                1 -> {
                                    cm.setPrimaryClip(ClipData.newPlainText("url", tab.url))
                                    Toast.makeText(this@MainActivity, "Адрес скопирован", Toast.LENGTH_SHORT).show()
                                }
                                2 -> closeTab(tab.id)
                                3 -> confirmCloseAll()
                            }
                        }
                        .show()
                    true
                }
            }

            val titleView = TextView(this).apply {
                text = if (tab.title.length > 18) tab.title.take(18) + "…" else tab.title
                setTextColor(Color.WHITE)
                textSize = 13f
                maxLines = 1
            }
            pill.addView(titleView)

            val lp = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                marginEnd = dp(8)
            }
  
