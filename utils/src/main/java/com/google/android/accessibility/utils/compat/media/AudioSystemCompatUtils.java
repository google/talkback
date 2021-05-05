/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.utils.compat.media;

import com.google.android.accessibility.utils.compat.CompatUtils;
import java.lang.reflect.Method;

public class AudioSystemCompatUtils {
  private static final Class<?> CLASS_AudioSystem =
      CompatUtils.getClass("android.media.AudioSystem");
  private static final Method METHOD_isSourceActive =
      CompatUtils.getMethod(CLASS_AudioSystem, "isSourceActive", int.class);

  /**
   * Calls into AudioSystem to check the current status of an input source.
   *
   * <p>This is only available on API 17+ and will always return false if invoked on earlier
   * platforms.
   *
   * @param source The source ID to query. Expects constants from {@code MediaRecorder.AudioSource}
   * @return {@code true} if the input source is active, {@code false} otherwise.
   */
  public static boolean isSourceActive(int source) {
    return (Boolean) CompatUtils.invoke(null, false, METHOD_isSourceActive, source);
  }
}
