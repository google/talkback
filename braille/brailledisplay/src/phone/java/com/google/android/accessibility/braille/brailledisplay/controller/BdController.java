package com.google.android.accessibility.braille.brailledisplay.controller;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import androidx.appcompat.app.AlertDialog;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayTalkBackSpeaker;
import com.google.android.accessibility.braille.brailledisplay.OverlayDisplay;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.brailledisplay.analytics.BrailleDisplayAnalytics;
import com.google.android.accessibility.braille.brailledisplay.controller.CellsContentManager.Cursor;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils.SupportedCommand;
import com.google.android.accessibility.braille.brailledisplay.platform.Controller;
import com.google.android.accessibility.braille.brailledisplay.platform.Displayer;
import com.google.android.accessibility.braille.brailledisplay.platform.PersistentStorage;
import com.google.android.accessibility.braille.brailledisplay.settings.BrailleDisplaySettingsActivity;
import com.google.android.accessibility.braille.brailledisplay.settings.KeyBindingsActivity;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages.Code;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForBrailleIme;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForBrailleIme.ResultForDisplay;
import com.google.android.accessibility.braille.interfaces.BrailleImeForBrailleDisplay;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleDisplay;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleDisplay.CustomLabelAction;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleDisplay.ScreenReaderAction;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils.Constants;
import com.google.android.accessibility.utils.BuildConfig;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.MaterialComponentUtils;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Holds the business logic for the braille display feature. */
public class BdController implements Controller {
  private static final String TAG = "BdController";
  private static final int ANNOUNCE_DELAY_MS =
      200; // Delay, so that it will not be replaced by edit box focus announcement.
  private static final int LOG_LANGUAGE_CHANGE_DELAY_MS =
      10000; // After the lowest TTS speed announcement ends.
  private static final int INPUT = 1;
  private static final int OUTPUT = 2;
  private final Context context;
  private final TalkBackForBrailleDisplay talkBackForBrailleDisplay;
  private final FeedbackManager feedbackManager;
  private final OverlayDisplay overlayDisplay;
  private final ImeHelper imeHelper;
  private final ModeSwitcher modeSwitcher;
  private final BrailleMenuNavigationMode brailleMenuNavigationMode;
  private final ImeNavigationMode imeNavigationMode;
  // While in 'suspended' mode, we ignore any input from the display and we render a static message
  // on the braille display; then when suspended mode gets exited, we return to normal operation.
  private final AtomicBoolean suspended = new AtomicBoolean();
  private TranslatorManager translatorManager;
  private CellsContentManager cellsContentManager;
  private Displayer displayer;
  private BehaviorIme behaviorIme;
  private Handler loggingHandler;

  public BdController(Context context, TalkBackForBrailleDisplay talkBackForBrailleDisplay) {
    this.context = context;
    this.talkBackForBrailleDisplay = talkBackForBrailleDisplay;

    feedbackManager = new FeedbackManager(talkBackForBrailleDisplay.getFeedBackController());
    translatorManager = new TranslatorManager(context);
    cellsContentManager =
        new CellsContentManager(
            context,
            new ImeStatusProvider(),
            translatorManager,
            new CellsContentManagerPanOverflowListener(),
            new CellsContentManagerMappedInputListnener(),
            new CellsContentManagerDotDisplayer());

    BehaviorNodeText behaviorNodeText = new BehaviorNodeText();
    BehaviorScreenReaderAction behaviorScreenReaderAction = new BehaviorScreenReaderAction();
    BehaviorFocus behaviorFocus = new BehaviorFocus();
    behaviorIme = new BehaviorIme(feedbackManager, cellsContentManager);
    BehaviorLabel behaviorLabel = new BehaviorLabel();
    NodeBrailler nodeBrailler = new NodeBrailler(context, behaviorNodeText);

    DefaultNavigationMode defaultNavigationMode =
        new DefaultNavigationMode(
            context,
            cellsContentManager,
            feedbackManager,
            nodeBrailler,
            behaviorFocus,
            behaviorScreenReaderAction);

    imeNavigationMode = new ImeNavigationMode(defaultNavigationMode, behaviorFocus, behaviorIme);

    modeSwitcher =
        new ModeSwitcher(
            imeNavigationMode,
            new TreeDebugNavigationMode(cellsContentManager, feedbackManager, behaviorFocus));

    brailleMenuNavigationMode =
        new BrailleMenuNavigationMode(
            feedbackManager,
            new BrailleMenuNavigationModeCallback(),
            behaviorFocus,
            behaviorLabel,
            behaviorNodeText);

    imeHelper = new ImeHelper(context);
    overlayDisplay = new OverlayDisplay(context, new OverlayDisplayCallback());
    loggingHandler = new LoggingHandler(context);
  }

  @Override
  public void onConnectStarted() {
    BrailleDisplayAnalytics.getInstance(context).logStartToEstablishBluetoothConnection();
  }

  @Override
  public void onConnected() {
    feedbackManager.emitFeedback(FeedbackManager.TYPE_DISPLAY_CONNECTED);
    BrailleDisplayAnalytics.getInstance(context).logStartToConnectToBrailleDisplay();
  }

  @Override
  public void onDisplayerReady(Displayer displayer) {
    this.displayer = displayer;
    overlayDisplay.start(displayer.getDeviceProperties().getNumTextCells());
    cellsContentManager.start(displayer.getDeviceProperties().getNumTextCells());
    modeSwitcher.onActivate();
    if (isBrailleKeyboardActivated()) {
      getBrailleImeForBrailleDisplay().onBrailleDisplayConnected();
    }
    logSessionMetrics();
  }

  private void logSessionMetrics() {
    BrailleDisplayAnalytics.getInstance(context)
        .logStartedEvent(
            displayer.getDeviceProperties().getDeviceName(),
            BrailleUserPreferences.readCurrentActiveInputCodeAndCorrect(context),
            BrailleUserPreferences.readCurrentActiveOutputCodeAndCorrect(context));
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    if (BrailleDisplayLog.DEBUG) {
      BrailleDisplayLog.v(TAG, "Event: " + accessibilityEvent.toString());
      BrailleDisplayLog.v(TAG, "Node:  " + accessibilityEvent.getSource());
    }
    if (modeSwitcher != null) {
      modeSwitcher.onAccessibilityEvent(accessibilityEvent);
    }
  }

  @Override
  public void onBrailleInputEvent(Displayer displayer, BrailleInputEvent brailleInputEvent) {
    if (shouldResumeBrailleDisplay(brailleInputEvent)) {
      BrailleDisplayAnalytics.getInstance(context).logChangeTypingMode(/* toPhysical= */ false);
      suspended.set(false);
      BrailleDisplayTalkBackSpeaker.getInstance()
          .speakInterrupt(
              context.getString(R.string.bd_switch_to_braille_hardware_message), ANNOUNCE_DELAY_MS);
      // The input is treated as resuming braille display.
      getBrailleImeForBrailleDisplay().onBrailleDisplayConnected();
      putAccessibilityFocusOnInputFocus();
    } else {
      cellsContentManager.onBrailleInputEvent(brailleInputEvent);
    }
  }

  @Override
  public void onDisconnected() {
    feedbackManager.emitFeedback(FeedbackManager.TYPE_DISPLAY_DISCONNECTED);
    modeSwitcher.onDeactivate();
    overlayDisplay.shutdown();
    cellsContentManager.shutdown();
    displayer = null;
    talkBackForBrailleDisplay.setVoiceFeedback(true);
    if (isBrailleKeyboardActivated()) {
      getBrailleImeForBrailleDisplay().onBrailleDisplayDisconnected();
    }
  }

  @Override
  public void onDestroy() {
    overlayDisplay.shutdown();
    if (cellsContentManager != null) {
      cellsContentManager.shutdown();
      cellsContentManager = null;
    }
    if (translatorManager != null) {
      translatorManager.shutdown();
      translatorManager = null;
    }
  }

  /** Returns BrailleDisplayForBrailleIme . */
  public BrailleDisplayForBrailleIme getBrailleDisplayForBrailleIme() {
    return brailleDisplayForBrailleIme;
  }

  private boolean isBrailleKeyboardActivated() {
    return getBrailleImeForBrailleDisplay() != null;
  }

  private BrailleImeForBrailleDisplay getBrailleImeForBrailleDisplay() {
    return talkBackForBrailleDisplay.getBrailleImeForBrailleDisplay();
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

  /** Returns whether to resume braille display. */
  private boolean shouldResumeBrailleDisplay(BrailleInputEvent brailleInputEvent) {
    // In case braille display is too small to show BK in use in a row.
    return brailleInputEvent.getCommand() != BrailleInputEvent.CMD_NAV_PAN_UP
        && brailleInputEvent.getCommand() != BrailleInputEvent.CMD_NAV_PAN_DOWN
        && isBrailleKeyboardActivated()
        && suspended.get();
  }

  private final BrailleDisplayForBrailleIme brailleDisplayForBrailleIme =
      new BrailleDisplayForBrailleIme() {
        @Override
        public void showOnDisplay(ResultForDisplay resultForDisplay) {
          if (!isBrailleDisplayConnected()) {
            return;
          }
          updateDisplay(resultForDisplay);
        }

        @Override
        public boolean isBrailleDisplayConnectedAndNotSuspended() {
          if (suspended.get()) {
            return false;
          }
          return isBrailleDisplayConnected();
        }

        private boolean isBrailleDisplayConnected() {
          return displayer != null && displayer.isDisplayReady();
        }

        @Override
        public void suspendInFavorOfBrailleKeyboard() {
          BrailleDisplayAnalytics.getInstance(context).logChangeTypingMode(/* toPhysical= */ true);
          suspended.set(true);
        }
      };

  private class CellsContentManagerPanOverflowListener
      implements CellsContentManager.PanOverflowListener {
    @Override
    public void onPanLeftOverflow() {
      if (!modeSwitcher.onPanLeftOverflow()) {
        feedbackManager.emitFeedback(FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
      }
    }

    @Override
    public void onPanRightOverflow() {
      if (!modeSwitcher.onPanRightOverflow()) {
        feedbackManager.emitFeedback(FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
      }
    }
  }

  private class CellsContentManagerMappedInputListnener
      implements CellsContentManager.MappedInputListener {

    // Dot combination for switching navigation mode. Currently this is only used for debugging.
    private final BrailleCharacter switchNavigationModeDots = new BrailleCharacter("78");

    @Override
    public void onMappedInputEvent(BrailleInputEvent event) {
      if (modeSwitcher == null) {
        return;
      }
      // Global commands can't be overridden.
      if (handleGlobalCommands(event)) {
        return;
      }
      if (BuildConfig.DEBUG
          && event.getCommand() == BrailleInputEvent.CMD_BRAILLE_KEY
          && event.getArgument() == switchNavigationModeDots.toInt()) {
        modeSwitcher.switchMode();
        return;
      }
      if (modeSwitcher.onMappedInputEvent(event)) {
        return;
      }
      if (imeHelper.onInputEvent(event)) {
        return;
      }
      feedbackManager.emitFeedback(FeedbackManager.TYPE_UNKNOWN_COMMAND);
    }

    private boolean handleGlobalCommands(BrailleInputEvent event) {
      boolean success = false;
      for (SupportedCommand cmd : BrailleKeyBindingUtils.getSupportedCommands(context)) {
        if (cmd.getCommand() == event.getCommand()) {
          BrailleDisplayAnalytics.getInstance(context).logBrailleCommand(event.getCommand());
          break;
        }
      }
      switch (event.getCommand()) {
        case BrailleInputEvent.CMD_GLOBAL_HOME:
          success = talkBackForBrailleDisplay.performAction(ScreenReaderAction.GLOBAL_HOME);
          break;
        case BrailleInputEvent.CMD_GLOBAL_BACK:
          success = talkBackForBrailleDisplay.performAction(ScreenReaderAction.GLOBAL_BACK);
          break;
        case BrailleInputEvent.CMD_GLOBAL_RECENTS:
          success = talkBackForBrailleDisplay.performAction(ScreenReaderAction.GLOBAL_RECENTS);
          break;
        case BrailleInputEvent.CMD_GLOBAL_NOTIFICATIONS:
          success =
              talkBackForBrailleDisplay.performAction(ScreenReaderAction.GLOBAL_NOTIFICATIONS);
          break;
        case BrailleInputEvent.CMD_HELP:
          success = runHelp();
          break;
        case BrailleInputEvent.CMD_QUICK_SETTINGS:
          success =
              talkBackForBrailleDisplay.performAction(ScreenReaderAction.GLOBAL_QUICK_SETTINGS);
          break;
        case BrailleInputEvent.CMD_ALL_APPS:
          success = talkBackForBrailleDisplay.performAction(ScreenReaderAction.GLOBAL_ALL_APPS);
          break;
        case BrailleInputEvent.CMD_SWITCH_TO_NEXT_OUTPUT_LANGUAGE:
          if (BrailleUserPreferences.readAvailablePreferredCodes(context).size() > 1) {
            Code nextCode = BrailleUserPreferences.getNextOutputCode(context);
            BrailleUserPreferences.writeCurrentActiveOutputCode(context, nextCode);
            // Delay logging because user might go through a long list of languages before reaching
            // his desired language.
            loggingHandler.removeMessages(OUTPUT);
            loggingHandler.sendMessageDelayed(
                loggingHandler.obtainMessage(OUTPUT, nextCode), LOG_LANGUAGE_CHANGE_DELAY_MS);
            BrailleDisplayTalkBackSpeaker.getInstance()
                .speakInterrupt(
                    context.getString(
                        R.string.bd_switch_reading_language_announcement,
                        nextCode.getUserFacingName(context.getResources())));
            success = true;
          }
          break;
        case BrailleInputEvent.CMD_SWITCH_TO_NEXT_INPUT_LANGUAGE:
          if (BrailleUserPreferences.readAvailablePreferredCodes(context).size() > 1) {
            Code nextCode = BrailleUserPreferences.getNextInputCode(context);
            BrailleUserPreferences.writeCurrentActiveInputCode(context, nextCode);
            // Delay logging because user might go through a long list of languages before reaching
            // his desired language.
            loggingHandler.removeMessages(INPUT);
            loggingHandler.sendMessageDelayed(
                loggingHandler.obtainMessage(INPUT, nextCode), LOG_LANGUAGE_CHANGE_DELAY_MS);
            BrailleDisplayTalkBackSpeaker.getInstance()
                .speakInterrupt(
                    context.getString(
                        R.string.bd_switch_typing_language_announcement,
                        nextCode.getUserFacingName(context.getResources())));
            success = true;
          }
          break;
        case BrailleInputEvent.CMD_TOGGLE_VOICE_FEEDBACK:
          success =
              talkBackForBrailleDisplay.performAction(ScreenReaderAction.TOGGLE_VOICE_FEEDBACK);
          break;
        case BrailleInputEvent.CMD_BRAILLE_DISPLAY_SETTINGS:
          {
            Intent intent = new Intent(context, BrailleDisplaySettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            success = true;
            break;
          }
        case BrailleInputEvent.CMD_TALKBACK_SETTINGS:
          {
            Intent intent = new Intent();
            intent.setComponent(Constants.SETTINGS_ACTIVITY);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            success = true;
            break;
          }
        case BrailleInputEvent.CMD_TURN_OFF_BRAILLE_DISPLAY:
          AlertDialog dialog =
              MaterialComponentUtils.alertDialogBuilder(context)
                  .setTitle(R.string.bd_turn_off_bd_confirm_dialog_title)
                  .setMessage(R.string.bd_turn_off_bd_confirm_dialog_message)
                  .setPositiveButton(
                      R.string.bd_turn_off_bd_confirm_dialog_positive_button,
                      (dialog1, which) ->
                          PersistentStorage.setConnectionEnabledByUser(context, false))
                  .setNegativeButton(android.R.string.cancel, null)
                  .create();
          dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
          dialog.show();
          success = true;
          break;
        case BrailleInputEvent.CMD_NEXT_READING_CONTROL:
          success =
              talkBackForBrailleDisplay.performAction(ScreenReaderAction.NEXT_READING_CONTROL);
          break;
        case BrailleInputEvent.CMD_PREVIOUS_READING_CONTROL:
          success =
              talkBackForBrailleDisplay.performAction(ScreenReaderAction.PREVIOUS_READING_CONTROL);
          break;
        case BrailleInputEvent.CMD_TOGGLE_SCREEN_SEARCH:
          success = talkBackForBrailleDisplay.performAction(ScreenReaderAction.SCREEN_SEARCH);
          break;
        case BrailleInputEvent.CMD_EDIT_CUSTOM_LABEL:
          if (brailleMenuNavigationMode.isActive()) {
            modeSwitcher.overrideMode(null);
          } else {
            modeSwitcher.overrideMode(brailleMenuNavigationMode);
          }
          success = true;
          break;
        case BrailleInputEvent.CMD_OPEN_TALKBACK_MENU:
          success = talkBackForBrailleDisplay.performAction(ScreenReaderAction.OPEN_TALKBACK_MENU);
          break;
        default:
          return false;
      }
      if (!success) {
        feedbackManager.emitFeedback(FeedbackManager.TYPE_COMMAND_FAILED);
      }
      // Always return true because we own these actions.
      return true;
    }

    private boolean runHelp() {
      Intent intent = new Intent(context, KeyBindingsActivity.class);
      intent.addFlags(
          Intent.FLAG_ACTIVITY_NEW_TASK
              | Intent.FLAG_ACTIVITY_CLEAR_TOP
              | Intent.FLAG_ACTIVITY_SINGLE_TOP);
      intent.putExtra(KeyBindingsActivity.PROPERTY_KEY, displayer.getDeviceProperties());
      context.startActivity(intent);
      return true;
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
        BrailleDisplayAnalytics.getInstance(context).logBrailleInputCodeSetting((Code) msg.obj);
      } else if (msg.what == OUTPUT) {
        BrailleDisplayAnalytics.getInstance(context).logBrailleOutputCodeSetting((Code) msg.obj);
      }
    }
  }

  private class CellsContentManagerDotDisplayer implements CellsContentManager.DotDisplayer {
    @Override
    public void displayDots(byte[] patterns, CharSequence text, int[] brailleToTextPositions) {
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
      cellsContentManager.onBrailleInputEvent(inputEvent);
    }
  }

  private class BrailleMenuNavigationModeCallback implements BrailleMenuNavigationMode.Callback {
    @Override
    public void onMenuClosed() {
      modeSwitcher.overrideMode(null);
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
  }

  /** Behavior for modes: ime. */
  public class BehaviorIme {
    private final FeedbackManager feedbackManager;
    private final CellsContentManager cellsContentManager;

    public BehaviorIme(FeedbackManager feedbackManager, CellsContentManager cellsContentManager) {
      this.feedbackManager = feedbackManager;
      this.cellsContentManager = cellsContentManager;
    }

    /** Whether Braille keyboard is open. */
    public boolean acceptInput() {
      return isBrailleKeyboardActivated();
    }

    /** Whether using braille display as the input channel. */
    public boolean isSuspended() {
      return suspended.get();
    }

    public boolean sendBrailleDots(int dots) {
      BrailleDisplayAnalytics.getInstance(context).logTypingBrailleCharacter(/* count= */ 1);
      byte dotsInByte = (byte) (dots & 0xff);
      putAccessibilityFocusOnInputFocus();
      return feedbackManager.emitOnFailure(
          getBrailleImeForBrailleDisplay().sendBrailleDots(new BrailleCharacter(dotsInByte)),
          FeedbackManager.TYPE_COMMAND_FAILED);
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
      putAccessibilityFocusOnInputFocus();
      return getBrailleImeForBrailleDisplay().deleteBackward();
    }

    public boolean deleteWordBackward() {
      putAccessibilityFocusOnInputFocus();
      return getBrailleImeForBrailleDisplay().deleteWordBackward();
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
      getBrailleImeForBrailleDisplay().commitHoldings();
    }
  }

  /** Behavior for modes: node text. */
  public class BehaviorNodeText {
    public CharSequence getCustomLabelText(AccessibilityNodeInfoCompat node) {
      return talkBackForBrailleDisplay.getCustomLabelText(node);
    }

    public boolean needsLabel(AccessibilityNodeInfoCompat node) {
      return talkBackForBrailleDisplay.needsLabel(node);
    }
  }

  /** Behavior for modes: screen reader action. */
  public class BehaviorScreenReaderAction {
    public boolean performAction(ScreenReaderAction action, Object... args) {
      return talkBackForBrailleDisplay.performAction(action, args);
    }
  }

  /** Behavior for modes: label. */
  public class BehaviorLabel {
    public boolean showLabelDialog(
        CustomLabelAction labelAction, AccessibilityNodeInfoCompat node) {
      return talkBackForBrailleDisplay.showLabelDialog(labelAction, node);
    }

    public boolean needsLabel(AccessibilityNodeInfoCompat node) {
      return talkBackForBrailleDisplay.needsLabel(node);
    }
  }

  @VisibleForTesting
  void testing_setBehaviorIme(BehaviorIme behaviorIme) {
    this.behaviorIme = behaviorIme;
  }
}
