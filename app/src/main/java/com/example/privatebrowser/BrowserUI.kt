package com.example.privatebrowser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
        topBlock.background = liquidGlassPanel(0, true)
        topBlock.elevation = dp(10).toFloat()

        val urlRow = LinearLayout(ctx)
        urlRow.orientation = LinearLayout.HORIZONTAL
        urlRow.gravity = Gravity.CENTER_VERTICAL
        urlRow.background = liquidGlassPill(26)
        urlRow.setPadding(dp(6), dp(6), dp(6), dp(6))
        urlRow.elevation = dp(6).toFloat()

        urlBox = EditText(ctx)
        urlBox.hint = "Поиск или адрес"
        urlBox.setHintTextColor(Color.parseColor("#CCFFFFFF"))
        urlBox.setTextColor(Color.WHITE)
        urlBox.textSize = 15f
        urlBox.setSingleLine()
        urlBox.setPadding(dp(16), dp(10), dp(16), dp(10))
        urlBox.background = liquidGlassPill(22)
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
        tabStripWrap.setPadding(0, dp(10), 0, 0)

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
        bottomBar.setPadding(dp(18), dp(12), dp(18), dp(12))
        bottomBar.background = liquidGlassPanel(0, false)
        bottomBar.elevation = dp(12).toFloat()

        val tabsBtn = liquidButton("[=]")
        tabsBtn.setOnClickListener { onTabsDialog?.invoke() }
        tabsBtn.setOnLongClickListener { onConfirmCloseAll?.invoke(); true }

        val tabsWrapper = FrameLayout(ctx)
        tabsWrapper.addView(tabsBtn, FrameLayout.LayoutParams(dp(58), dp(58)))
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

        val newTabBtn = liquidButton("+")
        newTabBtn.setOnClickListener { onNewTab?.invoke(it) }
        val settingsBtn = liquidButton("*")
        settingsBtn.setOnClickListener { onSettings?.invoke(it) }

        bottomBar.addView(tabsWrapper, LinearLayout.LayoutParams(dp(58), dp(58)))
        bottomBar.addView(View(ctx), LinearLayout.LayoutParams(dp(36), 1))
        bottomBar.addView(newTabBtn, LinearLayout.LayoutParams(dp(58), dp(58)))
        bottomBar.addView(View(ctx), LinearLayout.LayoutParams(dp(36), 1))
        bottomBar.addView(settingsBtn, LinearLayout.LayoutParams(dp(58), dp(58)))
        root.addView(bottomBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return root
    }

    fun animatePanelsIn() {
        topBlock.alpha = 0f
        topBlock.translationY = -dp(60).toFloat()
        topBlock.animate().alpha(1f).translationY(0f)
            .setDuration(1200)
            .setInterpolator(DecelerateInterpolator()).start()
        bottomBar.alpha = 0f
        bottomBar.translationY = dp(60).toFloat()
        bottomBar.animate().alpha(1f).translationY(0f)
            .setDuration(1200).setStartDelay(260)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    fun animateNewTabGhost(button: View, onDone: () -> Unit) {
        val s = IntArray(2); button.getLocationOnScreen(s)
        val sx = s[0]; val sy = s[1]; val sw = button.width; val sh = button.height

        val ghost = TextView(ctx)
        ghost.text = "Новая вкладка"
        ghost.setTextColor(Color.WHITE)
        ghost.textSize = 13f
        ghost.gravity = Gravity.CENTER_VERTICAL
        ghost.setPadding(dp(16), 0, dp(16), 0)
        ghost.background = liquidGlassPill(22)
        ghost.alpha = 0.95f
        ghost.elevation = dp(14).toFloat()

        val rv = button.rootView as ViewGroup
        val lp = FrameLayout.LayoutParams(sw, sh)
        lp.leftMargin = sx; lp.topMargin = sy
        rv.addView(ghost, lp)

        val endLoc = IntArray(2); tabStrip.getLocationOnScreen(endLoc)
        val endX = endLoc[0] + tabStrip.width + dp(6)
        val endY = endLoc[1]

        val ax = ValueAnimator.ofInt(sx, endX)
        ax.duration = 900
        ax.interpolator = DecelerateInterpolator()
        ax.addUpdateListener { ghost.translationX = ((it.animatedValue as Int) - sx).toFloat() }

        val ay = ValueAnimator.ofInt(sy, endY)
        ay.duration = 900
        ay.interpolator = DecelerateInterpolator()
        ay.addUpdateListener { ghost.translationY = ((it.animatedValue as Int) - sy).toFloat() }

        val aw = ValueAnimator.ofInt((sw * 1.4f).toInt(), dp(140))
        aw.duration = 900
        aw.addUpdateListener {
            val p = ghost.layoutParams as FrameLayout.LayoutParams
            p.width = it.animatedValue as Int
            ghost.layoutParams = p
        }

        val ah = ValueAnimator.ofFloat(1.0f, 1.15f, 1.0f)
        ah.duration = 900
        ah.addUpdateListener {
            val k = it.animatedValue as Float
            ghost.scaleY = k
            ghost.scaleX = 1f / k
        }

        button.animate().scaleX(0.85f).scaleY(0.85f).setDuration(260).withEndAction {
            button.animate().scaleX(1f).scaleY(1f).setDuration(460)
                .setInterpolator(OvershootInterpolator()).start()
        }.start()

        ax.start(); ay.start(); aw.start(); ah.start()

        ax.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) {
                ghost.animate().alpha(0f).setDuration(260).withEndAction {
                    rv.removeView(ghost); onDone()
                }.start()
            }
        })
    }

    fun pulseButton(button: View, after: () -> Unit) {
        button.animate().scaleX(0.85f).scaleY(0.85f).setDuration(260).withEndAction {
            button.animate().scaleX(1f).scaleY(1f).setDuration(460)
                .setInterpolator(OvershootInterpolator()).start()
        }.start()
        button.postDelayed({ after() }, 300)
    }

    fun makeTabPill(isActive: Boolean): LinearLayout {
        val pill = LinearLayout(ctx)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        pill.background = if (isActive) liquidGlassPill(22) else liquidGlassPillDim(22)
        pill.setPadding(dp(16), dp(8), dp(16), dp(8))
        return pill
    }

    fun animatePillIn(pill: View) {
        pill.alpha = 0f
        pill.scaleX = 0.6f
        pill.scaleY = 0.6f
        pill.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(620)
            .setInterpolator(OvershootInterpolator()).start()
    }

    fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun liquidButton(symbol: String): TextView {
        val tv = TextView(ctx)
        tv.text = symbol
        tv.setTextColor(Color.WHITE)
        tv.textSize = 20f
        tv.gravity = Gravity.CENTER
        tv.background = liquidGlassCircle()
        tv.elevation = dp(6).toFloat()
        return tv
    }

    private fun liquidGlassCircle(): LayerDrawable {
        val size = dp(58)
        val base = object : GradientDrawable() {
            override fun draw(canvas: Canvas) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                val r = bounds.width() / 2f
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val shader = RadialGradient(
                    cx, cy - r * 0.25f, r * 1.15f,
                    intArrayOf(
                        Color.parseColor("#AAFFFFFF"),
                        Color.parseColor("#55FFFFFF"),
                        Color.parseColor("#22FFFFFF"),
                        Color.parseColor("#11FFFFFF")
                    ),
                    floatArrayOf(0f, 0.45f, 0.8f, 1f),
                    Shader.TileMode.CLAMP
                )
                p.shader = shader
                canvas.drawCircle(cx, cy, r, p)
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
                stroke.style = Paint.Style.STROKE
                stroke.strokeWidth = dp(1).toFloat()
                stroke.color = Color.parseColor("#99FFFFFF")
                canvas.drawCircle(cx, cy, r - dp(1) / 2f, stroke)
                val inner = Paint(Paint.ANTI_ALIAS_FLAG)
                inner.style = Paint.Style.STROKE
                inner.strokeWidth = dp(1).toFloat()
                inner.color = Color.parseColor("#44FFFFFF")
                canvas.drawCircle(cx, cy, r - dp(3), inner)
            }
        }
        base.shape = GradientDrawable.OVAL
        base.setSize(size, size)
        val layer = LayerDrawable(arrayOf(base))
        layer.setLayerSize(0, size, size)
        return layer
    }

    private fun liquidGlassPill(radiusDp: Int): LayerDrawable {
        val radius = dp(radiusDp).toFloat()
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = radius
        g.colors = intArrayOf(
            Color.parseColor("#66FFFFFF"),
            Color.parseColor("#33FFFFFF"),
            Color.parseColor("#22FFFFFF")
        )
        g.gradientType = GradientDrawable.LINEAR_GRADIENT
        g.orientation = GradientDrawable.Orientation.TL_BR
        g.setStroke(dp(1), Color.parseColor("#88FFFFFF"))

        val edge = object : GradientDrawable() {
            override fun draw(canvas: Canvas) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                val w = bounds.width().toFloat()
                val h = bounds.height().toFloat()
                val shader = LinearGradient(
                    0f, 0f, w, 0f,
                    intArrayOf(
                        Color.parseColor("#55FFFFFF"),
                        Color.parseColor("#00FFFFFF"),
                        Color.parseColor("#00FFFFFF"),
                        Color.parseColor("#55FFFFFF")
                    ),
                    floatArrayOf(0f, 0.15f, 0.85f, 1f),
                    Shader.TileMode.CLAMP
                )
                p.shader = shader
                canvas.drawRoundRect(0f, 0f, w, h, radius, radius, p)
            }
        }
        edge.shape = GradientDrawable.RECTANGLE
        edge.cornerRadius = radius

        return LayerDrawable(arrayOf(g, edge))
    }

    private fun liquidGlassPillDim(radiusDp: Int): LayerDrawable {
        val radius = dp(radiusDp).toFloat()
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = radius
        g.colors = intArrayOf(
            Color.parseColor("#33FFFFFF"),
            Color.parseColor("#22FFFFFF"),
            Color.parseColor("#11FFFFFF")
        )
        g.gradientType = GradientDrawable.LINEAR_GRADIENT
        g.orientation = GradientDrawable.Orientation.TL_BR
        g.setStroke(dp(1), Color.parseColor("#55FFFFFF"))
        return LayerDrawable(arrayOf(g))
    }

    private fun liquidGlassPanel(radiusDp: Int, top: Boolean): LayerDrawable {
        val radius = dp(radiusDp).toFloat()
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = radius
        g.colors = if (top) intArrayOf(
            Color.parseColor("#77FFFFFF"),
            Color.parseColor("#44FFFFFF"),
            Color.parseColor("#22FFFFFF")
        ) else intArrayOf(
            Color.parseColor("#66FFFFFF"),
            Color.parseColor("#33FFFFFF"),
            Color.parseColor("#11FFFFFF")
        )
        g.gradientType = GradientDrawable.LINEAR_GRADIENT
        g.orientation = if (top) GradientDrawable.Orientation.TOP_BOTTOM
                        else GradientDrawable.Orientation.BOTTOM_TOP
        g.setStroke(dp(1), Color.parseColor("#88FFFFFF"))

        val edge = object : GradientDrawable() {
            override fun draw(canvas: Canvas) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                val w = bounds.width().toFloat()
                val h = bounds.height().toFloat()
                val shader = LinearGradient(
                    0f, 0f, w, 0f,
                    intArrayOf(
                        Color.parseColor("#66FFFFFF"),
                        Color.parseColor("#00FFFFFF"),
                        Color.parseColor("#00FFFFFF"),
                        Color.parseColor("#66FFFFFF")
                    ),
                    floatArrayOf(0f, 0.12f, 0.88f, 1f),
                    Shader.TileMode.CLAMP
                )
                p.shader = shader
                canvas.drawRect(0f, 0f, w, h, p)
            }
        }
        edge.shape = GradientDrawable.RECTANGLE
        return LayerDrawable(arrayOf(g, edge))
    }
}
