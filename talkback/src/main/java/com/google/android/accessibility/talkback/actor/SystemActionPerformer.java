package com.google.android.accessibility.talkback.actor;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;

/** System/global action performer */
public final class SystemActionPerformer {

  private final AccessibilityService service;

  // Keep list in sync with global action constants defined in AccessibilityService:
  // android/frameworks/base/core/java/android/accessibilityservice/AccessibilityService.java
  // Use new constants when available
  public static final int GLOBAL_ACTION_KEYCODE_HEADSETHOOK = 10;
  public static final int GLOBAL_ACTION_ACCESSIBILITY_BUTTON = 11;
  public static final int GLOBAL_ACTION_ACCESSIBILITY_BUTTON_CHOOSER = 12;
  public static final int GLOBAL_ACTION_ACCESSIBILITY_SHORTCUT = 13;
  // TODO: Uses AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS and also
  // removes GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS_LEGACY in Android S
  public static final int GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS = 14;
  private static final int GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS_LEGACY = 100;

  public static final ImmutableList<Integer> EXCLUDED_ACTIONS =
      ImmutableList.copyOf(
          Arrays.asList(
              // GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN is not included by default in getSystemActions()
              // Dedicated default gestures assignments
              AccessibilityService.GLOBAL_ACTION_BACK, // 1
              AccessibilityService.GLOBAL_ACTION_HOME, // 2
              AccessibilityService.GLOBAL_ACTION_RECENTS, // 3
              AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, // 4
              GLOBAL_ACTION_KEYCODE_HEADSETHOOK, // 10
              GLOBAL_ACTION_ACCESSIBILITY_BUTTON, // 11
              GLOBAL_ACTION_ACCESSIBILITY_BUTTON_CHOOSER, // 12
              GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS, // 14
              GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS_LEGACY, // 100
              // Available using hardware keys
              AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, // 6
              AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, // 8
              AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, // 9
              GLOBAL_ACTION_ACCESSIBILITY_SHORTCUT)); // 13

  public SystemActionPerformer(AccessibilityService service) {
    this.service = service;
  }

  public boolean performAction(int id) {
    // AccessibilityService.GLOBAL_ACTION_KEYCODE_HEADSETHOOK won't be displayed in the list until S
    // TODO remove this headset check in S
    if (FeatureSupport.supportSystemActions() && id != GLOBAL_ACTION_KEYCODE_HEADSETHOOK) {
      List<AccessibilityAction> actionList = service.getSystemActions();
      // Equality is by id
      if (actionList.contains(new AccessibilityAction(id, null))) {
        return service.performGlobalAction(id);
      } else if (id == GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS
          && actionList.contains(
              new AccessibilityAction(GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS_LEGACY, null))) {
        // It's for the legacy. All-apps action ID is changed on most Android R devices.
        return service.performGlobalAction(GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS_LEGACY);
      }
    } else {
      return service.performGlobalAction(id);
    }
    return false;
  }
}
