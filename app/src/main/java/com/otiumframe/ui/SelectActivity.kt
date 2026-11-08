package com.otiumframe.ui

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import com.otiumframe.R
import com.otiumframe.media.BitmapLoader
import com.otiumframe.media.MediaItem
import com.otiumframe.media.MediaRepository
import java.util.concurrent.Executors

/** 올려 둔 사진·영상 중 슬라이드쇼에 넣을 것을 체크한다. 체크한 것만 재생된다. */
class SelectActivity : Activity() {

    private lateinit var repository: MediaRepository
    private lateinit var grid: GridView
    private lateinit var count: TextView
    private lateinit var empty: TextView
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val thumbs = HashMap<String, Bitmap?>()
    private var items: List<MediaItem> = emptyList()
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select)
        title = getString(R.string.select_title)
        repository = MediaRepository(this)
        grid = findViewById(R.id.grid)
        count = findViewById(R.id.select_count)
        empty = findViewById(R.id.select_empty)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            val item = items[position]
            repository.setSelected(item, !repository.isSelected(item))
            render()
        }
        findViewById<Button>(R.id.select_all).setOnClickListener { repository.selectAll(true); render() }
        findViewById<Button>(R.id.select_none).setOnClickListener { repository.selectAll(false); render() }
        findViewById<Button>(R.id.select_delete).setOnClickListener {
            repository.delete(items.filter { repository.isSelected(it) })
            reload()
        }
        findViewById<Button>(R.id.select_back).setOnClickListener { finish() }
        reload()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    private fun reload() {
        items = repository.all()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        render()
    }

    private fun render() {
        count.text = getString(R.string.select_count, repository.selectedCount(), items.size)
        adapter.notifyDataSetChanged()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.item_media, parent, false)
            val item = items[position]
            val image = view.findViewById<ImageView>(R.id.thumb)
            val check = view.findViewById<TextView>(R.id.check)
            val badge = view.findViewById<TextView>(R.id.badge)
            val selected = repository.isSelected(item)
            check.visibility = if (selected) View.VISIBLE else View.GONE
            image.alpha = if (selected) 1f else 0.45f
            badge.visibility = if (item is MediaItem.Photo) View.GONE else View.VISIBLE
            badge.text = if (item is MediaItem.Video) getString(R.string.select_badge_video) else getString(R.string.select_badge_gif)
            view.tag = item.key
            val cached = thumbs[item.key]
            image.setImageBitmap(cached)
            if (cached == null && !thumbs.containsKey(item.key)) {
                thumbs[item.key] = null
                io.execute {
                    val bitmap = when (item) {
                        is MediaItem.Video -> BitmapLoader.loadVideoFrame(this@SelectActivity, item.uri)
                        else -> BitmapLoader.loadPhoto(this@SelectActivity, item.uri, THUMB_PX, THUMB_PX)
                    }
                    main.post {
                        thumbs[item.key] = bitmap
                        if (view.tag == item.key) image.setImageBitmap(bitmap)
                    }
                }
            }
            return view
        }
    }

    companion object {
        private const val THUMB_PX = 320
    }
}
