package com.google.android.accessibility.talkback.braille;

import static com.google.android.accessibility.utils.monitor.InputModeTracker.INPUT_MODE_BRAILLE_DISPLAY;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.interfaces.ScreenReaderActionPerformer;
import com.google.android.accessibility.braille.interfaces.ScreenReaderActionPerformer.ScreenReaderAction;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleDisplay;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.labeling.LabelDialogManager;
import com.google.android.accessibility.talkback.labeling.TalkBackLabelManager;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.KeyboardUtils;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.labeling.Label;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.output.SpeechControllerImpl;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Implements TalkBack functionalities exposed to BrailleDisplay. */
public class TalkBackForBrailleDisplayImpl implements TalkBackForBrailleDisplay {
  private TalkBackService service;
  private Pipeline.FeedbackReturner feedbackReturner;
  private SpeechControllerImpl speechController;
  private TalkBackLabelManager labelManager;
  private ScreenReaderActionPerformer screenReaderActionPerformer;

  public TalkBackForBrailleDisplayImpl(
      TalkBackService service,
      Pipeline.FeedbackReturner feedbackReturner,
      ScreenReaderActionPerformer talkBackActionPerformer) {
    this.service = service;
    this.feedbackReturner = feedbackReturner;
    this.speechController = service.getSpeechController();
    this.labelManager = service.getLabelManager();
    this.screenReaderActionPerformer = talkBackActionPerformer;
  }

  @Override
  public AccessibilityService getAccessibilityService() {
    return service;
  }

  @Override
  public boolean performAction(ScreenReaderAction action, Object... args) {
    return screenReaderActionPerformer.performAction(action, INPUT_MODE_BRAILLE_DISPLAY, args);
  }

  @Override
  public boolean setVoiceFeedback(boolean enabled) {
    if (enabled == speechController.isMute()) {
      return performAction(ScreenReaderAction.TOGGLE_VOICE_FEEDBACK);
    }
    return false;
  }

  @Override
  public boolean getVoiceFeedbackEnabled() {
    return !speechController.isMute();
  }

  @Override
  public AccessibilityNodeInfoCompat getAccessibilityFocusNode(boolean fallbackOnRoot) {
    return FocusFinder.getAccessibilityFocusNode(TalkBackService.getInstance(), fallbackOnRoot);
  }

  @Override
  public FocusFinder createFocusFinder() {
    return new FocusFinder(TalkBackService.getInstance());
  }

  @Override
  public FeedbackController getFeedBackController() {
    return service.getFeedbackController();
  }

  @Override
  public boolean showLabelDialog(CustomLabelAction action, AccessibilityNodeInfoCompat node) {
    if (action == CustomLabelAction.ADD_LABEL) {
      return LabelDialogManager.addLabel(
          TalkBackService.getInstance(),
          node.getViewIdResourceName(),
          /* needToRestoreFocus= */ true,
          feedbackReturner);
    } else if (action == CustomLabelAction.EDIT_LABEL) {
      return LabelDialogManager.editLabel(
          TalkBackService.getInstance(),
          labelManager.getLabelForViewIdFromCache(node.getViewIdResourceName()).getId(),
          /* needToRestoreFocus= */ true,
          feedbackReturner);
    }
    return false;
  }

  @Override
  public @Nullable String getCustomLabelText(AccessibilityNodeInfoCompat node) {
    Label label = labelManager.getLabelForViewIdFromCache(node.getViewIdResourceName());
    if (label != null) {
      return label.getText();
    }
    return null;
  }

  @Override
  public boolean needsLabel(AccessibilityNodeInfoCompat node) {
    return labelManager.stateReader().needsLabel(node);
  }

  @Override
  public boolean supportsLabel(AccessibilityNodeInfoCompat node) {
    return labelManager.stateReader().supportsLabel(node);
  }

  @Override
  public void speak(CharSequence textToSpeak, int delayMs, SpeakOptions speakOptions) {
    feedbackReturner.returnFeedback(
        Performance.EVENT_ID_UNTRACKED,
        Feedback.speech(textToSpeak, speakOptions).setDelayMs(delayMs));
  }

  @Override
  public boolean isOnscreenKeyboardActive() {
    return AccessibilityServiceCompatUtils.isInputWindowOnScreen(service);
  }

  @Override
  public CharSequence getOnScreenKeyboardName() {
    AccessibilityWindowInfo window =
        AccessibilityServiceCompatUtils.getOnscreenInputWindowInfo(service);
    return window == null ? "" : window.getTitle();
  }

  @Override
  public boolean switchInputMethodToBrailleKeyboard() {
    if (FeatureSupport.supportSwitchToInputMethod()) {
      return service
          .getSoftKeyboardController()
          .switchToInputMethod(
              KeyboardUtils.getEnabledImeId(
                  service.getApplicationContext(), service.getPackageName()));
    }
    return false;
  }
}
