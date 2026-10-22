package app.vimex.microglideboard

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.app.AlertDialog
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("glide_kb", Context.MODE_PRIVATE) }
    private val settings = KeyboardSettings.definitions
    private val fields = mutableMapOf<String, EditText>()
    private var previewLinesButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = s(R.string.settings_title)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
        }

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val appIcon = ImageView(this@MainActivity).apply {
                setImageResource(R.mipmap.ic_launcher)
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).also { it.marginEnd = dp(12) }
                contentDescription = s(R.string.app_icon_description)
            }
            addView(appIcon)
            addView(TextView(this@MainActivity).apply {
                text = s(R.string.app_name)
                textSize = 24f
            })
        })
        root.addView(TextView(this).apply {
            text = s(R.string.settings_intro)
            textSize = 14f
            setPadding(0, dp(8), 0, dp(16))
        })

        addSection(root, R.string.section_interface_language)
        val languageGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(0, 0, 0, dp(10))
        }
        val savedLanguage = prefs.getString(UI_LANGUAGE_KEY, LANGUAGE_UKRAINIAN) ?: LANGUAGE_UKRAINIAN
        listOf(
            LANGUAGE_UKRAINIAN to R.string.language_ukrainian,
            LANGUAGE_ENGLISH to R.string.language_english
        ).forEach { (code, labelRes) ->
            languageGroup.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = s(labelRes)
                isChecked = savedLanguage == code
                tag = code
            })
        }
        languageGroup.setOnCheckedChangeListener { group, checkedId ->
            val selected = group.findViewById<RadioButton>(checkedId)?.tag as? String ?: LANGUAGE_UKRAINIAN
            if (selected != (prefs.getString(UI_LANGUAGE_KEY, LANGUAGE_UKRAINIAN) ?: LANGUAGE_UKRAINIAN)) {
                prefs.edit().putString(UI_LANGUAGE_KEY, selected).apply()
                recreate()
            }
        }
        root.addView(languageGroup)

        addSection(root, R.string.section_keyboard_appearance)
        root.addView(TextView(this).apply {
            text = s(R.string.keyboard_theme_description)
            textSize = 13f
            alpha = 0.75f
            setPadding(0, 0, 0, dp(4))
        })
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
        val keyboardThemeButtons = keyboardThemeOptions.associate { (value, labelRes) ->
            value to RadioButton(this).apply {
                id = View.generateViewId()
                text = s(labelRes)
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

        var lastNumericSection = 0
        settings.forEach { setting ->
            if (setting.key == "preview_lines") return@forEach
            val sectionRes = numericSection(setting.key)
            if (sectionRes != lastNumericSection) {
                addSection(root, sectionRes)
                lastNumericSection = sectionRes
            }
            root.addView(TextView(this).apply {
                text = s(setting.labelRes)
                textSize = 15f
                setPadding(0, dp(10), 0, dp(3))
            })
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val edit = EditText(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                maxLines = 1
                setText(KeyboardSettings.read(prefs, setting.key).toString())
                hint = s(setting.hintRes)
                fields[setting.key] = this
            }
            row.addView(edit)
            row.addView(Button(this).apply {
                text = s(R.string.button_reset)
                setOnClickListener {
                    edit.setText(setting.default.toString())
                    KeyboardSettings.resetOne(prefs, setting.key)
                    Toast.makeText(
                        this@MainActivity,
                        s(R.string.toast_setting_reset, s(setting.labelRes)),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
            root.addView(row)
            root.addView(TextView(this).apply {
                text = s(setting.hintRes)
                textSize = 12f
                alpha = 0.7f
            })
        }

        addSection(root, R.string.section_test_keyboard)
        root.addView(EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            hint = s(R.string.keyboard_test_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP or Gravity.START
        })

        addSection(root, R.string.section_live_options)
        var lastToggleSection = 0
        KeyboardSettings.toggleDefinitions.forEach { setting ->
            val sectionRes = toggleSection(setting.key)
            if (sectionRes != lastToggleSection) {
                addSubsection(root, sectionRes)
                lastToggleSection = sectionRes
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val isTextPreviewToggle = setting.key == "show_text_preview"
            val checkBox = CheckBox(this).apply {
                text = s(setting.labelRes)
                isChecked = KeyboardSettings.readBoolean(prefs, setting.key)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(setting.key, checked).apply()
                }
            }
            row.addView(
                checkBox,
                LinearLayout.LayoutParams(
                    if (isTextPreviewToggle) 0 else ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    if (isTextPreviewToggle) 1f else 0f
                )
            )
            if (isTextPreviewToggle) {
                val lineCountButton = Button(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT)
                    contentDescription = s(R.string.setting_preview_lines)
                    fun refreshLabel() {
                        text = KeyboardSettings.read(prefs, "preview_lines").toInt().toString()
                    }
                    refreshLabel()
                    setOnClickListener {
                        val picker = NumberPicker(this@MainActivity).apply {
                            minValue = 1
                            maxValue = 50
                            wrapSelectorWheel = false
                            value = KeyboardSettings.read(prefs, "preview_lines").toInt().coerceIn(1, 50)
                            descendantFocusability = NumberPicker.FOCUS_AFTER_DESCENDANTS
                        }
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle(s(R.string.setting_preview_lines))
                            .setView(picker)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                prefs.edit().putFloat("preview_lines", picker.value.toFloat()).apply()
                                refreshLabel()
                            }
                            .show()
                    }
                }
                previewLinesButton = lineCountButton
                row.addView(lineCountButton)
            }
            root.addView(row)
            root.addView(TextView(this).apply {
                text = s(setting.hintRes)
                textSize = 12f
                alpha = 0.7f
            })
        }

        root.addView(Button(this).apply {
            text = s(R.string.button_save_numeric)
            setOnClickListener { saveSettings() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(18) }
        })
        root.addView(Button(this).apply {
            text = s(R.string.button_android_keyboard_settings)
            setOnClickListener {
                runCatching { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                    .onFailure {
                        Toast.makeText(this@MainActivity, s(R.string.toast_android_settings_failed), Toast.LENGTH_SHORT).show()
                    }
            }
        })
        root.addView(Button(this).apply {
            text = s(R.string.button_choose_active_keyboard)
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
            }
        })
        root.addView(Button(this).apply {
            text = s(R.string.button_reset_all_numeric)
            setOnClickListener {
                KeyboardSettings.resetAll(prefs)
                settings.forEach { fields[it.key]?.setText(it.default.toString()) }
                previewLinesButton?.text = KeyboardSettings.read(prefs, "preview_lines").toInt().toString()
                Toast.makeText(this@MainActivity, s(R.string.toast_all_numeric_reset), Toast.LENGTH_SHORT).show()
            }
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun numericSection(key: String): Int = when (key) {
        "height_dp", "left_ratio", "bottom_margin_dp", "gesture_height_scale", "gesture_center_offset_dp" -> R.string.section_size_position
        "gest_dp", "gesture_speed" -> R.string.section_gesture_behavior
        "preview_lines" -> R.string.section_text_indicator
        else -> R.string.section_hint_sizes
    }

    private fun toggleSection(key: String): Int = when (key) {
        "show_text_preview", "disable_left_hints", "disable_right_hints" -> R.string.section_indicator_hints
        "micro_vibration", "normal_vibration" -> R.string.section_vibration
        else -> R.string.section_text_entry
    }

    private fun addSection(root: LinearLayout, titleRes: Int) {
        root.addView(TextView(this).apply {
            text = s(titleRes)
            textSize = 18f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(4))
        })
    }

    private fun addSubsection(root: LinearLayout, titleRes: Int) {
        root.addView(TextView(this).apply {
            text = s(titleRes)
            textSize = 16f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(2))
        })
    }

    /** Reads resources using the language selected in this screen, not the phone language. */
    private fun s(resourceId: Int, vararg formatArgs: Any): String {
        val languageCode = prefs.getString(UI_LANGUAGE_KEY, LANGUAGE_UKRAINIAN) ?: LANGUAGE_UKRAINIAN
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(languageCode))
        return createConfigurationContext(configuration).getString(resourceId, *formatArgs)
    }

    private fun saveSettings() {
        val editor = prefs.edit()
        settings.forEach { setting ->
            if (setting.key == "preview_lines") return@forEach
            val entered = fields[setting.key]?.text?.toString()?.replace(',', '.')?.toFloatOrNull() ?: setting.default
            val raw = if (entered.isFinite()) entered else setting.default
            editor.putFloat(setting.key, raw.coerceIn(setting.min, setting.max))
        }
        editor.apply()
        Toast.makeText(this, s(R.string.toast_numeric_saved), Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val UI_LANGUAGE_KEY = "ui_language"
        private const val LANGUAGE_UKRAINIAN = "uk"
        private const val LANGUAGE_ENGLISH = "en"
    }
}
