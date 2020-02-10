/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.controller;

import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.CLICK_ANCESTOR;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_KEYBOARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_DOWN;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_LEFT;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_RIGHT;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UNKNOWN;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UP;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Message;
import androidx.annotation.IntDef;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableStringBuilder;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.widget.AdapterView;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.ClassLoadingCache;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Performance.EventIdAnd;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.TreeDebug;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.WindowManager;
import com.google.android.accessibility.utils.input.InputModeManager;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirection;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Implements directional-pad navigation specific to Android TV devices. Currently operates as mixed
 * event-interpreter and feedback-mapper, interacting with RingerModeAndScreenMonitor and
 * TelevisionDPadManager.
 */
public class TelevisionNavigationController implements ServiceKeyEventListener {

  public static final int MIN_API_LEVEL = Build.VERSION_CODES.M;

  private static final Filter<AccessibilityNodeInfoCompat> FILTER_FOCUSED =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return node.isFocused();
        }
      };

  /** Filter for nodes to pass through D-pad up/down on Android M or earlier. */
  private static final Filter<AccessibilityNodeInfoCompat> IGNORE_UP_DOWN_M =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          // ; the ScrollAdapterView intercepts D-pad events on M; pass through
          // the up and down events so that the user can scroll these lists.
          // "com.android.tv.settings" - Android TV Settings app and SetupWraith (setup wizard)
          // "com.google.android.gsf.notouch" - Google Account setup in SetupWraith
          return ("com.android.tv.settings".contentEquals(node.getPackageName())
                  || "com.google.android.gsf.notouch".contentEquals(node.getPackageName()))
              && ClassLoadingCache.checkInstanceOf(node.getClassName(), AdapterView.class);
        }
      };

  @IntDef({MODE_NAVIGATE, MODE_SEEK_CONTROL})
  @Retention(RetentionPolicy.SOURCE)
  private @interface RemoteMode {}

  // The four arrow buttons move the focus and the select button performs a click.
  private static final int MODE_NAVIGATE = 0;
  // The four arrow buttons move the seek control and the select button exits seek control mode.
  private static final int MODE_SEEK_CONTROL = 1;

  private final TalkBackService service;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;
  private final Pipeline.FeedbackReturner pipeline;
  private final SparseBooleanArray handledKeyDown = new SparseBooleanArray();
  private @RemoteMode int mode = MODE_NAVIGATE;
  private TelevisionKeyHandler handler = new TelevisionKeyHandler(this);
  private final String treeDebugPrefKey;
  private boolean treeDebugEnabled = false;

  private boolean shouldProcessDPadKeyEvent = true;

  public TelevisionNavigationController(
      TalkBackService service,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      Pipeline.FeedbackReturner pipeline) {
    this.service = service;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.pipeline = pipeline;

    final SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(service);
    prefs.registerOnSharedPreferenceChangeListener(treeDebugChangeListener);
    treeDebugPrefKey = service.getString(R.string.pref_tree_debug_key);
    treeDebugChangeListener.onSharedPreferenceChanged(prefs, treeDebugPrefKey);
  }

  public void setShouldProcessDPadEvent(boolean shouldProcessEvent) {
    shouldProcessDPadKeyEvent = shouldProcessEvent;
  }

  @Override
  public boolean onKeyEvent(KeyEvent event, EventId eventId) {
    service.getInputModeManager().setInputMode(InputModeManager.INPUT_MODE_TV_REMOTE);

    WindowManager windowManager = new WindowManager(service);

    // Let the system handle keyboards. The keys are input-focusable so this works fine.
    // Note: on Android TV, it looks like the on-screen IME always appears, even when a physical
    // keyboard is connected, so this check will allow the system to handle all typing on
    // physical keyboards as well. This behavior is current as of Nougat.
    if (windowManager.isInputWindowOnScreen()) {
      return false;
    }

    // Note: we getCursorOrInputCursor because we want to avoid getting the root if there
    // is no cursor; getCursor is defined as getting the root if there is no a11y focus.
    AccessibilityNodeInfoCompat cursor =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);

    try {
      if (shouldIgnore(cursor, event)) {
        return false;
      }

      // TalkBack should always consume up/down/left/right on the d-pad. Otherwise, strange
      // things will happen when TalkBack cannot navigate further.
      // For example, TalkBack cannot control the gray-highlighted item in a ListView; the
      // view itself controls the highlighted item. So if the key event gets propagated to the
      // list view at the end of the list, the scrolling will jump to the highlighted item.
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        switch (event.getKeyCode()) {
          case KeyEvent.KEYCODE_DPAD_LEFT:
          case KeyEvent.KEYCODE_DPAD_RIGHT:
          case KeyEvent.KEYCODE_DPAD_UP:
          case KeyEvent.KEYCODE_DPAD_DOWN:
            // Directional navigation takes a non-trivial amount of time, so we should
            // post to the handler and return true immediately.
            handler.postDirectionalKeyEvent(event, cursor, eventId);
            return true;
          case KeyEvent.KEYCODE_DPAD_CENTER:
            // Can't post to handler because the return value might vary.
            boolean handledCenter = onCenterKey(cursor, eventId);
            handledKeyDown.put(KeyEvent.KEYCODE_DPAD_CENTER, handledCenter);
            return handledCenter;
          case KeyEvent.KEYCODE_ENTER:
            // Note: handling the Enter key won't interfere with typing because
            // we skip key event handling above if the IME is visible. (See above:
            // this will also skip handling the Enter key if using a physical keyboard.)
            // Can't post to handler because the return value might vary.
            boolean handledEnter = onCenterKey(cursor, eventId);
            handledKeyDown.put(KeyEvent.KEYCODE_ENTER, handledEnter);
            return handledEnter;
          case KeyEvent.KEYCODE_SEARCH:
            if (treeDebugEnabled) {
              TreeDebug.logNodeTrees(AccessibilityServiceCompatUtils.getWindows(service));
              return true;
            }
            break;
          default: // fall out
        }
      } else {
        // We need to cancel the corresponding up key action if we consumed the down action.
        switch (event.getKeyCode()) {
          case KeyEvent.KEYCODE_DPAD_LEFT:
          case KeyEvent.KEYCODE_DPAD_RIGHT:
          case KeyEvent.KEYCODE_DPAD_UP:
          case KeyEvent.KEYCODE_DPAD_DOWN:
            return true;
          case KeyEvent.KEYCODE_DPAD_CENTER:
            if (handledKeyDown.get(KeyEvent.KEYCODE_DPAD_CENTER)) {
              handledKeyDown.delete(KeyEvent.KEYCODE_DPAD_CENTER);
              return true;
            }
            break;
          case KeyEvent.KEYCODE_ENTER:
            if (handledKeyDown.get(KeyEvent.KEYCODE_ENTER)) {
              handledKeyDown.delete(KeyEvent.KEYCODE_ENTER);
              return true;
            }
            break;
          case KeyEvent.KEYCODE_SEARCH:
            if (treeDebugEnabled) {
              return true;
            }
            break;
          default: // fall out
        }
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(cursor);
    }

    return false;
  }

  private void onDirectionalKey(int keyCode, AccessibilityNodeInfoCompat cursor, EventId eventId) {
    switch (mode) {
      case MODE_NAVIGATE:
        {
          @SearchDirection int direction = SEARCH_FOCUS_UNKNOWN;
          switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
              direction = SEARCH_FOCUS_LEFT;
              break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
              direction = SEARCH_FOCUS_RIGHT;
              break;
            case KeyEvent.KEYCODE_DPAD_UP:
              direction = SEARCH_FOCUS_UP;
              break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
              direction = SEARCH_FOCUS_DOWN;
              break;
            default: // fall out
          }
          if (direction != SEARCH_FOCUS_UNKNOWN) {
            pipeline.returnFeedback(
                eventId,
                Feedback.focusDirection(direction)
                    .setInputMode(INPUT_MODE_KEYBOARD)
                    .setScroll(true)
                    .setDefaultToInputFocus(true));
          }
        }
        break;
      case MODE_SEEK_CONTROL:
        {
          if (Role.getRole(cursor) != Role.ROLE_SEEK_CONTROL) {
            setMode(MODE_NAVIGATE, eventId);
          } else {
            boolean isRtl = WindowManager.isScreenLayoutRTL(service);
            switch (keyCode) {
              case KeyEvent.KEYCODE_DPAD_UP:
                pipeline.returnFeedback(
                    eventId, Feedback.nodeAction(cursor, ACTION_SCROLL_FORWARD));
                break;
              case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (isRtl) {
                  pipeline.returnFeedback(
                      eventId, Feedback.nodeAction(cursor, ACTION_SCROLL_BACKWARD));
                } else {
                  pipeline.returnFeedback(
                      eventId, Feedback.nodeAction(cursor, ACTION_SCROLL_FORWARD));
                }
                break;
              case KeyEvent.KEYCODE_DPAD_DOWN:
                pipeline.returnFeedback(
                    eventId, Feedback.nodeAction(cursor, ACTION_SCROLL_BACKWARD));
                break;
              case KeyEvent.KEYCODE_DPAD_LEFT:
                if (isRtl) {
                  pipeline.returnFeedback(
                      eventId, Feedback.nodeAction(cursor, ACTION_SCROLL_FORWARD));
                } else {
                  pipeline.returnFeedback(
                      eventId, Feedback.nodeAction(cursor, ACTION_SCROLL_BACKWARD));
                }
                break;
              default: // fall out
            }
          }
        }
        break;
      default: // fall out
    }
  }

  /** Returns true if TalkBack should consume the center key; otherwise returns false. */
  private boolean onCenterKey(AccessibilityNodeInfoCompat cursor, EventId eventId) {
    switch (mode) {
      case MODE_NAVIGATE:
        {
          if (Role.getRole(cursor) == Role.ROLE_SEEK_CONTROL) {
            // Seek control, center key toggles seek control input mode instead of clicking.
            setMode(MODE_SEEK_CONTROL, eventId);
            return true;
          } else if (pipeline.returnFeedback(eventId, Feedback.focus(CLICK_ANCESTOR))) {
            // We were able to find a clickable node in the hierarchy.
            return true;
          } else if (cursor == null
              || AccessibilityNodeInfoUtils.isOrHasMatchingAncestor(cursor, FILTER_FOCUSED)) {
            // Note: this branch is mainly for compatibility reasons.
            // The node with input focus is cursor or ancestor, so it's safe to pass the
            // d-pad key event through.
            // TODO: Re-evaluate whether this check is needed for N.
            return false;
          } else {
            // In all other cases, we must consume the key event instead of passing.
            return true;
          }
        }
      case MODE_SEEK_CONTROL:
        {
          setMode(MODE_NAVIGATE, eventId);
          return true;
        }
      default: // fall out
    }

    return false;
  }

  @Override
  public boolean processWhenServiceSuspended() {
    return false;
  }

  /**
   * Ignores the KeyEvent if any one of the two situations is satisfied:
   *
   * <p>1. Between TalkBack 4.4 and 4.3, the fundamental interaction model for TalkBack on Android
   * TV changed. On 4.3, TalkBack relies on the system to move the input focus, which meant that
   * only input-focusable objects could be selected. On 4.4, TalkBack does its own navigation, which
   * means that all accessibility-focusable objects could be selected, but some objects that did not
   * respond to the appropriate accessibility events started breaking.
   *
   * <p>To mitigate this, we will (very conservatively) pass through the D-pad key events for
   * certain views, effectively restoring the TB 4.3 behavior for these views.
   *
   * <p>2. Ignore D-pad KeyEvents if shouldProcessDPadKeyEvent is false. Which is a workaround to
   * for accessibility issue in Netflix. Refer to {@link com.android.talkback.TelevisionDPadManager}
   * for more details.
   */
  private boolean shouldIgnore(AccessibilityNodeInfoCompat node, KeyEvent event) {
    final int keyCode = event.getKeyCode();
    if (!shouldProcessDPadKeyEvent
        && (keyCode == KeyEvent.KEYCODE_DPAD_UP
            || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
            || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
            || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
            || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
      return true;
    }

    if (!BuildVersionUtils.isAtLeastN()) {
      if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
        return AccessibilityNodeInfoUtils.isOrHasMatchingAncestor(node, IGNORE_UP_DOWN_M);
      }
    }

    return false;
  }

  private void setMode(@RemoteMode int newMode, EventId eventId) {
    if (newMode == mode) {
      return;
    }

    int template;
    boolean hint;
    @RemoteMode int modeForFeedback;
    if (newMode == MODE_NAVIGATE) {
      // "XYZ mode ended".
      template = R.string.template_tv_remote_mode_ended;
      hint = false;
      modeForFeedback = mode; // Speak the old mode name on exit.
    } else {
      // "XYZ mode started".
      template = R.string.template_tv_remote_mode_started;
      hint = true;
      modeForFeedback = newMode; // Speak the new mode name on enter.
    }

    SpannableStringBuilder ttsText = new SpannableStringBuilder();
    switch (modeForFeedback) {
      case MODE_SEEK_CONTROL:
        StringBuilderUtils.appendWithSeparator(
            ttsText,
            service.getString(
                template, service.getString(R.string.value_tv_remote_mode_seek_control)));
        if (hint) {
          StringBuilderUtils.appendWithSeparator(
              ttsText, service.getString(R.string.value_hint_tv_remote_mode_seek_control));
        }
        break;
      default: // fall out
    }

    // Really critical that the user understands what mode the remote control is in.
    SpeechController.SpeakOptions speakOptions =
        SpeechController.SpeakOptions.create()
            .setQueueMode(SpeechController.QUEUE_MODE_INTERRUPT)
            .setFlags(
                FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                    | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                    | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE);
    pipeline.returnFeedback(eventId, Feedback.speech(ttsText, speakOptions));

    mode = newMode;
  }

  /** Silently resets the remote mode to navigate mode. */
  public void resetToNavigateMode() {
    mode = MODE_NAVIGATE;
  }

  /**
   * Message handler to allow onKeyEvent() to return before timeout, while handler finishes key
   * processing later.
   */
  private static class TelevisionKeyHandler
      extends WeakReferenceHandler<TelevisionNavigationController> {
    private static final int WHAT_DIRECTIONAL = 1;

    public TelevisionKeyHandler(TelevisionNavigationController parent) {
      super(parent);
    }

    @Override
    protected void handleMessage(Message msg, TelevisionNavigationController parent) {
      int keyCode = msg.arg1;
      @SuppressWarnings("unchecked")
      EventIdAnd<AccessibilityNodeInfoCompat> cursorAndEventId =
          (EventIdAnd<AccessibilityNodeInfoCompat>) msg.obj;
      AccessibilityNodeInfoCompat cursor = cursorAndEventId.object;

      switch (msg.what) {
        case WHAT_DIRECTIONAL:
          parent.onDirectionalKey(keyCode, cursor, cursorAndEventId.eventId);
          break;
        default: // fall out
      }

      AccessibilityNodeInfoUtils.recycleNodes(cursor);
    }

    public void postDirectionalKeyEvent(
        KeyEvent event, AccessibilityNodeInfoCompat cursor, EventId eventId) {
      AccessibilityNodeInfoCompat obtainedCursor;
      if (cursor == null) {
        obtainedCursor = null;
      } else {
        obtainedCursor = AccessibilityNodeInfoCompat.obtain(cursor);
      }
      EventIdAnd<AccessibilityNodeInfoCompat> cursorAndEventId =
          new EventIdAnd<>(obtainedCursor, eventId);
      Message msg = obtainMessage(WHAT_DIRECTIONAL, event.getKeyCode(), 0, cursorAndEventId);
      sendMessageDelayed(msg, 0);
    }
  }

  private final OnSharedPreferenceChangeListener treeDebugChangeListener =
      new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
          if (key != null && key.equals(treeDebugPrefKey)) {
            treeDebugEnabled =
                SharedPreferencesUtils.getBooleanPref(
                    sharedPreferences,
                    service.getResources(),
                    R.string.pref_tree_debug_key,
                    R.bool.pref_tree_debug_default);
          }
        }
      };
}
