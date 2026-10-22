package app.vimex.microglideboard

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputConnection
import kotlin.math.*

class GlideKeyboardView(context: Context) : View(context) {

    companion object {
        private const val PREV_DP   = 26f
        private const val CX_R      = 0.50f
        private const val Y_TOP     = 0.12f; private const val Y_BOT = 0.88f
        private const val SIDE_DP   = 22f
        private const val CENR_DP   = 14f;  private const val HUP_DP = 96f
        private const val BS_HOLD_MS = 500L;  private const val BS_RPT_MS = 80L
        private const val LK = "\u0001"
        private const val SK = "\u0002"
    }

    private val dp = resources.displayMetrics.density
    private val prefs = context.getSharedPreferences("glide_kb", Context.MODE_PRIVATE)
    private val hintToggleListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "disable_left_hints" || key == "disable_right_hints" ||
            key == "show_text_preview" || key == "preview_lines" ||
            key == "height_dp" || key == "left_ratio" || key == "gest_dp" ||
            key == "gesture_speed" || key == "gesture_height_scale" ||
            key == "gesture_center_offset_dp" || key == "hint_right" ||
            key == "hint_bubble" || key == "hint_pill" || key == "pill_dist" ||
            key == "bottom_margin_dp") {
            reloadSettings()
        }
    }

    private var heightDp = KeyboardSettings.defaultFor("height_dp")
    private var leftRatio = KeyboardSettings.defaultFor("left_ratio")
    private var gestureHeightScale = KeyboardSettings.defaultFor("gesture_height_scale")
    private var gestureCenterOffsetDp = KeyboardSettings.defaultFor("gesture_center_offset_dp")
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
    private var showTextPreview = true
    private var previewLines = 3
    private var pillRadius = KeyboardSettings.defaultFor("hint_pill") * dp
    private var pillOff = KeyboardSettings.defaultFor("pill_dist") * dp
    private var bottomMarginDp = KeyboardSettings.defaultFor("bottom_margin_dp")
    private var prevH = PREV_DP * dp

    init { reloadSettings() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.registerOnSharedPreferenceChangeListener(hintToggleListener)
        reloadSettings()
    }

    override fun onDetachedFromWindow() {
        prefs.unregisterOnSharedPreferenceChangeListener(hintToggleListener)
        super.onDetachedFromWindow()
    }

    fun reloadSettings() {
        val oldHeight = heightDp
        val oldPreviewHeight = prevH
        val oldShowTextPreview = showTextPreview
        val oldBottomMargin = bottomMarginDp
        heightDp = KeyboardSettings.read(prefs, "height_dp")
        leftRatio = KeyboardSettings.read(prefs, "left_ratio")
        gestureHeightScale = KeyboardSettings.read(prefs, "gesture_height_scale")
        gestureCenterOffsetDp = KeyboardSettings.read(prefs, "gesture_center_offset_dp")
        gestR = KeyboardSettings.read(prefs, "gest_dp") * dp
        minGestureSpeedDpS = KeyboardSettings.read(prefs, "gesture_speed")
        hR = KeyboardSettings.read(prefs, "hint_right") * dp
        hLR = hR * 0.79f
        hBubble = KeyboardSettings.read(prefs, "hint_bubble")
        disableLeftHints = KeyboardSettings.readBoolean(prefs, "disable_left_hints")
        disableRightHints = KeyboardSettings.readBoolean(prefs, "disable_right_hints")
        showTextPreview = KeyboardSettings.readBoolean(prefs, "show_text_preview")
        previewLines = KeyboardSettings.read(prefs, "preview_lines").toInt().coerceIn(1, 50)
        prevH = if (showTextPreview) max(PREV_DP * dp, previewLines * 12.5f * dp * 1.18f + 8f * dp) else PREV_DP * dp
        pillRadius = KeyboardSettings.read(prefs, "hint_pill") * dp
        pillOff = KeyboardSettings.read(prefs, "pill_dist") * dp
        bottomMarginDp = KeyboardSettings.read(prefs, "bottom_margin_dp")
        // The indicator and the typing area use identical geometry in both modes.
        // Since prevH grows with previewLines, onMeasure adds the same extra height
        // regardless of whether letters or symbols are currently displayed.
        if (oldHeight != heightDp || oldPreviewHeight != prevH ||
            oldBottomMargin != bottomMarginDp || oldShowTextPreview != showTextPreview) {
            requestLayout()
        }
        invalidate()
    }

    // ↑ ↗ → ↘ ↓ ↙ ← ↖  (індекси 0..7)
    private val N: String? = null
    private val langData = mapOf(
        "УКР" to listOf(
            //        ↑     ↗     →     ↘     ↓     ↙     ←     ↖
            listOf("о", "а", "е", "и", "н", "т", "р", "с"),  // Neutral
            listOf("в", "л", "к", "м", "д", "п", "у", "і"),  // G1
            listOf("ь", "я", "з", "й", "б", "г", "ч", "ж"),  // G2
            listOf("ю", "х", "ш", "є", "ї", "щ", "ц", "ф"),  // G3
            listOf("ґ", N, N, N, N, N, N, N)                    // G4
        ),
        "ENG" to listOf(
            listOf("e", "a", "o", "i", "n", "t", "r", "s"),  // Neutral
            listOf("w", "l", "c", "m", "d", "p", "u", "h"),  // G1
            listOf("g", "y", "f", "b", "v", "k", "j", N),    // G2
            listOf("x", "z", "q", N, N, N, N, N)                 // G3
        ),
        "РУС" to listOf(
            listOf("о", "а", "е", "и", "н", "т", "р", "с"),  // Neutral
            listOf("в", "л", "к", "м", "д", "п", "у", "ы"),  // G1
            listOf("ь", "я", "з", "й", "б", "г", "ч", "ж"),  // G2
            listOf("ю", "х", "ш", "э", "ё", "щ", "ц", "ф"),  // G3
            listOf("ъ", N, N, N, N, N, N, N)                     // G4
        )
    )
    private val langOrder = listOf("УКР","ENG","РУС")
    private var langIdx = 0
    private val lang    get() = langOrder[langIdx]

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
        "┐(￣ヘ￣)┌"
    )

    private var currentSymbolEmoticon = symbolEmoticons.random()
    private fun letters()             = langData[lang] ?: emptyList()
    private fun numG()                = letters().size
    private fun letAt(g: Int, d: Int) = letters().getOrNull(g)?.getOrNull(d)

    private val dv = listOf(
        0f to -1f, 1f to -1f, 1f to 0f, 1f to 1f,
        0f to  1f,-1f to  1f,-1f to 0f,-1f to -1f
    )

    var inputConnection: InputConnection? = null

    /** Called by InputMethodService whenever the editor selection/caret changes. */
    fun onEditorSelectionChanged() {
        if (showTextPreview) postInvalidateOnAnimation()
    }

    private var grp = 0; private var lastLtr = ""; private var lastDir = -1
    private var leftId = -1; private var lDown = false
    private var leftCX = 0f
    private var consumed = false; private var inBsZone = false; private var holdBs = false
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
            ActionKey("CHAR::", ":"),
            ActionKey("CHAR:-", "-", listOf("−", "–", "—")),
            ActionKey("CHAR:\"", "\"", listOf("“", "”", "„")),
            ActionKey("CHAR:'", "'", listOf("‘", "’", "‚"))
        ),
        listOf(
            ActionKey("CHAR:(", "(", listOf("[", "{", "<")),
            ActionKey("CHAR:)", ")", listOf("]", "}", ">")),
            ActionKey("CHAR:;", ";"),
            ActionKey("CHAR:/", "/")
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
    private val actionHandler = Handler(Looper.getMainLooper())
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
            if (holdBs && inBsZone && lDown && leftId != -1 && !symMode) {
                deleteBackwardOrSelection()
                invalidate()
                bsH.postDelayed(this, BS_RPT_MS)
            } else {
                holdBs = false
            }
        }
    }
    private val bsHoldRun = Runnable {
        if (lDown && leftId != -1 && consumed && inBsZone && !symMode) {
            holdBs = true
            bsH.post(bsRun)
        }
    }

    private fun stopBackspaceRepeat() {
        holdBs = false
        bsH.removeCallbacks(bsRun)
        bsH.removeCallbacks(bsHoldRun)
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
    private fun leftSplitY() = prevH + (height - prevH) * 0.5f
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
        val zoneTop = prevH
        val zoneBottom = contentBottom()
        val zoneHeight = (zoneBottom - zoneTop).coerceAtLeast(1f)
        val baseTop = zoneTop + zoneHeight * Y_TOP
        val baseBottom = zoneTop + zoneHeight * Y_BOT
        val baseSpan = (baseBottom - baseTop).coerceAtLeast(1f)
        // No arbitrary 0.45..1.0 cap: users can shrink or stretch the zone.
        // The final center is kept inside the keyboard's physical drawing area.
        val span = baseSpan.toDouble() * gestureHeightScale.toDouble()
        val center = zoneTop.toDouble() + zoneHeight.toDouble() / 2.0 + gestureCenterOffsetDp.toDouble() * dp.toDouble()
        val fraction = if (n <= 1) 0.5 else g.toDouble() / (n - 1).toDouble()
        val y = center - span / 2.0 + span * fraction
        return y.coerceIn(zoneTop.toDouble(), zoneBottom.toDouble()).toFloat()
    }
    private fun cursorKeyLayout(): List<CursorKeyBounds> {
        val dx = dX()
        val colW = dx / 3f
        val top = leftSplitY()
        val mid = top + (height - top) * 0.5f
        return listOf(
            CursorKeyBounds(cursorLeft, 0f, top, colW, contentBottom()),
            CursorKeyBounds(cursorUp, colW, top, colW * 2f, mid),
            CursorKeyBounds(cursorDown, colW, mid, colW * 2f, contentBottom()),
            CursorKeyBounds(cursorRight, colW * 2f, top, dx, contentBottom())
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
    private val pPrvCursor = ps("#566380", 1.5f)
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

    // Actual drawing/content bottom excludes the reserved blank bottom margin.
    // Both keyboard modes share the same indicator height and typing-area height.
    private fun contentBottom(): Float = height.toFloat() - bottomMarginDp * dp

    override fun onMeasure(w: Int, h: Int) {
        // Use the requested height without an arbitrary dp ceiling. The upper bound below
        // is Android's technical MeasureSpec size limit, not a user-facing setting limit.
        val maxMeasureSize = (1 shl 30) - 1
        // Indicator growth is added in both modes. This keeps the symbol-key area equal
        // in width and height to the normal typing area; bottomMarginDp remains empty
        // space below the keyboard content in both modes.
        val extraPreviewPx = if (showTextPreview) {
            (prevH - PREV_DP * dp).coerceAtLeast(0f)
        } else {
            0f
        }
        val requestedPx = (heightDp + bottomMarginDp).toDouble() * dp.toDouble() + extraPreviewPx.toDouble()
        val heightPx = requestedPx.coerceIn(1.0, maxMeasureSize.toDouble()).toInt()
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY))
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
        if (symMode) { drawSym(canvas); return }
        val W = width.toFloat(); val H = contentBottom(); val dx = dX()
        canvas.drawRect(0f,0f,W,prevH,pBgT)
        canvas.drawRect(0f,prevH,dx,H,pBgL)
        canvas.drawRect(dx,prevH,W,H, if (lDown) pBgR else pBgRd)
        canvas.drawLine(0f,prevH,W,prevH,pDvH); canvas.drawLine(dx,prevH,dx,H,pDvV)
        if (showTextPreview) drawTextPreview(canvas, W)
        canvas.drawText(lang, 20f*dp, prevH/2f+pLng.textSize/3f, pLng)
        canvas.save(); canvas.clipRect(0f,prevH,dx,H); drawLeft(canvas,dx); canvas.restore()
        if (lDown && rDown) {
            canvas.save(); canvas.clipRect(dx,prevH,W,H); drawRight(canvas,dx,W); canvas.restore()
        } else if (!lDown) {
            canvas.save(); canvas.clipRect(dx,prevH,W,H); drawActionBar(canvas); canvas.restore()
        }
    }

    private fun drawTextPreview(canvas: Canvas, widthPx: Float, areaHeight: Float = prevH) {
        val before = try { inputConnection?.getTextBeforeCursor(180, 0)?.toString() ?: "" } catch (_: Exception) { "" }
        val after = try { inputConnection?.getTextAfterCursor(180, 0)?.toString() ?: "" } catch (_: Exception) { "" }
        val full = before + after
        val allLines = full.split('\n')
        val cursorLine = before.count { it == '\n' }.coerceIn(0, allLines.lastIndex)
        val cursorColumn = before.substringAfterLast('\n').length

        val lineHeight = pPrv.textSize * 1.15f
        // In symbol mode the overall keyboard height is fixed, so the preview area
        // is capped to leave usable room for all five key rows. Show as many of the
        // requested context rows as can fit in that area.
        val rowsThatFit = ((areaHeight - 8f * dp) / lineHeight).toInt().coerceAtLeast(1)
        // Keep the configured number of rows when the area permits it. Missing
        // context rows are rendered blank instead of shrinking the indicator.
        val rowsToShow = min(previewLines.coerceIn(1, 50), rowsThatFit)
        val preceding = (rowsToShow - 1) / 2
        // If there are enough real lines, shift the window at document boundaries
        // so it remains full (e.g. last line => show two previous lines + current).
        // If the whole document is shorter than the selected count, blank rows fill the gap.
        val startLine = if (allLines.size >= rowsToShow) {
            (cursorLine - preceding).coerceIn(0, allLines.size - rowsToShow)
        } else {
            cursorLine - preceding
        }
        val totalHeight = rowsToShow * lineHeight
        val metrics = pPrv.fontMetrics
        val firstBaseline = (areaHeight - totalHeight) / 2f - metrics.ascent
        canvas.save()
        canvas.clipRect(48f * dp, 0f, widthPx - 5f * dp, areaHeight)
        val availableWidth = (widthPx - 58f * dp).coerceAtLeast(1f)
        val anchorX = widthPx - 8f * dp

        repeat(rowsToShow) { index ->
            val lineIndex = startLine + index
            if (lineIndex in allLines.indices) {
                val line = allLines[lineIndex]
                val baseline = firstBaseline + index * lineHeight
                if (lineIndex == cursorLine) {
                    // Draw the text on both sides of the caret separately. Using a font glyph
                    // such as '▏' inserts its own side-bearing, which looks like a fake space.
                    val safeColumn = cursorColumn.coerceIn(0, line.length)
                    val leftRaw = line.substring(0, safeColumn)
                    val rightRaw = line.substring(safeColumn)
                    val (left, right) = fitPreviewLineAroundCursor(leftRaw, rightRaw, availableWidth)
                    val rightWidth = pPrv.measureText(right)
                    val caretX = anchorX - rightWidth
                    canvas.drawText(right, anchorX, baseline, pPrv)
                    canvas.drawText(left, caretX, baseline, pPrv)
                    pPrvCursor.color = pPrv.color
                    canvas.drawLine(caretX, baseline + metrics.ascent, caretX,
                        baseline + metrics.descent, pPrvCursor)
                } else {
                    val visible = android.text.TextUtils.ellipsize(
                        line, android.text.TextPaint(pPrv), availableWidth,
                        android.text.TextUtils.TruncateAt.START
                    ).toString()
                    canvas.drawText(visible, anchorX, baseline, pPrv)
                }
            }
        }
        canvas.restore()
    }

    /** Returns left/right display fragments with truncation ellipses, but no caret glyph. */
    private fun fitPreviewLineAroundCursor(
        leftText: String,
        rightText: String,
        maxWidth: Float
    ): Pair<String, String> {
        var left = leftText
        var right = rightText
        var clippedLeft = false
        var clippedRight = false
        fun visibleLeft() = (if (clippedLeft) "…" else "") + left
        fun visibleRight() = right + (if (clippedRight) "…" else "")
        fun currentWidth() = pPrv.measureText(visibleLeft()) + pPrv.measureText(visibleRight())

        while (currentWidth() > maxWidth && (left.isNotEmpty() || right.isNotEmpty())) {
            if (left.length >= right.length && left.isNotEmpty()) {
                left = left.drop(1)
                clippedLeft = true
            } else if (right.isNotEmpty()) {
                right = right.dropLast(1)
                clippedRight = true
            }
        }
        return visibleLeft() to visibleRight()
    }

    private fun drawLeft(canvas: Canvas, dx: Float) {
        val cx = shownLeftCX()
        if (!lDown) {
            drawCursorButtons(canvas)
            if (!disableLeftHints) {
                canvas.drawText("◎", cx, idlePointY() + pGIn.textSize / 3f, pGIn)
            }
            return
        }
        if (disableLeftHints) return
        // Full-width separators between neighboring letter groups.
        for (g in 0 until numG() - 1) {
            val dividerY = (gY(g) + gY(g + 1)) * 0.5f
            canvas.drawLine(0f, dividerY, dx, dividerY, pGroupDivider)
        }
        (0 until numG()).forEach { g ->
            val gy = gY(g); val act = g == grp
            canvas.drawText(if (g==0) "◎" else "$g", cx, gy+(if (act) pGAc else pGIn).textSize/3f, if (act) pGAc else pGIn)
            if (act && !consumed) {
                // Відсуваємо підказки від точки контакту й збільшуємо їх.
                val pillR = pillRadius
                val pillOffset = pillOff + pillR + 5f * dp
                val leftX = (cx - pillOffset).coerceAtLeast(pillR + 4f * dp)
                val rightX = (cx + pillOffset).coerceAtMost(dx - pillR - 4f * dp)
                pill(canvas, rightX, gy, lastLtr.uppercase().let { if (it.isEmpty()) "·" else "↺$it" }, pPBg, pRpT)
                pill(canvas, leftX, gy, "⌫", pPBg, pBsT)
            } else if (!act) { bubble(canvas, dx, gy, g) }
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
            canvas.drawRoundRect(rect, 5f * dp, 5f * dp, pCursorBorder)
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
        val bcx = zoneW*0.83f; val fs = 10f*dp*scale
        val cw = 12f*dp*scale; val rh = 11f*dp*scale; val pad = 3f*dp*scale
        val rows = 2
        val cols = ceil(ls.size / rows.toFloat()).toInt().coerceAtLeast(1)
        val tw = cols*cw+pad; val th = rows*rh+pad
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
        val safeDistance = hR + touchRadius + 14f * dp

        var hx = rCX.coerceIn(minX, maxX)
        var hy = (rCY - hUp).coerceIn(minY, maxY)
        val topDistance = hypot((hx - rCX).toDouble(), (hy - rCY).toDouble()).toFloat()

        // Move sideways before the circles touch. Never place the hint below
        // the finger; prefer the side with more room while respecting bounds.
        if (topDistance < safeDistance) {
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
            )
            hy = (rCY - neededVertical).coerceIn(minY, sideY)
        }
        // If there is neither usable space above nor on the left, use the top-right corner.
        if (rCY - hUp < minY && rCX - safeDistance < minX) {
            hx = maxX
            hy = minY
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
        val availableH = (height - prevH).coerceAtLeast(1f)
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
            val keyW = zoneW / row.size
            row.forEachIndexed { index, key ->
                val left = dx + index * keyW
                result += ActionKeyBounds(key, rowIndex + 2, index, left, yTop, left + keyW, yTop + symbolRowH)
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
        layout.forEach { bounds ->
            val isActive = actionId != -1 && actionCurrent?.key?.id == bounds.key.id && actionCurrent?.row == bounds.row
            val isShift = bounds.key.id == "SHIFT"
            val shiftBg = when {
                isActive -> pActionBgActive
                isShift && shiftState == ShiftState.ONCE -> pActionShiftOnceBg
                isShift && shiftState == ShiftState.CAPS -> pActionShiftCapsBg
                else -> pActionBg
            }
            val rect = RectF(bounds.left + 2f * dp, bounds.top + 2f * dp, bounds.right - 2f * dp, bounds.bottom - 2f * dp)
            canvas.drawRoundRect(rect, 5f * dp, 5f * dp, if (isShift) shiftBg else if (isActive) pActionBgActive else pActionBg)
            if (isShift) {
                drawShiftIcon(canvas, bounds, shiftState)
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
            color = Color.parseColor(if (state == ShiftState.CAPS) "#FFFFFF" else "#DCE6F2")
            style = if (state == ShiftState.OFF) Paint.Style.STROKE else Paint.Style.FILL
            strokeWidth = 2.2f * dp
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(path, iconPaint)
        if (state == ShiftState.CAPS) {
            val underline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFFFFF")
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
        actionHandler.removeCallbacks(actionLongPressRun)
        if (actionStart?.key?.alternates?.isNotEmpty() == true) {
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
        }
        invalidate()
    }

    private fun finishActionTouch(id: Int, x: Float, y: Float) {
        if (id != actionId) return
        actionHandler.removeCallbacks(actionLongPressRun)
        if (actionPopupShown) {
            actionPopupIndexAtX(x)?.let { actionSelectedAlt = it }
            actionStart?.key?.alternates?.getOrNull(actionSelectedAlt)?.let { inputConnection?.commitText(it, 1) }
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
                requestLayout()
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
            "ENTER" -> inputConnection?.commitText("\n", 1)
            "SETTINGS" -> {
                runCatching {
                    context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            else -> if (key.id.startsWith("CHAR:")) inputConnection?.commitText(key.label, 1)
        }
        invalidate()
    }

    private fun repeatLastWord() {
        val before = try { inputConnection?.getTextBeforeCursor(200, 0)?.toString() } catch (_: Exception) { null } ?: return
        if (before.isEmpty()) return
        val hasTrailingSpace = before.endsWith(" ")
        val base = before.trimEnd(' ')
        val wordStart = base.indexOfLast { it.isWhitespace() } + 1
        val word = base.substring(wordStart)
        if (word.isEmpty()) return
        inputConnection?.commitText(if (hasTrailingSpace) "$word " else " $word", 1)
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
                symbol("+", "±", "∓"), symbol("(", "[", "{"), symbol(")", "]", "}"), symbol("/", "\\", "|")
            ),
            listOf(
                SymKey("=\\<", weight = 1.42f, action = SymAction.PAGE),
                SymKey("*", weight = 0.9f, alternates = listOf("•", "×")),
                SymKey("\"", weight = 0.9f, alternates = listOf("“", "”", "„", "‟")),
                SymKey("'", weight = 0.9f, alternates = listOf("‘", "’", "‚", "‛")),
                SymKey(":", weight = 0.9f, alternates = listOf("÷", "∶")),
                SymKey(";", weight = 0.9f, alternates = listOf("…", "⋅")),
                SymKey("!", weight = 0.9f, alternates = listOf("¡")),
                SymKey("?", weight = 0.9f, alternates = listOf("¿")),
                SymKey("⌫", weight = 1.35f, action = SymAction.BACKSPACE)
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
                SymKey("?123", weight = 1.42f, action = SymAction.PAGE),
                SymKey("%", weight = 0.78f, alternates = listOf("‰", "‱")),
                SymKey("©", weight = 0.78f, alternates = listOf("ⓒ")),
                SymKey("®", weight = 0.78f, alternates = listOf("ⓡ")),
                SymKey("™", weight = 0.78f, alternates = listOf("℠")),
                SymKey("✓", weight = 0.78f, alternates = listOf("✔", "☑")),
                SymKey("[", weight = 0.92f, alternates = listOf("(", "{")),
                SymKey("]", weight = 0.92f, alternates = listOf(")", "}")),
                SymKey("⌫", weight = 1.35f, action = SymAction.BACKSPACE)
            )
        )
    )

    private fun bottomSymKeys() = listOf(
        SymKey("ABC", weight = 1.35f, action = SymAction.ABC),
        if (symPage == 0) {
            SymKey(",", weight = 0.8f, alternates = listOf("،", "‚"))
        } else {
            SymKey("<", weight = 0.8f, alternates = listOf("≤", "≪"))
        },
        SymKey(
            currentSymbolEmoticon,
            value = " ", weight = 4f, action = SymAction.SPACE
        ),
        if (symPage == 0) {
            SymKey(".", weight = 0.8f, alternates = listOf("…", "·"))
        } else {
            SymKey(">", weight = 0.8f, alternates = listOf("≥", "≫"))
        },
        SymKey("↵", value = "\n", weight = 1.35f, action = SymAction.ENTER)
    )

    private fun symRowsForPage() = symPages[symPage.coerceIn(0, symPages.lastIndex)]

    private fun symbolPreviewHeight(): Float {
        // The indicator occupies exactly the same height as in letter mode. Do not
        // cap it based on the keyboard body height: prevH already determines the
        // matching total height shared by both modes.
        return prevH
    }

    private fun symLayout(): List<SymKeyBounds> {
        val w = width.toFloat(); val h = contentBottom()
        if (w <= 0f || h <= 0f) return emptyList()
        // Match the normal keyboard's geometry: same indicator height above the
        // symbol keys, same remaining typing area height, same bottom margin.
        val previewAreaH = symbolPreviewHeight()
        val top = previewAreaH + 5f * dp
        val bottom = 5f * dp
        val rowGap = 3f * dp
        val rows = symRowsForPage() + listOf(bottomSymKeys())
        val rowH = (h - top - bottom - rowGap * (rows.size - 1)).coerceAtLeast(1f) / rows.size.coerceAtLeast(1)
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
            (bounds.bottom + 5f * dp).coerceAtMost((height - popupH - 3f * dp).coerceAtLeast(3f * dp))
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
        val w = width.toFloat()
        val h = contentBottom()
        canvas.drawRect(0f, 0f, w, h, pSyB)
        val previewAreaH = symbolPreviewHeight()
        // Keep the indicator field identical in size and appearance in both modes,
        // even when its text is disabled (the field then stays blank).
        canvas.drawRect(0f, 0f, w, previewAreaH, pBgT)
        if (showTextPreview) drawTextPreview(canvas, w, previewAreaH)
        canvas.drawLine(0f, previewAreaH, w, previewAreaH, pDvH)
        symLayout().forEach { b ->
            val isCurrent = symDownId != -1 &&
                    symCurrentBounds?.rowIndex == b.rowIndex && symCurrentBounds?.keyIndex == b.keyIndex
            val special = b.key.action != SymAction.INPUT
            val bg = when {
                isCurrent -> pSPressed
                special -> pSBAl
                else -> pSBtn
            }
            canvas.drawRoundRect(
                RectF(b.left + 2f * dp, b.top + 2f * dp, b.right - 2f * dp, b.bottom - 2f * dp),
                6f * dp, 6f * dp, bg
            )
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
            SymAction.ABC -> "ABC"
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
        val maxTop = (height - previewH - 2f * dp).coerceAtLeast(minTop)
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
                        if (alt != null) inputConnection?.commitText(alt, 1)
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
            SymAction.INPUT -> inputConnection?.commitText(key.value, 1)
            SymAction.BACKSPACE -> deleteBackwardOrSelection()
            SymAction.ABC -> {
                clearSymTracking()
                symMode = false
                requestLayout()
                resetGesturePointers()
            }
            SymAction.PAGE -> {
                // Keep the same emoticon while switching symbol pages. It is chosen
                // once when entering symbol mode from the letter keyboard.
                symPage = (symPage + 1) % symPages.size
                clearSymTracking()
            }
            SymAction.SPACE -> inputConnection?.commitText(" ", 1)
            SymAction.ENTER -> inputConnection?.commitText("\n", 1)
        }
        invalidate()
    }

    private fun sendCursorKey(keyCode: Int) {
        val ic = inputConnection ?: return
        try {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (_: Exception) { }
        // Repeat-run moves the caret every ~80 ms. Redraw after every step instead of
        // waiting until the finger is released, so the preview follows the caret live.
        onEditorSelectionChanged()
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

    private fun insertSpaceUnlessTextIsImmediatelyAfterCursor() {
        val next = try { inputConnection?.getTextAfterCursor(1, 0)?.firstOrNull() } catch (_: Exception) { null }
        // A newline/whitespace after the cursor is not text blocking a space.
        if (next != null && !next.isWhitespace()) return
        inputConnection?.commitText(" ", 1)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
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
                    x < dx && leftId == -1 && cursorId == -1 && isInIdleGestureZone(y) -> {
                        leftId = id; lDown = true
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
                        leftId = -1; lDown = false; consumed = false; inBsZone = false
                        stopBackspaceRepeat(); invalidate()
                    }
                    rightId -> {
                        insertSpaceUnlessTextIsImmediatelyAfterCursor()
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
                consumed = false; inBsZone = false; lastDir = -1
                velocitySamples.clear(); rLastEventTime = 0L; rTD = 0f; candidateDir = -1
                stopBackspaceRepeat(); clearActionTracking(); clearCursorTracking(); invalidate()
            }
        }
        return true
    }

    private fun moveL(ev: MotionEvent) {
        if (leftId == -1) return
        val idx = ev.findPointerIndex(leftId); if (idx == -1) return
        val x = ev.getX(idx); val y = ev.getY(idx)
        val ng = nearestG(y)
        if (ng != grp) {
            grp = ng
            // Dynamic zero calibration happens at the group transition only.
            leftCX = clampLeftCX(x)
            consumed=false; inBsZone=false
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

        if (consumed) {
            val ddy = y - gY(grp)
            if (sqrt(ddx*ddx+ddy*ddy) < cenR) {
                consumed=false; inBsZone=false
                stopBackspaceRepeat(); invalidate()
            }
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
                if (lastLtr.isNotEmpty()) inputConnection?.commitText(lastLtr,1)
                consumed=true; invalidate()
            }
        }
    }

    private fun deleteBackwardOrSelection() {
        val ic = inputConnection ?: return
        val selected = try { ic.getSelectedText(0)?.toString() } catch (_: Exception) { null }
        if (!selected.isNullOrEmpty()) ic.commitText("", 1)
        else ic.deleteSurroundingText(1, 0)
    }

    private fun applyShift(letter: String): String {
        val output = if (shiftState != ShiftState.OFF) letter.uppercase() else letter
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
            val committed = applyShift(letter)
            inputConnection?.commitText(committed, 1)
            lastLtr = committed
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