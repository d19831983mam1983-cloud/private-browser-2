package com.example.privatebrowser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class BrowserUI(private val ctx: Context) {

    lateinit var root: LinearLayout
    lateinit var topBlock: LinearLayout
    lateinit var urlBox: EditText
    lateinit var bar: ProgressBar
    lateinit var tabStrip: LinearLayout
    lateinit var tabStripWrap: HorizontalScrollView
    lateinit var webContainer: FrameLayout
    lateinit var bottomBar: LinearLayout
    lateinit var tabsCountBadge: TextView

    var onOpen: ((String) -> Unit)? = null
    var onTabsDialog: (() -> Unit)? = null
    var onConfirmCloseAll: (() -> Unit)? = null
    var onNewTab: ((View) -> Unit)? = null
    var onSettings: ((View) -> Unit)? = null
    var onUrlLongPress: ((String) -> Unit)? = null

    fun build(): LinearLayout {
        root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#0B0F1A"))
        root.fitsSystemWindows = true

        topBlock = LinearLayout(ctx)
        topBlock.orientation = LinearLayout.VERTICAL
        topBlock.setPadding(dp(10), dp(10), dp(10), dp(8))
        topBlock.background = gradientGlass(0)
        topBlock.elevation = dp(8).toFloat()

        val urlRow = LinearLayout(ctx)
        urlRow.orientation = LinearLayout.HORIZONTAL
        urlRow.gravity = Gravity.CENTER_VERTICAL
        urlRow.background = gradientGlass(26)
        urlRow.setPadding(dp(6), dp(6), dp(6), dp(6))
        urlRow.elevation = dp(6).toFloat()

        urlBox = EditText(ctx)
        urlBox.hint = "Поиск или адрес"
        urlBox.setHintTextColor(Color.parseColor("#CCFFFFFF"))
        urlBox.setTextColor(Color.WHITE)
        urlBox.textSize = 15f
        urlBox.setSingleLine()
        urlBox.setPadding(dp(14), dp(10), dp(14), dp(10))
        urlBox.background = gradientGlass(22)
        urlBox.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        urlBox.imeOptions = EditorInfo.IME_ACTION_GO
        urlBox.setOnEditorActionListener { _, a, _ ->
            if (a == EditorInfo.IME_ACTION_GO) { onOpen?.invoke(urlBox.text.toString()); true } else false
        }
        urlBox.setOnLongClickListener { onUrlLongPress?.invoke(urlBox.text.toString()); true }
        urlRow.addView(urlBox, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        topBlock.addView(urlRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        bar.visibility = View.GONE
        bar.progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#5B8DEF"))
        topBlock.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, dp(2)))

        tabStripWrap = HorizontalScrollView(ctx)
        tabStripWrap.isHorizontalScrollBarEnabled = false
        tabStripWrap.setPadding(0, dp(8), 0, 0)

        tabStrip = LinearLayout(ctx)
        tabStrip.orientation = LinearLayout.HORIZONTAL
        tabStrip.gravity = Gravity.CENTER_VERTICAL
        tabStripWrap.addView(tabStrip)
        topBlock.addView(tabStripWrap, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(topBlock, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        webContainer = FrameLayout(ctx)
        root.addView(webContainer, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        bottomBar = LinearLayout(ctx)
        bottomBar.orientation = LinearLayout.HORIZONTAL
        bottomBar.gravity = Gravity.CENTER
        bottomBar.setPadding(dp(16), dp(10), dp(16), dp(10))
        bottomBar.background = gradientGlass(0)
        bottomBar.elevation = dp(10).toFloat()

        val tabsBtn = textNavButton("[=]")
        tabsBtn.setOnClickListener { onTabsDialog?.invoke() }
        tabsBtn.setOnLongClickListener { onConfirmCloseAll?.invoke(); true }

        val tabsWrapper = FrameLayout(ctx)
        tabsWrapper.addView(tabsBtn, FrameLayout.LayoutParams(dp(56), dp(56)))
        tabsCountBadge = TextView(ctx)
        tabsCountBadge.setTextColor(Color.WHITE)
        tabsCountBadge.textSize = 10f
        tabsCountBadge.gravity = Gravity.CENTER
        val bbg = GradientDrawable()
        bbg.shape = GradientDrawable.OVAL
        bbg.setColor(Color.parseColor("#5B8DEF"))
        tabsCountBadge.background = bbg
        val blp = FrameLayout.LayoutParams(dp(20), dp(20))
        blp.gravity = Gravity.TOP or Gravity.END
        tabsWrapper.addView(tabsCountBadge, blp)

        val newTabBtn = textNavButton("+")
        newTabBtn.setOnClickListener { onNewTab?.invoke(it) }
        val settingsBtn = textNavButton("*")
        settingsBtn.setOnClickListener { onSettings?.invoke(it) }

        bottomBar.addView(tabsWrapper, LinearLayout.LayoutParams(dp(56), dp(56)))
        bottomBar.addView(View(ctx), LinearLayout.LayoutParams(dp(32), 1))
        bottomBar.addView(newTabBtn, LinearLayout.LayoutParams(dp(56), dp(56)))
        bottomBar.addView(View(ctx), LinearLayout.LayoutParams(dp(32), 1))
        bottomBar.addView(settingsBtn, LinearLayout.LayoutParams(dp(56), dp(56)))
        root.addView(bottomBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return root
    }

    fun animatePanelsIn() {
        topBlock.alpha = 0f
        topBlock.translationY = -dp(60).toFloat()
        topBlock.animate().alpha(1f).translationY(0f).setDuration(1000)
            .setInterpolator(DecelerateInterpolator()).start()
        bottomBar.alpha = 0f
        bottomBar.translationY = dp(60).toFloat()
        bottomBar.animate().alpha(1f).translationY(0f).setDuration(1000)
            .setStartDelay(240).setInterpolator(DecelerateInterpolator()).start()
    }

    fun animateNewTabGhost(button: View, onDone: () -> Unit) {
        val s = IntArray(2); button.getLocationOnScreen(s)
        val sx = s[0]; val sy = s[1]; val sw = button.width; val sh = button.height

        val ghost = TextView(ctx)
        ghost.text = "Новая вкладка"
        ghost.setTextColor(Color.WHITE)
        ghost.textSize = 13f
        ghost.gravity = Gravity.CENTER_VERTICAL
        ghost.setPadding(dp(14), 0, dp(14), 0)
        ghost.background = ctx.resources.getDrawable(R.drawable.bg_tab_active, null)
        ghost.alpha = 0.95f
        ghost.elevation = dp(12).toFloat()

        val rv = button.rootView as ViewGroup
        val lp = FrameLayout.LayoutParams(sw, sh)
        lp.leftMargin = sx; lp.topMargin = sy
        rv.addView(ghost, lp)

        val e = IntArray(2); tabStrip.getLocationOnScreen(e)
        val ex = e[0] + dp(8); val ey = e[1]

        val ax = ValueAnimator.ofInt(sx, ex)
        ax.duration = 840
        ax.interpolator = DecelerateInterpolator()
        ax.addUpdateListener { ghost.translationX = ((it.animatedValue as Int) - sx).toFloat() }

        val ay = ValueAnimator.ofInt(sy, ey)
        ay.duration = 840
        ay.interpolator = DecelerateInterpolator()
        ay.addUpdateListener { ghost.translationY = ((it.animatedValue as Int) - sy).toFloat() }

        val aw = ValueAnimator.ofInt(sw, dp(140))
        aw.duration = 840
        aw.addUpdateListener {
            val p = ghost.layoutParams as FrameLayout.LayoutParams
            p.width = it.animatedValue as Int
            ghost.layoutParams = p
        }

        button.animate().scaleX(0.85f).scaleY(0.85f).setDuration(240).withEndAction {
            button.animate().scaleX(1f).scaleY(1f).setDuration(360)
                .setInterpolator(OvershootInterpolator()).start()
        }.start()

        ax.start(); ay.start(); aw.start()

        ax.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) {
                ghost.animate().alpha(0f).setDuration(240).withEndAction {
                    rv.removeView(ghost); onDone()
                }.start()
            }
        })
    }

    fun pulseButton(button: View, after: () -> Unit) {
        button.animate().scaleX(0.85f).scaleY(0.85f).setDuration(240).withEndAction {
            button.animate().scaleX(1f).scaleY(1f).setDuration(400)
                .setInterpolator(OvershootInterpolator()).start()
        }.start()
        button.postDelayed({ after() }, 280)
    }

    fun makeTabPill(isActive: Boolean): LinearLayout {
        val pill = LinearLayout(ctx)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        val r = if (isActive) R.drawable.bg_tab_active else R.drawable.bg_tab_inactive
        pill.background = ctx.resources.getDrawable(r, null)
        pill.setPadding(dp(14), dp(8), dp(14), dp(8))
        return pill
    }

    fun animatePillIn(pill: View) {
        pill.alpha = 0f; pill.scaleX = 0.7f; pill.scaleY = 0.7f
        pill.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(560).setInterpolator(OvershootInterpolator()).start()
    }

    fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun textNavButton(symbol: String): TextView {
        val tv = TextView(ctx)
        tv.text = symbol
        tv.setTextColor(Color.WHITE)
        tv.textSize = 20f
        tv.gravity = Gravity.CENTER
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#66FFFFFF"), Color.parseColor("#33FFFFFF"))
        )
        bg.shape = GradientDrawable.OVAL
        bg.setStroke(dp(1), Color.parseColor("#88FFFFFF"))
        tv.background = bg
        return tv
    }

    private fun gradientGlass(r: Int): GradientDrawable {
        val g = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#66FFFFFF"), Color.parseColor("#33FFFFFF"), Color.parseColor("#22FFFFFF"))
        )
        g.cornerRadius = dp(r).toFloat()
        g.setStroke(dp(1), Color.parseColor("#66FFFFFF"))
        return g
    }
}
