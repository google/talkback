package com.google.android.accessibility.braille.brltty.device;

/** Provides generic parameters that tell BRLTTY to connect using hid_android.c. */
public class BrlttyHidParameterProvider extends ParameterProvider {
  private static final String TAG_HID = "hid:";

  public BrlttyHidParameterProvider() {}

  @Override
  public String getParameters() {
    // In BRLTTY, the name is a mandatory parameter to identify a device for HID driver. However,
    // the value is not important anymore since we don't have a HID device filter to check whether
    // the device supports hid or not. (We don't have a list yet. We use the new API to check if a
    // device supports HID. If the API succeeds, the device supports HID)
    return TAG_HID + DELIMITER + "name=unused";
  }
}
