package com.spellapp.core.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// Björn Ottosson's Oklab → linear sRGB, then linear sRGB → sRGB encode.
// OKLCH is the polar form: a = C cos h, b = C sin h.
// Kept local to avoid a color dependency; equal-lightness steps look equal here.
internal fun oklch(l: Double, c: Double, hDegrees: Double): Color {
    val hRad = Math.toRadians(hDegrees)
    val a = c * cos(hRad)
    val b = c * sin(hRad)

    val lPrime = l + 0.3963377774 * a + 0.2158037573 * b
    val mPrime = l - 0.1055613458 * a - 0.0638541728 * b
    val sPrime = l - 0.0894841775 * a - 1.2914855480 * b

    val lCubed = lPrime * lPrime * lPrime
    val mCubed = mPrime * mPrime * mPrime
    val sCubed = sPrime * sPrime * sPrime

    val rLinear = 4.0767416621 * lCubed - 3.3077115913 * mCubed + 0.2309699292 * sCubed
    val gLinear = -1.2684380046 * lCubed + 2.6097574011 * mCubed - 0.3413193965 * sCubed
    val bLinear = -0.0041960863 * lCubed - 0.7034186147 * mCubed + 1.7076147010 * sCubed

    return Color(
        red = srgbEncode(rLinear).toFloat().coerceIn(0f, 1f),
        green = srgbEncode(gLinear).toFloat().coerceIn(0f, 1f),
        blue = srgbEncode(bLinear).toFloat().coerceIn(0f, 1f),
    )
}

private fun srgbEncode(linear: Double): Double {
    val magnitude = abs(linear)
    val sign = if (linear < 0.0) -1.0 else 1.0
    return if (magnitude <= 0.0031308) {
        12.92 * linear
    } else {
        sign * (1.055 * magnitude.pow(1.0 / 2.4) - 0.055)
    }
}
