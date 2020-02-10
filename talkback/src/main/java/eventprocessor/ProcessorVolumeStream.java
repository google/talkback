/*
 * Copyright (C) 2013 Google Inc.
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

package com.google.android.accessibility.talkback.eventprocessor;

import static com.google.android.accessibility.talkback.Feedback.Focus.Action.CLICK;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.LONG_CLICK;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_TOUCH;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_BACKWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.contextmenu.MenuManager;
import com.google.android.accessibility.talkback.controller.DimScreenController;
import com.google.android.accessibility.talkback.controller.FullScreenReadController;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.compat.media.AudioManagerCompatUtils;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.volumebutton.VolumeButtonPatternDetector;

/**
 * Handles volume-key events to adjust various volume streams based on current speech state. Locks
 * the volume control stream during a touch interaction event. Currently this class is a mix of
 * event-interpreter and feedback-mapper.
 */
public class ProcessorVolumeStream
    implements AccessibilityEventListener,
        ServiceKeyEventListener,
        VolumeButtonPatternDetector.OnPatternMatchListener {
  /** Default flags for volume adjustment while touching the screen. */
  private static final int DEFAULT_FLAGS_TOUCHING_SCREEN = (AudioManager.FLAG_VIBRATE);

  /** Default flags for volume adjustment while not touching the screen. */
  private static final int DEFAULT_FLAGS_NOT_TOUCHING_SCREEN =
      (AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_VIBRATE | AudioManager.FLAG_PLAY_SOUND);

  /** TalkBack audio stream. */
  private static final int STREAM_TALKBACK_AUDIO = SpeechController.DEFAULT_STREAM;

  /** System default audio stream. */
  private static final int STREAM_DEFAULT = AudioManager.USE_DEFAULT_STREAM_TYPE;

  /** Tag used for identification of the wake lock held by this class */
  private static final String WL_TAG = ProcessorVolumeStream.class.getSimpleName();

  /** Event types that are handled by ProcessorVolumeStream. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_VOL_STREAM =
      AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
          | AccessibilityEvent.TYPE_TOUCH_INTERACTION_END;

  /** The audio manager, used to adjust speech volume. */
  private final AudioManager audioManager;

  /** WakeLock used to keep the screen active during key events */
  private final WakeLock wakeLock;

  /** Handler for completing volume key handling outside of the main key-event handler. */
  private final VolumeStreamHandler handler = new VolumeStreamHandler(this);

  /** Focus-interpreter for determining the focused node. */
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  /**
   * Feedback Returner of Pipeline for providing feedback on boundaries during volume key
   * navigation.
   */
  private final Pipeline.FeedbackReturner pipeline;

  /** Full screen read controller used to decide whether the Volume UI should be shown on screen */
  private final FullScreenReadController fullScreenReadController;

  /** Whether the user is touching the screen. */
  private boolean touchingScreen = false;

  private boolean navigationMode = false;

  private SharedPreferences prefs;
  private TalkBackService service;
  private DimScreenController dimScreenController;
  private final ActorState actorState;
  private VolumeButtonPatternDetector patternDetector;
  private MenuManager menuManager;
  private final GlobalVariables globalVariables;

  @SuppressWarnings("deprecation")
  public ProcessorVolumeStream(
      Pipeline.FeedbackReturner pipeline,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      DimScreenController dimScreenController,
      ActorState actorState,
      FullScreenReadController fullScreenReadController,
      TalkBackService service,
      GlobalVariables globalVariables,
      MenuManager menuManager) {
    if (pipeline == null) {
      throw new IllegalStateException("CachedFeedbackController is null");
    }
    if (accessibilityFocusMonitor == null) {
      throw new IllegalStateException("accessibilityFocusMonitor is null");
    }
    if (dimScreenController == null) {
      throw new IllegalStateException("DimScreenController is null");
    }
    if (menuManager == null) {
      throw new IllegalStateException("MenuManager is null");
    }

    audioManager = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.pipeline = pipeline;
    this.actorState = actorState;
    this.fullScreenReadController = fullScreenReadController;
    this.menuManager = menuManager;

    final PowerManager pm = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
    wakeLock =
        pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, WL_TAG);

    prefs = SharedPreferencesUtils.getSharedPreferences(service);
    this.service = service;
    this.dimScreenController = dimScreenController;
    patternDetector = new VolumeButtonPatternDetector(this.service);
    patternDetector.setOnPatternMatchListener(this);
    this.globalVariables = globalVariables;
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_PROCESSOR_VOL_STREAM;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START:
        touchingScreen = true;
        break;
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END:
        touchingScreen = false;
        break;
      default: // fall out
    }
  }

  @Override
  public boolean onKeyEvent(KeyEvent event, EventId eventId) {
    boolean handled = patternDetector.onKeyEvent(event);

    if (handled) {
      // Quickly acquire and release the wake lock so that
      // PowerManager.ON_AFTER_RELEASE takes effect.
      wakeLock.acquire();
      wakeLock.release();
    }

    return handled;
  }

  @Override
  public boolean processWhenServiceSuspended() {
    return true;
  }

  private void handleBothVolumeKeysLongPressed(EventId eventId) {
    // Shortcut for accessibility on/off replaces talkback-suspend.
    if (FeatureSupport.hasAccessibilityShortcut(service)) {
      return;
    }

    // Check whether user enabled the volume-key shortcut for suspending talkback.
    boolean shortcutEnabled =
        SharedPreferencesUtils.getBooleanPref(
            prefs,
            service.getResources(),
            R.string.pref_two_volume_long_press_key,
            R.bool.pref_resume_volume_buttons_long_click_default);
    if (!shortcutEnabled) {
      return;
    }

    // Toggle talkback suspended state.
    if (TalkBackService.isServiceActive()) {
      service.requestSuspendTalkBack(eventId);
    } else {
      service.resumeTalkBack(eventId);
    }
  }

  public void toggleNavigationMode() {
    navigationMode = !navigationMode;
  }

  private void navigateSlider(
      int button, @NonNull AccessibilityNodeInfoCompat node, EventId eventId) {
    int action;
    if (button == VolumeButtonPatternDetector.VOLUME_UP) {
      action = AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD;
    } else if (button == VolumeButtonPatternDetector.VOLUME_DOWN) {
      action = AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD;
    } else {
      return;
    }

    PerformActionUtils.performAction(node, action, eventId);
  }

  private void navigateEditText(
      int button, @NonNull AccessibilityNodeInfoCompat node, EventId eventId) {
    boolean result = false;

    Bundle args = new Bundle();
    CursorGranularity currentGranularity =
        actorState.getDirectionNavigation().getGranularityAt(node);
    if (currentGranularity != CursorGranularity.DEFAULT) {
      args.putInt(
          AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
          currentGranularity.value);
    } else {
      args.putInt(
          AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
          AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER);
    }

    if (actorState.getDirectionNavigation().isSelectionModeActive()) {
      args.putBoolean(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, true);
    }

    globalVariables.setFlag(GlobalVariables.EVENT_SKIP_FOCUS_PROCESSING_AFTER_GRANULARITY_MOVE);
    EventState.getInstance().setFlag(EventState.EVENT_SKIP_HINT_AFTER_GRANULARITY_MOVE);

    if (button == VolumeButtonPatternDetector.VOLUME_UP) {
      result =
          PerformActionUtils.performAction(
              node, AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, args, eventId);
    } else if (button == VolumeButtonPatternDetector.VOLUME_DOWN) {
      result =
          PerformActionUtils.performAction(
              node,
              AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
              args,
              eventId);
    }

    if (!result) {
      pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
    }
  }

  private boolean attemptNavigation(int button, EventId eventId) {
    AccessibilityNodeInfoCompat node =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);

    if (node == null) {
      return false;
    }
    try {
      if (Role.getRole(node) == Role.ROLE_SEEK_CONTROL) {
        navigateSlider(button, node, eventId);
        return true;
      }

      // In general, do not allow volume key navigation when the a11y focus is placed but
      // it is not on the edit field that the keyboard is currently editing.
      //
      // Example 1:
      // EditText1 has input focus and EditText2 has accessibility focus.
      // getCursorOrInputCursor() will return EditText2 based on its priority order.
      // EditText2.isFocused() = false, so we should not allow volume keys to control text.
      //
      // Example 2:
      // EditText1 in Window1 has input focus. EditText2 in Window2 has input focus as well.
      // If Window1 is input-focused but Window2 has the accessibility focus, don't allow
      // the volume keys to control the text.
      boolean nodeWindowFocused;
      AccessibilityWindowInfoCompat windowInfo = AccessibilityNodeInfoUtils.getWindow(node);
      nodeWindowFocused = (windowInfo != null) && windowInfo.isFocused();
      if (node.isFocused() && nodeWindowFocused && node.isEditable() && !touchingScreen) {
        navigateEditText(button, node, eventId);
        return true;
      }

      return false;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(node);
    }
  }

  private void adjustVolumeFromKeyEvent(int button) {
    final int direction =
        ((button == VolumeButtonPatternDetector.VOLUME_UP)
            ? AudioManager.ADJUST_RAISE
            : AudioManager.ADJUST_LOWER);
    // While continuous reading is active, we do not want to show the UI and interrupt continuous
    // reading.
    if (touchingScreen || fullScreenReadController.isActive()) {
      AudioManagerCompatUtils.adjustStreamVolume(
          audioManager,
          STREAM_TALKBACK_AUDIO,
          direction,
          DEFAULT_FLAGS_TOUCHING_SCREEN,
          getClass().getName());
    } else if (actorState.getSpeechState().isSpeakingOrSpeechQueued()) {
      AudioManagerCompatUtils.adjustStreamVolume(
          audioManager,
          STREAM_TALKBACK_AUDIO,
          direction,
          DEFAULT_FLAGS_NOT_TOUCHING_SCREEN,
          getClass().getName());
    } else {
      // Attempt to adjust the suggested stream, but let the system
      // override in special situations like during voice calls, when an
      // application has locked the volume control stream, or when music
      // is playing.
      audioManager.adjustSuggestedStreamVolume(
          direction, STREAM_DEFAULT, DEFAULT_FLAGS_NOT_TOUCHING_SCREEN);
    }
  }

  @Override
  public void onPatternMatched(int patternCode, int buttonCombination, EventId eventId) {
    handler.postPatternMatched(patternCode, buttonCombination, eventId);
  }

  public void onPatternMatchedInternal(int patternCode, int buttonCombination, EventId eventId) {
    switch (patternCode) {
      case VolumeButtonPatternDetector.SHORT_PRESS_PATTERN:
        handleSingleShortTap(buttonCombination, eventId);
        break;
      case VolumeButtonPatternDetector.LONG_PRESS_PATTERN:
        handleSingleLongTap(buttonCombination, eventId);
        break;
      case VolumeButtonPatternDetector.TWO_BUTTONS_LONG_PRESS_PATTERN:
        handleBothVolumeKeysLongPressed(eventId);
        patternDetector.clearState();
        break;
      case VolumeButtonPatternDetector.TWO_BUTTONS_THREE_PRESS_PATTERN:
        if (!service.isInstanceActive()) {
          // If the service isn't active, the user won't get any feedback that
          // anything happened, so we shouldn't change the dimming setting.
          return;
        }

        boolean globalShortcut = isTripleClickEnabledGlobally();
        boolean dimmed = dimScreenController.isDimmingEnabled();

        if (dimmed && (globalShortcut || dimScreenController.isInstructionDisplayed())) {
          dimScreenController.disableDimming();
        } else if (!dimmed && globalShortcut) {
          dimScreenController.enableDimmingAndShowConfirmDialog();
        }

        break;
        // TODO Add back double play/pause button click to toggle navigation mode.
      default: // fall out
    }
  }

  // Generate a single play/pause button click and send it to system.
  private void dispatchPlayPauseSingleClick() {
    audioManager.dispatchMediaKeyEvent(
        new KeyEvent(KeyEvent.ACTION_DOWN, VolumeButtonPatternDetector.PLAY_PAUSE));
    audioManager.dispatchMediaKeyEvent(
        new KeyEvent(KeyEvent.ACTION_UP, VolumeButtonPatternDetector.PLAY_PAUSE));
  }

  private void passThroughMediaButtonClick(int button) {
    if (button == VolumeButtonPatternDetector.PLAY_PAUSE) {
      dispatchPlayPauseSingleClick();
    } else {
      adjustVolumeFromKeyEvent(button);
    }
  }

  private void handleSingleShortTap(int button, EventId eventId) {
    if (TalkBackService.isServiceActive() && attemptNavigation(button, eventId)) {
      return;
    }

    if (navigationMode) {
      if (button == VolumeButtonPatternDetector.VOLUME_UP) {
        boolean result =
            pipeline.returnFeedback(
                eventId,
                Feedback.focusDirection(SEARCH_FOCUS_BACKWARD)
                    .setInputMode(INPUT_MODE_TOUCH)
                    .setWrap(true)
                    .setScroll(true)
                    .setDefaultToInputFocus(true));
        if (!result) {
          pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
        }
      } else if (button == VolumeButtonPatternDetector.VOLUME_DOWN) {
        boolean result =
            pipeline.returnFeedback(
                eventId,
                Feedback.focusDirection(SEARCH_FOCUS_FORWARD)
                    .setInputMode(INPUT_MODE_TOUCH)
                    .setWrap(true)
                    .setScroll(true)
                    .setDefaultToInputFocus(true));
        if (!result) {
          pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
        }
      } else {
        pipeline.returnFeedback(eventId, Feedback.focus(CLICK));
      }
    } else {
      passThroughMediaButtonClick(button);
    }
  }

  private void handleSingleLongTap(int button, EventId eventId) {
    if (TalkBackService.isServiceActive() && attemptNavigation(button, eventId)) {
      return;
    }

    if (navigationMode) {
      if (button == VolumeButtonPatternDetector.VOLUME_UP) {
        menuManager.showMenu(R.menu.local_context_menu, eventId);
      } else if (button == VolumeButtonPatternDetector.VOLUME_DOWN) {
        menuManager.showMenu(R.menu.global_context_menu, eventId);
      } else {
        pipeline.returnFeedback(eventId, Feedback.focus(LONG_CLICK));
      }
    } else {
      passThroughMediaButtonClick(button);
    }
  }

  private boolean isTripleClickEnabledGlobally() {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(service);
    return SharedPreferencesUtils.getBooleanPref(
        prefs,
        service.getResources(),
        R.string.pref_dim_volume_three_clicks_key,
        R.bool.pref_dim_volume_three_clicks_default);
  }

  /**
   * Used to run potentially long methods outside of the key handler so that we don't ever hit the
   * key handler timeout.
   */
  private static final class VolumeStreamHandler
      extends WeakReferenceHandler<ProcessorVolumeStream> {

    public VolumeStreamHandler(ProcessorVolumeStream parent) {
      super(parent);
    }

    @Override
    protected void handleMessage(Message msg, ProcessorVolumeStream parent) {
      int patternCode = msg.arg1;
      int buttonCombination = msg.arg2;
      EventId eventId = (EventId) msg.obj;
      parent.onPatternMatchedInternal(patternCode, buttonCombination, eventId);
    }

    public void postPatternMatched(int patternCode, int buttonCombination, EventId eventId) {
      Message msg =
          obtainMessage(
              /* what= */ 0, /* arg1= */ patternCode, /* arg2= */ buttonCombination, eventId);
      sendMessage(msg);
    }
  }
}
