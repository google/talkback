/*
 * Copyright (C) 2022 Google Inc.
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
package com.google.android.accessibility.talkback.compositor;

import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_INPUT_DESCRIBE_NODE;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_SPEAK_HINT;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_VIEW_ACCESSIBILITY_FOCUSED;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_VIEW_SELECTED;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_WINDOW_CONTENT_CHANGED;
import static com.google.android.accessibility.talkback.compositor.Compositor.FLAVOR_JASPER;
import static com.google.android.accessibility.talkback.compositor.Compositor.FLAVOR_TV;
import static com.google.android.accessibility.talkback.compositor.Compositor.QUEUE_MODE_INTERRUPTIBLE_IF_LONG;
import static com.google.android.accessibility.talkback.compositor.roledescription.RoleDescriptionExtractor.DESC_ORDER_NAME_ROLE_STATE_POSITION;
import static com.google.android.accessibility.talkback.compositor.roledescription.RoleDescriptionExtractor.DESC_ORDER_ROLE_NAME_STATE_POSITION;
import static com.google.android.accessibility.talkback.compositor.roledescription.RoleDescriptionExtractor.DESC_ORDER_STATE_NAME_ROLE_POSITION;
import static com.google.android.accessibility.utils.AccessibilityWindowInfoUtils.WINDOW_TYPE_NONE;

import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo.RangeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.Compositor.Flavor;
import com.google.android.accessibility.talkback.compositor.parsetree.ParseTree;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.JsonUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.output.SpeechController;
import java.util.HashMap;
import java.util.Map;

/** Creates {@link ParseTree} that stores the event feedback rules for compositor. */
public class ParseTreeCreator {

  // IDs of the output types.
  static final int OUTPUT_TTS_OUTPUT = 0;
  static final int OUTPUT_TTS_QUEUE_MODE = 1;
  static final int OUTPUT_TTS_ADD_TO_HISTORY = 2;
  static final int OUTPUT_TTS_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE = 3;
  static final int OUTPUT_TTS_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE = 4;
  static final int OUTPUT_TTS_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE = 5;
  static final int OUTPUT_TTS_FORCE_FEEDBACK_EVEN_IF_PHONE_CALL_ACTIVE = 6;
  static final int OUTPUT_TTS_INTERRUPT_SAME_GROUP = 7;
  static final int OUTPUT_TTS_SKIP_DUPLICATE = 8;
  static final int OUTPUT_TTS_CLEAR_QUEUE_GROUP = 9;
  static final int OUTPUT_TTS_PITCH = 10;
  static final int OUTPUT_ADVANCE_CONTINUOUS_READING = 11;
  static final int OUTPUT_PREVENT_DEVICE_SLEEP = 12;
  static final int OUTPUT_REFRESH_SOURCE_NODE = 13;
  static final int OUTPUT_HAPTIC = 14;
  static final int OUTPUT_EARCON = 15;
  static final int OUTPUT_EARCON_RATE = 16;
  static final int OUTPUT_EARCON_VOLUME = 17;
  static final int OUTPUT_TTS_FORCE_FEEDBACK = 18;

  // IDs of the enum types.
  static final int ENUM_TTS_QUEUE_MODE = 0;
  static final int ENUM_TTS_QUEUE_GROUP = 1;
  static final int ENUM_ROLE = 2;
  static final int ENUM_LIVE_REGION = 3;
  static final int ENUM_WINDOW_TYPE = 4;
  static final int ENUM_VERBOSITY_DESCRIPTION_ORDER = 5;
  static final int ENUM_RANGE_INFO_TYPE = 6;

  static final int RANGE_INFO_UNDEFINED = -1;

  /**
   * Creates {@link ParseTree} and load compositor event feedback rules from compositor json file by
   * the platform flavor.
   */
  static ParseTree createParseTree(
      Context context, VariablesFactory variablesFactory, @Flavor int flavor) {
    ParseTree parseTree = new ParseTree(context.getResources(), context.getPackageName());

    declareEnums(parseTree);
    declareEvents(parseTree);
    variablesFactory.declareVariables(parseTree);

    try {
      parseTree.mergeTree(JsonUtils.readFromRawFile(context, R.raw.compositor));
    } catch (Exception e) {
      throw new IllegalStateException(e.toString());
    }

    parseTree.build();

    return parseTree;
  }

  private static void declareEnums(ParseTree parseTree) {
    Map<Integer, String> queueModes = new HashMap<>();
    queueModes.put(SpeechController.QUEUE_MODE_INTERRUPT, "interrupt");
    queueModes.put(SpeechController.QUEUE_MODE_QUEUE, "queue");
    queueModes.put(SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH, "uninterruptible");
    queueModes.put(SpeechController.QUEUE_MODE_CAN_IGNORE_INTERRUPTS, "ignoreInterrupts");
    queueModes.put(
        SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH_CAN_IGNORE_INTERRUPTS,
        "uninterruptibleAndIgnoreInterrupts");
    queueModes.put(SpeechController.QUEUE_MODE_FLUSH_ALL, "flush");
    queueModes.put(QUEUE_MODE_INTERRUPTIBLE_IF_LONG, "interruptible_if_long");
    queueModes.put(
        SpeechController.QUEUE_MODE_INTERRUPT_AND_UNINTERRUPTIBLE_BY_NEW_SPEECH,
        "interrupt_and_uninterruptible");

    parseTree.addEnum(ENUM_TTS_QUEUE_MODE, queueModes);

    Map<Integer, String> speechQueueGroups = new HashMap<>();
    speechQueueGroups.put(SpeechController.UTTERANCE_GROUP_DEFAULT, "default");
    speechQueueGroups.put(SpeechController.UTTERANCE_GROUP_PROGRESS_BAR_PROGRESS, "progress_bar");
    speechQueueGroups.put(SpeechController.UTTERANCE_GROUP_SEEK_PROGRESS, "seek_progress");
    speechQueueGroups.put(SpeechController.UTTERANCE_GROUP_TEXT_SELECTION, "text_selection");
    speechQueueGroups.put(SpeechController.UTTERANCE_GROUP_CONTENT_CHANGE, "content_change");
    speechQueueGroups.put(
        SpeechController.UTTERANCE_GROUP_SCREEN_MAGNIFICATION, "screen_magnification");

    parseTree.addEnum(ENUM_TTS_QUEUE_GROUP, speechQueueGroups);

    Map<Integer, String> roles = new HashMap<>();
    roles.put(Role.ROLE_NONE, "none");
    roles.put(Role.ROLE_BUTTON, "button");
    roles.put(Role.ROLE_CHECK_BOX, "check_box");
    roles.put(Role.ROLE_DROP_DOWN_LIST, "drop_down_list");
    roles.put(Role.ROLE_EDIT_TEXT, "edit_text");
    roles.put(Role.ROLE_GRID, "grid");
    roles.put(Role.ROLE_IMAGE, "image");
    roles.put(Role.ROLE_IMAGE_BUTTON, "image_button");
    roles.put(Role.ROLE_LIST, "list");
    roles.put(Role.ROLE_PAGER, "pager");
    roles.put(Role.ROLE_PROGRESS_BAR, "progress_bar");
    roles.put(Role.ROLE_RADIO_BUTTON, "radio_button");
    roles.put(Role.ROLE_SEEK_CONTROL, "seek_control");
    roles.put(Role.ROLE_SWITCH, "switch");
    roles.put(Role.ROLE_TAB_BAR, "tab_bar");
    roles.put(Role.ROLE_TOGGLE_BUTTON, "toggle_button");
    roles.put(Role.ROLE_VIEW_GROUP, "view_group");
    roles.put(Role.ROLE_WEB_VIEW, "web_view");
    roles.put(Role.ROLE_CHECKED_TEXT_VIEW, "checked_text_view");
    roles.put(Role.ROLE_ACTION_BAR_TAB, "action_bar_tab");
    roles.put(Role.ROLE_DRAWER_LAYOUT, "drawer_layout");
    roles.put(Role.ROLE_SLIDING_DRAWER, "sliding_drawer");
    roles.put(Role.ROLE_ICON_MENU, "icon_menu");
    roles.put(Role.ROLE_TOAST, "toast");
    roles.put(Role.ROLE_ALERT_DIALOG, "alert_dialog");
    roles.put(Role.ROLE_DATE_PICKER_DIALOG, "date_picker_dialog");
    roles.put(Role.ROLE_TIME_PICKER_DIALOG, "time_picker_dialog");
    roles.put(Role.ROLE_DATE_PICKER, "date_picker");
    roles.put(Role.ROLE_TIME_PICKER, "time_picker");
    roles.put(Role.ROLE_NUMBER_PICKER, "number_picker");
    roles.put(Role.ROLE_SCROLL_VIEW, "scroll_view");
    roles.put(Role.ROLE_HORIZONTAL_SCROLL_VIEW, "horizontal_scroll_view");
    roles.put(Role.ROLE_TEXT_ENTRY_KEY, "text_entry_key");

    parseTree.addEnum(ENUM_ROLE, roles);

    Map<Integer, String> liveRegions = new HashMap<>();
    liveRegions.put(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE, "assertive");
    liveRegions.put(View.ACCESSIBILITY_LIVE_REGION_POLITE, "polite");
    liveRegions.put(View.ACCESSIBILITY_LIVE_REGION_NONE, "none");

    parseTree.addEnum(ENUM_LIVE_REGION, liveRegions);

    Map<Integer, String> windowTypes = new HashMap<>();
    windowTypes.put(WINDOW_TYPE_NONE, "none");
    windowTypes.put(
        AccessibilityNodeInfoUtils.WINDOW_TYPE_PICTURE_IN_PICTURE, "picture_in_picture");
    windowTypes.put(
        AccessibilityWindowInfoCompat.TYPE_ACCESSIBILITY_OVERLAY, "accessibility_overlay");
    windowTypes.put(AccessibilityWindowInfoCompat.TYPE_APPLICATION, "application");
    windowTypes.put(AccessibilityWindowInfoCompat.TYPE_INPUT_METHOD, "input_method");
    windowTypes.put(AccessibilityWindowInfoCompat.TYPE_SYSTEM, "system");
    windowTypes.put(AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER, "split_screen_divider");

    parseTree.addEnum(ENUM_WINDOW_TYPE, windowTypes);

    Map<Integer, String> verbosityDescOrderValues = new HashMap<>();
    verbosityDescOrderValues.put(DESC_ORDER_ROLE_NAME_STATE_POSITION, "RoleNameStatePosition");
    verbosityDescOrderValues.put(DESC_ORDER_STATE_NAME_ROLE_POSITION, "StateNameRolePosition");
    verbosityDescOrderValues.put(DESC_ORDER_NAME_ROLE_STATE_POSITION, "NameRoleStatePosition");
    parseTree.addEnum(ENUM_VERBOSITY_DESCRIPTION_ORDER, verbosityDescOrderValues);

    Map<Integer, String> rangeInfoTypes = new HashMap<>();
    rangeInfoTypes.put(RangeInfo.RANGE_TYPE_INT, "int");
    rangeInfoTypes.put(RangeInfo.RANGE_TYPE_FLOAT, "float");
    rangeInfoTypes.put(RangeInfo.RANGE_TYPE_PERCENT, "percent");
    rangeInfoTypes.put(RANGE_INFO_UNDEFINED, "undefined");
    parseTree.addEnum(ENUM_RANGE_INFO_TYPE, rangeInfoTypes);
  }

  private static void declareEvents(ParseTree parseTree) {
    // Service events.
    parseTree.addEvent("Hint", EVENT_SPEAK_HINT);

    // Accessibility events.
    parseTree.addEvent("TYPE_VIEW_ACCESSIBILITY_FOCUSED", EVENT_TYPE_VIEW_ACCESSIBILITY_FOCUSED);
    parseTree.addEvent("TYPE_WINDOW_CONTENT_CHANGED", EVENT_TYPE_WINDOW_CONTENT_CHANGED);
    parseTree.addEvent("TYPE_VIEW_SELECTED", EVENT_TYPE_VIEW_SELECTED);

    // Interpreted events.
    parseTree.addEvent("EVENT_INPUT_DESCRIBE_NODE", EVENT_INPUT_DESCRIBE_NODE);

    // Outputs.
    parseTree.addStringOutput("ttsOutput", OUTPUT_TTS_OUTPUT);
    parseTree.addEnumOutput("ttsQueueMode", OUTPUT_TTS_QUEUE_MODE, ENUM_TTS_QUEUE_MODE);
    parseTree.addEnumOutput(
        "ttsClearQueueGroup", OUTPUT_TTS_CLEAR_QUEUE_GROUP, ENUM_TTS_QUEUE_GROUP);
    parseTree.addBooleanOutput("ttsInterruptSameGroup", OUTPUT_TTS_INTERRUPT_SAME_GROUP);
    parseTree.addBooleanOutput("ttsSkipDuplicate", OUTPUT_TTS_SKIP_DUPLICATE);
    parseTree.addBooleanOutput("ttsAddToHistory", OUTPUT_TTS_ADD_TO_HISTORY);
    parseTree.addBooleanOutput("ttsForceFeedback", OUTPUT_TTS_FORCE_FEEDBACK);
    parseTree.addBooleanOutput(
        "ttsForceFeedbackEvenIfAudioPlaybackActive",
        OUTPUT_TTS_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE);
    parseTree.addBooleanOutput(
        "ttsForceFeedbackEvenIfMicrophoneActive",
        OUTPUT_TTS_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE);
    parseTree.addBooleanOutput(
        "ttsForceFeedbackEvenIfSsbActive", OUTPUT_TTS_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE);
    parseTree.addBooleanOutput(
        "ttsForceFeedbackEvenIfPhoneCallActive",
        OUTPUT_TTS_FORCE_FEEDBACK_EVEN_IF_PHONE_CALL_ACTIVE);
    parseTree.addNumberOutput("ttsPitch", OUTPUT_TTS_PITCH);
    parseTree.addBooleanOutput("advanceContinuousReading", OUTPUT_ADVANCE_CONTINUOUS_READING);
    parseTree.addBooleanOutput("preventDeviceSleep", OUTPUT_PREVENT_DEVICE_SLEEP);
    parseTree.addBooleanOutput("refreshSourceNode", OUTPUT_REFRESH_SOURCE_NODE);
    parseTree.addIntegerOutput("haptic", OUTPUT_HAPTIC);
    parseTree.addIntegerOutput("earcon", OUTPUT_EARCON);
    parseTree.addNumberOutput("earcon_rate", OUTPUT_EARCON_RATE);
    parseTree.addNumberOutput("earcon_volume", OUTPUT_EARCON_VOLUME);
  }
}
