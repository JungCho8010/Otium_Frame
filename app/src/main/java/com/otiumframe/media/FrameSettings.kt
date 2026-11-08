package com.otiumframe.media

import android.content.Context

/** 사용자 설정. 네트워크 없이도 동작하도록 모두 기기 로컬(SharedPreferences)에 둔다. */
class FrameSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("otium_frame", Context.MODE_PRIVATE)

    var slideIntervalSec: Int
        get() = prefs.getInt(KEY_INTERVAL, 10)
        set(value) = prefs.edit().putInt(KEY_INTERVAL, value.coerceIn(MIN_INTERVAL, MAX_INTERVAL)).apply()

    var ambientSync: Boolean
        get() = prefs.getBoolean(KEY_AMBIENT, true)
        set(value) = prefs.edit().putBoolean(KEY_AMBIENT, value).apply()

    /** '화면 자동 전환' 토글. 켜져 있을 때만 P기어 감지 서비스가 돈다. 기본값은 꺼짐. */
    var autoLaunch: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LAUNCH, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LAUNCH, value).apply()

    var showClock: Boolean
        get() = prefs.getBoolean(KEY_CLOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_CLOCK, value).apply()

    /** 디버그 빌드 전용: QR 에 담을 주소 덮어쓰기(에뮬레이터 시연용, "host:port"). 빈 문자열이면 사용 안 함. */
    var uploadHost: String
        get() = prefs.getString(KEY_UPLOAD_HOST, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_UPLOAD_HOST, value).apply()

    /** 슬라이드쇼에 넣기로 체크한 파일명(files/uploads 안). */
    var selectedFiles: Set<String>
        get() = prefs.getStringSet(KEY_SELECTED, emptySet()).orEmpty().toSet()
        set(value) = prefs.edit().putStringSet(KEY_SELECTED, value.toSet()).apply()

    companion object {
        const val MIN_INTERVAL = 5
        const val MAX_INTERVAL = 60
        private const val KEY_INTERVAL = "slide_interval_sec"
        private const val KEY_AMBIENT = "ambient_sync"
        private const val KEY_AUTO_LAUNCH = "auto_launch"
        private const val KEY_CLOCK = "show_clock"
        private const val KEY_SELECTED = "selected_files"
        private const val KEY_UPLOAD_HOST = "upload_host"
    }
}
