/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.controller;

import static com.google.android.accessibility.braille.common.translate.EditBufferUtils.NO_CURSOR;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorFocus;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorScreenReaderAction;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.AccessibilityEventUtils;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleDisplay.ScreenReaderAction;
import com.google.android.accessibility.utils.AccessibilityNodeInfoRef;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import java.util.Optional;

/** Navigation mode that is based on traversing the node tree using accessibility focus. */
// TODO: Consolidate parts of this class with similar code in TalkBack.
class DefaultNavigationMode implements NavigationMode {

  private static final String TAG = "DefaultNavigationMode";

  private final Context context;
  private final CellsContentConsumer cellsContentConsumer;
  private final NodeBrailler nodeBrailler;
  private final FeedbackManager feedbackManager;
  private final BehaviorScreenReaderAction behaviorScreenReaderAction;
  private final BehaviorFocus behaviorFocus;

  private final AccessibilityNodeInfoRef lastFocusedNode = new AccessibilityNodeInfoRef();

  public DefaultNavigationMode(
      Context context,
      CellsContentConsumer cellsContentConsumer,
      FeedbackManager feedbackManager,
      NodeBrailler nodeBrailler,
      BehaviorFocus behaviorFocus,
      BehaviorScreenReaderAction behaviorScreenReaderAction) {
    this.context = context;
    this.behaviorScreenReaderAction = behaviorScreenReaderAction;
    this.behaviorFocus = behaviorFocus;
    this.cellsContentConsumer = cellsContentConsumer;
    this.nodeBrailler = nodeBrailler;
    this.feedbackManager = feedbackManager;
  }

  @Override
  public boolean onPanLeftOverflow() {
    return feedbackManager.emitOnFailure(
        behaviorScreenReaderAction.performAction(ScreenReaderAction.PREVIOUS_ITEM),
        FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
  }

  @Override
  public boolean onPanRightOverflow() {
    return feedbackManager.emitOnFailure(
        behaviorScreenReaderAction.performAction(ScreenReaderAction.NEXT_ITEM),
        FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
  }

  /** Moves accessibility focus to the first focusable node of the previous 'line'. */
  private boolean linePrevious() {
    return feedbackManager.emitOnFailure(
        behaviorScreenReaderAction.performAction(ScreenReaderAction.PREVIOUS_LINE),
        FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
  }

  /** Moves accessibility focus to the first focusable node of the next 'line'. */
  private boolean lineNext() {
    return feedbackManager.emitOnFailure(
        behaviorScreenReaderAction.performAction(ScreenReaderAction.NEXT_LINE),
        FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
  }

  private boolean itemPrevious() {
    return feedbackManager.emitOnFailure(
        behaviorScreenReaderAction.performAction(ScreenReaderAction.PREVIOUS_ITEM),
        FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
  }

  private boolean itemNext() {
    return feedbackManager.emitOnFailure(
        behaviorScreenReaderAction.performAction(ScreenReaderAction.NEXT_ITEM),
        FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
  }

  @Override
  public boolean onMappedInputEvent(BrailleInputEvent event) {
    switch (event.getCommand()) {
      case BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS:
        return itemPrevious();
      case BrailleInputEvent.CMD_NAV_ITEM_NEXT:
        return itemNext();
      case BrailleInputEvent.CMD_NAV_LINE_PREVIOUS:
        return linePrevious();
      case BrailleInputEvent.CMD_NAV_LINE_NEXT:
        return lineNext();
      case BrailleInputEvent.CMD_KEY_ENTER:
      case BrailleInputEvent.CMD_ACTIVATE_CURRENT:
        return feedbackManager.emitOnFailure(
            behaviorScreenReaderAction.performAction(ScreenReaderAction.CLICK_CURRENT),
            FeedbackManager.TYPE_COMMAND_FAILED);
      case BrailleInputEvent.CMD_LONG_PRESS_CURRENT:
        return feedbackManager.emitOnFailure(
            behaviorScreenReaderAction.performAction(ScreenReaderAction.LONG_CLICK_CURRENT),
            FeedbackManager.TYPE_COMMAND_FAILED);
      case BrailleInputEvent.CMD_ROUTE:
        {
          Optional<ClickableSpan[]> clickableSpans =
              cellsContentConsumer.getClickableSpans(event.getArgument());
          if (clickableSpans.isPresent() && clickableSpans.get().length > 0) {
            return activateClickableSpan(context, clickableSpans.get()[0]);
          }
          AccessibilityNodeInfoCompat node =
              cellsContentConsumer.getAccessibilityNode(event.getArgument());
          boolean result =
              feedbackManager.emitOnFailure(
                  behaviorScreenReaderAction.performAction(ScreenReaderAction.CLICK_NODE, node),
                  FeedbackManager.TYPE_COMMAND_FAILED);
          int index = cellsContentConsumer.getTextIndexInWhole(event.getArgument());
          if (node != null
              && AccessibilityNodeInfoUtils.isTextSelectable(node)
              && index != NO_CURSOR) {
            // TODO: handle selectable text too.
            final Bundle args = new Bundle();
            args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, index);
            args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT, index);
            PerformActionUtils.performAction(
                node, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, args, /* eventId= */ null);
          }
          return result;
        }
      case BrailleInputEvent.CMD_LONG_PRESS_ROUTE:
        {
          AccessibilityNodeInfoCompat node =
              cellsContentConsumer.getAccessibilityNode(event.getArgument());
          return feedbackManager.emitOnFailure(
              behaviorScreenReaderAction.performAction(ScreenReaderAction.LONG_CLICK_NODE, node),
              FeedbackManager.TYPE_COMMAND_FAILED);
        }
      case BrailleInputEvent.CMD_NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_FORWARD:
        return behaviorScreenReaderAction.performAction(
            ScreenReaderAction.NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_FORWARD);
      case BrailleInputEvent.CMD_NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_BACKWARD:
        return behaviorScreenReaderAction.performAction(
            ScreenReaderAction.NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_BACKWARD);
      case BrailleInputEvent.CMD_SCROLL_FORWARD:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.SCROLL_FORWARD);
      case BrailleInputEvent.CMD_SCROLL_BACKWARD:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.SCROLL_BACKWARD);
      case BrailleInputEvent.CMD_NAV_TOP:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.NAVIGATE_TO_TOP);
      case BrailleInputEvent.CMD_NAV_BOTTOM:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.NAVIGATE_TO_BOTTOM);
      case BrailleInputEvent.CMD_HEADING_NEXT:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.WEB_NEXT_HEADING);
      case BrailleInputEvent.CMD_HEADING_PREVIOUS:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.WEB_PREVIOUS_HEADING);
      case BrailleInputEvent.CMD_CONTROL_NEXT:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.WEB_NEXT_CONTROL);
      case BrailleInputEvent.CMD_CONTROL_PREVIOUS:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.WEB_PREVIOUS_CONTROL);
      case BrailleInputEvent.CMD_LINK_NEXT:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.WEB_NEXT_LINK);
      case BrailleInputEvent.CMD_LINK_PREVIOUS:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.WEB_PREVIOUS_LINK);
      case BrailleInputEvent.CMD_WINDOW_NEXT:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.NEXT_WINDOW);
      case BrailleInputEvent.CMD_WINDOW_PREVIOUS:
        return behaviorScreenReaderAction.performAction(ScreenReaderAction.PREVIOUS_WINDOW);
      default:
        return false;
    }
  }

  @Override
  public void onActivate() {
    lastFocusedNode.clear();
    // Braille the focused node, or if that fails, braille
    // the first focusable node.
    if (!brailleFocusedNode()) {
      brailleFirstFocusableNode();
    }
  }

  @Override
  public void onDeactivate() {}

  @Override
  public boolean onAccessibilityEvent(AccessibilityEvent event) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
        brailleNodeFromEvent(event);
        break;
      case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
        brailleFocusedNode();
        break;
      case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
      case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
        if (!brailleFocusedNode()) {
          // Since focus is typically not set in a newly opened
          // window, so braille the window as-if the first focusable
          // node had focus.  We don't update the focus because that
          // will make other services (e.g. talkback) reflect this
          // change, which is not desired.
          brailleFirstFocusableNode();
        }
        break;
      default:
        return true;
    }
    return true;
  }

  private boolean activateClickableSpan(Context context, ClickableSpan clickableSpan) {
    if (clickableSpan instanceof URLSpan) {
      final Intent intent =
          new Intent(Intent.ACTION_VIEW, Uri.parse(((URLSpan) clickableSpan).getURL()));
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      try {
        context.startActivity(intent);
      } catch (ActivityNotFoundException exception) {
        BrailleDisplayLog.e(TAG, "Failed to start activity", exception);
        return false;
      }
    } else {
      try {
        clickableSpan.onClick(null);
      } catch (RuntimeException exception) {
        BrailleDisplayLog.e(TAG, "Failed to invoke ClickableSpan", exception);
        return false;
      }
    }
    return true;
  }

  private AccessibilityNodeInfoCompat getFocusedNode(boolean fallbackOnRoot) {
    return behaviorFocus.getAccessibilityFocusNode(fallbackOnRoot);
  }

  /**
   * Formats some braille content from an {@link AccessibilityEvent}.
   *
   * @param event The event from which to format an utterance.
   * @return The formatted utterance.
   */
  private CellsContent formatEventToBraille(AccessibilityEvent event) {
    AccessibilityNodeInfoCompat eventNode = getNodeFromEvent(event);
    if (eventNode != null) {
      CellsContent ret = nodeBrailler.brailleNode(eventNode);
      ret.setPanStrategy(CellsContent.PAN_CURSOR);
      lastFocusedNode.reset(eventNode);
      return ret;
    }

    // Fall back on putting the event text on the display.
    // TODO: This can interfere with what's on the display and should be
    // done in a more disciplined manner.
    BrailleDisplayLog.v(TAG, "No node on event, falling back on event text");
    lastFocusedNode.clear();
    return new CellsContent(AccessibilityEventUtils.getEventText(event));
  }

  private void brailleNodeFromEvent(AccessibilityEvent event) {
    cellsContentConsumer.setContent(formatEventToBraille(event));
  }

  private boolean brailleFocusedNode() {
    AccessibilityNodeInfoCompat focused = getFocusedNode(false);
    if (focused != null) {
      CellsContent content = nodeBrailler.brailleNode(focused);
      if (focused.equals(lastFocusedNode.get())
          && (content.getPanStrategy() == CellsContent.PAN_RESET)) {
        content.setPanStrategy(CellsContent.PAN_KEEP);
      }
      cellsContentConsumer.setContent(content);
      lastFocusedNode.reset(focused);
      return true;
    }
    return false;
  }

  private void brailleFirstFocusableNode() {
    AccessibilityNodeInfoCompat root = getFocusedNode(true);
    if (root != null) {
      AccessibilityNodeInfoCompat toBraille;
      if (AccessibilityNodeInfoUtils.shouldFocusNode(root)) {
        toBraille = root;
      } else {
        TraversalStrategy traversalStrategy =
            TraversalStrategyUtils.getTraversalStrategy(
                root, behaviorFocus.createFocusFinder(), TraversalStrategy.SEARCH_FOCUS_FORWARD);
        toBraille = traversalStrategy.findFocus(root, TraversalStrategy.SEARCH_FOCUS_FORWARD);
        if (toBraille == null) {
          // Fall back on root as a last resort.
          toBraille = root;
        }
      }
      CellsContent content = nodeBrailler.brailleNode(toBraille);
      if (AccessibilityNodeInfoRef.isNull(lastFocusedNode)
          && (content.getPanStrategy() == CellsContent.PAN_RESET)) {
        content.setPanStrategy(CellsContent.PAN_KEEP);
      }
      lastFocusedNode.clear();
      cellsContentConsumer.setContent(content);
    }
  }

  @Nullable
  private AccessibilityNodeInfoCompat getNodeFromEvent(AccessibilityEvent event) {
    AccessibilityNodeInfo node = event.getSource();
    if (node != null) {
      return new AccessibilityNodeInfoCompat(node);
    } else {
      return null;
    }
  }
}
