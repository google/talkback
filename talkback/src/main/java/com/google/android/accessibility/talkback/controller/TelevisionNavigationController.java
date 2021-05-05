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
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.LONG_CLICK_CURRENT;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_TV_REMOTE;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_DOWN;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_LEFT;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_RIGHT;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UNKNOWN;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UP;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Message;
import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableStringBuilder;
import android.util.SparseLongArray;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.widget.AdapterView;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.TvNavigation;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.ClassLoadingCache;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Performance.EventIdAnd;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.TreeDebug;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.WindowUtils;
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
  private static final String PRINT_TREE_DEBUG_ACTION =
      "com.google.android.accessibility.talkback.PRINT_TREE_DEBUG";

  /** Filter for nodes to pass through D-pad up/down on Android M or earlier. */
  private static final Filter<AccessibilityNodeInfoCompat> IGNORE_UP_DOWN_M =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          // REFERTO; the ScrollAdapterView intercepts D-pad events on M; pass through
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
  // A map from key code to a time stamp that is was pressed down, but only containing events that
  // are handled and not passed through.
  private final SparseLongArray handledKeyDownTimestamps = new SparseLongArray();
  private final BroadcastReceiver treeDebugBroadcastReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (treeDebugEnabled) {
            TreeDebug.logNodeTrees(AccessibilityServiceCompatUtils.getWindows(service));
          }
        }
      };

  private @RemoteMode int mode = MODE_NAVIGATE;
  private TelevisionKeyHandler handler = new TelevisionKeyHandler(this);
  private final String treeDebugPrefKey;
  private boolean treeDebugEnabled = false;

  private boolean shouldProcessDPadKeyEvent = true;
  private boolean shouldProcessDPadCenterKeyEventOnInputFocusNodeWhenInsync = true;

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
    shouldProcessDPadCenterKeyEventOnInputFocusNodeWhenInsync =
        FeatureSupport.isTv(this.service)
            && TvNavigation.processDpadCenterInputFocusNodeWhenInsync(this.service);
  }

  public void setShouldProcessDPadEvent(boolean shouldProcessEvent) {
    shouldProcessDPadKeyEvent = shouldProcessEvent;
  }

  public void onDestroy() {
    service.unregisterReceiver(treeDebugBroadcastReceiver);
  }

  @Override
  public boolean onKeyEvent(KeyEvent event, EventId eventId) {
    service.getInputModeManager().setInputMode(InputModeManager.INPUT_MODE_TV_REMOTE);

    // Let the system handle keyboards. The keys are input-focusable so this works fine.
    // Note: on Android TV, it looks like the on-screen IME always appears, even when a physical
    // keyboard is connected, so this check will allow the system to handle all typing on
    // physical keyboards as well. This behavior is current as of Nougat.
    if (AccessibilityServiceCompatUtils.isInputWindowOnScreen(service)) {
      return false;
    }

    // Note: we getCursorOrInputCursor because we want to avoid getting the root if there
    // is no cursor; getCursor is defined as getting the root if there is no a11y focus.
    AccessibilityNodeInfoCompat cursor =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);

    try {
      if (shouldHandleEvent(cursor, event)) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
          onKeyDown(event.getKeyCode());
        } else {
          onKeyUp(cursor, event, eventId);
        }
        return true;
      }
      return false;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(cursor);
    }
  }

  private void onKeyUp(AccessibilityNodeInfoCompat cursor, KeyEvent event, EventId eventId) {
    // Always guaranteed that there was a handled keyDown event before this, so no null check
    // needed.
    long timeElapsedSinceKeyDown =
        SystemClock.elapsedRealtime() - handledKeyDownTimestamps.get(event.getKeyCode());
    handledKeyDownTimestamps.delete(event.getKeyCode());
    boolean isLongPress = timeElapsedSinceKeyDown >= ViewConfiguration.getLongPressTimeout();
    if (isLongPress) {
      handleLongPress(event, eventId);
    } else {
      handleShortPress(cursor, event, eventId);
    }
  }

  private void onKeyDown(int keyCode) {
    handledKeyDownTimestamps.put(keyCode, SystemClock.elapsedRealtime());
  }

  private void handleShortPress(
      AccessibilityNodeInfoCompat cursor, KeyEvent event, EventId eventId) {
    switch (event.getKeyCode()) {
      case KeyEvent.KEYCODE_DPAD_LEFT:
      case KeyEvent.KEYCODE_DPAD_RIGHT:
      case KeyEvent.KEYCODE_DPAD_UP:
      case KeyEvent.KEYCODE_DPAD_DOWN:
        // Directional navigation takes a non-trivial amount of time, so we should
        // post to the handler and return true immediately.
        handler.postDirectionalKeyEvent(event, cursor, eventId);
        break;
      case KeyEvent.KEYCODE_DPAD_CENTER:
        // fall through
      case KeyEvent.KEYCODE_ENTER:
        // Note: handling the Enter key won't interfere with typing because
        // we skip key event handling above if the IME is visible. (See above:
        // this will also skip handling the Enter key if using a physical keyboard.)
        // Can't post to handler because the return value might vary.
        onCenterKey(cursor, eventId);
        break;
      case KeyEvent.KEYCODE_SEARCH:
        TreeDebug.logNodeTrees(AccessibilityServiceCompatUtils.getWindows(service));
        break;
      default: // fall out
    }
  }

  private void handleLongPress(KeyEvent event, EventId eventId) {
    switch (event.getKeyCode()) {
      case KeyEvent.KEYCODE_DPAD_CENTER:
        // fall through
      case KeyEvent.KEYCODE_ENTER:
        pipeline.returnFeedback(eventId, Feedback.focus(LONG_CLICK_CURRENT));
        break;
      default:
    }
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
                    .setInputMode(INPUT_MODE_TV_REMOTE)
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
            boolean isRtl = WindowUtils.isScreenLayoutRTL(service);
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

  private void onCenterKey(AccessibilityNodeInfoCompat cursor, EventId eventId) {
    switch (mode) {
      case MODE_NAVIGATE:
        if (Role.getRole(cursor) == Role.ROLE_SEEK_CONTROL) {
          // Seek control, center key toggles seek control input mode instead of clicking.
          setMode(MODE_SEEK_CONTROL, eventId);
        } else {
          pipeline.returnFeedback(eventId, Feedback.focus(CLICK_ANCESTOR));
        }
        break;
      case MODE_SEEK_CONTROL:
        setMode(MODE_NAVIGATE, eventId);
        break;
      default: // fall out
    }
  }

  @Override
  public boolean processWhenServiceSuspended() {
    return false;
  }

  /**
   * Returns whether we should handle a KeyEvent:
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
  private boolean shouldHandleEvent(AccessibilityNodeInfoCompat cursor, KeyEvent event) {
    final int keyCode = event.getKeyCode();
    if (event.getAction() == KeyEvent.ACTION_UP) {
      // If we did not handle key down event for this keycode, then also ignore key up event.
      if (handledKeyDownTimestamps.get(keyCode, -1) == -1) {
        return false;
      }
    }
    // TalkBack should always consume up/down/left/right on the d-pad, unless
    // shouldProcessDPadKeyEvent is false. Otherwise, strange things will happen when TalkBack
    // cannot navigate further.
    // For example, TalkBack cannot control the gray-highlighted item in a ListView; the
    // view itself controls the highlighted item. So if the key event gets propagated to the
    // list view at the end of the list, the scrolling will jump to the highlighted item.
    switch (keyCode) {
      case KeyEvent.KEYCODE_DPAD_UP:
      case KeyEvent.KEYCODE_DPAD_DOWN:
        if (!shouldProcessDPadKeyEvent) {
          return false;
        }
        if (!BuildVersionUtils.isAtLeastN()) {
          // For before N platforms, only handle when we are not ignoring UP/DOWN key
          return !AccessibilityNodeInfoUtils.isOrHasMatchingAncestor(cursor, IGNORE_UP_DOWN_M);
        }
        return true;
      case KeyEvent.KEYCODE_DPAD_LEFT:
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        return shouldProcessDPadKeyEvent;
      case KeyEvent.KEYCODE_DPAD_CENTER:
        // Always handle DPAD_CENTER_KEY event on TV. When DPAD_CENTER_KEY event pass to node
        // which accessibility focus is selected and non-clickable, Talkback should consume the
        // DPAD_CENTER_KEY event if accessibility focsued node is not null. And there is a
        // implicit logic, that once the a11y focused node is not clickable, talkback still consume
        // the DPAD_CENTER_KEY event and ignore it.
        return shouldProcessDPadKeyEvent && shouldHandleKeyCenter(cursor);
      case KeyEvent.KEYCODE_ENTER:
        return shouldHandleKeyCenter(cursor);
      case KeyEvent.KEYCODE_SEARCH:
        return treeDebugEnabled;
      default:
    }
    // We do not handle other keys.
    return false;
  }

  /**
   * {@link KeyEvent.KEYCODE_DPAD_CENTER} or {@link KeyEvent.KEYCODE_ENTER} will flip between
   * navigation and seek control mode. During navigation mode, only handle the event when the node
   * which a11y focus on is not null.
   */
  private boolean shouldHandleKeyCenter(AccessibilityNodeInfoCompat a11yOrInputFocusedNode) {
    switch (mode) {
      case MODE_NAVIGATE:
        if (Role.getRole(a11yOrInputFocusedNode) == Role.ROLE_SEEK_CONTROL) {
          return true;
        } else {
          // See if the current node (BUT only consider A11y focused node) is not null.
          AccessibilityNodeInfoCompat currentFocus = null;
          AccessibilityNodeInfoCompat nodeToClick = null;
          try {
            currentFocus =
                accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
            if (currentFocus == null) {
              return false;
            }
            if (shouldProcessDPadCenterKeyEventOnInputFocusNodeWhenInsync) {
              nodeToClick =
                  AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
                      currentFocus, AccessibilityNodeInfoUtils.FILTER_CLICKABLE);
              return nodeToClick != null;
            } else {
              return true;
            }
          } finally {
            AccessibilityNodeInfoUtils.recycleNodes(currentFocus, nodeToClick);
          }
        }
      case MODE_SEEK_CONTROL:
        return true;
      default:
        // fall out
    }
    return true;
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
            if (treeDebugEnabled) {
              service.registerReceiver(
                  treeDebugBroadcastReceiver, new IntentFilter(PRINT_TREE_DEBUG_ACTION));
            } else {
              service.unregisterReceiver(treeDebugBroadcastReceiver);
            }
          }
        }
      };
}
