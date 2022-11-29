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
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.gesture.GestureShortcutMapping;
import com.google.android.accessibility.talkback.keyboard.KeyComboManager;
import com.google.android.accessibility.talkback.keyboard.KeyComboModel;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.PureFunction;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.feedbackpolicy.ScreenFeedbackManager;
import com.google.android.accessibility.utils.input.WindowEventInterpreter;
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
      FocusFinder focusFinder,
      GestureShortcutMapping gestureShortcutMapping,
      Pipeline.FeedbackReturner pipeline) {
    super(
        service,
        processorAccessibilityHints,
        /* speechController= */ null,
        /* feedbackController= */ null,
        service.isScreenOrientationLandscape());
    this.keyComboManager = keyComboManager;
    this.pipeline = pipeline;

    if (feedbackComposer != null) {
      ((TalkBackFeedbackComposer) feedbackComposer).setFocusFinder(focusFinder);
      ((TalkBackFeedbackComposer) feedbackComposer)
          .setGestureShortcutMapping(gestureShortcutMapping);
    }
  }

  @Override
  protected @Nullable UserPreferences createPreferences() {
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
    return getInterpreter();
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
      boolean forceFeedbackEvenIfAudioPlaybackActive,
      boolean forceFeedbackEvenIfMicrophoneActive,
      boolean forceFeedbackEvenIfSsbActive,
      boolean sourceIsVolumeControl) {
    if ((hint != null) && (accessibilityHintsManager != null)) {
      accessibilityHintsManager.postHintForScreen(hint);
    }

    int flags =
        (forceFeedbackEvenIfAudioPlaybackActive
                ? FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE
                : 0)
            | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_PHONE_CALL_ACTIVE
            | (forceFeedbackEvenIfMicrophoneActive
                ? FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE
                : 0)
            | (forceFeedbackEvenIfSsbActive
                ? FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE
                : 0)
            | (sourceIsVolumeControl ? FeedbackItem.FLAG_SOURCE_IS_VOLUME_CONTROL : 0);
    ;

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

    private @Nullable FocusFinder focusFinder;
    private @Nullable GestureShortcutMapping gestureShortcutMapping;

    @Override
    public Feedback customizeFeedback(
        AllContext allContext,
        Feedback feedback,
        WindowEventInterpreter.EventInterpretation interpretation,
        final int logDepth) {
      // Compose feedback for the popup window, such as auto-complete suggestions window.
      // To navigate to access the suggestions window when the suggestions window popups,
      // the user can
      // 1. perform a previous-window gesture when a11y focus is on IME window.
      // 2. perform a next-item gesture when a11y focus is on the auto-complete textView.
      if (focusFinder == null || gestureShortcutMapping == null) {
        return feedback;
      }
      if (interpretation.getAnchorNodeRole() == Role.ROLE_EDIT_TEXT) {
        logCompose(logDepth, "customComposeFeedback", "auto-complete suggestions");
        AccessibilityNodeInfoCompat focus =
            focusFinder == null
                ? null
                : focusFinder.findFocusCompat(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        if (focus == null) {
          return feedback;
        }
        final String gesture;
        if ((Role.getRole(focus) == Role.ROLE_EDIT_TEXT
            && AccessibilityWindowInfoUtils.getAnchoredWindow(focus) != null)) {
          gesture =
              gestureShortcutMapping.getGestureFromActionKey(
                  allContext.getContext().getString(R.string.shortcut_value_next));
        } else if (focus.getWindow().getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
          gesture =
              gestureShortcutMapping.getGestureFromActionKey(
                  allContext.getContext().getString(R.string.shortcut_value_previous_window));
        } else {
          return feedback;
        }
        String utterance =
            (FeatureSupport.isMultiFingerGestureSupported() && gesture != null)
                ? allContext
                    .getContext()
                    .getString(R.string.suggestions_window_available_with_gesture, gesture)
                : allContext.getContext().getString(R.string.suggestions_window_available);
        feedback.addPart(
            new FeedbackPart(utterance)
                .earcon(true)
                .forceFeedbackEvenIfAudioPlaybackActive(true)
                .forceFeedbackEvenIfMicrophoneActive(true));
      }
      return feedback;
    }

    @Override
    protected CharSequence formatAnnouncementForArc(
        Context context, @Nullable CharSequence title, final int logDepth) {
      logCompose(logDepth, "formatAnnouncementForArc", "");
      SpannableStringBuilder builder = new SpannableStringBuilder((title == null) ? "" : title);

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

    void setFocusFinder(FocusFinder focusFinder) {
      this.focusFinder = focusFinder;
    }

    void setGestureShortcutMapping(GestureShortcutMapping gestureShortcutMapping) {
      this.gestureShortcutMapping = gestureShortcutMapping;
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
