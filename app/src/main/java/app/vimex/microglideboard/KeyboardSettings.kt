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
        Definition("bottom_margin_dp", "Відступ клавіатури від нижнього краю", 0f, "dp · типово 0", 0f, 80f),
        Definition("gesture_height_scale", "Висота зон жестів", 1f, "масштаб висоти центрів · 0.45–1.0 · типово 1.0", 0.45f, 1f),
        Definition("gesture_center_offset_dp", "Вертикальне зміщення центру жестів", 0f, "dp · мінус = вгору, плюс = вниз", -100f, 100f)
    )

    // Independent live switches: persisted as soon as the user changes a checkbox.
    // They are intentionally not part of the numeric defaults/reset mechanism.
    val toggleDefinitions = listOf(
        ToggleDefinition("disable_left_hints", "Вимкнути всі ліві підказки",
            "Ховає цифри груп, підказки букв і кружечки повтору/видалення. Горизонтальні межі динамічних зон жестів та кнопки курсора залишаються."),
        ToggleDefinition("disable_right_hints", "Вимкнути праві підказки",
            "Ховає кругову підказку та індикатор точки дотику; жести праворуч продовжують працювати."),
        ToggleDefinition("micro_vibration", "Мікровібрація клавіш",
            "Короткий імпульс. Якщо телефон підтримує регулювання сили, імпульс буде слабшим; інакше Android може використати стандартну силу. Реальна інтенсивність залежить від телефона."),
        ToggleDefinition("normal_vibration", "Звичайна вібрація клавіш (мінімальна)",
            "Короткий імпульс при введенні або видаленні. Якщо силу не можна налаштовувати, інтенсивність визначає прошивка телефону."),
        ToggleDefinition("delete_space_on_quick_symbol", "Видаляти пробіл перед швидким символом",
            "Якщо перед символом є пробіл, він буде замінений символом."),
        ToggleDefinition("persistent_number_row", "Постійний ряд цифр зверху",
            "Показує цифри над основною клавіатурою; цифри на сторінці символів не змінюються."),
        ToggleDefinition("space_after_quick_symbol", "Ставити пробіл після швидкого символу",
            "Автоматично додає пробіл після символу з правої панелі."),
        ToggleDefinition("auto_capitalization", "Автовелика літера на початку речення",
            "Першу літеру після початку тексту або . ! ? робить великою."),
        ToggleDefinition("key_repeat_enabled", "Повторне видалення при утриманні",
            "Після затримки утримання Backspace автоматично повторює видалення. Кнопки переміщення курсора повторюють рух завжди й від цієї опції не залежать."),
        ToggleDefinition("sticky_repeat_symbol", "Залипання повтору останнього символу",
            "Утримуйте лівий палець у зоні повтору, щоб повторювати останній введений символ; логіка як у видалення.")
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
