package app.vimex.microglideboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("glide_kb", Context.MODE_PRIVATE) }

    private val settings = KeyboardSettings.definitions
    private val fields = mutableMapOf<String, EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "MicroGlideBoard — налаштування"
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
        }

        root.addView(TextView(this).apply {
            text = "MicroGlideBoard"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "Експериментальна жестова клавіатура. Числові параметри зберігай кнопкою нижче; перемикачі підказок зберігаються одразу."
            textSize = 14f
            setPadding(0, dp(8), 0, dp(16))
        })

        settings.forEach { setting ->
            root.addView(TextView(this).apply {
                text = setting.label
                textSize = 15f
                setPadding(0, dp(10), 0, dp(3))
            })
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val edit = EditText(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                maxLines = 1
                setText(KeyboardSettings.read(prefs, setting.key).toString())
                hint = setting.hint
                fields[setting.key] = this
            }
            row.addView(edit)
            row.addView(Button(this).apply {
                text = "Скинути"
                setOnClickListener {
                    edit.setText(setting.default.toString())
                    KeyboardSettings.resetOne(prefs, setting.key)
                    Toast.makeText(this@MainActivity, "Скинуто: ${setting.label}", Toast.LENGTH_SHORT).show()
                }
            })
            root.addView(row)
            root.addView(TextView(this).apply {
                text = setting.hint
                textSize = 12f
                alpha = 0.7f
            })
        }

        root.addView(TextView(this).apply {
            text = "Підказки"
            textSize = 18f
            setPadding(0, dp(18), 0, dp(4))
        })
        KeyboardSettings.toggleDefinitions.forEach { setting ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val checkBox = CheckBox(this).apply {
                text = setting.label
                isChecked = KeyboardSettings.readBoolean(prefs, setting.key)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                // Save switches immediately; the main Save button is only for numeric settings.
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(setting.key, checked).apply()
                }
            }
            row.addView(checkBox)
            root.addView(row)
            root.addView(TextView(this).apply {
                text = setting.hint
                textSize = 12f
                alpha = 0.7f
            })
        }

        root.addView(Button(this).apply {
            text = "Зберегти налаштування"
            setOnClickListener { saveSettings() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dp(18)
            }
        })
        root.addView(Button(this).apply {
            text = "Відкрити налаштування клавіатур Android"
            setOnClickListener {
                runCatching { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                    .onFailure { Toast.makeText(this@MainActivity, "Не вдалося відкрити налаштування Android", Toast.LENGTH_SHORT).show() }
            }
        })
        root.addView(Button(this).apply {
            text = "Вибрати активну клавіатуру"
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
            }
        })
        root.addView(Button(this).apply {
            text = "Скинути всі числові значення"
            setOnClickListener {
                KeyboardSettings.resetAll(prefs)
                settings.forEach { fields[it.key]?.setText(it.default.toString()) }
                Toast.makeText(this@MainActivity, "Числові налаштування скинуто до типових", Toast.LENGTH_SHORT).show()
            }
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun saveSettings() {
        val editor = prefs.edit()
        settings.forEach { setting ->
            val raw = fields[setting.key]?.text?.toString()?.replace(',', '.')?.toFloatOrNull() ?: setting.default
            editor.putFloat(setting.key, raw.coerceIn(setting.min, setting.max))
        }
        editor.apply()
        Toast.makeText(this, "Налаштування збережено", Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
