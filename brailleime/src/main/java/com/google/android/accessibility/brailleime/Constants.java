/*
 * Copyright 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.brailleime;

import android.content.ComponentName;
import com.google.android.accessibility.utils.PackageManagerUtils;

/** Holds constants in support of BrailleIme. */
public class Constants {

  private Constants() {}

  /** The package name for the Messages app. */
  public static final String ANDROID_MESSAGES_PACKAGE_NAME = "com.google.android.apps.messaging";

  /** The package name for the Gboard app. */
  public static final String GBOARD_PACKAGE_NAME = "com.google.android.inputmethod.latin";

  /** The package name for the Keep app. */
  public static final String KEEP_NOTES_PACKAGE_NAME = "com.google.android.keep";

  private static final String ACCESSIBILITY_SUITE_PACKAGE_NAME =
      PackageManagerUtils.TALBACK_PACKAGE;

  /** The name of the TalkBack Settings Activity. */
  public static final ComponentName SETTINGS_ACTIVITY =
      new ComponentName(
          ACCESSIBILITY_SUITE_PACKAGE_NAME, "com.android.talkback.TalkBackPreferencesActivity");

  /** The name of the TalkBack service. */
  public static final ComponentName TALKBACK_SERVICE =
      new ComponentName(
          ACCESSIBILITY_SUITE_PACKAGE_NAME, PackageManagerUtils.TALKBACK_SERVICE_NAME);

  /** The name of the Braille Ime. */
  public static final ComponentName BRAILLE_KEYBOARD =
      new ComponentName(
          ACCESSIBILITY_SUITE_PACKAGE_NAME,
          "com.google.android.accessibility.brailleime.BrailleIme");
}
