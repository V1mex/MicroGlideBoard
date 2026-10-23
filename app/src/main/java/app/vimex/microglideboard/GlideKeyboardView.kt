package app.vimex.microglideboard

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import kotlin.math.*

class GlideKeyboardView(context: Context) : View(context) {

    companion object {
        private const val PREV_DP   = 26f
        private const val NUMBER_ROW_DP = 52f
        private const val NUMBER_LABEL_STRIP_DP = 15f
        private const val NUMBER_KEYS_AREA_DP = NUMBER_ROW_DP - NUMBER_LABEL_STRIP_DP
        private const val CX_R      = 0.50f
        private const val Y_TOP     = 0.12f; private const val Y_BOT = 0.88f
        private const val SIDE_DP   = 22f
        private const val CENR_DP   = 14f;  private const val HUP_DP = 96f
        private const val BS_HOLD_MS = 500L;  private const val BS_RPT_MS = 80L
        private const val PILL_OFFSET_DP = 46f
        private const val LK = "\u0001"
        private const val SK = "\u0002"
    }

    private val dp = resources.displayMetrics.density
    private val prefs = context.getSharedPreferences("glide_kb", Context.MODE_PRIVATE)
    private val hintToggleListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "ui_language" -> {
                keyboardLanguageLabels = buildKeyboardLanguageLabels()
                invalidate()
            }
            "disable_left_hints", "disable_right_hints" -> {
                disableLeftHints = KeyboardSettings.readBoolean(prefs, "disable_left_hints")
                disableRightHints = KeyboardSettings.readBoolean(prefs, "disable_right_hints")
                invalidate()
            }
            KeyboardSettings.KEYBOARD_THEME_KEY -> {
                applyKeyboardTheme()
                invalidate()
            }
            "show_text_preview", "preview_lines", "persistent_number_row",
            "height_dp", "bottom_margin_dp", "left_ratio", "gest_dp", "gesture_speed",
            "hint_right", "hint_bubble", "hint_pill", "gesture_height_scale", "gesture_center_offset_dp",
            "keyboard_vertical_offset_dp" -> reloadSettings()
        }
    }

    private var heightDp = KeyboardSettings.defaultFor("height_dp")
    private var keyboardVerticalOffsetDp = KeyboardSettings.defaultFor("keyboard_vertical_offset_dp")
    private var leftRatio = KeyboardSettings.defaultFor("left_ratio")
    private var gestR = KeyboardSettings.defaultFor("gest_dp") * dp
    // Мінімальна швидкість свайпу в dp/s. 0 вимикає саме швидкісний фільтр;
    // поріг відстані gestR при цьому залишається окремим захистом від випадкових рухів.
    private var minGestureSpeedDpS = KeyboardSettings.defaultFor("gesture_speed")
    private var sideR = SIDE_DP * dp
    private var cenR = CENR_DP * dp
    private var hUp = HUP_DP * dp
    private var hR = KeyboardSettings.defaultFor("hint_right") * dp
    private var hLR = hR * 0.79f
    private var hBubble = KeyboardSettings.defaultFor("hint_bubble")
    private var disableLeftHints = false
    private var disableRightHints = false
    private var hasNumberRow = KeyboardSettings.readBoolean(prefs, "persistent_number_row")
    private var showTextPreview = KeyboardSettings.readBoolean(prefs, "show_text_preview")
    private var previewLines = KeyboardSettings.read(prefs, "preview_lines").toInt().coerceAtLeast(1)
    private var pillRadius = KeyboardSettings.defaultFor("hint_pill") * dp
    private val pillOff = PILL_OFFSET_DP * dp
    private var bottomMarginDp = KeyboardSettings.defaultFor("bottom_margin_dp")
    private var leftStillSince = 0L
    private var lastLeftX = 0f
    private var lastLeftY = 0f
    private var leftHintFollowY = false
    private val previewStripDp: Float
        get() {
            val base = if (hasNumberRow) NUMBER_LABEL_STRIP_DP else PREV_DP
            if (!showTextPreview) return base

            // No fixed setting limit (1..5 etc.). The requested count is only limited
            // visually by how much height the current display can physically provide.
            val requested = maxOf(base, previewLines.toFloat() * 14.5f + 8f)
            val screenHeightDp = resources.displayMetrics.heightPixels / dp
            val navBarDp = navigationBarInsetPx() / dp
            val maxStrip = base + (screenHeightDp - heightDp - bottomMarginDp - navBarDp).coerceAtLeast(0f)
            return requested.coerceAtMost(maxOf(base, maxStrip))
        }
    private val numberKeysTopPx: Float get() = previewStripDp * dp
    private val prevH: Float
        get() = (previewStripDp + if (hasNumberRow) NUMBER_KEYS_AREA_DP else 0f) * dp

    init { reloadSettings() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.registerOnSharedPreferenceChangeListener(hintToggleListener)
        reloadSettings()
        applyKeyboardTheme()
    }

    override fun onDetachedFromWindow() {
        prefs.unregisterOnSharedPreferenceChangeListener(hintToggleListener)
        super.onDetachedFromWindow()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyKeyboardTheme()
        invalidate()
    }

    fun reloadSettings() {
        val oldHeight = heightDp
        val oldKeyboardOffset = keyboardVerticalOffsetDp
        val oldBottomMargin = bottomMarginDp
        val oldNumberRow = hasNumberRow
        val oldPreviewEnabled = showTextPreview
        val oldPreviewLines = previewLines
        heightDp = KeyboardSettings.read(prefs, "height_dp")
        keyboardVerticalOffsetDp = KeyboardSettings.read(prefs, "keyboard_vertical_offset_dp")
        leftRatio = KeyboardSettings.read(prefs, "left_ratio")
        gestR = KeyboardSettings.read(prefs, "gest_dp") * dp
        minGestureSpeedDpS = KeyboardSettings.read(prefs, "gesture_speed")
        hR = KeyboardSettings.read(prefs, "hint_right") * dp
        hLR = hR * 0.79f
        hBubble = KeyboardSettings.read(prefs, "hint_bubble")
        disableLeftHints = KeyboardSettings.readBoolean(prefs, "disable_left_hints")
        disableRightHints = KeyboardSettings.readBoolean(prefs, "disable_right_hints")
        hasNumberRow = KeyboardSettings.readBoolean(prefs, "persistent_number_row")
        showTextPreview = KeyboardSettings.readBoolean(prefs, "show_text_preview")
        previewLines = KeyboardSettings.read(prefs, "preview_lines").toInt().coerceAtLeast(1)
        pillRadius = KeyboardSettings.read(prefs, "hint_pill") * dp
        bottomMarginDp = KeyboardSettings.read(prefs, "bottom_margin_dp")
        // Extra preview rows share the configured keyboard height with the gesture area.
        if (oldHeight != heightDp || oldKeyboardOffset != keyboardVerticalOffsetDp ||
            oldBottomMargin != bottomMarginDp || oldNumberRow != hasNumberRow || oldPreviewEnabled != showTextPreview ||
            oldPreviewLines != previewLines) requestLayout()
        invalidate()
    }

    /** Redraw the optional text preview when the hosting editor moves its caret/selection. */
    fun onEditorSelectionChanged() {
        if (showTextPreview) {
            invalidate()
            postInvalidateOnAnimation()
        }
    }

    // ↑ ↗ → ↘ ↓ ↙ ← ↖  (індекси 0..7)
    private val N: String? = null
    private val langData = mapOf(
        "uk" to listOf(
            listOf("о", "а", "е", "и", "н", "т", "р", "с"),
            listOf("в", "л", "к", "м", "д", "п", "у", "і"),
            listOf("ь", "я", "з", "й", "б", "г", "ч", "ж"),
            listOf("ю", "х", "ш", "є", "ї", "щ", "ц", "ф"),
            listOf("ґ", "-", "'", N, N, N, N, N)
        ),
        "en" to listOf(
            listOf("e", "a", "o", "i", "n", "t", "r", "s"),
            listOf("w", "l", "c", "m", "d", "p", "u", "h"),
            listOf("g", "y", "f", "b", "v", "k", "j", "q"),
            listOf("x", "z", "'", N, N, N, N, N)
        ),
        "ru" to listOf(
            listOf("о", "а", "е", "и", "н", "т", "р", "с"),
            listOf("в", "л", "к", "м", "д", "п", "у", "ы"),
            listOf("ь", "я", "з", "й", "б", "г", "ч", "ж"),
            listOf("ю", "х", "ш", "э", "ё", "щ", "ц", "ф"),
            listOf("ъ", "-", N, N, N, N, N, N)
        )
    )
    private val symbolEmoticons = listOf(
        "(o_O)",
        "＼(〇_ｏ)／",
        "(─‿─)",
        "(￢_￢)",
        "╮(￣_￣)╭",
        "(=^･ω･^=)",
        "( ಡ ﹏ ಡ )",
        "(〃＾▽＾〃)",
        "(◕‿◕)",
        "(ノ°∀°)ノ",
        "(╯°Д°)╯︵ ┻━┻",
        "┬─┬ノ( º _ ºノ)",
        "(╯°□°)╯︵/(\\ .□ . \\)",
        "┻━┻ ︵ヽ(`Д´)ﾉ︵ ┻━┻",
        "༼ つ ◕_◕ ༼ つ",
        "┌( ಠ_ಠ)┘",
        "(ง ◉ _ ◉)ง",
        "٩(╬ʘ益ʘ╬)۶",
        "ヘ(_ _ ヘ)",
        "(⊙_☉)",
        "┐(￣∀￣)┌",
        "( -_•)╦̵̵̿╤─",
        "＼(≧▽≦)／",
        "((┌|o^▽^o|┘))",
        "(｡◕‿◕｡)",
        "┐(￣ヘ￣)┌",
        "Vibe coding kills",
        "Паляниця"
    )
    private var currentSymbolEmoticon = symbolEmoticons.random()

    // Keep language identity independent from translated display abbreviations.
    private val langOrder = listOf("uk", "en", "ru")
    private var langIdx = 0
    private val lang get() = langOrder[langIdx]
    private var keyboardLanguageLabels = buildKeyboardLanguageLabels()

    private fun buildKeyboardLanguageLabels(): Map<String, String> = mapOf(
        "uk" to appLocalizedString(R.string.keyboard_lang_uk),
        "en" to appLocalizedString(R.string.keyboard_lang_en),
        "ru" to appLocalizedString(R.string.keyboard_lang_ru)
    )

    private fun appLocalizedString(resourceId: Int): String {
        val languageCode = prefs.getString("ui_language", "uk") ?: "uk"
        val configuration = Configuration(resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag(languageCode))
        }
        return context.createConfigurationContext(configuration).getString(resourceId)
    }

    private fun letters() = langData[lang] ?: emptyList()
    private fun numG()                = letters().size
    private fun letAt(g: Int, d: Int) = letters().getOrNull(g)?.getOrNull(d)

    private val dv = listOf(
        0f to -1f, 1f to -1f, 1f to 0f, 1f to 1f,
        0f to  1f,-1f to  1f,-1f to 0f,-1f to -1f
    )

    var inputConnection: InputConnection? = null
    var editorInfo: EditorInfo? = null
    var onSystemBarAppearanceChanged: (() -> Unit)? = null

    fun bottomSystemBarColor(): Int = if (symMode) pSyB.color else pBgRd.color
    fun bottomSystemBarUsesLightIcons(): Boolean = isLightKeyboard

    private var grp = 0; private var lastLtr = ""; private var lastDir = -1
    private var leftId = -1; private var lDown = false
    private var leftCX = 0f
    private var consumed = false; private var inBsZone = false; private var holdBs = false
    private var inRepeatZone = false; private var holdRepeatSymbol = false
    private var rightId = -1; private var rDown = false
    private var rLX = 0f; private var rLY = 0f; private var rCX = 0f; private var rCY = 0f
    private var symMode = false
    private var symPage = 0
    private var symIgnoreUntilAllUp = false
    private var symDownId = -1
    private var symDownX = 0f; private var symDownY = 0f
    private var symTouchX = 0f; private var symTouchY = 0f
    private var symStartBounds: SymKeyBounds? = null
    private var symCurrentBounds: SymKeyBounds? = null
    private var symLongPressed = false
    private var symSelectedAltIndex = 0
    private var symBackspaceRepeating = false

    private enum class ShiftState { OFF, ONCE, CAPS }
    private var shiftState = ShiftState.OFF
    private var lastShiftTap = 0L

    private data class CursorKey(val id: String, val label: String, val keyCode: Int)
    private data class CursorKeyBounds(
        val key: CursorKey,
        val left: Float, val top: Float, val right: Float, val bottom: Float
    ) {
        fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
        val centerX get() = (left + right) / 2f
        val centerY get() = (top + bottom) / 2f
    }

    private val cursorLeft = CursorKey("LEFT", "←", KeyEvent.KEYCODE_DPAD_LEFT)
    private val cursorUp = CursorKey("UP", "↑", KeyEvent.KEYCODE_DPAD_UP)
    private val cursorDown = CursorKey("DOWN", "↓", KeyEvent.KEYCODE_DPAD_DOWN)
    private val cursorRight = CursorKey("RIGHT", "→", KeyEvent.KEYCODE_DPAD_RIGHT)
    private var cursorId = -1
    private var cursorStartBounds: CursorKeyBounds? = null
    private var cursorInside = false
    private val cursorHandler = Handler(Looper.getMainLooper())
    private val cursorRepeatRun = object : Runnable {
        override fun run() {
            val key = cursorStartBounds?.key
            if (cursorId != -1 && cursorInside && key != null) {
                sendCursorKey(key.keyCode)
                cursorHandler.postDelayed(this, BS_RPT_MS)
            }
        }
    }
    private val cursorHoldRun = Runnable {
        if (cursorId != -1 && cursorInside && cursorStartBounds != null) {
            cursorHandler.post(cursorRepeatRun)
        }
    }

    private data class ActionKey(val id: String, val label: String, val alternates: List<String> = emptyList())
    private data class ActionKeyBounds(
        val key: ActionKey, val row: Int, val index: Int,
        val left: Float, val top: Float, val right: Float, val bottom: Float
    ) {
        fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
        val centerX get() = (left + right) / 2f
        val centerY get() = (top + bottom) / 2f
    }

    private val utilityKeys = listOf(
        ActionKey("LANG", "🌐"), ActionKey("SYM", "⌨"), ActionKey("SHIFT", "⇧"),
        ActionKey("REPEAT", "⧉"), ActionKey("ENTER", "↵"), ActionKey("SETTINGS", "⚙")
    )
    private val actionSymbolRows = listOf(
        listOf(
            ActionKey("CHAR:.", ".", listOf("…", "·")),
            ActionKey("CHAR:,", ","),
            ActionKey("CHAR:?", "?", listOf("¿")),
            ActionKey("CHAR:!", "!", listOf("¡"))
        ),
        listOf(
            ActionKey("CHAR::", ":", listOf(";")),
            ActionKey("CHAR:-", "-", listOf("−", "–", "—")),
            ActionKey("BACKSPACE", "⌫")
        ),
        listOf(
            ActionKey("CHAR:(", "(", listOf("[", "{", "<")),
            ActionKey("SPACE", "␣"),
            ActionKey("CHAR:)", ")", listOf("]", "}", ">"))
        )
    )
    private var actionId = -1
    private var actionStart: ActionKeyBounds? = null
    private var actionCurrent: ActionKeyBounds? = null
    private var actionPopupShown = false
    private var actionSelectedAlt = 0
    private var actionDownX = 0f
    private var actionDownY = 0f
    private var actionTouchX = 0f
    private var actionTouchY = 0f
    private var actionBackspaceRepeating = false
    private val actionHandler = Handler(Looper.getMainLooper())
    private val actionBackspaceRepeatRun = object : Runnable {
        override fun run() {
            if (actionId != -1 && actionStart?.key?.id == "BACKSPACE" &&
                actionCurrent?.key?.id == "BACKSPACE") {
                actionBackspaceRepeating = true
                deleteBackwardOrSelection()
                invalidate()
                actionHandler.postDelayed(this, BS_RPT_MS)
            }
        }
    }
    private val actionBackspaceHoldRun = Runnable {
        if (actionId != -1 && actionStart?.key?.id == "BACKSPACE" &&
            actionCurrent?.key?.id == "BACKSPACE") {
            actionBackspaceRepeating = true
            deleteBackwardOrSelection()
            invalidate()
            actionHandler.postDelayed(actionBackspaceRepeatRun, BS_RPT_MS)
        }
    }
    private val actionLongPressRun = Runnable {
        val key = actionStart?.key
        if (actionId != -1 && key != null && key.alternates.isNotEmpty() && actionCurrent?.key == key) {
            actionPopupShown = true
            actionSelectedAlt = key.alternates.size / 2
            invalidate()
        }
    }

    private val symLongPressMs = 420L
    private val symTouchSlop = 7f * dp
    private val symHandler = Handler(Looper.getMainLooper())
    private val symLongPressRun = Runnable {
        val key = symStartBounds?.key
        if (symMode && symDownId != -1 && key != null &&
            key.action == SymAction.INPUT && key.alternates.isNotEmpty() &&
            symCurrentBounds?.key == key) {
            symLongPressed = true
            // Gboard-like behavior: holding in the middle initially selects
            // the central alternative, so horizontal movement picks neighbours.
            symSelectedAltIndex = key.alternates.size / 2
            invalidate()
        }
    }
    private val symBackspaceHoldRun = Runnable {
        if (symMode && symDownId != -1 &&
            symStartBounds?.key?.action == SymAction.BACKSPACE &&
            symCurrentBounds?.key?.action == SymAction.BACKSPACE) {
            symBackspaceRepeating = true
            deleteBackwardOrSelection()
            invalidate()
            symHandler.postDelayed(symBackspaceRepeatRun, BS_RPT_MS)
        }
    }
    private val symBackspaceRepeatRun = object : Runnable {
        override fun run() {
            if (symMode && symBackspaceRepeating && symDownId != -1 &&
                symStartBounds?.key?.action == SymAction.BACKSPACE &&
                symCurrentBounds?.key?.action == SymAction.BACKSPACE) {
                deleteBackwardOrSelection()
                invalidate()
                symHandler.postDelayed(this, BS_RPT_MS)
            } else {
                stopSymBackspaceRepeat()
            }
        }
    }

    private fun stopSymBackspaceRepeat() {
        symBackspaceRepeating = false
        symHandler.removeCallbacks(symBackspaceHoldRun)
        symHandler.removeCallbacks(symBackspaceRepeatRun)
    }

    private val bsH = Handler(Looper.getMainLooper())
    private val bsRun = object : Runnable {
        override fun run() {
            if (KeyboardSettings.readBoolean(prefs, "key_repeat_enabled") && holdBs && inBsZone && lDown && leftId != -1 && !symMode) {
                deleteBackwardOrSelection()
                invalidate()
                bsH.postDelayed(this, BS_RPT_MS)
            } else {
                holdBs = false
            }
        }
    }
    private val bsHoldRun = Runnable {
        if (KeyboardSettings.readBoolean(prefs, "key_repeat_enabled") && lDown && leftId != -1 && consumed && inBsZone && !symMode) {
            holdBs = true
            bsH.post(bsRun)
        }
    }

    private val repeatSymbolRun = object : Runnable {
        override fun run() {
            if (KeyboardSettings.readBoolean(prefs, "sticky_repeat_symbol") && holdRepeatSymbol &&
                inRepeatZone && lDown && leftId != -1 && consumed && !inBsZone && lastLtr.isNotEmpty() && !symMode) {
                commitInputText(lastLtr)
                invalidate()
                bsH.postDelayed(this, BS_RPT_MS)
            } else {
                holdRepeatSymbol = false
            }
        }
    }
    private val repeatSymbolHoldRun = Runnable {
        if (KeyboardSettings.readBoolean(prefs, "sticky_repeat_symbol") && lDown && leftId != -1 &&
            consumed && inRepeatZone && !inBsZone && lastLtr.isNotEmpty() && !symMode) {
            holdRepeatSymbol = true
            bsH.post(repeatSymbolRun)
        }
    }

    private fun stopBackspaceRepeat() {
        holdBs = false
        holdRepeatSymbol = false
        bsH.removeCallbacks(bsRun)
        bsH.removeCallbacks(bsHoldRun)
        bsH.removeCallbacks(repeatSymbolRun)
        bsH.removeCallbacks(repeatSymbolHoldRun)
    }

    private fun resetGesturePointers() {
        leftId = -1; lDown = false
        rightId = -1; rDown = false
        consumed = false; inBsZone = false
        lastDir = -1
        velocitySamples.clear()
        rTD = 0f; candidateDir = -1
        stopBackspaceRepeat()
        clearCursorTracking()
    }

    private fun dX() = width  * leftRatio
    private fun cX() = dX()   * CX_R
    // The measured view includes any expanded text-preview strip and the configured bottom gap.
    // Keep all painted/touchable keyboard content above that gap; at 0 dp there is no artificial blank area.
    /** Height of the system navigation area which is drawn over the bottom of the IME. */
    private fun navigationBarInsetPx(): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val liveBottom = rootWindowInsets?.getInsets(android.view.WindowInsets.Type.navigationBars())?.bottom ?: 0
            if (liveBottom > 0) return liveBottom.toFloat()
        }
        // Input-method views may be measured before receiving WindowInsets. In portrait,
        // use Android's own navigation-bar dimension for the first measurement.
        if (resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT) return 0f
        val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resId != 0) resources.getDimension(resId) else 0f
    }

    /**
     * Positive values move the complete keyboard upward; negative values move it downward.
     * There is no arbitrary dp setting range. This bound only keeps Android MeasureSpecs and
     * Canvas coordinates within their integer/float-safe range for absurdly large user input.
     */
    private fun keyboardVerticalOffsetPx(): Float {
        val rawPx = keyboardVerticalOffsetDp.toDouble() * dp.toDouble()
        if (rawPx.isNaN()) return 0f
        val platformSafeBound = ((1 shl 30) - 1).toDouble() / 4.0
        return rawPx.coerceIn(-platformSafeBound, platformSafeBound).toFloat()
    }

    /** Translation inside the expanded IME view for a downward shift. Upward movement is
     * achieved by increasing the bottom-anchored IME view's measured height. */
    private fun keyboardCanvasOffsetY(): Float {
        val offset = keyboardVerticalOffsetPx()
        return if (offset < 0f) -2f * offset else 0f
    }

    private fun contentBottom() = (
            height.toFloat() - abs(keyboardVerticalOffsetPx()) -
                    bottomMarginDp.coerceAtLeast(0f) * dp - navigationBarInsetPx()
            ).coerceIn(0f, height.toFloat())
    private fun leftSplitY() = prevH + (contentBottom() - prevH) * 0.5f
    private fun idlePointY() = prevH + (leftSplitY() - prevH) * 0.5f
    private fun isInIdleGestureZone(y: Float): Boolean =
        y >= prevH && y < leftSplitY()
    private fun clampLeftCX(x: Float): Float {
        val margin = 8f * dp
        return x.coerceIn(margin, (dX() - margin).coerceAtLeast(margin))
    }
    private fun shownLeftCX() = if (lDown) leftCX else cX()
    private fun gY(g: Int): Float {
        val n = numG()
        // Keep the visible centers (and their bubbles/pills) inside the keyboard's
        // inner drawing bounds. Hit-testing via nearestG() still extends the first
        // and last gesture zones to the outer edges of the left area.
        val zoneTop = prevH
        val zoneBottom = contentBottom()
        val zoneH = (zoneBottom - zoneTop).coerceAtLeast(1f)
        // At scale=1, center each gyro halfway between the zone edge and its
        // neighbor. Add only as much safety inset as the actual hint size needs,
        // otherwise the first/last gyro look unnaturally far from the edge.
        val nominalHalfCell = zoneH / n.toFloat() / 2f
        val visualHalf = maxOf(18f * dp * hBubble, pillRadius + 2f * dp, 10f * dp)
        val edgeInset = (visualHalf - nominalHalfCell).coerceAtLeast(0f)
        val availableTop = zoneTop + edgeInset
        val availableBottom = (zoneBottom - edgeInset).coerceAtLeast(availableTop + 1f)
        val availableH = (availableBottom - availableTop).coerceAtLeast(1f)
        val scale = KeyboardSettings.read(prefs, "gesture_height_scale")
        val offset = KeyboardSettings.read(prefs, "gesture_center_offset_dp") * dp

        // Keep the gesture centers inside their usable area, but do not silently
        // cancel negative offsets when scale=1. Make room in the opposite direction
        // by shortening the spread as the grid is shifted toward an edge.
        val scaledSpan = (availableH * scale).coerceIn(1f, availableH)
        val spanAllowedByOffset = (availableH - abs(offset)).coerceAtLeast(1f)
        val span = minOf(scaledSpan, spanAllowedByOffset)
        val wantedCenter = availableTop + availableH / 2f + offset
        val top = (wantedCenter - span / 2f).coerceIn(availableTop, availableBottom - span)
        return top + span * ((g + 0.5f) / n.toFloat())
    }
    private fun cursorKeyLayout(): List<CursorKeyBounds> {
        val dx = dX()
        val colW = dx / 3f
        val top = leftSplitY()
        val bottom = contentBottom()
        val mid = top + (bottom - top) * 0.5f
        return listOf(
            CursorKeyBounds(cursorLeft, 0f, top, colW, bottom),
            CursorKeyBounds(cursorUp, colW, top, colW * 2f, mid),
            CursorKeyBounds(cursorDown, colW, mid, colW * 2f, bottom),
            CursorKeyBounds(cursorRight, colW * 2f, top, dx, bottom)
        )
    }
    private fun cursorKeyAt(x: Float, y: Float): CursorKeyBounds? =
        cursorKeyLayout().firstOrNull { it.contains(x, y) }
    private fun nearestG(y: Float) = (0 until numG()).minByOrNull { abs(gY(it) - y) } ?: 0

    private fun p(c: String)  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(c) }
    private fun pt(s: Float, c: String, a: Paint.Align = Paint.Align.CENTER) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(c); textSize = s*dp; textAlign = a }
    private fun ps(c: String, w: Float) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(c); style = Paint.Style.STROKE; strokeWidth = w*dp }

    private val pBgT = p("#0D0D1A"); private val pBgL  = p("#161628")
    private val pBgR = p("#13202E"); private val pBgRd = p("#0D1118")
    private val pDvH = ps("#1A263A",1f); private val pDvV = ps("#1A263A",1f)
    private val pGroupDivider = ps("#26344A", 1f)
    private val pPrv = pt(12.5f,"#566380", Paint.Align.RIGHT)
    private val pLng = pt(11f,"#4A9EBA")
    private val pGAc = pt(20f,"#E2E8F0").also { it.typeface = Typeface.DEFAULT_BOLD }
    private val pGIn = pt(17f,"#273450")
    private val pPBg = p("#192336")
    private val pRpT = pt(16f,"#4ADE80"); private val pBsT = pt(16f,"#F87171")
    private val pBbB = p("#0E1924"); private val pBbT = pt(10f,"#7EB0E0")
    private val pHBg = p("#1C3555").also { it.alpha = 245 }
    private val pHBd = ps("#4070A0",1.5f); private val pHDt = p("#4070A0")
    private val pHLa = pt(17f,"#A8D8FF").also { it.typeface = Typeface.DEFAULT_BOLD }
    private val pHLi = pt(16f,"#6B9FD4")
    private val pRDt = p("#5888B8").also { it.alpha = 110 }
    private val pSyB = p("#161624"); private val pSBtn = p("#292B30"); private val pSBAl = p("#3A3B40")
    private val pSPressed = p("#414650")
    private val pSTx = pt(23f,"#F1F2F5")
    private val pSTxS = pt(16f,"#D2D5DC")
    private val pSLang = pt(13.5f,"#D2D5DC")
    private val pSPopupBg = p("#3A3D44")
    private val pSPopupBorder = ps("#707681",1f)
    private val pSPopupSelected = p("#596A82")
    private val pSPopupText = pt(18f,"#FFFFFF")
    private val pSPreviewBg = p("#424750")
    private val pSPreviewBorder = ps("#8B94A2",1f)
    private val pSPreviewText = pt(27f,"#FFFFFF")
    private val pActionBg = p("#1A202A")
    private val pActionBgActive = p("#30435B")
    private val pActionShiftOnceBg = p("#29496B")
    private val pActionShiftCapsBg = p("#4B456F")
    private val pActionText = pt(18f,"#DCE6F2")
    private val pActionSmallText = pt(21f,"#DCE6F2")
    private val pActionEnterText = pt(30f,"#F1F5FA")
    private val pActionPopupBg = p("#3A3D44")
    private val pActionPopupBorder = ps("#8794A6", 1f)
    private val pActionPopupActive = p("#596A82")
    private val pActionPopupText = pt(19f,"#FFFFFF")
    private val pCursorText = pt(28f, "#DCE6F2")
    private val pCursorBorder = ps("#263348", 1f)
    private val pKeyBorder = ps("#39404A", 1f)

    private var isLightKeyboard = false
    private var isOledKeyboard = false

    /** Recolors every custom-drawn part of the keyboard without rebuilding its layout. */
    private fun applyKeyboardTheme() {
        val selected = KeyboardSettings.readKeyboardTheme(prefs)
        val systemIsDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        isLightKeyboard = when (selected) {
            KeyboardSettings.KEYBOARD_THEME_LIGHT -> true
            KeyboardSettings.KEYBOARD_THEME_DARK, KeyboardSettings.KEYBOARD_THEME_OLED -> false
            else -> !systemIsDark
        }
        isOledKeyboard = selected == KeyboardSettings.KEYBOARD_THEME_OLED

        fun recolor(paint: Paint, dark: String, light: String, oled: String = dark) {
            paint.color = Color.parseColor(when {
                isOledKeyboard -> oled
                isLightKeyboard -> light
                else -> dark
            })
        }

        recolor(pBgT, "#0D0D1A", "#F8FAFC", "#000000")
        recolor(pBgL, "#161628", "#F7FAFC", "#000000")
        recolor(pBgR, "#13202E", "#FFFFFF", "#000000")
        recolor(pBgRd, "#0D1118", "#F0F3F7", "#000000")
        recolor(pDvH, "#1A263A", "#C8D3E0", "#161616")
        recolor(pDvV, "#1A263A", "#C8D3E0", "#161616")
        recolor(pGroupDivider, "#26344A", "#C5D0DE", "#202020")
        recolor(pPrv, "#566380", "#64748B")
        recolor(pLng, "#4A9EBA", "#0E7490")
        recolor(pGAc, "#E2E8F0", "#0F172A")
        recolor(pGIn, "#273450", "#64748B")
        recolor(pPBg, "#192336", "#E2E8F0", "#000000")
        recolor(pRpT, "#4ADE80", "#15803D")
        recolor(pBsT, "#F87171", "#B91C1C")
        recolor(pBbB, "#0E1924", "#E2E8F0", "#000000")
        recolor(pBbT, "#7EB0E0", "#245B88")
        recolor(pHBg, "#1C3555", "#DCEBFA", "#000000")
        pHBg.alpha = 245
        recolor(pHBd, "#4070A0", "#7AA4D2")
        recolor(pHDt, "#4070A0", "#3975B8")
        recolor(pHLa, "#A8D8FF", "#174A7E")
        recolor(pHLi, "#6B9FD4", "#426B96")
        recolor(pRDt, "#5888B8", "#3975B8")
        pRDt.alpha = if (isLightKeyboard) 75 else 110
        recolor(pSyB, "#161624", "#F4F6F9", "#000000")
        recolor(pSBtn, "#292B30", "#E3E8EF", "#000000")
        recolor(pSBAl, "#3A3B40", "#CBD5E1", "#000000")
        recolor(pSPressed, "#414650", "#B9CAE0", "#000000")
        recolor(pSTx, "#F1F2F5", "#111827")
        recolor(pSTxS, "#D2D5DC", "#334155")
        recolor(pSLang, "#D2D5DC", "#334155")
        recolor(pSPopupBg, "#3A3D44", "#F8FAFC", "#000000")
        recolor(pSPopupBorder, "#707681", "#94A3B8")
        recolor(pSPopupSelected, "#596A82", "#BCD0E8", "#000000")
        recolor(pSPopupText, "#FFFFFF", "#0F172A")
        recolor(pSPreviewBg, "#424750", "#FFFFFF", "#000000")
        recolor(pSPreviewBorder, "#8B94A2", "#94A3B8")
        recolor(pSPreviewText, "#FFFFFF", "#0F172A")
        recolor(pActionBg, "#1A202A", "#E3E8EF", "#000000")
        recolor(pActionBgActive, "#30435B", "#B9D0E8", "#000000")
        recolor(pActionShiftOnceBg, "#29496B", "#B7D7FA", "#000000")
        recolor(pActionShiftCapsBg, "#4B456F", "#D4C7F2", "#000000")
        recolor(pActionText, "#DCE6F2", "#1E293B")
        recolor(pActionSmallText, "#DCE6F2", "#334155")
        recolor(pActionEnterText, "#F1F5FA", "#111827")
        recolor(pActionPopupBg, "#3A3D44", "#F8FAFC", "#000000")
        recolor(pActionPopupBorder, "#8794A6", "#94A3B8")
        recolor(pActionPopupActive, "#596A82", "#B7CEE8", "#000000")
        recolor(pActionPopupText, "#FFFFFF", "#0F172A")
        recolor(pCursorText, "#DCE6F2", "#1E293B")
        recolor(pCursorBorder, "#263348", "#C2CFDE", "#303030")
        recolor(pKeyBorder, "#39404A", "#B8C1CC", "#303030")
        onSystemBarAppearanceChanged?.invoke()
    }

    override fun onMeasure(w: Int, h: Int) {
        val baseStripDp = if (hasNumberRow) NUMBER_LABEL_STRIP_DP else PREV_DP
        val extraPreviewPx = if (showTextPreview) {
            (previewStripDp - baseStripDp).coerceAtLeast(0f) * dp
        } else 0f
        val maxMeasureSize = (1 shl 30) - 1
        // Keep the gesture/key area at its configured size when the permanent number row is enabled.
        // The new top strip + digit area is 52dp, replacing the old 26dp strip, so the overall IME
        // window must grow by the net 26dp instead of squeezing the gesture area downwards.
        val numberRowExtraDp = if (hasNumberRow) {
            (NUMBER_LABEL_STRIP_DP + NUMBER_KEYS_AREA_DP - PREV_DP).coerceAtLeast(0f)
        } else 0f
        val keyboardOffsetExtraPx = abs(keyboardVerticalOffsetPx()).toDouble()
        val requestedPx = (heightDp + bottomMarginDp + numberRowExtraDp).toDouble() * dp.toDouble() +
                extraPreviewPx.toDouble() + navigationBarInsetPx().toDouble() + keyboardOffsetExtraPx
        val measured = requestedPx.coerceIn(1.0, maxMeasureSize.toDouble()).toInt()
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(measured, MeasureSpec.EXACTLY))
    }

    // ── Draw ─────────────────────────────────────────────────────────────────

    private fun updateDynamicHintTextSizes() {
        // Direction labels scale with the configured right-circle radius.
        val rightScale = hR / (KeyboardSettings.defaultFor("hint_right") * dp)
        pHLa.textSize = 17f * dp * rightScale
        pHLi.textSize = 16f * dp * rightScale

        // Repeat/delete labels scale with the configured pill radius.
        val pillScale = pillRadius / (KeyboardSettings.defaultFor("hint_pill") * dp)
        pRpT.textSize = 16f * dp * pillScale
        pBsT.textSize = 16f * dp * pillScale
    }

    override fun onDraw(canvas: Canvas) {
        updateDynamicHintTextSizes()
        canvas.save()
        canvas.translate(0f, keyboardCanvasOffsetY())
        try {
            if (symMode) {
                drawSym(canvas)
            } else {
                drawStandardKeyboard(canvas)
            }
        } finally {
            canvas.restore()
        }
        drawNavigationBarBacking(canvas)
    }

    private fun drawStandardKeyboard(canvas: Canvas) {
        val W = width.toFloat(); val H = contentBottom(); val dx = dX()
        // Paint only the keyboard's logical bounds. The additional measured strip is used
        // for moving the whole keyboard and must remain empty instead of being painted as keys.
        canvas.drawRect(0f, 0f, W, H, pBgRd)
        canvas.drawRect(0f,0f,W,prevH,pBgT)
        canvas.drawRect(0f,prevH,dx,H,pBgL)
        canvas.drawRect(dx,prevH,W,H, if (lDown) pBgR else pBgRd)
        drawTextPreview(canvas, numberKeysTopPx)
        if (hasNumberRow) {
            val digitPaint = Paint(pLng).apply {
                textSize = 19f * dp
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            val digitW = W / 10f
            val digitAreaCenterY = (numberKeysTopPx + prevH) / 2f
            val digitBaseline = digitAreaCenterY - (digitPaint.ascent() + digitPaint.descent()) / 2f
            for (i in 1 until 10) {
                val x = digitW * i
                canvas.drawLine(x, numberKeysTopPx, x, prevH - 3f * dp, pGroupDivider)
            }
            "1234567890".forEachIndexed { i, digit ->
                canvas.drawText(digit.toString(), digitW * (i + 0.5f), digitBaseline, digitPaint)
            }
        }
        canvas.drawLine(0f,prevH,W,prevH,pDvH)
        canvas.drawLine(dx,prevH,dx,H,pDvV)
        canvas.save(); canvas.clipRect(0f,prevH,dx,H); drawLeft(canvas,dx); canvas.restore()
        if (lDown && rDown) {
            canvas.save(); canvas.clipRect(dx,prevH,W,H); drawRight(canvas,dx,W); canvas.restore()
        } else if (!lDown) {
            canvas.save(); canvas.clipRect(dx,prevH,W,H); drawActionBar(canvas); canvas.restore()
        }
    }


    /** Keep the transparent system navigation bar painted at the physical bottom of the IME,
     * independently of the user-configured keyboard content offset. */
    private fun drawNavigationBarBacking(canvas: Canvas) {
        val navInset = navigationBarInsetPx()
        if (navInset <= 0f) return
        val W = width.toFloat()
        val dx = dX()
        val navTop = (height.toFloat() - navInset).coerceAtLeast(0f)
        if (symMode) {
            canvas.drawRect(0f, navTop, W, height.toFloat(), pSyB)
        } else {
            canvas.drawRect(0f, navTop, dx, height.toFloat(), pBgL)
            canvas.drawRect(dx, navTop, W, height.toFloat(), if (lDown) pBgR else pBgRd)
        }
    }

    private data class PreviewTextLayout(val lines: List<String>, val caretLine: Int, val caretColumn: Int)

    /** Draws a small, independently configurable view of text around the actual insertion cursor. */
    private fun drawTextPreview(canvas: Canvas, stripHeightPx: Float) {
        val langBaseline = stripHeightPx / 2f - (pLng.ascent() + pLng.descent()) / 2f
        canvas.drawText(keyboardLanguageLabels[lang] ?: lang.uppercase(), 20f * dp, langBaseline, pLng)
        if (!showTextPreview) return

        val ic = inputConnection
        // The numeric setting has no fixed upper limit. Ask for enough context for the
        // number of rows which can physically fit on this display, plus a small buffer.
        val physicalRows = (resources.displayMetrics.heightPixels / dp / 14f).toLong().coerceAtLeast(1L)
        val requestedContextRows = minOf(previewLines.toLong(), physicalRows + 4L)
        val contextChars = (requestedContextRows * 100L).coerceIn(180L, Int.MAX_VALUE.toLong()).toInt()
        val before = try { ic?.getTextBeforeCursor(contextChars, 0)?.toString().orEmpty() } catch (_: Exception) { "" }
        val after = try { ic?.getTextAfterCursor(contextChars, 0)?.toString().orEmpty() } catch (_: Exception) { "" }
        val paint = Paint(pPrv).apply {
            textAlign = Paint.Align.LEFT
            textSize = 12f * dp
        }
        val left = 43f * dp
        val right = (width - 6f * dp).coerceAtLeast(left + 24f * dp)
        val maxWidth = right - left
        // Do not draw a blank space on both sides of the caret when the host editor
        // reports a collapsed cursor between two adjacent spaces. This is only a
        // preview normalization; the actual text in the editor is never changed.
        var previewBefore = before
        var previewAfter = after
        if (previewBefore.endsWith(' ') && previewAfter.startsWith(' ')) {
            previewBefore = previewBefore.dropLast(1)
            previewAfter = previewAfter.drop(1)
        }
        val wrapped = makeTextPreviewLines(previewBefore, previewAfter, maxWidth, paint)
        val lineHeight = 14f * dp
        val rowsThatFit = ((stripHeightPx - 4f * dp) / lineHeight).toInt().coerceAtLeast(1)
        val count = minOf(previewLines, wrapped.lines.size, rowsThatFit).coerceAtLeast(1)
        // For an even number of rows, bias toward the cursor and following text.
        val start = (wrapped.caretLine - (count - 1) / 2).coerceIn(0, (wrapped.lines.size - count).coerceAtLeast(0))
        val visible = wrapped.lines.subList(start, start + count)
        val top = (stripHeightPx - visible.size * lineHeight) / 2f
        val firstBaseline = top + lineHeight / 2f - (paint.ascent() + paint.descent()) / 2f
        val caretPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pPrv.color
            strokeWidth = 1.5f * dp
        }
        canvas.save()
        canvas.clipRect(left, 0f, right, stripHeightPx)
        visible.forEachIndexed { index, line ->
            val baseline = firstBaseline + index * lineHeight
            val caretIsHere = wrapped.caretLine == start + index
            var caretColumn = wrapped.caretColumn.coerceIn(0, line.length)
            val displayLine = line
            val display = displayLine
            if (display.isNotEmpty()) canvas.drawText(display, left, baseline, paint)
            if (caretIsHere) {
                caretColumn = caretColumn.coerceIn(0, displayLine.length)
                val caretX = left + paint.measureText(displayLine.substring(0, caretColumn))
                val glyphCenterY = baseline + (paint.ascent() + paint.descent()) / 2f
                canvas.drawLine(caretX, glyphCenterY - 6f * dp, caretX, glyphCenterY + 6f * dp, caretPaint)
            }
        }
        canvas.restore()
    }

    private fun makeTextPreviewLines(before: String, after: String, maxWidth: Float, paint: Paint): PreviewTextLayout {
        val lines = mutableListOf(StringBuilder())
        var caretLine = 0
        var caretColumn = 0

        fun appendCharacter(character: Char) {
            if (character == '\n') {
                lines += StringBuilder()
                return
            }
            var current = lines.last()
            if (current.isNotEmpty() && paint.measureText(current.toString() + character) > maxWidth) {
                lines += StringBuilder()
                current = lines.last()
            }
            current.append(character)
        }

        before.forEach { appendCharacter(it) }
        // Remember the exact insertion position without adding a fake character to the text.
        caretLine = lines.lastIndex
        caretColumn = lines.last().length
        after.forEach { appendCharacter(it) }
        return PreviewTextLayout(lines.map { it.toString() }, caretLine, caretColumn)
    }

    private fun drawLeft(canvas: Canvas, dx: Float) {
        val cx = shownLeftCX()
        // Boundaries are visible only while a real left pointer is down.
        // Check the pointer ID as well as the drawing flag to avoid stale dividers
        // after ACTION_POINTER_UP / ACTION_CANCEL.
        val leftTouchActive = lDown && leftId != -1
        if (!leftTouchActive) {
            drawCursorButtons(canvas)
            if (!disableLeftHints) {
                canvas.drawText("◎", cx, idlePointY() + pGIn.textSize / 3f, pGIn)
            }
            return
        }
        for (g in 0 until numG() - 1) {
            val dividerY = (gY(g) + gY(g + 1)) * 0.5f
            canvas.drawLine(0f, dividerY, dx, dividerY, pGroupDivider)
        }
        if (disableLeftHints) return
        (0 until numG()).forEach { g ->
            val gy = gY(g); val act = g == grp
            canvas.drawText(if (g==0) "◎" else "$g", cx, gy+(if (act) pGAc else pGIn).textSize/3f, if (act) pGAc else pGIn)
            if (act) {
                // Action pills stay horizontally anchored to the calibrated left
                // center and disappear as soon as delete/repeat is activated.
                // After the left center has been recalibrated, their Y position
                // follows that finger while still keeping both pills on-screen.
                if (!consumed) {
                    val hintY = if (leftHintFollowY) {
                        lastLeftY.coerceIn(prevH + pillRadius, contentBottom() - pillRadius)
                    } else gy
                    val hintCX = cx
                    val pillR = pillRadius
                    val pillOffset = pillOff + pillR + 5f * dp
                    val leftX = (hintCX - pillOffset).coerceIn(pillR + 4f * dp, (dx - pillR - 4f * dp).coerceAtLeast(pillR + 4f * dp))
                    val rightX = (hintCX + pillOffset).coerceIn(pillR + 4f * dp, (dx - pillR - 4f * dp).coerceAtLeast(pillR + 4f * dp))
                    pill(canvas, rightX, hintY, lastLtr.uppercase().let { if (it.isEmpty()) "·" else "↺$it" }, pPBg, pRpT)
                    pill(canvas, leftX, hintY, "⌫", pPBg, pBsT)
                }
            } else { bubble(canvas, dx, gy, g) }
        }
    }

    private fun drawCursorButtons(canvas: Canvas) {
        cursorKeyLayout().forEach { bounds ->
            val active = cursorId != -1 && cursorInside &&
                    cursorStartBounds?.key?.id == bounds.key.id
            val rect = RectF(
                bounds.left + 2f * dp, bounds.top + 2f * dp,
                bounds.right - 2f * dp, bounds.bottom - 2f * dp
            )
            canvas.drawRoundRect(rect, 5f * dp, 5f * dp, if (active) pActionBgActive else pActionBg)
            canvas.drawRoundRect(rect, 5f * dp, 5f * dp, pKeyBorder)
            canvas.drawText(bounds.key.label, bounds.centerX, bounds.centerY + pCursorText.textSize / 3f, pCursorText)
        }
    }

    private fun pill(canvas: Canvas, cx: Float, cy: Float, t: String, bg: Paint, tx: Paint) {
        canvas.drawCircle(cx, cy, pillRadius, bg)
        canvas.drawText(t, cx, cy+tx.textSize/3f, tx)
    }

    private fun bubble(canvas: Canvas, zoneW: Float, cy: Float, g: Int) {
        val ls = letters().getOrNull(g) ?: return
        val scale = hBubble
        val rows = 2
        val cols = ceil(ls.size / rows.toFloat()).toInt().coerceAtLeast(1)
        val pad = 3f * dp * scale
        val maxBubbleWidth = (zoneW - 4f * dp).coerceAtLeast(1f)
        val cw = min(12f * dp * scale, (maxBubbleWidth - pad) / cols)
        val rh = 11f * dp * scale
        val tw = cols * cw + pad
        val th = rows * rh + pad
        val bcx = (zoneW * 0.83f).coerceIn(tw / 2f + 2f * dp, (zoneW - tw / 2f - 2f * dp).coerceAtLeast(tw / 2f + 2f * dp))
        val fs = min(10f * dp * scale, cw * 0.86f)
        val r = RectF(bcx-tw/2f, cy-th/2f, bcx+tw/2f, cy+th/2f)
        canvas.drawRoundRect(r, 4f*dp, 4f*dp, pBbB)
        val tpN = Paint(pBbT).apply { textSize = 8f*dp*scale }
        ls.forEachIndexed { i, l ->
            val lx = r.left+(i%cols)*cw+cw/2f; val ly = r.top+(i/cols)*rh+rh/2f
            when (l) {
                null -> {}
                LK   -> canvas.drawText("🌐", lx, ly+tpN.textSize/3f, tpN)
                SK   -> canvas.drawText("⌨",  lx, ly+tpN.textSize/3f, tpN)
                else -> canvas.drawText(l.uppercase(), lx, ly+fs/3f, Paint(pBbT).apply { textSize = fs })
            }
        }
    }

    private fun drawRight(canvas: Canvas, dx: Float, W: Float) {
        if (disableRightHints) return
        val touchRadius = 15f * dp
        canvas.drawCircle(rCX, rCY, touchRadius, pRDt)

        // Use the configured hint radius for both screen-edge limits and the
        // early collision threshold, so changing the circle size also changes
        // when it moves away from the finger.
        val edgeGap = hR + 4f * dp
        val minX = dx + edgeGap
        val maxX = (W - edgeGap).coerceAtLeast(minX)
        val minY = prevH + edgeGap
        val maxY = (contentBottom() - edgeGap).coerceAtLeast(minY)
        // A little extra clearance makes the sideways move happen earlier.
        val safeDistance = hR + touchRadius + 27f * dp

        var hx = rCX.coerceIn(minX, maxX)
        var hy = (rCY - hUp).coerceIn(minY, maxY)
        val topDistance = hypot((hx - rCX).toDouble(), (hy - rCY).toDouble()).toFloat()

        // If the finger is pressed into both the upper and left boundary, there
        // is no safe upper/left placement. Use the fixed top-right corner instead.
        val noRoomAboveAndLeft = (rCY - minY) < safeDistance && (rCX - minX) < safeDistance
        if (noRoomAboveAndLeft) {
            hx = maxX
            hy = minY
        } else if (topDistance < safeDistance) {
            val sideY = rCY.coerceIn(minY, maxY)
            // Move sideways by approximately the same distance as the normal
            // above-finger offset, instead of barely clearing the finger.
            val horizontalOffset = max(hUp, safeDistance)
            val leftX = (rCX - horizontalOffset).coerceIn(minX, maxX)
            val rightX = (rCX + horizontalOffset).coerceIn(minX, maxX)
            val leftDistance = hypot((leftX - rCX).toDouble(), (sideY - rCY).toDouble()).toFloat()
            val rightDistance = hypot((rightX - rCX).toDouble(), (sideY - rCY).toDouble()).toFloat()
            val leftClear = leftDistance >= safeDistance
            val rightClear = rightDistance >= safeDistance
            val leftRoom = rCX - minX
            val rightRoom = maxX - rCX
            val moveLeft = when {
                leftClear && !rightClear -> true
                rightClear && !leftClear -> false
                leftClear && rightClear -> leftRoom >= rightRoom
                else -> leftDistance >= rightDistance
            }
            hx = if (moveLeft) leftX else rightX
            // If the side is tight, keep the bubble above the finger enough
            // to preserve the clearance; never compensate by moving it below.
            val actualHorizontalDistance = abs(hx - rCX)
            val neededVertical = sqrt(
                (safeDistance * safeDistance - actualHorizontalDistance * actualHorizontalDistance).coerceAtLeast(0f)
            ).toFloat()
            hy = (rCY - neededVertical).coerceIn(minY, sideY)
        }

        canvas.drawCircle(hx,hy,hR,pHBg); canvas.drawCircle(hx,hy,hR,pHBd); canvas.drawCircle(hx,hy,3f*dp,pHDt)
        val ls = letters().getOrNull(grp) ?: return
        dv.forEachIndexed { i, (vx,vy) ->
            val l = ls.getOrNull(i) ?: return@forEachIndexed
            val n = sqrt(vx*vx+vy*vy)
            val d = when(l) { LK -> "🌐"; SK -> "⌨"; else -> l.uppercase() }
            canvas.drawText(d, hx+(vx/n)*hLR, hy+(vy/n)*hLR+pHLa.textSize/3f, if (i==lastDir) pHLa else pHLi)
        }
    }

    // ── Action bar shown when the left thumb is released ───────────────────────

    private fun actionLayout(): List<ActionKeyBounds> {
        val dx = dX()
        val zoneW = (width - dx).coerceAtLeast(1f)
        val availableH = (contentBottom() - prevH).coerceAtLeast(1f)
        // Верхні два ряди з утилітами нижчі; дві пунктуаційні смуги внизу більші.
        val utilityRowH = availableH * 0.18f
        val symbolRowH = (availableH - utilityRowH * 2f) / actionSymbolRows.size.coerceAtLeast(1)
        val utilityKeyW = zoneW / 3f
        val result = ArrayList<ActionKeyBounds>()
        utilityKeys.forEachIndexed { index, key ->
            val row = index / 3
            val col = index % 3
            val left = dx + col * utilityKeyW
            val top = prevH + row * utilityRowH
            result += ActionKeyBounds(key, row, col, left, top, left + utilityKeyW, top + utilityRowH)
        }
        actionSymbolRows.forEachIndexed { rowIndex, row ->
            val yTop = prevH + utilityRowH * 2f + rowIndex * symbolRowH
            var x = dx
            row.forEachIndexed { index, key ->
                val keyW = if (rowIndex == 2) zoneW * (if (key.id == "SPACE") 0.5f else 0.25f) else zoneW / row.size
                result += ActionKeyBounds(key, rowIndex + 2, index, x, yTop, x + keyW, yTop + symbolRowH)
                x += keyW
            }
        }
        return result
    }

    private fun actionKeyAt(x: Float, y: Float): ActionKeyBounds? {
        val layout = actionLayout()
        layout.firstOrNull { it.contains(x, y) }?.let { return it }
        // Slightly forgiving hit areas for fast taps near the gaps between keycaps.
        val slop = 5f * dp
        return layout.asSequence().map { key ->
            val dx = when { x < key.left -> key.left - x; x >= key.right -> x - key.right; else -> 0f }
            val dy = when { y < key.top -> key.top - y; y >= key.bottom -> y - key.bottom; else -> 0f }
            Triple(key, dx * dx + dy * dy, max(dx, dy))
        }.filter { it.third <= slop }.minByOrNull { it.second }?.first
    }

    private fun drawActionBar(canvas: Canvas) {
        val layout = actionLayout()
        val autoCapitalizationReady = KeyboardSettings.readBoolean(prefs, "auto_capitalization") && shouldAutoCapitalize()
        val displayedShiftState = if (shiftState == ShiftState.OFF && autoCapitalizationReady) ShiftState.ONCE else shiftState
        layout.forEach { bounds ->
            val isActive = actionId != -1 && actionCurrent?.key?.id == bounds.key.id && actionCurrent?.row == bounds.row
            val isShift = bounds.key.id == "SHIFT"
            val shiftBg = when {
                isActive -> pActionBgActive
                isShift && displayedShiftState == ShiftState.ONCE -> pActionShiftOnceBg
                isShift && displayedShiftState == ShiftState.CAPS -> pActionShiftCapsBg
                else -> pActionBg
            }
            val rect = RectF(bounds.left + 2f * dp, bounds.top + 2f * dp, bounds.right - 2f * dp, bounds.bottom - 2f * dp)
            canvas.drawRoundRect(rect, 5f * dp, 5f * dp, if (isShift) shiftBg else if (isActive) pActionBgActive else pActionBg)
            canvas.drawRoundRect(rect, 5f * dp, 5f * dp, pKeyBorder)
            if (isShift) {
                drawShiftIcon(canvas, bounds, displayedShiftState)
            } else if (bounds.key.id == "ENTER") {
                // Enter/paragraph icon is intentionally larger than the other utility icons.
                canvas.drawText("↵", bounds.centerX, bounds.centerY + pActionEnterText.textSize * 0.34f, pActionEnterText)
            } else {
                val paint = if (bounds.row <= 1) pActionText else pActionSmallText
                val oldSize = paint.textSize
                val maxWidth = (bounds.right - bounds.left - 6f * dp).coerceAtLeast(1f)
                if (paint.measureText(bounds.key.label) > maxWidth) {
                    paint.textSize = min(paint.textSize, maxWidth / bounds.key.label.length.coerceAtLeast(1))
                }
                canvas.drawText(bounds.key.label, bounds.centerX, bounds.centerY + paint.textSize / 3f, paint)
                paint.textSize = oldSize
            }
        }
        if (actionPopupShown) drawActionPopup(canvas)
    }

    /** OFF = контурна стрілка, ONCE = заповнена, CAPS = заповнена з рискою блокування. */
    private fun drawShiftIcon(canvas: Canvas, bounds: ActionKeyBounds, state: ShiftState) {
        val cx = bounds.centerX
        val cy = bounds.centerY - 1f * dp
        val size = (min(bounds.right - bounds.left, bounds.bottom - bounds.top) * 0.46f - 3f * dp)
            .coerceAtLeast(1f * dp)
        val path = Path().apply {
            moveTo(cx, cy - size * 0.52f)
            lineTo(cx + size * 0.43f, cy - size * 0.04f)
            lineTo(cx + size * 0.20f, cy - size * 0.04f)
            lineTo(cx + size * 0.20f, cy + size * 0.43f)
            lineTo(cx - size * 0.20f, cy + size * 0.43f)
            lineTo(cx - size * 0.20f, cy - size * 0.04f)
            lineTo(cx - size * 0.43f, cy - size * 0.04f)
            close()
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(
                when {
                    isLightKeyboard && state == ShiftState.CAPS -> "#172033"
                    isLightKeyboard -> "#1E293B"
                    state == ShiftState.CAPS -> "#FFFFFF"
                    else -> "#DCE6F2"
                }
            )
            style = if (state == ShiftState.OFF) Paint.Style.STROKE else Paint.Style.FILL
            strokeWidth = 2.2f * dp
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(path, iconPaint)
        if (state == ShiftState.CAPS) {
            val underline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(if (isLightKeyboard) "#172033" else "#FFFFFF")
                strokeWidth = 2.4f * dp
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(cx - size * 0.28f, cy + size * 0.56f, cx + size * 0.28f, cy + size * 0.56f, underline)
        }
    }

    private fun actionPopupRect(): RectF? {
        val start = actionStart ?: return null
        val count = start.key.alternates.size
        if (count == 0) return null
        val cellW = 39f * dp
        val popupH = 43f * dp
        val gap = 5f * dp
        val totalW = count * cellW + gap * 2f
        val zoneLeft = dX() + 3f * dp
        val maxLeft = (width - totalW - 3f * dp).coerceAtLeast(zoneLeft)
        val left = (start.centerX - totalW / 2f).coerceIn(zoneLeft, maxLeft)
        val top = (start.top - popupH - 5f * dp).coerceAtLeast(prevH + 2f * dp)
        return RectF(left, top, left + totalW, top + popupH)
    }

    private fun actionPopupIndexAtX(x: Float): Int? {
        val rect = actionPopupRect() ?: return null
        val count = actionStart?.key?.alternates?.size ?: return null
        if (count == 0) return null
        val cellW = 39f * dp
        val gap = 5f * dp
        val firstCenter = rect.left + gap + cellW / 2f
        return ((x - firstCenter) / cellW).roundToInt().coerceIn(0, count - 1)
    }

    private fun drawActionPopup(canvas: Canvas) {
        val start = actionStart ?: return
        val rect = actionPopupRect() ?: return
        canvas.drawRoundRect(rect, 8f * dp, 8f * dp, pActionPopupBg)
        canvas.drawRoundRect(rect, 8f * dp, 8f * dp, pActionPopupBorder)
        val cellW = 39f * dp
        val gap = 5f * dp
        start.key.alternates.forEachIndexed { index, alt ->
            val left = rect.left + gap + index * cellW
            val cell = RectF(left, rect.top + 3f * dp, left + cellW - dp, rect.bottom - 3f * dp)
            if (index == actionSelectedAlt) canvas.drawRoundRect(cell, 5f * dp, 5f * dp, pActionPopupActive)
            canvas.drawText(alt, cell.centerX(), cell.centerY() + pActionPopupText.textSize / 3f, pActionPopupText)
        }
    }

    private fun beginActionTouch(id: Int, x: Float, y: Float) {
        actionId = id
        actionDownX = x; actionDownY = y; actionTouchX = x; actionTouchY = y
        actionStart = actionKeyAt(x, y)
        actionCurrent = actionStart
        actionPopupShown = false
        actionSelectedAlt = 0
        actionBackspaceRepeating = false
        actionHandler.removeCallbacks(actionLongPressRun)
        actionHandler.removeCallbacks(actionBackspaceHoldRun)
        actionHandler.removeCallbacks(actionBackspaceRepeatRun)
        if (actionStart?.key?.id == "BACKSPACE") {
            actionHandler.postDelayed(actionBackspaceHoldRun, BS_HOLD_MS)
        } else if (actionStart?.key?.alternates?.isNotEmpty() == true) {
            actionHandler.postDelayed(actionLongPressRun, 420L)
        }
        invalidate()
    }

    private fun moveActionTouch(ev: MotionEvent) {
        if (actionId == -1) return
        val idx = ev.findPointerIndex(actionId)
        if (idx == -1) return
        val x = ev.getX(idx); val y = ev.getY(idx)
        actionTouchX = x; actionTouchY = y
        if (actionPopupShown) {
            actionPopupIndexAtX(x)?.let { actionSelectedAlt = it }
        } else {
            actionCurrent = actionKeyAt(x, y)
            if (hypot((x - actionDownX).toDouble(), (y - actionDownY).toDouble()) > (9f * dp).toDouble()) {
                actionHandler.removeCallbacks(actionLongPressRun)
            }
            if (actionCurrent?.key != actionStart?.key) actionHandler.removeCallbacks(actionLongPressRun)
            if (actionStart?.key?.id == "BACKSPACE" && actionCurrent?.key?.id != "BACKSPACE") {
                actionHandler.removeCallbacks(actionBackspaceHoldRun)
                actionHandler.removeCallbacks(actionBackspaceRepeatRun)
            }
        }
        invalidate()
    }

    private fun finishActionTouch(id: Int, x: Float, y: Float) {
        if (id != actionId) return
        actionHandler.removeCallbacks(actionLongPressRun)
        actionHandler.removeCallbacks(actionBackspaceHoldRun)
        actionHandler.removeCallbacks(actionBackspaceRepeatRun)
        if (actionBackspaceRepeating) {
            // Deletions already happened while held; don't add another deletion on release.
        } else if (actionPopupShown) {
            actionPopupIndexAtX(x)?.let { actionSelectedAlt = it }
            actionStart?.key?.alternates?.getOrNull(actionSelectedAlt)?.let { commitInputText(it) }
        } else {
            val releasedOnKey = actionKeyAt(x, y)
            if (releasedOnKey != null) {
                performAction(releasedOnKey.key)
            } else {
                val drift = hypot((x - actionDownX).toDouble(), (y - actionDownY).toDouble())
                if (drift <= (12f * dp).toDouble()) actionStart?.let { performAction(it.key) }
            }
        }
        clearActionTracking()
        invalidate()
    }

    private fun clearActionTracking() {
        actionHandler.removeCallbacks(actionLongPressRun)
        actionHandler.removeCallbacks(actionBackspaceHoldRun)
        actionHandler.removeCallbacks(actionBackspaceRepeatRun)
        actionBackspaceRepeating = false
        actionId = -1
        actionStart = null; actionCurrent = null
        actionPopupShown = false; actionSelectedAlt = 0
    }

    private fun performAction(key: ActionKey) {
        when (key.id) {
            "LANG" -> {
                langIdx = (langIdx + 1) % langOrder.size
                grp = grp.coerceIn(0, numG() - 1)
                lastLtr = ""
            }
            "SYM" -> {
                currentSymbolEmoticon = symbolEmoticons.random()
                symMode = true
                onSystemBarAppearanceChanged?.invoke()
                symPage = 0
                clearSymTracking()
                resetGesturePointers()
            }
            "SHIFT" -> {
                val now = android.os.SystemClock.uptimeMillis()
                shiftState = when {
                    shiftState == ShiftState.ONCE && now - lastShiftTap < 400L -> ShiftState.CAPS
                    shiftState == ShiftState.OFF -> ShiftState.ONCE
                    else -> ShiftState.OFF
                }
                lastShiftTap = now
            }
            "REPEAT" -> repeatLastWord()
            "ENTER" -> sendEnterAction()
            "BACKSPACE" -> deleteBackwardOrSelection()
            "SPACE" -> commitInputText(" ")
            "SETTINGS" -> {
                runCatching {
                    context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            else -> if (key.id.startsWith("CHAR:")) commitQuickSymbol(key.label)
        }
        invalidate()
    }


    private fun commitQuickSymbol(symbol: String) {
        val ic = inputConnection ?: return
        if (KeyboardSettings.readBoolean(prefs, "delete_space_on_quick_symbol")) {
            val before = try { ic.getTextBeforeCursor(1, 0)?.toString() } catch (_: Exception) { null }
            if (before == " ") deleteSurroundingWithHaptic(ic, 1, 0)
        }
        val output = if (symbol.length == 1 && symbol[0].isLetter() &&
            KeyboardSettings.readBoolean(prefs, "auto_capitalization") && shouldAutoCapitalize()) symbol.uppercase() else symbol
        commitTextWithHaptic(ic, output, 1)
        if (KeyboardSettings.readBoolean(prefs, "space_after_quick_symbol")) commitTextWithHaptic(ic, " ", 1)
    }

    private fun shouldAutoCapitalize(): Boolean {
        val before = try { inputConnection?.getTextBeforeCursor(3, 0)?.toString().orEmpty() } catch (_: Exception) { "" }
        if (before.isEmpty()) return true
        val trimmed = before.trimEnd()
        return trimmed.isEmpty() || trimmed.last() in ".!?\n"
    }

    private fun repeatLastWord() {
        val before = try { inputConnection?.getTextBeforeCursor(200, 0)?.toString() } catch (_: Exception) { null } ?: return
        if (before.isEmpty()) return
        // Do not manufacture a leading space when the character immediately before
        // the cursor is not whitespace. Add a trailing space only when it was.
        val cursorWasAfterWhitespace = before.lastOrNull()?.isWhitespace() == true
        val base = before.trimEnd()
        val wordStart = base.indexOfLast { it.isWhitespace() } + 1
        val word = base.substring(wordStart)
        if (word.isEmpty()) return
        commitInputText(if (cursorWasAfterWhitespace) "$word " else word)
    }

    // ── Symbol keyboard ───────────────────────────────────────────────────────

    private enum class SymAction { INPUT, BACKSPACE, ABC, PAGE, SPACE, ENTER }
    private data class SymKey(
        val label: String,
        val value: String = label,
        val weight: Float = 1f,
        val action: SymAction = SymAction.INPUT,
        val alternates: List<String> = emptyList()
    )
    private data class SymKeyBounds(
        val key: SymKey,
        val rowIndex: Int,
        val keyIndex: Int,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
        val centerX get() = (left + right) / 2f
        val centerY get() = (top + bottom) / 2f
    }

    private fun symbol(label: String, vararg alternates: String) =
        SymKey(label, alternates = alternates.toList())

    // Розкладка з двох сторінок, орієнтована на Gboard: символи з прикладу
    // винесені на основні клавіші, додаткові варіанти відкриваються утриманням.
    private val symPages: List<List<List<SymKey>>> = listOf(
        listOf(
            listOf("1","2","3","4","5","6","7","8","9","0").mapIndexed { i, s ->
                symbol(s, listOf("¹","²","³","⁴","⁵","⁶","⁷","⁸","⁹","⁰")[i],
                    listOf("₁","₂","₃","₄","₅","₆","₇","₈","₉","₀")[i])
            },
            listOf(
                symbol("@", "©", "®"), symbol("#", "№"), symbol("₽", "\$", "€", "£", "¥", "¢"),
                symbol("_", "—", "–"), symbol("&", "§"), symbol("-", "−", "–", "—"),
                symbol("+", "±", "∓"), symbol("(", "[", "{"), symbol(")", "]", "}")
            ),
            listOf(
                SymKey("=\\<", weight = 1.065f, action = SymAction.PAGE),
                SymKey("*", weight = 0.9f, alternates = listOf("•", "×")),
                SymKey("\"", weight = 0.9f, alternates = listOf("“", "”", "„", "‟")),
                SymKey("'", weight = 0.9f, alternates = listOf("‘", "’", "ʼ")),
                SymKey(":", weight = 0.9f, alternates = listOf("÷", "∶")),
                SymKey(";", weight = 0.9f, alternates = listOf("…", "⋅")),
                SymKey("!", weight = 0.9f, alternates = listOf("¡")),
                SymKey("?", weight = 0.9f, alternates = listOf("¿")),
                SymKey("⌫", weight = 1.0125f, action = SymAction.BACKSPACE)
            )
        ),
        listOf(
            listOf(
                symbol("~", "≈", "≃"), symbol("`", "´", "ˋ"), symbol("|", "¦"), symbol("•", "·", "∙"),
                symbol("√", "∛", "∜"), symbol("π", "Π", "τ"), symbol("÷", "⁄"), symbol("×", "✕"),
                symbol("§", "¶"), symbol("Δ", "δ", "∆")
            ),
            listOf(
                symbol("£", "€", "\$", "¥", "₽"), symbol("€", "£", "\$", "¥", "₽"),
                symbol("\$", "€", "£", "¥", "₽"), symbol("¢", "₵", "₡"), symbol("^", "ˆ", "ˇ"),
                symbol("°", "º", "˚"), symbol("=", "≠", "≈"), symbol("{", "(", "["),
                symbol("}", ")", "]"), symbol("\\", "|", "¦")
            ),
            listOf(
                SymKey("?123", weight = 1.065f, action = SymAction.PAGE),
                SymKey("%", weight = 0.78f, alternates = listOf("‰", "‱")),
                SymKey("©", weight = 0.78f, alternates = listOf("ⓒ")),
                SymKey("®", weight = 0.78f, alternates = listOf("ⓡ")),
                SymKey("™", weight = 0.78f, alternates = listOf("℠")),
                SymKey("✓", weight = 0.78f, alternates = listOf("✔", "☑")),
                SymKey("[", weight = 0.92f, alternates = listOf("(", "{")),
                SymKey("]", weight = 0.92f, alternates = listOf(")", "}")),
                SymKey("⌫", weight = 1.0125f, action = SymAction.BACKSPACE)
            )
        )
    )

    private fun bottomSymKeys() = listOf(
        SymKey(context.getString(R.string.keyboard_abc), weight = 1f, action = SymAction.ABC),
        if (symPage == 0) {
            SymKey(",", weight = 1f, alternates = listOf("،", "‚"))
        } else {
            SymKey("<", weight = 1f, alternates = listOf("≤", "≪"))
        },
        SymKey(currentSymbolEmoticon, value = " ", weight = 2f, action = SymAction.SPACE),
        if (symPage == 0) {
            SymKey(".", weight = 1f, alternates = listOf("…", "·"))
        } else {
            SymKey(">", weight = 1f, alternates = listOf("≥", "≫"))
        },
        SymKey("↵", value = "\n", weight = 1f, action = SymAction.ENTER)
    )

    private fun symRowsForPage() = symPages[symPage.coerceIn(0, symPages.lastIndex)]

    private fun symLayout(): List<SymKeyBounds> {
        val w = width.toFloat(); val h = contentBottom()
        if (w <= 0f || h <= 0f) return emptyList()
        val top = previewStripDp * dp + 5f * dp
        // No hard-coded bottom padding: the user's bottom_margin_dp is the only gap.
        val bottom = 0f
        val rowGap = 3f * dp
        val rowH = (h - top - bottom - rowGap * 3f) / 4f
        val rows = symRowsForPage() + listOf(bottomSymKeys())
        val result = ArrayList<SymKeyBounds>()
        rows.forEachIndexed { rowIndex, row ->
            val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat()
            var x = 0f
            row.forEachIndexed { keyIndex, key ->
                val keyW = w * key.weight / totalWeight
                result += SymKeyBounds(
                    key, rowIndex, keyIndex,
                    x, top + rowIndex * (rowH + rowGap),
                    x + keyW, top + rowIndex * (rowH + rowGap) + rowH
                )
                x += keyW
            }
        }
        return result
    }

    private fun symKeyAt(x: Float, y: Float): SymKeyBounds? {
        val keys = symLayout()
        keys.firstOrNull { it.contains(x, y) }?.let { return it }

        // Real fingers wobble across the narrow gaps between rows. Treat a small
        // area around a key as part of that key so quick taps aren't lost.
        val slop = symTouchSlop
        return keys.asSequence().map { key ->
            val dx = when {
                x < key.left -> key.left - x
                x >= key.right -> x - key.right
                else -> 0f
            }
            val dy = when {
                y < key.top -> key.top - y
                y >= key.bottom -> y - key.bottom
                else -> 0f
            }
            Triple(key, dx * dx + dy * dy, max(dx, dy))
        }.filter { it.third <= slop }
            .minByOrNull { it.second }
            ?.first
    }

    private fun symPopupRect(): RectF? {
        val bounds = symStartBounds ?: return null
        val count = bounds.key.alternates.size
        if (count == 0) return null
        val cellW = 38f * dp
        val popupH = 43f * dp
        val gap = 5f * dp
        val totalW = count * cellW + gap * 2f
        val left = (bounds.centerX - totalW / 2f).coerceIn(3f * dp, (width - totalW - 3f * dp).coerceAtLeast(3f * dp))
        val top = if (bounds.top >= popupH + 9f * dp) {
            bounds.top - popupH - 5f * dp
        } else {
            (bounds.bottom + 5f * dp).coerceAtMost((contentBottom() - popupH - 3f * dp).coerceAtLeast(3f * dp))
        }
        return RectF(left, top, left + totalW, top + popupH)
    }

    private fun symPopupIndexAtFingerX(x: Float): Int? {
        val rect = symPopupRect() ?: return null
        val count = symStartBounds?.key?.alternates?.size ?: return null
        if (count == 0) return null
        val cellW = 38f * dp
        val gap = 5f * dp
        // The popup is a visual guide above the finger. Like Gboard, the finger
        // stays below it; only horizontal position selects a popup item.
        val firstCenter = rect.left + gap + cellW / 2f
        return ((x - firstCenter) / cellW).roundToInt().coerceIn(0, count - 1)
    }

    private fun drawSym(canvas: Canvas) {
        // The symbol keyboard and its optional bottom margin share one surface colour.
        canvas.drawRect(0f, 0f, width.toFloat(), contentBottom(), pSyB)
        drawTextPreview(canvas, previewStripDp * dp)
        symLayout().forEach { b ->
            val isCurrent = symDownId != -1 &&
                    symCurrentBounds?.rowIndex == b.rowIndex && symCurrentBounds?.keyIndex == b.keyIndex
            val special = b.key.action != SymAction.INPUT
            val bg = when {
                isCurrent -> pSPressed
                special -> pSBAl
                else -> pSBtn
            }
            val keyRect = RectF(b.left + 2f * dp, b.top + 2f * dp, b.right - 2f * dp, b.bottom - 2f * dp)
            canvas.drawRoundRect(keyRect, 6f * dp, 6f * dp, bg)
            canvas.drawRoundRect(keyRect, 6f * dp, 6f * dp, pKeyBorder)
            val tx = when {
                b.key.action == SymAction.SPACE -> pSLang
                b.key.label.length >= 4 || special -> pSTxS
                else -> pSTx
            }
            val maxWidth = (b.right - b.left - 8f * dp).coerceAtLeast(1f)
            var size = tx.textSize
            while (tx.measureText(b.key.label) > maxWidth && size > 9f * dp) {
                size -= 1f * dp
                tx.textSize = size
            }
            canvas.drawText(b.key.label, b.centerX, b.centerY + tx.textSize / 3f, tx)
            tx.textSize = when {
                b.key.action == SymAction.SPACE -> 13.5f * dp
                b.key.action != SymAction.INPUT || b.key.label.length >= 4 -> 16f * dp
                else -> 23f * dp
            }
        }

        if (symDownId != -1) {
            if (symLongPressed) drawSymAltPopup(canvas)
            drawSymPreview(canvas)
        }
    }

    private fun drawSymAltPopup(canvas: Canvas) {
        val bounds = symStartBounds ?: return
        val alternatives = bounds.key.alternates
        val rect = symPopupRect() ?: return
        canvas.drawRoundRect(rect, 8f * dp, 8f * dp, pSPopupBg)
        canvas.drawRoundRect(rect, 8f * dp, 8f * dp, pSPopupBorder)
        val cellW = 38f * dp
        val gap = 5f * dp
        alternatives.forEachIndexed { index, alt ->
            val left = rect.left + gap + index * cellW
            val cell = RectF(left, rect.top + 3f * dp, left + cellW - 1f * dp, rect.bottom - 3f * dp)
            if (index == symSelectedAltIndex) canvas.drawRoundRect(cell, 5f * dp, 5f * dp, pSPopupSelected)
            canvas.drawText(alt, cell.centerX(), cell.centerY() + pSPopupText.textSize / 3f, pSPopupText)
        }
    }

    private fun drawSymPreview(canvas: Canvas) {
        val bounds = symCurrentBounds ?: return
        var label = when (bounds.key.action) {
            SymAction.BACKSPACE -> "⌫"
            SymAction.ABC -> context.getString(R.string.keyboard_abc)
            SymAction.PAGE -> bounds.key.label
            SymAction.SPACE -> "⎵"
            SymAction.ENTER -> "↵"
            SymAction.INPUT -> bounds.key.label
        }
        if (symLongPressed && symStartBounds?.key == bounds.key && bounds.key.action == SymAction.INPUT) {
            label = bounds.key.alternates.getOrNull(symSelectedAltIndex) ?: label
        }
        val padX = 15f * dp
        val previewW = (pSPreviewText.measureText(label) + padX * 2f).coerceIn(48f * dp, 86f * dp)
        val previewH = 49f * dp
        val centerX = symTouchX.coerceIn(previewW / 2f + 2f * dp, width - previewW / 2f - 2f * dp)
        val minTop = 2f * dp
        val maxTop = (contentBottom() - previewH - 2f * dp).coerceAtLeast(minTop)
        var top = if (symLongPressed) {
            // Keep the selected-character preview out of the popup strip so the
            // alternatives stay visible as a guide above the finger.
            val popup = symPopupRect()
            val wantedTop = if (popup != null && symTouchY <= popup.bottom) {
                popup.bottom + 5f * dp
            } else {
                symTouchY + 12f * dp
            }
            wantedTop.coerceAtMost(maxTop)
        } else {
            var ordinaryTop = symTouchY - previewH - 18f * dp
            if (ordinaryTop < minTop) ordinaryTop = symTouchY + 18f * dp
            ordinaryTop.coerceIn(minTop, maxTop)
        }
        top = top.coerceIn(minTop, maxTop)
        val rect = RectF(centerX - previewW / 2f, top, centerX + previewW / 2f, top + previewH)
        canvas.drawRoundRect(rect, 8f * dp, 8f * dp, pSPreviewBg)
        canvas.drawRoundRect(rect, 8f * dp, 8f * dp, pSPreviewBorder)
        canvas.drawText(label, rect.centerX(), rect.centerY() + pSPreviewText.textSize / 3f, pSPreviewText)
    }

    private fun clearSymTracking() {
        symHandler.removeCallbacks(symLongPressRun)
        stopSymBackspaceRepeat()
        symDownId = -1
        symStartBounds = null
        symCurrentBounds = null
        symLongPressed = false
        symSelectedAltIndex = 0
    }

    private fun beginSymTracking(id: Int, x: Float, y: Float) {
        symHandler.removeCallbacks(symLongPressRun)
        symDownId = id
        symDownX = x; symDownY = y
        symTouchX = x; symTouchY = y
        symStartBounds = symKeyAt(x, y)
        symCurrentBounds = symStartBounds
        symLongPressed = false
        symSelectedAltIndex = 0
        when {
            symStartBounds?.key?.action == SymAction.BACKSPACE ->
                symHandler.postDelayed(symBackspaceHoldRun, BS_HOLD_MS)
            symStartBounds?.key?.action == SymAction.INPUT &&
                    symStartBounds?.key?.alternates?.isNotEmpty() == true ->
                symHandler.postDelayed(symLongPressRun, symLongPressMs)
        }
        invalidate()
    }

    private fun moveSymTracking(id: Int, x: Float, y: Float) {
        if (id != symDownId) return
        symTouchX = x; symTouchY = y
        val current = symKeyAt(x, y)
        symCurrentBounds = current

        if (!symLongPressed && current?.key != symStartBounds?.key) {
            // Leaving the original key cancels alternate-popup long press.
            symHandler.removeCallbacks(symLongPressRun)
        }

        if (symStartBounds?.key?.action == SymAction.BACKSPACE &&
            current?.key?.action != SymAction.BACKSPACE) {
            stopSymBackspaceRepeat()
        }

        if (symLongPressed) {
            // Select alternatives by X while keeping the finger on/below the
            // original key row. No need to move up onto the visible popup.
            symPopupIndexAtFingerX(x)?.let { symSelectedAltIndex = it }
            // Keep the preview anchored to the key that opened the popup.
            symCurrentBounds = symStartBounds
        }
        invalidate()
    }

    private fun handleSym(ev: MotionEvent) {
        // Після входу в символьний режим старий жест двома пальцями не повинен
        // натискати ABC або іншу клавішу; дочікуємося відпускання всіх пальців.
        if (symIgnoreUntilAllUp) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                symIgnoreUntilAllUp = false
                clearSymTracking()
                resetGesturePointers()
                invalidate()
            }
            return
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val idx = ev.actionIndex
                beginSymTracking(ev.getPointerId(idx), ev.getX(idx), ev.getY(idx))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Символьна клавіатура обробляє один палець; другий скасовує жест,
                // щоб не було випадкового введення при переході між режимами.
                clearSymTracking()
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (symDownId != -1) {
                    val idx = ev.findPointerIndex(symDownId)
                    if (idx == -1) clearSymTracking()
                    else moveSymTracking(symDownId, ev.getX(idx), ev.getY(idx))
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val id = ev.getPointerId(ev.actionIndex)
                if (id == symDownId) clearSymTracking()
            }
            MotionEvent.ACTION_UP -> {
                val idx = ev.actionIndex
                val id = ev.getPointerId(idx)
                if (id == symDownId) {
                    val x = ev.getX(idx); val y = ev.getY(idx)
                    symTouchX = x; symTouchY = y
                    val hit = symKeyAt(x, y) ?: symCurrentBounds ?: symStartBounds
                    if (symLongPressed) {
                        symPopupIndexAtFingerX(x)?.let { symSelectedAltIndex = it }
                        val startKey = symStartBounds?.key
                        val alt = startKey?.alternates?.getOrNull(symSelectedAltIndex)
                        if (alt != null) commitInputText(alt)
                        else hit?.let { performSymKey(it.key) }
                    } else {
                        // Keep quick taps reliable even when the release lands in
                        // a tiny inter-row gap. Swipes still commit the release key.
                        val startedOnBackspace = symStartBounds?.key?.action == SymAction.BACKSPACE
                        if (startedOnBackspace && symBackspaceRepeating) {
                            // Repeat already deleted while held; don't delete once more.
                        } else if (startedOnBackspace && hit?.key?.action == SymAction.BACKSPACE) {
                            // Tap deletes once on release; a long hold is handled by the repeat timer.
                            performSymKey(hit.key)
                        } else {
                            hit?.let { performSymKey(it.key) }
                        }
                    }
                }
                clearSymTracking()
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                clearSymTracking()
                invalidate()
            }
        }
    }

    private fun performSymKey(key: SymKey) {
        when (key.action) {
            SymAction.INPUT -> commitInputText(key.value)
            SymAction.BACKSPACE -> deleteBackwardOrSelection()
            SymAction.ABC -> {
                clearSymTracking()
                symMode = false
                onSystemBarAppearanceChanged?.invoke()
                resetGesturePointers()
            }
            SymAction.PAGE -> {
                symPage = (symPage + 1) % symPages.size
                clearSymTracking()
            }
            SymAction.SPACE -> commitInputText(" ")
            SymAction.ENTER -> sendEnterAction()
        }
        invalidate()
    }

    private fun sendCursorKey(keyCode: Int) {
        val ic = inputConnection ?: return
        try {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (_: Exception) { }
    }

    private fun beginCursorTouch(id: Int, x: Float, y: Float) {
        val hit = cursorKeyAt(x, y) ?: return
        cursorId = id
        cursorStartBounds = hit
        cursorInside = true
        sendCursorKey(hit.key.keyCode)
        cursorHandler.removeCallbacks(cursorHoldRun)
        cursorHandler.removeCallbacks(cursorRepeatRun)
        cursorHandler.postDelayed(cursorHoldRun, BS_HOLD_MS)
        invalidate()
    }

    private fun moveCursorTouch(ev: MotionEvent) {
        if (cursorId == -1) return
        val idx = ev.findPointerIndex(cursorId)
        if (idx == -1) {
            clearCursorTracking()
            invalidate()
            return
        }
        val hit = cursorKeyAt(ev.getX(idx), ev.getY(idx))
        val wasInside = cursorInside
        cursorInside = hit?.key?.id == cursorStartBounds?.key?.id
        if (!cursorInside) {
            cursorHandler.removeCallbacks(cursorHoldRun)
            cursorHandler.removeCallbacks(cursorRepeatRun)
        } else if (!wasInside) {
            cursorHandler.removeCallbacks(cursorHoldRun)
            cursorHandler.removeCallbacks(cursorRepeatRun)
            cursorHandler.postDelayed(cursorHoldRun, BS_HOLD_MS)
        }
        invalidate()
    }

    private fun stopCursorRepeat() {
        cursorHandler.removeCallbacks(cursorHoldRun)
        cursorHandler.removeCallbacks(cursorRepeatRun)
    }

    private fun clearCursorTracking() {
        stopCursorRepeat()
        cursorId = -1
        cursorStartBounds = null
        cursorInside = false
    }

    // ── Touch ────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Input coordinates arrive in the expanded view's space. The keyboard itself is
        // translated downward only for negative offsets, so map touch Y back to key space.
        val ev = MotionEvent.obtain(event)
        ev.offsetLocation(0f, -keyboardCanvasOffsetY())
        return try {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN || ev.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                val idx = ev.actionIndex
                if (ev.getY(idx) < 0f || ev.getY(idx) >= contentBottom()) return true
            }
            if (symMode) { handleSym(ev); return true }
            val dx = dX()
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val idx = ev.actionIndex
                    val id = ev.getPointerId(idx)
                    val x = ev.getX(idx); val y = ev.getY(idx)
                    if (ev.actionMasked == MotionEvent.ACTION_POINTER_DOWN && actionId != -1) {
                        clearActionTracking()
                    }
                    when {
                        hasNumberRow && y >= numberKeysTopPx && y < prevH -> {
                            val digit = "1234567890"[(x / (width / 10f)).toInt().coerceIn(0, 9)]
                            commitInputText(digit.toString())
                        }
                        x < dx && leftId == -1 && cursorId == -1 && isInIdleGestureZone(y) -> {
                            leftId = id; lDown = true
                            leftStillSince = ev.eventTime; lastLeftX = x; lastLeftY = y
                            leftHintFollowY = false
                            // Keep dynamic zero calibration at pickup and group transitions only.
                            leftCX = clampLeftCX(x)
                            consumed = false; inBsZone = false; stopBackspaceRepeat()
                            grp = nearestG(y)
                            lastDir = -1
                            invalidate()
                        }
                        x < dx && leftId == -1 && cursorId == -1 && cursorKeyAt(x, y) != null -> {
                            beginCursorTouch(id, x, y)
                        }
                        x >= dx && rightId == -1 && lDown -> {
                            rightId = id; rDown = true
                            rLX = x; rLY = y; rCX = x; rCY = y; lastDir = -1
                            resetVelocityWindow(x, y, ev.eventTime)
                            invalidate()
                        }
                        x >= dx && !lDown && rightId == -1 && actionId == -1 -> beginActionTouch(id, x, y)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    moveL(ev)
                    moveR(ev)
                    moveActionTouch(ev)
                    moveCursorTouch(ev)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    val idx = ev.actionIndex
                    val id = ev.getPointerId(idx)
                    val x = ev.getX(idx); val y = ev.getY(idx)
                    when (id) {
                        leftId -> {
                            leftId = -1; lDown = false; consumed = false; inBsZone = false; inRepeatZone = false
                            leftHintFollowY = false
                            stopBackspaceRepeat(); invalidate()
                        }
                        rightId -> {
                            // A tap without a selected direction is an explicit space.
                            // Do not inspect text after the cursor: it may be the next paragraph,
                            // and must not suppress a deliberate space insertion.
                            // A stationary right-finger tap is a space; a directional swipe
                            // types its letter during moveR(), then adds the expected trailing space.
                            commitInputText(" ")
                            rightId = -1; rDown = false; lastDir = -1
                            velocitySamples.clear()
                            rLastEventTime = 0L; rTD = 0f; candidateDir = -1
                            invalidate()
                        }
                        actionId -> finishActionTouch(id, x, y)
                        cursorId -> { clearCursorTracking(); invalidate() }
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    leftId = -1; lDown = false; rightId = -1; rDown = false
                    consumed = false; inBsZone = false; inRepeatZone = false; lastDir = -1
                    leftHintFollowY = false
                    velocitySamples.clear(); rLastEventTime = 0L; rTD = 0f; candidateDir = -1
                    stopBackspaceRepeat(); clearActionTracking(); clearCursorTracking(); invalidate()
                }
            }
            return true
        } finally {
            ev.recycle()
        }
    }

    private fun moveL(ev: MotionEvent) {
        if (leftId == -1) return
        val idx = ev.findPointerIndex(leftId); if (idx == -1) return
        val x = ev.getX(idx); val y = ev.getY(idx)
        val now = ev.eventTime
        if (leftStillSince == 0L) { leftStillSince = now; lastLeftX = x; lastLeftY = y }
        val tinyMove = hypot((x - lastLeftX).toDouble(), (y - lastLeftY).toDouble()).toFloat() < 2.5f * dp
        if (!tinyMove) { leftStillSince = now; lastLeftX = x; lastLeftY = y }
        else if (now - leftStillSince >= 450L && !inBsZone && !holdBs && !consumed) {
            // Re-center after a stable pause/tiny drift, but never while the left
            // finger is actively deleting or repeating a character.
            leftCX = clampLeftCX(x)
            lastLeftX = x; lastLeftY = y; leftStillSince = now
            leftHintFollowY = true
        }
        val ng = nearestG(y)
        if (ng != grp && !consumed) {
            grp = ng
            microVibrate()
            leftHintFollowY = false
            // Dynamic zero calibration happens at the group transition only.
            leftCX = clampLeftCX(x)
            consumed=false; inBsZone=false; inRepeatZone=false
            stopBackspaceRepeat()
            lastDir = -1
            if (rightId != -1) {
                val rightIndex = ev.findPointerIndex(rightId)
                if (rightIndex != -1) resetVelocityWindow(ev.getX(rightIndex), ev.getY(rightIndex), ev.eventTime)
            }
            invalidate(); return
        }

        val ddx = x - leftCX

        // Автовидалення негайно зупиняється, щойно палець залишає зону ⌫.
        // consumed при цьому лишається активним до повернення в центр, аби не було повторного тригера.
        if (inBsZone && ddx >= -sideR) {
            inBsZone = false
            stopBackspaceRepeat()
        }
        if (inRepeatZone && ddx <= sideR) {
            inRepeatZone = false
            holdRepeatSymbol = false
            bsH.removeCallbacks(repeatSymbolRun)
            bsH.removeCallbacks(repeatSymbolHoldRun)
        }

        if (consumed) {
            if (abs(ddx) < cenR) {
                consumed=false; inBsZone=false; inRepeatZone=false
                stopBackspaceRepeat()
            }
            // Repaint while the pointer moves so the active delete/repeat hint
            // follows it even when the gesture is outside the visual row center.
            invalidate()
            return
        }

        when {
            ddx < -sideR -> if (!inBsZone) {
                inBsZone=true; consumed=true
                deleteBackwardOrSelection()
                bsH.postDelayed(bsHoldRun, BS_HOLD_MS)
                invalidate()
            }
            ddx > sideR -> {
                if (lastLtr.isNotEmpty()) commitInputText(lastLtr)
                consumed=true
                inRepeatZone = true
                if (KeyboardSettings.readBoolean(prefs, "sticky_repeat_symbol") && lastLtr.isNotEmpty()) {
                    bsH.postDelayed(repeatSymbolHoldRun, BS_HOLD_MS)
                }
                invalidate()
            }
        }
    }

    private fun sendEnterAction() {
        val connection = inputConnection ?: return
        val inputType = editorInfo?.inputType ?: 0
        if ((inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) {
            commitInputText("\n")
            return
        }
        val action = (editorInfo?.imeOptions ?: EditorInfo.IME_ACTION_UNSPECIFIED) and EditorInfo.IME_MASK_ACTION
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            val handled = runCatching { connection.performEditorAction(action) }.getOrDefault(false)
            if (handled) return
        }
        commitInputText("\n")
    }

    private fun commitInputText(text: CharSequence, newCursorPosition: Int = 1) {
        val ic = inputConnection ?: return
        commitTextWithHaptic(ic, text, newCursorPosition)
    }

    private fun commitTextWithHaptic(ic: InputConnection, text: CharSequence, newCursorPosition: Int = 1) {
        if (text.isNotEmpty() && text.toString() != " ") microVibrate()
        ic.commitText(text, newCursorPosition)
        if (showTextPreview) post { onEditorSelectionChanged() }
    }

    private fun deleteSurroundingWithHaptic(ic: InputConnection, beforeLength: Int, afterLength: Int) {
        microVibrate()
        ic.deleteSurroundingText(beforeLength, afterLength)
        if (showTextPreview) post { onEditorSelectionChanged() }
    }

    private fun microVibrate() {
        val microEnabled = KeyboardSettings.readBoolean(prefs, "micro_vibration")
        val normalEnabled = KeyboardSettings.readBoolean(prefs, "normal_vibration")
        if (!microEnabled && !normalEnabled) return

        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) {
                val amplitudeControl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()
                when {
                    microEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        // Amplitude is reduced when supported. Without amplitude control,
                        // use a very short default-amplitude pulse rather than doing nothing.
                        val amplitude = if (amplitudeControl) 22 else VibrationEffect.DEFAULT_AMPLITUDE
                        vibrator.vibrate(VibrationEffect.createOneShot(12L, amplitude))
                    }
                    normalEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        // Keep this pulse short; actual strength is device/firmware dependent
                        // if amplitude control is unavailable.
                        val amplitude = if (amplitudeControl) 40 else VibrationEffect.DEFAULT_AMPLITUDE
                        vibrator.vibrate(VibrationEffect.createOneShot(if (amplitudeControl) 16L else 18L, amplitude))
                    }
                    microEnabled -> {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(12L)
                    }
                    normalEnabled -> {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(18L)
                    }
                }
            } else if (microEnabled || normalEnabled) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        } catch (_: Exception) {
            // Haptics must never prevent text input from working.
            if (microEnabled || normalEnabled) {
                runCatching { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
            }
        }
    }

    private fun deleteBackwardOrSelection() {
        val ic = inputConnection ?: return
        val selected = try { ic.getSelectedText(0)?.toString() } catch (_: Exception) { null }
        if (!selected.isNullOrEmpty()) {
            microVibrate()
            ic.commitText("", 1)
            if (showTextPreview) post { onEditorSelectionChanged() }
        } else {
            val before = try { ic.getTextBeforeCursor(1, 0)?.toString().orEmpty() } catch (_: Exception) { "" }
            if (before.isNotEmpty()) deleteSurroundingWithHaptic(ic, 1, 0)
        }
    }

    private fun applyShift(letter: String): String {
        val autoCap = KeyboardSettings.readBoolean(prefs, "auto_capitalization") && shouldAutoCapitalize()
        val output = if (shiftState != ShiftState.OFF || autoCap) letter.uppercase() else letter
        if (shiftState == ShiftState.ONCE) shiftState = ShiftState.OFF
        return output
    }

    private data class VelocitySample(val x: Float, val y: Float, val timeMs: Long)
    private val velocitySamples = ArrayList<VelocitySample>()
    private val velocityWindowMs = 32L
    private var rPX = 0f
    private var rPY = 0f
    private var rLastEventTime = 0L
    private var rTD = 0f
    private var rSegmentStartX = 0f
    private var rSegmentStartY = 0f
    private var candidateDir = -1

    private fun resetVelocityWindow(x: Float, y: Float, timeMs: Long) {
        velocitySamples.clear()
        velocitySamples += VelocitySample(x, y, timeMs)
        rPX = x
        rPY = y
        rLastEventTime = timeMs
        rSegmentStartX = x
        rSegmentStartY = y
        rTD = 0f
        candidateDir = -1
    }

    private fun moveR(ev: MotionEvent) {
        if (rightId == -1 || !lDown) return
        val idx = ev.findPointerIndex(rightId); if (idx == -1) return
        val x = ev.getX(idx); val y = ev.getY(idx)
        rCX = x; rCY = y

        val previousX = rPX
        val previousY = rPY
        val rawDx = x - previousX
        val rawDy = y - previousY
        val eventTime = ev.eventTime
        val dtMs = (eventTime - rLastEventTime).coerceAtLeast(1L)
        val step = hypot(rawDx.toDouble(), rawDy.toDouble()).toFloat()
        rPX = x; rPY = y; rLastEventTime = eventTime

        velocitySamples += VelocitySample(x, y, eventTime)
        // Keep a recent 32 ms window. If there was a long pause, do not let
        // stale coordinates from before the pause distort the current velocity.
        while (velocitySamples.size > 2 && eventTime - velocitySamples[1].timeMs > velocityWindowMs) {
            velocitySamples.removeAt(0)
        }
        if (velocitySamples.size >= 2 && eventTime - velocitySamples[0].timeMs > velocityWindowMs) {
            velocitySamples.removeAt(0)
        }
        val first = velocitySamples.firstOrNull() ?: VelocitySample(previousX, previousY, eventTime)
        val windowDtMs = eventTime - first.timeMs
        val windowDx = x - first.x
        val windowDy = y - first.y
        val windowDistance = hypot(windowDx.toDouble(), windowDy.toDouble()).toFloat()
        val windowSpeedDpS = if (windowDtMs >= 8L) {
            (windowDistance / dp) * 1000f / windowDtMs.toFloat()
        } else {
            (step / dp) * 1000f / dtMs.toFloat()
        }

        // Direction estimation is independent of the speed threshold. Use the
        // recent displacement window whenever it contains enough real movement;
        // setting gesture speed to 0 disables only the speed gate, not smoothing.
        val hasWindowVector = windowDtMs >= 8L && windowDistance >= 0.35f * dp
        val directionDx = if (hasWindowVector) windowDx else rawDx
        val directionDy = if (hasWindowVector) windowDy else rawDy
        if (hypot(directionDx.toDouble(), directionDy.toDouble()) < 0.2f * dp) {
            invalidate()
            return
        }
        val direction = dirOf(directionDx, directionDy, candidateDir)

        if (direction != candidateDir) {
            candidateDir = direction
            // Measure progress from the point at which this direction was detected.
            rSegmentStartX = previousX
            rSegmentStartY = previousY
        }
        rTD = hypot((x - rSegmentStartX).toDouble(), (y - rSegmentStartY).toDouble()).toFloat()

        val speedPasses = minGestureSpeedDpS <= 0f || windowSpeedDpS >= minGestureSpeedDpS
        // Distance and speed are independent gates. Setting speed to 0 disables
        // only the time filter; gestR still protects against accidental micro-movements.
        if (rTD < gestR || !speedPasses) { invalidate(); return }

        if (candidateDir == lastDir) {
            // Repeating a direction is not a second key; restart tracking at the current point.
            resetVelocityWindow(x, y, eventTime)
            invalidate()
            return
        }

        val selectedDirection = candidateDir
        val letter = letAt(grp, selectedDirection)
        if (letter != null) {
            val wasOneShotShift = shiftState == ShiftState.ONCE
            val autoCapApplied = shiftState != ShiftState.CAPS &&
                    KeyboardSettings.readBoolean(prefs, "auto_capitalization") && shouldAutoCapitalize()
            val committed = applyShift(letter)
            commitInputText(committed)
            // A capital produced only by one-shot Shift or sentence auto-cap is
            // repeated as its lowercase source. Caps Lock keeps uppercase repeats.
            lastLtr = if (wasOneShotShift || autoCapApplied) letter.lowercase() else committed
            lastDir = selectedDirection
        }
        // Drop pre-commit motion history so a quick turn (e.g. left then up)
        // is measured as the new movement rather than blended with the previous swipe.
        resetVelocityWindow(x, y, eventTime)
        invalidate()
    }

    private fun dirOf(dx: Float, dy: Float, previousDir: Int = -1): Int {
        // Eight equal 45-degree sectors. Previously the 1.65 ratio made cardinals
        // about 62 degrees wide while diagonals were only about 28 degrees wide.
        // The narrow diagonal sectors caused candidateDir to reset its distance
        // progress repeatedly, which felt like a higher threshold for diagonals.
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0f) angle += 360f
        val centers = floatArrayOf(270f, 315f, 0f, 45f, 90f, 135f, 180f, 225f)
        fun angleDistance(a: Float, b: Float): Float {
            val delta = abs(a - b) % 360f
            return min(delta, 360f - delta)
        }

        // A small hysteresis prevents touch jitter around sector borders from
        // switching candidates every event and restarting the distance counter.
        if (previousDir in 0..7 && angleDistance(angle, centers[previousDir]) <= 25f) {
            return previousDir
        }
        return centers.indices.minByOrNull { angleDistance(angle, centers[it]) } ?: 2
    }
}