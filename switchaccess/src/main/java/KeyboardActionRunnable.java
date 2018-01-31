/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.android.accessibility.switchaccess;

/** A runnable triggered from a keyboard action. Allows for setting of action time. */
public abstract class KeyboardActionRunnable implements Runnable {
  protected long mEventTime;

  /**
   * Sets the time at which the key press that resulted in this action occured. This method should
   * always be called before calling #run.
   *
   * @param actionTime The event time of the key press
   */
  public void setEventTime(long eventTime) {
    mEventTime = eventTime;
  }
}
