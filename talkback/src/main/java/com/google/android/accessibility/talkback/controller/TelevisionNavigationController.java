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

import static android.content.Context.RECEIVER_EXPORTED;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.CLICK_ANCESTOR;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.LONG_CLICK_CURRENT;
import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.TARGET_SPAN_CLASS;
import static com.google.android.accessibility.utils.input.CursorGranularity.DEFAULT;
import static com.google.android.accessibility.utils.monitor.InputModeTracker.INPUT_MODE_TV_REMOTE;
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
import android.text.SpannableStringBuilder;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import androidx.annotation.IntDef;
import androidx.core.content.ContextCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.PrimesController;
import com.google.android.accessibility.talkback.PrimesController.TimerAction;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.TvNavigation;
import com.google.android.accessibility.talkback.contextmenu.ListMenuManager;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.monitor.InputMethodMonitor;
import com.google.android.accessibility.talkback.preference.PreferencesActivityUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.TreeDebug;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.WindowUtils;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.traversal.SpannableTraversalUtils;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirectionOrUnknown;
import com.google.common.collect.ImmutableSet;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Implements directional-pad navigation specific to Android TV devices. Currently operates as mixed
 * event-interpreter and feedback-mapper, interacting with RingerModeAndScreenMonitor and
 * TelevisionDPadManager.
 */
public class TelevisionNavigationController implements ServiceKeyEventListener {

  public static final int MIN_API_LEVEL = Build.VERSION_CODES.M;
  private static final String PRINT_TREE_DEBUG_ACTION =
      "com.google.android.accessibility.talkback.PRINT_TREE_DEBUG";

  @IntDef({MODE_NAVIGATE, MODE_SEEK_CONTROL})
  @Retention(RetentionPolicy.SOURCE)
  private @interface RemoteMode {}

  // The four arrow buttons move the focus and the select button performs a click.
  private static final int MODE_NAVIGATE = 0;
  // The four arrow buttons move the seek control and the select button exits seek control mode.
  private static final int MODE_SEEK_CONTROL = 1;

  private final TalkBackService service;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;
  private final InputMethodMonitor inputMethodMonitor;
  private final PrimesController primesController;
  private final Pipeline.FeedbackReturner pipeline;
  private final BroadcastReceiver treeDebugBroadcastReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (treeDebugEnabled) {
            logNodeTreesOnAllDisplays();
          }
        }
      };

  @RemoteMode private int mode = MODE_NAVIGATE;
  private final TelevisionKeyHandler handler = new TelevisionKeyHandler(this);
  private final ListMenuManager listMenuManager;
  private @Nullable AccessibilityNodeInfoCompat listMenuTriggerNode;

  private final String treeDebugPrefKey;
  private volatile boolean treeDebugEnabled = false;

  private volatile boolean shouldProcessDPadKeyEvent = true;
  private final boolean letSystemHandleDpadCenterWhenFocusNotInSync;
  private final boolean useHandlerThread;
  private final Duration timeout;

  private final Map<FocusType, CachedFocus> focusCache =
      Collections.synchronizedMap(new EnumMap<>(FocusType.class));

  private volatile boolean isHoldingConfirmKey = false;
  private boolean hasTriggeredConfirmKeyLongPress = false;

  private static final ImmutableSet<Integer> HANDLED_KEYS =
      ImmutableSet.of(
          KeyEvent.KEYCODE_DPAD_LEFT,
          KeyEvent.KEYCODE_DPAD_RIGHT,
          KeyEvent.KEYCODE_DPAD_UP,
          KeyEvent.KEYCODE_DPAD_DOWN,
          KeyEvent.KEYCODE_DPAD_CENTER,
          KeyEvent.KEYCODE_ENTER,
          KeyEvent.KEYCODE_SEARCH);

  public TelevisionNavigationController(
      @NonNull TalkBackService service,
      @NonNull AccessibilityFocusMonitor accessibilityFocusMonitor,
      @NonNull InputMethodMonitor inputMethodMonitor,
      @NonNull PrimesController primesController,
      @NonNull ListMenuManager listMenuManager,
      Pipeline.@NonNull FeedbackReturner pipeline,
      boolean useHandlerThread) {
    this.service = service;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.inputMethodMonitor = inputMethodMonitor;
    this.pipeline = pipeline;
    this.primesController = primesController;
    this.useHandlerThread = useHandlerThread;
    this.listMenuManager = listMenuManager;

    final SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(service);
    prefs.registerOnSharedPreferenceChangeListener(treeDebugChangeListener);
    treeDebugPrefKey = service.getString(R.string.pref_tree_debug_key);
    treeDebugChangeListener.onSharedPreferenceChanged(prefs, treeDebugPrefKey);
    letSystemHandleDpadCenterWhenFocusNotInSync =
        TvNavigation.letSystemHandleDpadCenterWhenFocusNotInSync(this.service);
    timeout = Duration.ofMillis(TvNavigation.keyEventTimeoutMillis(this.service));
  }

  public void setShouldProcessDPadEvent(boolean shouldProcessEvent) {
    shouldProcessDPadKeyEvent = shouldProcessEvent;
  }

  public void onDestroy() {
    service.unregisterReceiver(treeDebugBroadcastReceiver);
  }

  @Override
  public boolean onKeyEvent(KeyEvent event, EventId eventId) {
    service.getInputModeTracker().setInputMode(INPUT_MODE_TV_REMOTE);

    if (!handlesKey(event.getKeyCode())) {
      return false;
    }

    // If we are using a handler thread, we have already tested for shouldHandleEvent.
    if (!useHandlerThread && !shouldHandleEvent(event, eventId)) {
      return false;
    }

    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      onKeyDown(event, eventId);
    } else {
      onKeyUp(event, eventId);
    }
    return true;
  }

  private void onKeyUp(KeyEvent event, @Nullable EventId eventId) {
    int keyCode = event.getKeyCode();

    if (isConfirmKey(keyCode) && hasTriggeredConfirmKeyLongPress) {
      isHoldingConfirmKey = false;
      hasTriggeredConfirmKeyLongPress = false;
      return;
    }

    if (isConfirmKey(keyCode)) {
      isHoldingConfirmKey = false;
      handler.removeMessages(TelevisionKeyHandler.WHAT_LONG_PRESS_CONFIRM_KEY);
    }

    // Drop the key press if it has happened too long ago, to not end up in an unresponsive state.
    if (eventId != null
        && SystemClock.uptimeMillis() - eventId.getEventTimeMs() > timeout.toMillis()) {
      return;
    }

    handleShortPress(event, eventId);
  }

  private void onKeyDown(KeyEvent keyEvent, @Nullable EventId eventId) {
    int keyCode = keyEvent.getKeyCode();
    if (isConfirmKey(keyCode) && isHoldingConfirmKey) {
      return; // Ignore new confirm key if one is already held down.
    }

    if (isConfirmKey(keyCode)) {
      isHoldingConfirmKey = true;
      handler.postLongPressConfirmKeyEvent(keyEvent, eventId);
    }
  }

  private void handleShortPress(KeyEvent event, @Nullable EventId eventId) {
    switch (event.getKeyCode()) {
      case KeyEvent.KEYCODE_DPAD_LEFT:
      case KeyEvent.KEYCODE_DPAD_RIGHT:
      case KeyEvent.KEYCODE_DPAD_UP:
      case KeyEvent.KEYCODE_DPAD_DOWN:
        // Directional navigation takes a non-trivial amount of time, so if we are not on a handler
        // thread, we should post to the local handler, and return true immediately.
        if (TvNavigation.useHandlerThread(service)) {
          onDirectionalKey(event.getKeyCode(), eventId);
        } else {
          handler.postDirectionalKeyEvent(event, eventId);
        }
        break;
      case KeyEvent.KEYCODE_DPAD_CENTER:
      case KeyEvent.KEYCODE_ENTER:
        // Note: handling the Enter key won't interfere with typing because
        // we skip key event handling above if the IME is visible. (See above:
        // this will also skip handling the Enter key if using a physical keyboard.)
        // Can't post to handler because the return value might vary.
        onCenterKey(eventId);
        break;
      case KeyEvent.KEYCODE_SEARCH:
        logNodeTreesOnAllDisplays();
        break;
      default:
        throw new AssertionError(
            String.format(
                "Trying to handle a key (keyCode=%d) that is not part of HANDLED_KEYS",
                event.getKeyCode()));
    }
  }

  private void handleLongPressConfirmKey(@Nullable EventId eventId) {
    pipeline.returnFeedback(eventId, Feedback.focus(LONG_CLICK_CURRENT));
    hasTriggeredConfirmKeyLongPress = true;
  }

  private void onDirectionalKey(int keyCode, @Nullable EventId eventId) {
    switch (mode) {
      case MODE_NAVIGATE:
        {
          @SearchDirectionOrUnknown int direction = SEARCH_FOCUS_UNKNOWN;
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
                    .setGranularity(DEFAULT)
                    .setInputMode(INPUT_MODE_TV_REMOTE)
                    .setScroll(true)
                    .setDefaultToInputFocus(true));
            if (eventId != null) {
              // We use keyEvent.getEventTime() as starting point because we don't know how long the
              // message was enqueued before onKeyEvent() has started.
              primesController.recordDuration(
                  TimerAction.DPAD_NAVIGATION,
                  eventId.getEventTimeMs(),
                  SystemClock.uptimeMillis());
            }
          }
        }
        break;
      case MODE_SEEK_CONTROL:
        {
          AccessibilityNodeInfoCompat cursor = getFocus(FocusType.ANY_FOCUS, eventId);
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

  private void onCenterKey(@Nullable EventId eventId) {
    switch (mode) {
      case MODE_NAVIGATE:
        AccessibilityNodeInfoCompat focusedNode = getFocus(FocusType.ANY_FOCUS, eventId);
        if (Role.getRole(focusedNode) == Role.ROLE_SEEK_CONTROL) {
          // Seek control, center key toggles seek control input mode instead of clicking.
          setMode(MODE_SEEK_CONTROL, eventId);
        } else if (shouldOpenLinkMenu(focusedNode)) {
          listMenuManager.showMenu(R.id.links_menu, eventId);
          listMenuTriggerNode = focusedNode;
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

  private boolean shouldTriggerClick(@Nullable AccessibilityNodeInfoCompat node) {
    AccessibilityNodeInfoCompat clickableAncestor =
        AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
            node, AccessibilityNodeInfoUtils.FILTER_CLICKABLE);
    return (clickableAncestor != null);
  }

  private boolean shouldOpenLinkMenu(@Nullable AccessibilityNodeInfoCompat node) {
    return (node != null)
        && !shouldTriggerClick(node)
        && !WebInterfaceUtils.supportsWebActions(node)
        && SpannableTraversalUtils.hasTargetSpanInNodeTreeDescription(node, TARGET_SPAN_CLASS);
  }

  /** Closes the link menu if the links refer to a node no longer on screen. */
  public void onWindowsChanged() {
    if (listMenuManager.isMenuShowing() && (listMenuTriggerNode != null)) {
      if (!listMenuTriggerNode.refresh()) {
        listMenuManager.dismissAll();
      }
    }
  }

  private void logNodeTreesOnAllDisplays() {
    AccessibilityServiceCompatUtils.forEachWindowInfoListOnAllDisplays(
        service, TreeDebug::logNodeTrees);
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
  public boolean shouldHandleEvent(@NonNull KeyEvent event, @Nullable EventId eventId) {
    // For new versions of Gboard, let TalkBack handle navigation. For other keyboards let them
    // do it themselves, i.e. let DPAD events pass through.
    // Note: on Android TV, it looks like the on-screen IME always appears, even when a physical
    // keyboard is connected, so this check will allow the system to handle all typing on
    // physical keyboards as well. This behavior is current as of Nougat.
    if (AccessibilityServiceCompatUtils.isInputWindowOnScreen(service)
        && !inputMethodMonitor.useInputWindowAsActiveWindow()) {
      return false;
    }

    // TalkBack should always consume directional events, unless shouldProcessDPadKeyEvent is false.
    // Otherwise, strange things will happen when TalkBack cannot navigate further.
    // For example, TalkBack cannot control the selected item in a ListView; the
    // view itself controls the selected item. So if the key event gets propagated to the
    // list view at the end of the list, the scrolling will jump to the highlighted item.
    switch (event.getKeyCode()) {
      case KeyEvent.KEYCODE_DPAD_UP:
      case KeyEvent.KEYCODE_DPAD_DOWN:
      case KeyEvent.KEYCODE_DPAD_LEFT:
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        return shouldProcessDPadKeyEvent;
      case KeyEvent.KEYCODE_DPAD_CENTER:
        return shouldProcessDPadKeyEvent && shouldHandleKeyCenter(event, eventId);
      case KeyEvent.KEYCODE_ENTER:
        return shouldHandleKeyCenter(event, eventId);
      case KeyEvent.KEYCODE_SEARCH:
        return treeDebugEnabled;
      default:
        return false;
    }
  }

  /**
   * {@link KeyEvent.KEYCODE_DPAD_CENTER} or {@link KeyEvent.KEYCODE_ENTER} will flip between
   * navigation and seek control mode. During navigation mode, only handle the event when the node
   * which a11y focus on is not null.
   */
  private boolean shouldHandleKeyCenter(@NonNull KeyEvent event, @Nullable EventId eventId) {
    if (isHoldingConfirmKey && event.getAction() == KeyEvent.ACTION_UP) {
      return true;
    }
    switch (mode) {
      case MODE_NAVIGATE:
        AccessibilityNodeInfoCompat a11yOrInputFocusedNode = getFocus(FocusType.ANY_FOCUS, eventId);
        if (Role.getRole(a11yOrInputFocusedNode) == Role.ROLE_SEEK_CONTROL) {
          return true;
        }
        AccessibilityNodeInfoCompat accessibilityFocus = getFocus(FocusType.A11Y_FOCUS, eventId);
        if (accessibilityFocus == null) {
          return false;
        }
        if (shouldTriggerClick(accessibilityFocus) || shouldOpenLinkMenu(accessibilityFocus)) {
          return true;
        }
        if (letSystemHandleDpadCenterWhenFocusNotInSync
            || WebInterfaceUtils.supportsWebActions(accessibilityFocus)) {
          return false;
        }
        AccessibilityNodeInfoCompat inputFocus = accessibilityFocusMonitor.getInputFocus();
        return !accessibilityFocus.equals(inputFocus);
      case MODE_SEEK_CONTROL:
        return true;
      default: // fall out
    }
    return true;
  }

  private void setMode(@RemoteMode int newMode, @Nullable EventId eventId) {
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
                FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE
                    | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE
                    | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE);
    pipeline.returnFeedback(eventId, Feedback.speech(ttsText, speakOptions));

    mode = newMode;
  }

  /** Silently resets the remote mode to navigate mode. */
  public void resetToNavigateMode() {
    mode = MODE_NAVIGATE;
  }

  private @Nullable AccessibilityNodeInfoCompat getFocus(
      FocusType focusType, @Nullable EventId eventId) {
    if (eventId == null) {
      return accessibilityFocusMonitor.getAccessibilityFocus(focusType.useInputFocusIfEmpty);
    }
    CachedFocus cachedFocus = focusCache.get(focusType);
    if (cachedFocus == null || !cachedFocus.eventId.equals(eventId)) {
      AccessibilityNodeInfoCompat node =
          accessibilityFocusMonitor.getAccessibilityFocus(focusType.useInputFocusIfEmpty);
      if (node == null) {
        return null;
      }
      cachedFocus = new CachedFocus(node, eventId);
      focusCache.put(focusType, cachedFocus);
    }
    return cachedFocus.node;
  }

  /**
   * Message handler to allow onKeyEvent() to return before timeout, while handler finishes key
   * processing later.
   */
  private static class TelevisionKeyHandler
      extends WeakReferenceHandler<TelevisionNavigationController> {
    private static final int WHAT_DIRECTIONAL = 1;
    private static final int WHAT_LONG_PRESS_CONFIRM_KEY = 2;

    public TelevisionKeyHandler(TelevisionNavigationController parent) {
      super(parent);
    }

    @Override
    protected void handleMessage(Message msg, TelevisionNavigationController parent) {
      int keyCode = msg.arg1;
      EventId eventId = (EventId) msg.obj;

      switch (msg.what) {
        case WHAT_DIRECTIONAL:
          parent.onDirectionalKey(keyCode, eventId);
          break;
        case WHAT_LONG_PRESS_CONFIRM_KEY:
          parent.handleLongPressConfirmKey(eventId);
          break;
        default: // fall out
      }
    }

    public void postDirectionalKeyEvent(KeyEvent event, @Nullable EventId eventId) {
      Message msg = obtainMessage(WHAT_DIRECTIONAL, event.getKeyCode(), 0, eventId);
      sendMessageDelayed(msg, 0);
    }

    public void postLongPressConfirmKeyEvent(KeyEvent event, @Nullable EventId eventId) {
      Message msg = obtainMessage(WHAT_LONG_PRESS_CONFIRM_KEY, event.getKeyCode(), 0, eventId);
      sendMessageDelayed(msg, ViewConfiguration.getLongPressTimeout());
    }
  }

  private final OnSharedPreferenceChangeListener treeDebugChangeListener =
      new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
          if (key != null && key.equals(treeDebugPrefKey)) {
            treeDebugEnabled =
                PreferencesActivityUtils.getDiagnosticPref(
                    sharedPreferences,
                    service.getResources(),
                    R.string.pref_tree_debug_key,
                    R.bool.pref_tree_debug_default);
            if (treeDebugEnabled) {
              ContextCompat.registerReceiver(
                  service,
                  treeDebugBroadcastReceiver,
                  new IntentFilter(PRINT_TREE_DEBUG_ACTION),
                  RECEIVER_EXPORTED);
            } else {
              service.unregisterReceiver(treeDebugBroadcastReceiver);
            }
          }
        }
      };

  private enum FocusType {
    A11Y_FOCUS(false),
    ANY_FOCUS(true);

    final boolean useInputFocusIfEmpty;

    FocusType(boolean useInputFocusIfEmpty) {
      this.useInputFocusIfEmpty = useInputFocusIfEmpty;
    }
  };

  /** Helper class for caching focus between {@link #shouldHandleEvent} and {@link #onCenterKey}. */
  private static class CachedFocus {
    @NonNull AccessibilityNodeInfoCompat node;
    @NonNull EventId eventId;

    private CachedFocus(@NonNull AccessibilityNodeInfoCompat node, @NonNull EventId eventId) {
      this.node = node;
      this.eventId = eventId;
    }
  }

  private static boolean isConfirmKey(int keyCode) {
    return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER;
  }

  /**
   * Returns whether the TelevisionNavigationController is interested in key events with the given
   * {@code keyCode} at all.
   */
  public static boolean handlesKey(int keyCode) {
    return HANDLED_KEYS.contains(keyCode);
  }
}
