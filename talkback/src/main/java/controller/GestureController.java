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

package com.google.android.accessibility.talkback.controller;

import static com.google.android.accessibility.talkback.Feedback.EditText.Action.COPY;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.CURSOR_TO_BEGINNING;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.CUT;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.END_SELECT;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.INSERT;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.PASTE;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.SELECT_ALL;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.START_SELECT;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.CLICK;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.NEXT_GRANULARITY;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.NEXT_PAGE;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.PREVIOUS_GRANULARITY;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.PREVIOUS_PAGE;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_TOUCH;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_BACKWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.FingerprintGestureController;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Analytics;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.GestureShortcutMapping;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.contextmenu.MenuManager;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorVolumeStream;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.screensummary.SummaryOutput;
import com.google.android.accessibility.talkback.voicecommands.SpeechRecognitionManager;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.TreeDebug;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Class to handle incoming gestures to TalkBack. TODO: Remove Shortcut gesture action TODO:
 * Make sure tutorial still works TODO: Map actions to ints, and store in a map that changes
 * with prefs
 */
public class GestureController {

  private static final String LOG_TAG = "GestureController";

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final TalkBackService service;
  private final Pipeline.FeedbackReturner pipeline;
  private final ActorState actorState;
  private final FullScreenReadController fullScreenReadController;
  private final MenuManager menuManager;
  private final SelectorController selectorController;
  private SpeechRecognitionManager speechRecognitionManager;
  private final ProcessorVolumeStream processorVolumeStream;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;
  private GestureShortcutMapping gestureShortcutMapping;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Constructor methods

  public GestureController(
      TalkBackService service,
      Pipeline.FeedbackReturner pipeline,
      ActorState actorState,
      FullScreenReadController fullScreenReadController,
      MenuManager menuManager,
      SelectorController selectorController,
      SpeechRecognitionManager speechRecognitionManager,
      ProcessorVolumeStream processorVolumeStream,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      GestureShortcutMapping gestureShortcutMapping) {
    if (pipeline == null) {
      throw new IllegalStateException();
    }
    if (fullScreenReadController == null) {
      throw new IllegalStateException();
    }
    if (menuManager == null) {
      throw new IllegalStateException();
    }
    if (selectorController == null) {
      throw new IllegalStateException();
    }
    if (speechRecognitionManager == null) {
      throw new IllegalStateException();
    }
    if (processorVolumeStream == null) {
      throw new IllegalStateException();
    }

    this.pipeline = pipeline;
    this.actorState = actorState;
    this.fullScreenReadController = fullScreenReadController;
    this.menuManager = menuManager;
    this.service = service;
    this.selectorController = selectorController;
    this.speechRecognitionManager = speechRecognitionManager;
    this.processorVolumeStream = processorVolumeStream;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.gestureShortcutMapping = gestureShortcutMapping;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Voice-shortcut methods

  /**
   * Gets the results from RecognitionListener and performs the correct action.
   *
   * @return Handled flag
   */
  public boolean handleSpeechCommand(String command) {
    LogUtils.v(LOG_TAG, "handleSpeechCommand() command=\"%s\"", command);
    EventId eventId = EVENT_ID_UNTRACKED;

    // Next heading
    if (startsWith(command, R.string.voice_commands_next_heading)) {
      boolean result = pipeline.returnFeedback(eventId, Feedback.nextHeading(INPUT_MODE_TOUCH));

      if (!result) {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
      }

      // Editing
    } else if (startsWith(command, android.R.string.selectAll)) {
      @Nullable AccessibilityNodeInfoCompat node = getEditTextFocus();
      if (node != null) {
        pipeline.returnFeedback(eventId, Feedback.edit(node, SELECT_ALL));
      }
    } else if (startsWith(command, R.string.title_edittext_breakout_start_selection_mode)
        || startsWith(command, R.string.voice_commands_start_select)) {
      @Nullable AccessibilityNodeInfoCompat node = getEditTextFocus();
      if (node != null) {
        pipeline.returnFeedback(eventId, Feedback.edit(node, START_SELECT));
      }
    } else if (startsWith(command, R.string.title_edittext_breakout_end_selection_mode)
        || startsWith(command, R.string.voice_commands_finish_select)) {
      @Nullable AccessibilityNodeInfoCompat node = getEditTextFocus();
      if (node != null) {
        pipeline.returnFeedback(eventId, Feedback.edit(node, END_SELECT));
      }
    } else if (startsWith(command, android.R.string.copy)) {
      @Nullable AccessibilityNodeInfoCompat node = getEditTextFocus();
      if (node != null) {
        pipeline.returnFeedback(eventId, Feedback.edit(node, COPY));
      }
    } else if (startsWith(command, android.R.string.cut)) {
      @Nullable AccessibilityNodeInfoCompat node = getEditTextFocus();
      if (node != null) {
        pipeline.returnFeedback(eventId, Feedback.edit(node, CUT));
      }
    } else if (startsWith(command, android.R.string.paste)) {
      @Nullable AccessibilityNodeInfoCompat node = getEditTextFocus();
      if (node != null) {
        pipeline.returnFeedback(eventId, Feedback.edit(node, PASTE));
      }
    } else if (startsWith(command, R.string.voice_commands_deselect)) {
      @Nullable AccessibilityNodeInfoCompat node = getEditTextFocus();
      if (node != null) {
        pipeline.returnFeedback(
            eventId, Feedback.edit(node, CURSOR_TO_BEGINNING).setStopSelecting(true));
      }
    } else if (startsWith(command, R.string.voice_commands_dictate)) {
      @Nullable AccessibilityNodeInfoCompat node = getEditTextFocus();
      CharSequence text = remainder(command, R.string.voice_commands_dictate);
      if (node != null && !TextUtils.isEmpty(text)) {
        pipeline.returnFeedback(eventId, Feedback.edit(node, INSERT).setText(text));
      }
    } else if (contains(command, R.string.voice_commands_edit)
        || contains(command, R.string.title_edittext_controls)) {
      menuManager.showMenu(R.id.editing_menu, null);

      // Search screen
    } else if (startsWith(command, R.string.voice_commands_find_again)) {
      // "Repeat find": find same keyword again, starting from current accessibility-focused node.
      boolean found = pipeline.returnFeedback(eventId, Feedback.repeatSearch());
      if (!found) {
        speechRecognitionManager.speakDelayed(service, R.string.msg_no_matches);
      }
    } else if (startsWith(command, R.string.voice_commands_find)) {
      // "Find X": Find argument, starting from root (not focused node), so user need not navigate
      // to root before searching.
      boolean found = false;
      CharSequence target = remainder(command, R.string.voice_commands_find);
      if (!TextUtils.isEmpty(target)) {
        found = pipeline.returnFeedback(eventId, Feedback.searchFromTop(target));
      }
      if (!found) {
        speechRecognitionManager.speakDelayed(service, R.string.msg_no_matches);
      }

      // Assistant
    } else if (startsWith(command, R.string.voice_commands_assistant)) {
      service.startActivity(
          new Intent(Intent.ACTION_VOICE_COMMAND).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

      // Navigation
    } else if (containsAll(command, R.string.voice_commands_read, R.string.voice_commands_top)
        || contains(command, R.string.shortcut_read_from_top)) {
      fullScreenReadController.startReadingFromBeginning(eventId);
    } else if (containsAll(command, R.string.voice_commands_read, R.string.voice_commands_next)
        || contains(command, R.string.shortcut_read_from_current)) {
      fullScreenReadController.startReadingFromNextNode(eventId);
    } else if (contains(command, R.string.keycombo_menu_global_home)) {
      service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
    } else if (contains(command, R.string.voice_commands_recent)
        || contains(command, R.string.keycombo_menu_global_recent)) {
      service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
    } else if (contains(command, R.string.value_stream_notification)
        || contains(command, R.string.keycombo_menu_global_notifications)) {
      service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
    } else if (contains(command, R.string.voice_commands_setting)
        || contains(command, R.string.shortcut_quick_settings)) {
      service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
    } else if (contains(command, R.string.voice_commands_language)
        || contains(command, R.string.language_options)) {
      menuManager.showMenu(R.menu.language_menu, eventId);
    } else if (contains(command, R.string.voice_commands_action)
        || contains(command, R.string.title_custom_action)) {
      menuManager.showMenu(R.id.custom_action_menu, eventId);

      // Miscellaneous
    } else if (contains(command, R.string.voice_commands_summary)) {
      if (TalkBackService.ENABLE_SUMMARIZATION) {
        SummaryOutput.showOutput(service);
      }
    } else if (contains(command, R.string.voice_commands_brighten_screen)
        || contains(command, R.string.shortcut_disable_dimming)) {
      if (DimScreenControllerApp.isSupported(service)) {
        service.getDimScreenController().disableDimming();
        // TODO: Cleanup this code by removing toast and speakDelayed.
        // Announcement should be done only by DimScreenController.
        //  suppresses toast feedback and hence speakDelayed is added.
        speechRecognitionManager.speakDelayed(service, R.string.screen_brightness_restored);
      }
    } else if (contains(command, R.string.voice_commands_dim_screen)
        || contains(command, R.string.shortcut_enable_dimming)) {
      if (DimScreenControllerApp.isSupported(service)) {
        boolean dialogShown = service.getDimScreenController().enableDimmingAndShowConfirmDialog();
        // TODO: Cleanup this code by removing toast and speakDelayed.
        // Announcement should be done only by DimScreenController.
        // If the dialog is shown, announcement is done by DimScreenController. The time to process
        // the dialog is enough for the ssb to be completely inactive.
        if (service.getDimScreenController().isDimmingEnabled() || !dialogShown) {
          //  suppresses toast feedback and hence speakDelayed is added.
          speechRecognitionManager.speakDelayed(service, R.string.screen_dimmed);
        }
      }

      // Help
    } else if (containsAll(command, R.string.voice_commands_what, R.string.voice_commands_say)
        || contains(command, R.string.title_pref_help)) {
      speechRecognitionManager.showCommandsList(service);
    } else {
      speechRecognitionManager.speakDelayed(
          service.getString(
              R.string.voice_commands_partial_result, service.getString(R.string.title_pref_help)));
      return false;
    }
    return true;
  }

  private boolean containsAll(String command, int stringResId1, int stringResId2) {
    return contains(command, stringResId1) && contains(command, stringResId2);
  }

  private boolean contains(String command, int stringResId) {
    return command.contains(service.getString(stringResId).toLowerCase());
  }

  private boolean startsWith(String command, int stringResId) {
    return command.startsWith(service.getString(stringResId).toLowerCase());
  }

  /* Returns the tail of command-string after the stringResId prefix. */
  private CharSequence remainder(String command, int stringResId) {
    CharSequence prefix = service.getString(stringResId).toLowerCase();
    return command.substring(prefix.length());
  }

  private @Nullable AccessibilityNodeInfoCompat getEditTextFocus() {
    @Nullable
    AccessibilityNodeInfoCompat node =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);
    if (Role.getRole(node) == Role.ROLE_EDIT_TEXT) {
      return node;
    } else {
      speechRecognitionManager.speakDelayed(service, R.string.voice_commands_not_editable);
      return null;
    }
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
    if (action.equals(service.getString(R.string.shortcut_value_unassigned))) {
      // Do Nothing
    } else if (action.equals(service.getString(R.string.shortcut_value_previous))) {
      boolean result =
          pipeline.returnFeedback(
              eventId,
              Feedback.focusDirection(SEARCH_FOCUS_BACKWARD)
                  .setInputMode(INPUT_MODE_TOUCH)
                  .setDefaultToInputFocus(true)
                  .setScroll(true)
                  .setWrap(true));
      if (!result) {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
      }
    } else if (action.equals(service.getString(R.string.shortcut_value_next))) {
      boolean result =
          pipeline.returnFeedback(
              eventId,
              Feedback.focusDirection(SEARCH_FOCUS_FORWARD)
                  .setInputMode(INPUT_MODE_TOUCH)
                  .setDefaultToInputFocus(true)
                  .setScroll(true)
                  .setWrap(true));
      if (!result) {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
      }
    } else if (action.equals(service.getString(R.string.shortcut_value_scroll_back))) {
      boolean result = pipeline.returnFeedback(eventId, Feedback.focusDirection(PREVIOUS_PAGE));
      if (!result) {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
      }
    } else if (action.equals(service.getString(R.string.shortcut_value_scroll_forward))) {
      boolean result = pipeline.returnFeedback(eventId, Feedback.focusDirection(NEXT_PAGE));
      if (!result) {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
      }
    } else if (action.equals(service.getString(R.string.shortcut_value_first_in_screen))) {
      boolean result = pipeline.returnFeedback(eventId, Feedback.focusTop(INPUT_MODE_TOUCH));
      if (!result) {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
      }
    } else if (action.equals(service.getString(R.string.shortcut_value_last_in_screen))) {
      boolean result = pipeline.returnFeedback(eventId, Feedback.focusBottom(INPUT_MODE_TOUCH));
      if (!result) {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
      }
    } else if (action.equals(service.getString(R.string.shortcut_value_back))) {
      service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
    } else if (action.equals(service.getString(R.string.shortcut_value_home))) {
      service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
    } else if (TalkBackService.ENABLE_SUMMARIZATION
        && action.equals(service.getString(R.string.shortcut_value_summary))) {
      SummaryOutput.showOutput(service);
    } else if (TalkBackService.ENABLE_VOICE_COMMANDS
        && action.equals(service.getString(R.string.shortcut_value_voice_commands))) {
      // TODO: Maybe disable when device is locked (KeyguardManager.isDeviceLocked()).
      if (!speechRecognitionManager.isListening()) {
        if (!speechRecognitionManager.hasMicPermission()) {
          speechRecognitionManager.getSpeechPermissions();
        } else {
          speechRecognitionManager.startListening();
        }
      }
    } else if (action.equals(service.getString(R.string.shortcut_value_overview))) {
      service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
    } else if (action.equals(service.getString(R.string.shortcut_value_notifications))) {
      service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
    } else if (action.equals(service.getString(R.string.shortcut_value_quick_settings))) {
      service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
    } else if (action.equals(service.getString(R.string.shortcut_value_talkback_breakout))) {
      menuManager.showMenu(R.menu.global_context_menu, eventId);
    } else if (action.equals(service.getString(R.string.shortcut_value_local_breakout))) {
      menuManager.showMenu(R.menu.local_context_menu, eventId);
    } else if (action.equals(service.getString(R.string.shortcut_value_show_custom_actions))) {
      menuManager.showMenu(R.id.custom_action_menu, eventId);
    } else if (action.equals(service.getString(R.string.shortcut_value_editing))) {
      menuManager.showMenu(R.id.editing_menu, eventId);
    } else if (action.equals(service.getString(R.string.shortcut_value_show_language_options))) {
      menuManager.showMenu(R.menu.language_menu, eventId);
    } else if (action.equals(service.getString(R.string.shortcut_value_previous_granularity))) {
      boolean result =
          pipeline.returnFeedback(eventId, Feedback.focusDirection(PREVIOUS_GRANULARITY));
      if (result) {
        service
            .getAnalytics()
            .onGranularityChanged(
                actorState.getDirectionNavigation().getCurrentGranularity(),
                Analytics.TYPE_GESTURE,
                /* isPending= */ true);
      } else {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
      }
    } else if (action.equals(service.getString(R.string.shortcut_value_next_granularity))) {
      boolean result = pipeline.returnFeedback(eventId, Feedback.focusDirection(NEXT_GRANULARITY));
      if (result) {
        service
            .getAnalytics()
            .onGranularityChanged(
                actorState.getDirectionNavigation().getCurrentGranularity(),
                Analytics.TYPE_GESTURE,
                /* isPending= */ true);
      } else {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
      }
    } else if (action.equals(service.getString(R.string.shortcut_value_read_from_top))) {
      fullScreenReadController.startReadingFromBeginning(eventId);
    } else if (action.equals(service.getString(R.string.shortcut_value_read_from_current))) {
      fullScreenReadController.startReadingFromNextNode(eventId);
    } else if (action.equals(service.getString(R.string.shortcut_value_print_node_tree))) {
      TreeDebug.logNodeTrees(AccessibilityServiceCompatUtils.getWindows(service));
    } else if (action.equals(service.getString(R.string.shortcut_value_print_performance_stats))) {
      Performance.getInstance().displayLabelToStats();
      Performance.getInstance().displayStatToLabelCompare();
      Performance.getInstance().displayAllEventStats();
    } else if (action.equals(service.getString(R.string.shortcut_value_perform_click_action))) {
      pipeline.returnFeedback(eventId, Feedback.focus(CLICK));
    } else if (action.equals(service.getString(R.string.shortcut_value_select_previous_setting))) {
      selectorController.selectPreviousOrNextSetting(eventId, false);
    } else if (action.equals(service.getString(R.string.shortcut_value_select_next_setting))) {
      selectorController.selectPreviousOrNextSetting(eventId, true);
    } else if (action.equals(
        service.getString(R.string.shortcut_value_selected_setting_previous_action))) {
      selectorController.performSelectedSettingAction(eventId, false);
    } else if (action.equals(
        service.getString(R.string.shortcut_value_selected_setting_next_action))) {
      selectorController.performSelectedSettingAction(eventId, true);
    } else if (action.equals(service.getString(R.string.shortcut_value_screen_search))) {
      service.getUniversalSearchManager().toggleSearch(eventId);
    } else if (action.equals(service.getString(R.string.shortcut_value_headphone_navigation))) {
      processorVolumeStream.toggleNavigationMode();
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
    String action = gestureShortcutMapping.getActionKeyFromGestureId(gestureId);
    performAction(action, eventId);
  }

  public void onFingerprintGesture(int fingerprintGestureId, EventId eventId) {
    String action = actionFromFingerprintGesture(fingerprintGestureId);
    performAction(action, eventId);
  }

  /**
   * Change the action if it's not allowed in continuous reading mode.
   *
   * @param action the action mapping from preference
   */
  private void maybeInterruptAllFeedback(String action) {
    if (!fullScreenReadController.isActive()) {
      return;
    }

    if (action.equals(service.getString(R.string.shortcut_value_previous))
        || action.equals(service.getString(R.string.shortcut_value_next))
        || action.equals(service.getString(R.string.shortcut_value_unassigned))) {
      return;
    }
    service.interruptAllFeedback(false);
  }
}
