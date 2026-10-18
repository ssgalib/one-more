package dev.galib.rotcounter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Reels per day: one neutral bar per day, today in the accent, a dashed average line under the bars,
 * and value labels only on today, the worst day and a tapped bar. Tapping reports the day.
 */
class BarChart(context: Context) : View(context) {
    var onSelect: ((Day) -> Unit)? = null
    private var days: List<Day> = emptyList()
    private var avg: Int? = null
    private var worst: LocalDate? = null
    private var selected = -1

    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val chartH = 150 * dp
    private val axisH = 20 * dp
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.mono(context)
        textSize = 10 * sp
        letterSpacing = 0.06f
    }
    private val rule = Paint().apply { strokeWidth = 1 * dp }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1 * dp
        pathEffect = DashPathEffect(floatArrayOf(4 * dp, 3 * dp), 0f)
    }
    private val ink = context.getColor(R.color.ink)
    private val muted = context.getColor(R.color.muted)
    private val accent = context.getColor(R.color.accent)
    private val path = Path()
    private val rect = RectF()

    fun set(days: List<Day>, avg: Int?, worst: LocalDate?) {
        this.days = days
        this.avg = avg
        this.worst = worst
        selected = -1
        contentDescription = "Reels per day, ${days.first().date} to today. " +
            days.joinToString { "${it.date.format(DateTimeFormatter.ofPattern("EEE d MMM"))}: ${it.reels}" }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(getDefaultSize(0, widthMeasureSpec), (chartH + axisH).toInt())

    private fun gap() = (if (days.size <= 7) 8 else 2) * dp
    private fun barWidth() = (width - gap() * (days.size - 1)) / days.size

    override fun onDraw(c: Canvas) {
        if (days.isEmpty()) return
        val labelRoom = 16 * dp
        val max = maxOf(days.maxOf { it.reels }, avg ?: 0, 1).toFloat()
        val scale = (chartH - labelRoom) / max
        // the average line goes under the bars; its value is the DAILY AVG figure above the chart
        avg?.let { a ->
            val y = chartH - a * scale
            dash.color = muted
            c.drawLine(0f, y, width.toFloat(), y, dash)
        }
        val w = barWidth()
        days.forEachIndexed { i, d ->
            val x = i * (w + gap())
            val isToday = i == days.lastIndex
            val h = d.reels * scale
            if (h > 0) {
                val r = minOf(4 * dp, w / 2, h)
                rect.set(x, chartH - h, x + w, chartH)
                path.reset()
                path.addRoundRect(rect, floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), Path.Direction.CW)
                bar.color = when { i == selected -> ink; isToday -> accent; else -> muted }
                c.drawPath(path, bar)
            }
            if (isToday || d.date == worst || i == selected) {
                text.color = if (isToday && i != selected) accent else ink
                text.textAlign = Paint.Align.CENTER
                c.drawText(d.reels.toString(), x + w / 2, chartH - h - 5 * dp, text)
            }
        }
        rule.color = muted
        c.drawLine(0f, chartH, width.toFloat(), chartH, rule)
        val axisY = chartH + 15 * dp
        if (days.size <= 7) {
            text.textAlign = Paint.Align.CENTER
            days.forEachIndexed { i, d ->
                text.color = if (i == days.lastIndex) accent else muted
                c.drawText(d.date.dayOfWeek.name.take(1), i * (w + gap()) + w / 2, axisY, text)
            }
        } else {
            text.color = muted
            text.textAlign = Paint.Align.LEFT
            c.drawText(days.first().date.format(DateTimeFormatter.ofPattern("d MMM")).uppercase(), 0f, axisY, text)
            text.color = accent
            text.textAlign = Paint.Align.RIGHT
            c.drawText("TODAY", width.toFloat(), axisY, text)
        }
    }

    @SuppressLint("ClickableViewAccessibility") // the whole chart is described in contentDescription
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (days.isEmpty()) return false
        if (e.action == MotionEvent.ACTION_UP) {
            // the hit target is the whole column, not just the bar
            val i = (e.x / (barWidth() + gap())).toInt().coerceIn(0, days.lastIndex)
            selected = i
            onSelect?.invoke(days[i])
            invalidate()
        }
        return true
    }
}
