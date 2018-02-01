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

package com.google.android.accessibility.compositor;

import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_INTERRUPT;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_UNINTERRUPTIBLE;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.IntDef;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.support.v4.view.accessibility.AccessibilityWindowInfoCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FailoverTextToSpeech.SpeechParam;
import com.google.android.accessibility.utils.JsonUtils;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.SpeechCleanupUtils;
import com.google.android.accessibility.utils.labeling.LabelManager;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.output.Utterance;
import com.google.android.accessibility.utils.parsetree.ParseTree;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Translates events into user visible feedback. */
public class Compositor {
  /** Flavors used to load different configurations for different device types and applications. */
  @IntDef({FLAVOR_NONE, FLAVOR_ARC, FLAVOR_TV, FLAVOR_SWITCH_ACCESS})
  @Retention(RetentionPolicy.SOURCE)
  public @interface Flavor {}

  public static final int FLAVOR_NONE = 0;
  public static final int FLAVOR_ARC = 1;
  public static final int FLAVOR_TV = 2;
  public static final int FLAVOR_SWITCH_ACCESS = 3;

  /** Identity numbers for incoming events, including AccessibilityEvents & interpreted events. */
  @IntDef({
    EVENT_SPOKEN_FEEDBACK_ON,
    EVENT_SPOKEN_FEEDBACK_SUSPENDED,
    EVENT_SPOKEN_FEEDBACK_RESUMED,
    EVENT_SPOKEN_FEEDBACK_DISABLED,
    EVENT_CAPS_LOCK_ON,
    EVENT_CAPS_LOCK_OFF,
    EVENT_NUM_LOCK_ON,
    EVENT_NUM_LOCK_OFF,
    EVENT_SCROLL_LOCK_ON,
    EVENT_SCROLL_LOCK_OFF,
    EVENT_ORIENTATION_PORTRAIT,
    EVENT_ORIENTATION_LANDSCAPE,
    EVENT_TYPE_INPUT_TEXT_CLEAR,
    EVENT_TYPE_INPUT_TEXT_REMOVE,
    EVENT_TYPE_INPUT_TEXT_ADD,
    EVENT_TYPE_INPUT_TEXT_REPLACE,
    EVENT_TYPE_INPUT_TEXT_PASSWORD_ADD,
    EVENT_TYPE_INPUT_TEXT_PASSWORD_REMOVE,
    EVENT_TYPE_INPUT_TEXT_PASSWORD_REPLACE,
    EVENT_TYPE_INPUT_CHANGE_INVALID,
    EVENT_TYPE_INPUT_SELECTION_FOCUS_EDIT_TEXT,
    EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_BEGINNING,
    EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_END,
    EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_NO_SELECTION,
    EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_WITH_SELECTION,
    EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_SELECTION_CLEARED,
    EVENT_TYPE_INPUT_SELECTION_CUT,
    EVENT_TYPE_INPUT_SELECTION_PASTE,
    EVENT_TYPE_INPUT_SELECTION_TEXT_TRAVERSAL,
    EVENT_TYPE_INPUT_SELECTION_SELECT_ALL,
    EVENT_TYPE_INPUT_SELECTION_SELECT_ALL_WITH_KEYBOARD,
    EVENT_TYPE_INPUT_SELECTION_RESET_SELECTION,
    EVENT_ACCESS_FOCUS_HINT,
    EVENT_ACCESS_FOCUS_HINT_FORCED,
    EVENT_INPUT_FOCUS_HINT,
    EVENT_INPUT_FOCUS_HINT_FORCED,
    EVENT_SCREEN_MAGNIFICATION_CHANGED,
    EVENT_SELECT_SPEECH_RATE,
    EVENT_SELECT_VERBOSITY,
    EVENT_SELECT_GRANULARITY,
    EVENT_SPEECH_RATE_CHANGE,
    EVENT_SELECT_AUDIO_FOCUS,
    EVENT_AUDIO_FOCUS_SWITCH,
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface Event {}

  // Events start from an arbitrary largish number to avoid conflicting with AccessibilityEvent.
  private static final int BASE_EVENT_ID = 0x40000001;
  public static final int EVENT_SPOKEN_FEEDBACK_ON = BASE_EVENT_ID;
  public static final int EVENT_SPOKEN_FEEDBACK_SUSPENDED = BASE_EVENT_ID + 1;
  public static final int EVENT_SPOKEN_FEEDBACK_RESUMED = BASE_EVENT_ID + 2;
  public static final int EVENT_SPOKEN_FEEDBACK_DISABLED = BASE_EVENT_ID + 3;
  public static final int EVENT_CAPS_LOCK_ON = BASE_EVENT_ID + 4;
  public static final int EVENT_CAPS_LOCK_OFF = BASE_EVENT_ID + 5;
  public static final int EVENT_NUM_LOCK_ON = BASE_EVENT_ID + 6;
  public static final int EVENT_NUM_LOCK_OFF = BASE_EVENT_ID + 7;
  public static final int EVENT_SCROLL_LOCK_ON = BASE_EVENT_ID + 8;
  public static final int EVENT_SCROLL_LOCK_OFF = BASE_EVENT_ID + 9;
  public static final int EVENT_ORIENTATION_PORTRAIT = BASE_EVENT_ID + 10;
  public static final int EVENT_ORIENTATION_LANDSCAPE = BASE_EVENT_ID + 11;
  public static final int EVENT_TYPE_INPUT_TEXT_CLEAR = BASE_EVENT_ID + 12;
  public static final int EVENT_TYPE_INPUT_TEXT_REMOVE = BASE_EVENT_ID + 13;
  public static final int EVENT_TYPE_INPUT_TEXT_ADD = BASE_EVENT_ID + 14;
  public static final int EVENT_TYPE_INPUT_TEXT_REPLACE = BASE_EVENT_ID + 15;
  public static final int EVENT_TYPE_INPUT_TEXT_PASSWORD_ADD = BASE_EVENT_ID + 16;
  public static final int EVENT_TYPE_INPUT_TEXT_PASSWORD_REMOVE = BASE_EVENT_ID + 17;
  public static final int EVENT_TYPE_INPUT_TEXT_PASSWORD_REPLACE = BASE_EVENT_ID + 18;
  public static final int EVENT_TYPE_INPUT_CHANGE_INVALID = BASE_EVENT_ID + 19;
  public static final int EVENT_TYPE_INPUT_SELECTION_FOCUS_EDIT_TEXT = BASE_EVENT_ID + 20;
  public static final int EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_BEGINNING = BASE_EVENT_ID + 21;
  public static final int EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_END = BASE_EVENT_ID + 22;
  public static final int EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_NO_SELECTION = BASE_EVENT_ID + 23;
  public static final int EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_WITH_SELECTION =
      BASE_EVENT_ID + 24;
  public static final int EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_SELECTION_CLEARED =
      BASE_EVENT_ID + 25;
  public static final int EVENT_TYPE_INPUT_SELECTION_CUT = BASE_EVENT_ID + 26;
  public static final int EVENT_TYPE_INPUT_SELECTION_PASTE = BASE_EVENT_ID + 27;
  public static final int EVENT_TYPE_INPUT_SELECTION_TEXT_TRAVERSAL = BASE_EVENT_ID + 28;
  public static final int EVENT_TYPE_INPUT_SELECTION_SELECT_ALL = BASE_EVENT_ID + 29;
  public static final int EVENT_TYPE_INPUT_SELECTION_SELECT_ALL_WITH_KEYBOARD = BASE_EVENT_ID + 30;
  public static final int EVENT_TYPE_INPUT_SELECTION_RESET_SELECTION = BASE_EVENT_ID + 31;
  public static final int EVENT_ACCESS_FOCUS_HINT = BASE_EVENT_ID + 32;
  public static final int EVENT_ACCESS_FOCUS_HINT_FORCED = BASE_EVENT_ID + 33;
  public static final int EVENT_INPUT_FOCUS_HINT = BASE_EVENT_ID + 34;
  public static final int EVENT_INPUT_FOCUS_HINT_FORCED = BASE_EVENT_ID + 35;
  public static final int EVENT_SCREEN_MAGNIFICATION_CHANGED = BASE_EVENT_ID + 36;

  // Setting change events for selector
  public static final int EVENT_SELECT_SPEECH_RATE = BASE_EVENT_ID + 37;
  public static final int EVENT_SELECT_VERBOSITY = BASE_EVENT_ID + 38;
  public static final int EVENT_SELECT_GRANULARITY = BASE_EVENT_ID + 39;
  public static final int EVENT_SELECT_AUDIO_FOCUS = BASE_EVENT_ID + 40;

  public static final int EVENT_SPEECH_RATE_CHANGE = BASE_EVENT_ID + 41;
  public static final int EVENT_AUDIO_FOCUS_SWITCH = BASE_EVENT_ID + 42;

  // IDs of the output types.
  private static final int OUTPUT_TTS_OUTPUT = 0;
  private static final int OUTPUT_TTS_QUEUE_MODE = 1;
  private static final int OUTPUT_TTS_ADD_TO_HISTORY = 2;
  private static final int OUTPUT_TTS_FORCE_FEEDBACK = 3;
  private static final int OUTPUT_TTS_INTERRUPT_SAME_GROUP = 4;
  private static final int OUTPUT_TTS_SKIP_DUPLICATE = 5;
  private static final int OUTPUT_TTS_CLEAR_QUEUE_GROUP = 6;
  private static final int OUTPUT_TTS_PITCH = 7;
  private static final int OUTPUT_ADVANCE_CONTINUOUS_READING = 8;
  private static final int OUTPUT_PREVENT_DEVICE_SLEEP = 9;
  private static final int OUTPUT_REFRESH_SOURCE_NODE = 10;
  private static final int OUTPUT_HAPTIC = 11;
  private static final int OUTPUT_EARCON = 12;
  private static final int OUTPUT_EARCON_RATE = 13;
  private static final int OUTPUT_EARCON_VOLUME = 14;

  // IDs of the enum types.
  private static final int ENUM_TTS_QUEUE_MODE = 0;
  private static final int ENUM_TTS_QUEUE_GROUP = 1;
  static final int ENUM_ROLE = 2;
  static final int ENUM_LIVE_REGION = 3;
  static final int ENUM_WINDOW_TYPE = 4;
  private static final int ENUM_VERBOSITY_DESCRIPTION_ORDER = 5;

  // Enum values
  private static final int QUEUE_MODE_INTERRUPTIBLE_IF_LONG = 0x40000001;

  // Constant parameters
  private static final int VERBOSE_UTTERANCE_THRESHOLD_CHARACTERS = 50;

  private static final String TALBACK_PACKAGE = "com.google.android.marvin.talkback";

  /** IDs of description orders in verbosity setting. */
  @IntDef({
    DESC_ORDER_ROLE_NAME_STATE_POSITION,
    DESC_ORDER_STATE_NAME_ROLE_POSITION,
    DESC_ORDER_NAME_ROLE_STATE_POSITION
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface DescriptionOrder {}

  public static final int DESC_ORDER_ROLE_NAME_STATE_POSITION = 0;
  public static final int DESC_ORDER_STATE_NAME_ROLE_POSITION = 1;
  public static final int DESC_ORDER_NAME_ROLE_STATE_POSITION = 2;

  private final SpeechController mSpeechController;
  private final Context mContext;
  private final @Flavor int mFlavor;

  private ParseTree mParseTree;
  private final VariablesFactory mVariablesFactory;

  private boolean mParseTreeIsStale = false;

  // Verbosity setting constants.
  private boolean mSpeakRoles;
  private boolean mSpeakCollectionInfo;
  private @DescriptionOrder int mDescriptionOrder;
  private boolean mSpeakElementIds;

  public Compositor(
      Context context,
      SpeechController speechController,
      LabelManager labelManager,
      GlobalVariables globalVariables,
      @Flavor int flavor) {
    mSpeechController = speechController;
    mVariablesFactory = new VariablesFactory(context, globalVariables, labelManager);
    mFlavor = flavor;
    mContext = context;

    mSpeakRoles = true;
    mSpeakCollectionInfo = true;
    mDescriptionOrder = DESC_ORDER_ROLE_NAME_STATE_POSITION;
    mSpeakElementIds = false;

    long startTime = SystemClock.uptimeMillis();
    refreshParseTree();
    long endTime = SystemClock.uptimeMillis();
    LogUtils.log(
        this,
        Log.INFO,
        "ParseTree built for compositor %s in %d ms",
        getFlavorName(flavor),
        endTime - startTime);
  }

  // Gets the user preferred locale changed using language switcher.
  public Locale getUserPreferredLanguage() {
    return mVariablesFactory.getUserPreferredLocale();
  }

  // Sets the user preferred locale changed using language switcher.
  public void setUserPreferredLanguage(Locale locale) {
    mVariablesFactory.setUserPreferredLocale(locale);
  }

  public void setSpeakCollectionInfo(boolean speakCollectionInfo) {
    if (speakCollectionInfo != mSpeakCollectionInfo) {
      mSpeakCollectionInfo = speakCollectionInfo;
      mParseTreeIsStale = true;
    }
  }

  public void setSpeakRoles(boolean speakRoles) {
    if (speakRoles != mSpeakRoles) {
      mSpeakRoles = speakRoles;
      mParseTreeIsStale = true;
    }
  }

  public void setDescriptionOrder(@DescriptionOrder int descOrderInt) {
    if (descOrderInt != mDescriptionOrder) {
      mDescriptionOrder = descOrderInt;
      mParseTreeIsStale = true;
    }
  }

  public void setSpeakElementIds(boolean speakElementIds) {
    if (speakElementIds != mSpeakElementIds) {
      mSpeakElementIds = speakElementIds;
      mParseTreeIsStale = true;
    }
  }

  public void refreshParseTreeIfNeeded() {
    if (mParseTreeIsStale) {
      mParseTreeIsStale = false;
      refreshParseTree();
    }
  }

  /**
   * Handles an event that has no meta-data associated with it.
   *
   * @param event Type of event that has occurred.
   * @param eventId ID of the event used for performance monitoring.
   */
  public void sendEvent(@Event int event, EventId eventId) {
    handleEvent(event, eventId, mVariablesFactory.getDefaultDelegate(), null);
  }

  /**
   * Handles an event that has a node associated.
   *
   * @param event Type of event that has occurred.
   * @param event Type of event that has occurred.
   * @param eventId ID of the event used for performance monitoring.
   */
  public void sendEvent(@Event int event, AccessibilityNodeInfoCompat node, EventId eventId) {
    handleEvent(
        event, eventId, mVariablesFactory.createLocalVariableDelegate(null, node, null), null);
  }

  /** Handles a standard AccessibilityEvent */
  public void sendEvent(
      AccessibilityEvent event, EventId eventId, EventInterpretation eventInterpreted) {

    final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
    @Event int eventType = eventInterpreted.getEvent();

    // Allocate source node & delegate which must be recycled.
    AccessibilityNodeInfoCompat sourceNode = record.getSource();
    ParseTree.VariableDelegate delegate =
        mVariablesFactory.createLocalVariableDelegate(event, sourceNode, eventInterpreted);

    // Refresh source node, and re-create variable delegate using fresh source node.
    boolean refreshSource =
        mParseTree.parseEventToBool(
            eventType, OUTPUT_REFRESH_SOURCE_NODE, false /* default */, delegate);
    if (refreshSource) {
      delegate.cleanup();
      sourceNode = AccessibilityNodeInfoUtils.replaceWithFreshNode(sourceNode);
      delegate = mVariablesFactory.createLocalVariableDelegate(event, sourceNode, eventInterpreted);
    }

    // Compute speech and speech flags.
    handleEvent(eventType, eventId, delegate, null);
    AccessibilityNodeInfoUtils.recycleNodes(sourceNode);
  }

  /**
   * Handles an event that has no meta-data associated with it.
   *
   * @param event Type of event that has occurred.
   * @param runnable Run when TTS output has completed
   */
  public void sendEventWithCompletionHandler(
      @Event int event, EventId eventId, SpeechController.UtteranceCompleteRunnable runnable) {
    handleEvent(event, eventId, mVariablesFactory.getDefaultDelegate(), runnable);
  }

  private void handleEvent(
      int event,
      EventId eventId,
      ParseTree.VariableDelegate delegate,
      SpeechController.UtteranceCompleteRunnable runnable) {

    // Compose earcons.
    SpeakOptions speakOptions = null;
    int earcon = mParseTree.parseEventToInteger(event, OUTPUT_EARCON, -1, delegate);
    if (earcon != -1) {
      if (speakOptions == null) {
        speakOptions = SpeakOptions.create();
      }
      Set<Integer> earcons = new HashSet<>();
      earcons.add(earcon);
      speakOptions.setEarcons(earcons);

      Bundle nonSpeechParams = new Bundle();
      double rate = mParseTree.parseEventToNumber(event, OUTPUT_EARCON_RATE, 1.0, delegate);
      if (rate != 1.0) {
        nonSpeechParams.putFloat(Utterance.KEY_METADATA_EARCON_RATE, (float) rate);
      }
      double volume = mParseTree.parseEventToNumber(event, OUTPUT_EARCON_RATE, 1.0, delegate);
      if (volume != 1.0) {
        nonSpeechParams.putFloat(Utterance.KEY_METADATA_EARCON_VOLUME, (float) volume);
      }
      speakOptions.setNonSpeechParams(nonSpeechParams);
    }

    // Compose haptics.
    int haptic = mParseTree.parseEventToInteger(event, OUTPUT_HAPTIC, -1, delegate);
    if (haptic != -1) {
      if (speakOptions == null) {
        speakOptions = SpeakOptions.create();
      }
      Set<Integer> haptics = new HashSet<>();
      haptics.add(haptic);
      speakOptions.setHaptics(haptics);
    }

    // FLAG_ADVANCED_CONTINUOUS_READING is used for "read from top". Ensure that the flag is set
    // correctly in SpeakOptions regardless of the speech/haptics/earcon feedback. So that the
    // "read from top" will not stop at focusable node with no feedback.
    if (hasFlagAdvancedContinuousReading(event, delegate)) {
      if (speakOptions == null) {
        speakOptions = SpeakOptions.create();
      }
      speakOptions.mFlags |= FeedbackItem.FLAG_ADVANCE_CONTINUOUS_READING;
    }

    // Compose speech, and speech parameters.
    CharSequence ttsOutput = mParseTree.parseEventToString(event, OUTPUT_TTS_OUTPUT, delegate);
    if (!TextUtils.isEmpty(ttsOutput)) {
      // Cleans up the TTS output if it is just 1 character long. This will announce single
      // symbols correctly.
      // TODO: Think about a unified clean up strategy instead of calling clean ups at
      // various places in the code.
      ttsOutput = SpeechCleanupUtils.cleanUp(mContext, ttsOutput);
      // Compute queueing mode.
      int queueMode =
          mParseTree.parseEventToEnum(
              event, OUTPUT_TTS_QUEUE_MODE, SpeechController.QUEUE_MODE_INTERRUPT, delegate);
      if (queueMode == QUEUE_MODE_INTERRUPTIBLE_IF_LONG) {
        queueMode =
            (ttsOutput.length() <= VERBOSE_UTTERANCE_THRESHOLD_CHARACTERS)
                ? QUEUE_MODE_UNINTERRUPTIBLE
                : QUEUE_MODE_INTERRUPT;
      }

      // Compose queue group to clear.
      int clearQueueGroup =
          mParseTree.parseEventToEnum(
              event,
              OUTPUT_TTS_CLEAR_QUEUE_GROUP,
              SpeechController.UTTERANCE_GROUP_DEFAULT,
              delegate);

      // Compose other speech flags/parameters.
      int flags = getSpeechFlags(event, clearQueueGroup, delegate);
      double speechPitch = mParseTree.parseEventToNumber(event, OUTPUT_TTS_PITCH, 1.0, delegate);
      Bundle speechParams = new Bundle();
      speechParams.putFloat(SpeechParam.PITCH, (float) speechPitch);

      // Output feedback: speech, haptics, earcons.
      if (speakOptions == null) {
        speakOptions = SpeakOptions.create();
      }
      speakOptions
          .setQueueMode(queueMode)
          .setSpeechParams(speechParams)
          .setUtteranceGroup(clearQueueGroup)
          .setCompletedAction(runnable);
      speakOptions.mFlags |= flags;
      mSpeechController.speak(ttsOutput, eventId, speakOptions);
    } else {
      if (speakOptions != null) {
        speakOptions.mFlags |= FeedbackItem.FLAG_NO_SPEECH;
        mSpeechController.speak("", eventId, speakOptions);
      }
      if (runnable != null) {
        runnable.run(SpeechController.STATUS_NOT_SPOKEN);
      }
    }

    delegate.cleanup();
  }

  private boolean hasFlagAdvancedContinuousReading(
      int event, ParseTree.VariableDelegate variables) {
    return mParseTree.parseEventToBool(event, OUTPUT_ADVANCE_CONTINUOUS_READING, false, variables);
  }

  /**
   * Gets speech flag mask for the event. <strong>Note:</strong> This method doesn't handle {@link
   * FeedbackItem#FLAG_ADVANCE_CONTINUOUS_READING}, which should be handled after calling {@link
   * #hasFlagAdvancedContinuousReading}.
   */
  private int getSpeechFlags(int event, int clearQueueGroup, ParseTree.VariableDelegate variables) {
    int flags = 0;
    if (!mParseTree.parseEventToBool(event, OUTPUT_TTS_ADD_TO_HISTORY, false, variables)) {
      flags = flags | FeedbackItem.FLAG_NO_HISTORY;
    }
    if (mParseTree.parseEventToBool(event, OUTPUT_TTS_FORCE_FEEDBACK, false, variables)) {
      flags = flags | FeedbackItem.FLAG_FORCED_FEEDBACK;
    }
    if (mParseTree.parseEventToBool(event, OUTPUT_TTS_SKIP_DUPLICATE, false, variables)) {
      flags = flags | FeedbackItem.FLAG_SKIP_DUPLICATE;
    }
    if (clearQueueGroup != SpeechController.UTTERANCE_GROUP_DEFAULT) {
      flags = flags | FeedbackItem.FLAG_CLEAR_QUEUED_UTTERANCES_WITH_SAME_UTTERANCE_GROUP;
    }
    if (mParseTree.parseEventToBool(event, OUTPUT_TTS_INTERRUPT_SAME_GROUP, false, variables)) {
      flags = flags | FeedbackItem.FLAG_INTERRUPT_CURRENT_UTTERANCE_WITH_SAME_UTTERANCE_GROUP;
    }
    if (mParseTree.parseEventToBool(event, OUTPUT_PREVENT_DEVICE_SLEEP, false, variables)) {
      flags = flags | FeedbackItem.FLAG_NO_DEVICE_SLEEP;
    }
    return flags;
  }

  private void refreshParseTree() {
    mParseTree = new ParseTree(mContext.getResources(), mContext.getPackageName());

    declareConstants();
    declareEnums();
    declareEvents();
    mVariablesFactory.declareVariables(mParseTree);

    try {
      mParseTree.mergeTree(JsonUtils.readFromRawFile(mContext, R.raw.compositor));
      if (mFlavor == FLAVOR_ARC) {
        mParseTree.mergeTree(JsonUtils.readFromRawFile(mContext, R.raw.compositor_arc));
      } else if (mFlavor == FLAVOR_TV) {
        mParseTree.mergeTree(JsonUtils.readFromRawFile(mContext, R.raw.compositor_tv));
      } else if (mFlavor == FLAVOR_SWITCH_ACCESS) {
        mParseTree.mergeTree(JsonUtils.readFromRawFile(mContext, R.raw.compositor_switchaccess));
      }
    } catch (Exception e) {
      throw new IllegalStateException(e.toString());
    }

    mParseTree.build();
  }

  /** Returns {@code true} if the package is Talkback package */
  public static boolean isTalkBackUi(CharSequence packageName) {
    return TextUtils.equals(packageName, TALBACK_PACKAGE);
  }

  private void declareConstants() {
    // Declare constans from verbosity settings.
    mParseTree.setConstantBool("VERBOSITY_SPEAK_ROLE", mSpeakRoles);
    mParseTree.setConstantBool("VERBOSITY_SPEAK_COLLECTION_INFO", mSpeakCollectionInfo);
    mParseTree.setConstantEnum(
        "VERBOSITY_DESCRIPTION_ORDER", ENUM_VERBOSITY_DESCRIPTION_ORDER, mDescriptionOrder);
    mParseTree.setConstantBool("VERBOSITY_SPEAK_ELEMENT_IDS", mSpeakElementIds);
  }

  private void declareEnums() {
    Map<Integer, String> queueModes = new HashMap<>();
    queueModes.put(SpeechController.QUEUE_MODE_INTERRUPT, "interrupt");
    queueModes.put(SpeechController.QUEUE_MODE_QUEUE, "queue");
    queueModes.put(SpeechController.QUEUE_MODE_UNINTERRUPTIBLE, "uninterruptible");
    queueModes.put(SpeechController.QUEUE_MODE_FLUSH_ALL, "flush");
    queueModes.put(QUEUE_MODE_INTERRUPTIBLE_IF_LONG, "interruptible_if_long");

    mParseTree.addEnum(ENUM_TTS_QUEUE_MODE, queueModes);

    Map<Integer, String> speech_queue_groups = new HashMap<>();
    speech_queue_groups.put(SpeechController.UTTERANCE_GROUP_DEFAULT, "default");
    speech_queue_groups.put(SpeechController.UTTERANCE_GROUP_PROGRESS_BAR_PROGRESS, "progress_bar");
    speech_queue_groups.put(SpeechController.UTTERANCE_GROUP_SEEK_PROGRESS, "seek_progress");
    speech_queue_groups.put(SpeechController.UTTERANCE_GROUP_TEXT_SELECTION, "text_selection");
    speech_queue_groups.put(
        SpeechController.UTTERANCE_GROUP_SCREEN_MAGNIFICATION, "screen_magnification");

    mParseTree.addEnum(ENUM_TTS_QUEUE_GROUP, speech_queue_groups);

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
    roles.put(Role.ROLE_DATE_PICKER_DIALOG, "date_picker_dialog");
    roles.put(Role.ROLE_TIME_PICKER_DIALOG, "time_picker_dialog");
    roles.put(Role.ROLE_DATE_PICKER, "date_picker");
    roles.put(Role.ROLE_TIME_PICKER, "time_picker");
    roles.put(Role.ROLE_NUMBER_PICKER, "number_picker");
    roles.put(Role.ROLE_SCROLL_VIEW, "scroll_view");
    roles.put(Role.ROLE_HORIZONTAL_SCROLL_VIEW, "horizontal_scroll_view");

    mParseTree.addEnum(ENUM_ROLE, roles);

    Map<Integer, String> liveRegions = new HashMap<>();
    liveRegions.put(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE, "assertive");
    liveRegions.put(View.ACCESSIBILITY_LIVE_REGION_POLITE, "polite");
    liveRegions.put(View.ACCESSIBILITY_LIVE_REGION_NONE, "none");

    mParseTree.addEnum(ENUM_LIVE_REGION, liveRegions);

    Map<Integer, String> windowTypes = new HashMap<>();
    windowTypes.put(AccessibilityNodeInfoUtils.WINDOW_TYPE_NONE, "none");
    windowTypes.put(
        AccessibilityNodeInfoUtils.WINDOW_TYPE_PICTURE_IN_PICTURE, "picture_in_picture");
    windowTypes.put(
        AccessibilityWindowInfoCompat.TYPE_ACCESSIBILITY_OVERLAY, "accessibility_overlay");
    windowTypes.put(AccessibilityWindowInfoCompat.TYPE_APPLICATION, "application");
    windowTypes.put(AccessibilityWindowInfoCompat.TYPE_INPUT_METHOD, "input_method");
    windowTypes.put(AccessibilityWindowInfoCompat.TYPE_SYSTEM, "system");
    windowTypes.put(AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER, "split_screen_divider");

    mParseTree.addEnum(ENUM_WINDOW_TYPE, windowTypes);

    Map<Integer, String> verbosityDescOrderValues = new HashMap<>();
    verbosityDescOrderValues.put(DESC_ORDER_ROLE_NAME_STATE_POSITION, "RoleNameStatePosition");
    verbosityDescOrderValues.put(DESC_ORDER_STATE_NAME_ROLE_POSITION, "StateNameRolePosition");
    verbosityDescOrderValues.put(DESC_ORDER_NAME_ROLE_STATE_POSITION, "NameRoleStatePosition");
    mParseTree.addEnum(ENUM_VERBOSITY_DESCRIPTION_ORDER, verbosityDescOrderValues);
  }

  private void declareEvents() {
    // Service events.
    mParseTree.addEvent("SpokenFeedbackOn", EVENT_SPOKEN_FEEDBACK_ON);
    mParseTree.addEvent("SpokenFeedbackSuspended", EVENT_SPOKEN_FEEDBACK_SUSPENDED);
    mParseTree.addEvent("SpokenFeedbackResumed", EVENT_SPOKEN_FEEDBACK_RESUMED);
    mParseTree.addEvent("SpokenFeedbackDisabled", EVENT_SPOKEN_FEEDBACK_DISABLED);
    mParseTree.addEvent("CapsLockOn", EVENT_CAPS_LOCK_ON);
    mParseTree.addEvent("CapsLockOff", EVENT_CAPS_LOCK_OFF);
    mParseTree.addEvent("NumLockOn", EVENT_NUM_LOCK_ON);
    mParseTree.addEvent("NumLockOff", EVENT_NUM_LOCK_OFF);
    mParseTree.addEvent("ScrollLockOn", EVENT_SCROLL_LOCK_ON);
    mParseTree.addEvent("ScrollLockOff", EVENT_SCROLL_LOCK_OFF);
    mParseTree.addEvent("OrientationPortrait", EVENT_ORIENTATION_PORTRAIT);
    mParseTree.addEvent("OrientationLandscape", EVENT_ORIENTATION_LANDSCAPE);
    mParseTree.addEvent("AccessFocusHint", EVENT_ACCESS_FOCUS_HINT);
    mParseTree.addEvent("AccessFocusHintForced", EVENT_ACCESS_FOCUS_HINT_FORCED);
    mParseTree.addEvent("InputFocusHint", EVENT_INPUT_FOCUS_HINT);
    mParseTree.addEvent("InputFocusHintForced", EVENT_INPUT_FOCUS_HINT_FORCED);
    mParseTree.addEvent("ScreenMagnificationChanged", EVENT_SCREEN_MAGNIFICATION_CHANGED);

    // Accessibility events.
    mParseTree.addEvent(
        "TYPE_VIEW_ACCESSIBILITY_FOCUSED", AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
    mParseTree.addEvent("TYPE_VIEW_FOCUSED", AccessibilityEvent.TYPE_VIEW_FOCUSED);
    mParseTree.addEvent("TYPE_VIEW_HOVER_ENTER", AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
    mParseTree.addEvent("TYPE_VIEW_CLICKED", AccessibilityEvent.TYPE_VIEW_CLICKED);
    mParseTree.addEvent("TYPE_VIEW_LONG_CLICKED", AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
    mParseTree.addEvent(
        "TYPE_NOTIFICATION_STATE_CHANGED", AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
    mParseTree.addEvent(
        "TYPE_WINDOW_CONTENT_CHANGED", AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    mParseTree.addEvent("TYPE_VIEW_SELECTED", AccessibilityEvent.TYPE_VIEW_SELECTED);
    mParseTree.addEvent("TYPE_VIEW_SCROLLED", AccessibilityEvent.TYPE_VIEW_SCROLLED);
    mParseTree.addEvent("TYPE_ANNOUNCEMENT", AccessibilityEvent.TYPE_ANNOUNCEMENT);
    mParseTree.addEvent("TYPE_WINDOW_STATE_CHANGED", AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);

    // Interpreted events.
    mParseTree.addEvent("EVENT_TYPE_INPUT_TEXT_CLEAR", EVENT_TYPE_INPUT_TEXT_CLEAR);
    mParseTree.addEvent("EVENT_TYPE_INPUT_TEXT_REMOVE", EVENT_TYPE_INPUT_TEXT_REMOVE);
    mParseTree.addEvent("EVENT_TYPE_INPUT_TEXT_ADD", EVENT_TYPE_INPUT_TEXT_ADD);
    mParseTree.addEvent("EVENT_TYPE_INPUT_TEXT_REPLACE", EVENT_TYPE_INPUT_TEXT_REPLACE);
    mParseTree.addEvent("EVENT_TYPE_INPUT_TEXT_PASSWORD_ADD", EVENT_TYPE_INPUT_TEXT_PASSWORD_ADD);
    mParseTree.addEvent(
        "EVENT_TYPE_INPUT_TEXT_PASSWORD_REMOVE", EVENT_TYPE_INPUT_TEXT_PASSWORD_REMOVE);
    mParseTree.addEvent(
        "EVENT_TYPE_INPUT_TEXT_PASSWORD_REPLACE", EVENT_TYPE_INPUT_TEXT_PASSWORD_REPLACE);
    mParseTree.addEvent("EVENT_TYPE_INPUT_CHANGE_INVALID", EVENT_TYPE_INPUT_CHANGE_INVALID);
    mParseTree.addEvent(
        "EVENT_TYPE_INPUT_SELECTION_FOCUS_EDIT_TEXT", EVENT_TYPE_INPUT_SELECTION_FOCUS_EDIT_TEXT);
    mParseTree.addEvent(
        "EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_BEGINNING",
        EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_BEGINNING);
    mParseTree.addEvent(
        "EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_END",
        EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_END);
    mParseTree.addEvent(
        "EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_NO_SELECTION",
        EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_NO_SELECTION);
    mParseTree.addEvent(
        "EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_WITH_SELECTION",
        EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_WITH_SELECTION);
    mParseTree.addEvent(
        "EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_SELECTION_CLEARED",
        EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_SELECTION_CLEARED);
    mParseTree.addEvent("EVENT_TYPE_INPUT_SELECTION_CUT", EVENT_TYPE_INPUT_SELECTION_CUT);
    mParseTree.addEvent("EVENT_TYPE_INPUT_SELECTION_PASTE", EVENT_TYPE_INPUT_SELECTION_PASTE);
    mParseTree.addEvent(
        "EVENT_TYPE_INPUT_SELECTION_TEXT_TRAVERSAL", EVENT_TYPE_INPUT_SELECTION_TEXT_TRAVERSAL);
    mParseTree.addEvent(
        "EVENT_TYPE_INPUT_SELECTION_SELECT_ALL", EVENT_TYPE_INPUT_SELECTION_SELECT_ALL);
    mParseTree.addEvent(
        "EVENT_TYPE_INPUT_SELECTION_SELECT_ALL_WITH_KEYBOARD",
        EVENT_TYPE_INPUT_SELECTION_SELECT_ALL_WITH_KEYBOARD);
    mParseTree.addEvent(
        "EVENT_TYPE_INPUT_SELECTION_RESET_SELECTION", EVENT_TYPE_INPUT_SELECTION_RESET_SELECTION);

    // Selector events.
    mParseTree.addEvent("EVENT_SELECT_SPEECH_RATE", EVENT_SELECT_SPEECH_RATE);
    mParseTree.addEvent("EVENT_SELECT_VERBOSITY", EVENT_SELECT_VERBOSITY);
    mParseTree.addEvent("EVENT_SELECT_GRANULARITY", EVENT_SELECT_GRANULARITY);
    mParseTree.addEvent("EVENT_SELECT_AUDIO_FOCUS", EVENT_SELECT_AUDIO_FOCUS);
    mParseTree.addEvent("EVENT_SPEECH_RATE_CHANGE", EVENT_SPEECH_RATE_CHANGE);
    mParseTree.addEvent("EVENT_AUDIO_FOCUS_SWITCH", EVENT_AUDIO_FOCUS_SWITCH);

    // Outputs.
    mParseTree.addStringOutput("ttsOutput", OUTPUT_TTS_OUTPUT);
    mParseTree.addEnumOutput("ttsQueueMode", OUTPUT_TTS_QUEUE_MODE, ENUM_TTS_QUEUE_MODE);
    mParseTree.addEnumOutput(
        "ttsClearQueueGroup", OUTPUT_TTS_CLEAR_QUEUE_GROUP, ENUM_TTS_QUEUE_GROUP);
    mParseTree.addBooleanOutput("ttsInterruptSameGroup", OUTPUT_TTS_INTERRUPT_SAME_GROUP);
    mParseTree.addBooleanOutput("ttsSkipDuplicate", OUTPUT_TTS_SKIP_DUPLICATE);
    mParseTree.addBooleanOutput("ttsAddToHistory", OUTPUT_TTS_ADD_TO_HISTORY);
    mParseTree.addBooleanOutput("ttsForceFeedback", OUTPUT_TTS_FORCE_FEEDBACK);
    mParseTree.addNumberOutput("ttsPitch", OUTPUT_TTS_PITCH);
    mParseTree.addBooleanOutput("advanceContinuousReading", OUTPUT_ADVANCE_CONTINUOUS_READING);
    mParseTree.addBooleanOutput("preventDeviceSleep", OUTPUT_PREVENT_DEVICE_SLEEP);
    mParseTree.addBooleanOutput("refreshSourceNode", OUTPUT_REFRESH_SOURCE_NODE);
    mParseTree.addIntegerOutput("haptic", OUTPUT_HAPTIC);
    mParseTree.addIntegerOutput("earcon", OUTPUT_EARCON);
    mParseTree.addNumberOutput("earcon_rate", OUTPUT_EARCON_RATE);
    mParseTree.addNumberOutput("earcon_volume", OUTPUT_EARCON_VOLUME);
  }

  ////////////////////////////////////////////////////////////////////////////////////////
  // Methods for logging

  public static String eventTypeToString(int eventType) {
    switch (eventType) {
      case EVENT_SPOKEN_FEEDBACK_ON:
        return "EVENT_SPOKEN_FEEDBACK_ON";
      case EVENT_SPOKEN_FEEDBACK_SUSPENDED:
        return "EVENT_SPOKEN_FEEDBACK_SUSPENDED";
      case EVENT_SPOKEN_FEEDBACK_RESUMED:
        return "EVENT_SPOKEN_FEEDBACK_RESUMED";
      case EVENT_SPOKEN_FEEDBACK_DISABLED:
        return "EVENT_SPOKEN_FEEDBACK_DISABLED";
      case EVENT_CAPS_LOCK_ON:
        return "EVENT_CAPS_LOCK_ON";
      case EVENT_CAPS_LOCK_OFF:
        return "EVENT_CAPS_LOCK_OFF";
      case EVENT_NUM_LOCK_ON:
        return "EVENT_NUM_LOCK_ON";
      case EVENT_NUM_LOCK_OFF:
        return "EVENT_NUM_LOCK_OFF";
      case EVENT_SCROLL_LOCK_ON:
        return "EVENT_SCROLL_LOCK_ON";
      case EVENT_SCROLL_LOCK_OFF:
        return "EVENT_SCROLL_LOCK_OFF";
      case EVENT_ORIENTATION_PORTRAIT:
        return "EVENT_ORIENTATION_PORTRAIT";
      case EVENT_ORIENTATION_LANDSCAPE:
        return "EVENT_ORIENTATION_LANDSCAPE";
      case EVENT_ACCESS_FOCUS_HINT:
        return "EVENT_ACCESS_FOCUS_HINT";
      case EVENT_ACCESS_FOCUS_HINT_FORCED:
        return "EVENT_ACCESS_FOCUS_HINT_FORCED";
      case EVENT_INPUT_FOCUS_HINT:
        return "EVENT_INPUT_FOCUS_HINT";
      case EVENT_INPUT_FOCUS_HINT_FORCED:
        return "EVENT_INPUT_FOCUS_HINT_FORCED";
      case EVENT_TYPE_INPUT_TEXT_CLEAR:
        return "EVENT_TYPE_INPUT_TEXT_CLEAR";
      case EVENT_TYPE_INPUT_TEXT_REMOVE:
        return "EVENT_TYPE_INPUT_TEXT_REMOVE";
      case EVENT_TYPE_INPUT_TEXT_ADD:
        return "EVENT_TYPE_INPUT_TEXT_ADD";
      case EVENT_TYPE_INPUT_TEXT_REPLACE:
        return "EVENT_TYPE_INPUT_TEXT_REPLACE";
      case EVENT_TYPE_INPUT_TEXT_PASSWORD_ADD:
        return "EVENT_TYPE_INPUT_TEXT_PASSWORD_ADD";
      case EVENT_TYPE_INPUT_TEXT_PASSWORD_REMOVE:
        return "EVENT_TYPE_INPUT_TEXT_PASSWORD_REMOVE";
      case EVENT_TYPE_INPUT_TEXT_PASSWORD_REPLACE:
        return "EVENT_TYPE_INPUT_TEXT_PASSWORD_REPLACE";
      case EVENT_TYPE_INPUT_CHANGE_INVALID:
        return "EVENT_TYPE_INPUT_CHANGE_INVALID";
      case EVENT_TYPE_INPUT_SELECTION_FOCUS_EDIT_TEXT:
        return "EVENT_TYPE_INPUT_SELECTION_FOCUS_EDIT_TEXT";
      case EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_BEGINNING:
        return "EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_BEGINNING";
      case EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_END:
        return "EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_END";
      case EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_NO_SELECTION:
        return "EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_NO_SELECTION";
      case EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_WITH_SELECTION:
        return "EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_WITH_SELECTION";
      case EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_SELECTION_CLEARED:
        return "EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_SELECTION_CLEARED";
      case EVENT_TYPE_INPUT_SELECTION_CUT:
        return "EVENT_TYPE_INPUT_SELECTION_CUT";
      case EVENT_TYPE_INPUT_SELECTION_PASTE:
        return "EVENT_TYPE_INPUT_SELECTION_PASTE";
      case EVENT_TYPE_INPUT_SELECTION_TEXT_TRAVERSAL:
        return "EVENT_TYPE_INPUT_SELECTION_TEXT_TRAVERSAL";
      case EVENT_TYPE_INPUT_SELECTION_SELECT_ALL:
        return "EVENT_TYPE_INPUT_SELECTION_SELECT_ALL";
      case EVENT_TYPE_INPUT_SELECTION_SELECT_ALL_WITH_KEYBOARD:
        return "EVENT_TYPE_INPUT_SELECTION_SELECT_ALL_WITH_KEYBOARD";
      case EVENT_TYPE_INPUT_SELECTION_RESET_SELECTION:
        return "EVENT_TYPE_INPUT_SELECTION_RESET_SELECTION";
      case EVENT_SELECT_SPEECH_RATE:
        return "EVENT_SELECT_SPEECH_RATE";
      case EVENT_SELECT_VERBOSITY:
        return "EVENT_SELECT_VERBOSITY";
      case EVENT_SELECT_GRANULARITY:
        return "EVENT_SELECT_GRANULARITY";
      case EVENT_SELECT_AUDIO_FOCUS:
        return "EVENT_SELECT_AUDIO_FOCUS";
      case EVENT_SPEECH_RATE_CHANGE:
        return "EVENT_SPEECH_RATE_CHANGE";
      case EVENT_AUDIO_FOCUS_SWITCH:
        return "EVENT_AUDIO_FOCUS_SWITCH";
      default:
        return AccessibilityEventUtils.typeToString(eventType);
    }
  }

  private static String getFlavorName(@Flavor int flavor) {
    switch (flavor) {
      case FLAVOR_NONE:
        return "FLAVOR_NONE";
      case FLAVOR_ARC:
        return "FLAVOR_ARC";
      case FLAVOR_TV:
        return "FLAVOR_TV";
      case FLAVOR_SWITCH_ACCESS:
        return "FLAVOR_SWITCH_ACCESS";
      default:
        return "UNKNOWN";
    }
  }
}
