package com.google.android.accessibility.talkback.braille;

import static com.google.android.accessibility.talkback.Feedback.PassThroughMode.Action.LOCK_PASS_THROUGH;
import static com.google.android.accessibility.talkback.selector.SelectorController.Setting.GRANULARITY_CHARACTERS;
import static com.google.android.accessibility.talkback.selector.SelectorController.Setting.GRANULARITY_LINES;
import static com.google.android.accessibility.talkback.selector.SelectorController.Setting.GRANULARITY_PARAGRAPHS;
import static com.google.android.accessibility.talkback.selector.SelectorController.Setting.GRANULARITY_TYPO;
import static com.google.android.accessibility.talkback.selector.SelectorController.Setting.GRANULARITY_WORDS;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.input.TextEventFilter.PREF_ECHO_CHARACTERS;
import static com.google.android.accessibility.utils.input.TextEventFilter.PREF_ECHO_CHARACTERS_AND_WORDS;
import static com.google.android.accessibility.utils.monitor.InputModeTracker.INPUT_MODE_BRAILLE_KEYBOARD;

import android.accessibilityservice.AccessibilityService.SoftKeyboardController;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Region;
import android.view.WindowManager;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.braille.interfaces.BrailleImeForTalkBack;
import com.google.android.accessibility.braille.interfaces.ScreenReaderActionPerformer;
import com.google.android.accessibility.braille.interfaces.ScreenReaderActionPerformer.ScreenReaderAction;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleIme;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.TalkBackService.ProximitySensorListener;
import com.google.android.accessibility.talkback.actor.DimScreenActor;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.selector.SelectorController;
import com.google.android.accessibility.talkback.selector.SelectorController.AnnounceType;
import com.google.android.accessibility.talkback.selector.SelectorController.Setting;
import com.google.android.accessibility.talkback.utils.VerbosityPreferences;
import com.google.android.accessibility.utils.ArrayUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.KeyboardUtils;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.input.TextEventFilter.KeyboardEchoType;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Implements TalkBack functionalities exposed to BrailleIme. */
public class TalkBackForBrailleImeImpl implements TalkBackForBrailleIme {
  private final Pipeline.FeedbackReturner feedbackReturner;
  private final TalkBackService service;
  private final DimScreenActor dimScreenController;
  private final ProximitySensorListener proximitySensorListener;
  private final TalkBackPrivateMethodProvider talkBackPrivateMethodProvider;
  private final ScreenReaderActionPerformer screenReaderActionPerformer;
  private final SelectorController selectorController;
  private final SharedPreferences prefs;

  /** A reference to the active Braille IME if any. */
  private @Nullable BrailleImeForTalkBack brailleImeForTalkBack;

  @VisibleForTesting
  static final Setting[] VALID_CURSOR_GRANULARITIES =
      new Setting[] {
        GRANULARITY_CHARACTERS, GRANULARITY_WORDS, GRANULARITY_LINES, GRANULARITY_PARAGRAPHS
      };

  @VisibleForTesting
  static final Setting[] VALID_NON_CURSOR_GRANULARITIES = new Setting[] {GRANULARITY_TYPO};

  @VisibleForTesting
  static final Set<Setting> VALID_GRANULARITIES =
      Arrays.stream(ArrayUtils.concat(VALID_CURSOR_GRANULARITIES, VALID_NON_CURSOR_GRANULARITIES))
          .collect(Collectors.toUnmodifiableSet());

  /** Provides functionality of private methods. */
  public interface TalkBackPrivateMethodProvider {
    void requestTouchExploration(boolean enabled);

    GlobalVariables getGlobalVariables();
  }

  public TalkBackForBrailleImeImpl(
      TalkBackService service,
      Pipeline.FeedbackReturner feedbackReturner,
      DimScreenActor dimScreenController,
      ProximitySensorListener proximitySensorListener,
      TalkBackPrivateMethodProvider talkBackPrivateMethodProvider,
      ScreenReaderActionPerformer talkBackActionPerformer,
      SelectorController selectorController) {
    this.prefs = SharedPreferencesUtils.getSharedPreferences(service);
    this.feedbackReturner = feedbackReturner;
    this.service = service;
    this.dimScreenController = dimScreenController;
    this.proximitySensorListener = proximitySensorListener;
    this.talkBackPrivateMethodProvider = talkBackPrivateMethodProvider;
    this.screenReaderActionPerformer = talkBackActionPerformer;
    this.selectorController = selectorController;
  }

  @Override
  public boolean performAction(ScreenReaderAction action, Object... args) {
    return screenReaderActionPerformer.performAction(action, INPUT_MODE_BRAILLE_KEYBOARD, args);
  }

  @Override
  public void onBrailleImeActivated(
      boolean disableEbt, boolean usePassThrough, Region passThroughRegion) {
    if (usePassThrough) {
      feedbackReturner.returnFeedback(
          Performance.EVENT_ID_UNTRACKED,
          Feedback.passThroughMode(LOCK_PASS_THROUGH, passThroughRegion));
    } else {
      service.getInputModeTracker().setInputMode(INPUT_MODE_BRAILLE_KEYBOARD);
      talkBackPrivateMethodProvider.requestTouchExploration(!disableEbt);
    }
  }

  @Override
  public void onBrailleImeInactivated(boolean usePassThrough, boolean brailleImeActive) {
    if (getServiceStatus() != ServiceStatus.ON) {
      return;
    }
    if (usePassThrough) {
      feedbackReturner.returnFeedback(
          Performance.EVENT_ID_UNTRACKED, Feedback.passThroughMode(LOCK_PASS_THROUGH, null));
    } else {
      boolean ebtEnabled =
          SharedPreferencesUtils.getBooleanPref(
              prefs,
              service.getResources(),
              R.string.pref_explore_by_touch_key,
              R.bool.pref_explore_by_touch_default);
      if (ebtEnabled) {
        talkBackPrivateMethodProvider.requestTouchExploration(true);
      }
    }
  }

  @Override
  public boolean setInputMethodEnabled() {
    if (FeatureSupport.supportEnableDisableIme() && TalkBackService.getInstance() != null) {
      return service
              .getSoftKeyboardController()
              .setInputMethodEnabled(
                  KeyboardUtils.getImeId(TalkBackService.getInstance(), service.getPackageName()),
                  /* enabled= */ true)
          == SoftKeyboardController.ENABLE_IME_SUCCESS;
    }
    return false;
  }

  @Override
  public WindowManager getWindowManager() {
    return (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
  }

  @Override
  public ServiceStatus getServiceStatus() {
    return TalkBackService.isServiceActive() ? ServiceStatus.ON : ServiceStatus.OFF;
  }

  @Override
  public void interruptSpeak() {
    service.interruptAllFeedback(false);
  }

  @Override
  public void disableSilenceOnProximity() {
    proximitySensorListener.setSilenceOnProximity(false);
  }

  @Override
  public void restoreSilenceOnProximity() {
    proximitySensorListener.reloadSilenceOnProximity();
  }

  @Override
  public boolean isContextMenuExist() {
    return service.getMenuManager().isMenuExist();
  }

  @Override
  public boolean isVibrationFeedbackEnabled() {
    return FeatureSupport.isVibratorSupported(service)
        && SharedPreferencesUtils.getBooleanPref(
            prefs,
            service.getResources(),
            R.string.pref_vibration_key,
            R.bool.pref_vibration_default);
  }

  @Override
  public boolean shouldAnnounceCharacterForOnScreenKeyboard() {
    @KeyboardEchoType
    int echoType = VerbosityPreferences.readOnScreenKeyboardEcho(prefs, service.getResources());
    return echoType == PREF_ECHO_CHARACTERS || echoType == PREF_ECHO_CHARACTERS_AND_WORDS;
  }

  @Override
  public boolean shouldAnnounceCharacterForPhysicalKeyboard() {
    @KeyboardEchoType
    int echoType = VerbosityPreferences.readPhysicalKeyboardEcho(prefs, service.getResources());
    return echoType == PREF_ECHO_CHARACTERS || echoType == PREF_ECHO_CHARACTERS_AND_WORDS;
  }

  @Override
  public boolean shouldSpeakPassword() {
    return talkBackPrivateMethodProvider.getGlobalVariables().shouldSpeakPasswords();
  }

  @Override
  public boolean shouldUseCharacterGranularity() {
    Setting granularity = SelectorController.getCurrentSetting(service);
    return granularity == GRANULARITY_CHARACTERS || !isSwitchGranularityValid();
  }

  @Override
  public boolean isCurrentGranularityTypoCorrection() {
    return SelectorController.getCurrentSetting(service) == GRANULARITY_TYPO
        && isSwitchGranularityValid();
  }

  @Override
  public boolean moveCursorForwardByDefault() {
    if (VALID_GRANULARITIES.contains(SelectorController.getCurrentSetting(service))) {
      performMovingCursor(/* isForward */ true);
      return true;
    }
    return false;
  }

  @Override
  public boolean moveCursorBackwardByDefault() {
    if (VALID_GRANULARITIES.contains(SelectorController.getCurrentSetting(service))) {
      performMovingCursor(/* isForward */ false);
      return true;
    }
    return false;
  }

  @Override
  public boolean isHideScreenMode() {
    return dimScreenController.isDimmingEnabled();
  }

  @Override
  public BrailleImeForTalkBackProvider getBrailleImeForTalkBackProvider() {
    return () -> brailleImeForTalkBack;
  }

  @Override
  public void setBrailleImeForTalkBack(BrailleImeForTalkBack brailleImeForTalkBack) {
    this.brailleImeForTalkBack = brailleImeForTalkBack;
  }

  @Override
  public FocusFinder createFocusFinder() {
    return new FocusFinder(service);
  }

  @Override
  public boolean switchToNextEditingGranularity() {
    return switchGranularity(/* isNext */ true);
  }

  @Override
  public boolean switchToPreviousEditingGranularity() {
    return switchGranularity(/* isNext */ false);
  }

  @Override
  public void resetGranularity() {
    if (SelectorController.getCurrentSetting(service) == GRANULARITY_CHARACTERS) {
      return;
    }
    boolean unused = selectorController.selectSettingSilently(GRANULARITY_CHARACTERS);
  }

  private void performMovingCursor(boolean isForward) {
    selectorController.adjustSelectedSetting(EVENT_ID_UNTRACKED, isForward);
  }

  private boolean switchGranularity(boolean isNext) {
    if (!isSwitchGranularityValid()) {
      return false;
    }
    Setting current = SelectorController.getCurrentSetting(service);
    do {
      selectorController.selectPreviousOrNextSettingWithoutOverlay(
          EVENT_ID_UNTRACKED, AnnounceType.DESCRIPTION, isNext);
    } while (!VALID_GRANULARITIES.contains(SelectorController.getCurrentSetting(service))
        && current != SelectorController.getCurrentSetting(service));
    return true;
  }

  private boolean isSwitchGranularityValid() {
    int validCursorGranularity = 0;
    int validNonCursorGranularity = 0;
    for (Setting setting : VALID_CURSOR_GRANULARITIES) {
      if (selectorController.isSettingAvailable(setting)) {
        validCursorGranularity++;
      }
    }
    for (Setting setting : VALID_NON_CURSOR_GRANULARITIES) {
      if (selectorController.isSettingAvailable(setting)) {
        validNonCursorGranularity++;
      }
    }
    // At least 2 available granularities and at least one cursor granularity to prevent stuck in
    // some mode such as typo correction.
    return validCursorGranularity >= 1 && (validNonCursorGranularity + validCursorGranularity) >= 2;
  }
}
