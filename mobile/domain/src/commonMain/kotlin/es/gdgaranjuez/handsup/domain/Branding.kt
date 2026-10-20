package es.gdgaranjuez.handsup.domain

import kotlin.math.pow

fun isHexColor(value: String): Boolean = Regex("^#[0-9a-fA-F]{6}$").matches(value)

fun hexToRgb(hex: String): Triple<Int, Int, Int> {
    val h = hex.removePrefix("#")
    return Triple(h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
}

fun luminance(hex: String): Double {
    val (r, g, b) = hexToRgb(hex)
    fun lin(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
}

fun contrast(a: String, b: String): Double {
    val la = luminance(a)
    val lb = luminance(b)
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05) / (lo + 0.05)
}

/** White or near-black, whichever reads better on [bg]. */
fun onColor(bg: String): String = if (contrast(bg, "#ffffff") >= contrast(bg, "#111111")) "#ffffff" else "#111111"

private fun mix(hex: String, other: String, amount: Double): String {
    val a = hexToRgb(hex)
    val b = hexToRgb(other)
    fun ch(x: Int, y: Int) = (x + (y - x) * amount).toInt().coerceIn(0, 255)
    fun h(v: Int) = v.toString(16).padStart(2, '0')
    return "#" + h(ch(a.first, b.first)) + h(ch(a.second, b.second)) + h(ch(a.third, b.third))
}

/** Darkens (light surface) or lightens (dark surface) a colour until it reaches 4.5:1 against the surface. */
fun readableOn(color: String, surface: String): String {
    val target = if (luminance(surface) > 0.5) "#000000" else "#ffffff"
    var out = color
    var i = 0
    while (i < 20 && contrast(out, surface) < 4.5) {
        out = mix(out, target, 0.1)
        i++
    }
    return out
}
