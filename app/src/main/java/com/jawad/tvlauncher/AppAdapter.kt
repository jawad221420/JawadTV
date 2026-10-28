package com.jawad.tvlauncher

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class AppEntry(
    val packageName: String,
    val label: String,
    val banner: Drawable?,
    val icon: Drawable?,
    val isSettings: Boolean = false,
    var pinned: Boolean = false
)

class AppAdapter(
    private val onOpen: (AppEntry) -> Unit,
    private val onLongPress: (AppEntry) -> Unit
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    private val items = ArrayList<AppEntry>()

    fun submit(list: List<AppEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val tile: View = view.findViewById(R.id.tile)
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
        holder.label.text = if (item.pinned) "★ " + item.label else item.label

        if (item.banner != null) {
            holder.banner.visibility = View.VISIBLE
            holder.icon.visibility = View.GONE
            holder.banner.setImageDrawable(item.banner)
        } else {
            holder.banner.visibility = View.GONE
            holder.icon.visibility = View.VISIBLE
            holder.icon.setImageDrawable(item.icon)
        }

        holder.tile.setOnClickListener { onOpen(item) }
        holder.tile.setOnLongClickListener {
            onLongPress(item)
            true
        }
        holder.tile.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.08f else 1.0f
            v.animate().scaleX(scale).scaleY(scale).setDuration(140).start()
            holder.label.setTextColor(
                v.context.getColor(if (hasFocus) R.color.text_primary else R.color.text_secondary)
            )
        }
    }
}
