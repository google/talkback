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

package com.google.android.accessibility.utils;

/**
 * Interface for receiving callbacks when the state a service changes.
 *
 * <p>Implementing controllers should note that this may be invoked even after the controller was
 * explicitly shut down.
 */
public interface ServiceStateListener {
  /** The possible states of the service. */
  /** The state of the service before the system has bound to it or after it is destroyed. */
  final int SERVICE_STATE_INACTIVE = 0;
  /** The state of the service when it initialized and active. */
  final int SERVICE_STATE_ACTIVE = 1;
  /** The state of the service when it has been suspended by the user. */
  final int SERVICE_STATE_SUSPENDED = 2;

  void onServiceStateChanged(int newState);
}
