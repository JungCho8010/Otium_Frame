package com.otiumframe.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.otiumframe.R
import java.io.File

/**
 * 재생 목록. 폰에서 올린 파일(`files/uploads/`)이 전부이고, 그중 사용자가 체크한 것만 재생한다.
 * 선택은 파일명 집합으로 SharedPreferences 에 둔다. 체크한 것이 하나도 없으면 앱에 들어 있는 샘플을 보여준다.
 */
class MediaRepository(private val context: Context) {
    private val settings = FrameSettings(context)
    val uploadsDir: File get() = File(context.filesDir, "uploads")

    /** 올려 둔 모든 파일, 올린 순서(파일명 앞의 타임스탬프 순). */
    fun all(): List<MediaItem> =
        uploadsDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.mapNotNull(::itemOf).orEmpty()

    /** 슬라이드쇼에 나올 항목: 체크한 파일. 없으면 샘플. */
    fun items(): List<MediaItem> {
        val selected = settings.selectedFiles
        return all().filter { nameOf(it) in selected }.ifEmpty { samples() }
    }

    fun selectedCount(): Int = all().count { nameOf(it) in settings.selectedFiles }

    fun totalCount(): Int = all().size

    fun isSelected(item: MediaItem): Boolean = nameOf(item) in settings.selectedFiles

    fun setSelected(item: MediaItem, selected: Boolean) {
        settings.selectedFiles = if (selected) settings.selectedFiles + nameOf(item) else settings.selectedFiles - nameOf(item)
    }

    fun selectAll(selected: Boolean) {
        settings.selectedFiles = if (selected) all().map(::nameOf).toSet() else emptySet()
    }

    /** 업로드 직후: 올린 파일은 바로 재생 목록에 포함한다. */
    fun select(file: File) {
        settings.selectedFiles = settings.selectedFiles + file.name
    }

    fun delete(items: List<MediaItem>) {
        items.forEach { File(uploadsDir, nameOf(it)).delete() }
        settings.selectedFiles = settings.selectedFiles - items.map(::nameOf).toSet()
    }

    fun clear() {
        uploadsDir.listFiles()?.forEach { it.delete() }
        settings.selectedFiles = emptySet()
    }

    fun nameOf(item: MediaItem): String = item.uri.lastPathSegment.orEmpty()

    private fun itemOf(file: File): MediaItem? {
        val uri = Uri.fromFile(file)
        return when (file.extension.lowercase()) {
            "gif" -> MediaItem.Gif(uri)
            in PHOTO_EXT -> MediaItem.Photo(uri)
            in VIDEO_EXT -> MediaItem.Video(uri)
            else -> null
        }
    }

    private fun samples(): List<MediaItem> = listOf(
        MediaItem.Photo(resourceUri(R.drawable.sample_dawn)),
        MediaItem.Video(resourceUri(R.raw.sample_waves)),
        MediaItem.Photo(resourceUri(R.drawable.sample_forest)),
        MediaItem.Photo(resourceUri(R.drawable.sample_dusk)),
    )

    private fun resourceUri(id: Int): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.packageName)
        .appendPath(id.toString())
        .build()

    companion object {
        val PHOTO_EXT = setOf("jpg", "jpeg", "png", "webp", "bmp", "heic", "heif")
        val VIDEO_EXT = setOf("mp4", "webm", "3gp", "mkv", "mov")
    }
}
