package dev.altayeb.hudhud

import android.content.Context
import android.graphics.Color
import android.os.Build

/** Colours for every keyboard surface. Sizes never depend on the theme. */
data class KeyTheme(
    val bg: Int,          // behind the keys
    val key: Int,
    val keyEdge: Int,     // darker bottom strip = 3D key
    val pressed: Int,
    val label: Int,
    val accent: Int,      // icons, special labels, shift on, selection
    val onAccent: Int,    // text drawn on accent
    val hint: Int,
    val panel: Int,       // strip, panel headers, popups
    val card: Int,        // clipboard cards
    val textDim: Int,
    val special: Int = key,     // shift / delete / enter / 123 / emoji key colour
    val specialLabel: Int = accent,
    val cornerDp: Float = 5f,   // key corner radius
    val edge: Boolean = true,   // darker bottom strip
    val pressScale: Float = 1f, // < 1 = key shrinks while held and springs back (expressive)
) {
    companion object {
        /** The default: Ginger-style grey keys on black, cyan accents. */
        val CLASSIC = KeyTheme(
            bg = Color.BLACK, key = 0xFF474747.toInt(), keyEdge = 0xFF2E2E2E.toInt(),
            pressed = 0xFF6B6B6B.toInt(), label = Color.WHITE, accent = 0xFF71E2FB.toInt(),
            onAccent = Color.BLACK, hint = 0xFF9E9E9E.toInt(), panel = 0xFF1B1B1C.toInt(),
            card = 0xFF2C2D2F.toInt(), textDim = 0xFF9E9E9E.toInt(),
        )

        /** Light keys on pale grey, same shapes as classic. */
        val LIGHT = KeyTheme(
            bg = 0xFFD8DADD.toInt(), key = Color.WHITE, keyEdge = 0xFFB4B7BB.toInt(),
            pressed = 0xFFBFC2C6.toInt(), label = 0xFF1A1A1A.toInt(), accent = 0xFF00708C.toInt(),
            onAccent = Color.WHITE, hint = 0xFF6B6E73.toInt(), panel = 0xFFEEEFF1.toInt(),
            card = Color.WHITE, textDim = 0xFF6B6E73.toInt(), special = 0xFFB9BCC1.toInt(),
        )

        /** Material 3 baseline dark, used for "material" before Android 12 (no wallpaper colours there). */
        private val MATERIAL_BASE = KeyTheme(
            bg = 0xFF141218.toInt(), key = 0xFF36343B.toInt(), keyEdge = 0xFF36343B.toInt(),
            pressed = 0xFF4A4458.toInt(), label = 0xFFE6E0E9.toInt(), accent = 0xFFD0BCFF.toInt(),
            onAccent = 0xFF381E72.toInt(), hint = 0xFFCAC4D0.toInt(), panel = 0xFF211F26.toInt(),
            card = 0xFF2B2930.toInt(), textDim = 0xFFCAC4D0.toInt(),
            special = 0xFF4A4458.toInt(), specialLabel = 0xFFE8DEF8.toInt(),
            cornerDp = 16f, edge = false, pressScale = 0.9f,
        )

        /**
         * "material" = Material You: rounder keys, tonal special keys, spring press, wallpaper colours on Android 12+.
         * "dynamic" = classic shapes with wallpaper colours. Anything else = classic.
         */
        fun of(context: Context, name: String, accent: Int = 0): KeyTheme {
            val t = of(context, name)
            if (accent == 0) return t
            val lum = Color.luminance(accent)
            return t.copy(accent = accent, specialLabel = accent, onAccent = if (lum > 0.4f) Color.BLACK else Color.WHITE)
        }

        private fun of(context: Context, name: String): KeyTheme {
            if (name == "light") return LIGHT
            if (name == "material" && Build.VERSION.SDK_INT < 31) return MATERIAL_BASE
            if ((name != "dynamic" && name != "material") || Build.VERSION.SDK_INT < 31) return CLASSIC
            fun c(id: Int) = context.getColor(id)
            if (name == "material") return KeyTheme(
                bg = c(android.R.color.system_neutral1_900),
                key = c(android.R.color.system_neutral2_800),
                keyEdge = c(android.R.color.system_neutral2_800),
                pressed = c(android.R.color.system_accent2_700),
                label = c(android.R.color.system_neutral1_50),
                accent = c(android.R.color.system_accent1_200),
                onAccent = c(android.R.color.system_accent1_900),
                hint = c(android.R.color.system_neutral2_300),
                panel = c(android.R.color.system_neutral1_800),
                card = c(android.R.color.system_neutral2_800),
                textDim = c(android.R.color.system_neutral2_300),
                special = c(android.R.color.system_accent2_700),
                specialLabel = c(android.R.color.system_accent2_100),
                cornerDp = 16f, edge = false, pressScale = 0.9f,
            )
            return KeyTheme(
                bg = c(android.R.color.system_neutral1_900),
                key = c(android.R.color.system_neutral2_700),
                keyEdge = c(android.R.color.system_neutral2_800),
                pressed = c(android.R.color.system_neutral2_500),
                label = c(android.R.color.system_neutral1_50),
                accent = c(android.R.color.system_accent1_200),
                onAccent = c(android.R.color.system_accent1_900),
                hint = c(android.R.color.system_neutral2_300),
                panel = c(android.R.color.system_neutral1_800),
                card = c(android.R.color.system_neutral2_800),
                textDim = c(android.R.color.system_neutral2_300),
            )
        }
    }
}
