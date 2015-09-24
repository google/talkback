/*
 * Copyright 2013 Google Inc.
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

package com.android.talkback;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.google.android.marvin.talkback.TalkBackService;

/**
 * Activity for broadcasting
 * {@link com.google.android.marvin.talkback.TalkBackService#ACTION_PERFORM_GESTURE_ACTION} to open the TalkBack
 * Global Context Menu from a search button long-press.
 */
public class ShortcutProxyActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = new Intent();
        intent.setPackage(getPackageName());
        intent.setAction(TalkBackService.ACTION_PERFORM_GESTURE_ACTION);
        intent.putExtra(TalkBackService.EXTRA_GESTURE_ACTION,
                R.string.shortcut_value_talkback_breakout);

        sendBroadcast(intent, TalkBackService.PERMISSION_TALKBACK);
        finish();
    }
}
