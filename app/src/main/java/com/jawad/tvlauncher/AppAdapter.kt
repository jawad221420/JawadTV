package com.jawad.tvlauncher

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

data class AppEntry(
    val packageName: String,
    val label: String,
    val banner: Drawable?,
    val icon: Drawable?,
    val isSettings: Boolean = false
)

class AppAdapter(
    private val onOpen: (AppEntry) -> Unit,
    private val onLongPress: (Int) -> Unit
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    val items = ArrayList<AppEntry>()

    /** Index of the tile currently being moved, or -1. */
    var movingPosition = -1
        private set

    fun submit(list: List<AppEntry>) {
        items.clear()
        items.addAll(list)
        movingPosition = -1
        notifyDataSetChanged()
    }

    fun startMoving(position: Int) {
        if (position < 0 || position >= items.size || items[position].isSettings) return
        val old = movingPosition
        movingPosition = position
        if (old >= 0) notifyItemChanged(old)
        notifyItemChanged(position)
    }

    fun stopMoving() {
        val old = movingPosition
        movingPosition = -1
        if (old >= 0) notifyItemChanged(old)
    }

    /** Moves the moving tile one step; returns the new position or -1 if it can't move. */
    fun moveBy(delta: Int): Int {
        val from = movingPosition
        if (from < 0) return -1
        val to = from + delta
        if (to < 0 || to >= items.size || items[to].isSettings) return -1
        Collections.swap(items, from, to)
        movingPosition = to
        notifyItemMoved(from, to)
        notifyItemChanged(from)
        notifyItemChanged(to)
        return to
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val tile: View = view.findViewById(R.id.tile)
        val frame: View = view.findViewById(R.id.tileFrame)
        val banner: ImageView = view.findViewById(R.id.tileBanner)
        val icon: ImageView = view.findViewById(R.id.tileIcon)
        val label: TextView = view.findViewById(R.id.tileLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return Holder(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.label.text = item.label

        // Google TV style: the app icon inside a circle (banner only as a last resort)
        if (item.icon != null) {
            holder.banner.visibility = View.GONE
            holder.icon.visibility = View.VISIBLE
            holder.icon.clipToOutline = true
            holder.icon.setImageDrawable(item.icon)
        } else {
            holder.icon.visibility = View.GONE
            holder.banner.visibility = View.VISIBLE
            holder.banner.setImageDrawable(item.banner)
        }

        val moving = position == movingPosition
        holder.frame.setBackgroundResource(if (moving) R.drawable.circle_moving_bg else R.drawable.circle_bg)
        holder.label.setTextColor(
            holder.itemView.context.getColor(if (moving || holder.tile.isFocused) R.color.text_primary else R.color.text_secondary)
        )

        holder.tile.setOnClickListener { onOpen(item) }
        holder.tile.setOnLongClickListener {
            onLongPress(holder.bindingAdapterPosition)
            true
        }
        holder.tile.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.12f else 1.0f
            v.animate().scaleX(scale).scaleY(scale).setDuration(140).start()
            holder.label.setTextColor(
                v.context.getColor(if (hasFocus) R.color.text_primary else R.color.text_secondary)
            )
        }
    }
}
