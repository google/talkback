package com.google.android.accessibility.talkback.brailledisplay;

import static com.google.android.accessibility.talkback.Feedback.Focus.Action.CLICK_CURRENT;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.CLICK_NODE;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.LONG_CLICK_CURRENT;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.LONG_CLICK_NODE;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.NEXT_PAGE;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.PREVIOUS_PAGE;
import static com.google.android.accessibility.talkback.Feedback.Speech.Action.TOGGLE_VOICE_FEEDBACK;
import static com.google.android.accessibility.talkback.Feedback.UniversalSearch.Action.TOGGLE_SEARCH;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.input.CursorGranularity.DEFAULT;
import static com.google.android.accessibility.utils.input.CursorGranularity.WEB_CONTROL;
import static com.google.android.accessibility.utils.input.CursorGranularity.WEB_HEADING;
import static com.google.android.accessibility.utils.input.CursorGranularity.WEB_LINK;
import static com.google.android.accessibility.utils.input.CursorGranularity.WINDOWS;
import static com.google.android.accessibility.utils.monitor.InputModeTracker.INPUT_MODE_BRAILLE_DISPLAY;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_BACKWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_DOWN;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UP;

import android.accessibilityservice.AccessibilityService;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleDisplay.ScreenReaderAction;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline.FeedbackReturner;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.contextmenu.ListMenuManager;
import com.google.android.accessibility.talkback.selector.SelectorController;
import com.google.android.accessibility.talkback.selector.SelectorController.AnnounceType;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.input.CursorGranularity;

/** Helper that handles the screen reader actions come from brille display. */
public final class BrailleDisplayHelper {
  private final ListMenuManager menuManager;
  private final SelectorController selectorController;

  public BrailleDisplayHelper(ListMenuManager menuManager, SelectorController selectorController) {
    this.menuManager = menuManager;
    this.selectorController = selectorController;
  }

  public boolean performAction(
      AccessibilityService accessibilityService,
      FeedbackReturner feedbackReturner,
      ScreenReaderAction action,
      Object... args) {
    switch (action) {
      case NEXT_ITEM:
        return performFocusAction(feedbackReturner, SEARCH_FOCUS_FORWARD);
      case PREVIOUS_ITEM:
        return performFocusAction(feedbackReturner, SEARCH_FOCUS_BACKWARD);
      case NEXT_LINE:
        return performFocusAction(feedbackReturner, SEARCH_FOCUS_DOWN);
      case PREVIOUS_LINE:
        return performFocusAction(feedbackReturner, SEARCH_FOCUS_UP);
      case NEXT_WINDOW:
        return performGranularityFocusAction(feedbackReturner, SEARCH_FOCUS_FORWARD, WINDOWS);
      case PREVIOUS_WINDOW:
        return performGranularityFocusAction(feedbackReturner, SEARCH_FOCUS_BACKWARD, WINDOWS);
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
      case NEXT_READING_CONTROL:
        selectorController.selectPreviousOrNextSetting(
            EVENT_ID_UNTRACKED, AnnounceType.DESCRIPTION, true);
        return true;
      case PREVIOUS_READING_CONTROL:
        selectorController.selectPreviousOrNextSetting(
            EVENT_ID_UNTRACKED, AnnounceType.DESCRIPTION, false);
        return true;
      case SCREEN_SEARCH:
        return feedbackReturner.returnFeedback(
            Performance.EVENT_ID_UNTRACKED, Feedback.universalSearch(TOGGLE_SEARCH));
      case SCROLL_FORWARD:
        return feedbackReturner.returnFeedback(
            Performance.EVENT_ID_UNTRACKED, Feedback.focusDirection(NEXT_PAGE));
      case SCROLL_BACKWARD:
        return feedbackReturner.returnFeedback(
            Performance.EVENT_ID_UNTRACKED, Feedback.focusDirection(PREVIOUS_PAGE));
      case NAVIGATE_TO_TOP:
        return feedbackReturner.returnFeedback(
            Performance.EVENT_ID_UNTRACKED, Feedback.focusTop(INPUT_MODE_BRAILLE_DISPLAY));
      case NAVIGATE_TO_BOTTOM:
        return feedbackReturner.returnFeedback(
            Performance.EVENT_ID_UNTRACKED, Feedback.focusBottom(INPUT_MODE_BRAILLE_DISPLAY));
      case NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_BACKWARD:
        selectorController.adjustSelectedSetting(EVENT_ID_UNTRACKED, /* isNext= */ false);
        return true;
      case NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_FORWARD:
        selectorController.adjustSelectedSetting(EVENT_ID_UNTRACKED, /* isNext= */ true);
        return true;
      case WEB_NEXT_HEADING:
        return performGranularityFocusAction(feedbackReturner, SEARCH_FOCUS_FORWARD, WEB_HEADING);
      case WEB_PREVIOUS_HEADING:
        return performGranularityFocusAction(feedbackReturner, SEARCH_FOCUS_BACKWARD, WEB_HEADING);
      case WEB_NEXT_CONTROL:
        return performGranularityFocusAction(feedbackReturner, SEARCH_FOCUS_FORWARD, WEB_CONTROL);
      case WEB_PREVIOUS_CONTROL:
        return performGranularityFocusAction(feedbackReturner, SEARCH_FOCUS_BACKWARD, WEB_CONTROL);
      case WEB_NEXT_LINK:
        return performGranularityFocusAction(feedbackReturner, SEARCH_FOCUS_FORWARD, WEB_LINK);
      case WEB_PREVIOUS_LINK:
        return performGranularityFocusAction(feedbackReturner, SEARCH_FOCUS_BACKWARD, WEB_LINK);
      case CLICK_CURRENT:
        return feedbackReturner.returnFeedback(
            Performance.EVENT_ID_UNTRACKED, Feedback.focus(CLICK_CURRENT));
      case LONG_CLICK_CURRENT:
        return feedbackReturner.returnFeedback(
            Performance.EVENT_ID_UNTRACKED, Feedback.focus(LONG_CLICK_CURRENT));
      case CLICK_NODE:
        {
          if (args[0] != null && args[0] instanceof AccessibilityNodeInfoCompat) {
            AccessibilityNodeInfoCompat node = (AccessibilityNodeInfoCompat) args[0];
            return feedbackReturner.returnFeedback(
                Feedback.create(
                    Performance.EVENT_ID_UNTRACKED,
                    Feedback.part()
                        .setFocus(Feedback.focus(CLICK_NODE).setTarget(node).build())
                        .build()));
          }
          return false;
        }
      case LONG_CLICK_NODE:
        {
          if (args[0] != null && args[0] instanceof AccessibilityNodeInfoCompat) {
            AccessibilityNodeInfoCompat node = (AccessibilityNodeInfoCompat) args[0];
            return feedbackReturner.returnFeedback(
                Feedback.create(
                    Performance.EVENT_ID_UNTRACKED,
                    Feedback.part()
                        .setFocus(Feedback.focus(LONG_CLICK_NODE).setTarget(node).build())
                        .build()));
          }
          return false;
        }
      case TOGGLE_VOICE_FEEDBACK:
        return feedbackReturner.returnFeedback(
            Performance.EVENT_ID_UNTRACKED, Feedback.speech(TOGGLE_VOICE_FEEDBACK));
      case OPEN_TALKBACK_MENU:
        return menuManager.showMenu(R.menu.context_menu, EVENT_ID_UNTRACKED);
      default:
        // fall through
    }
    return false;
  }

  private boolean performGlobalAction(FeedbackReturner feedbackReturner, int globalAction) {
    return feedbackReturner.returnFeedback(
        Performance.EVENT_ID_UNTRACKED, Feedback.systemAction(globalAction));
  }

  private boolean performFocusAction(FeedbackReturner feedbackReturner, int focusAction) {
    return feedbackReturner.returnFeedback(
        Performance.EVENT_ID_UNTRACKED,
        Feedback.focusDirection(focusAction)
            // Sets granularity to default because braille display navigation actions always moves
            // at default granularity.
            .setGranularity(DEFAULT)
            .setInputMode(INPUT_MODE_BRAILLE_DISPLAY)
            .setWrap(true)
            .setScroll(true)
            .setDefaultToInputFocus(true));
  }

  private boolean performGranularityFocusAction(
      FeedbackReturner feedbackReturner, int focusAction, CursorGranularity granularity) {
    feedbackReturner.returnFeedback(
        Performance.EVENT_ID_UNTRACKED, Feedback.granularity(granularity));
    return feedbackReturner.returnFeedback(
        Performance.EVENT_ID_UNTRACKED,
        Feedback.focusDirection(focusAction)
            .setInputMode(INPUT_MODE_BRAILLE_DISPLAY)
            .setToWindow(granularity.equals(CursorGranularity.WINDOWS))
            .setDefaultToInputFocus(true)
            .setScroll(true)
            .setWrap(true));
  }
}
