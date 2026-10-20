package app.vimex.microglideboard

import android.content.SharedPreferences

/** Single source of truth for user-visible settings, defaults and allowed ranges. */
object KeyboardSettings {
    data class Definition(
        val key: String,
        val label: String,
        val default: Float,
        val hint: String,
        val min: Float,
        val max: Float
    )

    data class ToggleDefinition(
        val key: String,
        val label: String,
        val hint: String
    )

    val definitions = listOf(
        Definition("height_dp", "Висота клавіатури", 330f, "dp · типово 330", 240f, 420f),
        Definition("left_ratio", "Ширина лівої зони", 0.38f, "0.30–0.52 · типово 0.38", 0.30f, 0.52f),
        Definition("gest_dp", "Поріг відстані свайпу", 10f, "dp · типово 10 · 0 = без порога відстані", 0f, 24f),
        Definition("gesture_speed", "Мінімальна швидкість свайпу", 55f, "dp/с · типово 55 · 0 = вимкнути фільтр швидкості", 0f, 800f),
        Definition("hint_right", "Розмір кругової підказки справа", 42f, "dp · типово 42", 28f, 48f),
        Definition("hint_bubble", "Розмір підказок груп букв", 1f, "масштаб · типово 1.0", 0.7f, 1.5f),
        Definition("hint_pill", "Розмір підказок повтору/видалення", 17f, "dp · радіус кружечка · типово 17", 12f, 24f),
        Definition("pill_dist", "Відступ підказок від нульової точки", 46f, "dp · типово 46", 12f, 60f)
    )

    // Independent live switches: persisted as soon as the user changes a checkbox.
    // They are intentionally not part of the numeric defaults/reset mechanism.
    val toggleDefinitions = listOf(
        ToggleDefinition("disable_left_hints", "Вимкнути всі ліві підказки",
            "Ховає маркери груп, підказки букв, повтору/видалення та розділювачі. Кнопки курсора залишаються."),
        ToggleDefinition("disable_right_hints", "Вимкнути праві підказки",
            "Ховає кругову підказку та індикатор точки дотику; жести праворуч продовжують працювати.")
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
        // Unset toggles start unchecked on a fresh install; there is no reset-to-default UI.
        return preferences.getBoolean(key, false)
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
