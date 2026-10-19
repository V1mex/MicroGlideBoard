package app.vimex.microglideboard

import GlideKeyboardView
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo

class KeyboardService : InputMethodService() {

    private lateinit var keyboardView: GlideKeyboardView

    override fun onCreateInputView(): View {
        keyboardView = GlideKeyboardView(this)
        return keyboardView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView.inputConnection = currentInputConnection
    }
}