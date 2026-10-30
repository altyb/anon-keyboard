package dev.altayeb.anon

import android.content.Context

/** Every user switch, read once per keyboard show. Keys are the SharedPreferences names. */
data class Prefs(
    val suggestions: Boolean = true,
    val predictNext: Boolean = true,
    val autocorrect: Boolean = true,
    val learn: Boolean = true,
    val contacts: Boolean = false,
    val clipboard: Boolean = true,
    val autoCaps: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val vibrate: Boolean = true,
    val sound: Boolean = false,
    val glide: Boolean = true,
    val swipeEdit: Boolean = true,        // hold + drag on space = cursor, slide on delete = words
    val oneHanded: Int = 0,               // -1 left, 0 off, +1 right
    val numberRow: Boolean = false,
    val hints: Boolean = true,
    val keyHeight: Int = 100, // percent, 80..140
    // Privacy
    val incognito: Boolean = false,       // no learning, no clipboard history, anywhere
    val clearClipsOnLock: Boolean = true, // wipe clipboard history when the screen turns off
    val secureKeyboard: Boolean = true,   // block screenshots/recording of the keys in password fields
    // Look (colours and motion only, never sizes)
    val theme: String = "classic",        // "classic" (default) or "dynamic" (wallpaper colours, Android 12+)
    val animations: Boolean = true,
) {
    companion object {
        const val FILE = "prefs"

        fun read(context: Context): Prefs {
            val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val d = Prefs()
            return Prefs(
                suggestions = p.getBoolean("suggestions", d.suggestions),
                predictNext = p.getBoolean("predictNext", d.predictNext),
                autocorrect = p.getBoolean("autocorrect", d.autocorrect),
                learn = p.getBoolean("learn", d.learn),
                contacts = p.getBoolean("contacts", d.contacts),
                clipboard = p.getBoolean("clipboard", d.clipboard),
                autoCaps = p.getBoolean("autoCaps", d.autoCaps),
                doubleSpacePeriod = p.getBoolean("doubleSpacePeriod", d.doubleSpacePeriod),
                vibrate = p.getBoolean("vibrate", d.vibrate),
                sound = p.getBoolean("sound", d.sound),
                glide = p.getBoolean("glide", d.glide),
                swipeEdit = p.getBoolean("swipeEdit", d.swipeEdit),
                oneHanded = p.getInt("oneHanded", d.oneHanded).coerceIn(-1, 1),
                numberRow = p.getBoolean("numberRow", d.numberRow),
                hints = p.getBoolean("hints", d.hints),
                keyHeight = p.getInt("keyHeight", d.keyHeight).coerceIn(80, 140),
                incognito = p.getBoolean("incognito", d.incognito),
                clearClipsOnLock = p.getBoolean("clearClipsOnLock", d.clearClipsOnLock),
                secureKeyboard = p.getBoolean("secureKeyboard", d.secureKeyboard),
                theme = p.getString("theme", d.theme) ?: d.theme,
                animations = p.getBoolean("animations", d.animations),
            )
        }
    }
}
