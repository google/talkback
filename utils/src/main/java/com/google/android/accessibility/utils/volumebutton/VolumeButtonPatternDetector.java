/*
 * Copyright 2015 Google Inc.
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

package com.google.android.accessibility.utils.volumebutton;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import androidx.annotation.IntDef;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import java.util.ArrayList;
import java.util.List;

/**
 * This class listens to key events, and when it detects certain key combinations, it creates
 * key-combination events. This class contains various VolumeButtonPatternMatcher subclasses.
 */
// TODO :  Simplify VolumeButtonPatternMatcher subclasses.
public class VolumeButtonPatternDetector {

  /** Constants denoting different sequences of pushing the volume buttons. */
  @IntDef({
    SHORT_PRESS_PATTERN,
    LONG_PRESS_PATTERN,
    SHORT_DOUBLE_PRESS_PARTTERN,
    TWO_BUTTONS_LONG_PRESS_PATTERN,
    TWO_BUTTONS_THREE_PRESS_PATTERN
  })
  public @interface ButtonSequence {}

  public static final int SHORT_PRESS_PATTERN = 1;
  public static final int LONG_PRESS_PATTERN = 2;
  public static final int TWO_BUTTONS_LONG_PRESS_PATTERN = 3;
  public static final int TWO_BUTTONS_THREE_PRESS_PATTERN = 4;
  public static final int SHORT_DOUBLE_PRESS_PARTTERN = 5;

  /** Constants denoting different combinations of the volume buttons. */
  @IntDef({VOLUME_UP, VOLUME_DOWN, TWO_BUTTONS, PLAY_PAUSE})
  public @interface ButtonsUsed {}

  public static final int VOLUME_UP = KeyEvent.KEYCODE_VOLUME_UP;
  public static final int VOLUME_DOWN = KeyEvent.KEYCODE_VOLUME_DOWN;
  public static final int PLAY_PAUSE = KeyEvent.KEYCODE_HEADSETHOOK;
  public static final int TWO_BUTTONS = 1;

  private static final long LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
  private static final int CHECK_MATCHERS_MESSAGE = 1;

  private OnPatternMatchListener mListener;
  private final List<VolumeButtonPatternMatcher> patternMatchers;

  private final Handler mHandler =
      new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message message) {
          if (message.what == CHECK_MATCHERS_MESSAGE) {
            checkMatchers();
          }
        }
      };

  public VolumeButtonPatternDetector(Context context) {
    patternMatchers = new ArrayList<>();
    patternMatchers.add(new SingleVolumeButtonPressPatternMatcher(SHORT_PRESS_PATTERN, VOLUME_UP));
    patternMatchers.add(
        new SingleVolumeButtonPressPatternMatcher(SHORT_PRESS_PATTERN, VOLUME_DOWN));
    patternMatchers.add(new SingleVolumeButtonPressPatternMatcher(SHORT_PRESS_PATTERN, PLAY_PAUSE));
    patternMatchers.add(new SingleVolumeButtonPressPatternMatcher(LONG_PRESS_PATTERN, VOLUME_UP));
    patternMatchers.add(new SingleVolumeButtonPressPatternMatcher(LONG_PRESS_PATTERN, VOLUME_DOWN));
    patternMatchers.add(new SingleVolumeButtonPressPatternMatcher(LONG_PRESS_PATTERN, PLAY_PAUSE));
    patternMatchers.add(new DoubleVolumeButtonLongPressPatternMatcher());
    patternMatchers.add(new DoubleVolumeButtonThreeShortPressPatternMatcher());
  }

  public boolean onKeyEvent(KeyEvent keyEvent) {
    if (!isFromVolumeKey(keyEvent.getKeyCode())) {
      return false;
    }

    processKeyEvent(keyEvent);
    checkMatchers();

    mHandler.removeMessages(CHECK_MATCHERS_MESSAGE);
    mHandler.sendEmptyMessageDelayed(CHECK_MATCHERS_MESSAGE, LONG_PRESS_TIMEOUT);
    return true;
  }

  private static boolean isFromVolumeKey(int keyCode) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_VOLUME_DOWN:
      case KeyEvent.KEYCODE_VOLUME_UP:
      case KeyEvent.KEYCODE_HEADSETHOOK:
        return true;
      default:
        return false;
    }
  }

  private void processKeyEvent(KeyEvent event) {
    for (VolumeButtonPatternMatcher matcher : patternMatchers) {
      matcher.onKeyEvent(event);
    }
  }

  private void checkMatchers() {
    for (VolumeButtonPatternMatcher matcher : patternMatchers) {
      if (matcher.checkMatch()) {
        EventId eventId =
            Performance.getInstance().onVolumeKeyComboEventReceived(matcher.getPatternCode());
        notifyPatternMatched(matcher.getPatternCode(), matcher.getButtonCombination(), eventId);
        matcher.clear();
      }
    }
  }

  public void clearState() {
    for (VolumeButtonPatternMatcher matcher : patternMatchers) {
      matcher.clear();
    }
  }

  public void setOnPatternMatchListener(OnPatternMatchListener listener) {
    mListener = listener;
  }

  private void notifyPatternMatched(
      @ButtonSequence int patternCode, @ButtonsUsed int buttonCombination, EventId eventId) {
    if (mListener != null) {
      mListener.onPatternMatched(patternCode, buttonCombination, eventId);
    }
  }

  public interface OnPatternMatchListener {
    public void onPatternMatched(
        @ButtonSequence int patternCode, @ButtonsUsed int buttonCombination, EventId eventId);
  }
}
