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

package com.google.android.accessibility.talkback.actor;

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import androidx.annotation.Nullable;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.talkback.DimmingOverlayView;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Feedback.Speech;
import com.google.android.accessibility.talkback.OrientationMonitor;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.gesture.GestureShortcutMapping;
import com.google.android.accessibility.utils.ScreenMonitor;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.widget.DialogUtils;
import java.util.concurrent.TimeUnit;

/** Manages UI and state related to dimming the screen */
public class DimScreenActor implements OrientationMonitor.OnOrientationChangedListener {

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Constants

  private static final float MAX_DIM_AMOUNT = 0.9f;
  private static final float MIN_BRIGHTNESS = 0.1f;
  private static final float MAX_BRIGHTNESS = 1.0f;

  private static final int START_DIMMING_MESSAGE = 1;
  private static final int UPDATE_TIMER_MESSAGE = 2;

  private static final int INSTRUCTION_VISIBLE_SECONDS = 180;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Inner class for actor-state reader

  /** Read-only interface for actor-state data. */
  public class State {
    public boolean isDimmingEnabled() {
      return DimScreenActor.this.isDimmingEnabled();
    }

    public boolean isInstructionDisplayed() {
      return DimScreenActor.this.isInstructionDisplayed();
    }
  }

  public final State state = new State();

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member data

  private Context service;
  private Pipeline.FeedbackReturner pipeline;
  private SharedPreferences prefs;
  private boolean isDimmed;
  private boolean stopFeedback;
  private WindowManager windowManager;
  @Nullable private LayoutParams viewParams;
  @Nullable private DimmingOverlayView view;
  @Nullable private DimScreenDialog dimScreenDialog;
  private int currentInstructionVisibleTime;
  private boolean isInstructionDisplayed;
  private final GestureShortcutMapping gestureShortcutMapping;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  private final Handler dimmingHandler =
      new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message message) {
          switch (message.what) {
            case START_DIMMING_MESSAGE:
              currentInstructionVisibleTime = INSTRUCTION_VISIBLE_SECONDS;
              isInstructionDisplayed = true;
              sendEmptyMessage(UPDATE_TIMER_MESSAGE);
              break;
            case UPDATE_TIMER_MESSAGE:
              currentInstructionVisibleTime--;
              if (currentInstructionVisibleTime > 0) {
                updateText(currentInstructionVisibleTime);
                sendEmptyMessageDelayed(UPDATE_TIMER_MESSAGE, TimeUnit.SECONDS.toMillis(1));
              } else {
                hideInstructionAndTurnOnDimming();
              }
              break;
            default: // fall out
          }
        }
      };

  /**
   * Checks if dim/brighten screen is supported by the platform.
   *
   * <p>Note: This function just checks API restrictions. Availability of dim/brighten screen is
   * also dependent on the current device lock state.
   *
   * @see #isSupported(TalkBackService).
   */
  public static boolean isSupportedbyPlatform(TalkBackService service) {
    // Screen dimming is disabled on Jasper because there is no easy way to exit this mode since
    // Talkback cannot capture volume button key presses on this platform.
    @Compositor.Flavor int compositorFlavor = service.getCompositorFlavor();
    return compositorFlavor != Compositor.FLAVOR_ARC
        && compositorFlavor != Compositor.FLAVOR_JASPER;
  }

  /**
   * Decides whether to support dim and brighten screen functionality. Dim/brighten screen support
   * depends on API level and current device lock state.
   */
  public static boolean isSupported(TalkBackService service) {
    if (!isSupportedbyPlatform(service)) {
      return false;
    }
    return !ScreenMonitor.isDeviceLocked(service);
  }

  public DimScreenActor(TalkBackService service, GestureShortcutMapping gestureShortcutMapping) {
    this.service = service;
    this.gestureShortcutMapping = gestureShortcutMapping;
    prefs = SharedPreferencesUtils.getSharedPreferences(service);
    stopFeedback = false;

    dimScreenDialog = new DimScreenDialog(service, this);
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  public boolean isDimmingEnabled() {
    return SharedPreferencesUtils.getBooleanPref(
        prefs,
        service.getResources(),
        R.string.pref_dim_when_talkback_enabled_key,
        R.bool.pref_dim_when_talkback_enabled_default);
  }

  /**
   * Turn on screen dimming without setting the shared preference. Used by {@link #resume()} and
   * {@link DimScreenDialog}
   */
  public void makeScreenDim() {
    if (!isDimmed) {
      isDimmed = true;

      initView();
      addExitInstructionView();
      startDimmingCount();
    }

    announceFeedbackAndUsageHintForScreenDimmed();
  }

  private void initView() {
    if (viewParams == null || view == null) {
      windowManager = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);

      viewParams = new LayoutParams();
      viewParams.type = DialogUtils.getDialogType();
      viewParams.flags |= LayoutParams.FLAG_DIM_BEHIND;
      viewParams.flags |= LayoutParams.FLAG_NOT_FOCUSABLE;
      viewParams.flags |= LayoutParams.FLAG_NOT_TOUCHABLE;
      viewParams.flags |= LayoutParams.FLAG_FULLSCREEN;
      viewParams.flags &= ~LayoutParams.FLAG_TURN_SCREEN_ON;
      viewParams.flags &= ~LayoutParams.FLAG_KEEP_SCREEN_ON;
      viewParams.format = PixelFormat.OPAQUE;

      view = new DimmingOverlayView(service);
      view.setInstruction(
          gestureShortcutMapping.getGestureFromActionKey(
              service.getString(R.string.shortcut_value_talkback_breakout)));
      view.setTimerLimit(INSTRUCTION_VISIBLE_SECONDS);
    }

    initCurtainSize();
  }

  private void initCurtainSize() {
    Point point = new Point();
    windowManager.getDefaultDisplay().getRealSize(point);

    viewParams.width = point.x;
    viewParams.height = point.y;
  }

  private void addExitInstructionView() {
    viewParams.dimAmount = MAX_DIM_AMOUNT;
    viewParams.screenBrightness = getDeviceBrightness();
    viewParams.buttonBrightness = MIN_BRIGHTNESS;
    windowManager.addView(view, viewParams);
    view.showText();
  }

  private float getDeviceBrightness() {
    try {
      return android.provider.Settings.System.getInt(
          service.getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS);
    } catch (Settings.SettingNotFoundException e) {
      return MAX_BRIGHTNESS;
    }
  }

  private void startDimmingCount() {
    dimmingHandler.sendEmptyMessage(START_DIMMING_MESSAGE);
  }

  private void updateText(int secondsLeft) {
    view.updateSecondsText(secondsLeft);
  }

  private void hideInstructionAndTurnOnDimming() {
    viewParams.dimAmount = MAX_DIM_AMOUNT;
    viewParams.screenBrightness = MIN_BRIGHTNESS;
    viewParams.buttonBrightness = viewParams.screenBrightness;
    isInstructionDisplayed = false;
    view.hideText();
    updateView();
  }

  public boolean isInstructionDisplayed() {
    return isInstructionDisplayed;
  }

  private void updateView() {
    if (isDimmed) {
      windowManager.removeViewImmediate(view);
      windowManager.addView(view, viewParams);
    }
  }

  public void resume() {
    if (isDimmingEnabled()) {
      makeScreenDim();
    }
  }

  public void suspend() {
    makeScreenBright();
    if (dimScreenDialog != null) {
      dimScreenDialog.cancelDialog();
    }
  }

  public void shutdown() {
    stopFeedback = true;
    suspend();
    viewParams = null;
    view = null;
  }

  /** Turns off screen dimming without setting the shared preference. */
  private void makeScreenBright() {
    if (isDimmed) {
      isDimmed = false;
      isInstructionDisplayed = false;

      windowManager.removeViewImmediate(view);
      dimmingHandler.removeMessages(START_DIMMING_MESSAGE);
      dimmingHandler.removeMessages(UPDATE_TIMER_MESSAGE);
    }
    // Not to feedback since this actor is shut down.
    if (stopFeedback) {
      pipeline.returnFeedback(
          EVENT_ID_UNTRACKED,
          Feedback.speech(service.getString(R.string.screen_brightness_restored)));
    }
  }

  public void disableDimming() {
    makeScreenBright();
    SharedPreferencesUtils.putBooleanPref(
        prefs, service.getResources(), R.string.pref_dim_when_talkback_enabled_key, false);
  }

  public boolean getShouldShowDialogPref() {
    return (dimScreenDialog == null) ? true : dimScreenDialog.getShouldShowDialogPref();
  }

  public boolean enableDimmingAndShowConfirmDialog() {
    if (isDimmingEnabled()) {
      announceFeedbackAndUsageHintForScreenDimmed();
      return false;
    }

    return (dimScreenDialog == null) ? false : dimScreenDialog.showDialogThenDimScreen();
  }

  @Override
  public void onOrientationChanged(int newOrientation) {
    if (isDimmed) {
      initCurtainSize();
      windowManager.removeViewImmediate(view);
      view = new DimmingOverlayView(service);
      view.setTimerLimit(INSTRUCTION_VISIBLE_SECONDS);
      if (isInstructionDisplayed) {
        view.showText();
      } else {
        view.hideText();
      }
      windowManager.addView(view, viewParams);
    }
  }

  private void announceFeedbackAndUsageHintForScreenDimmed() {
    pipeline.returnFeedback(
        EVENT_ID_UNTRACKED,
        Feedback.Part.builder()
            .setSpeech(
                Speech.builder()
                    .setAction(Speech.Action.SPEAK)
                    .setText(service.getString(R.string.screen_dimmed))
                    .build()));
    // The prompt for exiting Hide Screen mode. Need to separate it as a single Speech, instead of a
    // hint, because it will always be interrupted by window changed event if Hide Screen is
    // triggered from TalkBack menu or the confirm dialog.
    pipeline.returnFeedback(
        EVENT_ID_UNTRACKED,
        Feedback.Part.builder()
            .setSpeech(
                Speech.builder()
                    .setAction(Speech.Action.SPEAK)
                    .setText(
                        service.getString(
                            R.string.screen_dimming_exit_instruction_line2,
                            gestureShortcutMapping.getGestureFromActionKey(
                                service.getString(R.string.shortcut_value_talkback_breakout)),
                            service.getString(R.string.shortcut_disable_dimming)))
                    .build()));
  }
}
