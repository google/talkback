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

import android.graphics.Region;
import javax.annotation.Generated;

// This file is normally auto-generated using the @AutoValue processor.  But
// that operation has been failing on the gradle-based build, so this file is
// committed into version control for now.
@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_PassThroughMode extends Feedback.PassThroughMode {

  private final Feedback.PassThroughMode.Action action;

  private final Region region;

  AutoValue_Feedback_PassThroughMode(
      Feedback.PassThroughMode.Action action,
      Region region) {
    if (action == null) {
      throw new NullPointerException("Null action");
    }
    this.action = action;
    if (region == null) {
      throw new NullPointerException("Null region");
    }
    this.region = region;
  }

  @Override
  public Feedback.PassThroughMode.Action action() {
    return action;
  }

  @Override
  public Region region() {
    return region;
  }

  @Override
  public String toString() {
    return "PassThroughMode{"
        + "action=" + action + ", "
        + "region=" + region
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.PassThroughMode) {
      Feedback.PassThroughMode that = (Feedback.PassThroughMode) o;
      return this.action.equals(that.action())
          && this.region.equals(that.region());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= action.hashCode();
    h$ *= 1000003;
    h$ ^= region.hashCode();
    return h$;
  }

}
