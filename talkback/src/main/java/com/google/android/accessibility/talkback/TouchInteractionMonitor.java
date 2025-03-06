package com.google.android.accessibility.talkback;

import static android.accessibilityservice.AccessibilityService.GESTURE_DOUBLE_TAP;
import static android.accessibilityservice.AccessibilityService.GESTURE_DOUBLE_TAP_AND_HOLD;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_DOWN;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_LEFT;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_RIGHT;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_UP;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT;
import static android.accessibilityservice.TouchInteractionController.STATE_CLEAR;
import static android.accessibilityservice.TouchInteractionController.STATE_DELEGATING;
import static android.accessibilityservice.TouchInteractionController.STATE_DRAGGING;
import static android.accessibilityservice.TouchInteractionController.STATE_TOUCH_EXPLORING;
import static android.accessibilityservice.TouchInteractionController.STATE_TOUCH_INTERACTING;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.INVALID_POINTER_ID;
import static com.google.android.accessibility.talkback.PrimesController.TimerAction.TOUCH_CONTROLLER_STATE_CHANGE_LATENCY;
import static com.google.android.accessibility.utils.gestures.GestureManifold.GESTURE_FAKED_SPLIT_TYPING;
import static com.google.android.accessibility.utils.gestures.GestureManifold.GESTURE_TOUCH_EXPLORE;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.TouchInteractionController;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.annotation.MainThread;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.gestures.GestureConfiguration;
import com.google.android.accessibility.utils.gestures.GestureManifold;
import com.google.android.accessibility.utils.gestures.GestureUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

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

  // Queue size to hold the most recent change requests of controller state.
  private static final int MAX_CHANGE_REQUEST_SIZE = 5;

  private static final ImmutableSet<Integer> TOUCH_EXPLORE_GATE =
      ImmutableSet.of(
          GESTURE_DOUBLE_TAP,
          GESTURE_DOUBLE_TAP_AND_HOLD,
          GESTURE_SWIPE_RIGHT,
          GESTURE_SWIPE_LEFT,
          GESTURE_SWIPE_UP,
          GESTURE_SWIPE_DOWN,
          GESTURE_SWIPE_LEFT_AND_RIGHT,
          GESTURE_SWIPE_LEFT_AND_UP,
          GESTURE_SWIPE_LEFT_AND_DOWN,
          GESTURE_SWIPE_RIGHT_AND_UP,
          GESTURE_SWIPE_RIGHT_AND_DOWN,
          GESTURE_SWIPE_RIGHT_AND_LEFT,
          GESTURE_SWIPE_DOWN_AND_UP,
          GESTURE_SWIPE_DOWN_AND_LEFT,
          GESTURE_SWIPE_DOWN_AND_RIGHT,
          GESTURE_SWIPE_UP_AND_DOWN,
          GESTURE_SWIPE_UP_AND_LEFT,
          GESTURE_SWIPE_UP_AND_RIGHT);

  private int state;
  private int previousState;
  private final TouchInteractionController controller;
  private final Context context;
  private final ReceivedPointerTracker receivedPointerTracker;
  private int draggingPointerId = INVALID_POINTER_ID;
  private final TalkBackService service;
  private final Handler mainHandler;
  private final GestureManifold gestureDetector;
  private boolean gestureStarted = false;
  // Whether double tap and double tap and hold will be dispatched to the service or handled in
  // the framework.
  private boolean serviceHandlesDoubleTap = false;
  // The acceptable distance the pointer can move and still count as a tap.
  private final int passthroughTotalSlop;
  // The calculated edge height for the top and bottom edges.
  private final float edgeSwipeHeightPixels;
  // The time we take to determine what the user is doing.
  // We reduce it by 50 ms in order that touch exploration start doesn't arrive in the framework
  // after the finger has been lifted.
  // This happens because of the time overhead of IPCs.
  private final int determineUserIntentTimeout = GestureConfiguration.DOUBLE_TAP_TIMEOUT_MS;

  private final RequestTouchExplorationDelayed requestTouchExplorationDelayed;
  // A list of motion events that should be queued until a pending transition has taken place.
  private final Queue<MotionEvent> queuedMotionEvents = new LinkedList<>();
  // Whether this monitor is waiting for a state transition.
  // Motion events will be queued and sent to listeners after the transition has taken place.
  private boolean stateChangeRequested = false;
  // This is used to monitor whether all 1-finger gesture detectors have failed(cancelled).
  // If the condition met, we can request touch control for touch explore state transition.
  // Before it happens, if
  // 1. 2 or more fingers were detected.
  // 2. Any (1-finger) gesture's detected.
  // 3. Has done requestTouchExploration.
  // Then the monitor is off.
  private boolean keepMonitorTouchExplore = true;
  // To record any 1-finger gestures are still trying to match.
  private final Set<Integer> touchExploreGate = new HashSet<>(32);
  // To enable the MotionEvent log, issue the following command.
  // adb shell setprop log.tag.MotionEventLog VERBOSE
  // and re-enable TalkBack
  private final boolean logMotionEvent;
  // In general, the 1st MotionEvent#ACTION_DOWN after entering STATE_TOUCH_INTERACTING is the
  // firstDownTime.
  private boolean waitFirstMotionEvent;
  private EventId eventId;
  private final int displayId;
  private final boolean handleStateChangeInMainThread;
  private final PrimesController primesController;
  private final Queue<CallerInfo> callerInfos;

  private static class CallerInfo {
    final int state;
    final String caller;
    final long thread;

    CallerInfo(int state, String caller, long thread) {
      this.state = state;
      this.caller = caller;
      this.thread = thread;
    }

    @Override
    public String toString() {
      return "state:" + state + ", caller:" + caller + ", thread:" + thread;
    }
  }

  public TouchInteractionMonitor(
      Context context,
      TouchInteractionController controller,
      TalkBackService service,
      PrimesController primesController) {
    this.context = context;
    this.controller = controller;
    receivedPointerTracker = new ReceivedPointerTracker();
    this.service = service;
    this.primesController = primesController;
    handleStateChangeInMainThread = FeatureFlagReader.handleStateChangeInMainThread(context);
    displayId = context.getDisplay().getDisplayId();
    mainHandler = new Handler(context.getMainLooper());
    gestureDetector =
        new GestureManifold(
            context,
            this,
            displayId,
            ImmutableList.copyOf(
                context.getResources().getStringArray(R.array.service_detected_gesture_list)));
    int touchSlop =
        ViewConfiguration.get(context).getScaledTouchSlop()
            * context.getResources().getInteger(com.google.android.accessibility.utils.R.integer.config_slop_default_multiplier);
    int passthroughSlopMultiplier =
        context.getResources().getInteger(com.google.android.accessibility.utils.R.integer.config_passthrough_slop_multiplier);
    passthroughTotalSlop = passthroughSlopMultiplier * touchSlop;
    requestTouchExplorationDelayed = new RequestTouchExplorationDelayed(determineUserIntentTimeout);
    DisplayMetrics metrics = context.getResources().getDisplayMetrics();
    edgeSwipeHeightPixels = metrics.ydpi / GestureUtils.CM_PER_INCH * EDGE_SWIPE_HEIGHT_CM;

    LogUtils.v(LOG_TAG, "Touch Slop: %s", touchSlop);
    previousState = STATE_CLEAR;
    logMotionEvent = Log.isLoggable("MotionEventLog", Log.VERBOSE);
    if (logMotionEvent) {
      gestureDetector.enableLogMotionEvent();
    }
    callerInfos = EvictingQueue.create(MAX_CHANGE_REQUEST_SIZE);
    clear();
  }

  @WorkerThread
  @SuppressWarnings("Override")
  @Override
  public void onMotionEvent(MotionEvent event) {
    if (event != null) {
      if (logMotionEvent) {
        LogUtils.v(LOG_TAG, "Received motion event : %s", event.toString());
      }
    } else {
      LogUtils.e(LOG_TAG, "Event is null.");
      return;
    }
    if (event.getActionMasked() == ACTION_POINTER_DOWN) {
      keepMonitorTouchExplore = false;
    }
    if (stateChangeRequested) {
      queuedMotionEvents.add(event);
      return;
    }
    receivedPointerTracker.onMotionEvent(event);
    if (shouldPerformGestureDetection()) {
      if (waitFirstMotionEvent && event.getActionMasked() == ACTION_POINTER_DOWN) {
        // The split-typing gesture expect no action-down when re-entering the touch exploration
        // state. This must be placed before the dispatching the event to gesture detectors.
        eventId = Performance.getInstance().onGestureEventReceived(displayId, event);
        waitFirstMotionEvent = false;
      }
      gestureDetector.onMotionEvent(eventId, event);
    }
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
        if (waitFirstMotionEvent) {
          eventId = Performance.getInstance().onGestureEventReceived(displayId, event);
          waitFirstMotionEvent = false;
        }
        if (requestTouchExplorationDelayed.isPending()) {
          // The touch explore delay timer should be restart each time received Action-down event.
          // For multi-tap gestures, system will detect the ACTION_DOWN events multiple times. If
          // their is already a pending runnable and starting a new one, the older one will be
          // executed earlier than we expected.
          requestTouchExplorationDelayed.cancel();
        }
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
                if (moveDelta < passthroughTotalSlop) {
                  // For 3 finger swipe gestures which bear the 3 times of touchSlop during the
                  // detection. If the monitor issues state change to drag/delegate before the 3rd
                  // finger down due to the touch-slop over, the 3-finger swipe gesture detector
                  // fails. So we align the moveDelta to 3-times of touch-slop.
                  return;
                }
              }
            }
            if (isDraggingGesture(event)) {
              computeDraggingPointerIdIfNeeded(event);
              requestDragging(draggingPointerId, "handleMotionEventStateTouchInteracting");
            } else {
              requestDelegating("handleMotionEventStateTouchInteracting-2-points");
            }
            break;
          case 3:
            if (allPointersDownOnBottomEdge(event)) {
              requestDelegating("handleMotionEventStateTouchInteracting-3-points");
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
              requestDelegating("handleMotionEventStateDragging-2-points");
            }
            break;
          default:
            if (!gestureDetector.isMultiFingerGesturesEnabled()) {
              requestDelegating("handleMotionEventStateDragging-3-points");
            }
        }
        break;
      default:
        break;
    }
  }

  @WorkerThread
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
    if (state == STATE_TOUCH_INTERACTING) {
      waitFirstMotionEvent = true;
    } else if (state == STATE_TOUCH_EXPLORING) {
      // Log isDefaultDisplay/gestureId/onGestureDetectedTime. The targetGestureTimeout is the
      // current time minus lastMotionEventTransmissionLatency
      Performance.getInstance().onGestureRecognized(eventId, GESTURE_TOUCH_EXPLORE);
      waitFirstMotionEvent = true;
    }

    previousState = this.state;
    this.state = state;
    requestTouchExplorationDelayed.cancel();
    stateChangeRequested = false;
    if (shouldReceiveQueuedMotionEvents()) {
      while (!stateChangeRequested && !queuedMotionEvents.isEmpty()) {
        // When the onMotionEvent involve the request of gesture controller's state change, we stop
        // popping the event from the queue. Otherwise, it may cause infinite loop of onMotionEvent
        // callback.
        onMotionEvent(queuedMotionEvents.poll());
      }
    } else {
      queuedMotionEvents.clear();
    }
  }

  private void clear() {
    gestureStarted = false;
    stateChangeRequested = false;
    gestureDetector.clear();
    receivedPointerTracker.clear();
    requestTouchExplorationDelayed.cancel();
    queuedMotionEvents.clear();
    touchExploreGate.addAll(TOUCH_EXPLORE_GATE);
    keepMonitorTouchExplore = true;
    waitFirstMotionEvent = false;
    if (eventId != null) {
      Performance.getInstance().onGestureDetectionStopped(eventId);
      eventId = null;
    }
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
   * Computes {@link #draggingPointerId} if it is invalid. The pointer will be the finger closet to
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

    /**
     * @return The number of received pointers that are down.
     */
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
    LogUtils.v(
        LOG_TAG,
        "TalkBack gesture id:%s detected",
        AccessibilityServiceCompatUtils.gestureIdToString(gestureEvent.getGestureId()));
    keepMonitorTouchExplore = false;
    // As the state is controlled in controller, it could be switched to CLEAR by its internal
    // handling. We have to honor the gesture detection in CLEAR state unless the previous state is
    // dragging or delegating.
    if (state == STATE_DRAGGING
        || state == STATE_DELEGATING
        || (state == STATE_CLEAR && (previousState == STATE_DRAGGING)
            || (previousState == STATE_DELEGATING))) {
      // Gestures are expected when controller's state is either interacting or touch exploring.
      LogUtils.w(
          LOG_TAG,
          "Gesture %s dropped in state %s , previous state %s",
          gestureEvent,
          TouchInteractionController.stateToString(state),
          TouchInteractionController.stateToString(previousState));
      return;
    }
    int gestureId = gestureEvent.getGestureId();
    // Log isDefaultDisplay/gestureId/onGestureDetectedTime. The targetGestureTimeout is the current
    // time minus lastMotionEventTransmissionLatency
    Performance.getInstance().onGestureRecognized(eventId, gestureId);
    if (gestureId == AccessibilityService.GESTURE_DOUBLE_TAP) {
      if (serviceHandlesDoubleTap) {
        dispatchGestureToMainThreadAndClear(gestureEvent);
      } else {
        controller.performClick();
      }
    } else if (gestureId == AccessibilityService.GESTURE_DOUBLE_TAP_AND_HOLD) {
      // Double-tap and Double-tap-and-hold are in pair which can be (configurable) handled by
      // framework or by service. The Double-tap-and-hold gesture may involve not only the long
      // click but also the dragging gesture.
      // TalkBack cannot handle it till now and pass back to controller for the gesture integrity.
      controller.performLongClickAndStartDrag();
    } else if (gestureId == GESTURE_FAKED_SPLIT_TYPING) {
      dispatchGestureToMainThread(
          new AccessibilityGestureEvent(
              GESTURE_FAKED_SPLIT_TYPING, displayId, new ArrayList<MotionEvent>()));
      // log firstDownTime with display id
      eventId = Performance.getInstance().onGestureEventReceived(displayId, null);
    } else {
      dispatchGestureToMainThreadAndClear(gestureEvent);
    }
  }

  /** Dispatch a gesture event to the main thread of the service. */
  private void dispatchGestureToMainThreadAndClear(AccessibilityGestureEvent gestureEvent) {
    mainHandler.post(
        () -> {
          boolean unused = service.onGesture(gestureEvent);
        });
    clear();
  }

  /** Dispatch a gesture event to the main thread of the service, but do not clear state. */
  private void dispatchGestureToMainThread(AccessibilityGestureEvent gestureEvent) {
    mainHandler.post(
        () -> {
          boolean unused = service.onGesture(gestureEvent);
        });
  }

  @Override
  public void onGestureCancelled(int gestureId) {
    touchExploreGate.remove(gestureId);
    if (keepMonitorTouchExplore && touchExploreGate.isEmpty() && state == STATE_TOUCH_INTERACTING) {
      keepMonitorTouchExplore = false;
      requestTouchExplorationDelayed.cancel();
      requestTouchExploration("onGestureCancelled");
    }
  }

  @Override
  public void onGestureStarted(int gestureId) {
    gestureStarted = true;
    requestTouchExplorationDelayed.cancel();
    // The system will NOT send AccessibilityEvent TYPE_GESTURE_DETECTION_START and
    // TYPE_GESTURE_DETECTION_END when using TalkBack gesture detection. So the module has to
    // manually notify the service that gesture detection has been started.
    service.onGestureDetectionStarted();
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

  private void trackStateChangeRequest(int state, String caller) {
    CallerInfo newComer = new CallerInfo(state, caller, Thread.currentThread().getId());
    synchronized (callerInfos) {
      callerInfos.add(newComer);
    }
  }

  private IllegalStateException packExceptionWithCallerInfo(Exception e) {
    StringBuilder stringBuilder =
        new StringBuilder(
            String.format(
                "\nController's expected state: %s, , actual state: %s\n",
                TouchInteractionController.stateToString(state),
                TouchInteractionController.stateToString(controller.getState())));
    List<CallerInfo> callerList = new ArrayList<>(callerInfos);
    for (CallerInfo info : callerList) {
      stringBuilder.append(info).append("\n");
    }
    stringBuilder.append("\n");

    return new IllegalStateException(stringBuilder.toString(), e);
  }

  private IllegalStateException genNewException(Exception e) {
    return packExceptionWithCallerInfo(e);
  }

  private IllegalStateException genException(Exception e) {
    return packExceptionWithCallerInfo(e);
  }

  @MainThread
  protected void requestTouchExplorationFromMainThread(String caller, long requestStartTime) {
    if (requestStartTime != 0L) {
      reportStateChangeLatency(requestStartTime);
    }
    try {
      if (isStateTransitionAllowed()) {
        trackStateChangeRequest(STATE_TOUCH_EXPLORING, caller);
        controller.requestTouchExploration();
      }
    } catch (IllegalStateException e) {
      // The P/H flag determines which exception packer will be shown in the exception stack.
      // TODO: When the solution is verified OK, only one packer left for monitor.
      throw handleStateChangeInMainThread ? genNewException(e) : genException(e);
    }
    stateChangeRequested = true;
  }

  void requestTouchExploration(String caller) {
    long requestStartTime = SystemClock.uptimeMillis();
    if (!handleStateChangeInMainThread || Looper.getMainLooper().isCurrentThread()) {
      requestTouchExplorationFromMainThread(caller, 0L);
    } else {
      mainHandler.post(() -> requestTouchExplorationFromMainThread(caller, requestStartTime));
    }
  }

  protected void reportStateChangeLatency(long requestStartTime) {
    if (handleStateChangeInMainThread) {
      long currentTime = SystemClock.uptimeMillis();
      if (currentTime >= requestStartTime) {
        primesController.recordDuration(
            TOUCH_CONTROLLER_STATE_CHANGE_LATENCY, requestStartTime, currentTime);
      }
    }
  }

  @MainThread
  protected void requestDraggingFromMainThread(
      int pointerId, String caller, long requestStartTime) {
    if (requestStartTime != 0L) {
      reportStateChangeLatency(requestStartTime);
    }
    try {
      if (isStateTransitionAllowed()) {
        trackStateChangeRequest(STATE_DRAGGING, caller);
        controller.requestDragging(pointerId);
      }
      gestureDetector.clear();

    } catch (IllegalStateException e) {
      throw handleStateChangeInMainThread ? genNewException(e) : genException(e);
    }
    stateChangeRequested = true;
  }

  protected void requestDragging(int pointerId, String caller) {
    long requestStartTime = SystemClock.uptimeMillis();
    if (!handleStateChangeInMainThread || Looper.getMainLooper().isCurrentThread()) {
      requestDraggingFromMainThread(pointerId, caller, 0L);
    } else {
      mainHandler.post(() -> requestDraggingFromMainThread(pointerId, caller, requestStartTime));
    }
  }

  @MainThread
  protected void requestDelegatingFromMainThread(String caller, long requestStartTime) {
    if (requestStartTime != 0L) {
      reportStateChangeLatency(requestStartTime);
    }
    try {
      if (isStateTransitionAllowed()) {
        trackStateChangeRequest(STATE_DELEGATING, caller);
        controller.requestDelegating();
      }
      gestureDetector.clear();
    } catch (IllegalStateException e) {
      throw handleStateChangeInMainThread ? genNewException(e) : genException(e);
    }
    stateChangeRequested = true;
  }

  protected void requestDelegating(String caller) {
    long requestStartTime = SystemClock.uptimeMillis();
    if (!handleStateChangeInMainThread || Looper.getMainLooper().isCurrentThread()) {
      requestDelegatingFromMainThread(caller, 0L);
    } else {
      mainHandler.post(() -> requestDelegatingFromMainThread(caller, requestStartTime));
    }
  }

  private boolean shouldPerformGestureDetection() {
    if (this.state == STATE_TOUCH_INTERACTING || this.state == STATE_TOUCH_EXPLORING) {
      return true;
    }
    return false;
  }

  private boolean shouldReceiveQueuedMotionEvents() {
    if (this.state == STATE_TOUCH_INTERACTING || this.state == STATE_DRAGGING) {
      return true;
    } else {
      return false;
    }
  }

  /** As the controller does not allow to change state in some situations, check it in advance. */
  private boolean isStateTransitionAllowed() {
    int controllerState = controller.getState();
    return controllerState != STATE_DELEGATING && controllerState != STATE_TOUCH_EXPLORING;
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
      boolean result = mainHandler.postDelayed(this, mDelay);
    }

    public boolean isPending() {
      return mainHandler.hasCallbacks(this);
    }

    @Override
    public void run() {
      requestTouchExplorationFromMainThread("RequestTouchExplorationDelayed", 0L);
    }
  }
}
