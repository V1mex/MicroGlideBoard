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
        if (::keyboardView.isInitialized) {
            keyboardView.reloadSettings()
            keyboardView.inputConnection = currentInputConnection
            keyboardView.onEditorSelectionChanged()
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        if (::keyboardView.isInitialized) {
            keyboardView.inputConnection = currentInputConnection
            keyboardView.onEditorSelectionChanged()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
    }
}
