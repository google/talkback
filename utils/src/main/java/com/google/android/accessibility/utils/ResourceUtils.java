/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.Context;
import android.content.res.Resources;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Utility methods for resources. */
public class ResourceUtils {

  public static int getResourceIdFromString(Context context, String resourceIdString) {
    if (resourceIdString == null) {
      return 0;
    }

    if (resourceIdString.startsWith("@")) {
      resourceIdString = resourceIdString.substring(1);
    }

    String[] pair = resourceIdString.split("/");
    if (pair == null || pair.length != 2) {
      throw new IllegalArgumentException("Resource parameter is malformed: " + resourceIdString);
    }

    Resources res = context.getResources();
    return res.getIdentifier(pair[1], pair[0], context.getPackageName());
  }

  public static @Nullable String readStringByResourceIdFromString(
      Context context, String resourceIdString) {
    int resourceId = getResourceIdFromString(context, resourceIdString);
    if (resourceId != 0) {
      return context.getString(resourceId);
    }

    return null;
  }

  /** Returns the @colorInt associated with a particular resource ID {@code colorResId}. */
  @ColorInt
  public static int getColor(@ColorRes int colorResId, Context context) {
    // Resources.getColor(int) is deprecated M onwards and
    // Context.getColor(int) is added from M onwards.
    return context.getColor(colorResId);
  }
}
