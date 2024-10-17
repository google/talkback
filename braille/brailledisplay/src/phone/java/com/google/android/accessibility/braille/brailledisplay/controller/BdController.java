/*
 * Copyright (C) 2023 Google Inc.
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

import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_NAV_PAN_DOWN;
import static com.google.android.accessibility.braille.brltty.BrailleInputEvent.CMD_NAV_PAN_UP;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import androidx.appcompat.app.AlertDialog;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplay.BrailleImeProvider;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.OverlayDisplay;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.brailledisplay.analytics.BrailleDisplayAnalytics;
import com.google.android.accessibility.braille.brailledisplay.controller.CellsContentManager.Cursor;
import com.google.android.accessibility.braille.brailledisplay.controller.CellsContentManager.OnDisplayContentChangeListener;
import com.google.android.accessibility.braille.brailledisplay.platform.Controller;
import com.google.android.accessibility.braille.brailledisplay.platform.Displayer;
import com.google.android.accessibility.braille.brailledisplay.platform.PersistentStorage;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableBluetoothDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import com.google.android.accessibility.braille.brltty.BrailleDisplayProperties;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;
import com.google.android.accessibility.braille.brltty.SupportedDevicesHelper;
import com.google.android.accessibility.braille.common.BrailleCommonTalkBackSpeaker;
import com.google.android.accessibility.braille.common.BraillePreferenceUtils;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.braille.common.FeedbackManager;
import com.google.android.accessibility.braille.common.TalkBackSpeaker.AnnounceType;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages.Code;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForBrailleIme;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForBrailleIme.ResultForDisplay;
import com.google.android.accessibility.braille.interfaces.BrailleImeForBrailleDisplay;
import com.google.android.accessibility.braille.interfaces.ScreenReaderActionPerformer.ScreenReaderAction;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleCommon;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleDisplay;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleDisplay.CustomLabelAction;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FocusFinder;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Holds the business logic for the braille display feature. */
public class BdController implements Controller {
  private static final String TAG = "BdController";
  private static final int ANNOUNCE_DELAY_MS =
      200; // Delay, so that it will not be replaced by edit box focus announcement.
  private final Context context;
  private final TalkBackForBrailleDisplay talkBackForBrailleDisplay;
  private final BrailleImeProvider brailleImeProvider;
  private final FeedbackManager feedbackManager;
  private final OverlayDisplay overlayDisplay;
  private final EventManager eventManager;
  // While in 'suspended' mode, we ignore any input from the display and we render a static message
  // on the braille display; then when suspended mode gets exited, we return to normal operation.
  private final AtomicBoolean suspended = new AtomicBoolean();
  private final BehaviorFocus behaviorFocus = new BehaviorFocus();
  private TranslatorManager translatorManager;
  private CellsContentManager cellsContentManager;
  private Displayer displayer;
  private BehaviorIme behaviorIme;
  private AlertDialog someBrailleCommandUnavailableDialog;

  public BdController(
      Context context,
      TalkBackForBrailleDisplay talkBackForBrailleDisplay,
      TalkBackForBrailleCommon talkBackForBrailleCommon,
      BrailleImeProvider brailleImeProvider) {
    this.context = context;
    this.talkBackForBrailleDisplay = talkBackForBrailleDisplay;
    this.brailleImeProvider = brailleImeProvider;
    feedbackManager = new FeedbackManager(talkBackForBrailleCommon.getFeedBackController());
    translatorManager = new TranslatorManager(context);
    cellsContentManager =
        new CellsContentManager(
            context,
            new ImeStatusProvider(),
            translatorManager,
            new CellsContentManagerDotDisplayer());

    BehaviorNodeText behaviorNodeText = new BehaviorNodeText();
    BehaviorScreenReader behaviorScreenReaderAction = new BehaviorScreenReader();
    BehaviorDisplayer behaviorDisplayer = new BehaviorDisplayer();
    behaviorIme = new BehaviorIme();
    eventManager =
        new EventManager(
            context,
            cellsContentManager,
            feedbackManager,
            behaviorIme,
            behaviorFocus,
            behaviorScreenReaderAction,
            behaviorNodeText,
            behaviorDisplayer,
            new BehaviorNavigation());
    overlayDisplay = new OverlayDisplay(context, new OverlayDisplayCallback());
  }

  @Override
  public void onConnectHidStarted() {
    BrailleDisplayAnalytics.getInstance(context).logStartToEstablishHidConnection();
  }

  @Override
  public void onConnectRfcommStarted() {
    BrailleDisplayAnalytics.getInstance(context).logStartToEstablishRfcommConnection();
  }

  @Override
  public void onConnected() {
    BrailleDisplayLog.v(TAG, "onConnected");
    BrailleDisplayAnalytics.getInstance(context).logStartToConnectToBrailleDisplay();
  }

  @Override
  public void onDisplayerReady(Displayer displayer) {
    BrailleDisplayLog.v(TAG, "onDisplayerReady");
    feedbackManager.emitFeedback(FeedbackManager.Type.DISPLAY_CONNECTED);
    talkBackForBrailleDisplay.switchInputMethodToBrailleKeyboard();
    this.displayer = displayer;
    overlayDisplay.start(displayer.getDeviceProperties().getNumTextCells());
    cellsContentManager.start(displayer.getDeviceProperties().getNumTextCells());
    eventManager.onActivate();
    if (isBrailleKeyboardActivated()) {
      getBrailleImeForBrailleDisplay().onBrailleDisplayConnected();
    }
    logSessionMetrics();
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    if (!isDisplayerReady()) {
      BrailleDisplayLog.w(TAG, "Displayer is not ready yet.");
      return;
    }
    if (BrailleDisplayLog.DEBUG) {
      // The log is too long. Separate them into different lines.
      BrailleDisplayLog.v(TAG, "Event: " + accessibilityEvent);
      BrailleDisplayLog.v(TAG, "Node:  " + accessibilityEvent.getSource());
    }
    eventManager.onAccessibilityEvent(accessibilityEvent);
  }

  @Override
  public void onBrailleInputEvent(BrailleInputEvent brailleInputEvent) {
    if (!isDisplayerReady()) {
      BrailleDisplayLog.w(TAG, "Displayer is not ready yet.");
      return;
    }
    talkBackForBrailleDisplay.performAction(
        ScreenReaderAction.ACCESSIBILITY_FOCUS,
        talkBackForBrailleDisplay.getAccessibilityFocusNode(false));
    if (shouldResumeBrailleDisplay(brailleInputEvent)) {
      BrailleDisplayAnalytics.getInstance(context).logChangeTypingMode(/* toPhysical= */ true);
      suspended.set(false);
      BrailleCommonTalkBackSpeaker.getInstance()
          .speak(
              context.getString(R.string.bd_switch_to_braille_hardware_message),
              ANNOUNCE_DELAY_MS,
              AnnounceType.INTERRUPT);
      // The input is treated as resuming braille display.
      getBrailleImeForBrailleDisplay().onBrailleDisplayConnected();
      putAccessibilityFocusOnInputFocus();
    } else {
      eventManager.onMappedInputEvent(brailleInputEvent);
    }
  }

  @Override
  public void onDisconnected() {
    BrailleDisplayLog.v(TAG, "onDisconnected");
    feedbackManager.emitFeedback(FeedbackManager.Type.DISPLAY_DISCONNECTED);
    if (isSomeBrailleCommandUnavailableDialogShowing()) {
      someBrailleCommandUnavailableDialog.dismiss();
    }
    eventManager.onDeactivate();
    overlayDisplay.shutdown();
    cellsContentManager.shutdown();
    displayer = null;
    talkBackForBrailleDisplay.setVoiceFeedback(true);
    if (isBrailleKeyboardActivated()) {
      getBrailleImeForBrailleDisplay().onBrailleDisplayDisconnected();
    }
  }

  @Override
  public void onConnectFailed() {
    BrailleDisplayAnalytics.getInstance(context).logConnectionReset();
  }

  @Override
  public void onDestroy() {
    if (isDisplayerReady()) {
      onDisconnected();
    }
    if (translatorManager != null) {
      translatorManager.shutdown();
      translatorManager = null;
    }
  }

  @Override
  public void onReadingControlChanged(CharSequence readingControlDescription) {
    eventManager.onReadingControlChanged(readingControlDescription.toString());
  }

  /** Returns BrailleDisplayForBrailleIme . */
  public BrailleDisplayForBrailleIme getBrailleDisplayForBrailleIme() {
    return brailleDisplayForBrailleIme;
  }

  /** Switches braille display on or off. */
  public void switchBrailleDisplayOnOrOff() {
    boolean isEnable = !PersistentStorage.isConnectionEnabled(context);
    PersistentStorage.setConnectionEnabled(context, isEnable);
    String feedback =
        context.getString(
            isEnable ? R.string.bd_turn_braille_display_on : R.string.bd_turn_braille_display_off);
    BrailleCommonTalkBackSpeaker.getInstance().speak(feedback, AnnounceType.INTERRUPT);
  }

  private BrailleImeForBrailleDisplay getBrailleImeForBrailleDisplay() {
    return brailleImeProvider.getBrailleImeForBrailleDisplay();
  }

  private void logSessionMetrics() {
    boolean contracted = BrailleUserPreferences.readContractedMode(context);
    Code inputCode = BrailleUserPreferences.readCurrentActiveInputCodeAndCorrect(context);
    boolean inputContracted = inputCode.isSupportsContracted(context) && contracted;
    Code outputCode = BrailleUserPreferences.readCurrentActiveOutputCodeAndCorrect(context);
    boolean outputContracted = outputCode.isSupportsContracted(context) && contracted;

    ConnectableDevice device = displayer.getConnectableDevice();
    BrailleDisplayAnalytics.getInstance(context)
        .logStartedEvent(
            displayer.getDeviceProperties().getDriverCode(),
            SupportedDevicesHelper.getTruncatedName(device.name()),
            inputCode,
            outputCode,
            inputContracted,
            outputContracted,
            device.useHid(),
            device instanceof ConnectableBluetoothDevice);
  }

  private void updateDisplay(ResultForDisplay result) {
    AssembledResult assembledResult =
        new AssembledResult.Builder(translatorManager.getInputTranslator(), result)
            .appendHint(true)
            .appendAction(true)
            .build();
    cellsContentManager.setContent(
        assembledResult.textFieldTextClickableByteRange(),
        assembledResult.holdingsClickableByteRange(),
        assembledResult.actionClickableByteRange(),
        assembledResult.textByteSelection(),
        assembledResult.overlayTranslationResult(),
        result.isMultiLine());
  }

  private void putAccessibilityFocusOnInputFocus() {
    AccessibilityNodeInfoCompat node =
        talkBackForBrailleDisplay
            .createFocusFinder()
            .findFocusCompat(AccessibilityNodeInfo.FOCUS_INPUT);
    if (node != null) {
      node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
    }
  }

  private boolean isBrailleKeyboardActivated() {
    return getBrailleImeForBrailleDisplay() != null
        && getBrailleImeForBrailleDisplay().isBrailleKeyboardActivated();
  }

  private boolean isSomeBrailleCommandUnavailableDialogShowing() {
    return someBrailleCommandUnavailableDialog != null
        && someBrailleCommandUnavailableDialog.isShowing();
  }

  /** Returns true if a field suitable for modal editing is focused. */
  private boolean isModalFieldFocused() {
    // Only instances of EditText with both input and accessibility focus
    // should be edited modally.
    AccessibilityNodeInfoCompat accessibilityFocused =
        behaviorFocus.getAccessibilityFocusNode(false);
    if (accessibilityFocused == null) {
      return false;
    }
    return (accessibilityFocused != null
        && accessibilityFocused.isFocused()
        && AccessibilityNodeInfoUtils.nodeMatchesClassByType(accessibilityFocused, EditText.class));
  }

  /** Returns whether to resume braille display. */
  private boolean shouldResumeBrailleDisplay(BrailleInputEvent brailleInputEvent) {
    // In case braille display is too small to show BK in use in a row.
    return brailleInputEvent.getCommand() != CMD_NAV_PAN_UP
        && brailleInputEvent.getCommand() != CMD_NAV_PAN_DOWN
        && isBrailleKeyboardActivated()
        && suspended.get();
  }

  /** Whether any keyboard is onscreen. */
  public boolean isOnscreenKeyboardActive() {
    return talkBackForBrailleDisplay.isOnscreenKeyboardActive();
  }

  private boolean isDisplayerReady() {
    return displayer != null && displayer.isDisplayReady();
  }

  private final BrailleDisplayForBrailleIme brailleDisplayForBrailleIme =
      new BrailleDisplayForBrailleIme() {
        @Override
        public void onImeVisibilityChanged(boolean visible) {
          if (visible && BrailleUserPreferences.readShowNavigationCommandUnavailableTip(context)) {
            if (!isSomeBrailleCommandUnavailableDialogShowing()) {
              someBrailleCommandUnavailableDialog =
                  BraillePreferenceUtils.createTipAlertDialog(
                      talkBackForBrailleDisplay.getAccessibilityService(),
                      context.getString(
                          R.string.bd_notify_navigation_commands_unavailable_dialog_title),
                      context.getString(
                          R.string.bd_notify_navigation_commands_unavailable_dialog_message),
                      BrailleUserPreferences::writeShowNavigationCommandUnavailableTip);
              someBrailleCommandUnavailableDialog
                  .getWindow()
                  .setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
              if (VERSION.SDK_INT >= VERSION_CODES.P) {
                someBrailleCommandUnavailableDialog.show();
              } else {
                // Directly shows dialog will make Chrome hold the invalid InputConnection in
                // Android <= O lead cannot edit on the Chrome editor. post the action is the
                // workaround for fixing the bug without ag/3372490.
                new Handler().post(() -> someBrailleCommandUnavailableDialog.show());
              }
            }
          } else {
            if (isSomeBrailleCommandUnavailableDialogShowing()) {
              someBrailleCommandUnavailableDialog.dismiss();
            }
          }
        }

        @Override
        public void showOnDisplay(ResultForDisplay resultForDisplay) {
          if (!isDisplayerReady()) {
            return;
          }
          updateDisplay(resultForDisplay);
        }

        @Override
        public boolean isBrailleDisplayConnectedAndNotSuspended() {
          if (suspended.get()) {
            return false;
          }
          return isDisplayerReady();
        }

        @Override
        public void suspendInFavorOfBrailleKeyboard() {
          BrailleDisplayAnalytics.getInstance(context).logChangeTypingMode(/* toPhysical= */ false);
          suspended.set(true);
        }
      };

  private class CellsContentManagerDotDisplayer implements CellsContentManager.DotDisplayer {
    @Override
    public void displayDots(byte[] patterns, CharSequence text, int[] brailleToTextPositions) {
      if (!isDisplayerReady()) {
        return;
      }
      displayer.writeBrailleDots(patterns);
      overlayDisplay.displayDots(patterns, text, brailleToTextPositions);
    }
  }

  private class ImeStatusProvider implements CellsContentManager.ImeStatusProvider {

    @Override
    public boolean isImeOpen() {
      return isBrailleKeyboardActivated();
    }
  }

  private class OverlayDisplayCallback implements OverlayDisplay.Callback {
    @Override
    public void onInputEvent(BrailleInputEvent inputEvent) {
      onBrailleInputEvent(inputEvent);
    }
  }

  /** Behavior for modes: focus. */
  public class BehaviorFocus {
    public AccessibilityNodeInfoCompat getAccessibilityFocusNode(boolean fallbackOnRoot) {
      return talkBackForBrailleDisplay.getAccessibilityFocusNode(fallbackOnRoot);
    }

    public FocusFinder createFocusFinder() {
      return talkBackForBrailleDisplay.createFocusFinder();
    }

    /** Can perform braille key events without keyboard open. */
    public boolean handleBrailleKeyWithoutKeyboardOpen(int keyCode) {
      if (getBrailleImeForBrailleDisplay() != null) {
        // To allow BARD Mobile to receive keyboard shortcuts, whenever the user is typing with a
        // braille display.
        // See BARD Mobile keyboard shortcuts:
        // https://nlsbard.loc.gov/apidocs/BARDMobile.userguide.iOS.1.0.html#BrailleShortcutKeys7.3
        return getBrailleImeForBrailleDisplay().handleBrailleKeyForBARDMobile(keyCode);
      }
      return false;
    }
  }

  /** Behavior for modes: ime. */
  public class BehaviorIme {

    /** Whether Braille keyboard is open and accessibility focus on the editor. */
    public boolean acceptInput() {
      return BdController.this.isBrailleKeyboardActivated() && isModalFieldFocused();
    }

    /** Returns Whether keyboard is on screen . */
    public boolean isOnscreenKeyboardActive() {
      return BdController.this.isOnscreenKeyboardActive();
    }

    /** Returns on-screen keyboard name. */
    public CharSequence getOnScreenKeyboardName() {
      return talkBackForBrailleDisplay.getOnScreenKeyboardName();
    }

    /** Switch the input method to braille keyboard. */
    public boolean switchInputMethodToBrailleKeyboard() {
      return talkBackForBrailleDisplay.switchInputMethodToBrailleKeyboard();
    }

    /** Whether Braille keyboard is open. */
    public boolean isBrailleKeyboardActivated() {
      return BdController.this.isBrailleKeyboardActivated();
    }

    /** Whether using braille display as the input channel. */
    public boolean isSuspended() {
      return suspended.get();
    }

    public boolean sendBrailleDots(int dots) {
      BrailleDisplayAnalytics.getInstance(context).logTypingBrailleCharacter(/* count= */ 1);
      byte dotsInByte = (byte) (dots & 0xff);
      return feedbackManager.emitOnFailure(
          getBrailleImeForBrailleDisplay().sendBrailleDots(new BrailleCharacter(dotsInByte)),
          FeedbackManager.Type.COMMAND_FAILED);
    }

    public boolean moveCursorForward() {
      return getBrailleImeForBrailleDisplay().moveCursorForward();
    }

    public boolean moveCursorBackward() {
      return getBrailleImeForBrailleDisplay().moveCursorBackward();
    }

    public boolean moveCursorForwardByWord() {
      return getBrailleImeForBrailleDisplay().moveCursorForwardByWord();
    }

    public boolean moveCursorBackwardByWord() {
      return getBrailleImeForBrailleDisplay().moveCursorBackwardByWord();
    }

    public boolean moveCursorForwardByLine() {
      return getBrailleImeForBrailleDisplay().moveCursorForwardByLine();
    }

    public boolean moveCursorBackwardByLine() {
      return getBrailleImeForBrailleDisplay().moveCursorBackwardByLine();
    }

    public boolean moveCursor(int toIndex) {
      try {
        Cursor cursor = cellsContentManager.map(toIndex);
        if (cursor.type().equals(Cursor.Type.ACTION)) {
          return getBrailleImeForBrailleDisplay().commitHoldingsAndPerformEditorAction();
        } else if (cursor.type().equals(Cursor.Type.HOLDINGS)) {
          return getBrailleImeForBrailleDisplay().moveHoldingsCursor(cursor.position());
        } else {
          return getBrailleImeForBrailleDisplay().moveTextFieldCursor(cursor.position());
        }
      } catch (ExecutionException e) {
        BrailleDisplayLog.w(TAG, "Move cursor failed.", e);
        return false;
      }
    }

    /** Moves the cursor to the beginning of text field. */
    public boolean moveToBeginning() {
      return getBrailleImeForBrailleDisplay().moveCursorToBeginning();
    }

    /** Moves the cursor to the end of text field. */
    public boolean moveToEnd() {
      return getBrailleImeForBrailleDisplay().moveCursorToEnd();
    }

    public boolean deleteBackward() {
      return getBrailleImeForBrailleDisplay().deleteBackward();
    }

    public boolean deleteWordBackward() {
      return getBrailleImeForBrailleDisplay().deleteWordBackward();
    }

    /** Cuts the selected text when editing text. */
    public boolean cutSelectedText() {
      return getBrailleImeForBrailleDisplay().cutSelectedText();
    }

    /** Copies the selected text when editing text. */
    public boolean copySelectedText() {
      return getBrailleImeForBrailleDisplay().copySelectedText();
    }

    /** Pastes the selected text when editing text. */
    public boolean pasteSelectedText() {
      return getBrailleImeForBrailleDisplay().pasteSelectedText();
    }

    /** Selects all the text when editing text. */
    public boolean selectAllText() {
      return getBrailleImeForBrailleDisplay().selectAllText();
    }

    /**
     * Selects the text from current cursor position to the start of text field when editing text.
     */
    public boolean selectCurrentToStart() {
      return getBrailleImeForBrailleDisplay().selectCurrentToStart();
    }

    /** Selects the text from current cursor position to the ned of text field when editing text. */
    public boolean selectCurrentToEnd() {
      return getBrailleImeForBrailleDisplay().selectCurrentToEnd();
    }

    /** Selects the previous character from the cursor when editing text. */
    public boolean selectPreviousCharacter() {
      return getBrailleImeForBrailleDisplay().selectPreviousCharacter();
    }

    /** Selects the next character from the cursor when editing text. */
    public boolean selectNextCharacter() {
      return getBrailleImeForBrailleDisplay().selectNextCharacter();
    }

    /** Selects the previous word from the cursor when editing text. */
    public boolean selectPreviousWord() {
      return getBrailleImeForBrailleDisplay().selectPreviousWord();
    }

    /** Selects the next word from the cursor when editing text. */
    public boolean selectNextWord() {
      return getBrailleImeForBrailleDisplay().selectNextWord();
    }

    /** Selects the previous line from the cursor when editing text. */
    public boolean selectPreviousLine() {
      return getBrailleImeForBrailleDisplay().selectPreviousLine();
    }

    /** Selects the next line from the cursor when editing text. */
    public boolean selectNextLine() {
      return getBrailleImeForBrailleDisplay().selectNextLine();
    }

    /** Switches to next input method. */
    public boolean switchToNextInputMethod() {
      if (isSomeBrailleCommandUnavailableDialogShowing()) {
        someBrailleCommandUnavailableDialog.dismiss();
      }
      if (isBrailleKeyboardActivated()) {
        // Don't call the other one because we still need to well-handle holding.
        return getBrailleImeForBrailleDisplay().switchToNextInputMethod();
      } else if (isOnscreenKeyboardActive()) {
        // Don't switch keyboards when closed. It's the system norm.
        return talkBackForBrailleDisplay.switchToNextInputMethod();
      }
      return false;
    }

    /** Tells BrailleIme to update the result on a braille display. */
    public void triggerUpdateDisplay() {
      getBrailleImeForBrailleDisplay().updateResultForDisplay();
    }

    /** Performs enter key action. */
    public boolean performEnterKeyAction() {
      return getBrailleImeForBrailleDisplay().commitHoldingsAndPerformEnterKeyAction();
    }

    /** Notifies accessibility focus cleared. */
    public void onFocusCleared() {
      if (isBrailleKeyboardActivated()) {
        getBrailleImeForBrailleDisplay().commitHoldings();
      }
    }
  }

  /** Behavior for modes: node text. */
  public class BehaviorNodeText {

    /** Returns whether a label can be added for this {@param AccessibilityNodeInfoCompat}. */
    public boolean supportsLabel(AccessibilityNodeInfoCompat node) {
      return talkBackForBrailleDisplay.supportsLabel(node);
    }

    /** Gets a defined custom label for this {@param AccessibilityNodeInfoCompat}. */
    public CharSequence getCustomLabelText(AccessibilityNodeInfoCompat node) {
      return talkBackForBrailleDisplay.getCustomLabelText(node);
    }

    /** Returns whether {@param AccessibilityNodeInfoCompat node} needs a label. */
    public boolean needsLabel(AccessibilityNodeInfoCompat node) {
      return talkBackForBrailleDisplay.needsLabel(node);
    }

    /**
     * Shows custom label dialog for the {@param AccessibilityNodeInfoCompat node} to add or edit a
     * label.
     */
    public boolean showLabelDialog(CustomLabelAction action, AccessibilityNodeInfoCompat node) {
      return talkBackForBrailleDisplay.showLabelDialog(action, node);
    }
  }

  /** Behavior for modes: screen reader. */
  public class BehaviorScreenReader {
    public boolean performAction(ScreenReaderAction action, Object... args) {
      return talkBackForBrailleDisplay.performAction(action, args);
    }

    public boolean getVoiceFeedbackEnabled() {
      return talkBackForBrailleDisplay.getVoiceFeedbackEnabled();
    }

    public AccessibilityService getAccessibilityService() {
      return talkBackForBrailleDisplay.getAccessibilityService();
    }
  }

  /** Behavior for modes: navigation. */
  public class BehaviorNavigation {
    /** Pans up to previous line. */
    public boolean panUp() {
      return handlePanUp();
    }

    /** Pans down to next line when auto scroll is enabled. */
    public boolean panDownWhenAutoScrollEnabled() {
      return handlePanDown(/* isAutoScrollEnable= */ true);
    }

    /** Pans down to next line when auto scroll is disabled. */
    public boolean panDownWhenAutoScrollDisabled() {
      return handlePanDown(/* isAutoScrollEnable= */ false);
    }

    private boolean handlePanUp() {
      if (cellsContentManager.panUp()) {
        return true;
      }
      return talkBackForBrailleDisplay.performAction(ScreenReaderAction.PREVIOUS_ITEM);
    }

    private boolean handlePanDown(boolean isAutoScrollEnable) {
      if (cellsContentManager.panDown()) {
        return true;
      }
      if (isAutoScrollEnable && isOnscreenKeyboardActive()) {
        return false;
      }
      return talkBackForBrailleDisplay.performAction(ScreenReaderAction.NEXT_ITEM);
    }
  }

  /** Behavior for modes: displayer. */
  public class BehaviorDisplayer {
    public boolean isBrailleDisplayConnected() {
      return getBrailleDisplayForBrailleIme().isBrailleDisplayConnectedAndNotSuspended();
    }

    public BrailleDisplayProperties getDeviceProperties() {
      return displayer.getDeviceProperties();
    }

    public int getMaxDisplayCells() {
      return displayer.getDeviceProperties().getNumTextCells();
    }

    public int getCurrentShowContentLength() {
      return cellsContentManager.getCurrentShowContentLength();
    }

    public void addOnDisplayContentChangeListener(OnDisplayContentChangeListener listener) {
      cellsContentManager.addOnDisplayContentChangeListener(listener);
    }

    public void removeOnDisplayContentChangeListener(OnDisplayContentChangeListener listener) {
      cellsContentManager.removeOnDisplayContentChangeListener(listener);
    }
  }

  @VisibleForTesting
  void testing_setBehaviorIme(BehaviorIme behaviorIme) {
    this.behaviorIme = behaviorIme;
  }

  @VisibleForTesting
  CellsContentManager testing_getCellsContentManager() {
    return cellsContentManager;
  }

  @VisibleForTesting
  void testing_setCellsContentManager(CellsContentManager cellsContentManager) {
    this.cellsContentManager = cellsContentManager;
  }
}
