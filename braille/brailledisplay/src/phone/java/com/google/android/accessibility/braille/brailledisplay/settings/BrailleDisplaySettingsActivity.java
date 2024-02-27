/*
 * Copyright 2021 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.settings;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplay;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.preference.PreferencesActivity;

/** The Settings Activity for Braille Display. */
public class BrailleDisplaySettingsActivity extends PreferencesActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    // This prevents Fragment restore, in a safe way.
    super.onCreate(null);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new BrailleDisplaySettingsFragment(
        AccessibilityServiceCompatUtils.Constants.TALKBACK_SERVICE,
        BrailleDisplay.ENCODER_FACTORY.getDeviceNameFilter());
  }
}
