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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

/** Information about a supported bonded bluetooth device. */
@AutoValue
public abstract class DeviceInfo {
  public abstract String driverCode();

  public abstract String modelName();

  public abstract boolean connectSecurely();

  public abstract ImmutableMap<String, Integer> friendlyKeyNames();

  public static Builder builder() {
    return new AutoValue_DeviceInfo.Builder();
  }

  /** Builder for Speech device info */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setDriverCode(String driverCode);

    public abstract Builder setModelName(String modelName);

    public abstract Builder setConnectSecurely(boolean connectSecurely);

    public abstract Builder setFriendlyKeyNames(Map<String, Integer> value);

    public abstract DeviceInfo build();
  }
}
