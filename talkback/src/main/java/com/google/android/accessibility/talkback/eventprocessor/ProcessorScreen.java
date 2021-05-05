/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.accessibility.talkback.eventprocessor;


import android.content.Context;
import android.text.SpannableStringBuilder;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.PureFunction;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.WindowEventInterpreter;
import com.google.android.accessibility.utils.feedback.ScreenFeedbackManager;
import com.google.android.accessibility.utils.keyboard.KeyComboManager;
import com.google.android.accessibility.utils.keyboard.KeyComboModel;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Specializes ScreenFeedbackManager for TalkBack, customizing speech feedback and modifying state
 * of cursor controller and other event handlers.
 */
public class ProcessorScreen extends ScreenFeedbackManager {
  private static String TAG = "ProcessorScreen";

  private final KeyComboManager keyComboManager;
  private final Pipeline.FeedbackReturner pipeline;

  public ProcessorScreen(
      final TalkBackService service,
      ProcessorAccessibilityHints processorAccessibilityHints,
      KeyComboManager keyComboManager,
      Pipeline.FeedbackReturner pipeline) {
    super(
        service,
        processorAccessibilityHints,
        /* speechController= */ null,
        /* feedbackController= */ null,
        service.isScreenOrientationLandscape());
    this.keyComboManager = keyComboManager;
    this.pipeline = pipeline;
  }

  @Override
  @Nullable
  protected UserPreferences createPreferences() {
    return new UserPreferences() {
      @Override
      public @Nullable String keyComboResIdToString(int keyComboId) {
        KeyComboModel keyComboModel = keyComboManager.getKeyComboModel();
        long keyComboCode = keyComboModel.getKeyComboCodeForKey(service.getString(keyComboId));
        if (keyComboCode != KeyComboModel.KEY_COMBO_CODE_UNASSIGNED) {
          long keyComboCodeWithModifier =
              KeyComboManager.getKeyComboCode(
                  KeyComboManager.getModifier(keyComboCode) | keyComboModel.getTriggerModifier(),
                  KeyComboManager.getKeyCode(keyComboCode));
          String keyCombo =
              keyComboManager.getKeyComboStringRepresentation(keyComboCodeWithModifier);
          return keyCombo;
        }
        return null;
      }
    };
  }

  @Override
  protected FeedbackComposer createComposer() {
    return new TalkBackFeedbackComposer();
  }

  public WindowEventInterpreter getWindowEventInterpreter() {
    return interpreter;
  }

  @Override
  protected boolean allowAnnounce(AccessibilityEvent event) {
    // If the user performs a cursor control(copy, paste, start selection mode, etc) in the
    // talkback context menu and lands back to the edit text, a TYPE_WINDOWS_CHANGED and a
    // TYPE_WINDOW_STATE_CHANGED events will be fired. We should skip these two events to
    // avoid announcing the window title.
    boolean allowAnnounce = true;
    if (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        && EventState.getInstance()
            .checkAndClearRecentFlag(
                EventState.EVENT_SKIP_WINDOWS_CHANGED_PROCESSING_AFTER_CURSOR_CONTROL)) {
      allowAnnounce = false;
    }
    if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        && EventState.getInstance()
            .checkAndClearRecentFlag(
                EventState.EVENT_SKIP_WINDOW_STATE_CHANGED_PROCESSING_AFTER_CURSOR_CONTROL)) {
      allowAnnounce = false;
    }

    return allowAnnounce;
  }

  @Override
  protected boolean customHandle(
      WindowEventInterpreter.EventInterpretation interpretation, @Nullable EventId eventId) {
    if (interpretation == null) {
      return false;
    }

    // For original event, perform some state & UI actions, even if windows are unstable.
    if (interpretation.isOriginalEvent()) {
      if (!interpretation.isAllowAnnounce()) {
        interpretation.setMainWindowsChanged(false);
      }
    }

    // Only speak if windows are stable and the event allows announcement.
    return interpretation.areWindowsStable() && interpretation.isAllowAnnounce();
  }

  @Override
  protected void checkSpeaker() {
    if (pipeline == null) {
      throw new IllegalStateException();
    }
  }

  @Override
  protected void speak(
      CharSequence utterance,
      @Nullable CharSequence hint,
      EventId eventId,
      boolean forceAudioPlaybackActive,
      boolean forceMicrophoneActive,
      boolean forceSsbActive) {
    if ((hint != null) && (accessibilityHintsManager != null)) {
      accessibilityHintsManager.postHintForScreen(hint);
    }

    int flags =
        (forceAudioPlaybackActive ? FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE : 0)
            | FeedbackItem.FLAG_FORCED_FEEDBACK_PHONE_CALL_ACTIVE
            | (forceMicrophoneActive ? FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE : 0)
            | (forceSsbActive ? FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE : 0);

    SpeechController.SpeakOptions speakOptions =
        SpeechController.SpeakOptions.create()
            .setQueueMode(SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH)
            .setFlags(flags);

    pipeline.returnFeedback(
        eventId,
        com.google.android.accessibility.talkback.Feedback.speech(utterance, speakOptions)
            .sound(com.google.android.accessibility.utils.R.raw.window_state)
            .vibration(com.google.android.accessibility.utils.R.array.window_state_pattern));
  }

  @PureFunction
  private static class TalkBackFeedbackComposer extends ScreenFeedbackManager.FeedbackComposer {
    @Override
    protected CharSequence formatAnnouncementForArc(
        Context context, CharSequence title, final int logDepth) {
      logCompose(logDepth, "formatAnnouncementForArc", "");
      SpannableStringBuilder builder = new SpannableStringBuilder(title);

      StringBuilderUtils.appendWithSeparator(
          builder, context.getString(R.string.arc_android_window));

      return builder;
    }

    @Override
    protected CharSequence getHintForArc(AllContext allContext, final int logDepth) {
      logCompose(logDepth, "getHintForArc", "");
      SpannableStringBuilder builder = new SpannableStringBuilder();

      // Append TalkBack activation hint.
      StringBuilderUtils.appendWithSeparator(
          builder, allContext.getContext().getString(R.string.arc_talkback_activation_hint));

      // Append short navigation hint.
      StringBuilderUtils.appendWithSeparator(
          builder, allContext.getContext().getString(R.string.arc_navigation_hint));

      // Append hint to see the list of keyboard shortcuts.
      appendKeyboardShortcutHint(
          allContext,
          builder,
          R.string.arc_open_manage_keyboard_shortcuts_hint,
          R.string.keycombo_shortcut_open_manage_keyboard_shortcuts);

      // Append hint to open TalkBack settings.
      appendKeyboardShortcutHint(
          allContext,
          builder,
          R.string.arc_open_talkback_settings_hint,
          R.string.keycombo_shortcut_open_talkback_settings);

      return builder;
    }

    private void appendKeyboardShortcutHint(
        AllContext allContext, SpannableStringBuilder builder, int templateId, int keyComboId) {
      if (allContext.getUserPreferences() == null) {
        return;
      }
      String keyCombo = allContext.getUserPreferences().keyComboResIdToString(keyComboId);
      if (keyCombo == null) {
        return;
      }
      StringBuilderUtils.appendWithSeparator(
          builder, allContext.getContext().getString(templateId, keyCombo));
    }
  }
}
