package app.swtv.launcher.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListAdapter
import android.widget.TextView
import app.swtv.theme.LauncherConfig

/** Styling is scoped to our dialogs. Android Settings, pickers and installer remain untouched. */
object HudDialogs {
    fun builder(activity: Activity, config: LauncherConfig): AlertDialog.Builder =
        object : AlertDialog.Builder(activity, if (HudTokens.isLight(config))
            android.R.style.Theme_Material_Light_Dialog_Alert else android.R.style.Theme_Material_Dialog_Alert) {
            override fun setTitle(title: CharSequence?): AlertDialog.Builder = apply {
                setCustomTitle(TextView(context).apply {
                    id = android.R.id.title
                    text = title
                    textSize = 22f
                    isAccessibilityHeading = true
                    val inset = HudTokens.dp(context, 24)
                    setPadding(inset, inset, inset, HudTokens.dp(context, 16))
                })
            }
            override fun create(): AlertDialog = super.create().apply {
                setOnShowListener { style(this, config) }
            }
        }

    /** Can be reapplied after dynamic custom content changes; preserves listeners and selection. */
    fun style(dialog: AlertDialog, config: LauncherConfig) {
        val palette = HudTokens.palette(config)
        dialog.window?.decorView?.let { content(it, config) }
        dialog.window?.setBackgroundDrawable(HudPanel(dialog.context, palette, raised = true))
        dialog.listView?.let { list ->
            val adapter = list.adapter
            if (adapter != null && adapter !is HudListAdapter) {
                val selection = list.selectedItemPosition
                list.adapter = HudListAdapter(adapter, config)
                if (selection >= 0) list.setSelection(selection)
            }
            list.selector = ColorDrawable(Color.TRANSPARENT)
            list.divider = ColorDrawable(palette.edge)
            list.dividerHeight = HudTokens.dp(list.context, 1)
            list.setPadding(HudTokens.dp(list.context, 8), 0, HudTokens.dp(list.context, 8), 0)
        }
    }

    private fun content(view: View, config: LauncherConfig) {
        val palette = HudTokens.palette(config)
        when (view) {
            is Button -> HudControls.button(view, config, scale = false)
            is EditText -> {
                view.typeface = HudTokens.body
                view.setTextColor(palette.text)
                view.setHintTextColor(palette.secondary)
                view.backgroundTintList = ColorStateList.valueOf(palette.cyan)
                val padding = HudTokens.dp(view.context, 16)
                view.setPadding(padding, padding, padding, padding)
            }
            is TextView -> {
                view.typeface = if (view.isAccessibilityHeading) HudTokens.heading else HudTokens.body
                view.setTextColor(palette.text)
            }
            is ViewGroup -> {
                // Remove platform panel surfaces so the shared opaque window surface shows through.
                view.background = null
                for (index in 0 until view.childCount) content(view.getChildAt(index), config)
            }
        }
    }

    private class HudListAdapter(private val delegate: ListAdapter, private val config: LauncherConfig) : BaseAdapter() {
        override fun getCount() = delegate.count
        override fun getItem(position: Int): Any? = delegate.getItem(position)
        override fun getItemId(position: Int) = delegate.getItemId(position)
        override fun hasStableIds() = delegate.hasStableIds()
        override fun areAllItemsEnabled() = delegate.areAllItemsEnabled()
        override fun isEnabled(position: Int) = delegate.isEnabled(position)
        override fun getViewTypeCount() = delegate.viewTypeCount
        override fun getItemViewType(position: Int) = delegate.getItemViewType(position)
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            delegate.getView(position, convertView, parent).apply {
                content(this, config)
                isEnabled = delegate.isEnabled(position)
                background = HudPanel(context, HudTokens.palette(config), raised = true)
                minimumHeight = HudTokens.dp(context, 48)
                val inset = HudTokens.dp(context, 16)
                setPadding(inset, HudTokens.dp(context, 12), inset, HudTokens.dp(context, 12))
                if (this is TextView) {
                    setTextColor(HudTokens.palette(config).controlText())
                    textSize = 17f
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
            }
    }
}
