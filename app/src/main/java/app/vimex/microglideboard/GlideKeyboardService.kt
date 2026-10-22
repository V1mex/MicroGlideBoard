package app.vimex.microglideboard

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo

class GlideKeyboardService : InputMethodService() {
    private lateinit var keyboardView: GlideKeyboardView

    companion object {
        @Volatile private var activeKeyboardView: GlideKeyboardView? = null

        /** Explicitly apply numeric changes immediately after the settings screen saves them. */
        fun reloadActiveKeyboardSettings() {
            activeKeyboardView?.reloadSettings()
        }
    }

    override fun onCreateInputView(): View {
        keyboardView = GlideKeyboardView(this).apply {
            onSystemBarAppearanceChanged = { updateNavigationBarAppearance() }
        }
        activeKeyboardView = keyboardView
        updateNavigationBarAppearance()
        return keyboardView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView.editorInfo = info
        keyboardView.reloadSettings()
        keyboardView.inputConnection = currentInputConnection
        keyboardView.onEditorSelectionChanged()
        updateNavigationBarAppearance()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (::keyboardView.isInitialized) keyboardView.onEditorSelectionChanged()
    }

    override fun onDestroy() {
        if (::keyboardView.isInitialized && activeKeyboardView === keyboardView) {
            activeKeyboardView = null
        }
        super.onDestroy()
    }

    private fun updateNavigationBarAppearance() {
        if (!::keyboardView.isInitialized) return
        val imeWindow = window?.window ?: return
        val decor = imeWindow.decorView
        // Let the custom view paint beneath the transparent navigation bar. The view
        // reserves the real inset in its measure, so the keys keep their configured height.
        decor.systemUiVisibility = decor.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        imeWindow.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            imeWindow.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            imeWindow.insetsController?.setSystemBarsAppearance(
                if (keyboardView.bottomSystemBarUsesLightIcons()) WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        }
        keyboardView.requestLayout()
    }
}
