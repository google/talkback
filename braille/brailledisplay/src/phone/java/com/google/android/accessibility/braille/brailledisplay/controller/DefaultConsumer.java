/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.controller;

import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_ACTIVATE_CURRENT;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_BRAILLE_DISPLAY_SETTINGS;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_BRAILLE_KEY;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_CONTROL_NEXT;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_CONTROL_PREVIOUS;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_HEADING_NEXT;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_HEADING_PREVIOUS;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_HELP;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_KEY_ENTER;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_LINK_NEXT;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_LINK_PREVIOUS;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_BACKWARD;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_FORWARD;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_NAV_BOTTOM;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_NAV_BOTTOM_OR_KEY_ACTIVATE;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_NAV_ITEM_NEXT;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_NAV_LINE_NEXT;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_NAV_LINE_PREVIOUS;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_NAV_TOP;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_NAV_TOP_OR_KEY_ACTIVATE;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_NEXT_READING_CONTROL;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_PREVIOUS_READING_CONTROL;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_ROUTE;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_SCROLL_BACKWARD;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_SCROLL_FORWARD;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_STOP_READING;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_SWITCH_TO_NEXT_INPUT_LANGUAGE;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_SWITCH_TO_NEXT_OUTPUT_LANGUAGE;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_TALKBACK_SETTINGS;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_TOGGLE_BRAILLE_GRADE;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_TOGGLE_VOICE_FEEDBACK;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_TURN_OFF_BRAILLE_DISPLAY;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_WINDOW_NEXT;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_WINDOW_PREVIOUS;
import static com.google.android.accessibility.braille.common.translate.EditBufferUtils.NO_CURSOR;
import static com.google.android.accessibility.utils.AccessibilityServiceCompatUtils.Constants.BRAILLE_KEYBOARD;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayImeUnavailableActivity;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayTalkBackSpeaker;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.brailledisplay.analytics.BrailleDisplayAnalytics;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorDisplayer;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorFocus;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorIme;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorNodeText;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorScreenReader;
import com.google.android.accessibility.braille.brailledisplay.controller.CellsContentConsumer.Reason;
import com.google.android.accessibility.braille.brailledisplay.platform.PersistentStorage;
import com.google.android.accessibility.braille.brailledisplay.settings.BrailleDisplaySettingsActivity;
import com.google.android.accessibility.braille.brailledisplay.settings.KeyBindingsActivity;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;
import com.google.android.accessibility.braille.common.BrailleCommonUtils;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.braille.common.TalkBackSpeaker.AnnounceType;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages.Code;
import com.google.android.accessibility.braille.interfaces.ScreenReaderActionPerformer.ScreenReaderAction;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoRef;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils.Constants;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.material.MaterialComponentUtils;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Optional;

/** An event consumer digests calls not handled by the hosted IME. */
class DefaultConsumer implements EventConsumer {
  private static final String TAG = "DefaultNavigationMode";
  private static final int LOG_LANGUAGE_CHANGE_DELAY_MS =
      10000; // After the lowest TTS speed announcement ends.
  private static final int INPUT = 1;
  private static final int OUTPUT = 2;
  private final Context context;
  private final CellsContentConsumer cellsContentConsumer;
  private final NodeBrailler nodeBrailler;
  private final FeedbackManager feedbackManager;
  private final BehaviorScreenReader behaviorScreenReaderAction;
  private final BehaviorFocus behaviorFocus;
  private final BehaviorDisplayer behaviorDisplayer;
  private final BehaviorIme behaviorIme;
  private final AccessibilityNodeInfoRef lastFocusedNode = new AccessibilityNodeInfoRef();
  private final Handler loggingHandler;
  private AlertDialog turnOffBdDialog;

  public DefaultConsumer(
      Context context,
      CellsContentConsumer cellsContentConsumer,
      FeedbackManager feedbackManager,
      BehaviorNodeText behaviorNodeText,
      BehaviorFocus behaviorFocus,
      BehaviorScreenReader behaviorScreenReaderAction,
      BehaviorDisplayer behaviorDisplayer,
      BehaviorIme behaviorIme) {
    this.context = context;
    this.behaviorScreenReaderAction = behaviorScreenReaderAction;
    this.behaviorFocus = behaviorFocus;
    this.behaviorDisplayer = behaviorDisplayer;
    this.behaviorIme = behaviorIme;
    this.cellsContentConsumer = cellsContentConsumer;
    this.nodeBrailler = new NodeBrailler(context, behaviorNodeText);
    this.feedbackManager = feedbackManager;
    loggingHandler = new LoggingHandler(context);
  }

  /** Moves accessibility focus to the first focusable node of the previous 'line'. */
  private boolean linePrevious() {
    return feedbackManager.emitOnFailure(
        behaviorScreenReaderAction.performAction(ScreenReaderAction.PREVIOUS_LINE),
        FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
  }

  /** Moves accessibility focus to the first focusable node of the next 'line'. */
  private boolean lineNext() {
    return feedbackManager.emitOnFailure(
        behaviorScreenReaderAction.performAction(ScreenReaderAction.NEXT_LINE),
        FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
  }

  private boolean itemPrevious() {
    return feedbackManager.emitOnFailure(
        behaviorScreenReaderAction.performAction(ScreenReaderAction.PREVIOUS_ITEM),
        FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
  }

  private boolean itemNext() {
    return feedbackManager.emitOnFailure(
        behaviorScreenReaderAction.performAction(ScreenReaderAction.NEXT_ITEM),
        FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
  }

  @CanIgnoreReturnValue
  @Override
  public boolean onMappedInputEvent(BrailleInputEvent event) {
    // Switch to the braille display keyboard when typing on the braille display and braille
    // keyboard is not default anytime.
    if (isTriggerImeSwitchCommands(event)) {
      trySwitchIme(event);
    }
    switch (event.getCommand()) {
      case CMD_BRAILLE_KEY:
        return behaviorFocus.handleBrailleKeyWithoutKeyboardOpen(event.getArgument());
      case CMD_NAV_ITEM_PREVIOUS:
        return itemPrevious();
      case CMD_NAV_ITEM_NEXT:
        return itemNext();
      case CMD_NAV_LINE_PREVIOUS:
        return linePrevious();
      case CMD_NAV_LINE_NEXT:
        return lineNext();
      case CMD_NAV_TOP_OR_KEY_ACTIVATE:
      case CMD_NAV_BOTTOM_OR_KEY_ACTIVATE:
      case CMD_KEY_ENTER:
      case CMD_ACTIVATE_CURRENT:
        return feedbackManager.emitOnFailure(
            behaviorScreenReaderAction.performAction(ScreenReaderAction.CLICK_CURRENT),
            FeedbackManager.TYPE_COMMAND_FAILED);
      case CMD_ROUTE:
        Optional<ClickableSpan[]> clickableSpans =
            cellsContentConsumer.getClickableSpans(event.getArgument());
        if (clickableSpans.isPresent() && clickableSpans.get().length > 0) {
          if (activateClickableSpan(context, clickableSpans.get()[0])) {
            return true;
          }
        }
        AccessibilityNodeInfoCompat node =
            cellsContentConsumer.getAccessibilityNode(event.getArgument());
        boolean result =
            feedbackManager.emitOnFailure(
                behaviorScreenReaderAction.performAction(ScreenReaderAction.CLICK_NODE, node),
                FeedbackManager.TYPE_COMMAND_FAILED);
        int index = cellsContentConsumer.getTextIndexInWhole(event.getArgument());
        if (node != null
            && AccessibilityNodeInfoUtils.isTextSelectable(node)
            && index != NO_CURSOR) {
          // TODO: handle selectable text too.
          final Bundle args = new Bundle();
          args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, index);
          args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT, index);
          PerformActionUtils.performAction(
              node, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, args, /* eventId= */ null);
        }
        return result;
      case CMD_NEXT_READING_CONTROL:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.NEXT_READING_CONTROL);
      case CMD_PREVIOUS_READING_CONTROL:
        return behaviorScreenReaderAction.performAction(
            ScreenReaderAction.PREVIOUS_READING_CONTROL);
      case CMD_NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_FORWARD:
        return behaviorScreenReaderAction.performAction(
            ScreenReaderAction.NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_FORWARD);
      case CMD_NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_BACKWARD:
        return behaviorScreenReaderAction.performAction(
            ScreenReaderAction.NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_BACKWARD);
      case CMD_SCROLL_FORWARD:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.SCROLL_FORWARD);
      case CMD_SCROLL_BACKWARD:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.SCROLL_BACKWARD);
      case CMD_NAV_TOP:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.NAVIGATE_TO_TOP);
      case CMD_NAV_BOTTOM:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.NAVIGATE_TO_BOTTOM);
      case CMD_HEADING_NEXT:
        return behaviorScreenReaderAction.performAction(
            isWebContainer(event)
                ? ScreenReaderAction.WEB_NEXT_HEADING
                : ScreenReaderAction.NEXT_HEADING);
      case CMD_HEADING_PREVIOUS:
        return behaviorScreenReaderAction.performAction(
            isWebContainer(event)
                ? ScreenReaderAction.WEB_PREVIOUS_HEADING
                : ScreenReaderAction.PREVIOUS_HEADING);
      case CMD_CONTROL_NEXT:
        return behaviorScreenReaderAction.performAction(
            isWebContainer(event)
                ? ScreenReaderAction.WEB_NEXT_CONTROL
                : ScreenReaderAction.NEXT_CONTROL);
      case CMD_CONTROL_PREVIOUS:
        return behaviorScreenReaderAction.performAction(
            isWebContainer(event)
                ? ScreenReaderAction.WEB_PREVIOUS_CONTROL
                : ScreenReaderAction.PREVIOUS_CONTROL);
      case CMD_LINK_NEXT:
        return behaviorScreenReaderAction.performAction(
            isWebContainer(event)
                ? ScreenReaderAction.WEB_NEXT_LINK
                : ScreenReaderAction.NEXT_LINK);
      case CMD_LINK_PREVIOUS:
        return behaviorScreenReaderAction.performAction(
            isWebContainer(event)
                ? ScreenReaderAction.WEB_PREVIOUS_LINK
                : ScreenReaderAction.PREVIOUS_LINK);
      case CMD_WINDOW_NEXT:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.NEXT_WINDOW);
      case CMD_WINDOW_PREVIOUS:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.PREVIOUS_WINDOW);
      case CMD_SWITCH_TO_NEXT_OUTPUT_LANGUAGE:
        if (BrailleUserPreferences.readAvailablePreferredCodes(context).size() > 1) {
          Code nextCode = BrailleUserPreferences.getNextOutputCode(context);
          String userFacingName = nextCode.getUserFacingName(context);
          BrailleUserPreferences.writeCurrentActiveOutputCode(context, nextCode);
          displayTimedMessage(userFacingName);
          // Delay logging because user might go through a long list of languages before reaching
          // his desired language.
          loggingHandler.removeMessages(OUTPUT);
          loggingHandler.sendMessageDelayed(
              loggingHandler.obtainMessage(OUTPUT), LOG_LANGUAGE_CHANGE_DELAY_MS);
          BrailleUserPreferences.writeSwitchContactedCount(context);
          BrailleDisplayTalkBackSpeaker.getInstance()
              .speak(
                  getSwitchLanguageAnnounceText(
                      context.getString(
                          R.string.bd_switch_reading_language_announcement, userFacingName)),
                  AnnounceType.INTERRUPT);
          return true;
        }
        break;
      case CMD_SWITCH_TO_NEXT_INPUT_LANGUAGE:
        if (BrailleUserPreferences.readAvailablePreferredCodes(context).size() > 1) {
          Code nextCode = BrailleUserPreferences.getNextInputCode(context);
          String userFacingName = nextCode.getUserFacingName(context);
          BrailleUserPreferences.writeCurrentActiveInputCode(context, nextCode);
          displayTimedMessage(userFacingName);
          // Delay logging because user might go through a long list of languages before reaching
          // his desired language.
          loggingHandler.removeMessages(INPUT);
          loggingHandler.sendMessageDelayed(
              loggingHandler.obtainMessage(INPUT), LOG_LANGUAGE_CHANGE_DELAY_MS);
          BrailleUserPreferences.writeSwitchContactedCount(context);
          BrailleDisplayTalkBackSpeaker.getInstance()
              .speak(
                  getSwitchLanguageAnnounceText(
                      context.getString(
                          R.string.bd_switch_typing_language_announcement, userFacingName)),
                  AnnounceType.INTERRUPT);
          return true;
        }
        break;
      case CMD_TOGGLE_BRAILLE_GRADE:
        Code currentInputCode =
            BrailleUserPreferences.readCurrentActiveInputCodeAndCorrect(context);
        Code currentOutputCode =
            BrailleUserPreferences.readCurrentActiveOutputCodeAndCorrect(context);
        boolean newContractedMode = !BrailleUserPreferences.readContractedMode(context);
        BrailleUserPreferences.writeContractedMode(context, newContractedMode);
        if (currentInputCode.isSupportsContracted(context)) {
          BrailleDisplayAnalytics.getInstance(context)
              .logBrailleInputCodeSetting(currentInputCode, newContractedMode);
        }
        if (currentOutputCode.isSupportsContracted(context)) {
          BrailleDisplayAnalytics.getInstance(context)
              .logBrailleOutputCodeSetting(currentOutputCode, newContractedMode);
        }
        String feedback =
            context.getString(
                newContractedMode
                    ? R.string.bd_switch_to_contracted
                    : R.string.bd_switch_to_uncontracted);
        BrailleDisplayTalkBackSpeaker.getInstance().speak(feedback, AnnounceType.INTERRUPT);
        displayTimedMessage(feedback);
        return true;
      case CMD_HELP:
        return startHelpActivity();
      case CMD_BRAILLE_DISPLAY_SETTINGS:
        return startBrailleDisplayActivity();
      case CMD_TURN_OFF_BRAILLE_DISPLAY:
        if (turnOffBdDialog == null) {
          turnOffBdDialog =
              MaterialComponentUtils.alertDialogBuilder(
                      behaviorScreenReaderAction.getAccessibilityService())
                  .setTitle(R.string.bd_turn_off_bd_confirm_dialog_title)
                  .setMessage(R.string.bd_turn_off_bd_confirm_dialog_message)
                  .setPositiveButton(
                      R.string.bd_turn_off_bd_confirm_dialog_positive_button,
                      (dialog1, which) ->
                          PersistentStorage.setConnectionEnabledByUser(context, false))
                  .setNegativeButton(android.R.string.cancel, null)
                  .create();
          turnOffBdDialog
              .getWindow()
              .setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        }
        if (!turnOffBdDialog.isShowing()) {
          turnOffBdDialog.show();
          return true;
        }
        break;
      case CMD_TOGGLE_VOICE_FEEDBACK:
        boolean success =
            behaviorScreenReaderAction.performAction(ScreenReaderAction.TOGGLE_VOICE_FEEDBACK);
        String timedMessage =
            context.getString(
                behaviorScreenReaderAction.getVoiceFeedbackEnabled()
                    ? R.string.bd_voice_feedback_unmute
                    : R.string.bd_voice_feedback_mute);
        displayTimedMessage(timedMessage);
        return success;
      case CMD_TALKBACK_SETTINGS:
        return startTalkBackSettingsActivity();
      case CMD_STOP_READING:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.STOP_READING);
      default: // fall through
    }
    return false;
  }

  @Override
  public void onActivate() {
    lastFocusedNode.clear();
    // Braille the focused node, or if that fails, braille
    // the first focusable node.
    if (!brailleFocusedNode(Reason.START_UP)) {
      brailleFirstFocusableNode(Reason.START_UP);
    }
  }

  @Override
  public void onDeactivate() {}

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
        cellsContentConsumer.setContent(formatEventToBraille(event), Reason.NAVIGATE_TO_NEW_NODE);
        break;
      case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
        brailleFocusedNode(Reason.WINDOW_CHANGED);
        break;
      case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
      case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
        if (!brailleFocusedNode(Reason.WINDOW_CHANGED)) {
          // Since focus is typically not set in a newly opened
          // window, so braille the window as-if the first focusable
          // node had focus.  We don't update the focus because that
          // will make other services (e.g. talkback) reflect this
          // change, which is not desired.
          brailleFirstFocusableNode(Reason.WINDOW_CHANGED);
        }
        break;
      default: // fall out
    }
  }

  private static class LoggingHandler extends Handler {
    private final Context context;

    private LoggingHandler(Context context) {
      super();
      this.context = context;
    }

    @Override
    public void handleMessage(Message msg) {
      if (msg.what == INPUT) {
        BrailleDisplayAnalytics.getInstance(context)
            .logBrailleInputCodeSetting(
                BrailleUserPreferences.readCurrentActiveInputCodeAndCorrect(context),
                BrailleUserPreferences.readContractedMode(context));
      } else if (msg.what == OUTPUT) {
        BrailleDisplayAnalytics.getInstance(context)
            .logBrailleOutputCodeSetting(
                BrailleUserPreferences.readCurrentActiveOutputCodeAndCorrect(context),
                BrailleUserPreferences.readContractedMode(context));
      }
    }
  }

  private boolean activateClickableSpan(Context context, ClickableSpan clickableSpan) {
    if (clickableSpan instanceof URLSpan) {
      final Intent intent =
          new Intent(Intent.ACTION_VIEW, Uri.parse(((URLSpan) clickableSpan).getURL()));
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      try {
        context.startActivity(intent);
      } catch (ActivityNotFoundException exception) {
        BrailleDisplayLog.e(TAG, "Failed to start activity", exception);
        return false;
      }
    }
    try {
      clickableSpan.onClick(null);
    } catch (RuntimeException exception) {
      BrailleDisplayLog.e(TAG, "Failed to invoke ClickableSpan", exception);
      return false;
    }
    return true;
  }

  private AccessibilityNodeInfoCompat getFocusedNode(boolean fallbackOnRoot) {
    return behaviorFocus.getAccessibilityFocusNode(fallbackOnRoot);
  }

  /**
   * Formats some braille content from an {@link AccessibilityEvent}.
   *
   * @param event The event from which to format an utterance.
   * @return The formatted utterance.
   */
  private CellsContent formatEventToBraille(AccessibilityEvent event) {
    AccessibilityNodeInfoCompat eventNode = AccessibilityEventUtils.sourceCompat(event);
    if (eventNode != null) {
      CellsContent ret = nodeBrailler.brailleEvent(event);
      ret.setPanStrategy(CellsContent.PAN_CURSOR);
      lastFocusedNode.reset(eventNode);
      if (!TextUtils.isEmpty(ret.getText())) {
        return ret;
      }
    }

    // Fall back on putting the event text on the display.
    // TODO: This can interfere with what's on the display and should be
    // done in a more disciplined manner.
    BrailleDisplayLog.v(TAG, "No node on event, falling back on event text");
    lastFocusedNode.clear();
    return new CellsContent(AccessibilityEventUtils.getEventTextOrDescription(event));
  }

  @CanIgnoreReturnValue
  private boolean brailleFocusedNode(Reason reason) {
    AccessibilityNodeInfoCompat focused = getFocusedNode(false);
    if (focused != null) {
      CellsContent content = nodeBrailler.brailleNode(focused);
      if (focused.equals(lastFocusedNode.get())
          && (content.getPanStrategy() == CellsContent.PAN_RESET)) {
        content.setPanStrategy(CellsContent.PAN_KEEP);
      }
      cellsContentConsumer.setContent(content, reason);
      lastFocusedNode.reset(focused);
      return true;
    }
    return false;
  }

  private void brailleFirstFocusableNode(Reason reason) {
    AccessibilityNodeInfoCompat root = getFocusedNode(true);
    if (root != null) {
      AccessibilityNodeInfoCompat toBraille;
      if (AccessibilityNodeInfoUtils.shouldFocusNode(root)) {
        toBraille = root;
      } else {
        TraversalStrategy traversalStrategy =
            TraversalStrategyUtils.getTraversalStrategy(
                root, behaviorFocus.createFocusFinder(), TraversalStrategy.SEARCH_FOCUS_FORWARD);
        toBraille = traversalStrategy.findFocus(root, TraversalStrategy.SEARCH_FOCUS_FORWARD);
        if (toBraille == null) {
          // Fall back on root as a last resort.
          toBraille = root;
        }
      }
      CellsContent content = nodeBrailler.brailleNode(toBraille);
      if (AccessibilityNodeInfoRef.isNull(lastFocusedNode)
          && (content.getPanStrategy() == CellsContent.PAN_RESET)) {
        content.setPanStrategy(CellsContent.PAN_KEEP);
      }
      lastFocusedNode.clear();
      cellsContentConsumer.setContent(content, reason);
    }
  }

  // Check if node is web container.
  private boolean isWebContainer(BrailleInputEvent event) {
    return WebInterfaceUtils.isWebContainer(
        cellsContentConsumer.getAccessibilityNode(event.getArgument()));
  }

  private boolean startHelpActivity() {
    Intent intent = new Intent(context, KeyBindingsActivity.class);
    intent.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TOP
            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    intent.putExtra(KeyBindingsActivity.PROPERTY_KEY, behaviorDisplayer.getDeviceProperties());
    context.startActivity(intent);
    return true;
  }

  private boolean startTalkBackSettingsActivity() {
    Intent intent = new Intent();
    intent.setComponent(Constants.SETTINGS_ACTIVITY);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
    return true;
  }

  private boolean startBrailleDisplayActivity() {
    Intent intent = new Intent(context, BrailleDisplaySettingsActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
    return true;
  }

  private String getSwitchLanguageAnnounceText(String text) {
    StringBuilder sb = new StringBuilder(text);
    if (BrailleUserPreferences.readAnnounceSwitchContracted(context)) {
      sb.append("\n");
      sb.append(context.getString(R.string.bd_switch_contracted_mode_announcement));
    }
    return sb.toString();
  }

  private void displayTimedMessage(String timedMessage) {
    if (!behaviorDisplayer.isBrailleDisplayConnected()) {
      return;
    }
    cellsContentConsumer.setTimedContent(
        new CellsContent(timedMessage),
        BrailleUserPreferences.getTimedMessageDurationInMillisecond(
            context, timedMessage.length()));
  }

  private void trySwitchIme(BrailleInputEvent event) {
    if (isTriggerImeSwitchCommands(event)) {
      if (BrailleCommonUtils.isInputMethodEnabled(context, BRAILLE_KEYBOARD)
          && !BrailleCommonUtils.isInputMethodDefault(context, BRAILLE_KEYBOARD)) {
        if (!behaviorIme.switchInputMethodToBrailleKeyboard()) {
          showSwitchInputMethodDialog();
        }
      } else {
        showSwitchInputMethodDialog();
      }
    }
  }

  private boolean isTriggerImeSwitchCommands(BrailleInputEvent event) {
    return event.getCommand() == BrailleInputEvent.CMD_BRAILLE_KEY
        || event.getCommand() == BrailleInputEvent.CMD_KEY_DEL;
  }

  private void showSwitchInputMethodDialog() {
    BrailleDisplayImeUnavailableActivity.initialize(
        () ->
            MaterialComponentUtils.alertDialogBuilder(
                behaviorScreenReaderAction.getAccessibilityService()));
    if (BrailleDisplayImeUnavailableActivity.necessaryToStart(context)) {
      Intent intent = new Intent(context, BrailleDisplayImeUnavailableActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(intent);
    }
  }
}
