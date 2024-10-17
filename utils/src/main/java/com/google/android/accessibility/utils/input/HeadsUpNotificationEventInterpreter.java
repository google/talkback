/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.accessibility.utils.input;

import static com.google.android.accessibility.utils.Role.ROLE_SCROLL_VIEW;
import static com.google.android.accessibility.utils.StringBuilderUtils.optionalTag;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils;
import com.google.android.accessibility.utils.Consumer;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * This detects if a heads-up notification has appeared or disappeared. The notification window may
 * not appear before the TYPE_NOTIFICATION_STATE_CHANGED event is received, so we check when a
 * WINDOW_CHANGED_ADDED event is received. To determine if the notification has disappeared we wait
 * for WINDOWS_CHANGED_REMOVED and check against the notifications window id.
 */
public class HeadsUpNotificationEventInterpreter implements AccessibilityEventListener {

  /** Event types that are handled by HeadsUpNotificationEventInterpreter. */
  private static final int MASK_EVENTS =
      AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOWS_CHANGED;

  private static final int EVENT_WINDOW_ADDED_MILLIS = 1000;
  private static final String TAG = "HeadsUpNotificationEventInterpreter";

  private final List<Consumer<Interpretation>> listeners = new ArrayList<>();
  private final AccessibilityService service;
  private int headsUpNotificationWindowId = -1;
  private int headsUpNotificationDisplayId = -1;
  private boolean headsUpNotificationTracked = false;
  private long lastNotificationAppearanceTime = -1;

  public HeadsUpNotificationEventInterpreter(AccessibilityService service) {
    LogUtils.v(TAG, "HeadsUpNotificationEventInterpreter constructor");
    this.service = service;
  }

  /** Data-structure containing raw-event and interpretation, sent to listeners. */
  public static class Interpretation {
    public final @NonNull AccessibilityEvent event;
    public final @NonNull EventId eventId;
    public final boolean isHeadsUpAppearance;
    public final @Nullable AccessibilityNodeInfoCompat notificationGuess;

    public Interpretation(
        @NonNull AccessibilityEvent event,
        @NonNull EventId eventId,
        boolean isHeadsUpAppearance,
        @Nullable AccessibilityNodeInfoCompat notificationGuess) {
      this.event = event;
      this.eventId = eventId;
      this.isHeadsUpAppearance = isHeadsUpAppearance;
      this.notificationGuess = notificationGuess;
    }

    @Override
    public String toString() {
      return StringBuilderUtils.joinFields(
          eventId.toString(), optionalTag("appeared", isHeadsUpAppearance));
    }
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS;
  }

  public void addListener(Consumer<Interpretation> listener) {
    listeners.add(listener);
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    // This depends on window changes being available, since otherwise it may be too brittle to try
    // to detect the notification appearances and disappearances.
    if (!FeatureSupport.windowChanges()) {
      return;
    }
    if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
      if (headsUpNotificationTracked) {
        return;
      }
      AccessibilityNodeInfoCompat headsUpNotification = findHeadsUpNotification(-1, -1);
      if (headsUpNotification != null) {
        headsUpNotificationTracked = true;
        notifyListeners(headsUpNotification, event, eventId);
      } else {
        // Track the notification time. The window and node may not appear until after this event is
        // received, so windows added within a certain time frame should be checked.
        lastNotificationAppearanceTime = SystemClock.uptimeMillis();
      }
    } else if (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
      if ((event.getWindowChanges() & AccessibilityEvent.WINDOWS_CHANGE_REMOVED) != 0) {
        if (!headsUpNotificationTracked) {
          return;
        }

        boolean headsUpWindowDisappeared =
            event.getWindowId() == headsUpNotificationWindowId
                && AccessibilityEventUtils.getDisplayId(event) == headsUpNotificationDisplayId;
        if (headsUpWindowDisappeared) {
          headsUpNotificationWindowId = -1;
          headsUpNotificationDisplayId = -1;
          headsUpNotificationTracked = false;
          notifyListeners(null, event, eventId);
        }
      } else if ((event.getWindowChanges() & AccessibilityEvent.WINDOWS_CHANGE_ADDED) != 0) {
        if (headsUpNotificationTracked) {
          return;
        }
        long currentTime = SystemClock.uptimeMillis();
        // Since the event may arrive before the window, check windows added within a second of the
        // last notification event time.
        if (currentTime - lastNotificationAppearanceTime < EVENT_WINDOW_ADDED_MILLIS) {
          AccessibilityNodeInfoCompat headsUpNotification =
              findHeadsUpNotification(
                  event.getWindowId(), AccessibilityEventUtils.getDisplayId(event));
          if (headsUpNotification != null) {
            headsUpNotificationTracked = true;
            notifyListeners(headsUpNotification, event, eventId);
          }
        }
      }
    }
  }

  private @Nullable AccessibilityNodeInfoCompat findHeadsUpNotification(
      int addedWindowId, int addedWindowDisplayId) {
    AccessibilityNodeInfoCompat notificationGuess = null;
    SparseArray<List<AccessibilityWindowInfo>> windowsOnAllDisplays =
        AccessibilityServiceCompatUtils.getWindowsOnAllDisplays(service);

    for (int displayId = 0; displayId < windowsOnAllDisplays.size(); displayId++) {
      @Nullable List<AccessibilityWindowInfo> windows = windowsOnAllDisplays.get(displayId);
      if ((addedWindowDisplayId != -1 && addedWindowDisplayId != displayId) || windows == null) {
        continue;
      }
      for (AccessibilityWindowInfo window : windows) {
        if (window.getType() != AccessibilityWindowInfo.TYPE_SYSTEM) {
          continue;
        }

        if (addedWindowId != -1 && window.getId() != addedWindowId) {
          continue;
        }

        AccessibilityNodeInfoCompat windowRoot = AccessibilityWindowInfoUtils.getRootCompat(window);
        if (windowRoot == null) {
          continue;
        }

        notificationGuess =
            AccessibilityNodeInfoUtils.getMatchingDescendant(
                windowRoot,
                new Filter<AccessibilityNodeInfoCompat>() {
                  @Override
                  public boolean accept(AccessibilityNodeInfoCompat node) {
                    boolean hasResourceId = false;
                    if (node.getViewIdResourceName() != null) {
                      hasResourceId =
                          node.getViewIdResourceName().contains("expandableNotificationRow");
                    }

                    AccessibilityNodeInfoCompat parent = node.getParent();
                    if (parent == null) {
                      return false;
                    }
                    boolean hasScrollViewParent = Role.getRole(parent) == ROLE_SCROLL_VIEW;
                    LogUtils.v(
                        TAG,
                        "node=%s hasScrollViewParent=%b hasResourceId=%b",
                        node,
                        hasScrollViewParent,
                        hasResourceId);
                    return hasScrollViewParent && hasResourceId;
                  }
                });
        if (notificationGuess != null) {
          headsUpNotificationWindowId = window.getId();
          headsUpNotificationDisplayId = AccessibilityWindowInfoUtils.getDisplayId(window);
          return notificationGuess;
        }
      }
    }

    return null;
  }

  private void notifyListeners(
      @Nullable AccessibilityNodeInfoCompat notification,
      AccessibilityEvent event,
      EventId eventId) {
    boolean appeared = notification != null;
    LogUtils.v(TAG, "heads up notification %s appeared=%b", notification, appeared);
    Interpretation interpretation = new Interpretation(event, eventId, appeared, notification);
    for (Consumer<Interpretation> listener : listeners) {
      listener.accept(interpretation);
    }
  }
}
