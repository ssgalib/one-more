package dev.galib.rotcounter

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Direction A, "Editorial": warm paper, huge Archivo count, mono section labels, hairlines. */
class MainActivity : Activity() {
    private lateinit var date: TextView
    private lateinit var count: TextView
    private lateinit var time: TextView
    private lateinit var perReel: TextView
    private lateinit var ads: TextView
    private lateinit var dot: View
    private lateinit var status: TextView
    private val dp by lazy { resources.displayMetrics.density }
    private val ink by lazy { getColor(R.color.ink) }
    private val muted by lazy { getColor(R.color.muted) }
    private val line by lazy { getColor(R.color.line) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bg = getColor(R.color.bg)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        @Suppress("DEPRECATION") // the non-deprecated controller needs API 30; minSdk is 26
        if (!night) window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            // targetSdk 35 draws edge to edge: add the system bar insets to the base padding
            setOnApplyWindowInsetsListener { v, insets ->
                @Suppress("DEPRECATION")
                v.setPadding(px(24), insets.systemWindowInsetTop + px(24), px(24), insets.systemWindowInsetBottom + px(24))
                insets
            }
        }

        date = mono("", muted)
        root.addView(LinearLayout(this).apply {
            addView(mono("ONE MORE", muted), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(date)
        })
        root.addView(hairline(), lp(top = 12, height = 1))

        root.addView(mono("01 / TODAY", muted), lp(top = 40))
        count = TextView(this).apply {
            typeface = Fonts.archivo(context, 800)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 132f)
            letterSpacing = -0.04f
            includeFontPadding = false
            setTextColor(ink)
        }
        root.addView(LinearLayout(this).apply {
            // horizontal LinearLayouts baseline-align their children by default
            addView(count)
            addView(TextView(context).apply {
                text = "reels"
                typeface = Fonts.serifItalic(context)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
                setTextColor(getColor(R.color.accent))
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = px(12) })
        }, lp(top = 4))

        time = stat()
        perReel = stat()
        root.addView(hairline(), lp(top = 32, height = 1))
        root.addView(LinearLayout(this).apply {
            addView(cell("02 / IN REELS", time, start = 0, end = 16), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(View(context).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(px(1), MATCH_PARENT))
            addView(cell("03 / PER REEL", perReel, start = 16, end = 0), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        })
        root.addView(hairline(), lp(height = 1))

        ads = mono("", ink)
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(mono("04 / ADS SKIPPED · TIME KEPT", muted), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(ads)
        }, lp(top = 14))
        root.addView(hairline(), lp(top = 14, height = 1))

        root.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)) // push setup to the bottom

        dot = View(this)
        status = mono("", ink)
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(dot, LinearLayout.LayoutParams(px(8), px(8)).apply { marginEnd = px(10) })
            addView(status)
            addView(mono("  ·  RESETS AT MIDNIGHT", muted))
        })
        root.addView(FrameLayout(this).apply {
            background = GradientDrawable().apply { setStroke(px(1), ink) }
            isClickable = true
            isFocusable = true
            contentDescription = "Open accessibility settings"
            foreground = getDrawable(android.R.drawable.list_selector_background)
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            val label = { s: String, g: Int ->
                TextView(context).apply {
                    text = s
                    typeface = Fonts.archivo(context, 500)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(ink)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                } to FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, g or Gravity.CENTER_VERTICAL)
            }
            label("Accessibility settings", Gravity.START).let { (v, p) -> addView(v, p) }
            label("→", Gravity.END).let { (v, p) -> addView(v, p) }
            setPadding(px(18), 0, px(18), 0)
        }, lp(top = 16, height = 52))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val t = Stats.load(this)
        date.text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM")).uppercase()
        count.text = t.count.toString()
        time.text = Stats.minutes(t.reelMs)
        val items = t.count + t.ads
        perReel.text = if (items == 0) "–" else "${t.reelMs / 1000 / items}s"
        ads.text = t.ads.toString()
        val on = isServiceOn()
        status.text = if (on) "TRACKING" else "TRACKER OFF"
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (on) getColor(R.color.accent) else muted)
        }
    }

    private fun isServiceOn(): Boolean {
        val me = ComponentName(this, ReelService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    private fun mono(s: String, color: Int) = TextView(this).apply {
        text = s
        typeface = Fonts.mono(context)
        letterSpacing = 0.08f
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setTextColor(color)
    }

    private fun stat() = TextView(this).apply {
        typeface = Fonts.archivo(context, 700)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
        letterSpacing = -0.02f
        setTextColor(ink)
    }

    private fun cell(label: String, value: TextView, start: Int, end: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(start), px(18), px(end), px(18))
        addView(mono(label, muted))
        addView(value, lp(top = 6))
    }

    private fun hairline() = View(this).apply { setBackgroundColor(line) }

    private fun px(v: Int) = (v * dp).toInt()

    /** Full-width params; [height] in dp, or wrap content when null. */
    private fun lp(top: Int = 0, height: Int? = null) =
        LinearLayout.LayoutParams(MATCH_PARENT, height?.let(::px) ?: WRAP_CONTENT).apply { topMargin = px(top) }
}
