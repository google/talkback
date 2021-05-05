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

import static com.google.android.accessibility.talkback.Feedback.ContinuousRead.Action.START_AT_NEXT;
import static com.google.android.accessibility.talkback.Feedback.ContinuousRead.Action.START_AT_TOP;
import static com.google.android.accessibility.talkback.Feedback.DimScreen.Action.BRIGHTEN;
import static com.google.android.accessibility.talkback.Feedback.DimScreen.Action.DIM;
import static com.google.android.accessibility.talkback.Feedback.Speech.Action.COPY_SAVED;
import static com.google.android.accessibility.talkback.Feedback.Speech.Action.REPEAT_SAVED;
import static com.google.android.accessibility.talkback.Feedback.Speech.Action.SPELL_SAVED;
import static com.google.android.accessibility.talkback.Feedback.VoiceRecognition.Action.START_LISTENING;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Intent;
import android.text.TextUtils;
import android.view.MenuItem;
import com.android.talkback.TalkBackPreferencesActivity;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.preference.TalkBackVerbosityPreferencesActivity;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;

/** Class for processing the clicks on menu items */
public class ContextMenuItemClickProcessor {

  private final TalkBackService service;
  private final Pipeline.FeedbackReturner pipeline;

  public ContextMenuItemClickProcessor(
      TalkBackService service, Pipeline.FeedbackReturner pipeline) {
    this.service = service;
    this.pipeline = pipeline;
  }

  /**
   * Checks these menuitems that need ContextMenuItemClickProcessor to handle onMenuItemClicked
   * feedback.
   *
   * @param menuItem The menuItem to check if ContextMenuItemClickProcessor can handle
   *     onMenuItemClicked feedback.
   * @return {@code true} if supports item click feedback, {@code false} otherwise.
   */
  public boolean isItemSupported(MenuItem menuItem) {
    if (menuItem == null) {
      return false;
    }
    final int itemId = menuItem.getItemId();

    return (itemId == R.id.read_from_top)
        || (itemId == R.id.read_from_current)
        || (itemId == R.id.repeat_last_utterance)
        || (itemId == R.id.spell_last_utterance)
        || (itemId == R.id.copy_last_utterance_to_clipboard)
        || (itemId == R.id.verbosity)
        || (itemId == R.id.audio_ducking)
        || (itemId == R.id.sound_feedback)
        || (itemId == R.id.vibration_feedback)
        || (itemId == R.id.talkback_settings)
        || (itemId == R.id.tts_settings)
        || (itemId == R.id.enable_dimming)
        || (itemId == R.id.disable_dimming)
        || (itemId == R.id.screen_search)
        || (itemId == R.id.voice_commands)
        || (itemId == R.id.pause_feedback);
  }

  public boolean onMenuItemClicked(MenuItem menuItem) {
    if (!isItemSupported(menuItem)) {
      // Let the manager handle cancellations.
      return false;
    }

    EventId eventId = EVENT_ID_UNTRACKED; // Currently not tracking performance for menu events.

    final int itemId = menuItem.getItemId();
    if (itemId == R.id.read_from_top) {
      pipeline.returnFeedback(eventId, Feedback.continuousRead(START_AT_TOP));
    } else if (itemId == R.id.read_from_current) {
      pipeline.returnFeedback(eventId, Feedback.continuousRead(START_AT_NEXT));
    } else if (itemId == R.id.repeat_last_utterance) {
      pipeline.returnFeedback(
          eventId, Feedback.part().setSpeech(Feedback.Speech.create(REPEAT_SAVED)));
    } else if (itemId == R.id.spell_last_utterance) {
      pipeline.returnFeedback(
          eventId, Feedback.part().setSpeech(Feedback.Speech.create(SPELL_SAVED)));
    } else if (itemId == R.id.copy_last_utterance_to_clipboard) {
      pipeline.returnFeedback(
          eventId, Feedback.part().setSpeech(Feedback.Speech.create(COPY_SAVED)));
    } else if (itemId == R.id.verbosity) {
      Intent intent = new Intent(service, TalkBackVerbosityPreferencesActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      service.startActivity(intent);
    } else if (itemId == R.id.audio_ducking) {
      switchValueAndEcho(
          R.string.audio_focus_state,
          R.string.pref_use_audio_focus_key,
          R.bool.pref_use_audio_focus_default);
    } else if (itemId == R.id.sound_feedback) {
      switchValueAndEcho(
          R.string.sound_feedback_state,
          R.string.pref_soundback_key,
          R.bool.pref_soundback_default);
    } else if (itemId == R.id.vibration_feedback) {
      switchValueAndEcho(
          R.string.vibration_feedback_state,
          R.string.pref_vibration_key,
          R.bool.pref_vibration_default);
    } else if (itemId == R.id.talkback_settings) {
      final Intent settingsIntent = new Intent(service, TalkBackPreferencesActivity.class);
      settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      settingsIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      service.startActivity(settingsIntent);
    } else if (itemId == R.id.tts_settings) {
      Intent ttsSettingsIntent = new Intent(TalkBackService.INTENT_TTS_SETTINGS);
      ttsSettingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      service.startActivity(ttsSettingsIntent);
    } else if (itemId == R.id.enable_dimming) {
      pipeline.returnFeedback(eventId, Feedback.dimScreen(DIM));
    } else if (itemId == R.id.disable_dimming) {
      pipeline.returnFeedback(eventId, Feedback.dimScreen(BRIGHTEN));
    } else if (itemId == R.id.screen_search) {
      service.getUniversalSearchManager().toggleSearch(eventId);
    } else if (itemId == R.id.voice_commands) {
      if (TalkBackService.ENABLE_VOICE_COMMANDS) {
        pipeline.returnFeedback(
            eventId, Feedback.voiceRecognition(START_LISTENING, /* checkDialog= */ true));
      }
    } else if (itemId == R.id.pause_feedback) {
      // Toggle talkback suspended state.
      service.requestSuspendTalkBack(eventId);
    }

    return true;
  }

  /**
   * Switches key value and announces change
   *
   * @param textResId The resId of text to announce when switch value.
   * @param keyResId The resId of key to switch.
   * @param defaultResId The resId of default key value
   */
  private void switchValueAndEcho(int textResId, int keyResId, int defaultResId) {
    boolean newValue =
        !SharedPreferencesUtils.getBooleanPref(
            SharedPreferencesUtils.getSharedPreferences(service),
            service.getResources(),
            keyResId,
            defaultResId);
    SharedPreferencesUtils.putBooleanPref(
        SharedPreferencesUtils.getSharedPreferences(service),
        service.getResources(),
        keyResId,
        newValue);

    String announcement =
        service.getString(
            textResId,
            newValue
                ? service.getString(R.string.value_on)
                : service.getString(R.string.value_off));

    if (!TextUtils.isEmpty(announcement)) {
      pipeline.returnFeedback(
          EVENT_ID_UNTRACKED,
          Feedback.speech(
              announcement,
              SpeakOptions.create()
                  .setFlags(
                      FeedbackItem.FLAG_NO_HISTORY
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE
                          | FeedbackItem.FLAG_SKIP_DUPLICATE)));
    }
  }
}
