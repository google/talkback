package com.google.android.accessibility.talkback.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.DiagnosticOverlayController;
import com.google.android.accessibility.utils.DiagnosticOverlayUtils;
import com.google.android.accessibility.utils.DiagnosticOverlayUtils.DiagnosticType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// TODO: (b/191913512) - add tests for this implementation
/** Implementation for log overlay tool */
public class DiagnosticOverlayControllerImpl implements DiagnosticOverlayController {

  private static final String TAG = "DiagnosticOverlayController";

  private boolean enabled;
  private @NonNull Context context;
  private @Nullable DiagnosticOverlay diagnosticOverlay;
  private @Nullable HighlightOverlay highlightOverlay;

  @NonNull
  private static HashMap<Integer, AccessibilityNodeInfoCompat> traversedIdToNode = new HashMap<>();

  @NonNull
  private static HashMap<Integer, ArrayList<AccessibilityNodeInfoCompat>> unfocusedIdToNode =
      new HashMap<>();

  @NonNull private static HashSet<AccessibilityNode> refocusNodePath = new HashSet<>();

  private static AccessibilityNodeInfoCompat focusedNode = null;
  private static boolean collectNodes;

  public DiagnosticOverlayControllerImpl(Context context) {
    this.context = context;
    DiagnosticOverlayUtils.setDiagnosticOverlayController(this);
  }

  @VisibleForTesting
  DiagnosticOverlayControllerImpl(
      Context context, DiagnosticOverlay diagnosticOverlay, HighlightOverlay highlightOverlay) {
    this.context = context;
    // instantiate overlay in case preference is toggled on when TalkBack starts
    this.diagnosticOverlay = diagnosticOverlay;
    this.highlightOverlay = highlightOverlay;
  }

  /** Receives and appends the category of {@link DiagnosticType} and related unfocusable nodes */
  public void appendLog(@DiagnosticType Integer diagnosticInfo, AccessibilityNodeInfoCompat node) {
    if (!this.enabled || !collectNodes || (node == null)) {
      return;
    }

      /**
       * If node is appended from {@link TraversalStrategyUtils#searchFocus} then simply add to a
       * hashmap
       */
      if (diagnosticInfo == DiagnosticOverlayUtils.SEARCH_FOCUS_FAIL) {
        traversedIdToNode.put(node.hashCode(), node);
        return;
      }

      /**
       * If node is appended from {@link AccessibilityNodeInfoUtils#shouldFocusNode} then check to
       * see if node is also in {@code traversedNodes}. If so, add the node to collection to be
       * highlighted. If not, ignore the node since it wasn't a node affected by gesture swipe.
       */
      if (traversedIdToNode.get(node.hashCode()) == null) {
        return;
      }
      if (unfocusedIdToNode.get(diagnosticInfo) == null) {
        ArrayList<AccessibilityNodeInfoCompat> currentNodes = new ArrayList<>();
        currentNodes.add(node);
        unfocusedIdToNode.put(diagnosticInfo, currentNodes);
      } else {
        unfocusedIdToNode.get(diagnosticInfo).add(node);
      }
  }

  public void appendLog(@DiagnosticType Integer diagnosticInfo, AccessibilityNode node) {
    if (!this.enabled || (node == null)) {
      return;
    }

    if (diagnosticInfo == DiagnosticOverlayUtils.REFOCUS_PATH) {
      refocusNodePath.add(node);
    }
  }

  /** Highlights focused View after a focus-action. */
  public void displayFeedback(Feedback feedback) {
    if (!enabled || highlightOverlay == null) {
      return;
    }

    Feedback.@Nullable Part failover =
        (feedback.failovers() == null || feedback.failovers().size() < 1
            ? null
            : feedback.failovers().get(0));
    if (failover == null) {
      return;
    }
    // Filter for FOCUS and FOCUS DIRECTION actions,
    // which mark beg/end of swipe gesture + associated focus
    if (failover.focus() == null
        && failover.focusDirection() == null
        && failover.scroll() == null) {
      return;
    }
    /** Check to make sure eventID isn't null before checking for gestures */
    if (feedback.eventId() == null) {
      return;
    }

    if (feedback.eventId().getEventSubtype() == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        || feedback.eventId().getEventSubtype() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        || failover.scroll() != null) {
      highlightOverlay.clearHighlight();
    }

    if (failover.focus() != null) {
      Feedback.@Nullable Focus focus = failover.focus();
      if (focus.target() != null) {
        focusedNode = focus.target();
        /**
         * {@link TraversalStrategyUtils#searchFocus} will append focusedNode to list of unfocused
         * nodes since it is the last node to get traversed, so we must remove
         */
        traversedIdToNode.remove(focusedNode.hashCode());
        unfocusedIdToNode.remove(focusedNode.hashCode());
        highlightOverlay.highlightNodesOnScreen(focusedNode, unfocusedIdToNode, refocusNodePath);
        clearCollectionNodes();
      }
    } else if (failover.focusDirection() != null) {
      highlightOverlay.clearHighlight();
    }

    // Do not display feedback text, because it is directly observable.
  }

  @SuppressLint("SwitchIntDef") // Only some event-types are filtered out.
  public void displayEvent(AccessibilityEvent event) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START:
      case AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END:
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START:
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END:
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED:
      case AccessibilityEvent.TYPE_VIEW_HOVER_ENTER:
      case AccessibilityEvent.TYPE_VIEW_HOVER_EXIT:
        return;
      default:
        if (diagnosticOverlay != null) {
          CharSequence text = AccessibilityEventUtils.toStringShort(event);
          diagnosticOverlay.displayText(text);
        }
    }
  }

  public void setLogOverlayEnabled(boolean enabled) {
    /** Only enable if enabled state has changed to true and diagnostic overlay */
    if (enabled == this.enabled) {
      return;
    }
    if (enabled) {
      if (diagnosticOverlay == null) {
        diagnosticOverlay = new DiagnosticOverlay(context);
      }
      if (highlightOverlay == null) {
        highlightOverlay = new HighlightOverlay(context);
      }
    } else {
      if (diagnosticOverlay != null) {
        diagnosticOverlay.hide();
        diagnosticOverlay = null;
      }
      if (highlightOverlay != null) {
        highlightOverlay.removeHighlight();
        highlightOverlay = null;
      }
    }
    this.enabled = enabled;
  }

  public static void setNodeCollectionEnabled(boolean collect) {
    if (collect) {
      clearCollectionNodes();
    }
    collectNodes = collect;
  }

  /** Clear nodes in collections. */
  private static void clearCollectionNodes() {
    unfocusedIdToNode = new HashMap<>(); // HiglightOverlay may still be using the old map.
    refocusNodePath = new HashSet<>();
    traversedIdToNode.clear();
  }

  @VisibleForTesting
  boolean isLogOverlayEnabled() {
    return this.enabled && diagnosticOverlay != null;
  }
}
