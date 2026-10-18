package dev.galib.rotcounter

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView

class ReelService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var power: PowerManager
    private lateinit var bubble: LinearLayout
    private lateinit var bubbleCount: TextView
    private lateinit var bubbleTime: TextView
    private lateinit var bubbleAds: TextView
    private lateinit var adsDivider: View
    private lateinit var params: WindowManager.LayoutParams
    private var shown = false
    private var inReels = false
    private var day = Stats.today()
    private var ticks = 0
    private var tracker = ReelTracker()

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        power = getSystemService(PowerManager::class.java)
        tracker = Stats.load(this)
        bubble = makeBubble()
        handler.post(tick)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (e.packageName != IG) return
                val id = e.source?.viewIdResourceName ?: return
                if (BuildConfig.DEBUG) Log.d(TAG, "scroll id=$id from=${e.fromIndex} to=${e.toIndex} n=${e.itemCount}")
                if (REELS_ID_PART in id && e.fromIndex >= 0) tracker.onReel(e.fromIndex, now())
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> refreshWindow()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        save()
        if (shown) wm.removeView(bubble)
        super.onDestroy()
    }

    private fun onTick() {
        if (day != Stats.today()) {
            save() // flush the last seconds of the old day before the reset
            day = Stats.today()
            tracker.reset()
        }
        refreshWindow()
        if (inReels && power.isInteractive) {
            val isAd = isAdOnScreen()
            if (tracker.tick(now(), isAd)) {
                if (BuildConfig.DEBUG) Log.d(TAG, "counted ad=$isAd reels=${tracker.count} ads=${tracker.ads}")
                save()
            }
        } else {
            tracker.pause()
        }
        if (++ticks % 10 == 0) save()
        if (shown) updateBubble()
    }

    /** Shows the bubble only while Instagram is the active window, and tracks whether the Reels pager is on screen. */
    private fun refreshWindow() {
        val root = rootInActiveWindow
        val inIg = root?.packageName == IG
        val reelsNow = inIg && root!!.findAccessibilityNodeInfosByViewId(REELS_ID).isNotEmpty()
        if (inReels && !reelsNow) tracker.leave()
        inReels = reelsNow
        if (inIg && !shown) {
            updateBubble()
            if (params.x == CENTER) {
                bubble.measure(0, 0)
                params.x = (resources.displayMetrics.widthPixels - bubble.measuredWidth) / 2
            }
            wm.addView(bubble, params)
            shown = true
        } else if (!inIg && shown) {
            wm.removeView(bubble)
            shown = false
        }
    }

    private fun save() = Stats.save(this, day, tracker)

    /** Instagram marks a sponsored reel with a small node whose whole text is "Ad" (or "Sponsored"); captions never match exactly. */
    private fun isAdOnScreen(): Boolean {
        // Walk the tree ourselves: findAccessibilityNodeInfosByText misses Instagram's custom-drawn label.
        val stack = ArrayDeque(listOfNotNull(rootInActiveWindow))
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            val label = n.text?.toString() ?: n.contentDescription?.toString()
            if (label in AD_LABELS && n.isVisibleToUser) return true
            for (i in 0 until n.childCount) n.getChild(i)?.let(stack::addLast)
        }
        return false
    }

    private fun updateBubble() {
        bubbleCount.text = tracker.count.toString()
        bubbleTime.text = Stats.minutes(tracker.reelMs)
        bubbleAds.text = "${tracker.ads} ads"
        val adsVisibility = if (tracker.ads > 0) View.VISIBLE else View.GONE
        bubbleAds.visibility = adsVisibility
        adsDivider.visibility = adsVisibility
    }

    /** Direction A: a rounded smoked-glass pill (no real blur: FLAG_BLUR_BEHIND blurs the whole screen), "42 | 18m | 6 ads". */
    @SuppressLint("ClickableViewAccessibility")
    private fun makeBubble(): LinearLayout {
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val saved = Stats.loadBubble(this@ReelService)
            x = saved?.first ?: CENTER
            y = saved?.second ?: px(46)
        }
        fun part(wght: Int, alpha: Float = 1f) = TextView(this).apply {
            typeface = Fonts.archivo(context, wght)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(0xFFFFFFFF.toInt())
            this.alpha = alpha
            includeFontPadding = false
        }
        fun divider() = View(this).apply { setBackgroundColor(0x66FFFFFF) }
        bubbleCount = part(800)
        bubbleTime = part(700)
        bubbleAds = part(500, alpha = 0.75f)
        adsDivider = divider()
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(38)
            setPadding(px(18), 0, px(18), 0)
            background = GradientDrawable().apply {
                cornerRadius = px(19).toFloat()
                setColor(0x73101014) // ink at 45%: keeps white text readable on bright reels
                setStroke(px(1), 0x47FFFFFF)
            }
            val gap = { LinearLayout.LayoutParams(px(1), px(16)).apply { marginStart = px(12); marginEnd = px(12) } }
            addView(bubbleCount)
            addView(divider(), gap())
            addView(bubbleTime)
            addView(adsDivider, gap())
            addView(bubbleAds)
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            setOnTouchListener { v, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = ev.rawX; downY = ev.rawY; startX = params.x; startY = params.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + (ev.rawX - downX).toInt()
                        params.y = startY + (ev.rawY - downY).toInt()
                        wm.updateViewLayout(v, params)
                    }
                    MotionEvent.ACTION_UP -> Stats.saveBubble(this@ReelService, params.x, params.y)
                }
                true
            }
        }
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val TAG = "OneMore"
        const val IG = "com.instagram.android"

        // shortcut: Instagram's view IDs aren't a public API and can change in any Instagram update; re-check with `adb shell uiautomator dump` if counting stops.
        const val REELS_ID = "$IG:id/clips_viewer_view_pager"
        const val REELS_ID_PART = "clips_viewer"
        val AD_LABELS = setOf("Ad", "Sponsored")
        private const val CENTER = Int.MIN_VALUE // placeholder x until the pill is measured
    }
}
