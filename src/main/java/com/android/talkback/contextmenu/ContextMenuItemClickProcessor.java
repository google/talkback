/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.talkback.contextmenu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.view.MenuItem;
import com.android.talkback.R;
import com.android.talkback.TalkBackPreferencesActivity;
import com.android.utils.SharedPreferencesUtils;
import com.google.android.marvin.talkback.TalkBackService;

public class ContextMenuItemClickProcessor {

    private TalkBackService mService;

    public ContextMenuItemClickProcessor(TalkBackService service) {
        mService = service;
    }

    public boolean onMenuItemClicked(MenuItem menuItem) {
        if (menuItem == null) {
            // Let the manager handle cancellations.
            return false;
        }

        final int itemId = menuItem.getItemId();
        if (itemId == R.id.read_from_top) {
            mService.getFullScreenReadController().startReadingFromBeginning();
        } else if (itemId == R.id.read_from_current) {
            mService.getFullScreenReadController().startReadingFromNextNode();
        } else if (itemId == R.id.repeat_last_utterance) {
            mService.getSpeechController().repeatLastUtterance();
        } else if (itemId == R.id.spell_last_utterance) {
            mService.getSpeechController().spellLastUtterance();
        } else if (itemId == R.id.copy_last_utterance_to_clipboard) {
            mService.getSpeechController().copyLastUtteranceToClipboard(
                    mService.getSpeechController().getLastUtterance());
        } else if (itemId == R.id.pause_feedback) {
            mService.requestSuspendTalkBack();
        } else if (itemId == R.id.talkback_settings) {
            final Intent settingsIntent = new Intent(mService, TalkBackPreferencesActivity.class);
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            mService.startActivity(settingsIntent);
        } else if (itemId == R.id.tts_settings) {
            Intent intent = new Intent();
            intent.setAction(TalkBackService.INTENT_TTS_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mService.startActivity(intent);
        } else if (itemId == R.id.enable_dimming) {
            mService.getDimScreenController().showDimScreenDialog();
        } else if (itemId == R.id.disable_dimming) {
            mService.getDimScreenController().disableDimming();
        } else {
            // The menu item was not recognized.
            return false;
        }

        return true;
    }
}
