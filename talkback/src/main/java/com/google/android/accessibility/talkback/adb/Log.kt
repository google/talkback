package com.google.android.accessibility.talkback.adb

object Log {
    operator fun invoke(text: String) {
        android.util.Log.d("TB4Dev", text)
    }
}