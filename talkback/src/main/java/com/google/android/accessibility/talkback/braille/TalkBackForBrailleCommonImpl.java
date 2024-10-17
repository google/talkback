package com.google.android.accessibility.talkback.braille;

import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleCommon;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;

/** Implements TalkBack functionalities exposed to BrailleCommon. */
public class TalkBackForBrailleCommonImpl implements TalkBackForBrailleCommon {
  private final Pipeline.FeedbackReturner feedbackReturner;
  private final TalkBackService service;

  public TalkBackForBrailleCommonImpl(
      TalkBackService talkBackService, Pipeline.FeedbackReturner feedbackReturner) {
    this.service = talkBackService;
    this.feedbackReturner = feedbackReturner;
  }

  @Override
  public void speak(CharSequence textToSpeak, int delayMs, SpeakOptions speakOptions) {
    feedbackReturner.returnFeedback(
        Performance.EVENT_ID_UNTRACKED,
        Feedback.speech(textToSpeak, speakOptions).setDelayMs(delayMs));
  }

  @Override
  public FeedbackController getFeedBackController() {
    return service.getFeedbackController();
  }
}
