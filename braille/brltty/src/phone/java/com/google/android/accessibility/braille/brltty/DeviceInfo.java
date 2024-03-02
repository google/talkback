package com.google.android.accessibility.braille.brltty;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

/** Information about a supported bonded bluetooth device. */
@AutoValue
public abstract class DeviceInfo {
  public abstract String driverCode();

  public abstract boolean connectSecurely();

  public abstract ImmutableMap<String, Integer> friendlyKeyNames();

  public static Builder builder() {
    return new AutoValue_DeviceInfo.Builder();
  }

  /** Builder for Speech device info */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setDriverCode(String driverCode);

    public abstract Builder setConnectSecurely(boolean connectSecurely);

    public abstract Builder setFriendlyKeyNames(Map<String, Integer> value);

    public abstract DeviceInfo build();
  }
}
