package com.otiumframe.media

import android.net.Uri

sealed class MediaItem {
    abstract val uri: Uri
    val key: String get() = uri.toString()

    data class Photo(override val uri: Uri) : MediaItem()

    /** 움직이는 GIF: 정지 사진과 달리 AnimatedImageDrawable 로 재생한다. */
    data class Gif(override val uri: Uri) : MediaItem()

    data class Video(override val uri: Uri) : MediaItem()
}
