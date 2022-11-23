package com.google.android.accessibility.talkback;

import static android.accessibilityservice.TouchInteractionController.STATE_CLEAR;
import static android.accessibilityservice.TouchInteractionController.STATE_DRAGGING;
import static android.accessibilityservice.TouchInteractionController.STATE_TOUCH_INTERACTING;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.INVALID_POINTER_ID;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.TouchInteractionController;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.annotation.RequiresApi;
import com.google.android.accessibility.utils.gestures.GestureManifold;
import com.google.android.accessibility.utils.gestures.GestureUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * This class receives motion events from the framework for the purposes of figuring out whether an
 * interaction is a gesture, touch exploration, or passthrough . If the gesture detector clasifies
 * an interaction as a gesture this class will relay that back to the service. If an interaction
 * qualifies as touch exploration or a passthrough this class will relay that to the framework.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class TouchInteractionMonitor
    implements TouchInteractionController.Callback, GestureManifold.Listener {
  private static final String LOG_TAG = "TouchInteractionMonitor";
  private static final float MAX_DRAGGING_ANGLE_COS = 0.525321989f; // cos(pi/4)
  // The height of the top and bottom edges for  edge-swipes.
  // For now this is only used to allow three-finger edge-swipes from the bottom.
  private static final float EDGE_SWIPE_HEIGHT_CM = 0.25f;

  private int state;
  private TouchInteractionController controller;
  private Context context;
  private ReceivedPointerTracker receivedPointerTracker;
  private int draggingPointerId = INVALID_POINTER_ID;
  private AccessibilityService service;
  private Handler mainHandler;
  private int displayId;
  private GestureManifold gestureDetector;
  private boolean gestureStarted = false;
  // Whether double tap and double tap and hold will be dispatched to the service or handled in
  // the framework.
  private boolean serviceHandlesDoubleTap = false;
  // The acceptable distance the pointer can move and still count as a tap.
  private int touchSlop;
  // The calculated edge height for the top and bottom edges.
  private final float edgeSwipeHeightPixels;

  // Timeout before trying to decide what the user is trying to do.
  private final int determineUserIntentTimeout;
  private RequestTouchExplorationDelayed requestTouchExplorationDelayed;

  public TouchInteractionMonitor(
      Context context, TouchInteractionController controller, AccessibilityService service) {
    this.context = context;
    this.controller = controller;
    receivedPointerTracker = new ReceivedPointerTracker();
    this.service = service;
    mainHandler = new Handler(context.getMainLooper());
    this.displayId = context.getDisplay().getDisplayId();
    gestureDetector = new GestureManifold(context, this, this.displayId);
    touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    determineUserIntentTimeout = ViewConfiguration.getDoubleTapTimeout();
    requestTouchExplorationDelayed = new RequestTouchExplorationDelayed(determineUserIntentTimeout);
    DisplayMetrics metrics = context.getResources().getDisplayMetrics();
    edgeSwipeHeightPixels = metrics.ydpi / GestureUtils.CM_PER_INCH * EDGE_SWIPE_HEIGHT_CM;

    LogUtils.v(LOG_TAG, "Touch Slop: %s", touchSlop);
    clear();
  }

  @SuppressWarnings("Override")
  @Override
  public void onMotionEvent(MotionEvent event) {
    if (event != null) {
      LogUtils.v(LOG_TAG, "Received motion event : %s", event.toString());
    } else {
      LogUtils.e(LOG_TAG, "Event is null.");
    }
    receivedPointerTracker.onMotionEvent(event);
    gestureDetector.onMotionEvent(event);
    if (!gestureStarted) {
      switch (state) {
        case STATE_TOUCH_INTERACTING:
          handleMotionEventStateTouchInteracting(event);
          break;
        case STATE_DRAGGING:
          handleMotionEventStateDragging(event);
          break;
        default:
          break;
      }
    }
  }

  public void handleMotionEventStateTouchInteracting(MotionEvent event) {
    switch (event.getActionMasked()) {
      case ACTION_DOWN:
        requestTouchExplorationDelayed.post();
        break;
      case ACTION_MOVE:
        switch (event.getPointerCount()) {
          case 1:
            // Do nothing. Touch exploration will fire on a delay.
            break;
          case 2:
            if (gestureDetector.isTwoFingerPassthroughEnabled()) {
              for (int index = 0; index < event.getPointerCount(); ++index) {
                int id = event.getPointerId(index);
                if (!receivedPointerTracker.isReceivedPointerDown(id)) {
                  // Something is wrong with the event stream.
                  LogUtils.e(LOG_TAG, "Invalid pointer id: %d", id);
                  return;
                }
                final float deltaX =
                    receivedPointerTracker.getReceivedPointerDownX(id) - event.getX(index);
                final float deltaY =
                    receivedPointerTracker.getReceivedPointerDownY(id) - event.getY(index);
                final double moveDelta = Math.hypot(deltaX, deltaY);
                if (moveDelta < (2 * touchSlop)) {
                  return;
                }
              }
            }
            if (isDraggingGesture(event)) {
              computeDraggingPointerIdIfNeeded(event);
              controller.requestDragging(draggingPointerId);
            } else {
              controller.requestDelegating();
            }
            break;
          case 3:
            if (allPointersDownOnBottomEdge(event)) {
              controller.requestDelegating();
            }
            break;
          default:
            break;
        }
        break;
      case ACTION_POINTER_DOWN:
        requestTouchExplorationDelayed.cancel();
        break;
      default:
        break;
    }
  }

  public void handleMotionEventStateDragging(MotionEvent event) {
    switch (event.getActionMasked()) {
      case ACTION_MOVE:
        if (draggingPointerId == INVALID_POINTER_ID) {
          break;
        }
        switch (event.getPointerCount()) {
          case 1:
            // do nothing
            break;
          case 2:
            if (isDraggingGesture(event)) {
              // Do nothing. The system will continue the drag on its own.
            } else {
              // The two pointers are moving either in different directions or
              // no close enough => delegate the gesture to the view hierarchy.
              controller.requestDelegating();
            }
            break;
          default:
            if (!gestureDetector.isMultiFingerGesturesEnabled()) {
            controller.requestDelegating();
            }
        }
        break;
      default:
        break;
    }
  }

  @SuppressWarnings("Override")
  @Override
  public void onStateChanged(int state) {
    LogUtils.v(
        LOG_TAG,
        "%s -> %s",
        TouchInteractionController.stateToString(this.state),
        TouchInteractionController.stateToString(state));
    if (this.state == STATE_CLEAR) {
      // Clear on transition to a new interaction
      clear();
    }
    this.state = state;
    requestTouchExplorationDelayed.cancel();
  }

  private void clear() {
    gestureStarted = false;
    gestureDetector.clear();
    receivedPointerTracker.clear();
    requestTouchExplorationDelayed.cancel();
  }

  private boolean allPointersDownOnBottomEdge(MotionEvent event) {
    final long screenHeight = context.getResources().getDisplayMetrics().heightPixels;
    for (int i = 0; i < event.getPointerCount(); ++i) {
      final int pointerId = event.getPointerId(i);
      final float pointerDownY = receivedPointerTracker.getReceivedPointerDownY(pointerId);
      if (pointerDownY < (screenHeight - edgeSwipeHeightPixels)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Computes {@link #mDraggingPointerId} if it is invalid. The pointer will be the finger closet to
   * an edge of the screen.
   */
  private void computeDraggingPointerIdIfNeeded(MotionEvent event) {
    if (draggingPointerId != INVALID_POINTER_ID) {
      // If we have a valid pointer ID, we should be good
      final int pointerIndex = event.findPointerIndex(draggingPointerId);
      if (event.findPointerIndex(pointerIndex) >= 0) {
        return;
      }
    }
    // Use the pointer that is closest to its closest edge.
    final float firstPtrX = event.getX(0);
    final float firstPtrY = event.getY(0);
    final int firstPtrId = event.getPointerId(0);
    final float secondPtrX = event.getX(1);
    final float secondPtrY = event.getY(1);
    final int secondPtrId = event.getPointerId(1);
    draggingPointerId =
        (getDistanceToClosestEdge(firstPtrX, firstPtrY)
                < getDistanceToClosestEdge(secondPtrX, secondPtrY))
            ? firstPtrId
            : secondPtrId;
  }

  private float getDistanceToClosestEdge(float x, float y) {
    final long width = this.context.getResources().getDisplayMetrics().widthPixels;
    final long height = this.context.getResources().getDisplayMetrics().heightPixels;
    float distance = Float.MAX_VALUE;
    if (x < (width - x)) {
      distance = x;
    } else {
      distance = width - x;
    }
    if (distance > y) {
      distance = y;
    }
    if (distance > (height - y)) {
      distance = (height - y);
    }
    return distance;
  }
  /**
   * Determines whether a two pointer gesture is a dragging one.
   *
   * @param event The event with the pointer data.
   * @return True if the gesture is a dragging one.
   */
  private boolean isDraggingGesture(MotionEvent event) {

    final float firstPtrX = event.getX(0);
    final float firstPtrY = event.getY(0);
    final float secondPtrX = event.getX(1);
    final float secondPtrY = event.getY(1);

    final float firstPtrDownX = receivedPointerTracker.getReceivedPointerDownX(0);
    final float firstPtrDownY = receivedPointerTracker.getReceivedPointerDownY(0);
    final float secondPtrDownX = receivedPointerTracker.getReceivedPointerDownX(1);
    final float secondPtrDownY = receivedPointerTracker.getReceivedPointerDownY(1);

    return GestureUtils.isDraggingGesture(
        firstPtrDownX,
        firstPtrDownY,
        secondPtrDownX,
        secondPtrDownY,
        firstPtrX,
        firstPtrY,
        secondPtrX,
        secondPtrY,
        MAX_DRAGGING_ANGLE_COS);
  }
  /** This class tracks where and when a pointer went down. It does not track its movement. */
  class ReceivedPointerTracker {

    private final PointerDownInfo[] mReceivedPointers;

    // Which pointers are down.
    private int mReceivedPointersDown;

    ReceivedPointerTracker() {
      mReceivedPointers = new PointerDownInfo[controller.getMaxPointerCount()];
      clear();
    }

    /** Clears the internals state. */
    public void clear() {
      mReceivedPointersDown = 0;
      for (int i = 0; i < controller.getMaxPointerCount(); ++i) {
        mReceivedPointers[i] = new PointerDownInfo();
      }
    }

    /**
     * Processes a received {@link MotionEvent} event.
     *
     * @param event The event to process.
     */
    public void onMotionEvent(MotionEvent event) {
      final int action = event.getActionMasked();
      switch (action) {
        case MotionEvent.ACTION_DOWN:
          handleReceivedPointerDown(event.getActionIndex(), event);
          break;
        case MotionEvent.ACTION_POINTER_DOWN:
          handleReceivedPointerDown(event.getActionIndex(), event);
          break;
        case MotionEvent.ACTION_UP:
          handleReceivedPointerUp(event.getActionIndex(), event);
          break;
        case MotionEvent.ACTION_POINTER_UP:
          handleReceivedPointerUp(event.getActionIndex(), event);
          break;
        default:
          break;
      }
    }

    /** @return The number of received pointers that are down. */
    public int getReceivedPointerDownCount() {
      return Integer.bitCount(mReceivedPointersDown);
    }

    /**
     * Whether an received pointer is down.
     *
     * @param pointerId The unique pointer id.
     * @return True if the pointer is down.
     */
    public boolean isReceivedPointerDown(int pointerId) {
      final int pointerFlag = (1 << pointerId);
      return (mReceivedPointersDown & pointerFlag) != 0;
    }

    /**
     * @param pointerId The unique pointer id.
     * @return The X coordinate where the pointer went down.
     */
    public float getReceivedPointerDownX(int pointerId) {
      return mReceivedPointers[pointerId].mX;
    }

    /**
     * @param pointerId The unique pointer id.
     * @return The Y coordinate where the pointer went down.
     */
    public float getReceivedPointerDownY(int pointerId) {
      return mReceivedPointers[pointerId].mY;
    }

    /**
     * @param pointerId The unique pointer id.
     * @return The time when the pointer went down.
     */
    public long getReceivedPointerDownTime(int pointerId) {
      return mReceivedPointers[pointerId].mTime;
    }

    /**
     * Handles a received pointer down event.
     *
     * @param pointerIndex The index of the pointer that has changed.
     * @param event The event to be handled.
     */
    private void handleReceivedPointerDown(int pointerIndex, MotionEvent event) {
      final int pointerId = event.getPointerId(pointerIndex);
      final int pointerFlag = (1 << pointerId);
      mReceivedPointersDown |= pointerFlag;
      mReceivedPointers[pointerId].set(
          event.getX(pointerIndex), event.getY(pointerIndex), event.getEventTime());
    }

    /**
     * Handles a received pointer up event.
     *
     * @param pointerIndex The index of the pointer that has changed.
     * @param event The event to be handled.
     */
    private void handleReceivedPointerUp(int pointerIndex, MotionEvent event) {
      final int pointerId = event.getPointerId(pointerIndex);
      final int pointerFlag = (1 << pointerId);
      mReceivedPointersDown &= ~pointerFlag;
      mReceivedPointers[pointerId].clear();
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("=========================");
      builder.append("\nDown pointers #");
      builder.append(getReceivedPointerDownCount());
      builder.append(" [ ");
      for (int i = 0; i < controller.getMaxPointerCount(); i++) {
        if (isReceivedPointerDown(i)) {
          builder.append(i);
          builder.append(" ");
        }
      }
      builder.append("]");
      builder.append(" ]");
      builder.append("\n=========================");
      return builder.toString();
    }
  }

  /**
   * This class tracks where and when an individual pointer went down. Note that it does not track
   * when it went up.
   */
  static class PointerDownInfo {
    private float mX;
    private float mY;
    private long mTime;

    public void set(float x, float y, long time) {
      mX = x;
      mY = y;
      mTime = time;
    }

    public void clear() {
      mX = 0;
      mY = 0;
      mTime = 0;
    }
  }

  @Override
  public void onGestureCompleted(AccessibilityGestureEvent gestureEvent) {
    if (gestureEvent.getGestureId() == AccessibilityService.GESTURE_DOUBLE_TAP) {
      if (serviceHandlesDoubleTap) {
        dispatchGestureToMainThread(gestureEvent);
      } else {
      controller.performClick();
      }
    } else if (gestureEvent.getGestureId() == AccessibilityService.GESTURE_DOUBLE_TAP_AND_HOLD) {
      if (serviceHandlesDoubleTap) {
        dispatchGestureToMainThread(gestureEvent);
      } else {
      controller.performLongClickAndStartDrag();
      }
    } else {
      dispatchGestureToMainThread(gestureEvent);
    }
  }

  /** Dispatch a gesture event to the main thread of the service. */
  private void dispatchGestureToMainThread(AccessibilityGestureEvent gestureEvent) {
    mainHandler.post(
        () -> {
          service.onGesture(gestureEvent);
        });
    clear();
  }

  @Override
  public void onGestureCancelled() {}

  @Override
  public void onGestureStarted() {
    gestureStarted = true;
    requestTouchExplorationDelayed.cancel();
  }

  public void setMultiFingerGesturesEnabled(boolean mode) {
    gestureDetector.setMultiFingerGesturesEnabled(mode);
  }

  public void setTwoFingerPassthroughEnabled(boolean mode) {
    gestureDetector.setTwoFingerPassthroughEnabled(mode);
  }

  public void setServiceHandlesDoubleTap(boolean mode) {
    serviceHandlesDoubleTap = mode;
  }

  private class RequestTouchExplorationDelayed implements Runnable {
    private final int mDelay;

    public RequestTouchExplorationDelayed(int delay) {
      mDelay = delay;
    }

    public void cancel() {
      mainHandler.removeCallbacks(this);
    }

    public void post() {
      mainHandler.postDelayed(this, mDelay);
    }

    public boolean isPending() {
      return mainHandler.hasCallbacks(this);
    }

    @Override
    public void run() {
      controller.requestTouchExploration();
    }
  }
}
