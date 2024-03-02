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
import com.google.android.accessibility.talkback.actor.DirectionNavigationActor;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.selector.SelectorController;
import com.google.android.accessibility.talkback.selector.SelectorController.AnnounceType;
import com.google.android.accessibility.talkback.selector.SelectorController.Setting;
import com.google.android.accessibility.talkback.utils.VerbosityPreferences;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.KeyboardUtils;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.input.TextEventFilter.KeyboardEchoType;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Implements TalkBack functionalities exposed to BrailleIme. */
public class TalkBackForBrailleImeImpl implements TalkBackForBrailleIme {
  private final Pipeline.FeedbackReturner feedbackReturner;
  private final TalkBackService service;
  private final FocusFinder focusFinder;
  private final DimScreenActor dimScreenController;
  private final DirectionNavigationActor.StateReader directionNavigationActorStateReader;
  private final ProximitySensorListener proximitySensorListener;
  private final TalkBackPrivateMethodProvider talkBackPrivateMethodProvider;
  private final ScreenReaderActionPerformer screenReaderActionPerformer;
  private final SelectorController selectorController;
  private final SharedPreferences prefs;

  /** A reference to the active Braille IME if any. */
  private @Nullable BrailleImeForTalkBack brailleImeForTalkBack;

  private static final ImmutableSet<Setting> VALID_GRANULARITIES =
      ImmutableSet.of(
          GRANULARITY_CHARACTERS,
          GRANULARITY_WORDS,
          GRANULARITY_LINES,
          GRANULARITY_PARAGRAPHS,
          GRANULARITY_TYPO);

  /** Provides functionality of private methods. */
  public interface TalkBackPrivateMethodProvider {
    void requestTouchExploration(boolean enabled);

    GlobalVariables getGlobalVariables();
  }

  public TalkBackForBrailleImeImpl(
      TalkBackService service,
      Pipeline.FeedbackReturner feedbackReturner,
      FocusFinder focusFinder,
      DimScreenActor dimScreenController,
      DirectionNavigationActor.StateReader directionNavigationActorStateReader,
      ProximitySensorListener proximitySensorListener,
      TalkBackPrivateMethodProvider talkBackPrivateMethodProvider,
      ScreenReaderActionPerformer talkBackActionPerformer,
      SelectorController selectorController) {
    this.prefs = SharedPreferencesUtils.getSharedPreferences(service);
    this.feedbackReturner = feedbackReturner;
    this.service = service;
    this.focusFinder = focusFinder;
    this.dimScreenController = dimScreenController;
    this.directionNavigationActorStateReader = directionNavigationActorStateReader;
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
  public void speak(CharSequence textToSpeak, int delayMs, SpeakOptions speakOptions) {
    // TODO: For uses cases where the timer is meant to re-schedule text, we
    // should create a centralized repeat-feedback feature, and have BrailleIme use that.
    feedbackReturner.returnFeedback(
        Performance.EVENT_ID_UNTRACKED,
        Feedback.speech(textToSpeak, speakOptions).setDelayMs(delayMs));
  }

  @Override
  public void interruptSpeak() {
    service.interruptAllFeedback(false);
  }

  @Override
  public void playSound(int resId, int delayMs) {
    feedbackReturner.returnFeedback(
        Performance.EVENT_ID_UNTRACKED, Feedback.sound(resId).setDelayMs(delayMs));
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
    return granularity == GRANULARITY_CHARACTERS || hasValidGranularityUnavailable();
  }

  @Override
  public boolean isCurrentGranularityTypoCorrection() {
    return SelectorController.getCurrentSetting(service) == GRANULARITY_TYPO
        && !hasValidGranularityUnavailable();
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
    if (hasValidGranularityUnavailable()) {
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

  private boolean hasValidGranularityUnavailable() {
    for (Setting setting : VALID_GRANULARITIES) {
      if (!selectorController.isSettingAvailable(setting)) {
        feedbackReturner.returnFeedback(
            Performance.EVENT_ID_UNTRACKED, Feedback.granularity(CursorGranularity.CHARACTER));
        return true;
      }
    }
    return false;
  }
}
