package app.vimex.microglideboard

import android.content.SharedPreferences

/** Single source of truth for numeric settings, defaults, and persisted switches. */
object KeyboardSettings {
    const val KEYBOARD_THEME_KEY = "keyboard_theme"
    const val KEYBOARD_THEME_SYSTEM = "system"
    const val KEYBOARD_THEME_DARK = "dark"
    const val KEYBOARD_THEME_LIGHT = "light"
    const val KEYBOARD_THEME_OLED = "oled"

    fun readKeyboardTheme(preferences: SharedPreferences): String =
        when (preferences.getString(KEYBOARD_THEME_KEY, KEYBOARD_THEME_SYSTEM)) {
            KEYBOARD_THEME_DARK -> KEYBOARD_THEME_DARK
            KEYBOARD_THEME_LIGHT -> KEYBOARD_THEME_LIGHT
            KEYBOARD_THEME_OLED -> KEYBOARD_THEME_OLED
            else -> KEYBOARD_THEME_SYSTEM
        }

    data class Definition(
        val key: String,
        val default: Float,
        val min: Float,
        val max: Float
    )

    data class ToggleDefinition(val key: String)

    val definitions = listOf(
        Definition("height_dp", 330f, 240f, 420f),
        Definition("left_ratio", 0.38f, 0.30f, 0.52f),
        Definition("gest_dp", 10f, 0f, 24f),
        Definition("gesture_speed", 55f, 0f, 800f),
        Definition("preview_lines", 3f, 1f, Float.MAX_VALUE),
        Definition("hint_right", 42f, 28f, 48f),
        Definition("hint_bubble", 1f, 0.7f, 1.5f),
        Definition("hint_pill", 17f, 12f, 24f),
        Definition("bottom_margin_dp", 0f, 0f, 80f),
        Definition("gesture_height_scale", 1f, 0.45f, 1f),
        Definition("gesture_center_offset_dp", 0f, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY),
        // No user-visible min/max for moving the entire keyboard. The view only applies
        // a platform-safe pixel bound when calculating its measured height.
        Definition("keyboard_vertical_offset_dp", 0f, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    // All labels and descriptions are stored in Android string resources.
    val toggleDefinitions = listOf(
        ToggleDefinition("show_text_preview"),
        ToggleDefinition("disable_left_hints"),
        ToggleDefinition("disable_right_hints"),
        ToggleDefinition("micro_vibration"),
        ToggleDefinition("normal_vibration"),
        ToggleDefinition("delete_space_on_quick_symbol"),
        ToggleDefinition("persistent_number_row"),
        ToggleDefinition("space_after_quick_symbol"),
        ToggleDefinition("auto_capitalization"),
        ToggleDefinition("key_repeat_enabled"),
        ToggleDefinition("sticky_repeat_symbol")
    )

    private val byKey = definitions.associateBy(Definition::key)
    private val togglesByKey = toggleDefinitions.associateBy(ToggleDefinition::key)

    fun defaultFor(key: String): Float =
        requireNotNull(byKey[key]) { "Unknown keyboard setting: $key" }.default

    fun read(preferences: SharedPreferences, key: String): Float {
        val setting = requireNotNull(byKey[key]) { "Unknown keyboard setting: $key" }
        val value = preferences.getFloat(key, setting.default)
        if (value.isNaN()) return setting.default
        return value.coerceIn(setting.min, setting.max)
    }

    fun readBoolean(preferences: SharedPreferences, key: String): Boolean {
        requireNotNull(togglesByKey[key]) { "Unknown keyboard toggle: $key" }
        val defaultEnabled = key in setOf(
            "show_text_preview",
            "micro_vibration",
            "delete_space_on_quick_symbol",
            "persistent_number_row",
            "space_after_quick_symbol",
            "auto_capitalization",
            "key_repeat_enabled",
            "sticky_repeat_symbol"
        )
        val raw = preferences.getBoolean(key, defaultEnabled)
        // Even if an older build saved both switches as true, the UI and haptics
        // expose one mode only. Micro vibration wins in that legacy conflict.
        return when (key) {
            // In a legacy conflict the micro setting wins. The settings UI also fixes the
            // stored pair when opened and writes both keys atomically when either is enabled.
            "micro_vibration" -> raw
            "normal_vibration" -> raw && !preferences.getBoolean("micro_vibration", true)
            else -> raw
        }
    }

    fun resetOne(preferences: SharedPreferences, key: String) {
        preferences.edit().putFloat(key, defaultFor(key)).apply()
    }

    fun resetAll(preferences: SharedPreferences) {
        preferences.edit().also { editor ->
            definitions.forEach { editor.putFloat(it.key, it.default) }
        }.apply()
    }
}
