/*
 * Copyright (C) 2023 Google Inc.
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
package com.google.android.accessibility.talkback;

import static com.google.android.accessibility.talkback.TalkBackExitController.TalkBackMistriggeringRecoveryType.TYPE_AUTOMATIC_TURNOFF_LOCKSCREEN;
import static com.google.android.accessibility.talkback.TalkBackExitController.TalkBackMistriggeringRecoveryType.TYPE_AUTOMATIC_TURNOFF_SHUTDOWN;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_WELCOME_TO_TALKBACK;

import android.os.SystemClock;
import android.text.TextUtils;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.talkback.RingerModeAndScreenMonitor.ScreenChangedListener;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.monitor.ScreenMonitor;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * Controller for TalkBack mis-triggering recovery.
 *
 * <ul>
 *   Turns off TalkBack settings
 *   <li>1. When TalkBack-exit button is single-tapped by the user,
 * </ul>
 *
 * <ul>
 *   TalkBack automatically turns off TalkBack settings
 *   <li>When it is device shutdown, the training is active and the current training page is the
 *       welcome-to-TalkBack,
 *   <li>When it enters lockscreen mode, the training is active and the current training page is the
 *       welcome-to-TalkBack,
 * </ul>
 */
public class TalkBackExitController implements AccessibilityEventListener, ScreenChangedListener {

  /** Interfaces to get training state for talkback automatic turn-off. */
  public interface TrainingState {
    /** Returns the current training page ID. */
    PageId getCurrentPageId();

    /** Returns {@code true} if training is active recently. */
    boolean isTrainingRecentActive();
  }

  /** TalkBack mis-triggering recovery type. See TalkBackMistriggeringRecoveryEnums. */
  public enum TalkBackMistriggeringRecoveryType {
    TYPE_UNSPECIFIED,
    TYPE_TALKBACK_EXIT_BANNER,
    TYPE_AUTOMATIC_TURNOFF_LOCKSCREEN,
    TYPE_AUTOMATIC_TURNOFF_SHUTDOWN,
  }

  private TrainingState trainingState;

  private static final String TAG = "TalkBackExitController";
  private static final String EXIT_BUTTON_RES_NAME =
      "com.google.android.marvin.talkback:id/training_exit_talkback_button";
  private static final long TAP_TIMEOUT_MS = ViewConfiguration.getJumpTapTimeout();

  private final TalkBackService service;

  private ActorState actorState;

  private long touchInteractionStartTime;

  /** The button in Tutorial talkback-exit banner. */
  private AccessibilityNodeInfo targetNode = null;

  public TalkBackExitController(TalkBackService service) {
    this.service = service;
  }

  @Override
  public int getEventTypes() {
    return AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
        | AccessibilityEvent.TYPE_TOUCH_INTERACTION_END
        | AccessibilityEvent.TYPE_VIEW_HOVER_ENTER;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    AccessibilityNodeInfo node = event.getSource();
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START:
        touchInteractionStartTime = SystemClock.uptimeMillis();
        targetNode = null;
        break;
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END:
        // ExitBanner handles the click event action.
        if (targetNode != null
            && ((SystemClock.uptimeMillis() - touchInteractionStartTime) < TAP_TIMEOUT_MS)) {
          targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
          targetNode = null;
        }
        break;
      case AccessibilityEvent.TYPE_VIEW_HOVER_ENTER:
        String viewResIdName = node == null ? "" : node.getViewIdResourceName();
        targetNode = TextUtils.equals(viewResIdName, EXIT_BUTTON_RES_NAME) ? node : null;
        break;
      default: // fall out
    }
  }

  public void onShutDown() {
    if (trainingState == null || !FeatureFlagReader.allowAutomaticTurnOff(service)) {
      return;
    }
    LogUtils.d(TAG, "onShutDown: ");
    turnOffTalkBackIfTutorialActive(TYPE_AUTOMATIC_TURNOFF_SHUTDOWN.ordinal());
  }

  @Override
  public void onScreenChanged(boolean isInteractive, EventId eventId) {
    // TODO optimize the conditions gradually
    if (actorState == null || trainingState == null) {
      return;
    }
    int lastPerformedSystemAction = actorState.getLastSystemAction();
    boolean isDeviceLocked = ScreenMonitor.isDeviceLocked(service);
    LogUtils.d(
        TAG,
        "onScreenChanged: isDeviceLocked=%b , lastPerformedSystemAction=%d",
        isDeviceLocked,
        lastPerformedSystemAction);
    // The device is locked and no system action performed since TalkBack is on.
    if (isDeviceLocked && lastPerformedSystemAction == 0) {
      turnOffTalkBackIfTutorialActive(TYPE_AUTOMATIC_TURNOFF_LOCKSCREEN.ordinal());
    }
  }

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  public void setTrainingState(TrainingState state) {
    trainingState = state;
  }

  private void turnOffTalkBackIfTutorialActive(int recoveryType) {
    boolean trainingRecentActive = trainingState.isTrainingRecentActive();
    PageId currentPageId = trainingState.getCurrentPageId();
    LogUtils.w(
        TAG,
        "turnOffTalkBackIfTutorialActive:  trainingActive=%b, current pageId=%d",
        trainingRecentActive,
        currentPageId.ordinal());
    if (!service.hasTrainingFinishedByUser()
        && trainingRecentActive
        && currentPageId == PAGE_ID_WELCOME_TO_TALKBACK) {
      service.requestDisableTalkBack(recoveryType);
    }
  }
}
