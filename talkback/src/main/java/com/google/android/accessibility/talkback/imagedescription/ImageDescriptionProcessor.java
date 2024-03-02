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

package com.google.android.accessibility.talkback.imagedescription;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;

/** A class supports to provides content for an image. */
public class ImageDescriptionProcessor {

  public ImageDescriptionProcessor(Context context, TalkBackAnalytics analytics) {}

  public void stop() {}

  public void caption(Bitmap bitmap, ImageDescriptionListener listener) {}

  public static boolean isSupportImageDescription() {
    return false;
  }

  @VisibleForTesting
  public static void setSupportImageDescription(boolean supportImageDescription) {}
}
