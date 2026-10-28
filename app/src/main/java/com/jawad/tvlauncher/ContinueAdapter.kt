package com.jawad.tvlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class ContinueItem(
    val title: String,
    val posterUri: String?,
    val intentUri: String?,
    val packageName: String?,
    val fallbackDrawable: Drawable? = null
)

class ContinueAdapter(private val onOpen: (ContinueItem) -> Unit) :
    RecyclerView.Adapter<ContinueAdapter.Holder>() {

    private val items = ArrayList<ContinueItem>()
    private val cache = HashMap<String, Bitmap>()
    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun submit(list: List<ContinueItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val tile: View = view.findViewById(R.id.cwTile)
        val image: ImageView = view.findViewById(R.id.cwImage)
        val title: TextView = view.findViewById(R.id.cwTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_continue, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.image.setImageDrawable(item.fallbackDrawable)
        holder.image.scaleType = if (item.fallbackDrawable != null && item.posterUri == null)
            ImageView.ScaleType.CENTER_INSIDE else ImageView.ScaleType.CENTER_CROP

        val uri = item.posterUri
        if (uri != null) {
            val cached = cache[uri]
            if (cached != null) {
                holder.image.setImageBitmap(cached)
            } else {
                holder.image.tag = uri
                val ctx = holder.itemView.context.applicationContext
                executor.execute {
                    val bmp = loadBitmap(ctx, uri)
                    if (bmp != null) {
                        cache[uri] = bmp
                        main.post { if (holder.image.tag == uri) holder.image.setImageBitmap(bmp) }
                    }
                }
            }
        }

        holder.tile.setOnClickListener { onOpen(item) }
        holder.tile.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.08f else 1.0f
            v.animate().scaleX(scale).scaleY(scale).setDuration(140).start()
        }
    }

    private fun loadBitmap(ctx: Context, uri: String): Bitmap? = try {
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        if (uri.startsWith("http")) {
            val conn = URL(uri).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.inputStream.use { BitmapFactory.decodeStream(it, null, opts) }
        } else {
            ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Reads the Android TV "Watch Next" row. Returns an empty list if not permitted or empty. */
        fun loadWatchNext(ctx: Context): List<ContinueItem> {
            val out = ArrayList<ContinueItem>()
            try {
                val cursor = ctx.contentResolver.query(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    WatchNextProgram.PROJECTION, null, null,
                    TvContractCompat.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC"
                ) ?: return out
                cursor.use {
                    while (it.moveToNext() && out.size < 12) {
                        val p = WatchNextProgram.fromCursor(it)
                        val title = p.title ?: continue
                        val poster = p.posterArtUri?.toString() ?: p.thumbnailUri?.toString()
                        out.add(ContinueItem(title, poster, p.intentUri?.toString(), p.packageName))
                    }
                }
            } catch (e: Exception) {
                // no permission / provider missing
            }
            return out
        }

        /** Fallback: apps launched most recently from this launcher. */
        fun loadRecent(ctx: Context, packages: List<String>): List<ContinueItem> {
            val pm = ctx.packageManager
            val out = ArrayList<ContinueItem>()
            for (pkg in packages) {
                try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(info).toString()
                    val banner = try { info.loadBanner(pm) } catch (e: Exception) { null }
                    out.add(ContinueItem(label, null, null, pkg, banner ?: pm.getApplicationIcon(info)))
                } catch (e: PackageManager.NameNotFoundException) {
                }
                if (out.size >= 8) break
            }
            return out
        }

        fun openIntent(ctx: Context, item: ContinueItem): Intent? {
            item.intentUri?.let {
                try {
                    return Intent.parseUri(it, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                } catch (e: Exception) {
                }
            }
            item.packageName?.let { pkg ->
                val pm = ctx.packageManager
                return (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return null
        }
    }
}
