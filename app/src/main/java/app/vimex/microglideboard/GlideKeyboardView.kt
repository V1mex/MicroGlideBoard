import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputConnection
import kotlin.math.*

class GlideKeyboardView(context: Context) : View(context) {

    companion object {
        private const val HEIGHT_DP  = 330f;  private const val PREV_DP   = 26f
        private const val LEFT_RATIO = 0.38f; private const val CX_R      = 0.42f
        private const val Y_TOP      = 0.12f; private const val Y_BOT     = 0.88f
        private const val GEST_DP    = 12f;   private const val SIDE_DP   = 22f
        private const val CENR_DP    = 14f;   private const val HUP_DP    = 88f
        private const val HR_DP      = 38f;   private const val HLR_DP    = 30f
        private const val PILL_R_DP  = 17f
        private const val BS_HOLD_MS = 500L;  private const val BS_RPT_MS = 80L
        private const val LK = "\u0001"  // LANG_KEY
        private const val SK = "\u0002"  // SYM_KEY
    }

    private val dp    = resources.displayMetrics.density
    private val gestR = GEST_DP * dp; private val sideR = SIDE_DP * dp
    private val cenR  = CENR_DP * dp; private val hUp   = HUP_DP  * dp
    private val hR    = HR_DP   * dp; private val hLR   = HLR_DP  * dp
    private val prevH = PREV_DP * dp

    // ↑ ↗ → ↘ ↓ ↙ ← ↖  (індекси 0..7)
    private val N: String? = null
    private val langData = mapOf(
        "УКР" to listOf(
            listOf("о","а","е","и","н","т","р","і"),
            listOf("с","в","к","л","м","д","п","у"),
            listOf("й","з","б","г","ж","ч","ю","ь"),
            listOf("х","ц","ш","щ","ф","я","є","ї"),
            listOf("ґ", N, N, SK, N, LK, N, N)
        ),
        "ENG" to listOf(
            listOf("e","t","a","o","i","n","s","h"),
            listOf("r","d","l","c","u","m","w","f"),
            listOf("g","y","p","b","v","k","j","q"),
            listOf("x","z", N, SK, N, LK, N, N)
        ),
        "РУС" to listOf(
            listOf("о","е","а","и","н","т","с","р"),
            listOf("в","л","к","м","д","п","у","я"),
            listOf("з","ь","б","г","й","х","ж","ш"),
            listOf("ы","э","ю","ч","щ","ц","ф","ё"),
            listOf("ъ", N, N, SK, N, LK, N, N)
        )
    )
    private val langOrder = listOf("УКР","ENG","РУС")
    private var langIdx = 0
    private val lang    get() = langOrder[langIdx]
    private fun letters()             = langData[lang] ?: emptyList()
    private fun numG()                = letters().size
    private fun letAt(g: Int, d: Int) = letters().getOrNull(g)?.getOrNull(d)

    private val dv = listOf(
        0f to -1f, 1f to -1f, 1f to 0f, 1f to 1f,
        0f to  1f,-1f to  1f,-1f to 0f,-1f to -1f
    )

    var inputConnection: InputConnection? = null

    private var grp = 0; private var lastLtr = ""; private var lastDir = -1
    private var leftId = -1; private var lDown = false
    private var leftCX = 0f; private var leftPrevX = 0f; private var leftPrevY = 0f
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
            inputConnection?.deleteSurroundingText(1, 0)
            invalidate()
            symHandler.postDelayed(symBackspaceRepeatRun, BS_RPT_MS)
        }
    }
    private val symBackspaceRepeatRun = object : Runnable {
        override fun run() {
            if (symMode && symBackspaceRepeating && symDownId != -1 &&
                symStartBounds?.key?.action == SymAction.BACKSPACE &&
                symCurrentBounds?.key?.action == SymAction.BACKSPACE) {
                inputConnection?.deleteSurroundingText(1, 0)
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
                inputConnection?.deleteSurroundingText(1,0)
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
        stopBackspaceRepeat()
    }

    private fun dX() = width  * LEFT_RATIO
    private fun cX() = dX()   * CX_R
    private fun clampLeftCX(x: Float): Float {
        val margin = 8f * dp
        return x.coerceIn(margin, (dX() - margin).coerceAtLeast(margin))
    }
    private fun shownLeftCX() = if (lDown) leftCX else cX()
    private fun gY(g: Int): Float {
        val n = numG()
        val t = if (n <= 1) 0.5f else Y_TOP + (Y_BOT - Y_TOP) * g.toFloat() / (n - 1)
        return prevH + (height - prevH) * t
    }
    private fun nearestG(y: Float) = (0 until numG()).minByOrNull { abs(gY(it) - y) } ?: 0

    private fun p(c: String)  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(c) }
    private fun pt(s: Float, c: String, a: Paint.Align = Paint.Align.CENTER) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(c); textSize = s*dp; textAlign = a }
    private fun ps(c: String, w: Float) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(c); style = Paint.Style.STROKE; strokeWidth = w*dp }

    private val pBgT = p("#0D0D1A"); private val pBgL  = p("#161628")
    private val pBgR = p("#13202E"); private val pBgRd = p("#0D1118")
    private val pDvH = ps("#1A263A",1f); private val pDvV = ps("#1A263A",1f)
    private val pPrv = pt(12.5f,"#566380", Paint.Align.RIGHT)
    private val pLng = pt(11f,"#4A9EBA")
    private val pGAc = pt(19f,"#E2E8F0").also { it.typeface = Typeface.DEFAULT_BOLD }
    private val pGIn = pt(16f,"#273450")
    private val pPBg = p("#192336")
    private val pRpT = pt(10.5f,"#4ADE80"); private val pBsT = pt(10.5f,"#F87171")
    private val pBbB = p("#0E1924"); private val pBbT = pt(10f,"#7EB0E0")
    private val pHBg = p("#112030").also { it.alpha = 230 }
    private val pHBd = ps("#213850",1.5f); private val pHDt = p("#213850")
    private val pHLa = pt(13f,"#5899DA").also { it.typeface = Typeface.DEFAULT_BOLD }
    private val pHLi = pt(12f,"#304A66")
    private val pRDt = p("#5899DA").also { it.alpha = 80 }
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

    override fun onMeasure(w: Int, h: Int) =
        super.onMeasure(w, MeasureSpec.makeMeasureSpec((HEIGHT_DP*dp).toInt(), MeasureSpec.EXACTLY))

    // ── Draw ─────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        if (symMode) { drawSym(canvas); return }
        val W = width.toFloat(); val H = height.toFloat(); val dx = dX()
        canvas.drawRect(0f,0f,W,prevH,pBgT)
        canvas.drawRect(0f,prevH,dx,H,pBgL)
        canvas.drawRect(dx,prevH,W,H, if (lDown) pBgR else pBgRd)
        canvas.drawLine(0f,prevH,W,prevH,pDvH); canvas.drawLine(dx,prevH,dx,H,pDvV)
        val t = try { inputConnection?.getTextBeforeCursor(40,0)?.toString() ?: "" } catch (e: Exception) { "" }
        if (t.isNotEmpty()) canvas.drawText(t, W-8f*dp, prevH/2f+pPrv.textSize/3f, pPrv)
        canvas.drawText(lang, 20f*dp, prevH/2f+pLng.textSize/3f, pLng)
        canvas.save(); canvas.clipRect(0f,prevH,dx,H); drawLeft(canvas,dx); canvas.restore()
        if (lDown && rDown) { canvas.save(); canvas.clipRect(dx,prevH,W,H); drawRight(canvas,dx,W); canvas.restore() }
    }

    private fun drawLeft(canvas: Canvas, dx: Float) {
        val cx = shownLeftCX()
        if (!lDown) { canvas.drawText("◎", cx, gY(0)+pGIn.textSize/3f, pGIn); return }
        (0 until numG()).forEach { g ->
            val gy = gY(g); val act = g == grp
            canvas.drawText(if (g==0) "◎" else "$g", cx, gy+(if (act) pGAc else pGIn).textSize/3f, if (act) pGAc else pGIn)
            if (act && !consumed) {
                // Відсуваємо підказки від точки контакту й збільшуємо їх.
                val pillR = PILL_R_DP * dp
                val pillOffset = sideR + pillR + 13f * dp
                val leftX = (cx - pillOffset).coerceAtLeast(pillR + 4f * dp)
                val rightX = (cx + pillOffset).coerceAtMost(dx - pillR - 4f * dp)
                pill(canvas, rightX, gy, lastLtr.uppercase().let { if (it.isEmpty()) "·" else "↺$it" }, pPBg, pRpT)
                pill(canvas, leftX, gy, "⌫", pPBg, pBsT)
            } else if (!act) { bubble(canvas, dx, gy, g) }
        }
    }

    private fun pill(canvas: Canvas, cx: Float, cy: Float, t: String, bg: Paint, tx: Paint) {
        canvas.drawCircle(cx, cy, PILL_R_DP*dp, bg)
        canvas.drawText(t, cx, cy+tx.textSize/3f, tx)
    }

    private fun bubble(canvas: Canvas, zoneW: Float, cy: Float, g: Int) {
        val ls = letters().getOrNull(g) ?: return
        val bcx = zoneW*0.83f; val fs = 10f*dp
        val cw = 12f*dp; val rh = 11f*dp; val pad = 3f*dp
        val tw = 4*cw+pad; val th = 2*rh+pad
        val r = RectF(bcx-tw/2f, cy-th/2f, bcx+tw/2f, cy+th/2f)
        canvas.drawRoundRect(r, 4f*dp, 4f*dp, pBbB)
        val tpN = Paint(pBbT).apply { textSize = 8f*dp }
        ls.forEachIndexed { i, l ->
            val lx = r.left+(i%4)*cw+cw/2f; val ly = r.top+(i/4)*rh+rh/2f
            when (l) {
                null -> {}
                LK   -> canvas.drawText("🌐", lx, ly+tpN.textSize/3f, tpN)
                SK   -> canvas.drawText("⌨",  lx, ly+tpN.textSize/3f, tpN)
                else -> canvas.drawText(l.uppercase(), lx, ly+fs/3f, Paint(pBbT).apply { textSize = fs })
            }
        }
    }

    private fun drawRight(canvas: Canvas, dx: Float, W: Float) {
        canvas.drawCircle(rCX, rCY, 15f*dp, pRDt)
        val hx = rCX.coerceIn(dx+hR+4f*dp, W-hR-4f*dp)
        val hy = (rCY-hUp).coerceIn(prevH+hR+4f*dp, height.toFloat()-hR-4f*dp)
        canvas.drawCircle(hx,hy,hR,pHBg); canvas.drawCircle(hx,hy,hR,pHBd); canvas.drawCircle(hx,hy,3f*dp,pHDt)
        val ls = letters().getOrNull(grp) ?: return
        dv.forEachIndexed { i, (vx,vy) ->
            val l = ls.getOrNull(i) ?: return@forEachIndexed
            val n = sqrt(vx*vx+vy*vy)
            val d = when(l) { LK -> "🌐"; SK -> "⌨"; else -> l.uppercase() }
            canvas.drawText(d, hx+(vx/n)*hLR, hy+(vy/n)*hLR+pHLa.textSize/3f, if (i==lastDir) pHLa else pHLi)
        }
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
                SymKey("%", weight = 0.9f, alternates = listOf("‰", "‱")),
                SymKey("©", weight = 0.9f, alternates = listOf("ⓒ")),
                SymKey("®", weight = 0.9f, alternates = listOf("ⓡ")),
                SymKey("™", weight = 0.9f, alternates = listOf("℠")),
                SymKey("✓", weight = 0.9f, alternates = listOf("✔", "☑")),
                SymKey("[", weight = 0.9f, alternates = listOf("(", "{")),
                SymKey("]", weight = 0.9f, alternates = listOf(")", "}")),
                SymKey("⌫", weight = 1.35f, action = SymAction.BACKSPACE)
            )
        )
    )

    private fun bottomSymKeys() = listOf(
        SymKey("ABC", weight = 1.35f, action = SymAction.ABC),
        SymKey(",", weight = 0.8f, alternates = listOf("،", "，", "、")),
        SymKey(
            when (lang) { "УКР" -> "Українська"; "ENG" -> "English"; else -> "Русский" },
            value = " ", weight = 4f, action = SymAction.SPACE
        ),
        SymKey(".", weight = 0.8f, alternates = listOf("…", "·")),
        SymKey("↵", value = "\n", weight = 1.35f, action = SymAction.ENTER)
    )

    private fun symRowsForPage() = symPages[symPage.coerceIn(0, symPages.lastIndex)]

    private fun symLayout(): List<SymKeyBounds> {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return emptyList()
        val top = 5f * dp
        val bottom = 5f * dp
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
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pSyB)
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
            SymAction.BACKSPACE -> inputConnection?.deleteSurroundingText(1, 0)
            SymAction.ABC -> {
                clearSymTracking()
                symMode = false
                resetGesturePointers()
            }
            SymAction.PAGE -> {
                symPage = (symPage + 1) % symPages.size
                clearSymTracking()
            }
            SymAction.SPACE -> inputConnection?.commitText(" ", 1)
            SymAction.ENTER -> inputConnection?.commitText("\n", 1)
        }
        invalidate()
    }

    // ── Touch ────────────────────────────────────────────────────────────────

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (symMode) { handleSym(ev); return true }
        val dx = dX()
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = ev.actionIndex; val id = ev.getPointerId(idx)
                val x = ev.getX(idx); val y = ev.getY(idx)
                if (x < dx && leftId == -1) {
                    leftId=id; lDown=true
                    leftCX=clampLeftCX(x); leftPrevX=x; leftPrevY=y
                    consumed=false; inBsZone=false; stopBackspaceRepeat()
                    grp=nearestG(y); invalidate()
                } else if (x >= dx && rightId == -1 && lDown) {
                    rightId=id; rDown=true; rLX=x; rLY=y; rCX=x; rCY=y; lastDir=-1; invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> { moveL(ev); moveR(ev) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                when (ev.getPointerId(ev.actionIndex)) {
                    leftId  -> {
                        leftId=-1; lDown=false; consumed=false; inBsZone=false
                        stopBackspaceRepeat(); invalidate()
                    }
                    rightId -> {
                        if (lDown) inputConnection?.commitText(" ",1) // відпускання правого пальця = пробіл
                        rightId=-1; rDown=false; lastDir=-1; invalidate()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                leftId=-1; lDown=false; rightId=-1; rDown=false
                consumed=false; inBsZone=false; lastDir=-1
                stopBackspaceRepeat(); invalidate()
            }
        }
        return true
    }

    private fun moveL(ev: MotionEvent) {
        if (leftId == -1) return
        val idx = ev.findPointerIndex(leftId); if (idx == -1) return
        val x = ev.getX(idx); val y = ev.getY(idx)
        val stepX = x - leftPrevX
        val stepY = y - leftPrevY

        // Центр стежить за горизонтальним дрейфом пальця під час руху вгору/вниз.
        // Майже горизонтальний рух не рухає сам центр, щоб зберегти жест стирання/повтору.
        if (abs(stepY) > abs(stepX) * 0.35f) {
            leftCX = clampLeftCX(leftCX + stepX)
        }
        leftPrevX = x; leftPrevY = y

        val ng = nearestG(y)
        if (ng != grp) {
            grp=ng
            leftCX=clampLeftCX(x)
            consumed=false; inBsZone=false
            stopBackspaceRepeat()
            lastDir=-1; invalidate(); return
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
                inputConnection?.deleteSurroundingText(1,0)
                bsH.postDelayed(bsHoldRun, BS_HOLD_MS)
                invalidate()
            }
            ddx > sideR -> {
                if (lastLtr.isNotEmpty()) inputConnection?.commitText(lastLtr,1)
                consumed=true; invalidate()
            }
        }
    }

    private fun moveR(ev: MotionEvent) {
        if (rightId == -1 || !lDown) return
        val idx = ev.findPointerIndex(rightId); if (idx == -1) return
        val x = ev.getX(idx); val y = ev.getY(idx); rCX=x; rCY=y
        val dx = x-rLX; val dy = y-rLY
        if (sqrt(dx*dx+dy*dy) < gestR) { invalidate(); return }
        val dir = dirOf(dx,dy)
        if (dir == lastDir) { rLX=x; rLY=y; invalidate(); return }
        val l = letAt(grp,dir)
        when {
            l == null -> { rLX=x; rLY=y; invalidate() }
            l == LK   -> {
                langIdx=(langIdx+1) % langOrder.size
                grp=grp.coerceAtMost(langData[lang]?.lastIndex ?: 0)
                lastLtr=""; lastDir=dir; rLX=x; rLY=y; invalidate()
            }
            l == SK   -> {
                symMode = true
                symIgnoreUntilAllUp = true
                clearSymTracking()
                stopBackspaceRepeat()
                invalidate()
            }
            else      -> { inputConnection?.commitText(l,1); lastLtr=l; lastDir=dir; rLX=x; rLY=y; invalidate() }
        }
    }

    private fun dirOf(dx: Float, dy: Float): Int {
        val ax = abs(dx); val ay = abs(dy)
        // Трохи ширші сектори для вертикалі/горизонталі зменшують хибні діагоналі
        // через мікрозсуви пальця, не збільшуючи GEST_DP.
        return when {
            ax >= ay * 2f -> if (dx >= 0f) 2 else 6  // → ←
            ay >= ax * 2f -> if (dy >= 0f) 4 else 0  // ↓ ↑
            dx >= 0f && dy < 0f -> 1                // ↗
            dx >= 0f && dy >= 0f -> 3               // ↘
            dx < 0f && dy >= 0f -> 5                // ↙
            else -> 7                               // ↖
        }
    }
}