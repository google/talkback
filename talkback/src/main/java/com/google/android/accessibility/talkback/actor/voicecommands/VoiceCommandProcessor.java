/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.accessibility.talkback.actor.voicecommands;

import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_ALL_APPS;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_BACK;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_BRIGHTEN_SCREEN;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_COPY;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_CUT;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_DELETE;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_DIM_SCREEN;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_END_SELECT;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_FIND;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_FIRST;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_HOME;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_INSERT;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_LABEL;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_LAST;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_NEXT_GRANULARITY;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_NOTIFICATIONS;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_PASTE;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_QUICK_SETTINGS;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_RECENT;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_REPEAT_SEARCH;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_SELECT_ALL;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_SHOW_COMMAND_LIST;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_START_AT_NEXT;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_START_AT_TOP;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_START_SELECT;
import static com.google.android.accessibility.talkback.actor.voicecommands.SpeechRecognizerActor.RECOGNITION_SPEECH_DELAY_MS;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.VOICE_COMMAND_RECOGNIZED;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.VOICE_COMMAND_UNRECOGNIZED;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Intent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import com.android.talkback.TalkBackPreferencesActivity;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Interpretation;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.actor.DimScreenActor;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.contextmenu.ListMenuManager;
import com.google.android.accessibility.talkback.controller.SelectorController;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.menurules.RuleCustomAction;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.LocaleUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.SpeechCleanupUtils;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

// TODO: Refactoring this class as a feedback-mapper class
/** Class to handle voice commands and calls from {@link SpeechRecognizerActor}. */
public class VoiceCommandProcessor {

  private static final String LOG_TAG = "VoiceCommandProcessor";

  private final TalkBackService service;
  private Pipeline.FeedbackReturner pipeline;
  private Pipeline.InterpretationReceiver interpretationReceiver;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;
  private ListMenuManager menuManager;
  private final SelectorController selectorController;
  private final TalkBackAnalytics analytics;

  private boolean echoNotRecognizedTextEnabled;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  private List<String> verbosityCommandList;
  private List<String> granularityCommandList;

  private static final int[] typeCommandResArray = {
    R.string.voice_commands_type,
    R.string.voice_commands_input,
    R.string.voice_commands_dictate,
    R.string.voice_commands_write,
    R.string.voice_commands_Spell
  };

  private static final int[] talkbackSettingCommandResArray = {
    R.string.voice_commands_talkback_settings, R.string.voice_commands_talkback_setting
  };

  private static final int[] finishSelectCommandResArray = {
    R.string.voice_commands_finish_select,
    R.string.voice_commands_finish_selection_mode,
    R.string.voice_commands_finish_selection,
    R.string.voice_commands_end_select
  };

  private static final int[] selectCommandResArray = {
    R.string.voice_commands_select,
    R.string.voice_commands_start_select,
    R.string.voice_commands_start_selection,
    R.string.voice_commands_start_selection_mode
  };

  private static final int[] languageCommandResArray = {
    R.string.voice_commands_language, R.string.voice_commands_languages
  };

  private static final int[] actionsCommandResArray = {
    R.string.title_custom_action,
    R.string.voice_commands_custom_actions,
    R.string.voice_commands_action
  };

  private static final int[] quickSettingCommandResArray = {
    R.string.voice_commands_quick_settings, R.string.voice_commands_quick_setting
  };

  private static final int[] showScreenCommandResArray = {
    R.string.shortcut_disable_dimming,
    R.string.voice_commands_brighten_screen,
    R.string.voice_commands_restore_screen,
    R.string.voice_commands_cancel_hide_screen
  };

  private static final int[] notificationsCommandResArray = {
    R.string.voice_commands_notification, R.string.voice_commands_notifications
  };

  private static final int[] hideScreenCommandResArray = {
    R.string.voice_commands_dim, R.string.voice_commands_darken
  };

  private static final int[] readFromNextCommandResArray = {
    R.string.shortcut_read_from_current, R.string.voice_commands_read_from_next
  };

  private static final int[] editOptionsCommandResArray = {
    R.string.voice_commands_edit_options, R.string.voice_commands_text_editing,
    R.string.voice_commands_edit_text, R.string.voice_commands_editing_options
  };

  private static final int[] overviewCommandResArray = {
    R.string.voice_commands_overview, R.string.voice_commands_recent_apps,
    R.string.voice_commands_recent, R.string.voice_commands_recents
  };

  private static final int[] verbosityCommandArray = {
    R.string.voice_commands_verbosity_parameter,
    R.string.voice_commands_parameter_verbosity,
    R.string.voice_commands_change_verbosity_to_parameter
  };

  private static final int[] verbosityParameters = {
    R.string.pref_verbosity_preset_entry_high,
    R.string.pref_verbosity_preset_entry_custom,
    R.string.pref_verbosity_preset_entry_low,
    R.string.voice_commands_homophone_high_and_hi
  };

  private static final int[] granularityCommandArray = {
    R.string.voice_commands_navigation_by_parameter,
    R.string.voice_commands_parameter_navigation,
    R.string.voice_commands_parameter_granularity,
    R.string.voice_commands_read_by_parameter
  };

  // This sequence of granularity mode should always aligns with
  // SelectorController.SELECTOR_SETTINGS
  private static final int[] granularityModeArray = {
    R.string.granularity_character,
    R.string.granularity_word,
    R.string.granularity_line,
    R.string.granularity_paragraph,
    R.string.granularity_web_heading, // headings
    R.string.granularity_web_control, // controls
    R.string.granularity_web_landmark, // landmarks
    R.string.granularity_default
  };

  private static final int[] findCommandResArray = {
    R.string.voice_commands_find, R.string.voice_commands_search_for, R.string.voice_commands_search
  };

  public VoiceCommandProcessor(
      TalkBackService service,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      SelectorController selectorController,
      TalkBackAnalytics analytics) {
    this.service = service;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.selectorController = selectorController;
    this.analytics = analytics;
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  public void setPipelineInterpretationReceiver(
      Pipeline.InterpretationReceiver interpretationReceiver) {
    this.interpretationReceiver = interpretationReceiver;
  }

  public void setListMenuManager(ListMenuManager menuManager) {
    this.menuManager = menuManager;
  }

  public void setEchoRecognizedTextEnabled(boolean enable) {
    echoNotRecognizedTextEnabled = enable;
  }

  private void dimScreenVoiceCommand(EventId eventId) {
    if (DimScreenActor.isSupported(service)) {
      sendInterpretation(VOICE_COMMAND_DIM_SCREEN, eventId);
    }
  }

  /* Returns true if the recognized string is one of the voice commands. */
  public boolean handleSpeechCommand(String command) {
    if (TextUtils.isEmpty(command)) {
      return false;
    }
    LogUtils.i(LOG_TAG, "handleSpeechCommand() command=\"%s\"", command);
    EventId eventId = EVENT_ID_UNTRACKED;

    @Nullable AccessibilityNodeInfoCompat node = null;
    // select all voice command
    // command format: Select all
    if (equals(command, android.R.string.selectAll)) {
      node = getEditTextFocus();
      try {
        if (node != null) {
          sendInterpretation(VOICE_COMMAND_SELECT_ALL, node, eventId);
        }
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }

      handleVoiceCommandRecognized();
      return true;
    }

    // hide screen command
    // command format: Hide screen,
    if (equals(command, R.string.shortcut_enable_dimming)) {
      dimScreenVoiceCommand(eventId);
      handleVoiceCommandRecognized();
      return true;
    }

    // Edit options voice command
    // command format: Edit options, Text Editing, Edit text, Editing options
    int editOptionsCommand = equals(command, editOptionsCommandResArray);
    if (editOptionsCommand >= 0) {
      menuManager.showMenu(R.id.editing_menu, null, R.string.not_editable);
      handleVoiceCommandRecognized();
      return true;
    }

    // finish selection mode voice command
    // command format: Finish select, Finish selection, Finish selection mode, End select
    int finishSelectCommand = equals(command, finishSelectCommandResArray);
    if (finishSelectCommand >= 0) {
      node = getEditTextFocus();
      try {
        if (node != null) {
          // TODO Separate VoiceCommandProcessor as feedback mapper and
          // command-pattern-matching.
          sendInterpretation(VOICE_COMMAND_END_SELECT, node, eventId);
        }
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }

      handleVoiceCommandRecognized();
      return true;
    }

    // screen search voice command
    // command format: screen search, search on screen
    if (!FeatureSupport.isWatch(service)
        && (equals(command, R.string.voice_commands_screen_search)
            || equals(command, R.string.voice_commands_search_on_screen))) {
      service.getUniversalSearchManager().toggleSearch(eventId);
      handleVoiceCommandRecognized();
      return true;
    }

    // select voice command
    // command format: select, start select, start selection mode, start selection
    int selectCommand = equals(command, selectCommandResArray);
    if (selectCommand >= 0) {
      node = getEditTextFocus();
      try {
        if (node != null) {
          sendInterpretation(VOICE_COMMAND_START_SELECT, node, eventId);
        }
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }

      handleVoiceCommandRecognized();
      return true;
    }

    // actions voice command
    // command format: actions, custom actions
    int actionCommand = equals(command, actionsCommandResArray);
    if (actionCommand >= 0) {
      node = accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);
      try {
        RuleCustomAction ruleCustomAction = new RuleCustomAction(pipeline, analytics);
        List<ContextMenuItem> menuItems =
            ruleCustomAction.getMenuItemsForNode(service, node, /* includeAncestors= */ true);
        if (node == null || menuItems.size() == 0) {
          menuManager.showMenu(
              R.id.custom_action_menu, eventId, R.string.voice_commands_no_actions_feedback);
        } else {
          menuManager.showMenu(R.id.custom_action_menu, eventId);
        }
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }

      handleVoiceCommandRecognized();
      return true;
    }

    // next heading voice command
    // command format: next heading
    if (equals(command, R.string.voice_commands_next_heading)) {
      boolean result;
      node = accessibilityFocusMonitor.getAccessibilityFocus(false);
      try {
        boolean isWebElement = WebInterfaceUtils.supportsWebActions(node);
        result =
            sendInterpretation(
                VOICE_COMMAND_NEXT_GRANULARITY,
                isWebElement ? CursorGranularity.WEB_HEADING : null,
                eventId);
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }

      if (!result) {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
        speakDelayed(service.getString(R.string.voice_commands_no_next_heading_feedback));
      }

      handleVoiceCommandRecognized();
      return true;
    }

    // next control voice command
    // command format: next control
    if (equals(command, R.string.voice_commands_next_control)) {
      boolean result;

      node = accessibilityFocusMonitor.getAccessibilityFocus(false);
      try {
        boolean isWebElement = WebInterfaceUtils.supportsWebActions(node);

        result =
            sendInterpretation(
                VOICE_COMMAND_NEXT_GRANULARITY,
                isWebElement ? CursorGranularity.WEB_CONTROL : CursorGranularity.CONTROL,
                eventId);
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }

      if (!result) {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
        speakDelayed(service.getString(R.string.voice_commands_no_next_control_feedback));
      }
      handleVoiceCommandRecognized();
      return true;
    }

    // next link voice command
    // command format: next link
    if (equals(command, R.string.voice_commands_next_link)) {
      boolean result;
      node = accessibilityFocusMonitor.getAccessibilityFocus(false);
      try {
        boolean isWebElement = WebInterfaceUtils.supportsWebActions(node);
        result =
            sendInterpretation(
                VOICE_COMMAND_NEXT_GRANULARITY,
                isWebElement ? CursorGranularity.WEB_LINK : CursorGranularity.LINK,
                eventId);
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }

      if (!result) {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
        speakDelayed(service.getString(R.string.voice_commands_no_next_link_feedback));
      }
      handleVoiceCommandRecognized();
      return true;
    }

    // verbosity command
    // support command format:
    //             low/high/custom verbosity
    //             verbosity low/high/custom
    //             change verbosity to low/high/custom
    if (verbosityCommandList == null) {
      verbosityCommandList = getCommandList(verbosityCommandArray, verbosityParameters);
    }
    String verbosityCommand = equals(command, verbosityCommandList);
    if (verbosityCommand != null) {
      int verbosityCommandIndex = contains(verbosityCommand, verbosityParameters);
      // TODO workaround for the homophones high and hi.
      if (LocaleUtils.isDefaultLocale(LocaleUtils.LANGUAGE_EN)
          && containsWord(verbosityCommand, R.string.voice_commands_homophone_high_and_hi)) {
        verbosityCommandIndex = 0;
      }
      if (verbosityCommandIndex >= 0) {
        selectorController.changeVerbosity(eventId, verbosityCommandIndex);
      }

      handleVoiceCommandRecognized();
      return true;
    }

    // word/characters/line/etc navigation set granularity command
    // command format: navigation by %s, %s granularity, read by %s
    if (granularityCommandList == null) {
      granularityCommandList = getCommandList(granularityCommandArray, granularityModeArray);
    }
    String granularityCommand = equals(command, granularityCommandList);
    if (granularityCommand != null) {
      int granularityCommandIndex = contains(granularityCommand, granularityModeArray);

      if (granularityCommandIndex >= 0) {
        // TODO Apply selector-changes to pipeline on VoiceCommandProcessor.
        selectorController.selectSetting(
            SelectorController.SELECTOR_SETTINGS.get(granularityCommandIndex),
            /* showOverlay= */ false);
      }
      handleVoiceCommandRecognized();
      return true;
    }

    // next landmark voice command
    // command format: next landmark
    if (equals(command, R.string.voice_commands_next_landmark)) {
      boolean result = false;

      node = accessibilityFocusMonitor.getAccessibilityFocus(false);
      try {
        boolean isWebElement = WebInterfaceUtils.supportsWebActions(node);
        if (isWebElement) {
          result =
              sendInterpretation(
                  VOICE_COMMAND_NEXT_GRANULARITY, CursorGranularity.WEB_LANDMARK, eventId);
        }
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }

      if (!result) {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
        speakDelayed(service.getString(R.string.voice_commands_no_next_landmark_feedback));
      }

      handleVoiceCommandRecognized();
      return true;
    }

    // show screen command
    // command format:show screen, brighten screen, restore screen, cancel hide screen
    int showScreenCommand = equals(command, showScreenCommandResArray);
    if (showScreenCommand >= 0) {
      if (DimScreenActor.isSupported(service)) {
        sendInterpretation(VOICE_COMMAND_BRIGHTEN_SCREEN, eventId);
      }
      handleVoiceCommandRecognized();
      return true;
    }

    // back voice command
    // command format: back, go back
    if (equals(command, R.string.voice_commands_back)
        || equals(command, R.string.voice_commands_go_back)) {
      sendInterpretation(VOICE_COMMAND_BACK, eventId);
      handleVoiceCommandRecognized();
      return true;
    }

    // talk faster voice command
    // command format: increase speech rate
    if (equals(command, R.string.voice_commands_increase_speech_rate)) {
      selectorController.changeSpeechRate(eventId, /* isIncrease= */ true);
      handleVoiceCommandRecognized();
      return true;
    }
    // talk slower voice command
    // command format: decrease speech rate
    if (equals(command, R.string.voice_commands_decrease_speech_rate)) {
      selectorController.changeSpeechRate(eventId, /* isIncrease= */ false);
      handleVoiceCommandRecognized();
      return true;
    }

    // find voice command
    // command format: find *, search for *, search *, find
    int findCommand = startsWith(command, findCommandResArray);
    if (findCommand >= 0) {
      // "Find X": Find argument, starting from root (not focused node), so user need not navigate
      // to root before searching.
      boolean found = false;
      final CharSequence text = remainder(command, findCommand);
      if (TextUtils.isEmpty(text)) {
        found = sendInterpretation(VOICE_COMMAND_REPEAT_SEARCH, eventId);
      } else {
        found = sendInterpretation(VOICE_COMMAND_FIND, text, eventId);
      }
      if (!found) {
        speakDelayed(service.getString(R.string.msg_no_matches));
      }
      handleVoiceCommandRecognized();
      return true;
    }

    // type voice command
    // command format: Type *, Input *, Dictate *, Write *, Spell *
    int inputCommand = startsWith(command, typeCommandResArray);
    if (inputCommand >= 0) {
      node = getEditTextFocus();
      try {
        if (node != null) {
          CharSequence inputText = remainder(command, inputCommand);
          if (!TextUtils.isEmpty(inputText)) {
            sendInterpretation(VOICE_COMMAND_INSERT, node, inputText, eventId);
          }
        }
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }
      handleVoiceCommandRecognized();
      return true;
    }

    // label voice command
    // command format: Label *
    if (startsWith(command, R.string.voice_commands_label)) {
      CharSequence label = remainder(command, R.string.voice_commands_label);
      label = SpeechCleanupUtils.trimText(label);
      node = accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
      try {
        if (node != null && !TextUtils.isEmpty(label)) {
          boolean success = sendInterpretation(VOICE_COMMAND_LABEL, node, label, eventId);
          if (success) {
            String successFeedback = service.getString(R.string.voice_commands_label_saved);
            pipeline.returnFeedback(
                eventId, Feedback.speech(successFeedback, SpeakOptions.create()).setDelayMs(500));
          } else {
            speakDelayed(service.getString(R.string.voice_commands_cannot_label_feedback));
          }
        }
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }
      handleVoiceCommandRecognized();
      return true;
    }

    // read from next voice command
    // command format: Read from next item, Read from next
    int readFromNextCommand = startsWith(command, readFromNextCommandResArray);
    if (readFromNextCommand >= 0) {
      sendInterpretation(VOICE_COMMAND_START_AT_NEXT, eventId);
      handleVoiceCommandRecognized();
      return true;
    }

    // read from top voice command
    // command format: Read from top
    if (startsWith(command, R.string.shortcut_read_from_top)) {
      sendInterpretation(VOICE_COMMAND_START_AT_TOP, eventId);
      handleVoiceCommandRecognized();
      return true;
    }

    // quick setting voice command
    // command format: * quick setting *, * quick settings *
    int quickSettingCommand = contains(command, quickSettingCommandResArray);
    if (quickSettingCommand >= 0) {
      sendInterpretation(VOICE_COMMAND_QUICK_SETTINGS, eventId);
      handleVoiceCommandRecognized();
      return true;
    }

    // start talkback setting voice command
    // command format: * talkback setting *, * talkback settings *
    int talkbackSettingCommand = contains(command, talkbackSettingCommandResArray);
    if (talkbackSettingCommand >= 0) {
      Intent intent = new Intent(service, TalkBackPreferencesActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      service.startActivity(intent);
      handleVoiceCommandRecognized();
      return true;
    }

    // hide screen command
    // command format: * dim *, * darken *
    if (contains(command, hideScreenCommandResArray) >= 0) {
      dimScreenVoiceCommand(eventId);
      handleVoiceCommandRecognized();
      return true;
    }

    // copy voice command
    // command format: * copy *
    if (containsWord(command, R.string.voice_commands_copy)) {
      node = getEditTextFocus();
      try {
        if (node != null) {
          sendInterpretation(VOICE_COMMAND_COPY, node, eventId);
        }
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }

      handleVoiceCommandRecognized();
      return true;
    }

    // paste voice command
    // command format: * paste *
    if (containsWord(command, R.string.voice_commands_paste)) {
      node = getEditTextFocus();
      try {
        if (node != null) {
          sendInterpretation(VOICE_COMMAND_PASTE, node, eventId);
        }
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }

      handleVoiceCommandRecognized();
      return true;
    }

    // delete voice command
    // command format: * delete *
    if (containsWord(command, R.string.voice_commands_delete)) {
      node = getEditTextFocus();
      try {
        if (node != null) {
          sendInterpretation(VOICE_COMMAND_DELETE, node, eventId);
        }
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }

      handleVoiceCommandRecognized();
      return true;
    }

    // first voice command
    // command format: * first *, * top *
    if (containsWord(command, R.string.voice_commands_first)
        || (containsWord(command, R.string.voice_commands_top))) {
      boolean result = sendInterpretation(VOICE_COMMAND_FIRST, eventId);
      if (!result) {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
      }
      handleVoiceCommandRecognized();
      return true;
    }

    // last voice command
    // command format: * last *, * bottom *
    if (containsWord(command, R.string.voice_commands_last)
        || containsWord(command, R.string.voice_commands_bottom)) {
      boolean result = sendInterpretation(VOICE_COMMAND_LAST, eventId);
      if (!result) {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
      }
      handleVoiceCommandRecognized();
      return true;
    }

    // language voice command
    // command format: * language *, * languages *
    int languageCommmand = contains(command, languageCommandResArray);
    if (languageCommmand >= 0) {
      menuManager.showMenu(R.menu.language_menu, eventId);
      handleVoiceCommandRecognized();
      return true;
    }

    // notifications command
    // command format: * notification *, * notifications *
    int notificationCommand = contains(command, notificationsCommandResArray);
    if (notificationCommand >= 0) {
      boolean result = sendInterpretation(VOICE_COMMAND_NOTIFICATIONS, eventId);
      if (!result) {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
      }

      handleVoiceCommandRecognized();
      return true;
    }

    // all apps command
    // command format: * apps *
    if (containsWord(command, R.string.voice_commands_apps)
        && FeatureSupport.supportSystemActions()
        && !containsWord(command, R.string.voice_commands_recent)
        && !containsWord(command, R.string.voice_commands_recents)) {
      boolean result = sendInterpretation(VOICE_COMMAND_ALL_APPS, eventId);
      if (!result) {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
      }
      handleVoiceCommandRecognized();
      return true;
    }

    // Overview command
    // command format: " recent apps *, * recents *, * recent *, * overview *
    int overviewCommand = contains(command, overviewCommandResArray);
    if (overviewCommand >= 0) {
      boolean result = sendInterpretation(VOICE_COMMAND_RECENT, eventId);
      if (!result) {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
      }
      handleVoiceCommandRecognized();
      return true;
    }

    // assistant voice command
    // command format: * assistant *
    if (containsWord(command, R.string.voice_commands_assistant)) {
      service.startActivity(
          new Intent(Intent.ACTION_VOICE_COMMAND).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      handleVoiceCommandRecognized();
      return true;
    }

    // home voice command
    // command format: * home *, * desktop *
    if (containsWord(command, R.string.voice_commands_home)
        || containsWord(command, R.string.voice_commands_desktop)) {
      sendInterpretation(VOICE_COMMAND_HOME, eventId);
      handleVoiceCommandRecognized();
      return true;
    }

    // stop voice command
    // command format: * stop *, * quit *, * quite *, " pause *, nevermind, shut up
    if (containsWord(command, R.string.voice_commands_stop)
        || containsWord(command, R.string.voice_commands_quit)
        || containsWord(command, R.string.voice_commands_quiet)
        || containsWord(command, R.string.voice_commands_pause)
        || equals(command, R.string.voice_commands_nevermind)
        || equals(command, R.string.voice_commands_shut_up)) {
      handleVoiceCommandRecognized();
      return true;
    }

    // talk faster voice command
    // command format: * faster *, increase speech rate
    if (containsWord(command, R.string.voice_commands_faster)) {
      selectorController.changeSpeechRate(eventId, /* isIncrease= */ true);
      handleVoiceCommandRecognized();
      return true;
    }

    // talk slower voice command
    // command format: * slower *, decrease speech rate
    if (containsWord(command, R.string.voice_commands_slower)) {
      selectorController.changeSpeechRate(eventId, /* isIncrease= */ false);
      handleVoiceCommandRecognized();
      return true;
    }

    // cut voice command
    // command format: * cut *
    if (containsWord(command, R.string.voice_commands_cut)) {
      node = getEditTextFocus();
      try {
        if (node != null) {
          sendInterpretation(VOICE_COMMAND_CUT, node, eventId);
        }
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }

      handleVoiceCommandRecognized();
      return true;
    }

    // help voice command
    // command format: * help *, * what * && * say *
    if (containsAll(command, R.string.voice_commands_what, R.string.voice_commands_say)
        || containsWord(command, R.string.title_pref_help)) {
      sendInterpretation(VOICE_COMMAND_SHOW_COMMAND_LIST, eventId);
      handleVoiceCommandRecognized();
      return true;
    }

    if (echoNotRecognizedTextEnabled) {
      speakDelayed(
          service.getString(R.string.voice_commands_echo_feedback_not_recognized, command));
    } else {
      speakDelayed(
          service.getString(
              R.string.voice_commands_partial_result, service.getString(R.string.title_pref_help)));
    }
    analytics.onVoiceCommandEvent(VOICE_COMMAND_UNRECOGNIZED);

    return false;
  }

  private List<String> getCommandList(int[] formattedCommandArray, int[] parameterArrays) {
    List<String> commandList = new ArrayList<>();
    for (int i = 0; i < formattedCommandArray.length; i++) {
      for (int j = 0; j < parameterArrays.length; j++) {
        commandList.add(
            service.getString(formattedCommandArray[i], service.getString(parameterArrays[j])));
      }
    }

    return commandList;
  }

  private void handleVoiceCommandRecognized() {
    analytics.onVoiceCommandEvent(VOICE_COMMAND_RECOGNIZED);
  }

  private boolean containsAll(String command, int stringResId1, int stringResId2) {
    return containsWord(command, stringResId1) && containsWord(command, stringResId2);
  }

  private int contains(String command, int[] stringResIdArray) {
    if (command == null || stringResIdArray == null) {
      return -1;
    }
    for (int i = 0; i < stringResIdArray.length; i++) {
      if (containsWord(command, stringResIdArray[i])) {
        return i;
      }
    }
    return -1;
  }

  private boolean containsWord(String command, int stringResId) {
    String[] commandSplit = command.split("\\s|\\p{Punct}");
    if (equals((command), stringResId)) {
      return true;
    }
    for (int i = 0; i < commandSplit.length; i++) {
      if (equals(commandSplit[i], stringResId)) {
        return true;
      }
    }
    return false;
  }

  private String equals(String command, List<String> stringList) {
    for (int i = 0; i < stringList.size(); i++) {
      if (command.equals(stringList.get(i).toLowerCase())) {
        return command;
      }
    }
    return null;
  }

  private boolean equals(String command, int stringResId) {
    return command.equals(service.getString(stringResId).toLowerCase());
  }

  private boolean startsWith(String command, int stringResId) {
    return command.startsWith(service.getString(stringResId).toLowerCase());
  }

  private int startsWith(String command, int[] stringResIdArray) {
    if (command == null || stringResIdArray == null) {
      return -1;
    }
    for (int i = 0; i < stringResIdArray.length; i++) {
      if (command.startsWith(service.getString(stringResIdArray[i]).toLowerCase())) {
        return stringResIdArray[i];
      }
    }
    return -1;
  }

  private int equals(String command, int[] stringResIdArray) {
    if (command == null || stringResIdArray == null) {
      return -1;
    }
    for (int i = 0; i < stringResIdArray.length; i++) {
      if (command.equals(service.getString(stringResIdArray[i]).toLowerCase())) {
        return stringResIdArray[i];
      }
    }
    return -1;
  }

  /* Returns the tail of command-string after the stringResId prefix. */
  private CharSequence remainder(String command, int stringResId) {
    CharSequence prefix = service.getString(stringResId).toLowerCase();
    return command.substring(prefix.length());
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
      speakDelayed(service.getString(R.string.not_editable));
      return null;
    }
  }

  /** Delay to announce message to inform user. */
  private void speakDelayed(String text) {
    SpeakOptions speakOptions =
        SpeakOptions.create()
            .setFlags(
                FeedbackItem.FLAG_NO_HISTORY
                    | FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                    | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                    | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE);
    pipeline.returnFeedback(
        EVENT_ID_UNTRACKED,
        Feedback.speech(text, speakOptions).setDelayMs(RECOGNITION_SPEECH_DELAY_MS));
  }

  private boolean sendInterpretation(Interpretation.VoiceCommand.Action action, EventId eventId) {
    return sendInterpretation(action, /* node= */ null, /* text= */ null, eventId);
  }

  private boolean sendInterpretation(
      Interpretation.VoiceCommand.Action action,
      AccessibilityNodeInfoCompat node,
      EventId eventId) {
    return sendInterpretation(action, node, /* text= */ null, eventId);
  }

  private boolean sendInterpretation(
      Interpretation.VoiceCommand.Action action, CharSequence text, EventId eventId) {
    return sendInterpretation(action, /* node= */ null, text, eventId);
  }

  private boolean sendInterpretation(
      Interpretation.VoiceCommand.Action action,
      AccessibilityNodeInfoCompat node,
      CharSequence text,
      EventId eventId) {
    return interpretationReceiver.input(
        eventId,
        /* event= */ null,
        Interpretation.VoiceCommand.create(action, node, /* granularity= */ null, text));
  }

  private boolean sendInterpretation(
      Interpretation.VoiceCommand.Action action, CursorGranularity granularity, EventId eventId) {
    return interpretationReceiver.input(
        eventId,
        /* event= */ null,
        Interpretation.VoiceCommand.create(
            action, /* targetNode= */ null, granularity, /* text= */ null));
  }
}
