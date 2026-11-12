package app.swtv.home.web

import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import app.swtv.home.R
import app.swtv.launcher.ui.HudDialogs
import app.swtv.theme.LauncherConfig

class WebsiteEditor(private val activity: Activity, private val config: LauncherConfig,
    private val work: WebsiteWork) {
    private var dialog: AlertDialog? = null
    fun dismiss() { dialog?.dismiss(); dialog = null }
    fun edit(title: String = "", address: String = "", id: String? = null,
        finished: () -> Unit = {}, cancelled: () -> Unit = {}) {
        dismiss()
        val fields = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val titleField = EditText(activity).apply {
            this.id = R.id.website_title; hint = "Title (optional)"; setText(title)
            filters = arrayOf(InputFilter.LengthFilter(WebsiteFavorites.MAX_TITLE_LENGTH))
            inputType = InputType.TYPE_CLASS_TEXT; isSingleLine = true
        }
        val addressField = EditText(activity).apply {
            this.id = R.id.website_address; hint = "HTTPS address or hostname"; setText(address)
            filters = arrayOf(InputFilter.LengthFilter(WebsiteUrl.MAX_LENGTH))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; isSingleLine = true
        }
        val error = TextView(activity).apply { this.id = R.id.website_error }
        fields.addView(titleField); fields.addView(addressField); fields.addView(error)
        val form = HudDialogs.builder(activity, config).setTitle(if (id == null) "Add website" else "Edit website")
            .setView(fields).setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { _, _ -> cancelled() }.create()
        dialog = form
        form.setOnCancelListener { cancelled() }
        form.setOnShowListener {
            HudDialogs.style(form, config)
            form.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                this.id = R.id.website_save
                setOnClickListener {
                    isEnabled = false
                    val command = WebsiteWork.Command.Save(titleField.text.toString(), addressField.text.toString(), id)
                    work.submit(command) { result ->
                        if (dialog !== form || !form.isShowing) return@submit
                        isEnabled = true
                        when (result) {
                            is WebsiteResult.Success -> { dismiss(); finished() }
                            is WebsiteResult.Failure -> error.text = message(result.error)
                        }
                    }
                }
            }
        }
        form.show()
    }
    fun menu(favorite: WebsiteFavorite, changed: () -> Unit) {
        dismiss()
        dialog = HudDialogs.builder(activity, config).setTitle(favorite.title)
            .setItems(arrayOf("Edit / rename", "Move earlier", "Move later", "Remove")) { _, index ->
                if (index == 0) edit(favorite.title, favorite.normalizedUrl, favorite.id, changed)
                else work.submit(when (index) {
                    1 -> WebsiteWork.Command.Move(favorite.id, -1)
                    2 -> WebsiteWork.Command.Move(favorite.id, 1)
                    else -> WebsiteWork.Command.Remove(favorite.id)
                }) { result ->
                    if (result is WebsiteResult.Success) changed()
                    else if (result is WebsiteResult.Failure) {
                        dialog = HudDialogs.builder(activity, config).setTitle("Website not changed")
                            .setMessage(message(result.error)).setPositiveButton("OK", null).show()
                    }
                }
            }.show()
    }
    companion object {
        fun message(error: WebsiteError): String = when (error) {
            WebsiteError.INVALID_ADDRESS -> "Enter a valid HTTPS address without credentials."
            WebsiteError.INVALID_TITLE -> "Use a title of at most 100 characters without control characters."
            WebsiteError.CAPACITY -> "All 32 website slots are used. Remove a website first."
            WebsiteError.DUPLICATE_EDIT -> "Another website already uses this address."
            WebsiteError.NOT_FOUND -> "This website was removed. Return home and refresh."
            WebsiteError.UNREADABLE_DATA -> "Saved websites cannot be read. No changes were made."
            WebsiteError.UNKNOWN_SCHEMA -> "These websites need a newer SWTV version."
            WebsiteError.PERSISTENCE_FAILED -> "Could not confirm saving. Please try Save again."
        }
    }
}
