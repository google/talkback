package com.google.android.accessibility.talkback.brailledisplay;

import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.NEXT_PAGE;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.PREVIOUS_PAGE;
import static com.google.android.accessibility.utils.input.CursorGranularity.DEFAULT;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_TOUCH;

import android.accessibilityservice.AccessibilityService;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleDisplay.ScreenReaderAction;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline.FeedbackReturner;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.input.InputModeManager;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;

/** Helper that handles the screen reader actions come from brille display. */
public final class BrailleDisplayHelper {
  public static boolean performAction(
      FeedbackReturner feedbackReturner, ScreenReaderAction action) {
    switch (action) {
      case NEXT_ITEM:
        return performFocusAction(feedbackReturner, TraversalStrategy.SEARCH_FOCUS_FORWARD);
      case PREVIOUS_ITEM:
        return performFocusAction(feedbackReturner, TraversalStrategy.SEARCH_FOCUS_BACKWARD);
      case NEXT_LINE:
        return performFocusAction(feedbackReturner, TraversalStrategy.SEARCH_FOCUS_DOWN);
      case PREVIOUS_LINE:
        return performFocusAction(feedbackReturner, TraversalStrategy.SEARCH_FOCUS_UP);
      case GLOBAL_HOME:
        return performGlobalAction(feedbackReturner, AccessibilityService.GLOBAL_ACTION_HOME);
      case GLOBAL_BACK:
        return performGlobalAction(feedbackReturner, AccessibilityService.GLOBAL_ACTION_BACK);
      case GLOBAL_RECENTS:
        return performGlobalAction(feedbackReturner, AccessibilityService.GLOBAL_ACTION_RECENTS);
      case GLOBAL_NOTIFICATIONS:
        return performGlobalAction(
            feedbackReturner, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
      case GLOBAL_QUICK_SETTINGS:
        return performGlobalAction(
            feedbackReturner, AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
      case GLOBAL_ALL_APPS:
        return performGlobalAction(
            feedbackReturner, AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS);
      case SCROLL_FORWARD:
        return feedbackReturner.returnFeedback(
            Performance.EVENT_ID_UNTRACKED, Feedback.focusDirection(NEXT_PAGE));
      case SCROLL_BACKWARD:
        return feedbackReturner.returnFeedback(
            Performance.EVENT_ID_UNTRACKED, Feedback.focusDirection(PREVIOUS_PAGE));
      case NAVIGATE_TO_TOP:
        return feedbackReturner.returnFeedback(
            Performance.EVENT_ID_UNTRACKED, Feedback.focusTop(INPUT_MODE_TOUCH));
      case NAVIGATE_TO_BOTTOM:
        return feedbackReturner.returnFeedback(
            Performance.EVENT_ID_UNTRACKED, Feedback.focusBottom(INPUT_MODE_TOUCH));
      default:
        // fall through
    }
    return false;
  }

  private static boolean performGlobalAction(FeedbackReturner feedbackReturner, int globalAction) {
    return feedbackReturner.returnFeedback(
        Performance.EVENT_ID_UNTRACKED, Feedback.systemAction(globalAction));
  }

  private static boolean performFocusAction(FeedbackReturner feedbackReturner, int focusAction) {
    return feedbackReturner.returnFeedback(
        Performance.EVENT_ID_UNTRACKED,
        Feedback.focusDirection(focusAction)
            // Sets granularity to default because braille display navigation actions always moves
            // at default granularity.
            .setGranularity(DEFAULT)
            .setInputMode(InputModeManager.INPUT_MODE_KEYBOARD)
            .setWrap(true)
            .setScroll(true)
            .setDefaultToInputFocus(true));
  }

  private BrailleDisplayHelper() {}
}
