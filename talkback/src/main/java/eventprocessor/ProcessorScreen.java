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

import static com.google.android.accessibility.utils.WindowEventInterpreter.WINDOW_ID_NONE;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ReadOnly;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.WindowEventInterpreter;
import com.google.android.accessibility.utils.WindowManager;
import com.google.android.accessibility.utils.keyboard.KeyComboManager;
import com.google.android.accessibility.utils.keyboard.KeyComboModel;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.SpeechController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Translates Accessibility Window Events into discreet state changes.
 *
 * <p>The overall design is to have 3 stages, similar to Compositor:
 *
 * <ol>
 *   <li>Event interpretation, which outputs a complete description of the event that can be logged
 *       to tell us all we need to know about what happened.
 *   <li>Feedback rules, which are stateless (aka static) and independent of the android operating
 *       system version. The feedback can be logged to tell us all we need to know about what
 *       talkback is trying to do in response to the event. This happens in composeFeedback().
 *   <li>feedback methods, which provide a simple interface for speaking and acting on the
 *       user-interface.
 * </ol>
 *
 * TODO: Move speech logic from this class into Compositor.
 */
public class ProcessorScreen
    implements AccessibilityEventListener,
        GlobalVariables.WindowsDelegate,
        WindowEventInterpreter.WindowEventHandler {

  /** Event types that are handled by ProcessorScreen. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_SCREEN =
      AccessibilityEvent.TYPE_WINDOWS_CHANGED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

  private WindowEventInterpreter mInterpreter;

  private final TalkBackService mService;
  private final boolean mIsArc;
  private final AllContext mAllContext; // Wrapper around various context and preference data.
  private final DeviceInfo mDeviceInfo;

  private CharSequence mUtterance;
  private CharSequence mHint;
  private EventId mUtteranceEventId;

  public ProcessorScreen(TalkBackService service) {
    mInterpreter = new WindowEventInterpreter(service);
    mInterpreter.addListener(this);
    mService = service;
    mIsArc = FormFactorUtils.getInstance(service).isArc();
    mDeviceInfo = new DeviceInfo();
    mAllContext = new AllContext(mDeviceInfo, mService, mUserPreferences);
  }

  public void clearScreenState() {
    mInterpreter.clearScreenState();
  }

  @Override
  public CharSequence getWindowTitle(int windowId) {
    return mInterpreter.getWindowTitle(windowId);
  }

  @Override
  public boolean isSplitScreenMode() {
    return mInterpreter.isSplitScreenMode();
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_PROCESSOR_SCREEN;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    mInterpreter.interpret(event, eventId);
  }

  @Override
  public void handle(WindowEventInterpreter.EventInterpretation interpretation, EventId eventId) {

    if (interpretation == null) {
      return;
    }

    // For original event, perform some state & UI actions, even if windows are unstable.
    if (interpretation.isOriginalEvent()) {
      // If the user performs a cursor control(copy, paste, start selection mode, etc) in the
      // local context menu and lands back to the edit text, a TYPE_WINDOWS_CHANGED and a
      // TYPE_WINDOW_STATE_CHANGED events will be fired. We should skip these two events to
      // avoid announcing the window title.
      if (interpretation.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED
          && EventState.getInstance()
              .checkAndClearRecentFlag(
                  EventState.EVENT_SKIP_WINDOWS_CHANGED_PROCESSING_AFTER_CURSOR_CONTROL)) {
        interpretation.setMainWindowsChanged(false);
      }
      if (interpretation.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
          && EventState.getInstance()
              .checkAndClearRecentFlag(
                  EventState.EVENT_SKIP_WINDOW_STATE_CHANGED_PROCESSING_AFTER_CURSOR_CONTROL)) {
        interpretation.setMainWindowsChanged(false);
      }

      // Change navigation granularity.
      if (interpretation.getMainWindowsChanged()) {
        mService.getCursorController().setGranularityToDefault();
        mService.getCursorController().resetLastFocusedInfo();
        // We might need to call initLastEditable() at more places in the code incase we see unusual
        // text field behavior. Currently the need to add this at other places is not evident.
        // If talkback changes window with input focus already in a text input, talkback
        // will not receive TYPE_VIEW_FOCUSED and so this is required here.
        mService.getCursorController().initLastEditable();
      }
    }

    // Only speak if windows are stable.
    if (!interpretation.areWindowsStable()) {
      return;
    }

    // Generate hint feedback.
    ProcessorAccessibilityHints processorAccessibilityHints =
        mService.getProcessorAccessibilityHints();
    if (processorAccessibilityHints != null) {
      processorAccessibilityHints.onScreenStateChanged();
    }

    // Generate feedback from interpreted event.
    Feedback feedback = composeFeedback(mAllContext, interpretation, 0 /* logDepth */);
    LogUtils.log(this, Log.VERBOSE, "feedback=%s", feedback);

    // Speak each feedback part.
    for (FeedbackPart feedbackPart : feedback.getParts()) {
      if (feedbackPart.getPlayEarcon()) {
        speakWithFeedback(feedbackPart.getSpeech(), feedbackPart.getHint(), eventId);
      } else {
        speak(
            feedbackPart.getSpeech(),
            feedbackPart.getHint(),
            feedbackPart.getClearQueue(),
            eventId);
      }
    }
  }

  /** Compose speech feedback for fully interpreted window event, statelessly. */
  private static Feedback composeFeedback(
      AllContext allContext,
      WindowEventInterpreter.EventInterpretation interpretation,
      final int logDepth) {

    // Compose feedback for keyboard window event.
    Feedback feedback = new Feedback();
    CharSequence announcement = interpretation.getAnnouncement();
    if (announcement != null) {
      feedback.addPart(new FeedbackPart(announcement).earcon(true));
    }

    // Generate spoken feedback.
    CharSequence utterance = "";
    CharSequence hint = null;
    if (interpretation.getMainWindowsChanged()) {
      if (interpretation.getAccessibilityOverlay().getId() != WINDOW_ID_NONE) {
        logCompose(logDepth, "composeFeedback", "accessibility overlay");
        // Case where accessibility overlay is shown. Use separated logic for accessibility
        // overlay not to say out of split screen mode, e.g. accessibility overlay is shown when
        // user is in split screen mode.
        utterance = interpretation.getAccessibilityOverlay().getTitleForFeedback();
      } else if (interpretation.getWindowB().getId() == WINDOW_ID_NONE) {
        // Single window mode.
        logCompose(logDepth, "composeFeedback", "single window mode");
        if (interpretation.getWindowA().getTitle() == null) {
          // In single window mode, do not provide feedback if window title is not set.
          feedback.setReadOnly();
          return feedback;
        }

        utterance = interpretation.getWindowA().getTitleForFeedback();

        if (allContext.deviceInfo.isArc()) {
          logCompose(logDepth, "composeFeedback", "device is ARC");
          // If windowIdABefore was WINDOW_ID_NONE, we consider it as the focus comes into Arc
          // window.
          utterance = formatAnnouncementForArc(allContext.context, utterance, logDepth + 1);

          // When focus goes into Arc, append hint.
          if (interpretation.getWindowA().getOldId() == WINDOW_ID_NONE) {
            hint = getHintForArc(allContext, logDepth + 1);
          }
        }

      } else {
        // Split screen mode.
        logCompose(logDepth, "composeFeedback", "split screen mode");
        int feedbackTemplate;
        if (allContext.deviceInfo.isScreenOrientationLandscape()) {
          if (allContext.deviceInfo.isScreenLayoutRTL()) {

            feedbackTemplate = R.string.template_split_screen_mode_landscape_rtl;
          } else {
            feedbackTemplate = R.string.template_split_screen_mode_landscape_ltr;
          }
        } else {
          feedbackTemplate = R.string.template_split_screen_mode_portrait;
        }

        utterance =
            allContext.context.getString(
                feedbackTemplate,
                interpretation.getWindowA().getTitleForFeedback(),
                interpretation.getWindowB().getTitleForFeedback());
      }
    }

    // Append picture-in-picture window description.
    if ((interpretation.getMainWindowsChanged() || interpretation.getPicInPicChanged())
        && interpretation.getPicInPic().getId() != WINDOW_ID_NONE
        && interpretation.getAccessibilityOverlay().getId() == WINDOW_ID_NONE) {
      logCompose(logDepth, "composeFeedback", "picture-in-picture");
      CharSequence picInPicWindowTitle = interpretation.getPicInPic().getTitleForFeedback();
      if (picInPicWindowTitle == null) {
        picInPicWindowTitle = ""; // Notify that pic-in-pic exists, even if title unavailable.
      }
      utterance =
          appendTemplate(
              allContext.context,
              utterance,
              R.string.template_overlay_window,
              picInPicWindowTitle,
              logDepth + 1);
    }

    // Return feedback.
    if (!TextUtils.equals("", utterance)) {
      feedback.addPart(new FeedbackPart(utterance).hint(hint).clearQueue(true));
    }
    feedback.setReadOnly();
    return feedback;
  }

  private static CharSequence appendTemplate(
      Context context,
      CharSequence text,
      int templateResId,
      CharSequence templateArg,
      final int logDepth) {
    logCompose(logDepth, "appendTemplate", "templateArg=%s", templateArg);
    CharSequence templatedText = context.getString(templateResId, templateArg);
    SpannableStringBuilder builder = new SpannableStringBuilder(text);
    StringBuilderUtils.appendWithSeparator(builder, templatedText);
    return builder;
  }

  private static CharSequence formatAnnouncementForArc(
      Context context, CharSequence title, final int logDepth) {
    logCompose(logDepth, "formatAnnouncementForArc", "");
    SpannableStringBuilder builder = new SpannableStringBuilder(title);

    StringBuilderUtils.appendWithSeparator(builder, context.getString(R.string.arc_android_window));

    return builder;
  }

  private static CharSequence getHintForArc(AllContext allContext, final int logDepth) {
    logCompose(logDepth, "getHintForArc", "");
    SpannableStringBuilder builder = new SpannableStringBuilder();

    // Append short navigation hint.
    StringBuilderUtils.appendWithSeparator(
        builder, allContext.context.getString(R.string.arc_navigation_hint));

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

  private static void appendKeyboardShortcutHint(
      AllContext allContext, SpannableStringBuilder builder, int templateId, int keyComboId) {
    String keyCombo = allContext.preferences.keyComboResIdToString(keyComboId);
    if (keyCombo == null) {
      return;
    }
    StringBuilderUtils.appendWithSeparator(
        builder, allContext.context.getString(templateId, keyCombo));
  }

  /** An instance of UserPreferences that contains preferred key combos. */
  private final UserPreferences mUserPreferences =
      new UserPreferences() {
        @Override
        public String keyComboResIdToString(int keyComboId) {
          KeyComboManager keyComboManager = mService.getKeyComboManager();
          KeyComboModel keyComboModel = keyComboManager.getKeyComboModel();
          long keyComboCode = keyComboModel.getKeyComboCodeForKey(mService.getString(keyComboId));
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

  private void speak(
      CharSequence utterance, CharSequence hint, boolean clearQueue, EventId eventId) {
    mUtterance = null;
    mHint = null;
    mUtteranceEventId = null;

    speakWithFeedback(utterance, hint, eventId);
  }

  private void speakWithFeedback(CharSequence utterance, CharSequence hint, EventId eventId) {
    if (hint != null) {
      ProcessorAccessibilityHints processorAccessibilityHints =
          mService.getProcessorAccessibilityHints();
      if (processorAccessibilityHints != null) {
        processorAccessibilityHints.postHintForScreen(hint);
      }
    }

    FeedbackController feedbackController = mService.getFeedbackController();
    feedbackController.playHaptic(R.array.window_state_pattern);
    feedbackController.playAuditory(R.raw.window_state);
    mService
        .getSpeechController()
        .speak(
            utterance, /* Text */
            SpeechController.QUEUE_MODE_UNINTERRUPTIBLE, /* QueueMode */
            0, /* Flags */
            null, /* SpeechParams */
            eventId);
  }

  // /////////////////////////////////////////////////////////////////////////////////////
  // Inner classes for feedback generation context

  /** Wrapper around various context data for feedback generation. */
  public static class AllContext {
    public final DeviceInfo deviceInfo;
    public final Context context;
    public final UserPreferences preferences;

    public AllContext(
        DeviceInfo deviceInfoArg, Context contextArg, UserPreferences preferencesArg) {
      deviceInfo = deviceInfoArg;
      context = contextArg;
      preferences = preferencesArg;
    }
  }

  /** A source of data about the device running talkback. */
  private class DeviceInfo {
    public boolean isArc() {
      return mIsArc;
    }

    public boolean isSplitScreenModeAvailable() {
      return mInterpreter.isSplitScreenModeAvailable();
    }

    public boolean isScreenOrientationLandscape() {
      return mService.isScreenOrientationLandscape();
    }

    public boolean isScreenLayoutRTL() {
      return WindowManager.isScreenLayoutRTL(mService);
    }
  };

  /** Read-only interface to user preferences. */
  public interface UserPreferences {
    String keyComboResIdToString(int keyComboId);
  }

  // /////////////////////////////////////////////////////////////////////////////////////
  // Inner class: speech output

  /** Data container specifying speech, earcons, feedback timing, etc. */
  private static class Feedback extends ReadOnly {
    private final List<FeedbackPart> mParts = new ArrayList();

    public void addPart(FeedbackPart part) {
      checkIsWritable();
      mParts.add(part);
    }

    public List<FeedbackPart> getParts() {
      return isWritable() ? mParts : Collections.unmodifiableList(mParts);
    }

    @Override
    public String toString() {
      StringBuilder strings = new StringBuilder();
      for (FeedbackPart part : mParts) {
        strings.append("[" + part + "] ");
      }
      return strings.toString();
    }
  }

  /** Data container used by Feedback, with a builder-style interface. */
  private static class FeedbackPart {
    private CharSequence mSpeech;
    private CharSequence mHint;
    private boolean mPlayEarcon = false;
    private boolean mClearQueue = false;

    public FeedbackPart(CharSequence speech) {
      mSpeech = speech;
    }

    public FeedbackPart hint(CharSequence hint) {
      mHint = hint;
      return this;
    }

    public FeedbackPart earcon(boolean playEarcon) {
      mPlayEarcon = playEarcon;
      return this;
    }

    public FeedbackPart clearQueue(boolean clear) {
      mClearQueue = clear;
      return this;
    }

    public CharSequence getSpeech() {
      return mSpeech;
    }

    public CharSequence getHint() {
      return mHint;
    }

    public boolean getPlayEarcon() {
      return mPlayEarcon;
    }

    public boolean getClearQueue() {
      return mClearQueue;
    }

    @Override
    public String toString() {
      return formatString(mSpeech)
          + (mHint == null ? "" : " hint:" + formatString(mHint))
          + (mPlayEarcon ? " PlayEarcon" : "")
          + (mClearQueue ? " ClearQueue" : "");
    }
  }

  // /////////////////////////////////////////////////////////////////////////////////////
  // Logging functions

  private static CharSequence formatString(CharSequence text) {
    return (text == null) ? "null" : String.format("\"%s\"", text);
  }

  private static void logCompose(
      final int depth, String methodName, String format, Object... args) {

    // Compute indentation.
    char[] indentChars = new char[depth * 2];
    Arrays.fill(indentChars, ' ');
    String indent = new String(indentChars);

    // Log message.
    LogUtils.log(
        ProcessorScreen.class,
        Log.VERBOSE,
        "%s%s() %s",
        indent,
        methodName,
        String.format(format, args));
  }
}
