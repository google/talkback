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

package com.google.android.accessibility.talkback.permission;

import android.content.Context;
import android.content.Intent;

/** Utility class for common permission operations. */
public class PermissionUtils {

  private PermissionUtils() {}

  /** Launches {@link PermissionRequestActivity} to request {@code permissions}. */
  public static void requestPermissions(Context context, String... permissions) {
    Intent intent = new Intent(context, PermissionRequestActivity.class);

    intent.putExtra(PermissionRequestActivity.PERMISSIONS, permissions);
    // Always launch Activity with new Task.
    intent.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
    context.startActivity(intent);
  }
}
