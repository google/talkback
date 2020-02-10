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

package com.google.android.accessibility.talkback.contextmenu;

import static com.google.android.accessibility.talkback.Feedback.Speech.Action.COPY_SAVED;
import static com.google.android.accessibility.talkback.Feedback.Speech.Action.REPEAT_SAVED;
import static com.google.android.accessibility.talkback.Feedback.Speech.Action.SPELL_SAVED;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Intent;
import android.view.MenuItem;
import com.android.talkback.TalkBackPreferencesActivity;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.Performance.EventId;

/** Class for processing the clicks on menu items */
public class ContextMenuItemClickProcessor {

  private final TalkBackService service;
  private final Pipeline.FeedbackReturner pipeline;

  public ContextMenuItemClickProcessor(
      TalkBackService service, Pipeline.FeedbackReturner pipeline) {
    this.service = service;
    this.pipeline = pipeline;
  }

  public boolean onMenuItemClicked(MenuItem menuItem) {
    if (menuItem == null) {
      // Let the manager handle cancellations.
      return false;
    }

    EventId eventId = EVENT_ID_UNTRACKED; // Currently not tracking performance for menu events.

    final int itemId = menuItem.getItemId();
    if (itemId == R.id.read_from_top) {
      service
          .getFullScreenReadController()
          .startReadingFromBeginning(eventId, /* fromContextMenu= */ true);
    } else if (itemId == R.id.read_from_current) {
      service
          .getFullScreenReadController()
          .startReadingFromNextNode(eventId, /* fromContextMenu= */ true);
    } else if (itemId == R.id.repeat_last_utterance) {
      pipeline.returnFeedback(
          eventId, Feedback.part().setSpeech(Feedback.Speech.create(REPEAT_SAVED)));
    } else if (itemId == R.id.spell_last_utterance) {
      pipeline.returnFeedback(
          eventId, Feedback.part().setSpeech(Feedback.Speech.create(SPELL_SAVED)));
    } else if (itemId == R.id.copy_last_utterance_to_clipboard) {
      pipeline.returnFeedback(
          eventId, Feedback.part().setSpeech(Feedback.Speech.create(COPY_SAVED)));
    } else if (itemId == R.id.pause_feedback) {
      service.requestSuspendTalkBack(eventId);
    } else if (itemId == R.id.talkback_settings) {
      final Intent settingsIntent = new Intent(service, TalkBackPreferencesActivity.class);
      settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      settingsIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      service.startActivity(settingsIntent);
    } else if (itemId == R.id.tts_settings) {
      Intent intent = new Intent();
      intent.setAction(TalkBackService.INTENT_TTS_SETTINGS);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      service.startActivity(intent);
    } else if (itemId == R.id.enable_dimming) {
      service.getDimScreenController().enableDimmingAndShowConfirmDialog();
    } else if (itemId == R.id.disable_dimming) {
      service.getDimScreenController().disableDimming();
    } else if (itemId == R.id.screen_search) {
      service.getUniversalSearchManager().toggleSearch(eventId);
    } else {
      // The menu item was not recognized.
      return false;
    }

    return true;
  }
}
