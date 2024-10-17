package com.google.android.accessibility.talkback;

import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_ADDED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_REMOVED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_TITLE;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD;
import static androidx.core.view.accessibility.AccessibilityEventCompat.CONTENT_CHANGE_TYPE_PANE_APPEARED;
import static androidx.core.view.accessibility.AccessibilityEventCompat.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED;
import static com.google.android.accessibility.utils.Performance.MOTION_EVENT_DIRECTION_BACKWARD;
import static com.google.android.accessibility.utils.Performance.MOTION_EVENT_DIRECTION_FORWARD;
import static com.google.android.accessibility.utils.Performance.MOTION_EVENT_DIRECTION_UNDEFINED;
import static com.google.android.accessibility.utils.input.CursorGranularity.DEFAULT;
import static com.google.android.accessibility.utils.monitor.InputModeTracker.INPUT_MODE_TOUCH;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_BACKWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.TargetApi;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.view.ViewConfigurationCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Pipeline.FeedbackReturner;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.DisplayUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.Role.RoleName;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * A controller to decide whether to intercept rotary encoder events and handle it if needed.
 *
 * <p>The methods annotated as {@code @WorkerThread} should be run in the {@link
 * MotionEventController#backgroundHandler}.
 */
@TargetApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
public class MotionEventController implements AccessibilityEventListener {

  private static final String TAG = "MotionEventController";

  private static final int TYPE_WINDOWS_CHANGE_FOR_RESET =
      WINDOWS_CHANGE_ADDED | WINDOWS_CHANGE_REMOVED | WINDOWS_CHANGE_TITLE;

  private static final int TYPE_WINDOW_STATE_CHANGE_FOR_RESET =
      CONTENT_CHANGE_TYPE_PANE_APPEARED | CONTENT_CHANGE_TYPE_PANE_DISAPPEARED;

  private static final int THROTTLE_MS = 50;
  // This is an experience value which is approximately equal to a default height for a
  // WearChipButton.
  private static final int THRESHOLD_SCROLL_DP = 52;

  private final int thresholdScrollPx;
  private final float scaledVerticalScrollFactor;

  private final AccessibilityService service;
  private final FeedbackReturner feedbackReturner;
  private final FeedbackController feedbackController;

  private final MotionEventHandler backgroundHandler;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  private boolean isHandlingMotionEvent;

  private float axisSum = 0;

  // We update these current nodes from TYPE_VIEW_ACCESSIBILITY_FOCUSED and TYPE_VIEW_SCROLLED.
  // Besides, we will make sure that the currentScrollableNodeForFocus is the descendant of the
  // currentFocusedNodeInScrollable.
  private AccessibilityNodeInfoCompat currentScrollableNodeForFocus;
  private AccessibilityNodeInfoCompat currentFocusedNodeInScrollable;

  public MotionEventController(
      AccessibilityService service,
      FeedbackReturner feedbackReturner,
      FeedbackController feedbackController) {
    this.service = service;
    this.feedbackReturner = feedbackReturner;
    this.feedbackController = feedbackController;

    scaledVerticalScrollFactor =
        ViewConfigurationCompat.getScaledVerticalScrollFactor(
            ViewConfiguration.get(service), service);
    thresholdScrollPx = DisplayUtils.dpToPx(service, THRESHOLD_SCROLL_DP);

    AccessibilityServiceInfo info = service.getServiceInfo();
    if (info != null) {
      isHandlingMotionEvent =
          (info.getMotionEventSources() & InputDevice.SOURCE_ROTARY_ENCODER) != 0;
    } else {
      LogUtils.w(TAG, "AccessibilityServiceInfo is null while initializing isHandlingMotionEvent.");
    }

    HandlerThread handlerThread = new HandlerThread(TAG);
    handlerThread.start();
    backgroundHandler = new MotionEventHandler(this, handlerThread.getLooper());
  }

  @Override
  public int getEventTypes() {
    return TYPE_VIEW_ACCESSIBILITY_FOCUSED
        | TYPE_VIEW_SCROLLED
        | TYPE_WINDOWS_CHANGED
        | TYPE_WINDOW_STATE_CHANGED;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    backgroundHandler.post(
        () -> {
          updateCurrentNodes(event);
          interceptRotaryEncoderIfNeeded();
        });
  }

  public void onMotionEvent(@NonNull MotionEvent event) {
    if (event.getSource() != InputDevice.SOURCE_ROTARY_ENCODER) {
      // Currently, skipping other sources, we only handle SOURCE_ROTARY_ENCODER in this class.
      return;
    }

    float axisValue = event.getAxisValue(MotionEvent.AXIS_SCROLL);
    backgroundHandler.post(() -> updateAxisSum(axisValue));
    backgroundHandler.postDelayPerformActionIfEmptyMessage();
  }

  public void shutdown() {
    LogUtils.v(TAG, "shutdown: MotionEventController is going to shutdown.");
    backgroundHandler.getLooper().quit();
  }

  @WorkerThread
  private void updateAxisSum(float axisValue) {
    float scrollPx = axisValue * scaledVerticalScrollFactor;
    if (axisSum * scrollPx > 0) {
      axisSum += scrollPx;
    } else {
      // We reset axisSum as the 1st opposite direction value if a users scroll in a different
      // direction to the previous scrolling.
      axisSum = scrollPx;
    }
  }

  @WorkerThread
  private void performActionIfNeeded(EventId eventId) {
    Feedback.FocusDirection.Builder feedback = null;
    int direction = MOTION_EVENT_DIRECTION_UNDEFINED;

    if (axisSum > thresholdScrollPx) {
      feedback = Feedback.focusDirection(SEARCH_FOCUS_BACKWARD);
      direction = MOTION_EVENT_DIRECTION_BACKWARD;

      axisSum -= thresholdScrollPx;
    } else if (axisSum < -thresholdScrollPx) {
      feedback = Feedback.focusDirection(SEARCH_FOCUS_FORWARD);
      direction = MOTION_EVENT_DIRECTION_FORWARD;

      axisSum += thresholdScrollPx;
    }

    if (direction == MOTION_EVENT_DIRECTION_UNDEFINED) {
      return;
    }

    // Sets granularity to default because previous/next action always moves at default granularity.
    feedback
        .setGranularity(DEFAULT)
        .setInputMode(INPUT_MODE_TOUCH)
        .setDefaultToInputFocus(true)
        .setScroll(true)
        .setWrap(false);

    final Feedback.FocusDirection.Builder currentFeedback = feedback;

    mainHandler.post(
        () -> {
          feedbackController.playAuditory(R.raw.gesture_end, eventId);
          boolean success = feedbackReturner.returnFeedback(eventId, currentFeedback);
          if (!success) {
            // If we reach an edge, we don't need to intercept motion events and we can just
            // fallback to original scrolling. It is helpful to avoid missing animation like the
            // dismiss of quick settings and notification tray by scrolling RSB.
            LogUtils.v(TAG, "performActionIfNeeded: returnFeedback failed");
            backgroundHandler.post(
                () -> interceptRotaryEncoderInternal(/* shouldHandleMotionEvent= */ false));
          }
        });
  }

  @WorkerThread
  private void updateCurrentNodes(AccessibilityEvent event) {
    LogUtils.v(TAG, "updateCurrentNodes on event: " + event);
    int eventType = event.getEventType();
    switch (eventType) {
      case TYPE_VIEW_ACCESSIBILITY_FOCUSED:
        updateFocusedNode(event);
        break;
      case TYPE_VIEW_SCROLLED:
        updateScrollableNode(event);
        break;
      case TYPE_WINDOWS_CHANGED:
        if ((event.getWindowChanges() & TYPE_WINDOWS_CHANGE_FOR_RESET) != 0) {
          resetCurrentNodes();
        }
        break;
      case TYPE_WINDOW_STATE_CHANGED:
        if ((event.getContentChangeTypes() & TYPE_WINDOW_STATE_CHANGE_FOR_RESET) != 0) {
          resetCurrentNodes();
        }
        break;
      default:
        LogUtils.w(TAG, "updateCurrentNodes: Shouldn't process this event. (event=%s)", event);
    }
  }

  private void resetCurrentNodes() {
    currentScrollableNodeForFocus = null;
    currentFocusedNodeInScrollable = null;
    axisSum = 0;
  }

  private void updateFocusedNode(AccessibilityEvent event) {
    AccessibilityNodeInfoCompat focusedNode =
        AccessibilityNodeInfoUtils.toCompat(event.getSource());

    AccessibilityNodeInfoCompat scrollableContainerNode = getScrollableRoleContainer(focusedNode);
    boolean focusedNodeInScrollableContainer = scrollableContainerNode != null;

    if (focusedNodeInScrollableContainer) {
      currentFocusedNodeInScrollable = focusedNode;
      if (currentScrollableNodeForFocus == null && focusedNode != null) {
        // There are 2 situations that currentScrollableNodeForFocus is null so we use
        // scrollableContainerNode as a backup suggestion.
        //
        // 1. After we enter a page, we might haven't received TYPE_VIEW_SCROLLED and the
        // currentScrollableNodeForFocus is null. So, we set it from the focused node's ancestor if
        // the ancestor is a scrollable container.
        // 2. The last source node from TYPE_VIEW_SCROLLED is not the ancestor of the that time
        // focused node.
        currentScrollableNodeForFocus = scrollableContainerNode;
      }
    } else {
      currentFocusedNodeInScrollable = null;
    }
  }

  private void updateScrollableNode(AccessibilityEvent event) {
    AccessibilityNodeInfoCompat scrollableNode =
        AccessibilityNodeInfoUtils.toCompat(event.getSource());

    if (AccessibilityNodeInfoUtils.hasDescendant(scrollableNode, currentFocusedNodeInScrollable)) {
      currentScrollableNodeForFocus = scrollableNode;
    } else {
      currentScrollableNodeForFocus = null;
    }
  }

  @Nullable
  private AccessibilityNodeInfoCompat getScrollableRoleContainer(AccessibilityNodeInfoCompat node) {
    if (!AccessibilityNodeInfoUtils.isVisible(node)) {
      LogUtils.v(TAG, "getScrollableContainer: node is invisible");
      return null;
    }

    return AccessibilityNodeInfoUtils.getMatchingAncestor(
        node,
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat ancestor) {
            if (ancestor == null) {
              return false;
            }

            @RoleName int role = Role.getRole(ancestor);
            return role == Role.ROLE_LIST
                || role == Role.ROLE_GRID
                || role == Role.ROLE_SCROLL_VIEW
                || ancestor.getCollectionInfo() != null;
          }
        });
  }

  @WorkerThread
  private void interceptRotaryEncoderIfNeeded() {
    if (service == null) {
      LogUtils.w(
          TAG,
          "interceptRotaryEncoderIfNeeded: AccessibilityService is null while checking condition.");
      return;
    }

    boolean shouldHandleMotionEvent = shouldHandleMotionEvent();
    LogUtils.d(
        TAG,
        "interceptRotaryEncoderIfNeeded: shouldHandleMotionEvent=%b, isHandlingMotionEvent=%b",
        shouldHandleMotionEvent,
        isHandlingMotionEvent);
    if (isHandlingMotionEvent == shouldHandleMotionEvent) {
      return;
    }

    interceptRotaryEncoderInternal(shouldHandleMotionEvent);
  }

  @WorkerThread
  private void interceptRotaryEncoderInternal(boolean shouldHandleMotionEvent) {
    AccessibilityServiceInfo info = service.getServiceInfo();
    if (info == null) {
      LogUtils.w(
          TAG,
          "interceptRotaryEncoderInternal: AccessibilityServiceInfo is null while checking"
              + " condition.");
      return;
    }

    if (shouldHandleMotionEvent) {
      info.setMotionEventSources(InputDevice.SOURCE_ROTARY_ENCODER);
      axisSum = 0;
    } else {
      info.setMotionEventSources(/* motionEventSources= */ 0);
    }

    service.setServiceInfo(info);
    isHandlingMotionEvent = shouldHandleMotionEvent;
  }

  // Make it visible for testing since we cannot mock AccessibilityServiceInfo in Robolectric test.
  @VisibleForTesting
  boolean shouldHandleMotionEvent() {
    boolean unsupportedDoubleSideScrollable =
        currentScrollableNodeForFocus != null
            && (!AccessibilityNodeInfoUtils.supportsAction(
                    currentScrollableNodeForFocus, ACTION_SCROLL_BACKWARD.getId())
                || !AccessibilityNodeInfoUtils.supportsAction(
                    currentScrollableNodeForFocus, ACTION_SCROLL_FORWARD.getId()));
    LogUtils.d(
        TAG,
        "shouldHandleMotionEvent: currentFocusedNodeInScrollable="
            + currentFocusedNodeInScrollable);
    LogUtils.d(
        TAG,
        "shouldHandleMotionEvent: currentScrollableNodeForFocus=" + currentScrollableNodeForFocus);
    return currentFocusedNodeInScrollable != null && unsupportedDoubleSideScrollable;
  }

  @VisibleForTesting
  Looper getBackgroundLooper() {
    return backgroundHandler.getLooper();
  }

  private static final class MotionEventHandler
      extends WeakReferenceHandler<MotionEventController> {

    static final int WHAT_PERFORM_ACTION = 1;

    public MotionEventHandler(MotionEventController controller, Looper looper) {
      super(controller, looper);
    }

    @Override
    protected void handleMessage(Message msg, MotionEventController parent) {
      parent.performActionIfNeeded((EventId) msg.obj);
    }

    public void postDelayPerformActionIfEmptyMessage() {
      if (!hasMessages(WHAT_PERFORM_ACTION)) {
        Message message =
            obtainMessage(
                /* what= */ WHAT_PERFORM_ACTION,
                /* obj= */ Performance.getInstance()
                    .onEventReceived(InputDevice.SOURCE_ROTARY_ENCODER));
        sendMessageDelayed(message, THROTTLE_MS);
      }
    }
  }
}
