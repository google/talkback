package com.google.android.accessibility.talkback.adb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.getSystemService
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.talkback.selector.SelectorController
import java.lang.ref.WeakReference

/**
 * adb shell am broadcast -a com.a11y.adb.[A11yAction] [-e mode [SelectorController.Granularity]]
 * adb shell am broadcast -a com.a11y.adb.[ToggleDeveloperSetting.DeveloperSetting]
 * adb shell am broadcast -a com.a11y.adb.[VolumeControl]
 * adb -s $PIXEL5 shell am broadcast -a com.a11y.adb.volume_max
 * adb -s $PIXEL5 shell am broadcast -a com.a11y.adb.volume_min
 *
 * EXAMPLES
 * adb shell am broadcast -a com.a11y.adb.next
 * adb shell am broadcast -a com.a11y.adb.next -e mode headings
 * adb shell am broadcast -a com.a11y.adb.perform_click_action
 * adb shell am broadcast -a com.a11y.adb.toggle_speech_output
 */

class AdbReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) {
            return
        }

        val action = intent.action?.replace("$IntentActionPrefix.", "") ?: "NONE"

        val screenInteractions = A11yAction.values().map { it.name.lowercase() }
        val developerSettings =
            ToggleDeveloperSetting.DeveloperSetting.values().map { it.name.lowercase() }
        val volumeSettings = VolumeControl.values().map { it.name.lowercase() }

        when (val ref = action.lowercase()) {
            in screenInteractions -> {
                val a11yAction = A11yAction.fromString(ref)
                if (intent.hasExtra(A11yAction.granularityParameter) && (a11yAction in listOf(
                        A11yAction.NEXT,
                        A11yAction.PREVIOUS
                    ))
                ) {
                    val granularity =
                        intent.getStringExtra(A11yAction.granularityParameter) ?: "default"
                    TalkBackService.getInstance()?.moveAtGranularity(
                        A11yAction.granularityFrom(granularity),
                        a11yAction == A11yAction.NEXT
                    )
                } else {
                    TalkBackService.getInstance()
                        ?.performGesture(context.getString(a11yAction.gestureMappingReference))
                }
            }
            in developerSettings -> {
                val devSetting = ToggleDeveloperSetting.DeveloperSetting.fromString(ref)
                ToggleDeveloperSetting(context, devSetting)
            }
            in volumeSettings -> {
                val volumeControl = VolumeControl.fromString(ref)
                if (volumeControl != VolumeControl.NONE) {
                    context.setAccessibilityVolume(volumeControl)
                }
            }
            else -> Log("INVALID ACTION: $action")
        }
    }

    private fun Context.setAccessibilityVolume(volumeControl: VolumeControl) {
        val stream = AudioManager.STREAM_ACCESSIBILITY
        val audioManager = getSystemService<AudioManager>() ?: return
        val max = audioManager.getStreamMaxVolume(stream)
        val currentVolume = audioManager.getStreamVolume(stream)

        when (volumeControl) {
            VolumeControl.VOLUME_MIN -> {
                audioManager.setStreamVolume(stream, 1, 0)
            }
            VolumeControl.VOLUME_QUARTER -> {
                audioManager.setStreamVolume(stream, (max * 0.25).toInt(), 0)
            }
            VolumeControl.VOLUME_HALF -> {
                audioManager.setStreamVolume(stream, (max * 0.5).toInt(), 0)
            }
            VolumeControl.VOLUME_THREE_QUARTER -> {
                audioManager.setStreamVolume(stream, (max * 0.75).toInt(), 0)
            }
            VolumeControl.VOLUME_MAX -> {
                audioManager.setStreamVolume(stream, max, 0)
            }
            VolumeControl.VOLUME_TOGGLE -> {
                if (currentVolume > 1) {
                    audioManager.setStreamVolume(stream, 1, 0)
                } else {
                    audioManager.setStreamVolume(stream, (max * 0.5).toInt(), 0)
                }
            }
            else -> return
        }
    }

    companion object {
        private const val DEBUG = true

        private const val IntentActionPrefix = "com.a11y.adb"

        lateinit var instance: WeakReference<AdbReceiver>

        fun Context.registerAdbReceiver() {
            Log("Receiver registered")
            instance = WeakReference(AdbReceiver())
            registerReceiver(instance.get(), IntentFilter(intentFilter))
        }

        fun Context.unregisterAdbReceiver() {
            Log("Receiver unregistered")
            unregisterReceiver(instance.get())
        }

        private val intentFilter = IntentFilter().apply {
            A11yAction.values().forEach { userAction ->
                addAction("$IntentActionPrefix.${userAction.name.lowercase()}")
            }
            ToggleDeveloperSetting.DeveloperSetting.values().forEach { setting ->
                addAction("$IntentActionPrefix.${setting.name.lowercase()}")
            }
            VolumeControl.values().forEach { setting ->
                if (setting != VolumeControl.NONE)
                    addAction("$IntentActionPrefix.${setting.name.lowercase()}")
            }
        }
    }
}