/*
 * Copyright 2020 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.brailleime.tutorial;

import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS;
import static com.google.android.accessibility.brailleime.BrailleImeVibrator.VibrationType.OTHER_GESTURES;
import static com.google.android.accessibility.brailleime.translate.BrailleTranslateUtilsUeb.getTextToSpeak;
import static com.google.android.accessibility.brailleime.tutorial.TutorialAnimationView.SwipeAnimation.Direction.BOTTOM_TO_TOP;
import static com.google.android.accessibility.brailleime.tutorial.TutorialAnimationView.SwipeAnimation.Direction.LEFT_TO_RIGHT;
import static com.google.android.accessibility.brailleime.tutorial.TutorialAnimationView.SwipeAnimation.Direction.RIGHT_TO_LEFT;
import static com.google.android.accessibility.brailleime.tutorial.TutorialAnimationView.SwipeAnimation.Direction.TOP_TO_BOTTOM;
import static java.util.stream.Collectors.toList;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.util.Size;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.brailleime.BrailleCharacter;
import com.google.android.accessibility.brailleime.BrailleIme.OrientationSensitive;
import com.google.android.accessibility.brailleime.BrailleImeLog;
import com.google.android.accessibility.brailleime.BrailleImeVibrator;
import com.google.android.accessibility.brailleime.BrailleImeVibrator.VibrationType;
import com.google.android.accessibility.brailleime.BrailleLanguages.Code;
import com.google.android.accessibility.brailleime.BrailleWord;
import com.google.android.accessibility.brailleime.OrientationMonitor;
import com.google.android.accessibility.brailleime.OrientationMonitor.Orientation;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.brailleime.UserPreferences;
import com.google.android.accessibility.brailleime.Utils;
import com.google.android.accessibility.brailleime.dialog.ContextMenuDialog;
import com.google.android.accessibility.brailleime.input.BrailleInputPlane.DotTarget;
import com.google.android.accessibility.brailleime.input.BrailleInputView;
import com.google.android.accessibility.brailleime.input.BrailleInputView.FingersPattern;
import com.google.android.accessibility.brailleime.input.Swipe;
import com.google.android.accessibility.brailleime.input.Swipe.Direction;
import com.google.android.accessibility.brailleime.translate.BrailleTranslateUtils;
import com.google.android.accessibility.brailleime.translate.BrailleTranslateUtilsUeb;
import com.google.android.accessibility.brailleime.translate.Translator;
import com.google.android.accessibility.brailleime.tutorial.TutorialView.TutorialState.State;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.UtteranceCompleteRunnable;
import com.google.common.base.Ascii;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Views that displays the braille tutorial and there are several {@link State} in the tutorial. */
public class TutorialView extends FrameLayout implements OrientationSensitive {

  /** An interface of tutorial state machine. */
  public interface TutorialState {

    /** The stage in which the tutorial is. */
    enum State {
      NONE,
      INTRO,
      ROTATE_ORIENTATION,
      ROTATE_ORIENTATION_CONTINUE,
      TYPE_LETTER_A,
      TYPE_LETTER_BCD,
      SWIPE_LEFT,
      SWIPE_RIGHT,
      SWIPE_DOWN_2_FINGERS,
      SWIPE_DOWN_3_FINGERS,
      SWIPE_UP_3_FINGERS,
      CONTEXT_MENU_OPENED,
      HOLD_6_FINGERS,
    }

    State getCurrentState();

    TutorialState nextState();

    void loadView();

    default String hintText() {
      return "";
    }

    default String resultText() {
      return "";
    }

    default String audialAnnouncement() {
      return "";
    }

    default String audialAnnouncementRepeated() {
      return "";
    }

    /** Whether user has completed the guide in the current tutorial step. */
    default boolean isActionCompleted() {
      return false;
    }

    /**
     * Invokes when speech announcement is finished. In each tutorial step it should repeat the
     * audio announcement or go to next step once post-action announcement is finished.
     */
    default void onUtteranceCompleted() {}

    default void onDoubleTap() {}

    default void onBrailleProduced(BrailleCharacter brailleCharacter) {}

    default void onSwipeProduced(Swipe swipe) {}

    default void onCalibration(FingersPattern fingersPattern) {}
  }

  class Intro implements TutorialState {
    @Override
    public State getCurrentState() {
      return State.INTRO;
    }

    @Override
    public TutorialState nextState() {
      return isTabletop ? holdSixFingers : rotateOrientation;
    }

    @Override
    public void loadView() {
      tutorialCallback.onBrailleImeInactivated();
      inflate(getContext(), R.layout.tutorial_intro, TutorialView.this);
      ImageView animationView = findViewById(R.id.tutorial_animation);
      ((AnimationDrawable) animationView.getDrawable()).start();
      findViewById(R.id.next_button)
          .setOnClickListener(
              view -> {
                tutorialCallback.onBrailleImeActivated();
                switchNextState(nextState(), 0);
              });
      findViewById(R.id.leave_keyboard_button)
          .setOnClickListener(
              view -> {
                // For users who got in accidentally, switch to the next keyboard.
                tutorialCallback.onSwitchToNextInputMethod();
                switchNextState(State.NONE, /* delay= */ 0);
              });

      // To make TalkBack focus on tutorial view. This is not a good solution, try to find better
      // one later.
      TutorialView.this.postDelayed(
          () ->
              findViewById(R.id.tutorial_title)
                  .performAccessibilityAction(ACTION_ACCESSIBILITY_FOCUS, new Bundle()),
          /* delayMillis= */ 1000);
    }
  }

  class RotateOrientation implements TutorialState {
    private boolean actionCompleted;

    @Override
    public State getCurrentState() {
      return State.ROTATE_ORIENTATION;
    }

    @Override
    public TutorialState nextState() {
      OrientationMonitor.Orientation orientation =
          OrientationMonitor.getInstance().getCurrentOrientation();
      if (orientation == OrientationMonitor.Orientation.PORTRAIT
          || orientation == OrientationMonitor.Orientation.REVERSE_PORTRAIT) {
        // Guide user to rotate their phone to landscape.
        return rotateOrientationContinue;
      }
      return typeLetterA;
    }

    @Override
    public void loadView() {
      // This view is used to detect swipe gesture and set as transparent to not seen by user.
      inputView.setAlpha(0f);
      addView(inputView);

      inflate(getContext(), R.layout.tutorial_rotate_orientation, TutorialView.this);
      findViewById(R.id.tap_to_continue).setBackground(new TapMeAnimationDrawable(context));
      speakAnnouncement(audialAnnouncement(), /* delayMs= */ 0);
    }

    @Override
    public void onUtteranceCompleted() {
      speakAnnouncement(audialAnnouncementRepeated(), /* delayMs= */ 0);
    }

    @Override
    public String audialAnnouncement() {
      return getResources()
          .getString(
              R.string.rotate_orientation_announcement,
              getResources().getString(R.string.rotate_orientation_inactive_announcement));
    }

    @Override
    public String audialAnnouncementRepeated() {
      return getResources().getString(R.string.rotate_orientation_inactive_announcement);
    }

    @Override
    public void onDoubleTap() {
      if (!actionCompleted) {
        actionCompleted = true;
        // onDoubleTap callback comes twice, delay 500ms to consume this event.
        switchNextState(nextState(), /* delay= */ 500);
      }
    }

    @Override
    public void onSwipeProduced(Swipe swipe) {
      int touchCount = swipe.getTouchCount();
      Direction direction = swipe.getDirection();
      if (direction == Direction.DOWN && touchCount == 3) {
        // Exit Braille keyboard.
        tutorialCallback.onSwitchToNextInputMethod();
      } else if (direction == Direction.LEFT && touchCount == 3) {
        // Braille keyboard view is forced to be in landscape. When device is portrait and user
        // swipes left, for keyboard view, it's swipe down.
        if (OrientationMonitor.getInstance().getCurrentOrientation() == Orientation.PORTRAIT) {
          // Exit Braille keyboard.
          tutorialCallback.onSwitchToNextInputMethod();
        }
      }
      vibrateForSwipeGestures(swipe);
    }
  }

  class RotateOrientationContinue implements TutorialState {
    private static final int ANIMATION_DELAY_MS = 3000;
    private ValueAnimator animator;
    private boolean actionCompleted;

    @VisibleForTesting
    final OrientationMonitor.Callback orientationMonitorCallBack =
        new OrientationMonitor.Callback() {
          @Override
          public void onOrientationChanged(OrientationMonitor.Orientation orientation) {
            if (orientation == OrientationMonitor.Orientation.LANDSCAPE
                || orientation == OrientationMonitor.Orientation.REVERSE_LANDSCAPE) {
              tutorialCallback.onPlaySound(R.raw.volume_beep);
              BrailleImeVibrator.getInstance(context).vibrate(OTHER_GESTURES);
              // Save the state only because TutorialView will recreate if orientation is changed.
              TutorialView.this.state = rotateOrientation;
              tutorialCallback.unregisterOrientationChange();
              tutorialCallback.onRestartTutorial();
            }
          }
        };

    @Override
    public State getCurrentState() {
      return State.ROTATE_ORIENTATION_CONTINUE;
    }

    @Override
    public TutorialState nextState() {
      return typeLetterA;
    }

    @Override
    public void loadView() {
      tutorialCallback.registerOrientationChange(orientationMonitorCallBack);
      // Use to detect swipe gesture and set as transparent.
      inputView.setAlpha(0f);
      addView(inputView);

      inflate(getContext(), R.layout.tutorial_rotate_orientation, TutorialView.this);
      findViewById(R.id.tap_to_continue).setBackground(new TapMeAnimationDrawable(context));
      startHighlightTextAnimation();
      speakAnnouncement(audialAnnouncement(), /* delayMs= */ 0);
    }

    @Override
    public String audialAnnouncement() {
      return getResources().getString(R.string.rotate_orientation_continue_announcement);
    }

    @Override
    public String audialAnnouncementRepeated() {
      return getResources().getString(R.string.rotate_orientation_continue_announcement);
    }

    @Override
    public void onUtteranceCompleted() {
      speakAnnouncement(audialAnnouncementRepeated(), /* delayMs= */ 0);
    }

    @Override
    public void onDoubleTap() {
      if (!actionCompleted) {
        actionCompleted = true;
        tutorialCallback.unregisterOrientationChange();
        switchNextState(nextState(), /* delay= */ 0);
      }
    }

    private void startHighlightTextAnimation() {
      final TextView highlightTextView = findViewById(R.id.highlight_description);
      CharSequence highlightText = ((TextView) findViewById(R.id.highlight_description)).getText();
      final int highlightColor =
          getResources().getColor(R.color.text_highlight_color, /* theme= */ null);
      animator = ValueAnimator.ofInt(1, highlightText.length());
      animator.setDuration(highlightText.length() * 10);
      animator.start();
      animator.addUpdateListener(
          animation -> {
            Spannable spanText = Spannable.Factory.getInstance().newSpannable(highlightText);
            spanText.setSpan(
                new BackgroundColorSpan(highlightColor),
                /* start= */ 0,
                (int) animation.getAnimatedValue(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            highlightTextView.setText(spanText);
            highlightTextView.invalidate();
          });
      animator.addListener(
          new AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {}

            @Override
            public void onAnimationEnd(Animator animation) {
              animator.setStartDelay(ANIMATION_DELAY_MS);
              // This might not be a good solution but it works, do not start animator because
              // Robolectric tests run on main thread also, otherwise animator will repeat in
              // endless loop and cause the test timeout.
              if (!Utils.isRobolectric()) {
                animator.start();
              }
            }

            @Override
            public void onAnimationCancel(Animator animation) {}

            @Override
            public void onAnimationRepeat(Animator animation) {}
          });
    }
  }

  class HoldSixFingers implements TutorialState {
    private boolean actionCompleted;

    @Override
    public State getCurrentState() {
      return State.HOLD_6_FINGERS;
    }

    @Override
    public TutorialState nextState() {
      return typeLetterA;
    }

    @Override
    public String audialAnnouncement() {
      return getResources()
          .getString(
              R.string.hold_six_fingers_announcement,
              getResources().getString(R.string.hold_six_fingers_description));
    }

    @Override
    public String audialAnnouncementRepeated() {
      return getResources().getString(R.string.hold_six_fingers_inactive_announcement);
    }

    @Override
    public boolean isActionCompleted() {
      return actionCompleted;
    }

    @Override
    public void onCalibration(FingersPattern fingersPattern) {
      if (fingersPattern.equals(FingersPattern.SIX_FINGERS)
          || fingersPattern.equals(FingersPattern.REMAINING_THREE_FINGERS)) {
        if (actionCompleted) {
          return;
        }
        tutorialCallback.onPlaySound(R.raw.volume_beep);
        // Wait a second for playing sound and then speak the post-action announcement.
        speakAnnouncement(
            getResources().getString(R.string.calibration_finish_announcement), BEEP_DELAY_MS);
        actionCompleted = true;
      } else if (fingersPattern.equals(FingersPattern.FIVE_FINGERS)) {
        speakAnnouncement(
            getResources()
                .getString(
                    reverseDots
                        ? R.string.calibration_step1_hold_right_finger_announcement
                        : R.string.calibration_step1_hold_left_finger_announcement),
            /* delayMs= */ 0);
      } else if (fingersPattern.equals(FingersPattern.FIRST_THREE_FINGERS)) {
        speakAnnouncement(
            getResources()
                .getString(
                    reverseDots
                        ? R.string.calibration_step2_hold_left_finger_announcement
                        : R.string.calibration_step2_hold_right_finger_announcement),
            /* delayMs= */ 0);
      } else if (fingersPattern.equals(FingersPattern.UNKNOWN)) {
        speakAnnouncement(
            getResources().getString(R.string.calibration_fail_announcement), /* delayMs= */ 0);
      }
    }

    @Override
    public void onUtteranceCompleted() {
      if (actionCompleted) {
        actionCompleted = false;
        switchNextState(nextState(), /* delay= */ 0);
      } else {
        speakAnnouncement(audialAnnouncementRepeated(), /* delayMs= */ 0);
      }
    }

    @Override
    public void loadView() {
      // This view is used to detect swipe gesture and set as transparent to not seen by user.
      inputView.setAlpha(0f);
      addView(inputView);

      inflate(getContext(), R.layout.tutorial_hold_six_fingers, TutorialView.this);
      ImageView animationView = findViewById(R.id.tutorial_animation);
      ((AnimationDrawable) animationView.getDrawable()).start();
      speakAnnouncement(audialAnnouncement(), /* delayMs= */ 0);
    }
  }

  class TypeLetterA implements TutorialState {
    private boolean actionCompleted;

    @Override
    public State getCurrentState() {
      return State.TYPE_LETTER_A;
    }

    @Override
    public TutorialState nextState() {
      return tapLetterBCD;
    }

    @Override
    public void loadView() {
      dotBlockView.setAlpha(0f);
      dotBlockView.animate().alpha(1f).setDuration(ANIMATION_DURATION_MS);
      addView(dotBlockView);
      // Get dot 1 coordinate to show flashing animation.
      dotsFlashingAnimationView =
          new DotsFlashingAnimationView(
              context,
              getDotTargetsForBrailleCharacter(BrailleTranslateUtilsUeb.LETTER_A),
              orientation,
              isTabletop);
      dotsFlashingAnimationView.setVisibility(View.INVISIBLE);
      inputView.setAlpha(0f);
      inputView
          .animate()
          .alpha(1f)
          .setDuration(INPUT_VIEW_ANIMATION_DURATION_MS)
          .setStartDelay(ANIMATION_DURATION_MS)
          .setListener(
              new AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {}

                @Override
                public void onAnimationEnd(Animator animation) {
                  postDelayed(
                      () -> {
                        removeView(inputView);
                        addView(inputView, 0);
                        dotsFlashingAnimationView.setVisibility(View.VISIBLE);
                        tutorialAnimationView.startHintToastAnimation(state.hintText());
                      },
                      FLASHING_ANIMATION_DELAY_MS);
                }

                @Override
                public void onAnimationCancel(Animator animation) {}

                @Override
                public void onAnimationRepeat(Animator animation) {}
              });
      addView(inputView);
      addView(dotsFlashingAnimationView);
      tutorialAnimationView.reset();
      addView(tutorialAnimationView);
      speakAnnouncement(audialAnnouncement(), /* delayMs= */ 0);
    }

    @Override
    public String audialAnnouncement() {
      return getResources()
          .getString(
              R.string.type_letter_a_announcement,
              reverseDots
                  ? getResources().getString(R.string.type_letter_a_announcement_right_index_finger)
                  : getResources()
                      .getString(R.string.type_letter_a_announcement_left_index_finger));
    }

    @Override
    public String audialAnnouncementRepeated() {
      return getResources()
          .getString(
              R.string.type_letter_a_inactive_announcement,
              reverseDots
                  ? getResources().getString(R.string.instruction_type_letter_a_right_index_finger)
                  : getResources().getString(R.string.instruction_type_letter_a_left_index_finger));
    }

    @Override
    public String hintText() {
      return getResources()
          .getString(
              R.string.instruction_type_letter_a,
              reverseDots
                  ? getResources().getString(R.string.instruction_type_letter_a_right_index_finger)
                  : getResources().getString(R.string.instruction_type_letter_a_left_index_finger));
    }

    @Override
    public boolean isActionCompleted() {
      return actionCompleted;
    }

    @Override
    public void onUtteranceCompleted() {
      if (actionCompleted) {
        actionCompleted = false;
        switchNextState(nextState(), /* delay= */ 0);
      } else {
        speakAnnouncement(audialAnnouncementRepeated(), /* delayMs= */ 0);
      }
    }

    @Override
    public void onBrailleProduced(BrailleCharacter brailleCharacter) {
      if (actionCompleted) {
        return;
      }
      BrailleImeVibrator.getInstance(context).vibrate(VibrationType.BRAILLE_COMMISSION);
      // Dot 1 is clicked.
      if (brailleCharacter.toByte() == 0b01) {
        tutorialAnimationView.startActionResultAnimation(
            Ascii.toUpperCase(translator.translateToPrint(new BrailleWord(brailleCharacter))));
        tutorialCallback.onPlaySound(R.raw.volume_beep);
        // Wait a second for playing sound and then speak the post-action announcement.
        String textToSpeak = translator.translateToPrint(new BrailleWord(brailleCharacter));
        speakAnnouncement(textToSpeak, BEEP_DELAY_MS);
        actionCompleted = true;
      } else {
        // Read out the corresponding announcement.
        speakBrailleCharacter(brailleCharacter, /* delayMs= */ 0);
      }
    }

    @Override
    public void onSwipeProduced(Swipe swipe) {
      if (actionCompleted) {
        return;
      }

      // Read out the corresponding announcement and do nothing.
      speakSwipeEvent(swipe);
      vibrateForSwipeGestures(swipe);
    }
  }

  class TypeLetterBCD implements TutorialState {
    private static final int TOTAL_LETTERS = 3;
    private boolean actionCompleted;
    private final List<Character> remainLetters =
        new ArrayList<>(
            Arrays.asList(Character.valueOf('B'), Character.valueOf('C'), Character.valueOf('D')));

    @Override
    public State getCurrentState() {
      return State.TYPE_LETTER_BCD;
    }

    @Override
    public TutorialState nextState() {
      if (remainLetters.isEmpty()) {
        return swipeLeft;
      }
      return this;
    }

    @Override
    public void loadView() {
      addView(inputView);
      BrailleCharacter brailleCharacter;
      switch (remainLetters.get(0)) {
        case 'B':
          brailleCharacter = BrailleTranslateUtilsUeb.LETTER_B;
          break;
        case 'C':
          brailleCharacter = BrailleTranslateUtilsUeb.LETTER_C;
          break;
        case 'D':
          brailleCharacter = BrailleTranslateUtilsUeb.LETTER_D;
          break;
        default:
          throw new IllegalStateException("Unexpected value: " + remainLetters.get(0));
      }
      List<DotTarget> animationDots = getDotTargetsForBrailleCharacter(brailleCharacter);
      dotsFlashingAnimationView =
          new DotsFlashingAnimationView(context, animationDots, orientation, isTabletop);
      addView(dotsFlashingAnimationView);
      tutorialAnimationView.reset();
      tutorialAnimationView.startHintToastAnimation(state.hintText());
      addView(tutorialAnimationView);
      speakAnnouncement(audialAnnouncement(), /* delayMs= */ 0);
    }

    @Override
    public String audialAnnouncement() {
      if (remainLetters.size() == TOTAL_LETTERS) {
        return getResources().getString(R.string.instruction_type_letter_bcd);
      }
      return audialAnnouncementRepeated();
    }

    @Override
    public String audialAnnouncementRepeated() {
      switch (remainLetters.get(0)) {
        case 'B':
          return getResources()
              .getString(
                  R.string.type_letter_inactive_announcement,
                  BrailleTranslateUtils.getDotsText(
                      getResources(), BrailleTranslateUtilsUeb.LETTER_B),
                  remainLetters.get(0));
        case 'C':
          return getResources()
              .getString(
                  R.string.type_letter_inactive_announcement,
                  BrailleTranslateUtils.getDotsText(
                      getResources(), BrailleTranslateUtilsUeb.LETTER_C),
                  remainLetters.get(0));
        case 'D':
          return getResources()
              .getString(
                  R.string.type_letter_inactive_announcement,
                  BrailleTranslateUtils.getDotsText(
                      getResources(), BrailleTranslateUtilsUeb.LETTER_D),
                  remainLetters.get(0));
        default:
          // Should not happen.
          return getResources()
              .getString(
                  R.string.type_letter_inactive_announcement,
                  BrailleTranslateUtils.getDotsText(
                      getResources(), BrailleTranslateUtilsUeb.LETTER_B),
                  remainLetters.get(0));
      }
    }

    @Override
    public String hintText() {
      return getResources().getString(R.string.instruction_type_letter_bcd);
    }

    @Override
    public boolean isActionCompleted() {
      return actionCompleted;
    }

    @Override
    public void onUtteranceCompleted() {
      if (actionCompleted) {
        actionCompleted = false;
        switchNextState(nextState(), /* delay= */ 0);
      } else {
        speakAnnouncement(audialAnnouncementRepeated(), /* delayMs= */ 0);
      }
    }

    @Override
    public void onBrailleProduced(BrailleCharacter brailleCharacter) {
      if (actionCompleted) {
        return;
      }
      BrailleImeVibrator.getInstance(context).vibrate(VibrationType.BRAILLE_COMMISSION);

      String result =
          Ascii.toUpperCase(translator.translateToPrint(new BrailleWord(brailleCharacter)));
      Character character = Character.MAX_VALUE;
      if (result.length() > 0) {
        character = result.charAt(0);
      }

      if (remainLetters.contains(character)) {
        tutorialAnimationView.startActionResultAnimation(result);
        tutorialCallback.onPlaySound(R.raw.volume_beep);
        // Wait a second for playing sound and then speak the post-action announcement.
        String textToSpeak = translator.translateToPrint(new BrailleWord(brailleCharacter));
        speakAnnouncement(textToSpeak, BEEP_DELAY_MS);
        actionCompleted = true;
        remainLetters.remove(character);
      } else {
        // Read out the corresponding announcement.
        speakBrailleCharacter(brailleCharacter, /* delayMs= */ 0);
      }
    }

    @Override
    public void onSwipeProduced(Swipe swipe) {
      if (actionCompleted) {
        return;
      }
      // Read out the corresponding announcement and do nothing.
      speakSwipeEvent(swipe);
      vibrateForSwipeGestures(swipe);
    }
  }

  class SwipeLeft implements TutorialState {
    private boolean actionCompleted;

    @Override
    public State getCurrentState() {
      return State.SWIPE_LEFT;
    }

    @Override
    public TutorialState nextState() {
      return swipeRight;
    }

    @Override
    public void loadView() {
      addView(inputView);
      tutorialAnimationView.reset();
      tutorialAnimationView.startSwipeAnimation(1, LEFT_TO_RIGHT);
      tutorialAnimationView.startHintToastAnimation(state.hintText());
      addView(tutorialAnimationView);
      speakAnnouncement(audialAnnouncement(), /* delayMs= */ 0);
    }

    @Override
    public String audialAnnouncement() {
      return getResources().getString(R.string.swipe_left_announcement);
    }

    @Override
    public String audialAnnouncementRepeated() {
      return getResources().getString(R.string.swipe_left_announcement);
    }

    @Override
    public String hintText() {
      return getResources().getString(R.string.instruction_swipe_left);
    }

    @Override
    public String resultText() {
      return getResources().getString(R.string.result_swipe_left);
    }

    @Override
    public boolean isActionCompleted() {
      return actionCompleted;
    }

    @Override
    public void onUtteranceCompleted() {
      if (actionCompleted) {
        actionCompleted = false;
        switchNextState(nextState(), /* delay= */ 0);
      } else {
        speakAnnouncement(audialAnnouncementRepeated(), /* delayMs= */ 0);
      }
    }

    @Override
    public void onBrailleProduced(BrailleCharacter brailleCharacter) {
      if (actionCompleted) {
        return;
      }

      BrailleImeVibrator.getInstance(context).vibrate(VibrationType.BRAILLE_COMMISSION);
      // Read out the corresponding announcement.
      speakBrailleCharacter(brailleCharacter, /* delayMs= */ 0);
    }

    @Override
    public void onSwipeProduced(Swipe swipe) {
      if (actionCompleted) {
        return;
      }

      int touchCount = swipe.getTouchCount();
      Direction direction = swipe.getDirection();
      if (direction == Direction.RIGHT && touchCount == 1) {
        tutorialAnimationView.stopSwipeAnimation();
        tutorialAnimationView.startActionResultAnimation(resultText());
        tutorialCallback.onPlaySound(R.raw.volume_beep);
        // Wait a second for playing sound and then speak the post-action announcement.
        speakAnnouncement(
            getResources().getString(R.string.swipe_left_action_result_announcement),
            BEEP_DELAY_MS);
        actionCompleted = true;
      } else {
        // Read out the corresponding announcement.
        speakSwipeEvent(swipe);
      }
      vibrateForSwipeGestures(swipe);
    }
  }

  class SwipeRight implements TutorialState {
    private boolean actionCompleted;

    @Override
    public State getCurrentState() {
      return State.SWIPE_RIGHT;
    }

    @Override
    public TutorialState nextState() {
      return swipeDown2Fingers;
    }

    @Override
    public void loadView() {
      addView(inputView);
      tutorialAnimationView.reset();
      tutorialAnimationView.startSwipeAnimation(1, RIGHT_TO_LEFT);
      tutorialAnimationView.startHintToastAnimation(state.hintText());
      addView(tutorialAnimationView);
      speakAnnouncement(audialAnnouncement(), /* delayMs= */ 0);
    }

    @Override
    public String audialAnnouncement() {
      return getResources().getString(R.string.swipe_right_announcement);
    }

    @Override
    public String audialAnnouncementRepeated() {
      return getResources().getString(R.string.swipe_right_announcement);
    }

    @Override
    public String hintText() {
      return getResources().getString(R.string.instruction_swipe_right);
    }

    @Override
    public String resultText() {
      return getResources().getString(R.string.result_swipe_right);
    }

    @Override
    public boolean isActionCompleted() {
      return actionCompleted;
    }

    @Override
    public void onUtteranceCompleted() {
      if (actionCompleted) {
        actionCompleted = false;
        switchNextState(nextState(), /* delay= */ 0);
      } else {
        speakAnnouncement(audialAnnouncementRepeated(), /* delayMs= */ 0);
      }
    }

    @Override
    public void onBrailleProduced(BrailleCharacter brailleCharacter) {
      if (actionCompleted) {
        return;
      }

      BrailleImeVibrator.getInstance(context).vibrate(VibrationType.BRAILLE_COMMISSION);
      // Read out the corresponding announcement.
      speakBrailleCharacter(brailleCharacter, /* delayMs= */ 0);
    }

    @Override
    public void onSwipeProduced(Swipe swipe) {
      if (actionCompleted) {
        return;
      }

      int touchCount = swipe.getTouchCount();
      Direction direction = swipe.getDirection();
      if (direction == Direction.LEFT && touchCount == 1) {
        tutorialAnimationView.stopSwipeAnimation();
        tutorialAnimationView.startActionResultAnimation(resultText());
        tutorialCallback.onPlaySound(R.raw.volume_beep);
        // Wait a second for playing sound and then speak the post-action announcement.
        speakAnnouncement(getResources().getString(R.string.result_swipe_right), BEEP_DELAY_MS);
        actionCompleted = true;
      } else {
        // Read out the corresponding announcement.
        speakSwipeEvent(swipe);
      }
      vibrateForSwipeGestures(swipe);
    }
  }

  class SwipeDown2Fingers implements TutorialState {
    private boolean actionCompleted;

    @Override
    public State getCurrentState() {
      return State.SWIPE_DOWN_2_FINGERS;
    }

    @Override
    public TutorialState nextState() {
      return swipeDown3Fingers;
    }

    @Override
    public void loadView() {
      addView(inputView);
      tutorialAnimationView.reset();
      tutorialAnimationView.startSwipeAnimation(2, TOP_TO_BOTTOM);
      tutorialAnimationView.startHintToastAnimation(state.hintText());
      addView(tutorialAnimationView);
      speakAnnouncement(audialAnnouncement(), /* delayMs= */ 0);
    }

    @Override
    public String audialAnnouncement() {
      return getResources().getString(R.string.swipe_down_2_fingers_announcement);
    }

    @Override
    public String audialAnnouncementRepeated() {
      return getResources().getString(R.string.swipe_down_2_fingers_announcement);
    }

    @Override
    public String hintText() {
      return getResources().getString(R.string.instruction_swipe_down_2_fingers);
    }

    @Override
    public String resultText() {
      return getResources().getString(R.string.result_swipe_down_2_fingers);
    }

    @Override
    public boolean isActionCompleted() {
      return actionCompleted;
    }

    @Override
    public void onUtteranceCompleted() {
      if (actionCompleted) {
        actionCompleted = false;
        switchNextState(nextState(), /* delay= */ 0);
      } else {
        speakAnnouncement(audialAnnouncementRepeated(), /* delayMs= */ 0);
      }
    }

    @Override
    public void onBrailleProduced(BrailleCharacter brailleCharacter) {
      if (actionCompleted) {
        return;
      }

      BrailleImeVibrator.getInstance(context).vibrate(VibrationType.BRAILLE_COMMISSION);
      // Read out the corresponding announcement.
      speakBrailleCharacter(brailleCharacter, /* delayMs= */ 0);
    }

    @Override
    public void onSwipeProduced(Swipe swipe) {
      if (actionCompleted) {
        return;
      }

      int touchCount = swipe.getTouchCount();
      Direction direction = swipe.getDirection();
      if (direction == Direction.DOWN && touchCount == 2) {
        tutorialAnimationView.stopSwipeAnimation();
        tutorialAnimationView.startActionResultAnimation(resultText());
        tutorialCallback.onPlaySound(R.raw.volume_beep);
        // Wait a second for playing sound and then speak the post-action announcement.
        speakAnnouncement(
            getResources().getString(R.string.swipe_down_2_fingers_completed_announcement),
            BEEP_DELAY_MS);
        actionCompleted = true;
      } else {
        // Read out the corresponding announcement.
        speakSwipeEvent(swipe);
      }
      vibrateForSwipeGestures(swipe);
    }
  }

  class SwipeDown3Fingers implements TutorialState {
    private boolean actionCompleted;

    @Override
    public State getCurrentState() {
      return State.SWIPE_DOWN_3_FINGERS;
    }

    @Override
    public TutorialState nextState() {
      return swipeUp3Fingers;
    }

    @Override
    public void loadView() {
      addView(inputView);
      tutorialAnimationView.reset();
      tutorialAnimationView.startSwipeAnimation(3, TOP_TO_BOTTOM);
      tutorialAnimationView.startHintToastAnimation(state.hintText());
      addView(tutorialAnimationView);
      speakAnnouncement(audialAnnouncement(), /* delayMs= */ 0);
    }

    @Override
    public String audialAnnouncement() {
      return getResources().getString(R.string.swipe_down_3_fingers_announcement);
    }

    @Override
    public String audialAnnouncementRepeated() {
      return getResources().getString(R.string.swipe_down_3_fingers_announcement);
    }

    @Override
    public String hintText() {
      return getResources().getString(R.string.instruction_swipe_down_3_fingers);
    }

    @Override
    public String resultText() {
      return getResources().getString(R.string.result_swipe_down_3_fingers);
    }

    @Override
    public boolean isActionCompleted() {
      return actionCompleted;
    }

    @Override
    public void onUtteranceCompleted() {
      if (actionCompleted) {
        actionCompleted = false;
        switchNextState(nextState(), /* delay= */ 0);
      } else {
        speakAnnouncement(audialAnnouncementRepeated(), /* delayMs= */ 0);
      }
    }

    @Override
    public void onBrailleProduced(BrailleCharacter brailleCharacter) {
      if (actionCompleted) {
        return;
      }

      BrailleImeVibrator.getInstance(context).vibrate(VibrationType.BRAILLE_COMMISSION);
      speakBrailleCharacter(brailleCharacter, /* delayMs= */ 0);
    }

    @Override
    public void onSwipeProduced(Swipe swipe) {
      if (actionCompleted) {
        return;
      }

      int touchCount = swipe.getTouchCount();
      Direction direction = swipe.getDirection();
      if (direction == Direction.DOWN && touchCount == 3) {
        tutorialAnimationView.stopSwipeAnimation();
        tutorialAnimationView.startActionResultAnimation(resultText());
        tutorialCallback.onPlaySound(R.raw.volume_beep);
        // Wait a second for playing sound and then speak the post-action announcement.
        speakAnnouncement(
            getResources().getString(R.string.swipe_down_3_fingers_completed_announcement),
            BEEP_DELAY_MS);
        actionCompleted = true;
      } else {
        speakSwipeEvent(swipe);
      }
      vibrateForSwipeGestures(swipe);
    }
  }

  class SwipeUp3Fingers implements TutorialState {
    private boolean actionCompleted;

    @Override
    public State getCurrentState() {
      return State.SWIPE_UP_3_FINGERS;
    }

    @Override
    public TutorialState nextState() {
      return contextMenuOpened;
    }

    @Override
    public void loadView() {
      actionCompleted = false;
      addView(inputView);
      tutorialAnimationView.reset();
      tutorialAnimationView.startSwipeAnimation(3, BOTTOM_TO_TOP);
      tutorialAnimationView.startHintToastAnimation(state.hintText());
      addView(tutorialAnimationView);
      speakAnnouncement(audialAnnouncement(), /* delayMs= */ 0);
    }

    @Override
    public String audialAnnouncement() {
      return getResources().getString(R.string.swipe_up_3_fingers_announcement);
    }

    @Override
    public String audialAnnouncementRepeated() {
      return getResources().getString(R.string.swipe_up_3_fingers_announcement);
    }

    @Override
    public String hintText() {
      return getResources().getString(R.string.instruction_swipe_up_3_fingers);
    }

    @Override
    public void onUtteranceCompleted() {
      if (!actionCompleted) {
        speakAnnouncement(audialAnnouncementRepeated(), /* delayMs= */ 0);
      }
    }

    @Override
    public void onBrailleProduced(BrailleCharacter brailleCharacter) {
      if (actionCompleted) {
        return;
      }

      BrailleImeVibrator.getInstance(context).vibrate(VibrationType.BRAILLE_COMMISSION);
      speakBrailleCharacter(brailleCharacter, /* delayMs= */ 0);
    }

    @Override
    public void onSwipeProduced(Swipe swipe) {
      if (actionCompleted) {
        return;
      }

      int touchCount = swipe.getTouchCount();
      Direction direction = swipe.getDirection();
      if (direction == Direction.UP && touchCount == 3) {
        switchNextState(nextState(), /* delay= */ 0);
        actionCompleted = true;
      } else if (direction == Direction.RIGHT && touchCount == 3) {
        // Braille keyboard view is forced to be in landscape. When device is portrait and user
        // swipes upward, for keyboard view, it's swipe rightward.
        if (OrientationMonitor.getInstance().getCurrentOrientation() == Orientation.PORTRAIT) {
          switchNextState(nextState(), /* delay= */ 0);
          actionCompleted = true;
        }
      } else {
        // Read out the corresponding announcement.
        speakSwipeEvent(swipe);
      }
      vibrateForSwipeGestures(swipe);
    }
  }

  class ContextMenuOpened implements TutorialState {

    @Override
    public State getCurrentState() {
      return State.CONTEXT_MENU_OPENED;
    }

    @Override
    public TutorialState nextState() {
      return tutorialFinished;
    }

    @Override
    public String audialAnnouncement() {
      return getResources().getString(R.string.open_context_menu_announcement);
    }

    @Override
    public String audialAnnouncementRepeated() {
      return getResources().getString(R.string.open_context_menu_announcement);
    }

    @Override
    public void onUtteranceCompleted() {
      speakAnnouncement(audialAnnouncementRepeated(), /* delayMs= */ 0);
    }

    @Override
    public void loadView() {
      addView(inputView);
      if (!contextMenuDialog.isShowing()) {
        openContextMenu();
      }
    }
  }

  @SuppressWarnings("ClassCanBeStatic")
  class TutorialFinished implements TutorialState {
    @Override
    public State getCurrentState() {
      return State.NONE;
    }

    @Override
    public TutorialState nextState() {
      return this;
    }

    @Override
    public void loadView() {}
  }

  /** A callback for Tutorial event. */
  public interface TutorialCallback {
    void onBrailleImeActivated();

    void onBrailleImeInactivated();

    void onAudialAnnounce(
        String announcement, int delayMs, UtteranceCompleteRunnable utteranceCompleteRunnable);

    void onPlaySound(int resId, int delayMs);

    default void onPlaySound(int resId) {
      onPlaySound(resId, 0);
    }

    void onSwitchToNextInputMethod();

    void onLaunchSettings();

    void onTutorialFinished();

    void onRestartTutorial();

    void onSwitchContractedMode();

    void onTypingLanguageChanged();

    void registerOrientationChange(OrientationMonitor.Callback callBack);

    void unregisterOrientationChange();
  }

  static final int ROTATION_270 = 270;
  static final int ROTATION_90 = 90;
  static final int ROTATION_180 = 180;
  static final int ROTATION_0 = 0;
  // Exceeding this number of free interactions triggers a replay of the current tutorial
  // instruction upon subsequent interactions.
  public static final int FREE_INTERACTIONS_MAX = 5;

  // Wait this amount of time before re-announcing instructions for a task for the user (such as
  // swipe-leftward) that has not yet been completed.
  public static final int AUDIAL_ANNOUNCEMENT_IDLE_MS = 5000;

  // Wait this amount of time (estimated) for external speech gets completed before re-announcing
  // instructions for a task for the user (such as swipe-leftward) that has not yet been completed.
  public static final int AUDIAL_ANNOUNCEMENT_IDLE_FOR_EXTERNAL_SPEECH_MS = 5000;

  private static final int ANIMATION_DURATION_MS = 600;
  private static final int INPUT_VIEW_ANIMATION_DURATION_MS = 500;
  private static final int FLASHING_ANIMATION_DELAY_MS = 1000;
  private static final int BEEP_DELAY_MS = 1000;
  private static final String TAG = TutorialView.class.getSimpleName();

  private final Context context;
  private final Intro intro;
  private final RotateOrientation rotateOrientation;
  private final RotateOrientationContinue rotateOrientationContinue;
  private final TypeLetterA typeLetterA;
  private final TypeLetterBCD tapLetterBCD;
  private final SwipeLeft swipeLeft;
  private final SwipeRight swipeRight;
  private final SwipeDown2Fingers swipeDown2Fingers;
  private final SwipeDown3Fingers swipeDown3Fingers;
  private final SwipeUp3Fingers swipeUp3Fingers;
  private final ContextMenuOpened contextMenuOpened;
  private final TutorialFinished tutorialFinished;
  private final HoldSixFingers holdSixFingers;

  private TutorialState state;
  private int orientation;
  private final TutorialCallback tutorialCallback;
  private final BrailleInputView inputView;
  private final DotBlockView dotBlockView;
  private DotsFlashingAnimationView dotsFlashingAnimationView;
  private final TutorialAnimationView tutorialAnimationView;
  private final GestureDetector gestureDetector;
  private final ContextMenuDialog contextMenuDialog;

  private final Translator translator;
  private final Handler handler = new Handler();

  // This is used to prevent handling utterance completed callback once ViewContainer is
  // deactivated.
  private boolean isStopped;

  // Whether or not to play announcement of clicking Braille dot or swipe event. Exploration
  // feedbacks will be skipped if the instruction is speaking.
  private boolean allowExploration;
  private boolean isTabletop;
  private int numberOfInteractionsPerState;
  private final AtomicInteger instructionSpeechId = new AtomicInteger();
  private final boolean reverseDots;

  public TutorialView(
      Context context, TutorialCallback tutorialCallback, Size screenSize, boolean reverseDots) {
    super(context);
    this.context = context;
    this.tutorialCallback = tutorialCallback;
    this.reverseDots = reverseDots;
    orientation = getResources().getConfiguration().orientation;
    intro = new Intro();
    rotateOrientation = new RotateOrientation();
    rotateOrientationContinue = new RotateOrientationContinue();
    typeLetterA = new TypeLetterA();
    tapLetterBCD = new TypeLetterBCD();
    swipeLeft = new SwipeLeft();
    swipeRight = new SwipeRight();
    swipeDown2Fingers = new SwipeDown2Fingers();
    swipeDown3Fingers = new SwipeDown3Fingers();
    swipeUp3Fingers = new SwipeUp3Fingers();
    contextMenuOpened = new ContextMenuOpened();
    tutorialFinished = new TutorialFinished();
    holdSixFingers = new HoldSixFingers();
    state = tutorialFinished;

    gestureDetector = new GestureDetector(context, doubleTapDetector);
    inputView =
        new BrailleInputView(context, inputPlaneCallback, screenSize, /* isTutorial= */ true);
    isTabletop = !Utils.isPhoneSizedDevice(context.getResources());
    inputView.setTableMode(isTabletop);
    dotBlockView = new DotBlockView(context, orientation, isTabletop);
    tutorialAnimationView = new TutorialAnimationView(context, orientation, screenSize, isTabletop);
    contextMenuDialog = new ContextMenuDialog(context, contextMenuDialogCallback);
    contextMenuDialog.setTutorialMode(true);

    Code code = UserPreferences.readTranslateCode(context);
    translator =
        UserPreferences.readTranslatorFactory().create(context, code, /* contractedMode= */ false);
  }

  @VisibleForTesting
  void setTableMode(boolean isTabletop) {
    this.isTabletop = isTabletop;
  }

  @Override
  public void onOrientationChanged(int orientation, Size screenSize) {
    this.orientation = orientation;
    inputView.onOrientationChanged(orientation, screenSize);
    dotBlockView.onOrientationChanged(orientation, screenSize);
    if (dotsFlashingAnimationView != null) {
      dotsFlashingAnimationView.updateDotTarget(
          getDotTargetsForBrailleCharacter(dotsFlashingAnimationView.getFlashingCharacter()));
      dotsFlashingAnimationView.onOrientationChanged(orientation, screenSize);
    }
    tutorialAnimationView.onOrientationChanged(orientation, screenSize);
    if (state.isActionCompleted()) {
      // Wait for utterance to complete, which will trigger next action.
      return;
    }
    reloadView();
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent event) {
    gestureDetector.onTouchEvent(event);
    return false;
  }

  public State getTutorialState() {
    return state.getCurrentState();
  }

  public void setTutorialState(State state) {
    switch (state) {
      case NONE:
        // Should not happen.
        break;
      case INTRO:
        this.state = intro;
        break;
      case ROTATE_ORIENTATION:
        this.state = rotateOrientation;
        break;
      case ROTATE_ORIENTATION_CONTINUE:
        this.state = rotateOrientationContinue;
        break;
      case TYPE_LETTER_A:
        this.state = typeLetterA;
        break;
      case TYPE_LETTER_BCD:
        this.state = tapLetterBCD;
        break;
      case SWIPE_LEFT:
        this.state = swipeLeft;
        break;
      case SWIPE_RIGHT:
        this.state = swipeRight;
        break;
      case SWIPE_DOWN_2_FINGERS:
        this.state = swipeDown2Fingers;
        break;
      case SWIPE_DOWN_3_FINGERS:
        this.state = swipeDown3Fingers;
        break;
      case SWIPE_UP_3_FINGERS:
        this.state = swipeUp3Fingers;
        break;
      case CONTEXT_MENU_OPENED:
        this.state = contextMenuOpened;
        break;
      case HOLD_6_FINGERS:
        this.state = holdSixFingers;
        break;
    }
  }

  public void switchNextState(State state, long delay) {
    setTutorialState(state);
    switchNextState(this.state, delay);
  }

  public void tearDown() {
    isStopped = true;
    handler.removeCallbacksAndMessages(/* token= */ null);
    contextMenuDialog.dismiss();
  }

  @VisibleForTesting
  void onUtteranceCompleted() {
    if (isStopped) {
      return;
    }
    handler.removeCallbacksAndMessages(/* token= */ null);
    if (state.isActionCompleted()) {
      state.onUtteranceCompleted();
    } else {
      // Allow exploration feedback when the instruction is idle.
      allowExploration = true;
      handler.postDelayed(() -> state.onUtteranceCompleted(), AUDIAL_ANNOUNCEMENT_IDLE_MS);
    }
  }

  private void switchNextState(TutorialState state, long delay) {
    postDelayed(
        () -> {
          BrailleImeLog.logD(TAG, "Switch to " + state.getCurrentState().name());
          handler.removeCallbacksAndMessages(/* token= */ null);
          this.state = state;
          numberOfInteractionsPerState = 0;
          reloadView();
        },
        delay);
  }

  private void reloadView() {
    removeAllViews();
    state.loadView();
  }

  /**
   * Speaks the events of Braille dots and swipe gestures. If the user has tried a fixed number of
   * interactions, speak the instruction.
   */
  private void speakUserEventAnnouncement(String textToSpeak, int delayMs) {
    if (!allowExploration) {
      return;
    }

    if (numberOfInteractionsPerState >= FREE_INTERACTIONS_MAX) {
      speakAnnouncement(
          context.getString(
              R.string.continue_tutorial_announcement, state.audialAnnouncementRepeated()),
          /* delayMs= */ 0);
    } else {
      tutorialCallback.onAudialAnnounce(
          textToSpeak, delayMs, /* utteranceCompleteRunnable= */ null);
      numberOfInteractionsPerState++;
    }
  }

  /**
   * Speaks the instructions. Repeat the instruction or go to next step once callback is invoked.
   */
  private void speakAnnouncement(String textToSpeak, int delayMs) {
    allowExploration = false;
    handler.removeCallbacksAndMessages(/* token= */ null);
    int speechId = instructionSpeechId.incrementAndGet();
    tutorialCallback.onAudialAnnounce(
        textToSpeak,
        delayMs,
        status -> {
          // When incoming speech uses {@link SpeechController.QUEUE_MODE_INTERRUPT}, the
          // sequence is
          // 1. second speech onUtteranceStarted
          // 2. first speech onUtteranceCompleted
          // 3. second speech is playing. A few seconds later.
          // 4. second speech onUtteranceCompleted.
          //
          // Put a condition here so that when first speech onUtteranceCompleted, we don't start
          // countdown. Otherwise, it will report onIdle() but actually second speech is playing.
          if (status == SpeechController.STATUS_INTERRUPTED) {
            if (speechId == instructionSpeechId.get()) {
              // Announcement is interrupted by non-tutorial speech. Go to next step immediately if
              // action is completed. Otherwise, repeat the instruction in a certain time.
              int postDelayedTime =
                  state.isActionCompleted() ? 0 : AUDIAL_ANNOUNCEMENT_IDLE_FOR_EXTERNAL_SPEECH_MS;
              handler.postDelayed(() -> onUtteranceCompleted(), postDelayedTime);
            }
            return;
          }
          onUtteranceCompleted();
        });
  }

  private void speakSwipeEvent(Swipe swipe) {
    String action = null;
    int touchCount = swipe.getTouchCount();
    Direction direction = swipe.getDirection();
    if (direction == Direction.DOWN && touchCount == 2) {
      action = getResources().getString(R.string.perform_hide_keyboard_announcement);

    } else if (direction == Direction.DOWN && touchCount == 3) {
      action = getResources().getString(R.string.perform_switch_keyboard_announcement);

    } else if (direction == Direction.UP && touchCount == 2) {
      action = getResources().getString(R.string.perform_submit_text_announcement);

    } else if (direction == Direction.LEFT && touchCount == 1) {
      action = getResources().getString(R.string.perform_add_space_announcement);

    } else if (direction == Direction.LEFT && touchCount == 2) {
      action = getResources().getString(R.string.perform_add_new_line_announcement);

    } else if (direction == Direction.RIGHT && touchCount == 1) {
      action = getResources().getString(R.string.perform_delete_letter_announcement);

    } else if (direction == Direction.RIGHT && touchCount == 2) {
      action = getResources().getString(R.string.perform_delete_word_announcement);
    }

    if (action != null) {
      speakUserEventAnnouncement(action, /* delayMs= */ 0);
    }
  }

  private void vibrateForSwipeGestures(Swipe swipe) {
    int touchCount = swipe.getTouchCount();
    Direction direction = swipe.getDirection();
    if (direction == Direction.DOWN && touchCount == 2) {
      // Close keyboard.
      BrailleImeVibrator.getInstance(context).vibrate(VibrationType.OTHER_GESTURES);
    } else if (direction == Direction.DOWN && touchCount == 3) {
      // Switch keyboard.
      BrailleImeVibrator.getInstance(context).vibrate(VibrationType.OTHER_GESTURES);
    } else if (direction == Direction.UP && touchCount == 2) {
      // Submit.
      BrailleImeVibrator.getInstance(context).vibrate(VibrationType.OTHER_GESTURES);
    } else if (direction == Direction.LEFT && touchCount == 1) {
      // Space.
      BrailleImeVibrator.getInstance(context).vibrate(VibrationType.SPACE_OR_DELETE);
    } else if (direction == Direction.LEFT && touchCount == 2) {
      // Newline.
      BrailleImeVibrator.getInstance(context).vibrate(VibrationType.NEWLINE_OR_DELETE_WORD);
    } else if (direction == Direction.RIGHT && touchCount == 1) {
      // Delete.
      BrailleImeVibrator.getInstance(context).vibrate(VibrationType.SPACE_OR_DELETE);
    } else if (direction == Direction.RIGHT && touchCount == 2) {
      // Delete word.
      BrailleImeVibrator.getInstance(context).vibrate(VibrationType.NEWLINE_OR_DELETE_WORD);
    } else if (swipe.getDirection() == Direction.UP && swipe.getTouchCount() == 3) {
      // Open context menu.
      BrailleImeVibrator.getInstance(context).vibrate(VibrationType.OTHER_GESTURES);
    } else if (swipe.getDirection() == Direction.RIGHT && swipe.getTouchCount() == 3) {
      // Open context menu in portrait. Braille keyboard view is forced to be in landscape. When
      // device is portrait and user swipes upward, for keyboard view, it's swipe rightward.
      if (OrientationMonitor.getInstance().getCurrentOrientation() == Orientation.PORTRAIT) {
        BrailleImeVibrator.getInstance(context).vibrate(VibrationType.OTHER_GESTURES);
      }
    }
  }

  private void speakBrailleCharacter(BrailleCharacter brailleCharacter, int delayMs) {
    String textToSpeak = getTextToSpeak(TutorialView.this.getResources(), brailleCharacter);
    if (TextUtils.isEmpty(textToSpeak)) {
      textToSpeak = translator.translateToPrint(new BrailleWord(brailleCharacter));
    }
    if (TextUtils.isEmpty(textToSpeak) || !BrailleTranslateUtils.isPronounceable(textToSpeak)) {
      textToSpeak = BrailleTranslateUtils.getDotsText(getResources(), brailleCharacter);
    }
    speakUserEventAnnouncement(textToSpeak, delayMs);
  }

  private void openContextMenu() {
    contextMenuDialog.show(TutorialView.this);
    speakAnnouncement(
        getResources().getString(R.string.open_context_menu_announcement), /* delayMs= */ 0);
  }

  private List<DotTarget> getDotTargetsForBrailleCharacter(BrailleCharacter brailleCharacter) {
    return inputView.getDotTargets().stream()
        .filter(dotTarget -> brailleCharacter.isDotNumberOn(dotTarget.getDotNumber()))
        .collect(toList());
  }

  private final BrailleInputView.Callback inputPlaneCallback =
      new BrailleInputView.Callback() {
        @Override
        public void onSwipeProduced(Swipe swipe) {
          if (swipe.getDirection() == Direction.UP && swipe.getTouchCount() == 3) {
            openContextMenu();
          } else if (swipe.getDirection() == Direction.RIGHT && swipe.getTouchCount() == 3) {
            // Braille keyboard view is forced to be in landscape. When device is portrait and user
            // swipes upward, for keyboard view, it's swipe rightward.
            if (OrientationMonitor.getInstance().getCurrentOrientation() == Orientation.PORTRAIT) {
              openContextMenu();
            }
          }
          vibrateForSwipeGestures(swipe);
          state.onSwipeProduced(swipe);
        }

        @Override
        public boolean isHoldRecognized(int pointersHeldCount) {
          // For calibration.
          return pointersHeldCount >= 5 || pointersHeldCount == 3;
        }

        @Override
        public void onHoldProduced(int pointersHeldCount) {}

        @Override
        public String onBrailleProduced(BrailleCharacter brailleCharacter) {
          BrailleImeVibrator.getInstance(context).vibrate(VibrationType.BRAILLE_COMMISSION);
          state.onBrailleProduced(brailleCharacter);
          return null;
        }

        @Override
        public void onCalibration(FingersPattern fingersPattern) {
          state.onCalibration(fingersPattern);
        }
      };

  private final ContextMenuDialog.Callback contextMenuDialogCallback =
      new ContextMenuDialog.Callback() {
        @Override
        public void onDialogHidden() {
          tutorialCallback.onBrailleImeActivated();
          if (state == contextMenuOpened) {
            switchNextState(swipeUp3Fingers, /* delay= */ 0);
          } else {
            // Repeat audial announcement when back to tutorial.
            state.onUtteranceCompleted();
          }
        }

        @Override
        public void onDialogShown() {
          tutorialCallback.onBrailleImeInactivated();
        }

        @Override
        public void onLaunchSettings() {
          tutorialCallback.onLaunchSettings();
          switchNextState(tutorialFinished, /* delay= */ 0);
          tutorialCallback.onTutorialFinished();
        }

        @Override
        public void onSwitchContractedMode() {
          // Keep using uncontracted mode in tutorial but update the setting.
          tutorialCallback.onSwitchContractedMode();
          if (state == contextMenuOpened) {
            switchNextState(swipeUp3Fingers, /* delay= */ 0);
          }
        }

        @Override
        public void onTutorialOpen() {}

        @Override
        public void onTutorialClosed() {
          switchNextState(tutorialFinished, /* delay= */ 0);
          speakAnnouncement(
              getResources().getString(R.string.finish_tutorial_announcement), /* delayMs= */ 0);
          tutorialCallback.onTutorialFinished();
        }

        @Override
        public void onTypingLanguageChanged() {
          tutorialCallback.onTypingLanguageChanged();
        }

        @Override
        public boolean isLanguageContractionSupported() {
          return UserPreferences.readTranslateCode(context).isSupportsContracted();
        }
      };

  private final SimpleOnGestureListener doubleTapDetector =
      new SimpleOnGestureListener() {
        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
          state.onDoubleTap();
          return false;
        }
      };

  @VisibleForTesting
  void testing_setTutorialState(TutorialState state) {
    this.state = state;
  }

  @VisibleForTesting
  TutorialState testing_getTutorialState() {
    return state;
  }

  @VisibleForTesting
  BrailleInputView testing_getInputView() {
    return inputView;
  }

  @VisibleForTesting
  BrailleInputView.Callback testing_getBrailleInputViewCallback() {
    return inputPlaneCallback;
  }

  @VisibleForTesting
  RotateOrientationContinue testing_getRotateOrientationContinue() {
    return rotateOrientationContinue;
  }
}
