/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.utils.monitor;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import java.util.HashSet;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Listens for headphone state. This should only be instantiated on Android M+ (API 23) -- earlier
 * versions of Android can use static method isHeadphoneOn().
 */
public class HeadphoneStateMonitor {

  /** An interface that can be used to listen to headphone state changes. */
  public interface Listener {
    void onHeadphoneStateChanged(boolean hasHeadphones);
  }

  private final Set<Integer> mConnectedAudioDevices = new HashSet<>();
  private @Nullable AudioDeviceCallback audioDeviceCallback;
  private Context mContext;
  private @Nullable Listener listener;

  public HeadphoneStateMonitor(Context context) {
    mContext = context;
  }

  private @NonNull AudioDeviceCallback getAudioDeviceCallback() {
    if (audioDeviceCallback == null) {
      audioDeviceCallback =
          new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
              // When devices are added, increase our count of active output devices.
              for (AudioDeviceInfo device : addedDevices) {
                if (isExternalDevice(device)) {
                  mConnectedAudioDevices.add(device.getId());
                }
              }
              if (listener != null) {
                listener.onHeadphoneStateChanged(hasHeadphones());
              }
            }

            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
              // When devices are removed, decrease our count of active output devices.
              for (AudioDeviceInfo device : removedDevices) {
                if (isExternalDevice(device)) {
                  mConnectedAudioDevices.remove(device.getId());
                }
              }
              if (listener != null) {
                listener.onHeadphoneStateChanged(hasHeadphones());
              }
            }
          };
    }
    return audioDeviceCallback;
  }

  /** Initializes this HeadphoneStateMonitor to start listening to headphone state changes. */
  public void startMonitoring() {
    @Nullable AudioManager audioManager =
        (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    // Initialize the active device count.
    mConnectedAudioDevices.clear();
    if (audioManager == null) {
      return;
    }
    AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
    for (AudioDeviceInfo device : devices) {
      if (isExternalDevice(device)) {
        mConnectedAudioDevices.add(device.getId());
      }
    }
    audioManager.registerAudioDeviceCallback(
        getAudioDeviceCallback(), /* use the main thread */ null);
  }

  /** Stop listening to headphone state changes. */
  public void stopMonitoring() {
    @Nullable AudioManager audioManager =
        (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    if (audioManager == null) {
      return;
    }
    audioManager.unregisterAudioDeviceCallback(getAudioDeviceCallback());
  }

  /**
   * Whether headphones are present, i.e. if there is at least one output audio device. Provides the
   * same result at isHeadphoneOn() for Android M and above.
   */
  public boolean hasHeadphones() {
    return !mConnectedAudioDevices.isEmpty();
  }

  /**
   * Whether the device is currently connected to bluetooth or wired headphones for audio output.
   * When called on older devices this use a deprecat methods on audioManager to get the same
   * result.
   */
  @SuppressWarnings("deprecation")
  public static boolean isHeadphoneOn(Context context) {
    @Nullable AudioManager audioManager =
        (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    if (audioManager == null) {
      return false;
    }
    AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
    for (AudioDeviceInfo device : devices) {
      if (isExternalDevice(device)) {
        // While there can be more than one external audio device, finding one is enough here.
        return true;
      }
    }
    return false;
  }

  /**
   * Sets a listener which will be informed of any headphone state changes. This listener is also
   * called immediately with the current state.
   */
  public void setHeadphoneListener(@Nullable Listener listener) {
    this.listener = listener;
    if (this.listener != null) {
      this.listener.onHeadphoneStateChanged(hasHeadphones());
    }
  }

  private static boolean isExternalDevice(AudioDeviceInfo device) {
    return device.isSink()
        && (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            || device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            || device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET
            || device.getType() == AudioDeviceInfo.TYPE_AUX_LINE
            || device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            || device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET
            || device.getType() == AudioDeviceInfo.TYPE_USB_HEADSET);
  }
}
