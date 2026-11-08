package com.otiumframe.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import java.io.File
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.otiumframe.core.ColorExtractor

/** 화면 크기에 맞춰 줄여서 디코딩한다. 백그라운드 스레드에서 호출할 것. */
object BitmapLoader {

    fun loadPhoto(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
        }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    /** 영상의 대표 프레임. 주행 전환 시 정지 이미지와 색 추출에 쓴다. */
    fun loadVideoFrame(context: Context, uri: Uri, positionMs: Long = 1_000): Bitmap? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(positionMs * 1_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally {
            retriever.release()
        }
    }.getOrNull()

    /** 움직이는 GIF. 드로어블(AnimatedImageDrawable)과 색 추출용 첫 프레임을 함께 돌려준다. */
    fun loadGif(context: Context, uri: Uri): Pair<Drawable, Bitmap>? = runCatching {
        val source = if (uri.scheme == "file") ImageDecoder.createSource(File(uri.path!!)) else ImageDecoder.createSource(context.contentResolver, uri)
        val drawable = ImageDecoder.decodeDrawable(source)
        val first = loadPhoto(context, uri, 256, 256) ?: return null
        drawable to first
    }.getOrNull()

    fun dominantColor(bitmap: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        val pixels = IntArray(32 * 32)
        small.getPixels(pixels, 0, 32, 0, 0, 32, 32)
        if (small !== bitmap) small.recycle()
        return ColorExtractor.dominantColor(pixels)
    }

    private fun sampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var size = 1
        if (width <= 0 || height <= 0) return size
        while (width / (size * 2) >= reqWidth && height / (size * 2) >= reqHeight) size *= 2
        return size
    }
}
