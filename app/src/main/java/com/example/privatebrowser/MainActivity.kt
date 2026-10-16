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
        get() {
            for (t in tabs) if (t.id == activeTabId) return t
            return null
        }

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

        val settingsBtn = textNavButton("*")
        settingsBtn.setOnClickListener { animateDialogFromButton(it) { showSettingsDialog() } }

        bottomBar.addView(tabsWrapper, LinearLayout.LayoutParams(dp(56), dp(56)))
        bottomBar.addView(View(this), LinearLayout.LayoutParams(dp(32), 1))
        bottomBar.addView(newTabBtn, LinearLayout.LayoutParams(dp(56), dp(56)))
        bottomBar.addView(View(this), LinearLayout.LayoutParams(dp(32), 1))
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

        addTab(home, false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val t = activeTab
                if (t != null && t.webView.canGoBack()) t.webView.goBack() else finish()
            }
        })
    }

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

        button.animate().scaleX(0.85f).scaleY(0.85f).setDuration(120)
            .withEndAction {
                button.animate().scaleX(1f).scaleY(1f).setDuration(180)
                    .setInterpolator(OvershootInterpolator()).start()
            }.start()

        animX.start()
        animY.start()
        animW.start()

        animX.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                ghost.animate().alpha(0f).setDuration(120).withEndAction {
                    rootView.removeView(ghost)
                }.start()
                addTab(home, true)
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
                if (tab.id == activeTabId && !urlBox.hasFocus()) urlBox.setText(url)
            }

            override fun onPageFinished(v: WebView, url: String) {
                val title = v.title
                if (title != null) tab.title = title else tab.title = url
                renderTabStrip()
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                if (tab.id != activeTabId) return
                bar.progress = p
                if (p in 1..99) bar.visibility = View.VISIBLE else bar.visibility = View.GONE
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

        if (tabs.isEmpty()) {
            addTab(home, false)
            return
        }
        if (activeTabId == id) switchToTab(tabs[tabs.size - 1].id, true)
        renderTabStrip()
    }

    private fun closeAllTabs() {
        for (t in tabs) {
            webContainer.removeView(t.webView)
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
        tabStrip.removeAllViews()
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
            titleView.text = title
            titleView.setTextColor(Color.WHITE)
            titleView.textSize = 13f
            titleView.maxLines = 1
            pill.addView(titleView)

            val lp = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            lp.marginEnd = dp(8)
            tabStrip.addView(pill, lp)

            pill.alpha = 0f
            pill.scaleX = 0.7f
            pill.scaleY = 0.7f
            pill.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(280)
                .setInterpolator(OvershootInterpolator())
                .start()
        }

        tabsCountBadge.text = tabs.size.toString()

        tabStripWrap.post {
            tabStripWrap.smoothScrollTo(tabStrip.width, 0)
        }
    }

    private fun showTabMenu(tab: BrowserTab) {
        val items = arrayOf(
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
                    cm.setPrimaryClip(ClipData.newPlainText("title", tab.title))
                    Toast.makeText(this, "Название скопировано", Toast.LENGTH_SHORT).show()
                } else if (which == 1) {
                    cm.setPrimaryClip(ClipData.newPlainText("url", tab.url))
                    Toast.makeText(this, "Адрес скопирован", Toast.LENGTH_SHORT).show()
                } else if (which == 2) {
                    closeTab(tab.id)
                } else if (which == 3) {
                    confirmCloseAll()
                }
            }
            .show()
    }

    private fun showTabsDialog() {
        val titles = ArrayList<String>()
        for (tab in tabs) {
            val prefix = if (tab.id == activeTabId) "● " else "   "
            titles.add(prefix + tab.title)
        }

        val dlg = AlertDialog.Builder(this)
            .setTitle("Вкладки (" + tabs.size + ")")
            .setItems(titles.toTypedArray()) { _, which ->
                switchToTab(tabs[which].id, true)
            }
            .setPositiveButton("Новая вкладка") { _, _ -> animateNewTabFromButton(bottomBar) }
            .setNeutralButton("Закрыть все") { _, _ -> confirmCloseAll() }
            .setNegativeButton("Отмена", null)
            .create()
        dlg.setOnShowListener {
            val dv = dlg.window?.decorView
            if (dv != null) {
