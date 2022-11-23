package com.google.android.accessibility.braille.brltty;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.google.android.accessibility.braille.translate.liblouis.TranslateUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.apps.common.proguard.UsedByNative;
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
    public Encoder createEncoder(
        Context context, Callback callback, String deviceName, String macAddress) {
      return new BrlttyEncoder(context, callback, deviceName, macAddress);
    }

    @Override
    public Predicate<String> getDeviceNameFilter() {
      return deviceName -> SupportedDevicesHelper.getDeviceInfo(deviceName) != null;
    }
  }

  private enum FileState {
    FILES_ERROR,
    FILES_NOT_EXTRACTED,
    FILES_EXTRACTED,
  }

  private static final String BRLTTY_BLUETOOTH_TAG = "bluetooth:";
  // Brltty expects an address in this format. We send a dummy if we can not get the address
  // correctly.
  private static final String FAKE_BLUETOOTH_ADDRESS = BRLTTY_BLUETOOTH_TAG + "AA:AA:AA:AA:AA";
  private static final float START_TIMEOUT_FACTOR = 2f;
  private final Callback callback;
  private final DeviceInfo deviceInfo;
  private final Context context;
  private FileState dataFileState = FileState.FILES_NOT_EXTRACTED;
  private final File tablesDir;
  private final String bluetoothMacAddress;

  public BrlttyEncoder(Context context, Callback callback, String deviceName, String address) {
    this.context = context;
    this.callback = callback;
    this.deviceInfo = SupportedDevicesHelper.getDeviceInfo(deviceName);
    // Extract tables to device storage so we can read tables before device is unlocked after
    // reboot.
    if (BuildVersionUtils.isAtLeastN()) {
      context = ContextCompat.createDeviceProtectedStorageContext(context);
    }
    tablesDir = context.getDir("keytables", Context.MODE_PRIVATE);
    tablesDirPath = tablesDir.getPath();
    if (address != null) {
      bluetoothMacAddress = BRLTTY_BLUETOOTH_TAG + address;
    } else {
      bluetoothMacAddress = FAKE_BLUETOOTH_ADDRESS;
    }
    initNative();
  }

  @Override
  public Optional<BrailleDisplayProperties> start() {
    ensureDataFiles();
    long momentStart = SystemClock.elapsedRealtime();
    boolean success =
        startNative(deviceInfo.driverCode(), bluetoothMacAddress, START_TIMEOUT_FACTOR);
    long elapsed = SystemClock.elapsedRealtime() - momentStart;
    Log.d(
        "BrlttyEncoder",
        "brltty start took " + elapsed + " ms, driver: " + deviceInfo.driverCode());

    if (success) {
      BrailleKeyBinding[] keyBindings = getFilteredKeyMap();
      return Optional.of(
          new BrailleDisplayProperties(
              deviceInfo.driverCode(),
              getTextCellsNative(),
              getStatusCellsNative(),
              keyBindings,
              getFriendlyKeyNames(keyBindings)));
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

  private BrailleKeyBinding[] getFilteredKeyMap() {
    BrailleKeyBinding[] fullKeyMap = getKeyMapNative();
    List<BrailleKeyBinding> arrayList = new ArrayList<>();
    for (BrailleKeyBinding binding : fullKeyMap) {
      if (hasAllFriendlyKeyNames(binding)) {
        arrayList.add(binding);
      }
    }
    return arrayList.toArray(new BrailleKeyBinding[0]);
  }

  private boolean hasAllFriendlyKeyNames(BrailleKeyBinding binding) {
    Map<String, Integer> friendlyNames = deviceInfo.friendlyKeyNames();
    for (String key : binding.getKeyNames()) {
      if (!friendlyNames.containsKey(key)) {
        return false;
      }
    }
    return true;
  }

  private Map<String, String> getFriendlyKeyNames(BrailleKeyBinding[] bindings) {
    Map<String, String> result = new HashMap<>();
    Map<String, Integer> friendlyNames = deviceInfo.friendlyKeyNames();
    for (BrailleKeyBinding binding : bindings) {
      for (String key : binding.getKeyNames()) {
        Integer resId = friendlyNames.get(key);
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
  private void readDelayed(long delayMillis) {
    callback.readAfterDelay((int) delayMillis);
  }

  // Native methods

  private native boolean initNative();

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
    System.loadLibrary("brlttywrap");
    classInitNative();
  }
}
