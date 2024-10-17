/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.braille.brltty;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import com.google.android.accessibility.braille.translate.liblouis.TranslateUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.apps.common.proguard.UsedByNative;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** Handles the encoding of braille display packets by delegating to BRLTTY. */
public class BrlttyEncoder implements Encoder {

  /** A factory for Brltty. */
  public static class BrlttyFactory implements Factory {

    @Override
    public Encoder createEncoder(Context context, Callback callback) {
      return new BrlttyEncoder(context, callback);
    }

    @Override
    public Predicate<String> getDeviceNameFilter() {
      return deviceName ->
          SupportedDevicesHelper.getDeviceInfo(deviceName, /* useHid= */ false) != null;
    }
  }

  private enum FileState {
    FILES_ERROR,
    FILES_NOT_EXTRACTED,
    FILES_EXTRACTED,
  }

  private static final String TAG = "BrlttyEncoder";
  private static final float START_TIMEOUT_FACTOR = 2f;
  private final Callback callback;
  private final Context context;
  private final File tablesDir;
  private FileState dataFileState = FileState.FILES_NOT_EXTRACTED;

  public BrlttyEncoder(Context context, Callback callback) {
    this.context = context;
    this.callback = callback;
    // Extract tables to device storage so we can read tables before device is unlocked after
    // reboot.
    if (BuildVersionUtils.isAtLeastN()) {
      context = ContextCompat.createDeviceProtectedStorageContext(context);
    }
    tablesDir = context.getDir("keytables", Context.MODE_PRIVATE);
    tablesDirPath = tablesDir.getPath();
  }

  @Override
  public Optional<BrailleDisplayProperties> start(
      String deviceName, boolean useHid, String parameters) {
    ensureDataFiles();
    long momentStart = SystemClock.elapsedRealtime();
    DeviceInfo deviceInfo = SupportedDevicesHelper.getDeviceInfo(deviceName, useHid);
    boolean result = initNative(context);
    if (!result) {
      Log.d(TAG, "init result failed");
      return Optional.empty();
    }
    final String driverCode = deviceInfo.driverCode();
    boolean success = startNative(driverCode, parameters, START_TIMEOUT_FACTOR);
    long elapsed = SystemClock.elapsedRealtime() - momentStart;
    Log.d(TAG, "brltty start took " + elapsed + " ms, driver: " + driverCode);

    if (success) {
      BrailleKeyBinding[] keyBindings = getFilteredKeyMap(deviceInfo.friendlyKeyNames());
      return Optional.of(
          new BrailleDisplayProperties(
              driverCode,
              getTextCellsNative(),
              getStatusCellsNative(),
              keyBindings,
              getFriendlyKeyNames(keyBindings, deviceInfo.friendlyKeyNames())));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public void stop() {
    stopNative();
  }

  @Override
  public void consumePacketFromDevice(byte[] packet) {
    try {
      addBytesFromDeviceNative(packet, packet.length);
    } catch (IOException e) {
      // Do nothing.
    }
  }

  @Override
  public void writeBrailleDots(byte[] brailleDotBytes) {
    writeWindowNative(brailleDotBytes);
  }

  @Override
  public int readCommand() {
    return readCommandNative();
  }

  private void ensureDataFiles() {
    if (dataFileState != FileState.FILES_NOT_EXTRACTED) {
      return;
    }
    // TODO: When the zip file is larger than a few kilobytes, detect if
    // the data was already extracted and don't do this every time the
    // service starts.
    if (TranslateUtils.extractTables(context.getResources(), R.raw.keytables, tablesDir)) {
      dataFileState = FileState.FILES_EXTRACTED;
    } else {
      dataFileState = FileState.FILES_ERROR;
    }
  }

  private BrailleKeyBinding[] getFilteredKeyMap(ImmutableMap<String, Integer> friendlyKeyNames) {
    BrailleKeyBinding[] fullKeyMap = getKeyMapNative();
    List<BrailleKeyBinding> arrayList = new ArrayList<>();
    for (BrailleKeyBinding binding : fullKeyMap) {
      if (hasAllFriendlyKeyNames(binding, friendlyKeyNames)) {
        arrayList.add(binding);
      }
    }
    return arrayList.toArray(new BrailleKeyBinding[0]);
  }

  private boolean hasAllFriendlyKeyNames(
      BrailleKeyBinding binding, ImmutableMap<String, Integer> friendlyKeyNames) {
    for (String key : binding.getKeyNames()) {
      if (!friendlyKeyNames.containsKey(key)) {
        return false;
      }
    }
    return true;
  }

  private Map<String, String> getFriendlyKeyNames(
      BrailleKeyBinding[] bindings, ImmutableMap<String, Integer> friendlyKeyNames) {
    Map<String, String> result = new HashMap<>();
    for (BrailleKeyBinding binding : bindings) {
      for (String key : binding.getKeyNames()) {
        Integer resId = friendlyKeyNames.get(key);
        if (resId != null) {
          result.put(key, context.getString(resId));
        } else {
          result.put(key, key);
        }
      }
    }
    return result;
  }

  /** This field is accessed by native BrlttyWrapper. */
  @UsedByNative("BrlttyWrapper.c")
  private final String tablesDirPath;

  /** This method is invoked by native BrlttyWrapper. */
  @UsedByNative("BrlttyWrapper.c")
  private boolean sendBytesToDevice(byte[] command) {
    callback.sendPacketToDevice(command);
    return true;
  }

  /** This method is invoked by native BrlttyWrapper. */
  @UsedByNative("BrlttyWrapper.c")
  @VisibleForTesting
  public void readDelayed(long delayMillis) {
    callback.readAfterDelay((int) delayMillis);
  }

  // Native methods

  private native boolean initNative(Context context);

  private native boolean startNative(String driverCode, String brailleDevice, float timeoutFactor);

  private native void stopNative();

  private native boolean writeWindowNative(byte[] pattern);

  private native int readCommandNative();

  private native void addBytesFromDeviceNative(byte[] bytes, int size) throws IOException;

  private native BrailleKeyBinding[] getKeyMapNative();

  private native int getTextCellsNative();

  private native int getStatusCellsNative();

  private static native void classInitNative();

  static {
    if (!isRobolectric()) {
      System.loadLibrary("brlttywrap");
      classInitNative();
    }
  }

  private static boolean isRobolectric() {
    return "robolectric".equals(Build.FINGERPRINT);
  }
}
