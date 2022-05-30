/*
 * Copyright (C) 2019 Google Inc.
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
package com.google.android.accessibility.talkback;

import android.content.res.Configuration;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

// This file is normally auto-generated using the @AutoValue processor.
// But that operation has been failing on the gradle-based build, so this file is committed
// into version control for now.
@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_DeviceInfo extends Feedback.DeviceInfo {

  private final Feedback.DeviceInfo.Action action;

  private final @Nullable Configuration configuration;

  AutoValue_Feedback_DeviceInfo(
      Feedback.DeviceInfo.Action action,
      @Nullable Configuration configuration) {
    if (action == null) {
      throw new NullPointerException("Null action");
    }
    this.action = action;
    this.configuration = configuration;
  }

  @Override
  public Feedback.DeviceInfo.Action action() {
    return action;
  }

  @Override
  public @Nullable Configuration configuration() {
    return configuration;
  }

  @Override
  public String toString() {
    return "DeviceInfo{"
        + "action=" + action + ", "
        + "configuration=" + configuration
        + "}";
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.DeviceInfo) {
      Feedback.DeviceInfo that = (Feedback.DeviceInfo) o;
      return this.action.equals(that.action())
          && (this.configuration == null ? that.configuration() == null : this.configuration.equals(that.configuration()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= action.hashCode();
    h$ *= 1000003;
    h$ ^= (configuration == null) ? 0 : configuration.hashCode();
    return h$;
  }

}
