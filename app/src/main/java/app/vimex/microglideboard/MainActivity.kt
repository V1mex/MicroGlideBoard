package app.vimex.microglideboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("glide_kb", Context.MODE_PRIVATE) }

    private val settings = KeyboardSettings.definitions
    private val fields = mutableMapOf<String, EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = localizedString(R.string.settings_title)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
        }

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val appIcon = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.old_icon)
                layoutParams = LinearLayout.LayoutParams(dp(50), dp(50)).also {
                    it.marginEnd = dp(12)
                }
                contentDescription = localizedString(R.string.app_icon_description)
            }
            addView(appIcon)
            addView(TextView(this@MainActivity).apply {
                text = localizedString(R.string.app_name)
                textSize = 24f
            })
        })
        root.addView(TextView(this).apply {
            text = localizedString(R.string.settings_intro)
            textSize = 14f
            setPadding(0, dp(8), 0, dp(16))
        })

        root.addView(TextView(this).apply {
            text = localizedString(R.string.section_interface_language)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(4))
        })
        val languageGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(0, 0, 0, dp(10))
        }
        val savedLanguage = prefs.getString("ui_language", "uk") ?: "uk"
        listOf("uk" to R.string.language_ukrainian, "en" to R.string.language_english).forEach { (code, labelRes) ->
            languageGroup.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = localizedString(labelRes)
                isChecked = savedLanguage == code
                tag = code
            })
        }
        languageGroup.setOnCheckedChangeListener { group, checkedId ->
            val selected = group.findViewById<RadioButton>(checkedId)?.tag as? String ?: "uk"
            if (selected != (prefs.getString("ui_language", "uk") ?: "uk")) {
                prefs.edit().putString("ui_language", selected).apply()
                recreate()
            }
        }
        root.addView(languageGroup)

        root.addView(TextView(this).apply {
            text = localizedString(R.string.section_keyboard_appearance)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = localizedString(R.string.keyboard_theme_description)
            textSize = 13f
            alpha = 0.75f
            setPadding(0, 0, 0, dp(4))
        })
        // Explicit types keep Kotlin from trying to infer Pair<Nothing, Nothing>.
        // All visible theme names live in Android string resources.
        val keyboardThemeOptions: List<Pair<String, Int>> = listOf(
            KeyboardSettings.KEYBOARD_THEME_SYSTEM to R.string.keyboard_theme_system,
            KeyboardSettings.KEYBOARD_THEME_DARK to R.string.keyboard_theme_dark,
            KeyboardSettings.KEYBOARD_THEME_LIGHT to R.string.keyboard_theme_light,
            KeyboardSettings.KEYBOARD_THEME_OLED to R.string.keyboard_theme_oled
        )
        val keyboardThemeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val keyboardThemeButtons = keyboardThemeOptions.associate { (value, labelResource) ->
            value to RadioButton(this).apply {
                id = View.generateViewId()
                text = localizedString(labelResource)
                isChecked = value == KeyboardSettings.readKeyboardTheme(prefs)
                layoutParams = RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                keyboardThemeGroup.addView(this)
            }
        }
        keyboardThemeGroup.setOnCheckedChangeListener { _, checkedId ->
            val selected = keyboardThemeButtons.entries.firstOrNull { it.value.id == checkedId }?.key
                ?: KeyboardSettings.KEYBOARD_THEME_SYSTEM
            prefs.edit().putString(KeyboardSettings.KEYBOARD_THEME_KEY, selected).apply()
        }
        root.addView(keyboardThemeGroup)

        val settingsByKey = settings.associateBy { it.key }
        val numericSettingGroups: List<Pair<Int, List<String>>> = listOf(
            R.string.section_size_position to listOf("height_dp", "left_ratio", "bottom_margin_dp", "keyboard_vertical_offset_dp"),
            R.string.section_gesture_behavior to listOf("gest_dp", "gesture_speed", "gesture_height_scale", "gesture_center_offset_dp"),
            R.string.section_text_indicator to listOf("preview_lines"),
            R.string.section_hint_sizes to listOf("hint_right", "hint_bubble", "hint_pill")
        )
        numericSettingGroups.forEach { (sectionTitleRes, keys) ->
            root.addView(TextView(this).apply {
                text = localizedString(sectionTitleRes)
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(14), 0, dp(2))
            })
            keys.forEach { key ->
                val setting = requireNotNull(settingsByKey[key]) { "Unknown numeric setting group key: $key" }
                root.addView(TextView(this).apply {
                    text = settingLabel(setting.key)
                    textSize = 15f
                    setPadding(0, dp(10), 0, dp(3))
                })
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val edit = EditText(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    inputType = if (setting.key == "preview_lines") {
                        InputType.TYPE_CLASS_NUMBER
                    } else {
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                    }
                    maxLines = 1
                    isSingleLine = true
                    imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                    setOnEditorActionListener { _, actionId, event ->
                        val enterPressed = actionId == EditorInfo.IME_ACTION_DONE ||
                                actionId == EditorInfo.IME_ACTION_GO ||
                                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                        if (enterPressed) {
                            saveSettings()
                            true
                        } else {
                            false
                        }
                    }
                    setOnKeyListener { _, keyCode, event ->
                        if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                            saveSettings()
                            true
                        } else {
                            false
                        }
                    }
                    setText(KeyboardSettings.read(prefs, setting.key).toString())
                    hint = settingHint(setting.key)
                    fields[setting.key] = this
                }
                row.addView(edit)
                row.addView(Button(this).apply {
                    text = localizedString(R.string.button_reset)
                    setOnClickListener {
                        edit.setText(setting.default.toString())
                        KeyboardSettings.resetOne(prefs, setting.key)
                        Toast.makeText(this@MainActivity, localizedString(R.string.toast_setting_reset, settingLabel(setting.key)), Toast.LENGTH_SHORT).show()
                    }
                })
                root.addView(row)
                root.addView(TextView(this).apply {
                    text = settingHint(setting.key)
                    textSize = 12f
                    alpha = 0.7f
                })
            }
        }

        // A small text field to test the keyboard directly from the settings screen.
        root.addView(TextView(this).apply {
            text = localizedString(R.string.section_test_keyboard)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(4))
        })
        root.addView(EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            hint = localizedString(R.string.keyboard_test_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP or Gravity.START
        })

        // Older builds could persist both vibration switches as enabled. Normalize that
        // once when opening settings; micro vibration has priority for those old values.
        if (prefs.getBoolean("micro_vibration", true) && prefs.getBoolean("normal_vibration", false)) {
            prefs.edit().putBoolean("normal_vibration", false).apply()
        }
        val toggleCheckBoxes = mutableMapOf<String, CheckBox>()
        var updatingVibrationPair = false
        val toggleGroups: List<Pair<Int, List<String>>> = listOf(
            R.string.section_indicator_hints to listOf("show_text_preview", "disable_left_hints", "disable_right_hints"),
            R.string.section_vibration to listOf("micro_vibration", "normal_vibration"),
            R.string.section_keyboard_behavior to listOf(
                "persistent_number_row", "delete_space_on_quick_symbol", "space_after_quick_symbol",
                "auto_capitalization", "key_repeat_enabled", "sticky_repeat_symbol"
            )
        )
        val togglesByKey = KeyboardSettings.toggleDefinitions.associateBy { it.key }
        toggleGroups.forEach { (sectionTitleRes, keys) ->
            root.addView(TextView(this).apply {
                text = localizedString(sectionTitleRes)
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(18), 0, dp(4))
            })
            keys.forEach { key ->
                val setting = requireNotNull(togglesByKey[key]) { "Unknown toggle group key: $key" }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val checkBox = CheckBox(this).apply {
                    text = toggleLabel(setting.key)
                    isChecked = KeyboardSettings.readBoolean(prefs, setting.key)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    // Vibration switches are mutually exclusive when enabled; both may be off.
                    setOnCheckedChangeListener { _, checked ->
                        val otherVibrationKey = when (setting.key) {
                            "micro_vibration" -> "normal_vibration"
                            "normal_vibration" -> "micro_vibration"
                            else -> null
                        }
                        if (otherVibrationKey == null) {
                            prefs.edit().putBoolean(setting.key, checked).apply()
                        } else if (!updatingVibrationPair) {
                            updatingVibrationPair = true
                            if (checked) toggleCheckBoxes[otherVibrationKey]?.isChecked = false
                            updatingVibrationPair = false

                            val editor = prefs.edit().putBoolean(setting.key, checked)
                            if (checked) editor.putBoolean(otherVibrationKey, false)
                            editor.apply()
                        }
                    }
                }
                toggleCheckBoxes[setting.key] = checkBox
                row.addView(checkBox)
                root.addView(row)
                root.addView(TextView(this).apply {
                    text = toggleHint(setting.key)
                    textSize = 12f
                    alpha = 0.7f
                })
            }
        }

        root.addView(CheckBox(this).apply {
            text = localizedString(R.string.toggle_vibe_coder_protection)
            setOnCheckedChangeListener { button, checked ->
                if (checked) {
                    // Android always asks for confirmation before uninstalling a package.
                    button.isChecked = false
                    runCatching {
                        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
                    }.onFailure {
                        Toast.makeText(this@MainActivity, localizedString(R.string.toast_uninstall_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

        root.addView(Button(this).apply {
            text = localizedString(R.string.button_save_numeric)
            setOnClickListener { saveSettings() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dp(18)
            }
        })
        root.addView(Button(this).apply {
            text = localizedString(R.string.button_android_keyboard_settings)
            setOnClickListener {
                runCatching { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                    .onFailure { Toast.makeText(this@MainActivity, localizedString(R.string.toast_android_settings_failed), Toast.LENGTH_SHORT).show() }
            }
        })
        root.addView(Button(this).apply {
            text = localizedString(R.string.button_choose_active_keyboard)
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
            }
        })
        root.addView(Button(this).apply {
            text = localizedString(R.string.button_reset_all_numeric)
            setOnClickListener {
                KeyboardSettings.resetAll(prefs)
                settings.forEach { fields[it.key]?.setText(it.default.toString()) }
                Toast.makeText(this@MainActivity, localizedString(R.string.toast_all_numeric_reset), Toast.LENGTH_SHORT).show()
            }
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    /** All visible UI strings are resolved from Android resources in the selected app language. */
    private fun localizedString(resourceId: Int, vararg formatArgs: Any): String {
        val languageCode = prefs.getString("ui_language", "uk") ?: "uk"
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(languageCode))
        val localizedContext = createConfigurationContext(configuration)
        return if (formatArgs.isEmpty()) localizedContext.getString(resourceId)
        else localizedContext.getString(resourceId, *formatArgs)
    }

    private fun settingLabel(key: String): String = localizedString(when (key) {
        "height_dp" -> R.string.setting_height
        "left_ratio" -> R.string.setting_left_ratio
        "gest_dp" -> R.string.setting_gesture_distance
        "gesture_speed" -> R.string.setting_gesture_speed
        "preview_lines" -> R.string.setting_preview_lines
        "hint_right" -> R.string.setting_hint_right
        "hint_bubble" -> R.string.setting_hint_bubble
        "hint_pill" -> R.string.setting_hint_pill
        "bottom_margin_dp" -> R.string.setting_bottom_margin
        "gesture_height_scale" -> R.string.setting_gesture_height
        "gesture_center_offset_dp" -> R.string.setting_gesture_offset
        "keyboard_vertical_offset_dp" -> R.string.setting_keyboard_offset
        else -> throw IllegalArgumentException("Unknown setting key: $key")
    })

    private fun settingHint(key: String): String = localizedString(when (key) {
        "height_dp" -> R.string.hint_height
        "left_ratio" -> R.string.hint_left_ratio
        "gest_dp" -> R.string.hint_gesture_distance
        "gesture_speed" -> R.string.hint_gesture_speed
        "preview_lines" -> R.string.hint_preview_lines
        "hint_right" -> R.string.hint_hint_right
        "hint_bubble" -> R.string.hint_hint_bubble
        "hint_pill" -> R.string.hint_hint_pill
        "bottom_margin_dp" -> R.string.hint_bottom_margin
        "gesture_height_scale" -> R.string.hint_gesture_height
        "gesture_center_offset_dp" -> R.string.hint_gesture_offset
        "keyboard_vertical_offset_dp" -> R.string.hint_keyboard_offset
        else -> throw IllegalArgumentException("Unknown setting key: $key")
    })

    private fun toggleLabel(key: String): String = localizedString(when (key) {
        "show_text_preview" -> R.string.toggle_show_text_preview
        "disable_left_hints" -> R.string.toggle_disable_left_hints
        "disable_right_hints" -> R.string.toggle_disable_right_hints
        "micro_vibration" -> R.string.toggle_micro_vibration
        "normal_vibration" -> R.string.toggle_normal_vibration
        "delete_space_on_quick_symbol" -> R.string.toggle_delete_space_symbol
        "persistent_number_row" -> R.string.toggle_number_row
        "space_after_quick_symbol" -> R.string.toggle_space_after_symbol
        "auto_capitalization" -> R.string.toggle_auto_capitalization
        "key_repeat_enabled" -> R.string.toggle_key_repeat
        "sticky_repeat_symbol" -> R.string.toggle_repeat_last_symbol
        else -> throw IllegalArgumentException("Unknown toggle key: $key")
    })

    private fun toggleHint(key: String): String = localizedString(when (key) {
        "show_text_preview" -> R.string.toggle_hint_show_text_preview
        "disable_left_hints" -> R.string.toggle_hint_disable_left_hints
        "disable_right_hints" -> R.string.toggle_hint_disable_right_hints
        "micro_vibration" -> R.string.toggle_hint_micro_vibration
        "normal_vibration" -> R.string.toggle_hint_normal_vibration
        "delete_space_on_quick_symbol" -> R.string.toggle_hint_delete_space_symbol
        "persistent_number_row" -> R.string.toggle_hint_number_row
        "space_after_quick_symbol" -> R.string.toggle_hint_space_after_symbol
        "auto_capitalization" -> R.string.toggle_hint_auto_capitalization
        "key_repeat_enabled" -> R.string.toggle_hint_key_repeat
        "sticky_repeat_symbol" -> R.string.toggle_hint_repeat_last_symbol
        else -> throw IllegalArgumentException("Unknown toggle key: $key")
    })

    private fun saveSettings() {
        val editor = prefs.edit()
        settings.forEach { setting ->
            val parsed = fields[setting.key]?.text?.toString()?.replace(',', '.')?.toFloatOrNull() ?: setting.default
            val raw = if (parsed.isNaN()) setting.default else parsed
            editor.putFloat(setting.key, raw.coerceIn(setting.min, setting.max))
        }
        // commit() updates SharedPreferences synchronously before asking the live IME view to relayout.
        editor.commit()
        GlideKeyboardService.reloadActiveKeyboardSettings()
        Toast.makeText(this, localizedString(R.string.toast_numeric_saved), Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
