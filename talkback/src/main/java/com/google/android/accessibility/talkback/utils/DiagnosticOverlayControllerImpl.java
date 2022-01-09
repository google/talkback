package com.google.android.accessibility.talkback.utils;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Color;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Feedback.Part;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.output.DiagnosticOverlayController;
import com.google.android.accessibility.utils.output.DiagnosticOverlayUtils;
import com.google.android.accessibility.utils.output.DiagnosticOverlayUtils.DiagnosticType;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// TODO: (b/191913512) - add tests for this implementation
/** Implementation for log overlay tool */
public class DiagnosticOverlayControllerImpl implements DiagnosticOverlayController {

  private static final String TAG = "DiagnosticOverlayControllerImpl";

  private static final String RED_CODE = "RED - Node(s) failed all focusability tests\n";
  private static final String MAGENTA_CODE = "MAGENTA - Node(s) has/have nothing to speak\n";
  private static final String YELLOW_CODE = "YELLOW - Node(s) not visible\n";
  private static final String ORANGE_CODE =
      "ORANGE - Node(s) has/have same bounds as window and "
          + "node(s) has/have children and is neither actionable nor focusable\n";
  private static final String GREEN_CODE = "GREEN - Node is clickable\n";
  private static final String BLUE_CODE = "BLUE - Node is not clickable";

  private static int ORANGE = 0xFFFFA500;

  /** Types of Log Entries */
  private enum Type {
    TEXT,
    TAG
  }

  private boolean enabled;
  @NonNull private Context context;
  @Nullable private DiagnosticOverlay diagnosticOverlay;
  @Nullable private HighlightOverlay highlightOverlay;
  /**
   * Need to recyle {@code unfocusedIdToNode}, {@code traversedIdToNode}, and {@code focusedNode} by
   * calling {@link DiagnosticOverlayControllerImpl#clearAndRecycleCollectionNodes} each time new
   * collection is needed
   */
  private static HashMap<Integer, AccessibilityNodeInfoCompat> traversedIdToNode = null;

  private static HashMap<Integer, ArrayList<AccessibilityNodeInfoCompat>> unfocusedIdToNode = null;
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

  /**
   * Receives and appends log to log controller.
   *
   * @param format The format of incoming log
   * @param args The log and other debugging objects
   */
  @FormatMethod
  public void appendLog(@FormatString String format, Object... args) {}

  /** Receives and appends the category of {@link DiagnosticType} and related unfocusable nodes */
  public void appendLog(@DiagnosticType Integer diagnosticInfo, Object... args) {
    if (!this.enabled) {
      return;
    }
    if (collectNodes) {
      if (args == null || args.length < 1) {
        return;
      }
      AccessibilityNodeInfoCompat node = null;
      if (args[0] instanceof AccessibilityNodeInfoCompat) {
        node = AccessibilityNodeInfoCompat.obtain((AccessibilityNodeInfoCompat) args[0]);
      } else {
        LogUtils.e(TAG, "Controller does support type.");
        return;
      }

      /**
       * If node is appended from {@link TraversalStrategyUtils#searchFocus} then simply add to a
       * hashmap
       */
      if (diagnosticInfo == DiagnosticOverlayUtils.SEARCH_FOCUS_FAIL) {
        if (traversedIdToNode == null) {
          traversedIdToNode = new HashMap<Integer, AccessibilityNodeInfoCompat>();
        }
        traversedIdToNode.put(node.hashCode(), node);
        return;
      }

      /**
       * If node is appended from {@link AccessibilityNodeInfoUtils#shouldFocusNode} then check to
       * see if node is also in {@code traversedNodes}. If so, add the node to collection to be
       * highlighted. If not, ignore the node since it wasn't a node affected by gesture swipe.
       */
      if (traversedIdToNode != null && traversedIdToNode.get(node.hashCode()) == null) {
        AccessibilityNodeInfoUtils.recycleNodes(node);
        return;
      }
      if (unfocusedIdToNode == null) {
        unfocusedIdToNode = new HashMap<Integer, ArrayList<AccessibilityNodeInfoCompat>>();
      }
      if (unfocusedIdToNode.get(diagnosticInfo) == null) {
        ArrayList<AccessibilityNodeInfoCompat> currentNodes =
            new ArrayList<AccessibilityNodeInfoCompat>();
        currentNodes.add(node);
        unfocusedIdToNode.put(diagnosticInfo, currentNodes);
      } else {
        unfocusedIdToNode.get(diagnosticInfo).add(node);
      }
    }
  }

  /**
   * Receives and appends Feedback object to log controller. Feedback objects are filtered for swipe
   * gestures.
   */
  public void appendLog(Feedback feedback) {
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
    if (!this.enabled
        || feedback.eventId() == null
        || highlightOverlay == null
        || diagnosticOverlay == null) {
      return;
    }
    /**
     * Clear/recycle traversed/unfocused nodes when window changes/scrolls to new screen because
     * usually {@link FocusProcessorForLogicalNavigation} tells when clear/recycle, but with new
     * screen change/scroll, we must call {@link
     * DiagnosticOverlayControllerImpl#clearAndRecycleCollectionNodes} from here
     */
    if (feedback.eventId().getEventSubtype() == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        || feedback.eventId().getEventSubtype() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        || failover.scroll() != null) {
      clearAndRecycleCollectionNodes(/* recycleFocusedNode= */ false);
      highlightOverlay.clearHighlight();
    }

    if (failover.focus() != null) {
      Feedback.@Nullable Focus focus = failover.focus();
      if (focus.target() != null) {
        focusedNode = AccessibilityNodeInfoCompat.obtain(focus.target());
        /**
         * {@link TraversalStrategyUtils#searchFocus} will append focusedNodes to list of unfocused
         * nodes since it is the last node to get traversed, so we must remove
         */
        if (traversedIdToNode != null && traversedIdToNode.containsKey(focusedNode.hashCode())) {
          AccessibilityNodeInfoUtils.recycleNodes(traversedIdToNode.remove(focusedNode.hashCode()));
        }
        if (unfocusedIdToNode != null && unfocusedIdToNode.containsKey(focusedNode.hashCode())) {
          AccessibilityNodeInfoUtils.recycleNodes(unfocusedIdToNode.remove(focusedNode.hashCode()));
        }
        highlightOverlay.highlightNodesOnScreen(focusedNode, unfocusedIdToNode);
      }
    } else if (failover.focusDirection() != null) {
      highlightOverlay.clearHighlight();
    }

    if (feedback.eventId().getEventSubtype() == AccessibilityService.GESTURE_SWIPE_LEFT
        || feedback.eventId().getEventSubtype() == AccessibilityService.GESTURE_SWIPE_RIGHT) {
      CharSequence incomingLog = processSwipeGesture(feedback);
      diagnosticOverlay.displayText(incomingLog);
    }
  }

  private CharSequence processSwipeGesture(Feedback feedback) {
    @Nullable List<Part> failovers = (feedback == null) ? null : feedback.failovers();
    Feedback.@Nullable Part failover =
        (failovers == null || failovers.size() < 1) ? null : failovers.get(0);
    Feedback.@Nullable Focus focus = (failover == null) ? null : failover.focus();
    if (focus == null) {
      return "";
    }
    int nodeCollectionSize = 0;
    if (unfocusedIdToNode != null) {
      for (ArrayList<AccessibilityNodeInfoCompat> nodes : unfocusedIdToNode.values()) {
        if (nodes != null) {
          nodeCollectionSize += nodes.size();
        }
      }
    }
    SpannableStringBuilder incomingLog =
        new SpannableStringBuilder(
            String.format(
                "Because of a %s that triggered "
                    + "%s, Talkback moved a11y focus to an "
                    + "%s. At least %d views were deemed "
                    + "as not focusable when deciding focus.\n\n",
                AccessibilityServiceCompatUtils.gestureIdToString(
                    feedback.eventId().getEventSubtype()),
                (focus.focusActionInfo() == null
                    ? "N/A"
                    : FocusActionInfo.sourceActionToString(focus.focusActionInfo().sourceAction)),
                (focus.target() == null ? "N/A" : focus.target().getClassName()),
                nodeCollectionSize));
    StringBuilderUtils.append(
        incomingLog,
        getSpannableStringColor(RED_CODE, Color.RED, 0, 3),
        getSpannableStringColor(MAGENTA_CODE, Color.MAGENTA, 0, 7),
        getSpannableStringColor(YELLOW_CODE, Color.YELLOW, 0, 6),
        getSpannableStringColor(ORANGE_CODE, ORANGE, 0, 6),
        getSpannableStringColor(GREEN_CODE, Color.GREEN, 0, 5),
        getSpannableStringColor(BLUE_CODE, Color.BLUE, 0, 4));
    return incomingLog;
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
        highlightOverlay.clearHighlight();
        highlightOverlay = null;
      }
    }
    this.enabled = enabled;
  }

  public static void setNodeCollectionEnabled(boolean collect) {
    if (collect) {
      clearAndRecycleCollectionNodes(/* recycleFocusedNode= */ true);
    }
    collectNodes = collect;
  }

  /**
   * Recycle and clear nodes in collections and only recycle focusedNode {@code recycleFocusedNode}
   * if a new swipe is registered - scrolls and screen changes do not require focusedNode to be
   * recycled.
   */
  private static void clearAndRecycleCollectionNodes(boolean recycleFocusedNode) {
    if (unfocusedIdToNode != null) {
      for (ArrayList<AccessibilityNodeInfoCompat> nodes : unfocusedIdToNode.values()) {
        AccessibilityNodeInfoUtils.recycleNodes(nodes);
      }
      unfocusedIdToNode.clear();
    }
    if (traversedIdToNode != null) {
      for (AccessibilityNodeInfoCompat node : traversedIdToNode.values()) {
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }
      traversedIdToNode.clear();
    }
    if (recycleFocusedNode) {
      if (focusedNode != null) {
        AccessibilityNodeInfoUtils.recycleNodes(focusedNode);
      }
    }
  }

  /**
   * Returns SpannableString that reads as {@code text} and has characters in indices from {@code
   * start} to {@code end} colored in {@color}
   */
  private SpannableString getSpannableStringColor(String text, int color, int start, int end) {
    SpannableString spannableStringColor = new SpannableString(text);
    spannableStringColor.setSpan(
        new ForegroundColorSpan(color), start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
    return spannableStringColor;
  }

  @VisibleForTesting
  boolean isLogOverlayEnabled() {
    return this.enabled && diagnosticOverlay != null;
  }
}
