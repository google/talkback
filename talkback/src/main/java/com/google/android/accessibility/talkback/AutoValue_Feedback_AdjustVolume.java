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

import javax.annotation.Generated;

// This file is normally auto-generated using the @AutoValue processor.  But
// that operation has been failing on the gradle-based build, so this file is
// committed into version control for now.
@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_AdjustVolume extends Feedback.AdjustVolume {

  private final Feedback.AdjustVolume.Action action;

  private final Feedback.AdjustVolume.StreamType streamType;

  AutoValue_Feedback_AdjustVolume(
      Feedback.AdjustVolume.Action action,
      Feedback.AdjustVolume.StreamType streamType) {
    if (action == null) {
      throw new NullPointerException("Null action");
    }
    this.action = action;
    if (streamType == null) {
      throw new NullPointerException("Null streamType");
    }
    this.streamType = streamType;
  }

  @Override
  public Feedback.AdjustVolume.Action action() {
    return action;
  }

  @Override
  public Feedback.AdjustVolume.StreamType streamType() {
    return streamType;
  }

  @Override
  public String toString() {
    return "AdjustVolume{"
        + "action=" + action + ", "
        + "streamType=" + streamType
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.AdjustVolume) {
      Feedback.AdjustVolume that = (Feedback.AdjustVolume) o;
      return this.action.equals(that.action())
          && this.streamType.equals(that.streamType());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= action.hashCode();
    h$ *= 1000003;
    h$ ^= streamType.hashCode();
    return h$;
  }

}