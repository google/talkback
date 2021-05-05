/*
 * Copyright (C) 2015 Google Inc.
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

package com.google.android.accessibility.talkback.gesture;

import static com.google.android.accessibility.talkback.Feedback.ContinuousRead.Action.START_AT_NEXT;
import static com.google.android.accessibility.talkback.Feedback.ContinuousRead.Action.START_AT_TOP;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.COPY;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.CUT;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.END_SELECT;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.PASTE;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.START_SELECT;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.CLICK_CURRENT;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.LONG_CLICK_CURRENT;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.NEXT_GRANULARITY;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.NEXT_PAGE;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.PREVIOUS_GRANULARITY;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.PREVIOUS_PAGE;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.SCROLL_DOWN;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.SCROLL_LEFT;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.SCROLL_RIGHT;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.SCROLL_UP;
import static com.google.android.accessibility.talkback.Feedback.PassThroughMode.Action.PASSTHROUGH_CONFIRM_DIALOG;
import static com.google.android.accessibility.talkback.Feedback.Speech.Action.TOGGLE_VOICE_FEEDBACK;
import static com.google.android.accessibility.talkback.Feedback.VoiceRecognition.Action.START_LISTENING;
import static com.google.android.accessibility.talkback.actor.SystemActionPerformer.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS;
import static com.google.android.accessibility.talkback.actor.SystemActionPerformer.GLOBAL_ACTION_ACCESSIBILITY_BUTTON;
import static com.google.android.accessibility.talkback.actor.SystemActionPerformer.GLOBAL_ACTION_ACCESSIBILITY_BUTTON_CHOOSER;
import static com.google.android.accessibility.talkback.actor.SystemActionPerformer.GLOBAL_ACTION_KEYCODE_HEADSETHOOK;
import static com.google.android.accessibility.talkback.training.PageConfig.ANNOUNCE_REAL_ACTION;
import static com.google.android.accessibility.talkback.training.PageConfig.UNKNOWN_ANNOUNCEMENT;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.input.CursorGranularity.DEFAULT;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_TOUCH;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_BACKWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.FingerprintGestureController;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Feedback.TriggerIntent.Action;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.contextmenu.ListMenuManager;
import com.google.android.accessibility.talkback.controller.SelectorController;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorVolumeStream;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.training.TrainingActivity;
import com.google.android.accessibility.talkback.training.TrainingActivity.TrainingState;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.TreeDebug;
import com.google.android.accessibility.utils.WindowUtils;
import com.google.android.accessibility.utils.keyboard.KeyboardUtils;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Class to handle incoming gestures to TalkBack. Make sure tutorial still works TODO: Map
 * actions to ints, and store in a map that changes with prefs
 */
public class GestureController {

  private static final String LOG_TAG = "GestureController";

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final TalkBackService service;
  private final Pipeline.FeedbackReturner pipeline;
  private final ActorState actorState;
  private final ListMenuManager menuManager;
  private final SelectorController selectorController;
  private final ProcessorVolumeStream processorVolumeStream;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;
  private GestureShortcutMapping gestureShortcutMapping;
  private final TalkBackAnalytics analytics;
  /** Records whether should echo not recognized text speech */
  private boolean echoNotRecognizedTextEnabled;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Constructor methods

  public GestureController(
      TalkBackService service,
      Pipeline.FeedbackReturner pipeline,
      ActorState actorState,
      ListMenuManager menuManager,
      SelectorController selectorController,
      ProcessorVolumeStream processorVolumeStream,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      GestureShortcutMapping gestureShortcutMapping,
      TalkBackAnalytics analytics) {
    if (pipeline == null) {
      throw new IllegalStateException();
    }
    if (menuManager == null) {
      throw new IllegalStateException();
    }
    if (selectorController == null) {
      throw new IllegalStateException();
    }
    if (processorVolumeStream == null) {
      throw new IllegalStateException();
    }

    this.pipeline = pipeline;
    this.actorState = actorState;
    this.menuManager = menuManager;
    this.service = service;
    this.selectorController = selectorController;
    this.processorVolumeStream = processorVolumeStream;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.gestureShortcutMapping = gestureShortcutMapping;
    this.analytics = analytics;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Screen-swipe-shortcut methods

  /**
   * Maps fingerprint gesture Id to TalkBack action.
   *
   * @param gesture Fingerprint gesture Id
   * @return Mapped action shortcut
   */
  @TargetApi(Build.VERSION_CODES.O)
  private String actionFromFingerprintGesture(int gesture) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(service);
    switch (gesture) {
      case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_UP:
        return prefs.getString(
            service.getString(R.string.pref_shortcut_fingerprint_up_key),
            service.getString(R.string.pref_shortcut_fingerprint_up_default));
      case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_DOWN:
        return prefs.getString(
            service.getString(R.string.pref_shortcut_fingerprint_down_key),
            service.getString(R.string.pref_shortcut_fingerprint_down_default));
      case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_LEFT:
        return prefs.getString(
            service.getString(R.string.pref_shortcut_fingerprint_left_key),
            service.getString(R.string.pref_shortcut_fingerprint_left_default));
      case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_RIGHT:
        return prefs.getString(
            service.getString(R.string.pref_shortcut_fingerprint_right_key),
            service.getString(R.string.pref_shortcut_fingerprint_right_default));
      default:
        return service.getString(R.string.shortcut_value_unassigned);
    }
  }

  public void performAction(String action, EventId eventId) {
    maybeInterruptAllFeedback(action);
    boolean result = true;
    if (action.equals(service.getString(R.string.shortcut_value_unassigned))) {
      // Do Nothing
    } else if (action.equals(service.getString(R.string.shortcut_value_previous))) {
      result =
          pipeline.returnFeedback(
              eventId,
              Feedback.focusDirection(SEARCH_FOCUS_BACKWARD)
                  // Sets granularity to default because "Previous item" action always moves at
                  // default granularity.
                  .setGranularity(DEFAULT)
                  .setInputMode(INPUT_MODE_TOUCH)
                  .setDefaultToInputFocus(true)
                  .setScroll(true)
                  .setWrap(true));
    } else if (action.equals(service.getString(R.string.shortcut_value_next))) {
      result =
          pipeline.returnFeedback(
              eventId,
              Feedback.focusDirection(SEARCH_FOCUS_FORWARD)
                  // Sets granularity to default because "Next item" action always moves at default
                  // granularity.
                  .setGranularity(DEFAULT)
                  .setInputMode(INPUT_MODE_TOUCH)
                  .setDefaultToInputFocus(true)
                  .setScroll(true)
                  .setWrap(true));
    } else if (action.equals(service.getString(R.string.shortcut_value_scroll_back))) {
      result = pipeline.returnFeedback(eventId, Feedback.focusDirection(PREVIOUS_PAGE));
    } else if (action.equals(service.getString(R.string.shortcut_value_scroll_forward))) {
      result = pipeline.returnFeedback(eventId, Feedback.focusDirection(NEXT_PAGE));
    } else if (action.equals(service.getString(R.string.shortcut_value_scroll_up))) {
      result = pipeline.returnFeedback(eventId, Feedback.focusDirection(SCROLL_UP));
    } else if (action.equals(service.getString(R.string.shortcut_value_scroll_down))) {
      result = pipeline.returnFeedback(eventId, Feedback.focusDirection(SCROLL_DOWN));
    } else if (action.equals(service.getString(R.string.shortcut_value_scroll_left))) {
      result = pipeline.returnFeedback(eventId, Feedback.focusDirection(SCROLL_LEFT));
    } else if (action.equals(service.getString(R.string.shortcut_value_scroll_right))) {
      result = pipeline.returnFeedback(eventId, Feedback.focusDirection(SCROLL_RIGHT));
    } else if (action.equals(service.getString(R.string.shortcut_value_first_in_screen))) {
      result = pipeline.returnFeedback(eventId, Feedback.focusTop(INPUT_MODE_TOUCH));
    } else if (action.equals(service.getString(R.string.shortcut_value_last_in_screen))) {
      result = pipeline.returnFeedback(eventId, Feedback.focusBottom(INPUT_MODE_TOUCH));
    } else if (action.equals(service.getString(R.string.shortcut_value_media_control))) {
      result =
          pipeline.returnFeedback(
              eventId, Feedback.systemAction(GLOBAL_ACTION_KEYCODE_HEADSETHOOK));
    } else if (action.equals(service.getString(R.string.shortcut_value_back))) {
      result =
          pipeline.returnFeedback(
              eventId, Feedback.systemAction(AccessibilityService.GLOBAL_ACTION_BACK));
    } else if (action.equals(service.getString(R.string.shortcut_value_home))) {
      result =
          pipeline.returnFeedback(
              eventId, Feedback.systemAction(AccessibilityService.GLOBAL_ACTION_HOME));
    } else if (TalkBackService.ENABLE_VOICE_COMMANDS
        && action.equals(service.getString(R.string.shortcut_value_voice_commands))) {
      // TODO: Maybe disable when device is locked
      // (KeyguardManager.isDeviceLocked()).
      result =
          pipeline.returnFeedback(
              eventId, Feedback.voiceRecognition(START_LISTENING, /* checkDialog= */ true));
    } else if (action.equals(service.getString(R.string.shortcut_value_overview))) {
      result =
          pipeline.returnFeedback(
              eventId, Feedback.systemAction(AccessibilityService.GLOBAL_ACTION_RECENTS));
    } else if (action.equals(service.getString(R.string.shortcut_value_notifications))) {
      result =
          pipeline.returnFeedback(
              eventId, Feedback.systemAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS));
    } else if (action.equals(service.getString(R.string.shortcut_value_quick_settings))) {
      result =
          pipeline.returnFeedback(
              eventId, Feedback.systemAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS));
    } else if (action.equals(service.getString(R.string.shortcut_value_all_apps))) {
      result =
          pipeline.returnFeedback(
              eventId, Feedback.systemAction(GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS));
    } else if (action.equals(service.getString(R.string.shortcut_value_talkback_breakout))) {
      result = menuManager.showMenu(R.menu.context_menu, eventId);
    } else if (action.equals(service.getString(R.string.shortcut_value_local_breakout))) {
      result = menuManager.showMenu(R.menu.context_menu, eventId);
    } else if (action.equals(service.getString(R.string.shortcut_value_show_custom_actions))) {
      result = menuManager.showMenu(R.id.custom_action_menu, eventId);
    } else if (action.equals(service.getString(R.string.shortcut_value_editing))) {
      result = menuManager.showMenu(R.id.editing_menu, eventId);
    } else if (action.equals(service.getString(R.string.shortcut_value_show_language_options))) {
      result = menuManager.showMenu(R.menu.language_menu, eventId);
    } else if (action.equals(service.getString(R.string.shortcut_value_previous_granularity))) {
      result = pipeline.returnFeedback(eventId, Feedback.focusDirection(PREVIOUS_GRANULARITY));
    } else if (action.equals(service.getString(R.string.shortcut_value_next_granularity))) {
      result = pipeline.returnFeedback(eventId, Feedback.focusDirection(NEXT_GRANULARITY));
    } else if (action.equals(service.getString(R.string.shortcut_value_read_from_top))) {
      result = pipeline.returnFeedback(eventId, Feedback.continuousRead(START_AT_TOP));
    } else if (action.equals(service.getString(R.string.shortcut_value_read_from_current))) {
      result = pipeline.returnFeedback(eventId, Feedback.continuousRead(START_AT_NEXT));
    } else if (action.equals(service.getString(R.string.shortcut_value_print_node_tree))) {
      List<AccessibilityWindowInfo> windows = AccessibilityServiceCompatUtils.getWindows(service);
      TreeDebug.logNodeTrees(windows);
      TreeDebug.logOrderedTraversalTree(windows);
      pipeline.returnFeedback(
          eventId, Feedback.speech(service.getString(R.string.dump_node_tree_description)));
    } else if (action.equals(service.getString(R.string.shortcut_value_print_performance_stats))) {
      Performance.getInstance().displayLabelToStats();
      Performance.getInstance().displayStatToLabelCompare();
      Performance.getInstance().displayAllEventStats();
    } else if (action.equals(service.getString(R.string.shortcut_value_perform_click_action))) {
      result = pipeline.returnFeedback(eventId, Feedback.focus(CLICK_CURRENT));
    } else if (action.equals(
        service.getString(R.string.shortcut_value_perform_long_click_action))) {
      result = pipeline.returnFeedback(eventId, Feedback.focus(LONG_CLICK_CURRENT));
    } else if (action.equals(service.getString(R.string.shortcut_value_select_previous_setting))) {
      selectorController.selectPreviousOrNextSetting(eventId, false);
    } else if (action.equals(service.getString(R.string.shortcut_value_select_next_setting))) {
      selectorController.selectPreviousOrNextSetting(eventId, true);
    } else if (action.equals(
        service.getString(R.string.shortcut_value_selected_setting_previous_action))) {
      selectorController.adjustSelectedSetting(eventId, false);
    } else if (action.equals(
        service.getString(R.string.shortcut_value_selected_setting_next_action))) {
      selectorController.adjustSelectedSetting(eventId, true);
    } else if (action.equals(service.getString(R.string.shortcut_value_screen_search))) {
      service.getUniversalSearchManager().toggleSearch(eventId);
    } else if (action.equals(
        service.getString(R.string.shortcut_value_pass_through_next_gesture))) {
      result =
          pipeline.returnFeedback(eventId, Feedback.passThroughMode(PASSTHROUGH_CONFIRM_DIALOG));
    } else if (action.equals(service.getString(R.string.shortcut_value_a11y_button))) {
      result =
          pipeline.returnFeedback(
              eventId, Feedback.systemAction(GLOBAL_ACTION_ACCESSIBILITY_BUTTON));
    } else if (action.equals(service.getString(R.string.shortcut_value_a11y_button_long_press))) {
      result =
          pipeline.returnFeedback(
              eventId, Feedback.systemAction(GLOBAL_ACTION_ACCESSIBILITY_BUTTON_CHOOSER));
    } else if (action.equals(service.getString(R.string.shortcut_value_headphone_navigation))) {
      processorVolumeStream.toggleNavigationMode();
    } else if (action.equals(service.getString(R.string.shortcut_value_pause_or_resume_feedback))) {
      pipeline.returnFeedback(eventId, Feedback.speech(Feedback.Speech.Action.PAUSE_OR_RESUME));
    } else if (action.equals(service.getString(R.string.shortcut_value_start_selection_mode))) {
      AccessibilityNodeInfoCompat node = getEditTextFocus();
      if (node == null) {
        result = false;
      } else {
        // Edit text found.
        if (actorState.getDirectionNavigation().isSelectionModeActive()) {
          result = pipeline.returnFeedback(eventId, Feedback.edit(node, END_SELECT));
        } else {
          result = pipeline.returnFeedback(eventId, Feedback.edit(node, START_SELECT));
        }
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }
    } else if (action.equals(service.getString(R.string.shortcut_value_copy))) {
      AccessibilityNodeInfoCompat node =
          accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);
      result = pipeline.returnFeedback(eventId, Feedback.edit(node, COPY));
      AccessibilityNodeInfoUtils.recycleNodes(node);
    } else if (action.equals(service.getString(R.string.shortcut_value_cut))) {
      AccessibilityNodeInfoCompat node = getEditTextFocus();
      if (node == null) {
        result = false;
      } else {
        // Edit text found.
        result = pipeline.returnFeedback(eventId, Feedback.edit(node, CUT));
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }
    } else if (action.equals(service.getString(R.string.shortcut_value_paste))) {
      AccessibilityNodeInfoCompat node = getEditTextFocus();
      if (node == null) {
        result = false;
      } else {
        // Edit text found.
        result = pipeline.returnFeedback(eventId, Feedback.edit(node, PASTE));
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }
    } else if (action.equals(service.getString(R.string.shortcut_value_toggle_voice_feedback))) {
      pipeline.returnFeedback(eventId, Feedback.speech(TOGGLE_VOICE_FEEDBACK));
    } else if (action.equals(service.getString(R.string.shortcut_value_braille_keyboard))) {
      String inputMethodInfoId = KeyboardUtils.getEnabledImeId(service, service.getPackageName());
      if (!TextUtils.isEmpty(inputMethodInfoId)) {
        result = service.getSoftKeyboardController().switchToInputMethod(inputMethodInfoId);
      } else {
        result =
            pipeline.returnFeedback(
                eventId,
                Feedback.speech(
                    service.getString(R.string.switch_to_braille_keyboard_failure_msg)));
      }
    } else if (action.equals(service.getString(R.string.shortcut_value_tutorial))) {
      pipeline.returnFeedback(eventId, Feedback.triggerIntent(Action.TRIGGER_TUTORIAL));
    } else if (action.equals(service.getString(R.string.shortcut_value_practice_gestures))) {
      pipeline.returnFeedback(eventId, Feedback.triggerIntent(Action.TRIGGER_PRACTICE_GESTURE));
    } else if (action.equals(service.getString(R.string.shortcut_value_report_gesture))) {
      result = pipeline.returnFeedback(eventId, Feedback.reportGesture());
    }

    if (!result) {
      // Show failure hint if perform action fail.
      pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
    }
    Intent intent = new Intent(GestureActionMonitor.ACTION_GESTURE_ACTION_PERFORMED);
    intent.putExtra(GestureActionMonitor.EXTRA_SHORTCUT_GESTURE_ACTION, action);
    LocalBroadcastManager.getInstance(service).sendBroadcast(intent);
  }

  public boolean isFingerprintGestureAssigned(int fingerprintGestureId) {
    return !TextUtils.equals(
        service.getString(R.string.shortcut_value_unassigned),
        actionFromFingerprintGesture(fingerprintGestureId));
  }

  public void onGesture(int gestureId, EventId eventId) {
    // TalkBack can ignore the gesture, which is handled by OnGestureListener.onCaptureGesture(), if
    // gesture listener exists and the current window is training.
    TrainingState trainingState = TrainingActivity.getTrainingState();
    if (trainingState != null && trainingState.getCurrentPage() != null && isOnTrainingPage()) {
      int feedbackResId = trainingState.getCurrentPage().intercepts(gestureId);
      String feedbackString;
      if (feedbackResId != UNKNOWN_ANNOUNCEMENT) {
        if (feedbackResId == ANNOUNCE_REAL_ACTION) {
          SpannableStringBuilder gestureAndAction = new SpannableStringBuilder();
          StringBuilderUtils.appendWithSeparator(
              gestureAndAction,
              GestureShortcutMapping.getGestureString(service, gestureId),
              GestureShortcutMapping.getActionString(
                  service, gestureShortcutMapping.getActionKeyFromGestureId(gestureId)));
          feedbackString = gestureAndAction.toString();
        } else {
          feedbackString = service.getString(feedbackResId);
        }
        pipeline.returnFeedback(
            /* eventId= */ null,
            Feedback.speech(
                feedbackString,
                SpeakOptions.create().setQueueMode(QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH)));
        return;
      }
    }

    String action = gestureShortcutMapping.getActionKeyFromGestureId(gestureId);
    LogUtils.v(
        LOG_TAG,
        "Recognized gesture id: %d [%s] , action: [%s]",
        gestureId,
        GestureShortcutMapping.getGestureString(service, gestureId),
        action);
    performAction(action, eventId);
  }

  /** Returns true if the active window is training activity. */
  private boolean isOnTrainingPage() {
    // Compares resource ID with all child nodes on the active window if navigate-up arrow is
    // shown because the first child node is the navigate-up arrow which is no resource ID.
    return WindowUtils.rootChildMatchesResId(service, TrainingActivity.ROOT_RES_ID);
  }

  /**
   * The interruptAllFeedback inside performAction is effective only when CRM is active.. Here to
   * perform an equivalent logic(by interruptGentle) to interrupt feedback when CRM is inactive.
   */
  public void onFingerprintGesture(int fingerprintGestureId, EventId eventId) {
    pipeline.returnFeedback(eventId, Feedback.part().setInterruptGentle(true));
    performAction(actionFromFingerprintGesture(fingerprintGestureId), eventId);
  }

  /**
   * Change the action if it's not allowed in continuous reading mode.
   *
   * @param action the action mapping from preference
   */
  private void maybeInterruptAllFeedback(String action) {
    if (!actorState.getContinuousRead().isActive()) {
      return;
    }

    if (action.equals(service.getString(R.string.shortcut_value_previous))
        || action.equals(service.getString(R.string.shortcut_value_next))
        || action.equals(service.getString(R.string.shortcut_value_unassigned))) {
      return;
    }
    service.interruptAllFeedback(false);
  }

  /** Checks if users have changed gestures manually. */
  public static boolean isAnyGestureChanged(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    String[] gestureKeys = context.getResources().getStringArray(R.array.pref_shortcut_keys);
    for (String key : gestureKeys) {
      if (prefs.contains(key)) {
        return true;
      }
    }
    return false;
  }

  /** Caller must recycle returned AccessibilityNode. */
  private @Nullable AccessibilityNodeInfoCompat getEditTextFocus() {
    @Nullable
    AccessibilityNodeInfoCompat node =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);
    if (Role.getRole(node) == Role.ROLE_EDIT_TEXT) {
      return node;
    } else {
      AccessibilityNodeInfoUtils.recycleNodes(node);
      speak(service.getString(R.string.not_editable));
      return null;
    }
  }

  /** voice feedback function */
  private void speak(String text) {
    SpeakOptions speakOptions =
        SpeakOptions.create()
            .setFlags(
                FeedbackItem.FLAG_NO_HISTORY
                    | FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                    | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                    | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE);
    pipeline.returnFeedback(EVENT_ID_UNTRACKED, Feedback.speech(text, speakOptions));
  }
}
