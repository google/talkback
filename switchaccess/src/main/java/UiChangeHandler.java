/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.switchaccess;

import android.os.Handler;
import android.os.Trace;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.switchaccess.SwitchAccessService;
import com.google.android.accessibility.switchaccess.PerformanceMonitor.TreeBuildingEvent;
import com.google.android.accessibility.switchaccess.UiChangeStabilizer.WindowChangedListener;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessMenuTypeEnum.MenuType;
import com.google.android.accessibility.switchaccess.treebuilding.MainTreeBuilder;
import com.google.android.accessibility.switchaccess.treenodes.ClearOverlayNode;
import com.google.android.accessibility.switchaccess.treenodes.ShowGlobalMenuNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanNode;
import com.google.android.accessibility.switchaccess.ui.OverlayController;
import com.google.android.accessibility.switchaccess.ui.SwitchAccessActionsMenuLayout;
import com.google.android.accessibility.switchaccess.ui.SwitchAccessGlobalMenuLayout;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.Performance;
import com.google.android.libraries.accessibility.utils.concurrent.ThreadUtils;
import com.google.common.collect.ImmutableMap;
import java.util.List;

/**
 * Class responsible for handling UI changes by rebuilding the scan tree and notifying the {@link
 * WindowChangedListener}.
 */
public class UiChangeHandler implements UiChangeStabilizer.UiChangedListener {

  private final SwitchAccessService service;
  private final Handler handler;
  private final MainTreeBuilder mainTreeBuilder;
  private final OverlayController overlayController;
  private final OptionManager optionManager;
  private final PointScanManager pointScanManager;

  // Rebuild the tree in a background thread.
  private Runnable rebuildScanTreeRunnable;

  private boolean isRunning;

  private final ImmutableMap<CharSequence, MenuType> switchAccessMenus =
      ImmutableMap.<CharSequence, MenuType>builder()
          .put(SwitchAccessGlobalMenuLayout.class.getName(), MenuType.TYPE_GLOBAL)
          .put(SwitchAccessActionsMenuLayout.class.getName(), MenuType.TYPE_ACTION)
          .build();

  /**
   * Constructs a new UI change handler.
   *
   * @param service AccessibilityService that will handle UI changes
   * @param treeBuilder builder that constructs a tree of {@link TreeScanNode}s to scan from a list
   *     of windows
   * @param optionManager manager that handles options in the scan tree
   * @param overlayController controller for the overlay on which to present options
   * @param pointScanManager manager that handles point scanning
   * @param backgroundThreadHandler background thread handler to rebuild the scan tree
   */
  public UiChangeHandler(
      SwitchAccessService service,
      MainTreeBuilder treeBuilder,
      OptionManager optionManager,
      OverlayController overlayController,
      PointScanManager pointScanManager,
      Handler backgroundThreadHandler) {
    this.service = service;
    this.mainTreeBuilder = treeBuilder;
    this.optionManager = optionManager;
    this.overlayController = overlayController;
    this.pointScanManager = pointScanManager;
    this.handler = backgroundThreadHandler;
    isRunning = true;
  }

  /**
   * Cleans up when this object is no longer needed. Calling this method will prevent the object
   * from doing additional work if other methods are called after #shutdown is called.
   */
  public void shutdown() {
    // Remove the callbacks from the handler on shutdown.
    ThreadUtils.removeCallbacksAndMessages(null);
    isRunning = false;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The windowChangedListener passed into the method will be notified of the window changes
   * after the tree is rebuilt.
   */
  @Override
  public void onUiChangedAndIsNowStable(
      WindowChangedListener windowChangedListener, List<AccessibilityEvent> windowChangeEventList) {
    Trace.beginSection("UiChangeHandler#onUiChangedAndIsNowStable");
    if (SwitchAccessPreferenceUtils.isPointScanEnabled(service)) {
      // Only reset scan if we're not already scanning so as not to throw users off with the
      // cursor randomly jumping to the beginning.
      pointScanManager.onUiChanged();
      sendWindowChangeEventsToWindowChangedListener(windowChangedListener, windowChangeEventList);
    } else {
      PerformanceMonitor.getOrCreateInstance()
          .startNewTimerEvent(TreeBuildingEvent.REBUILD_TREE_AND_UPDATE_FOCUS);
      handler.removeCallbacks(rebuildScanTreeRunnable);
      rebuildScanTreeRunnable =
          () -> {
            rebuildScanTree(windowChangedListener, windowChangeEventList);
          };
      handler.post(rebuildScanTreeRunnable);
      PerformanceMonitor.getOrCreateInstance()
          .stopTimerEvent(TreeBuildingEvent.REBUILD_TREE_AND_UPDATE_FOCUS, true);
    }
    Trace.endSection();
  }

  // This method will be run on a background thread.
  private void rebuildScanTree(
      WindowChangedListener windowChangedListener, List<AccessibilityEvent> windowChangeEventList) {
    Trace.beginSection("UiChangeHandler#rebuildScanTree");
    TreeScanNode firstOrLastNode;
    boolean shouldPlaceNodeFirst;
    if (overlayController.isMenuVisible()) {
      firstOrLastNode = new ClearOverlayNode(overlayController);
      shouldPlaceNodeFirst = false;
    } else {
      firstOrLastNode = new ShowGlobalMenuNode(overlayController);
      shouldPlaceNodeFirst = true;
    }

    PerformanceMonitor.getOrCreateInstance().startNewTimerEvent(TreeBuildingEvent.REBUILD_TREE);
    TreeScanNode treeScanNode =
        mainTreeBuilder.addWindowListToTree(
            SwitchAccessWindowInfo.convertZOrderWindowList(
                AccessibilityServiceCompatUtils.getWindows(service)),
            firstOrLastNode,
            shouldPlaceNodeFirst);
    PerformanceMonitor.getOrCreateInstance().stopTimerEvent(TreeBuildingEvent.REBUILD_TREE, true);
    ThreadUtils.runOnMainThread(
        () -> !isRunning,
        () -> {
          optionManager.clearFocusIfNewTree(treeScanNode);
          // After the focus is cleared, send the list of AccessibilityEvents generated by the UI
          // change to the feedback controller to generate screen hints for the UI change.
          sendWindowChangeEventsToWindowChangedListener(
              windowChangedListener, windowChangeEventList);
        });
    Trace.endSection();
  }

  private void sendWindowChangeEventsToWindowChangedListener(
      WindowChangedListener windowChangedListener, List<AccessibilityEvent> windowChangeEventList) {
    if (isRunning) {
      while (!windowChangeEventList.isEmpty()) {
        AccessibilityEvent event = windowChangeEventList.get(0);

        CharSequence packageName = event.getPackageName();
        if (service.getPackageName().equals(packageName)) {
          AccessibilityNodeInfo info = event.getSource();
          if (info != null) {
            CharSequence className = info.getClassName();
            // Check whether the window events in the event list are triggered by opening the
            // Switch Access menu window. If so, clears the event list and calls
            // WindowChangeListener#onSwitchAccessMenuShown to generate screen feedback for
            // the Switch Access menu.
            //
            // We use two criteria to check if a window is a Switch Access menu window:
            // 1. The package of the event is the same as the Switch Access Accessibility
            //    Service.
            // 2. The source AccessibilityNodeInfoCompat of the event has the same class name
            //    as SwitchAccessMenuOverlay.
            if (switchAccessMenus.containsKey(className)) {
              windowChangedListener.onSwitchAccessMenuShown(switchAccessMenus.get(className));
              windowChangeEventList.clear();
              return;
            }
          }
        }

        windowChangedListener.onWindowChangedAndIsNowStable(
            windowChangeEventList.get(0), Performance.EVENT_ID_UNTRACKED);
        windowChangeEventList.remove(0);
      }
    }
  }
}
