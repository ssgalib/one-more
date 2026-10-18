package dev.galib.rotcounter

import android.content.Context
import android.graphics.Typeface

/** The three bundled variable fonts (assets/), instanced at a weight. */
object Fonts {
    private val cache = HashMap<String, Typeface>()

    private fun load(ctx: Context, file: String, wght: Int?): Typeface = cache.getOrPut("$file@$wght") {
        Typeface.Builder(ctx.assets, file).apply { if (wght != null) setFontVariationSettings("'wght' $wght") }.build()
    }

    fun archivo(ctx: Context, wght: Int) = load(ctx, "archivo.ttf", wght)
    fun mono(ctx: Context, wght: Int = 400) = load(ctx, "geist_mono.ttf", wght)
    fun serifItalic(ctx: Context) = load(ctx, "instrument_serif_italic.ttf", null)
}
