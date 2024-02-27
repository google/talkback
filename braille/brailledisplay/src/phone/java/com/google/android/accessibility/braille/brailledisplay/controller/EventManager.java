package com.google.android.accessibility.braille.brailledisplay.controller;

import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

import android.content.Context;
import android.os.PowerManager;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.FeatureFlagReader;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.brailledisplay.analytics.BrailleDisplayAnalytics;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorDisplayer;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorFocus;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorIme;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorNavigation;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorNodeText;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorScreenReader;
import com.google.android.accessibility.braille.brailledisplay.controller.CellsContentConsumer.Reason;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils.SupportedCommand;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.braille.interfaces.ScreenReaderActionPerformer.ScreenReaderAction;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleDisplay.CustomLabelAction;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** A class that transfers events to responding event consumers. */
public class EventManager implements EventConsumer {
  private static final String TAG = "EventManager";

  /** Accessibility event types that warrant rechecking the current state. */
  private static final int UPDATE_STATE_EVENT_MASK =
      TYPE_VIEW_FOCUSED
          | TYPE_WINDOWS_CHANGED
          | TYPE_WINDOW_STATE_CHANGED
          | TYPE_WINDOW_CONTENT_CHANGED
          | TYPE_VIEW_ACCESSIBILITY_FOCUSED
          | TYPE_VIEW_TEXT_SELECTION_CHANGED
          | TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED;

  private final Context context;
  private final DefaultConsumer defaultConsumer;
  private final EditorConsumer editorConsumer;
  private final BehaviorIme behaviorIme;
  private final BehaviorFocus behaviorFocus;
  private final BehaviorNodeText behaviorNodeText;
  private final BehaviorDisplayer behaviorDisplayer;
  private final BehaviorNavigation behaviorNavigation;
  private final BehaviorScreenReader behaviorScreenReader;
  private final CellsContentConsumer cellsContentConsumer;
  private final PowerManager powerManager;
  private final AutoScrollManager autoScrollManager;
  private final FeedbackManager feedbackManager;
  private EventConsumer currentConsumer;
  private boolean windowActive;

  public EventManager(
      Context context,
      CellsContentConsumer cellsContentConsumer,
      FeedbackManager feedbackManager,
      BehaviorIme behaviorIme,
      BehaviorFocus behaviorFocus,
      BehaviorScreenReader behaviorScreenReader,
      BehaviorNodeText behaviorNodeText,
      BehaviorDisplayer behaviorDisplayer,
      BehaviorNavigation behaviorNavigation) {
    this.context = context;
    this.cellsContentConsumer = cellsContentConsumer;
    autoScrollManager =
        new AutoScrollManager(context, behaviorNavigation, feedbackManager, behaviorDisplayer);
    this.behaviorIme = behaviorIme;
    this.behaviorScreenReader = behaviorScreenReader;
    this.behaviorFocus = behaviorFocus;
    this.behaviorNodeText = behaviorNodeText;
    this.behaviorDisplayer = behaviorDisplayer;
    this.behaviorNavigation = behaviorNavigation;
    this.feedbackManager = feedbackManager;
    defaultConsumer =
        new DefaultConsumer(
            context,
            cellsContentConsumer,
            feedbackManager,
            behaviorNodeText,
            behaviorFocus,
            behaviorScreenReader,
            behaviorDisplayer,
            behaviorIme);
    editorConsumer = new EditorConsumer(context, behaviorIme);
    currentConsumer = defaultConsumer;
    powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    windowActive = behaviorIme.isOnscreenKeyboardActive();
  }

  @Override
  public void onActivate() {
    currentConsumer.onActivate();
  }

  @Override
  public void onDeactivate() {
    currentConsumer.onDeactivate();
    autoScrollManager.stop();
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    BrailleDisplayLog.v(TAG, "isInteractive: " + powerManager.isInteractive());
    if (powerManager.isInteractive()) {
      if ((event.getEventType() & UPDATE_STATE_EVENT_MASK) == 0) {
        return;
      }
      updateConsumer();
      currentConsumer.onAccessibilityEvent(event);
    } else {
      // Clear the cell bar.
      cellsContentConsumer.setContent(new CellsContent(""), Reason.SCREEN_OFF);
      autoScrollManager.stop();
      return;
    }
    // Detect keyboard visibility.
    if (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
      boolean currentWindowActive = behaviorIme.isOnscreenKeyboardActive();
      if (windowActive != currentWindowActive) {
        windowActive = currentWindowActive;
        displayKeyboardVisibilityChangedTimedMessage(windowActive);
      }
    }
  }

  @Override
  @CanIgnoreReturnValue
  public boolean onMappedInputEvent(BrailleInputEvent event) {
    int command = event.getCommand();
    logBrailleCommands(command);
    if (shouldPanUp(command)) {
      if (behaviorNavigation.panUp()) {
        return true;
      }
      feedbackManager.emitFeedback(FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
    } else if (shouldPanDown(command)) {
      if (behaviorNavigation.panDown()) {
        return true;
      }
      feedbackManager.emitFeedback(FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
    } else {
      if (handleAutoscrollCommands(command, event.getArgument())) {
        return true;
      }
      if (cellsContentConsumer.isTimedMessageDisplaying()) {
        cellsContentConsumer.clearTimedMessage();
        // Skip route key events when a timed message is showing because route key event should
        // do nothing about taking action on the timed message.
        if (event.getCommand() == BrailleInputEvent.CMD_ROUTE) {
          return true;
        }
      }
      // Global commands can't be overridden.
      if (handleGlobalCommands(event)) {
        return true;
      }
      if (handleHighPriorityCommands(event)) {
        return true;
      }
      if (currentConsumer.onMappedInputEvent(event)) {
        return true;
      }
      feedbackManager.emitFeedback(FeedbackManager.TYPE_UNKNOWN_COMMAND);
    }
    return false;
  }

  /** Called when the reading control changes state. */
  public void onReadingControlChanged(String readingControlDescription) {
    displayTimedMessage(readingControlDescription);
  }

  private void updateConsumer() {
    if (behaviorIme.acceptInput()) {
      if (currentConsumer != editorConsumer) {
        currentConsumer.onDeactivate();
        currentConsumer = editorConsumer;
        currentConsumer.onActivate();
      }
    } else {
      if (currentConsumer != defaultConsumer) {
        currentConsumer.onDeactivate();
        currentConsumer = defaultConsumer;
        currentConsumer.onActivate();
      }
    }
  }

  /** Logs triggered command. */
  private void logBrailleCommands(int command) {
    for (SupportedCommand cmd : BrailleKeyBindingUtils.getSupportedCommands(context)) {
      if (cmd.getCommand() == command) {
        BrailleDisplayAnalytics.getInstance(context).logBrailleCommand(command);
        break;
      }
    }
  }

  private boolean shouldPanUp(int command) {
    boolean reversePanningButtons = BrailleUserPreferences.readReversePanningButtons(context);
    return (command == BrailleInputEvent.CMD_NAV_PAN_UP && !reversePanningButtons)
        || (command == BrailleInputEvent.CMD_NAV_PAN_DOWN && reversePanningButtons);
  }

  private boolean shouldPanDown(int command) {
    boolean reversePanningButtons = BrailleUserPreferences.readReversePanningButtons(context);
    return (command == BrailleInputEvent.CMD_NAV_PAN_DOWN && !reversePanningButtons)
        || (command == BrailleInputEvent.CMD_NAV_PAN_UP && reversePanningButtons);
  }

  private void displayKeyboardVisibilityChangedTimedMessage(boolean visible) {
    String timedMessage;
    if (visible) {
      CharSequence keyboardName = behaviorIme.getOnScreenKeyboardName();
      keyboardName =
          TextUtils.isEmpty(keyboardName) ? context.getString(R.string.bd_keyboard) : keyboardName;
      timedMessage = context.getString(R.string.bd_keyboard_showing, keyboardName);
    } else {
      timedMessage = context.getString(R.string.bd_keyboard_hidden);
    }
    displayTimedMessage(timedMessage);
  }

  private void displayTimedMessage(String timedMessage) {
    if (behaviorDisplayer.isBrailleDisplayConnected()) {
      cellsContentConsumer.setTimedContent(
          new CellsContent(timedMessage),
          BrailleUserPreferences.getTimedMessageDurationInMillisecond(
              context, timedMessage.length()));
    }
  }

  /** Handles the auto-scroll commands. */
  private boolean handleAutoscrollCommands(int command, int argument) {
    if (autoScrollManager.isActive()) {
      if (command == BrailleInputEvent.CMD_BRAILLE_KEY) {
        SupportedCommand supportedCommand =
            BrailleKeyBindingUtils.convertToCommand(context, /* hasSpace= */ false, argument);
        if (shouldDecreaseAutoScrollDuration(supportedCommand)) {
          autoScrollManager.decreaseDuration();
          return true;
        } else if (shouldIncreaseAutoScrollDuration(supportedCommand)) {
          autoScrollManager.increaseDuration();
          return true;
        }
      }
      autoScrollManager.stop();
      return true;
    }
    return false;
  }

  /** Returns whether to increase auto scroll duration. */
  private boolean shouldIncreaseAutoScrollDuration(SupportedCommand supportedCommand) {
    return supportedCommand != null
        && supportedCommand.getCommand() == BrailleInputEvent.CMD_INCREASE_AUTO_SCROLL_DURATION;
  }

  /** Returns whether to decrease auto scroll duration. */
  private boolean shouldDecreaseAutoScrollDuration(SupportedCommand supportedCommand) {
    return supportedCommand != null
        && supportedCommand.getCommand() == BrailleInputEvent.CMD_DECREASE_AUTO_SCROLL_DURATION;
  }

  /**
   * Handles global commands.
   *
   * @param event input event fromm braille display
   * @return return true when we own these actions return value is not related to talkback
   *     operation.
   */
  private boolean handleGlobalCommands(BrailleInputEvent event) {
    boolean success;
    switch (event.getCommand()) {
      case BrailleInputEvent.CMD_GLOBAL_HOME:
        success = behaviorScreenReader.performAction(ScreenReaderAction.GLOBAL_HOME);
        break;
      case BrailleInputEvent.CMD_GLOBAL_BACK:
        success = behaviorScreenReader.performAction(ScreenReaderAction.GLOBAL_BACK);
        break;
      case BrailleInputEvent.CMD_GLOBAL_RECENTS:
        success = behaviorScreenReader.performAction(ScreenReaderAction.GLOBAL_RECENTS);
        break;
      case BrailleInputEvent.CMD_GLOBAL_NOTIFICATIONS:
        success = behaviorScreenReader.performAction(ScreenReaderAction.GLOBAL_NOTIFICATIONS);
        break;
      case BrailleInputEvent.CMD_QUICK_SETTINGS:
        success = behaviorScreenReader.performAction(ScreenReaderAction.GLOBAL_QUICK_SETTINGS);
        break;
      case BrailleInputEvent.CMD_ALL_APPS:
        success = behaviorScreenReader.performAction(ScreenReaderAction.GLOBAL_ALL_APPS);
        break;
      default:
        return false;
    }
    if (success) {
      if (behaviorIme.isBrailleKeyboardActivated()) {
        behaviorIme.onFocusCleared();
      }
    } else {
      feedbackManager.emitFeedback(FeedbackManager.TYPE_COMMAND_FAILED);
    }
    return true;
  }

  /** Handles the commands not restricted in any situation. */
  private boolean handleHighPriorityCommands(BrailleInputEvent event) {
    boolean success = false;
    AccessibilityNodeInfoCompat node;
    switch (event.getCommand()) {
      case BrailleInputEvent.CMD_TOGGLE_SCREEN_SEARCH:
        success = behaviorScreenReader.performAction(ScreenReaderAction.SCREEN_SEARCH);
        break;
      case BrailleInputEvent.CMD_EDIT_CUSTOM_LABEL:
        node = behaviorFocus.getAccessibilityFocusNode(false);
        if (node != null && behaviorNodeText.supportsLabel(node)) {
          CharSequence viewLabel = behaviorNodeText.getCustomLabelText(node);
          // If no custom label, only have "add" option. If there is already a
          // label we have the "edit" and "remove" options.
          return behaviorNodeText.showLabelDialog(
              TextUtils.isEmpty(viewLabel)
                  ? CustomLabelAction.ADD_LABEL
                  : CustomLabelAction.EDIT_LABEL,
              node);
        }
        break;
      case BrailleInputEvent.CMD_OPEN_TALKBACK_MENU:
        success = behaviorScreenReader.performAction(ScreenReaderAction.OPEN_TALKBACK_MENU);
        break;
      case BrailleInputEvent.CMD_LONG_PRESS_CURRENT:
        success = behaviorScreenReader.performAction(ScreenReaderAction.LONG_CLICK_CURRENT);
        break;
      case BrailleInputEvent.CMD_LONG_PRESS_ROUTE:
        node = cellsContentConsumer.getAccessibilityNode(event.getArgument());
        if (node == null) {
          success = behaviorScreenReader.performAction(ScreenReaderAction.LONG_CLICK_CURRENT);
          break;
        }
        success = behaviorScreenReader.performAction(ScreenReaderAction.LONG_CLICK_NODE, node);
        break;
      case BrailleInputEvent.CMD_TOGGLE_AUTO_SCROLL:
        if (autoScrollManager.isActive()) {
          autoScrollManager.stop();
        } else {
          autoScrollManager.start();
        }
        success = true;
        break;
      case BrailleInputEvent.CMD_PLAY_PAUSE_MEDIA:
        success =
            !FeatureFlagReader.usePlayPauseMedia(context)
                || behaviorScreenReader.performAction(ScreenReaderAction.PLAY_PAUSE_MEDIA);
        break;
      default:
        return false;
    }
    if (success) {
      if (behaviorIme.isBrailleKeyboardActivated()) {
        behaviorIme.onFocusCleared();
      }
    } else {
      feedbackManager.emitFeedback(FeedbackManager.TYPE_COMMAND_FAILED);
    }
    // Always return true because we own these actions.
    return true;
  }
}
