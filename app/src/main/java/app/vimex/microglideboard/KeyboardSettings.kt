package app.vimex.microglideboard

import android.content.SharedPreferences

/** Settings defaults/ranges and their localized resource IDs. */
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
        val labelRes: Int,
        val default: Float,
        val hintRes: Int,
        val min: Float,
        val max: Float
    )

    data class ToggleDefinition(
        val key: String,
        val labelRes: Int,
        val hintRes: Int
    )

    // Keep this list in the same logical order used by the settings screen.
    val definitions = listOf(
        // Size and placement
        Definition("height_dp", R.string.setting_height, 330f, R.string.hint_height, 1f, Float.MAX_VALUE),
        Definition("left_ratio", R.string.setting_left_ratio, 0.38f, R.string.hint_left_ratio, 0.001f, 1f),
        Definition("bottom_margin_dp", R.string.setting_bottom_margin, 0f, R.string.hint_bottom_margin, 0f, 80f),
        Definition("gesture_height_scale", R.string.setting_gesture_height, 1f, R.string.hint_gesture_height, 0.001f, Float.MAX_VALUE),
        Definition("gesture_center_offset_dp", R.string.setting_gesture_offset, 0f, R.string.hint_gesture_offset, -100f, 100f),
        // Gesture recognition
        Definition("gest_dp", R.string.setting_gesture_distance, 10f, R.string.hint_gesture_distance, 0f, 24f),
        Definition("gesture_speed", R.string.setting_gesture_speed, 55f, R.string.hint_gesture_speed, 0f, 800f),
        // Text indicator
        Definition("preview_lines", R.string.setting_preview_lines, 3f, R.string.hint_preview_lines, 1f, 50f),
        // Hints
        Definition("hint_right", R.string.setting_hint_right, 42f, R.string.hint_hint_right, 28f, 48f),
        Definition("hint_bubble", R.string.setting_hint_bubble, 1f, R.string.hint_hint_bubble, 0.7f, 1.5f),
        Definition("hint_pill", R.string.setting_hint_pill, 17f, R.string.hint_hint_pill, 12f, 24f),
        Definition("pill_dist", R.string.setting_pill_distance, 46f, R.string.hint_pill_distance, 12f, 60f)
    )

    val toggleDefinitions = listOf(
        ToggleDefinition("show_text_preview", R.string.toggle_show_text_preview, R.string.toggle_hint_show_text_preview),
        ToggleDefinition("disable_left_hints", R.string.toggle_disable_left_hints, R.string.toggle_hint_disable_left_hints),
        ToggleDefinition("disable_right_hints", R.string.toggle_disable_right_hints, R.string.toggle_hint_disable_right_hints),
        ToggleDefinition("micro_vibration", R.string.toggle_micro_vibration, R.string.toggle_hint_micro_vibration),
        ToggleDefinition("normal_vibration", R.string.toggle_normal_vibration, R.string.toggle_hint_normal_vibration),
        ToggleDefinition("delete_space_on_quick_symbol", R.string.toggle_delete_space_symbol, R.string.toggle_hint_delete_space_symbol),
        ToggleDefinition("persistent_number_row", R.string.toggle_number_row, R.string.toggle_hint_number_row),
        ToggleDefinition("space_after_quick_symbol", R.string.toggle_space_after_symbol, R.string.toggle_hint_space_after_symbol),
        ToggleDefinition("auto_capitalization", R.string.toggle_auto_capitalization, R.string.toggle_hint_auto_capitalization),
        ToggleDefinition("key_repeat_enabled", R.string.toggle_key_repeat, R.string.toggle_hint_key_repeat),
        ToggleDefinition("sticky_repeat_symbol", R.string.toggle_repeat_last_symbol, R.string.toggle_hint_repeat_last_symbol)
    )

    private val byKey = definitions.associateBy(Definition::key)
    private val togglesByKey = toggleDefinitions.associateBy(ToggleDefinition::key)

    fun defaultFor(key: String): Float =
        requireNotNull(byKey[key]) { "Unknown keyboard setting: $key" }.default

    fun read(preferences: SharedPreferences, key: String): Float {
        val setting = requireNotNull(byKey[key]) { "Unknown keyboard setting: $key" }
        return preferences.getFloat(key, setting.default).coerceIn(setting.min, setting.max)
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
        return preferences.getBoolean(key, defaultEnabled)
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
