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
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Direction A, "Editorial": warm paper, huge Archivo count, mono section labels, hairlines. Tabs: today, 7 days, 30 days. */
class MainActivity : Activity() {
    private enum class Period(val tab: String, val kicker: String, val length: Int, val vs: String) {
        TODAY("TODAY", "01 / TODAY", 1, "yesterday"),
        WEEK("WEEK", "02 / LAST 7 DAYS", 7, "the 7 days before"),
        MONTH("MONTH", "03 / LAST 30 DAYS", 30, "the 30 days before"),
    }

    private var period = Period.TODAY
    private val tabs = HashMap<Period, Pair<TextView, View>>()
    private lateinit var date: TextView
    private lateinit var kicker: TextView
    private lateinit var count: TextView
    private lateinit var delta: TextView
    private lateinit var time: TextView
    private lateinit var secondLabel: TextView
    private lateinit var second: TextView
    private lateinit var chart: BarChart
    private lateinit var chartBlock: LinearLayout
    private lateinit var picked: TextView
    private lateinit var ads: TextView
    private lateinit var worstRow: LinearLayout
    private lateinit var worst: TextView
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

        root.addView(LinearLayout(this).apply {
            for (p in Period.entries) {
                val label = mono(p.tab, muted).apply { gravity = Gravity.CENTER; textSize = 12f }
                val underline = View(context)
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    isClickable = true
                    isFocusable = true
                    contentDescription = "${p.tab.lowercase()} tab"
                    setOnClickListener { period = p; render() }
                    addView(label, LinearLayout.LayoutParams(MATCH_PARENT, px(42)).apply { gravity = Gravity.CENTER })
                    addView(underline, LinearLayout.LayoutParams(MATCH_PARENT, px(2)))
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                tabs[p] = label to underline
            }
        })
        root.addView(hairline(), lp(height = 1))

        kicker = mono("", muted)
        root.addView(kicker, lp(top = 28))
        count = TextView(this).apply {
            typeface = Fonts.archivo(context, 800)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 108f)
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
                setTextColor(getColor(R.color.accent))
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = px(12) })
        }, lp(top = 4))
        delta = mono("", ink).apply { textSize = 12f; letterSpacing = 0.04f }
        root.addView(delta, lp(top = 10))

        time = stat()
        second = stat()
        secondLabel = mono("", muted)
        root.addView(hairline(), lp(top = 22, height = 1))
        root.addView(LinearLayout(this).apply {
            addView(cell(mono("IN REELS", muted), time, start = 0, end = 16), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(View(context).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(px(1), MATCH_PARENT))
            addView(cell(secondLabel, second, start = 16, end = 0), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        })
        root.addView(hairline(), lp(height = 1))

        chart = BarChart(this)
        picked = mono("", muted)
        chartBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(chart, lp())
            addView(picked, lp(top = 6))
        }
        chart.onSelect = { d ->
            picked.setTextColor(ink)
            picked.text = "${d.date.format(DateTimeFormatter.ofPattern("EEE d MMM")).uppercase()} · ${d.reels} REELS · " +
                "${Stats.minutes(d.ms).uppercase()} · ${d.ads} ADS"
        }
        root.addView(chartBlock, lp(top = 20))

        ads = mono("", ink)
        root.addView(row(mono("ADS SKIPPED · TIME KEPT", muted), ads), lp(top = 4))
        root.addView(hairline(), lp(height = 1))
        worst = mono("", ink)
        worstRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row(mono("WORST DAY", muted), worst))
            addView(hairline(), lp(height = 1))
        }
        root.addView(worstRow, lp())

        root.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, px(24), 1f)) // push setup to the bottom

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
        // scrolls on short screens; fillViewport keeps setup pinned to the bottom on tall ones
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bg)
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        render()
        val on = isServiceOn()
        status.text = if (on) "TRACKING" else "TRACKER OFF"
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (on) getColor(R.color.accent) else muted)
        }
    }

    private fun render() {
        val today = LocalDate.now()
        Stats.load(this) // moves v1.0/1.1's single day into history
        val s = History.summary(Stats.all(this), today, period.length)
        date.text = today.format(DateTimeFormatter.ofPattern("EEE d MMM")).uppercase()
        for ((p, v) in tabs) {
            v.first.setTextColor(if (p == period) ink else muted)
            v.second.setBackgroundColor(if (p == period) ink else 0)
            v.first.parent.let { (it as View).isSelected = p == period }
        }
        kicker.text = period.kicker
        count.text = "%,d".format(s.reels)
        delta.text = when (val d = s.deltaPct) {
            null -> "NOTHING TO COMPARE WITH YET"
            0 -> "= same as ${period.vs} (${"%,d".format(s.previousReels)})"
            else -> "${if (d < 0) "↓" else "↑"} ${kotlin.math.abs(d)}% vs ${period.vs} (${"%,d".format(s.previousReels)})"
        }
        time.text = Stats.minutes(s.ms)
        ads.text = "%,d".format(s.ads)
        val isToday = period == Period.TODAY
        if (isToday) {
            secondLabel.text = "PER REEL"
            val items = s.reels + s.ads
            second.text = if (items == 0) "–" else "${s.ms / 1000 / items}s"
        } else {
            secondLabel.text = "DAILY AVG"
            second.text = s.dailyAvg?.toString() ?: "–"
            chart.set(s.days, s.dailyAvg, s.worst?.date)
            picked.setTextColor(muted)
            picked.text = "TAP A BAR TO SEE THAT DAY"
            worst.text = s.worst?.let { "${it.date.format(DateTimeFormatter.ofPattern("EEE d MMM")).uppercase()} · ${it.reels}" } ?: "–"
        }
        chartBlock.visibility = if (isToday) View.GONE else View.VISIBLE
        worstRow.visibility = if (isToday) View.GONE else View.VISIBLE
    }

    private fun isServiceOn(): Boolean {
        val me = ComponentName(this, ReelService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        // entries may be stored short ("pkg/.Cls") or long ("pkg/pkg.Cls"); compare parsed names
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
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
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
        letterSpacing = -0.02f
        setTextColor(ink)
    }

    private fun cell(label: TextView, value: TextView, start: Int, end: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(start), px(14), px(end), px(14))
        addView(label)
        addView(value, lp(top = 4))
    }

    private fun row(label: TextView, value: TextView) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, px(14), 0, px(14))
        addView(label, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(value)
    }

    private fun hairline() = View(this).apply { setBackgroundColor(line) }

    private fun px(v: Int) = (v * dp).toInt()

    /** Full-width params; [height] in dp, or wrap content when null. */
    private fun lp(top: Int = 0, height: Int? = null) =
        LinearLayout.LayoutParams(MATCH_PARENT, height?.let(::px) ?: WRAP_CONTENT).apply { topMargin = px(top) }
}
