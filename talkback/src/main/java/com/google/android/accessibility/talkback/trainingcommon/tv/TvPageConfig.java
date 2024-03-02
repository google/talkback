/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.android.accessibility.talkback.trainingcommon.tv;

import androidx.annotation.Nullable;
import com.google.android.accessibility.talkback.trainingcommon.ExternalDrawableResource;
import com.google.auto.value.AutoValue;

/** Class to represent a step of the TV tutorial. May contain only partial information. */
@AutoValue
abstract class TvPageConfig {
  abstract boolean enabled();

  @Nullable
  abstract String title();

  @Nullable
  abstract String summary();

  @Nullable
  abstract ExternalDrawableResource image();

  static Builder builder() {
    return new AutoValue_TvPageConfig.Builder().setEnabled(true);
  }

  /**
   * Returns a new {@code TvPageConfig} that is a combination of two configs where for each field
   * the value of the second argument has precedence if it is not null.
   */
  static TvPageConfig combine(
      @Nullable TvPageConfig baseConfig, @Nullable TvPageConfig additionalConfig) {
    if (baseConfig == null || additionalConfig == null) {
      throw new IllegalArgumentException("Neither config must be null.");
    }
    Builder builder = builder();
    builder.setEnabled(additionalConfig.enabled());
    builder.setTitle(
        additionalConfig.title() != null ? additionalConfig.title() : baseConfig.title());
    builder.setSummary(
        additionalConfig.summary() != null ? additionalConfig.summary() : baseConfig.summary());
    builder.setImage(
        additionalConfig.image() != null ? additionalConfig.image() : baseConfig.image());
    return builder.build();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract TvPageConfig.Builder setEnabled(boolean value);

    abstract TvPageConfig.Builder setTitle(@Nullable String value);

    abstract TvPageConfig.Builder setSummary(@Nullable String value);

    abstract TvPageConfig.Builder setImage(@Nullable ExternalDrawableResource image);

    abstract TvPageConfig build();
  }
}
