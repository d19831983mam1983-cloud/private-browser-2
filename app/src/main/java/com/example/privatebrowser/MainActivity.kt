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
import android.view.MotionEvent
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
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
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
    private val doh = DohProxy()
    private val store: Store by lazy { Store(applicationContext) }
    private val animatedIds = HashSet<Int>()

    private val duckUrl = "https://duckduckgo.com/?q="
    private val googleUrl = "https://www.google.com/search?q="
    private var searchUrl = "https://duckduckgo.com/?q="
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

    // ---------- helpers ----------

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }

    private fun gradientGlass(radius: Int): GradientDrawable {
        val g = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#26FFFFFF"), Color.parseColor("#12FFFFFF"))
        )
        g.cornerRadius = radius.toFloat()
        g.setStroke(dp(1), Color.parseColor("#33FFFFFF"))
        return g
    }

    private fun textNavButton(label: String): TextView {
        val b = TextView(this)
        b.text = label
        b.setTextColor(Color.WHITE)
        b.textSize = 20f
        b.gravity = Gravity.CENTER
        b.background = gradientGlass(dp(28))
        b.isClickable = true
        b.isFocusable = true
        return b
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun pressFx(v: View) {
        v.setOnTouchListener { view, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
            } else if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(160)
                    .setInterpolator(OvershootInterpolator()).start()
            }
            false
        }
    }

    // ---------- lifecycle ----------

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#0B0F1A"))
        root.fitsSystemWindows = true

        val topBlock = LinearLayout(this)
        topBlock.orientation = LinearLayout.VERTICAL
        topBlock.setPadding(dp(10), dp(10), dp(10), dp(8))
        topBlock.background = gradientGlass(dp(0))
        topBlock.elevation = dp(8).toFloat()

        val urlRow = LinearLayout(this)
        urlRow.orientation = LinearLayout.HORIZONTAL
        urlRow.gravity = Gravity.CENTER_VERTICAL
        urlRow.background = gradientGlass(dp(26))
        urlRow.setPadding(dp(6), dp(6), dp(6), dp(6))
        urlRow.elevation = dp(6).toFloat()

        urlBox = EditText(this)
        urlBox.hint = "Поиск или адрес"
        urlBox.setHintTextColor(Color.parseColor("#CCFFFFFF"))
        urlBox.setTextColor(Color.WHITE)
        urlBox.textSize = 15f
        urlBox.setSingleLine()
        urlBox.setPadding(dp(14), dp(10), dp(14), dp(10))
        urlBox.background = gradientGlass(dp(22))
        urlBox.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        urlBox.imeOptions = EditorInfo.IME_ACTION_GO
        urlBox.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                open(urlBox.text.toString())
                true
            } else false
        }
        urlBox.setOnLongClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("url", urlBox.text.toString()))
            Toast.makeText(this@MainActivity, "Скопировано", Toast.LENGTH_SHORT).show()
            true
        }
        urlRow.addView(urlBox, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        topBlock.addView(urlRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        bar.visibility = View.GONE
        bar.progressTintList = android.content.res.ColorStateList.valueOf(
            Color.parseColor("#5B8DEF")
        )
        topBlock.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, dp(2)))

        tabStripWrap = HorizontalScrollView(this)
        tabStripWrap.isHorizontalScrollBarEnabled = false
        tabStripWrap.setPadding(0, dp(8), 0, 0)

        tabStrip = LinearLayout(this)
        tabStrip.orientation = LinearLayout.HORIZONTAL
        tabStrip.gravity = Gravity.CENTER_VERTICAL
        tabStripWrap.addView(tabStrip)
        topBlock.addView(tabStripWrap, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        root.addView(topBlock, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        webContainer = FrameLayout(this)
        root.addView(webContainer, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        bottomBar = LinearLayout(this)
        bottomBar.orientation = LinearLayout.HORIZONTAL
        bottomBar.gravity = Gravity.CENTER
        bottomBar.setPadding(dp(16), dp(10), dp(16), dp(10))
        bottomBar.background = gradientGlass(dp(0))
        bottomBar.elevation = dp(10).toFloat()

        val tabsBtn = textNavButton("[=]")
        tabsBtn.setOnClickListener { showTabsDialog() }
        tabsBtn.setOnLongClickListener {
            confirmCloseAll()
            true
        }

        val tabsWrapper = FrameLayout(this)
        tabsWrapper.addView(tabsBtn, FrameLayout.LayoutParams(dp(56), dp(56)))
        tabsCountBadge = TextView(this)
        tabsCountBadge.setTextColor(Color.WHITE)
        tabsCountBadge.textSize = 10f
        tabsCountBadge.gravity = Gravity.CENTER
        val badgeBg = GradientDrawable()
        badgeBg.shape = GradientDrawable.OVAL
        badgeBg.setColor(Color.parseColor("#5B8DEF"))
        tabsCountBadge.background = badgeBg
        val badgeLp = FrameLayout.LayoutParams(dp(20), dp(20))
        badgeLp.gravity = Gravity.TOP or Gravity.END
        tabsWrapper.addView(tabsCountBadge, badgeLp)

        val newTabBtn = textNavButton("+")
        newTabBtn.setOnClickListener { animateNewTabFromButton(it) }

        val refreshBtn = textNavButton("↻")
        refreshBtn.setOnClickListener {
            val t = activeTab
            if (t != null) {
                if (t.webView.url == null) t.webView.loadUrl(t.url) else t.webView.reload()
                it.animate().rotationBy(360f).setDuration(450)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
        }

        val settingsBtn = textNavButton("*")
        settingsBtn.setOnClickListener { animateDialogFromButton(it) { showSettingsDialog() } }

        pressFx(tabsBtn)
        pressFx(newTabBtn)
        pressFx(refreshBtn)
        pressFx(settingsBtn)

        bottomBar.addView(tabsWrapper, LinearLayout.LayoutParams(dp(56), dp(56)))
        bottomBar.addView(View(this), LinearLayout.LayoutParams(dp(20), 1))
        bottomBar.addView(newTabBtn, LinearLayout.LayoutParams(dp(56), dp(56)))
        bottomBar.addView(View(this), LinearLayout.LayoutParams(dp(20), 1))
        bottomBar.addView(refreshBtn, LinearLayout.LayoutParams(dp(56), dp(56)))
        bottomBar.addView(View(this), LinearLayout.LayoutParams(dp(20), 1))
        bottomBar.addView(settingsBtn, LinearLayout.LayoutParams(dp(56), dp(56)))

        root.addView(bottomBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        setContentView(root)

        topBlock.alpha = 0f
        topBlock.translationY = -dp(60).toFloat()
        topBlock.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator())
            .start()

        bottomBar.alpha = 0f
        bottomBar.translationY = dp(60).toFloat()
        bottomBar.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(120)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Чистим остатки прошлой сессии, если приложение было убито системой
        wipeAll()

        val startUrl = intent?.dataString
        startWithDns {
            restorePinned()
            if (startUrl != null) addTab(startUrl, false) else addTab(home, false)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val t = activeTab
                if (t != null && t.webView.canGoBack()) t.webView.goBack() else finish()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val data = intent.dataString
        if (data != null) addTab(data, true)
    }

    override fun onPause() {
        savePinned()
        super.onPause()
    }

    override fun onDestroy() {
        wipeAll()
        doh.stop()
        for (t in tabs) {
            webContainer.removeView(t.webView)
            t.webView.destroy()
        }
        tabs.clear()
        super.onDestroy()
    }

    // ---------- DNS (AdGuard / Comss.one через локальный прокси) ----------

    private fun startWithDns(onReady: () -> Unit) {
        var started = false
        try {
            val port = doh.start()
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                val cfg = ProxyConfig.Builder().addProxyRule("127.0.0.1:" + port).build()
                ProxyController.getInstance().setProxyOverride(
                    cfg,
                    ContextCompat.getMainExecutor(this),
                    Runnable { onReady() }
                )
                started = true
            }
        } catch (e: Exception) {
            started = false
        }
        if (!started) {
            Toast.makeText(
                this,
                "DNS-защита недоступна: обновите Android System WebView",
                Toast.LENGTH_LONG
            ).show()
            onReady()
        }
    }

    // ---------- animations ----------

    private fun animateNewTabFromButton(button: View) {
        val startLoc = IntArray(2)
        button.getLocationOnScreen(startLoc)
        val startX = startLoc[0]
        val startY = startLoc[1]
        val startW = button.width
        val startH = button.height

        val ghost = TextView(this)
        ghost.text = "Новая вкладка"
        ghost.setTextColor(Color.WHITE)
        ghost.textSize = 13f
        ghost.gravity = Gravity.CENTER_VERTICAL
        ghost.setPadding(dp(14), 0, dp(14), 0)
        ghost.background = resources.getDrawable(R.drawable.bg_tab_active, theme)
        ghost.alpha = 0.95f
        ghost.elevation = dp(12).toFloat()

        val rootView = window.decorView as ViewGroup
        val lp = FrameLayout.LayoutParams(startW, startH)
        lp.leftMargin = startX
        lp.topMargin = startY
        rootView.addView(ghost, lp)

        val endLoc = IntArray(2)
        tabStrip.getLocationOnScreen(endLoc)
        val endX = endLoc[0] + dp(8)
        val endY = endLoc[1]

        val animX = ValueAnimator.ofInt(startX, endX)
        animX.duration = 420
        animX.interpolator = DecelerateInterpolator()
        animX.addUpdateListener {
            ghost.translationX = ((it.animatedValue as Int) - startX).toFloat()
        }

        val animY = ValueAnimator.ofInt(startY, endY)
        animY.duration = 420
        animY.interpolator = DecelerateInterpolator()
        animY.addUpdateListener {
            ghost.translationY = ((it.animatedValue as Int) - startY).toFloat()
        }

        val animW = ValueAnimator.ofInt(startW, dp(140))
        animW.duration = 420
        animW.addUpdateListener {
            val p = ghost.layoutParams as FrameLayout.LayoutParams
            p.width = it.animatedValue as Int
            ghost.layoutParams = p
        }

        animX.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                ghost.animate().alpha(0f).setDuration(120).withEndAction {
                    rootView.removeView(ghost)
                }.start()
                addTab(home, true)
            }
        })

        animX.start()
        animY.start()
        animW.start()
    }

    private fun animateDialogFromButton(button: View, show: () -> Unit) {
        button.postDelayed({ show() }, 120)
    }

    // ---------- tabs ----------

    @SuppressLint("SetJavaScriptEnabled")
    private fun addTab(
        initialUrl: String,
        animate: Boolean,
        pinned: Boolean = false,
        savedTitle: String = "",
        activate: Boolean = true
    ) {
        val id = nextTabId
        nextTabId = nextTabId + 1
        val tab = BrowserTab(id)
        tab.pinned = pinned
        if (savedTitle.isNotEmpty()) tab.title = savedTitle

        val wv = WebView(this)
        wv.setBackgroundColor(Color.parseColor("#0B0F1A"))
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        wv.settings.allowFileAccess = false
        wv.settings.allowContentAccess = false
        wv.settings.setGeolocationEnabled(false)
        wv.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        @Suppress("DEPRECATION")
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
                if (tab.id == activeTabId && !urlBox.hasFocus()) urlBox.setText(url)
            }

            override fun onPageFinished(v: WebView, url: String) {
                val title = v.title
                if (title != null) tab.title = title else tab.title = url
                if (tab.pinned) savePinned()
                renderTabStrip()
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                if (tab.id != activeTabId) return
                if (p in 1..99) {
                    bar.animate().cancel()
                    bar.alpha = 1f
                    bar.visibility = View.VISIBLE
                    bar.setProgress(p, true)
                } else if (p >= 100) {
                    bar.setProgress(100, true)
                    bar.animate().alpha(0f).setDuration(300).setStartDelay(150)
                        .withEndAction {
                            bar.visibility = View.GONE
                            bar.alpha = 1f
                            bar.progress = 0
                        }.start()
                }
            }

            override fun onReceivedTitle(v: WebView?, title: String?) {
                if (title != null) {
                    tab.title = title
                    renderTabStrip()
                }
            }
        }

        tab.webView = wv
        tab.url = initialUrl
        tabs.add(tab)
        webContainer.addView(wv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        if (activate) {
            switchToTab(tab.id, animate)
            wv.loadUrl(initialUrl)
        } else {
            tab.needsLoad = true
        }
        renderTabStrip()
    }

    private fun switchToTab(id: Int, animate: Boolean) {
        activeTabId = id
        for (t in tabs) {
            if (t.id == id) {
                t.webView.visibility = View.VISIBLE
                if (t.needsLoad) {
                    t.needsLoad = false
                    t.webView.loadUrl(t.url)
                }
                if (animate) {
                    t.webView.alpha = 0f
                    t.webView.animate().alpha(1f).setDuration(220).start()
                }
            } else {
                          }
        
        }
        val t = activeTab
        if (t != null && !urlBox.hasFocus()) urlBox.setText(t.url)
        renderTabStrip()
    }

    private fun closeTab(id: Int) {
        var target: BrowserTab? = null
        for (t in tabs) if (t.id == id) target = t
        if (target == null) return
        webContainer.removeView(target.webView)
        target.webView.destroy()
        tabs.remove(target)
        if (target.pinned) savePinned()

        if (tabs.isEmpty()) {
            addTab(home, false)
            return
        }
        if (activeTabId == id) switchToTab(tabs[tabs.size - 1].id, true)
        renderTabStrip()
    }

    private fun closeAllTabs() {
        val toClose = ArrayList<BrowserTab>()
        for (t in tabs) if (!t.pinned) toClose.add(t)
        for (t in toClose) {
            webContainer.removeView(t.webView)
            t.webView.destroy()
            tabs.remove(t)
        }
        addTab(home, true)
        Toast.makeText(this, "Вкладки закрыты", Toast.LENGTH_SHORT).show()
    }

    private fun confirmCloseAll() {
        var count = 0
        for (t in tabs) if (!t.pinned) count = count + 1
        AlertDialog.Builder(this)
            .setTitle("Закрыть все вкладки?")
            .setMessage("Будет закрыто вкладок: " + count + " (закреплённые останутся)")
            .setPositiveButton("Закрыть все") { _, _ -> closeAllTabs() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun renderTabStrip() {
        tabStrip.removeAllViews()
        var activePill: View? = null
        for (tab in tabs) {
            val isActive = tab.id == activeTabId

            val pill = LinearLayout(this)
            pill.orientation = LinearLayout.HORIZONTAL
            pill.gravity = Gravity.CENTER_VERTICAL
            if (isActive) {
                pill.background = resources.getDrawable(R.drawable.bg_tab_active, theme)
            } else {
                pill.background = resources.getDrawable(R.drawable.bg_tab_inactive, theme)
            }
            pill.setPadding(dp(14), dp(8), dp(14), dp(8))
            pill.setOnClickListener { switchToTab(tab.id, true) }
            pill.setOnLongClickListener {
                showTabMenu(tab)
                true
            }

            val titleView = TextView(this)
            var title = tab.title
            if (title.length > 18) title = title.substring(0, 18) + "..."
            if (tab.pinned) title = "📌 " + title
            titleView.text = title
            titleView.setTextColor(Color.WHITE)
            titleView.textSize = 13f
            titleView.maxLines = 1
            pill.addView(titleView)

            val lp = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            lp.marginEnd = dp(8)
            tabStrip.addView(pill, lp)
            if (isActive) activePill = pill

            // Анимация появления только у новых вкладок, остальные не мигают
            if (!animatedIds.contains(tab.id)) {
                animatedIds.add(tab.id)
                pill.alpha = 0f
                pill.scaleX = 0.7f
                pill.scaleY = 0.7f
                pill.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(280)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }
        }
