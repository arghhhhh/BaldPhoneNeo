// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.ui.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.EditorInfo

import app.baldphone.neo.R
import app.baldphone.neo.databinding.DialogRenameBinding

/**
 * Shows a dialog that lets the user type a new label.
 *
 * [onResult] receives the trimmed new label, or `null` when the user chose to restore the
 * original name (only offered when [isCustomized] is true). Blank input is treated as a reset.
 */
fun Context.showRenameDialog(
    currentLabel: CharSequence?,
    isCustomized: Boolean,
    onResult: (newLabel: String?) -> Unit
) {
    val binding = DialogRenameBinding.inflate(LayoutInflater.from(this))
    val editText = binding.etRename
    editText.setText(currentLabel)
    editText.setSelection(editText.text?.length ?: 0)

    fun submit() = onResult(editText.text?.toString()?.trim()?.ifEmpty { null })

    val builder =
        BaldDialog
            .Builder(this)
            .setTitle(R.string.rename)
            .setCustomView(binding.root)
            .setPositiveButton(R.string.save) { submit() }
            .setNegativeButton(R.string.cancel, null)

    if (isCustomized) {
        builder.setNeutralButton(R.string.rename_reset) { onResult(null) }
    }

    val dialog = builder.create()

    editText.setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            submit()
            dialog.dismiss()
            true
        } else {
            false
        }
    }

    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    dialog.show()
    editText.requestFocus()
}
