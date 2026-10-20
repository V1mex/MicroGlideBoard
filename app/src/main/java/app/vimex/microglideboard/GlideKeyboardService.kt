package app.vimex.microglideboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo

class GlideKeyboardService : InputMethodService() {

    private lateinit var keyboardView: GlideKeyboardView

    override fun onCreateInputView(): View {
        keyboardView = GlideKeyboardView(this)
        return keyboardView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView.reloadSettings()
        keyboardView.inputConnection = currentInputConnection
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
    }
}
