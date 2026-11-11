package app.baldphone.neo.launcher.apps.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import android.widget.ImageView
import android.widget.TextView

import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

import app.baldphone.neo.R
import app.baldphone.neo.core.assisttouch.enableAssistTouch
import app.baldphone.neo.databinding.AppGridItemBinding
import app.baldphone.neo.databinding.AppListItemBinding
import app.baldphone.neo.databinding.ItemListHeaderBinding
import app.baldphone.neo.extensions.setClickableAccessibilityRole
import app.baldphone.neo.launcher.apps.AppIconBinder
import app.baldphone.neo.launcher.apps.data.db.AppEntry

import com.bald.uriah.baldphone.adapters.ModularListAdapter

/**
 * ListAdapter-based adapter for the applications list. Handles two view types:
 *  - [TYPE_HEADER]: Alphabetical section dividers ("A", "B", …).
 *  - [TYPE_ITEM]: App entries with icon, label, and pin indicator.
 */
class AppsListAdapter(
    private val onAppClick: (item: AppEntry) -> Unit,
    private val onAppLongClick: ((anchor: View, item: AppEntry) -> Unit)? = null,
    /** Grid of large icons (original BaldPhone style) instead of rows. */
    private val gridMode: Boolean = false
) : ModularListAdapter<AppListItem, RecyclerView.ViewHolder>(AppListItemDiffCallback) {
    private var selectedPosition = RecyclerView.NO_POSITION
    private var themeColors: ThemeColors? = null

    private data class ThemeColors(
        val selectedText: Int,
        val defaultText: Int,
        val selectedDrawable: Drawable?
    )

    /**
     * Clears the current selection state.
     */
    fun deselect() {
        val old = selectedPosition
        selectedPosition = RecyclerView.NO_POSITION
        if (old != RecyclerView.NO_POSITION) notifyItemChanged(old)
    }

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is AppListItem.Header -> TYPE_HEADER
            is AppListItem.App -> TYPE_ITEM
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(ItemListHeaderBinding.inflate(inflater, parent, false))
        } else {
            if (gridMode) {
                val b = AppGridItemBinding.inflate(inflater, parent, false)
                AppViewHolder(b.root, b.appIcon, b.appName, b.pin)
            } else {
                val b = AppListItemBinding.inflate(inflater, parent, false)
                AppViewHolder(b.root, b.appIcon, b.appName, b.pin)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is AppListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is AppListItem.App -> (holder as AppViewHolder).bind(item.entry, position == selectedPosition)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && holder is AppViewHolder) {
            holder.updateSelectionState(position == selectedPosition)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    class HeaderViewHolder(private val binding: ItemListHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: AppListItem.Header) {
            binding.tvLetter.text = header.letter
        }
    }

    inner class AppViewHolder(
        private val root: View,
        private val appIcon: ImageView,
        private val appName: TextView,
        private val pin: View
    ) : RecyclerView.ViewHolder(root) {
        private val colors = getThemeColors(root.context)
        private val defaultBackground = root.background

        init {
            root.setClickableAccessibilityRole()
            root.enableAssistTouch()
            root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val item = getItem(pos) as? AppListItem.App ?: return@setOnClickListener
                    onAppClick(item.entry)
                }
            }

            // In choose mode items won't show selection/haptic feedback on long-press.
            if (onAppLongClick != null) {
                root.setOnLongClickListener { anchor ->
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnLongClickListener false
                    val item = getItem(pos) as? AppListItem.App ?: return@setOnLongClickListener false

                    val oldPos = selectedPosition
                    selectedPosition = pos
                    if (oldPos != RecyclerView.NO_POSITION) notifyItemChanged(oldPos, PAYLOAD_SELECTION)
                    notifyItemChanged(pos, PAYLOAD_SELECTION)

                    onAppLongClick(anchor, item.entry)
                    true
                }
            }
        }

        fun bind(app: AppEntry, isSelected: Boolean) {
            appName.text = app.label
            AppIconBinder.loadPic(app, appIcon)
            pin.isVisible = app.isPinned
            updateSelectionState(isSelected)
        }

        fun updateSelectionState(isSelected: Boolean) {
            root.background = if (isSelected) colors.selectedDrawable else defaultBackground
            appName.setTextColor(if (isSelected) colors.selectedText else colors.defaultText)
        }
    }

    private fun getThemeColors(context: Context): ThemeColors =
        themeColors ?: ThemeColors(
            selectedText = context.resolveThemeColor(R.attr.bald_text_on_selected),
            defaultText = context.resolveThemeColor(R.attr.bald_text_on_background),
            selectedDrawable = ContextCompat.getDrawable(context, R.drawable.btn_selected)
        ).also { themeColors = it }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
        private const val PAYLOAD_SELECTION = "PAYLOAD_SELECTION"
    }
}

fun Context.resolveThemeColor(
    @AttrRes attrRes: Int
): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
}

private object AppListItemDiffCallback : DiffUtil.ItemCallback<AppListItem>() {
    override fun areItemsTheSame(oldItem: AppListItem, newItem: AppListItem): Boolean =
        when {
            oldItem is AppListItem.Header && newItem is AppListItem.Header -> {
                oldItem.letter == newItem.letter
            }

            oldItem is AppListItem.App && newItem is AppListItem.App -> {
                oldItem.entry.componentName == newItem.entry.componentName &&
                    oldItem.entry.userId == newItem.entry.userId
            }

            else -> {
                false
            }
        }

    override fun areContentsTheSame(oldItem: AppListItem, newItem: AppListItem): Boolean = (oldItem == newItem)
}
