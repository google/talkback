/*
 * Copyright (C) 2011 Google Inc.
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

package com.google.android.accessibility.utils.compat.speech.tts;

import android.speech.tts.TextToSpeech;
import com.google.android.accessibility.utils.compat.CompatUtils;
import java.lang.reflect.Method;

public class TextToSpeechCompatUtils {
  private static final Method METHOD_getCurrentEngine =
      CompatUtils.getMethod(TextToSpeech.class, "getCurrentEngine");

  private TextToSpeechCompatUtils() {
    // This class is non-instantiable.
  }

  /** @return the engine currently in use by this TextToSpeech instance. */
  public static String getCurrentEngine(TextToSpeech receiver) {
    return (String) CompatUtils.invoke(receiver, null, METHOD_getCurrentEngine);
  }
}
