package com.google.android.accessibility.talkback;

import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_Part extends Feedback.Part {

  private final int delayMs;

  private final int interruptGroup;

  private final int interruptLevel;

  private final @Nullable String senderName;

  private final boolean interruptSoundAndVibration;

  private final boolean interruptAllFeedback;

  private final boolean interruptGentle;

  private final boolean stopTts;

  private final Feedback.@Nullable Label label;

  private final Feedback.@Nullable DimScreen dimScreen;

  private final Feedback.@Nullable Speech speech;

  private final Feedback.@Nullable VoiceRecognition voiceRecognition;

  private final Feedback.@Nullable ContinuousRead continuousRead;

  private final Feedback.@Nullable Sound sound;

  private final Feedback.@Nullable Vibration vibration;

  private final Feedback.@Nullable TriggerIntent triggerIntent;

  private final Feedback.@Nullable Language language;

  private final Feedback.@Nullable EditText edit;

  private final Feedback.@Nullable SystemAction systemAction;

  private final Feedback.@Nullable NodeAction nodeAction;

  private final Feedback.@Nullable WebAction webAction;

  private final Feedback.@Nullable Scroll scroll;

  private final Feedback.@Nullable Focus focus;

  private final Feedback.@Nullable FocusDirection focusDirection;

  private final Feedback.@Nullable PassThroughMode passThroughMode;

  private final Feedback.@Nullable SpeechRate speechRate;

  private final Feedback.@Nullable AdjustValue adjustValue;

  private final Feedback.@Nullable AdjustVolume adjustVolume;

  private final Feedback.@Nullable TalkBackUI talkBackUI;

  private final Feedback.@Nullable ShowToast showToast;

  private final Feedback.@Nullable Gesture gesture;

  private final Feedback.@Nullable ImageCaption imageCaption;

  private final Feedback.@Nullable DeviceInfo deviceInfo;

  private final Feedback.@Nullable UiChange uiChange;

  private AutoValue_Feedback_Part(
      int delayMs,
      int interruptGroup,
      int interruptLevel,
      @Nullable String senderName,
      boolean interruptSoundAndVibration,
      boolean interruptAllFeedback,
      boolean interruptGentle,
      boolean stopTts,
      Feedback.@Nullable Label label,
      Feedback.@Nullable DimScreen dimScreen,
      Feedback.@Nullable Speech speech,
      Feedback.@Nullable VoiceRecognition voiceRecognition,
      Feedback.@Nullable ContinuousRead continuousRead,
      Feedback.@Nullable Sound sound,
      Feedback.@Nullable Vibration vibration,
      Feedback.@Nullable TriggerIntent triggerIntent,
      Feedback.@Nullable Language language,
      Feedback.@Nullable EditText edit,
      Feedback.@Nullable SystemAction systemAction,
      Feedback.@Nullable NodeAction nodeAction,
      Feedback.@Nullable WebAction webAction,
      Feedback.@Nullable Scroll scroll,
      Feedback.@Nullable Focus focus,
      Feedback.@Nullable FocusDirection focusDirection,
      Feedback.@Nullable PassThroughMode passThroughMode,
      Feedback.@Nullable SpeechRate speechRate,
      Feedback.@Nullable AdjustValue adjustValue,
      Feedback.@Nullable AdjustVolume adjustVolume,
      Feedback.@Nullable TalkBackUI talkBackUI,
      Feedback.@Nullable ShowToast showToast,
      Feedback.@Nullable Gesture gesture,
      Feedback.@Nullable ImageCaption imageCaption,
      Feedback.@Nullable DeviceInfo deviceInfo,
      Feedback.@Nullable UiChange uiChange) {
    this.delayMs = delayMs;
    this.interruptGroup = interruptGroup;
    this.interruptLevel = interruptLevel;
    this.senderName = senderName;
    this.interruptSoundAndVibration = interruptSoundAndVibration;
    this.interruptAllFeedback = interruptAllFeedback;
    this.interruptGentle = interruptGentle;
    this.stopTts = stopTts;
    this.label = label;
    this.dimScreen = dimScreen;
    this.speech = speech;
    this.voiceRecognition = voiceRecognition;
    this.continuousRead = continuousRead;
    this.sound = sound;
    this.vibration = vibration;
    this.triggerIntent = triggerIntent;
    this.language = language;
    this.edit = edit;
    this.systemAction = systemAction;
    this.nodeAction = nodeAction;
    this.webAction = webAction;
    this.scroll = scroll;
    this.focus = focus;
    this.focusDirection = focusDirection;
    this.passThroughMode = passThroughMode;
    this.speechRate = speechRate;
    this.adjustValue = adjustValue;
    this.adjustVolume = adjustVolume;
    this.talkBackUI = talkBackUI;
    this.showToast = showToast;
    this.gesture = gesture;
    this.imageCaption = imageCaption;
    this.deviceInfo = deviceInfo;
    this.uiChange = uiChange;
  }

  @Override
  public int delayMs() {
    return delayMs;
  }

  @Feedback.InterruptGroup
  @Override
  public int interruptGroup() {
    return interruptGroup;
  }

  @Feedback.InterruptLevel
  @Override
  public int interruptLevel() {
    return interruptLevel;
  }

  @Override
  public @Nullable String senderName() {
    return senderName;
  }

  @Override
  public boolean interruptSoundAndVibration() {
    return interruptSoundAndVibration;
  }

  @Override
  public boolean interruptAllFeedback() {
    return interruptAllFeedback;
  }

  @Override
  public boolean interruptGentle() {
    return interruptGentle;
  }

  @Override
  public boolean stopTts() {
    return stopTts;
  }

  @Override
  public Feedback.@Nullable Label label() {
    return label;
  }

  @Override
  public Feedback.@Nullable DimScreen dimScreen() {
    return dimScreen;
  }

  @Override
  public Feedback.@Nullable Speech speech() {
    return speech;
  }

  @Override
  public Feedback.@Nullable VoiceRecognition voiceRecognition() {
    return voiceRecognition;
  }

  @Override
  public Feedback.@Nullable ContinuousRead continuousRead() {
    return continuousRead;
  }

  @Override
  public Feedback.@Nullable Sound sound() {
    return sound;
  }

  @Override
  public Feedback.@Nullable Vibration vibration() {
    return vibration;
  }

  @Override
  public Feedback.@Nullable TriggerIntent triggerIntent() {
    return triggerIntent;
  }

  @Override
  public Feedback.@Nullable Language language() {
    return language;
  }

  @Override
  public Feedback.@Nullable EditText edit() {
    return edit;
  }

  @Override
  public Feedback.@Nullable SystemAction systemAction() {
    return systemAction;
  }

  @Override
  public Feedback.@Nullable NodeAction nodeAction() {
    return nodeAction;
  }

  @Override
  public Feedback.@Nullable WebAction webAction() {
    return webAction;
  }

  @Override
  public Feedback.@Nullable Scroll scroll() {
    return scroll;
  }

  @Override
  public Feedback.@Nullable Focus focus() {
    return focus;
  }

  @Override
  public Feedback.@Nullable FocusDirection focusDirection() {
    return focusDirection;
  }

  @Override
  public Feedback.@Nullable PassThroughMode passThroughMode() {
    return passThroughMode;
  }

  @Override
  public Feedback.@Nullable SpeechRate speechRate() {
    return speechRate;
  }

  @Override
  public Feedback.@Nullable AdjustValue adjustValue() {
    return adjustValue;
  }

  @Override
  public Feedback.@Nullable AdjustVolume adjustVolume() {
    return adjustVolume;
  }

  @Override
  public Feedback.@Nullable TalkBackUI talkBackUI() {
    return talkBackUI;
  }

  @Override
  public Feedback.@Nullable ShowToast showToast() {
    return showToast;
  }

  @Override
  public Feedback.@Nullable Gesture gesture() {
    return gesture;
  }

  @Override
  public Feedback.@Nullable ImageCaption imageCaption() {
    return imageCaption;
  }

  @Override
  public Feedback.@Nullable DeviceInfo deviceInfo() {
    return deviceInfo;
  }

  @Override
  public Feedback.@Nullable UiChange uiChange() {
    return uiChange;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.Part) {
      Feedback.Part that = (Feedback.Part) o;
      return this.delayMs == that.delayMs()
          && this.interruptGroup == that.interruptGroup()
          && this.interruptLevel == that.interruptLevel()
          && (this.senderName == null ? that.senderName() == null : this.senderName.equals(that.senderName()))
          && this.interruptSoundAndVibration == that.interruptSoundAndVibration()
          && this.interruptAllFeedback == that.interruptAllFeedback()
          && this.interruptGentle == that.interruptGentle()
          && this.stopTts == that.stopTts()
          && (this.label == null ? that.label() == null : this.label.equals(that.label()))
          && (this.dimScreen == null ? that.dimScreen() == null : this.dimScreen.equals(that.dimScreen()))
          && (this.speech == null ? that.speech() == null : this.speech.equals(that.speech()))
          && (this.voiceRecognition == null ? that.voiceRecognition() == null : this.voiceRecognition.equals(that.voiceRecognition()))
          && (this.continuousRead == null ? that.continuousRead() == null : this.continuousRead.equals(that.continuousRead()))
          && (this.sound == null ? that.sound() == null : this.sound.equals(that.sound()))
          && (this.vibration == null ? that.vibration() == null : this.vibration.equals(that.vibration()))
          && (this.triggerIntent == null ? that.triggerIntent() == null : this.triggerIntent.equals(that.triggerIntent()))
          && (this.language == null ? that.language() == null : this.language.equals(that.language()))
          && (this.edit == null ? that.edit() == null : this.edit.equals(that.edit()))
          && (this.systemAction == null ? that.systemAction() == null : this.systemAction.equals(that.systemAction()))
          && (this.nodeAction == null ? that.nodeAction() == null : this.nodeAction.equals(that.nodeAction()))
          && (this.webAction == null ? that.webAction() == null : this.webAction.equals(that.webAction()))
          && (this.scroll == null ? that.scroll() == null : this.scroll.equals(that.scroll()))
          && (this.focus == null ? that.focus() == null : this.focus.equals(that.focus()))
          && (this.focusDirection == null ? that.focusDirection() == null : this.focusDirection.equals(that.focusDirection()))
          && (this.passThroughMode == null ? that.passThroughMode() == null : this.passThroughMode.equals(that.passThroughMode()))
          && (this.speechRate == null ? that.speechRate() == null : this.speechRate.equals(that.speechRate()))
          && (this.adjustValue == null ? that.adjustValue() == null : this.adjustValue.equals(that.adjustValue()))
          && (this.adjustVolume == null ? that.adjustVolume() == null : this.adjustVolume.equals(that.adjustVolume()))
          && (this.talkBackUI == null ? that.talkBackUI() == null : this.talkBackUI.equals(that.talkBackUI()))
          && (this.showToast == null ? that.showToast() == null : this.showToast.equals(that.showToast()))
          && (this.gesture == null ? that.gesture() == null : this.gesture.equals(that.gesture()))
          && (this.imageCaption == null ? that.imageCaption() == null : this.imageCaption.equals(that.imageCaption()))
          && (this.deviceInfo == null ? that.deviceInfo() == null : this.deviceInfo.equals(that.deviceInfo()))
          && (this.uiChange == null ? that.uiChange() == null : this.uiChange.equals(that.uiChange()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= delayMs;
    h$ *= 1000003;
    h$ ^= interruptGroup;
    h$ *= 1000003;
    h$ ^= interruptLevel;
    h$ *= 1000003;
    h$ ^= (senderName == null) ? 0 : senderName.hashCode();
    h$ *= 1000003;
    h$ ^= interruptSoundAndVibration ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= interruptAllFeedback ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= interruptGentle ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= stopTts ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= (label == null) ? 0 : label.hashCode();
    h$ *= 1000003;
    h$ ^= (dimScreen == null) ? 0 : dimScreen.hashCode();
    h$ *= 1000003;
    h$ ^= (speech == null) ? 0 : speech.hashCode();
    h$ *= 1000003;
    h$ ^= (voiceRecognition == null) ? 0 : voiceRecognition.hashCode();
    h$ *= 1000003;
    h$ ^= (continuousRead == null) ? 0 : continuousRead.hashCode();
    h$ *= 1000003;
    h$ ^= (sound == null) ? 0 : sound.hashCode();
    h$ *= 1000003;
    h$ ^= (vibration == null) ? 0 : vibration.hashCode();
    h$ *= 1000003;
    h$ ^= (triggerIntent == null) ? 0 : triggerIntent.hashCode();
    h$ *= 1000003;
    h$ ^= (language == null) ? 0 : language.hashCode();
    h$ *= 1000003;
    h$ ^= (edit == null) ? 0 : edit.hashCode();
    h$ *= 1000003;
    h$ ^= (systemAction == null) ? 0 : systemAction.hashCode();
    h$ *= 1000003;
    h$ ^= (nodeAction == null) ? 0 : nodeAction.hashCode();
    h$ *= 1000003;
    h$ ^= (webAction == null) ? 0 : webAction.hashCode();
    h$ *= 1000003;
    h$ ^= (scroll == null) ? 0 : scroll.hashCode();
    h$ *= 1000003;
    h$ ^= (focus == null) ? 0 : focus.hashCode();
    h$ *= 1000003;
    h$ ^= (focusDirection == null) ? 0 : focusDirection.hashCode();
    h$ *= 1000003;
    h$ ^= (passThroughMode == null) ? 0 : passThroughMode.hashCode();
    h$ *= 1000003;
    h$ ^= (speechRate == null) ? 0 : speechRate.hashCode();
    h$ *= 1000003;
    h$ ^= (adjustValue == null) ? 0 : adjustValue.hashCode();
    h$ *= 1000003;
    h$ ^= (adjustVolume == null) ? 0 : adjustVolume.hashCode();
    h$ *= 1000003;
    h$ ^= (talkBackUI == null) ? 0 : talkBackUI.hashCode();
    h$ *= 1000003;
    h$ ^= (showToast == null) ? 0 : showToast.hashCode();
    h$ *= 1000003;
    h$ ^= (gesture == null) ? 0 : gesture.hashCode();
    h$ *= 1000003;
    h$ ^= (imageCaption == null) ? 0 : imageCaption.hashCode();
    h$ *= 1000003;
    h$ ^= (deviceInfo == null) ? 0 : deviceInfo.hashCode();
    h$ *= 1000003;
    h$ ^= (uiChange == null) ? 0 : uiChange.hashCode();
    return h$;
  }

  static final class Builder extends Feedback.Part.Builder {
    private Integer delayMs;
    private Integer interruptGroup;
    private Integer interruptLevel;
    private @Nullable String senderName;
    private Boolean interruptSoundAndVibration;
    private Boolean interruptAllFeedback;
    private Boolean interruptGentle;
    private Boolean stopTts;
    private Feedback.@Nullable Label label;
    private Feedback.@Nullable DimScreen dimScreen;
    private Feedback.@Nullable Speech speech;
    private Feedback.@Nullable VoiceRecognition voiceRecognition;
    private Feedback.@Nullable ContinuousRead continuousRead;
    private Feedback.@Nullable Sound sound;
    private Feedback.@Nullable Vibration vibration;
    private Feedback.@Nullable TriggerIntent triggerIntent;
    private Feedback.@Nullable Language language;
    private Feedback.@Nullable EditText edit;
    private Feedback.@Nullable SystemAction systemAction;
    private Feedback.@Nullable NodeAction nodeAction;
    private Feedback.@Nullable WebAction webAction;
    private Feedback.@Nullable Scroll scroll;
    private Feedback.@Nullable Focus focus;
    private Feedback.@Nullable FocusDirection focusDirection;
    private Feedback.@Nullable PassThroughMode passThroughMode;
    private Feedback.@Nullable SpeechRate speechRate;
    private Feedback.@Nullable AdjustValue adjustValue;
    private Feedback.@Nullable AdjustVolume adjustVolume;
    private Feedback.@Nullable TalkBackUI talkBackUI;
    private Feedback.@Nullable ShowToast showToast;
    private Feedback.@Nullable Gesture gesture;
    private Feedback.@Nullable ImageCaption imageCaption;
    private Feedback.@Nullable DeviceInfo deviceInfo;
    private Feedback.@Nullable UiChange uiChange;
    Builder() {
    }
    @Override
    public Feedback.Part.Builder setDelayMs(int delayMs) {
      this.delayMs = delayMs;
      return this;
    }
    @Override
    public Feedback.Part.Builder setInterruptGroup(int interruptGroup) {
      this.interruptGroup = interruptGroup;
      return this;
    }
    @Override
    public Feedback.Part.Builder setInterruptLevel(int interruptLevel) {
      this.interruptLevel = interruptLevel;
      return this;
    }
    @Override
    public Feedback.Part.Builder setSenderName(String senderName) {
      this.senderName = senderName;
      return this;
    }
    @Override
    public Feedback.Part.Builder setInterruptSoundAndVibration(boolean interruptSoundAndVibration) {
      this.interruptSoundAndVibration = interruptSoundAndVibration;
      return this;
    }
    @Override
    public Feedback.Part.Builder setInterruptAllFeedback(boolean interruptAllFeedback) {
      this.interruptAllFeedback = interruptAllFeedback;
      return this;
    }
    @Override
    public Feedback.Part.Builder setInterruptGentle(boolean interruptGentle) {
      this.interruptGentle = interruptGentle;
      return this;
    }
    @Override
    public Feedback.Part.Builder setStopTts(boolean stopTts) {
      this.stopTts = stopTts;
      return this;
    }
    @Override
    public Feedback.Part.Builder setLabel(Feedback.Label label) {
      this.label = label;
      return this;
    }
    @Override
    public Feedback.Part.Builder setDimScreen(Feedback.DimScreen dimScreen) {
      this.dimScreen = dimScreen;
      return this;
    }
    @Override
    public Feedback.Part.Builder setSpeech(Feedback.Speech speech) {
      this.speech = speech;
      return this;
    }
    @Override
    public Feedback.Part.Builder setVoiceRecognition(Feedback.VoiceRecognition voiceRecognition) {
      this.voiceRecognition = voiceRecognition;
      return this;
    }
    @Override
    public Feedback.Part.Builder setContinuousRead(Feedback.ContinuousRead continuousRead) {
      this.continuousRead = continuousRead;
      return this;
    }
    @Override
    public Feedback.Part.Builder setSound(Feedback.Sound sound) {
      this.sound = sound;
      return this;
    }
    @Override
    public Feedback.Part.Builder setVibration(Feedback.Vibration vibration) {
      this.vibration = vibration;
      return this;
    }
    @Override
    public Feedback.Part.Builder setTriggerIntent(Feedback.TriggerIntent triggerIntent) {
      this.triggerIntent = triggerIntent;
      return this;
    }
    @Override
    public Feedback.Part.Builder setLanguage(Feedback.Language language) {
      this.language = language;
      return this;
    }
    @Override
    public Feedback.Part.Builder setEdit(Feedback.EditText edit) {
      this.edit = edit;
      return this;
    }
    @Override
    public Feedback.Part.Builder setSystemAction(Feedback.SystemAction systemAction) {
      this.systemAction = systemAction;
      return this;
    }
    @Override
    public Feedback.Part.Builder setNodeAction(Feedback.NodeAction nodeAction) {
      this.nodeAction = nodeAction;
      return this;
    }
    @Override
    public Feedback.Part.Builder setWebAction(Feedback.WebAction webAction) {
      this.webAction = webAction;
      return this;
    }
    @Override
    public Feedback.Part.Builder setScroll(Feedback.Scroll scroll) {
      this.scroll = scroll;
      return this;
    }
    @Override
    public Feedback.Part.Builder setFocus(Feedback.Focus focus) {
      this.focus = focus;
      return this;
    }
    @Override
    public Feedback.Part.Builder setFocusDirection(Feedback.FocusDirection focusDirection) {
      this.focusDirection = focusDirection;
      return this;
    }
    @Override
    public Feedback.Part.Builder setPassThroughMode(Feedback.PassThroughMode passThroughMode) {
      this.passThroughMode = passThroughMode;
      return this;
    }
    @Override
    public Feedback.Part.Builder setSpeechRate(Feedback.SpeechRate speechRate) {
      this.speechRate = speechRate;
      return this;
    }
    @Override
    public Feedback.Part.Builder setAdjustValue(Feedback.AdjustValue adjustValue) {
      this.adjustValue = adjustValue;
      return this;
    }
    @Override
    public Feedback.Part.Builder setAdjustVolume(Feedback.AdjustVolume adjustVolume) {
      this.adjustVolume = adjustVolume;
      return this;
    }
    @Override
    public Feedback.Part.Builder setTalkBackUI(Feedback.TalkBackUI talkBackUI) {
      this.talkBackUI = talkBackUI;
      return this;
    }
    @Override
    public Feedback.Part.Builder setShowToast(Feedback.ShowToast showToast) {
      this.showToast = showToast;
      return this;
    }
    @Override
    public Feedback.Part.Builder setGesture(Feedback.Gesture gesture) {
      this.gesture = gesture;
      return this;
    }
    @Override
    public Feedback.Part.Builder setImageCaption(Feedback.ImageCaption imageCaption) {
      this.imageCaption = imageCaption;
      return this;
    }
    @Override
    public Feedback.Part.Builder setDeviceInfo(Feedback.DeviceInfo deviceInfo) {
      this.deviceInfo = deviceInfo;
      return this;
    }
    @Override
    public Feedback.Part.Builder setUiChange(Feedback.UiChange uiChange) {
      this.uiChange = uiChange;
      return this;
    }
    @Override
    public Feedback.Part build() {
      if (this.delayMs == null
          || this.interruptGroup == null
          || this.interruptLevel == null
          || this.interruptSoundAndVibration == null
          || this.interruptAllFeedback == null
          || this.interruptGentle == null
          || this.stopTts == null) {
        StringBuilder missing = new StringBuilder();
        if (this.delayMs == null) {
          missing.append(" delayMs");
        }
        if (this.interruptGroup == null) {
          missing.append(" interruptGroup");
        }
        if (this.interruptLevel == null) {
          missing.append(" interruptLevel");
        }
        if (this.interruptSoundAndVibration == null) {
          missing.append(" interruptSoundAndVibration");
        }
        if (this.interruptAllFeedback == null) {
          missing.append(" interruptAllFeedback");
        }
        if (this.interruptGentle == null) {
          missing.append(" interruptGentle");
        }
        if (this.stopTts == null) {
          missing.append(" stopTts");
        }
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_Feedback_Part(
          this.delayMs,
          this.interruptGroup,
          this.interruptLevel,
          this.senderName,
          this.interruptSoundAndVibration,
          this.interruptAllFeedback,
          this.interruptGentle,
          this.stopTts,
          this.label,
          this.dimScreen,
          this.speech,
          this.voiceRecognition,
          this.continuousRead,
          this.sound,
          this.vibration,
          this.triggerIntent,
          this.language,
          this.edit,
          this.systemAction,
          this.nodeAction,
          this.webAction,
          this.scroll,
          this.focus,
          this.focusDirection,
          this.passThroughMode,
          this.speechRate,
          this.adjustValue,
          this.adjustVolume,
          this.talkBackUI,
          this.showToast,
          this.gesture,
          this.imageCaption,
          this.deviceInfo,
          this.uiChange);
    }
  }

}
