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

package com.google.android.accessibility.utils.feedback;

import static com.google.android.accessibility.utils.AccessibilityEventUtils.WINDOW_ID_NONE;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.PureFunction;
import com.google.android.accessibility.utils.R;
import com.google.android.accessibility.utils.ReadOnly;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.WindowEventInterpreter;
import com.google.android.accessibility.utils.WindowManager;
import com.google.android.accessibility.utils.WindowsDelegate;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Generates speech for window events. Customized by SwitchAccess and TalkBack.
 *
 * <p>The overall design is to have 3 stages, similar to Compositor:
 *
 * <ol>
 *   <li>Event interpretation, which outputs a complete description of the event that can be logged
 *       to tell us all we need to know about what happened.
 *   <li>Feedback rules, which are stateless (aka static) and independent of the android operating
 *       system version. The feedback can be logged to tell us all we need to know about what
 *       talkback is trying to do in response to the event. This happens in composeFeedback().
 *   <li>Feedback methods, which provide a simple interface for speaking and acting on the
 *       user-interface.
 * </ol>
 */
public class ScreenFeedbackManager
    implements AccessibilityEventListener,
        WindowsDelegate,
        WindowEventInterpreter.WindowEventHandler {

  private static final String TAG = "ScreenFeedbackManager";

  /** Event types that are handled by ScreenFeedbackManager. */
  private static final int MASK_EVENTS_HANDLED_BY_SCREEN_FEEDBACK_MANAGER =
      AccessibilityEvent.TYPE_WINDOWS_CHANGED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

  private final AllContext allContext; // Wrapper around various context and preference data.
  protected final WindowEventInterpreter interpreter;
  protected FeedbackComposer feedbackComposer;

  // Context used by this class.
  protected final AccessibilityService service;
  private final boolean isArc;
  protected final @Nullable AccessibilityHintsManager accessibilityHintsManager;
  private final SpeechController speechController;
  private final FeedbackController feedbackController;
  private final boolean isScreenOrientationLandscape;

  public ScreenFeedbackManager(
      AccessibilityService service,
      @Nullable AccessibilityHintsManager hintsManager,
      SpeechController speechController,
      FeedbackController feedbackController,
      boolean screenOrientationLandscape) {
    interpreter = new WindowEventInterpreter(service);
    interpreter.addListener(this);

    allContext = getAllContext(service, createPreferences());
    feedbackComposer = createComposer();

    this.service = service;
    isArc = FeatureSupport.isArc();

    accessibilityHintsManager = hintsManager;
    this.speechController = speechController;
    this.feedbackController = feedbackController;
    isScreenOrientationLandscape = screenOrientationLandscape;
  }

  /** Allow overriding preference creation. */
  @Nullable
  protected UserPreferences createPreferences() {
    return null;
  }

  /** Allow overriding feedback composition. */
  protected FeedbackComposer createComposer() {
    return new FeedbackComposer();
  }

  public void clearScreenState() {
    interpreter.clearScreenState();
  }

  @Override
  public CharSequence getWindowTitle(int windowId) {
    return interpreter.getWindowTitle(windowId);
  }

  @Override
  public boolean isSplitScreenMode() {
    return interpreter.isSplitScreenMode();
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_SCREEN_FEEDBACK_MANAGER;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    // Skip the delayed interpret if doesn't allow the announcement.
    interpreter.interpret(event, eventId, allowAnnounce(event));
  }

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

    if (feedbackController != null) {
      feedbackController.playActionCompletionFeedback();
    }

    if (speechController != null) {
      int flags =
          (forceAudioPlaybackActive ? FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE : 0)
              | FeedbackItem.FLAG_FORCED_FEEDBACK_PHONE_CALL_ACTIVE
              | (forceMicrophoneActive ? FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE : 0)
              | (forceSsbActive ? FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE : 0);
      speechController.speak(
          utterance, /* Text */
          SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH, /* QueueMode */
          flags,
          new Bundle(), /* SpeechParams */
          eventId);
    }
  }

  /**
   * Returns the context data for feedback generation.
   *
   * @param context The context from which information about the screen will be retrieved.
   * @param preferences The {@link UserPreferences} object which contains user preferences related
   *     to the current accessibility service.
   * @return The {@link AllContext} object which contains the context data for feedback generation.
   */
  protected AllContext getAllContext(Context context, @Nullable UserPreferences preferences) {
    DeviceInfo deviceInfo = new DeviceInfo();
    AllContext allContext = new AllContext(deviceInfo, context, preferences);
    return allContext;
  }

  @Override
  public void handle(
      WindowEventInterpreter.EventInterpretation interpretation, @Nullable EventId eventId) {
    if (interpretation == null) {
      return;
    }

    boolean doFeedback = customHandle(interpretation, eventId);
    if (!doFeedback) {
      return;
    }

    // Generate feedback from interpreted event.
    Feedback feedback =
        feedbackComposer.composeFeedback(allContext, interpretation, /* logDepth= */ 0);
    LogUtils.v(TAG, "feedback=%s", feedback);

    if (!feedback.isEmpty() && (accessibilityHintsManager != null)) {
      accessibilityHintsManager.onScreenStateChanged();
    }
    // Speak each feedback part.
    for (FeedbackPart feedbackPart : feedback.getParts()) {
      speak(
          feedbackPart.getSpeech(),
          feedbackPart.getHint(),
          eventId,
          feedbackPart.getForceFeedbackAudioPlaybackActive(),
          feedbackPart.getForceFeedbackMicrophoneActive(),
          feedbackPart.getForceFeedbackSsbActive());
    }
  }

  /** Allow overriding the condition to skip announcing the window-change event. */
  protected boolean allowAnnounce(AccessibilityEvent event) {
    return true;
  }

  /** Allow overriding handling of interpreted event, and return whether to compose speech. */
  protected boolean customHandle(
      WindowEventInterpreter.EventInterpretation interpretation, @Nullable EventId eventId) {
    return true;
  }

  /** Inner class used for speech feedback generation. */
  @PureFunction
  protected static class FeedbackComposer {
    public FeedbackComposer() {
      super();
    }

    /** Compose speech feedback for fully interpreted window event, statelessly. */
    public Feedback composeFeedback(
        AllContext allContext,
        WindowEventInterpreter.EventInterpretation interpretation,
        final int logDepth) {

      logCompose(logDepth, "composeFeedback", "interpretation=%s", interpretation);

      // Compose feedback for keyboard window event.
      // From Android O, Date/TimePicker titles are also treated as "announcement", and will be
      // forced feedback -- though it is not necessary for these titles to speak over media player
      // nor search assistant.
      Feedback feedback = new Feedback();
      CharSequence announcement = interpretation.getAnnouncement();
      if (announcement != null) {
        feedback.addPart(
            new FeedbackPart(announcement)
                .earcon(true)
                .forceFeedbackAudioPlaybackActive(!interpretation.isFromVolumeControlPanel())
                .forceFeedbackMicrophoneActive(!interpretation.isFromVolumeControlPanel())
                .forceFeedbackSsbActive(interpretation.isFromInputMethodEditor()));
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

          if (allContext.getDeviceInfo().isArc()) {
            logCompose(logDepth, "composeFeedback", "device is ARC");
            // If windowIdABefore was WINDOW_ID_NONE, we consider it as the focus comes into Arc
            // window.
            utterance = formatAnnouncementForArc(allContext.getContext(), utterance, logDepth + 1);

            // When focus goes into Arc, append hint.
            if (interpretation.getWindowA().getOldId() == WINDOW_ID_NONE) {
              hint = getHintForArc(allContext, logDepth + 1);
            }
          }

        } else {
          // Split screen mode.
          logCompose(logDepth, "composeFeedback", "split screen mode");
          int feedbackTemplate;
          if (allContext.getDeviceInfo().isScreenOrientationLandscape()) {
            if (allContext.getDeviceInfo().isScreenLayoutRTL()) {

              feedbackTemplate = R.string.template_split_screen_mode_landscape_rtl;
            } else {
              feedbackTemplate = R.string.template_split_screen_mode_landscape_ltr;
            }
          } else {
            feedbackTemplate = R.string.template_split_screen_mode_portrait;
          }

          utterance =
              allContext
                  .getContext()
                  .getString(
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
                allContext.getContext(),
                utterance,
                R.string.template_overlay_window,
                picInPicWindowTitle,
                logDepth + 1);
      }

      // Return feedback.
      if (!TextUtils.equals("", utterance)) {
        feedback.addPart(
            new FeedbackPart(utterance)
                .hint(hint)
                .clearQueue(true)
                .forceFeedbackAudioPlaybackActive(!interpretation.isFromVolumeControlPanel())
                .forceFeedbackMicrophoneActive(!interpretation.isFromVolumeControlPanel()));
      }
      feedback.setReadOnly();
      return feedback;
    }

    private CharSequence appendTemplate(
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

    /** Returns the announcement that should be spoken for an Arc window. */
    protected CharSequence formatAnnouncementForArc(
        Context context, CharSequence title, final int logDepth) {
      return title;
    }

    /** Returns the hint that should be spoken for Arc. */
    protected CharSequence getHintForArc(AllContext allContext, final int logDepth) {
      return "";
    }
  }

  // /////////////////////////////////////////////////////////////////////////////////////
  // Inner classes for feedback generation context

  /** Wrapper around various context data for feedback generation. */
  public static class AllContext {
    private final DeviceInfo deviceInfo;
    private final Context context;
    private final @Nullable UserPreferences preferences;

    public AllContext(
        DeviceInfo deviceInfoArg, Context contextArg, @Nullable UserPreferences preferencesArg) {
      deviceInfo = deviceInfoArg;
      context = contextArg;
      preferences = preferencesArg;
    }

    public DeviceInfo getDeviceInfo() {
      return deviceInfo;
    }

    public Context getContext() {
      return context;
    }

    @Nullable
    public UserPreferences getUserPreferences() {
      return preferences;
    }
  }

  /** A source of data about the device running talkback. */
  protected class DeviceInfo {
    public boolean isArc() {
      return isArc;
    }

    public boolean isSplitScreenModeAvailable() {
      return interpreter.isSplitScreenModeAvailable();
    }

    public boolean isScreenOrientationLandscape() {
      return isScreenOrientationLandscape;
    }

    public boolean isScreenLayoutRTL() {
      return WindowManager.isScreenLayoutRTL(service);
    }
  };

  /** Read-only interface to user preferences. */
  public interface UserPreferences {
    @Nullable
    String keyComboResIdToString(int keyComboId);
  }

  // /////////////////////////////////////////////////////////////////////////////////////
  // Inner class: speech output

  /** Data container specifying speech, earcons, feedback timing, etc. */
  protected static class Feedback extends ReadOnly {
    private final List<FeedbackPart> parts = new ArrayList<>();

    public void addPart(FeedbackPart part) {
      checkIsWritable();
      parts.add(part);
    }

    public List<FeedbackPart> getParts() {
      return isWritable() ? parts : Collections.unmodifiableList(parts);
    }

    public boolean isEmpty() {
      return parts.isEmpty();
    }

    @Override
    public String toString() {
      StringBuilder strings = new StringBuilder();
      for (FeedbackPart part : parts) {
        strings.append("[" + part + "] ");
      }
      return strings.toString();
    }
  }

  /** Data container used by Feedback, with a builder-style interface. */
  protected static class FeedbackPart {
    private final CharSequence speech;
    private @Nullable CharSequence hint;
    private boolean playEarcon = false;
    private boolean clearQueue = false;
    // Follows 
    private boolean forceFeedbackAudioPlaybackActive = false;
    private boolean forceFeedbackMicrophoneActive = false;
    private boolean forceFeedbackSsbActive = false;

    public FeedbackPart(CharSequence speech) {
      this.speech = speech;
    }

    public FeedbackPart hint(@Nullable CharSequence hint) {
      this.hint = hint;
      return this;
    }

    public FeedbackPart earcon(boolean playEarcon) {
      this.playEarcon = playEarcon;
      return this;
    }

    public FeedbackPart clearQueue(boolean clear) {
      clearQueue = clear;
      return this;
    }

    public FeedbackPart forceFeedbackAudioPlaybackActive(boolean force) {
      forceFeedbackAudioPlaybackActive = force;
      return this;
    }

    public FeedbackPart forceFeedbackMicrophoneActive(boolean force) {
      forceFeedbackMicrophoneActive = force;
      return this;
    }

    public FeedbackPart forceFeedbackSsbActive(boolean force) {
      forceFeedbackSsbActive = force;
      return this;
    }

    public CharSequence getSpeech() {
      return speech;
    }

    public @Nullable CharSequence getHint() {
      return hint;
    }

    public boolean getPlayEarcon() {
      return playEarcon;
    }

    public boolean getClearQueue() {
      return clearQueue;
    }

    public boolean getForceFeedbackAudioPlaybackActive() {
      return forceFeedbackAudioPlaybackActive;
    }

    public boolean getForceFeedbackMicrophoneActive() {
      return forceFeedbackMicrophoneActive;
    }

    public boolean getForceFeedbackSsbActive() {
      return forceFeedbackSsbActive;
    }

    @Override
    public String toString() {
      return StringBuilderUtils.joinFields(
          formatString(speech).toString(),
          (hint == null ? "" : " hint:" + formatString(hint)),
          StringBuilderUtils.optionalTag(" PlayEarcon", playEarcon),
          StringBuilderUtils.optionalTag(" ClearQueue", clearQueue),
          StringBuilderUtils.optionalTag(
              " ForceFeedbackAudioPlaybackActive", forceFeedbackAudioPlaybackActive),
          StringBuilderUtils.optionalTag(
              " ForceFeedbackMicrophoneActive", forceFeedbackMicrophoneActive),
          StringBuilderUtils.optionalTag(" ForceFeedbackAudioSsbActive", forceFeedbackSsbActive));
    }
  }

  // /////////////////////////////////////////////////////////////////////////////////////
  // Logging functions

  private static CharSequence formatString(CharSequence text) {
    return (text == null) ? "null" : String.format("\"%s\"", text);
  }

  @FormatMethod
  protected static void logCompose(
      final int depth, String methodName, @FormatString String format, Object... args) {

    // Compute indentation.
    char[] indentChars = new char[depth * 2];
    Arrays.fill(indentChars, ' ');
    String indent = new String(indentChars);

    // Log message.
    LogUtils.v(TAG, "%s%s() %s", indent, methodName, String.format(format, args));
  }
}
