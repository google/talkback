package com.google.android.accessibility.talkback.braille;

import static android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY;
import static android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT;
import static com.google.android.accessibility.talkback.Feedback.ContinuousRead.Action.INTERRUPT;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.COPY;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.CUT;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.PASTE;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.TYPO_CORRECTION;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.CLICK_CURRENT;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.CLICK_NODE;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.LONG_CLICK_CURRENT;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.LONG_CLICK_NODE;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.NEXT_PAGE;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.PREVIOUS_PAGE;
import static com.google.android.accessibility.talkback.Feedback.Speech.Action.TOGGLE_VOICE_FEEDBACK;
import static com.google.android.accessibility.talkback.Feedback.UniversalSearch.Action.TOGGLE_SEARCH;
import static com.google.android.accessibility.talkback.selector.SelectorController.Setting.ACTIONS;
import static com.google.android.accessibility.talkback.selector.SelectorController.Setting.CHANGE_ACCESSIBILITY_VOLUME;
import static com.google.android.accessibility.talkback.selector.SelectorController.Setting.SCROLLING_SEQUENTIAL;
import static com.google.android.accessibility.talkback.selector.SelectorController.Setting.SPEECH_RATE;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.input.CursorGranularity.CHARACTER;
import static com.google.android.accessibility.utils.input.CursorGranularity.CONTROL;
import static com.google.android.accessibility.utils.input.CursorGranularity.DEFAULT;
import static com.google.android.accessibility.utils.input.CursorGranularity.HEADING;
import static com.google.android.accessibility.utils.input.CursorGranularity.LINE;
import static com.google.android.accessibility.utils.input.CursorGranularity.LINK;
import static com.google.android.accessibility.utils.input.CursorGranularity.WEB_CONTROL;
import static com.google.android.accessibility.utils.input.CursorGranularity.WEB_HEADING;
import static com.google.android.accessibility.utils.input.CursorGranularity.WEB_LINK;
import static com.google.android.accessibility.utils.input.CursorGranularity.WINDOWS;
import static com.google.android.accessibility.utils.input.CursorGranularity.WORD;
import static com.google.android.accessibility.utils.monitor.InputModeTracker.INPUT_MODE_BRAILLE_DISPLAY;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_BACKWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_DOWN;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UP;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.interfaces.ScreenReaderActionPerformer;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Feedback.EditText.Action;
import com.google.android.accessibility.talkback.Pipeline.FeedbackReturner;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.actor.SystemActionPerformer;
import com.google.android.accessibility.talkback.contextmenu.ListMenuManager;
import com.google.android.accessibility.talkback.selector.SelectorController;
import com.google.android.accessibility.talkback.selector.SelectorController.AnnounceType;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.SpellingSuggestion;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.input.CursorGranularity;

/** Helper that handles the screen reader actions come from braille display or braille keyboard. */
public final class BrailleHelper implements ScreenReaderActionPerformer {
  private final ListMenuManager menuManager;
  private final SelectorController selectorController;
  private final FocusFinder focusFinder;
  private AccessibilityService accessibilityService;
  private FeedbackReturner feedbackReturner;
  private ActorState actorState;

  public BrailleHelper(
      AccessibilityService accessibilityService,
      FeedbackReturner feedbackReturner,
      ActorState actorState,
      ListMenuManager menuManager,
      SelectorController selectorController,
      FocusFinder focusFinder) {
    this.accessibilityService = accessibilityService;
    this.feedbackReturner = feedbackReturner;
    this.actorState = actorState;
    this.menuManager = menuManager;
    this.selectorController = selectorController;
    this.focusFinder = focusFinder;
  }

  @Override
  public boolean performAction(ScreenReaderAction action, int inputMode, Object... args) {
    // Talkback interrupts speech when touch starts.  We also interrupts speech when performing
    // screen reader actions.
    feedbackReturner.returnFeedback(
        Performance.EVENT_ID_UNTRACKED, Feedback.part().setInterruptGentle(true));
    switch (action) {
      case NEXT_ITEM:
        return performFocusAction(SEARCH_FOCUS_FORWARD);
      case PREVIOUS_ITEM:
        return performFocusAction(SEARCH_FOCUS_BACKWARD);
      case NEXT_LINE:
        return performFocusAction(SEARCH_FOCUS_DOWN);
      case PREVIOUS_LINE:
        return performFocusAction(SEARCH_FOCUS_UP);
      case NEXT_WINDOW:
        return performGranularityFocusAction(SEARCH_FOCUS_FORWARD, WINDOWS, inputMode);
      case PREVIOUS_WINDOW:
        return performGranularityFocusAction(SEARCH_FOCUS_BACKWARD, WINDOWS, inputMode);
      case GLOBAL_HOME:
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
      case GLOBAL_BACK:
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
      case GLOBAL_RECENTS:
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
      case GLOBAL_NOTIFICATIONS:
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
      case GLOBAL_QUICK_SETTINGS:
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
      case GLOBAL_ALL_APPS:
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS);
      case PLAY_PAUSE_MEDIA:
        return feedbackReturner.returnFeedback(
            Performance.EVENT_ID_UNTRACKED,
            Feedback.systemAction(SystemActionPerformer.GLOBAL_ACTION_KEYCODE_HEADSETHOOK));
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
        selectorController.adjustSelectedSetting(
            EVENT_ID_UNTRACKED, shouldReverseAdjustReadingControl(accessibilityService));
        return true;
      case NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_FORWARD:
        selectorController.adjustSelectedSetting(
            EVENT_ID_UNTRACKED, !shouldReverseAdjustReadingControl(accessibilityService));
        return true;
      case WEB_NEXT_HEADING:
        return performGranularityFocusAction(SEARCH_FOCUS_FORWARD, WEB_HEADING, inputMode);
      case WEB_PREVIOUS_HEADING:
        return performGranularityFocusAction(SEARCH_FOCUS_BACKWARD, WEB_HEADING, inputMode);
      case WEB_NEXT_CONTROL:
        return performGranularityFocusAction(SEARCH_FOCUS_FORWARD, WEB_CONTROL, inputMode);
      case WEB_PREVIOUS_CONTROL:
        return performGranularityFocusAction(SEARCH_FOCUS_BACKWARD, WEB_CONTROL, inputMode);
      case WEB_NEXT_LINK:
        return performGranularityFocusAction(SEARCH_FOCUS_FORWARD, WEB_LINK, inputMode);
      case WEB_PREVIOUS_LINK:
        return performGranularityFocusAction(SEARCH_FOCUS_BACKWARD, WEB_LINK, inputMode);
      case NEXT_HEADING:
        return performGranularityFocusAction(SEARCH_FOCUS_FORWARD, HEADING, inputMode);
      case PREVIOUS_HEADING:
        return performGranularityFocusAction(SEARCH_FOCUS_BACKWARD, HEADING, inputMode);
      case NEXT_CONTROL:
        return performGranularityFocusAction(SEARCH_FOCUS_FORWARD, CONTROL, inputMode);
      case PREVIOUS_CONTROL:
        return performGranularityFocusAction(SEARCH_FOCUS_BACKWARD, CONTROL, inputMode);
      case NEXT_LINK:
        return performGranularityFocusAction(SEARCH_FOCUS_FORWARD, LINK, inputMode);
      case PREVIOUS_LINK:
        return performGranularityFocusAction(SEARCH_FOCUS_BACKWARD, LINK, inputMode);
      case ACCESSIBILITY_FOCUS:
        if (args[0] != null && args[0] instanceof AccessibilityNodeInfoCompat) {
          AccessibilityNodeInfoCompat node = (AccessibilityNodeInfoCompat) args[0];
          return node.performAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);
        }
        return false;
      case FOCUS_NEXT_CHARACTER:
        return performGranularityFocusAction(SEARCH_FOCUS_FORWARD, CHARACTER, inputMode);
      case FOCUS_PREVIOUS_CHARACTER:
        return performGranularityFocusAction(SEARCH_FOCUS_BACKWARD, CHARACTER, inputMode);
      case FOCUS_NEXT_WORD:
        return performGranularityFocusAction(SEARCH_FOCUS_FORWARD, WORD, inputMode);
      case FOCUS_PREVIOUS_WORD:
        return performGranularityFocusAction(SEARCH_FOCUS_BACKWARD, WORD, inputMode);
      case FOCUS_NEXT_LINE:
        return performGranularityFocusAction(SEARCH_FOCUS_FORWARD, LINE, inputMode);
      case FOCUS_PREVIOUS_LINE:
        return performGranularityFocusAction(SEARCH_FOCUS_BACKWARD, LINE, inputMode);
      case CLICK_CURRENT:
        if (SelectorController.getCurrentSetting(accessibilityService).equals(ACTIONS)) {
          selectorController.activateCurrentAction(Performance.EVENT_ID_UNTRACKED);
          return true;
        }
        return feedbackReturner.returnFeedback(
            Performance.EVENT_ID_UNTRACKED, Feedback.focus(CLICK_CURRENT));
      case LONG_CLICK_CURRENT:
        return feedbackReturner.returnFeedback(
            Performance.EVENT_ID_UNTRACKED, Feedback.focus(LONG_CLICK_CURRENT));
      case CLICK_NODE:
        {
          if (args[0] != null && args[0] instanceof AccessibilityNodeInfoCompat) {
            AccessibilityNodeInfoCompat node = (AccessibilityNodeInfoCompat) args[0];
            if (node.isAccessibilityFocused()
                && SelectorController.getCurrentSetting(accessibilityService).equals(ACTIONS)) {
              selectorController.activateCurrentAction(Performance.EVENT_ID_UNTRACKED);
              return true;
            }
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
      case STOP_READING:
        return feedbackReturner.returnFeedback(
            EVENT_ID_UNTRACKED, Feedback.continuousRead(INTERRUPT));
      case CUT:
        return performEditAction(CUT);
      case COPY:
        return performEditAction(COPY);
      case PASTE:
        return performEditAction(PASTE);
      case SELECT_PREVIOUS_CHARACTER:
        return performSelectedAction(SEARCH_FOCUS_BACKWARD, CHARACTER, inputMode);
      case SELECT_NEXT_CHARACTER:
        return performSelectedAction(SEARCH_FOCUS_FORWARD, CHARACTER, inputMode);
      case SELECT_PREVIOUS_WORD:
        return performSelectedAction(SEARCH_FOCUS_BACKWARD, WORD, inputMode);
      case SELECT_NEXT_WORD:
        return performSelectedAction(SEARCH_FOCUS_FORWARD, WORD, inputMode);
      case SELECT_PREVIOUS_LINE:
        return performSelectedAction(SEARCH_FOCUS_BACKWARD, LINE, inputMode);
      case SELECT_NEXT_LINE:
        return performSelectedAction(SEARCH_FOCUS_FORWARD, LINE, inputMode);
      case TYPO_CORRECT:
        return feedbackReturner.returnFeedback(
            Feedback.create(
                EVENT_ID_UNTRACKED,
                Feedback.part()
                    .setEdit(
                        Feedback.edit((AccessibilityNodeInfoCompat) args[0], TYPO_CORRECTION)
                            .setText((CharSequence) args[1])
                            .setSpellingSuggestion((SpellingSuggestion) args[2])
                            .build())
                    .build()));
      default:
        // fall through
    }
    return false;
  }

  private boolean performGlobalAction(int globalAction) {
    return feedbackReturner.returnFeedback(
        Performance.EVENT_ID_UNTRACKED, Feedback.systemAction(globalAction));
  }

  private boolean performFocusAction(int focusAction) {
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
      int focusAction, CursorGranularity granularity, int inputMode) {
    feedbackReturner.returnFeedback(
        Performance.EVENT_ID_UNTRACKED, Feedback.granularity(granularity));
    return feedbackReturner.returnFeedback(
        Performance.EVENT_ID_UNTRACKED,
        Feedback.focusDirection(focusAction)
            .setGranularity(granularity)
            .setInputMode(inputMode)
            .setToWindow(granularity.equals(CursorGranularity.WINDOWS))
            .setDefaultToInputFocus(true)
            .setScroll(true)
            .setWrap(true));
  }

  private boolean performEditAction(Action action) {
    if (isFocusedNodeEditable()) {
      return feedbackReturner.returnFeedback(
          EVENT_ID_UNTRACKED, Feedback.edit(getAccessibilityNodeInfoCompat(), action));
    }
    return false;
  }

  private boolean shouldReverseAdjustReadingControl(Context context) {
    SelectorController.Setting currentReadingControl =
        SelectorController.getCurrentSetting(context);
    return currentReadingControl == SPEECH_RATE
        || currentReadingControl == SCROLLING_SEQUENTIAL
        || currentReadingControl == CHANGE_ACCESSIBILITY_VOLUME;
  }

  private boolean isFocusedNodeEditable() {
    AccessibilityNodeInfoCompat focusedNode = getAccessibilityNodeInfoCompat();
    return focusedNode != null && focusedNode.isEditable();
  }

  private boolean performSelectedAction(
      int focusAction, CursorGranularity granularity, int inputMode) {
    boolean result;
    if (actorState.getDirectionNavigation().isSelectionModeActive()) {
      result = performGranularityFocusAction(focusAction, granularity, inputMode);
    } else {
      setSelectionModeOn();

      // TODO: The selected mode doesn't successfully as first turn on after typing
      // text, so we need to turn on again.
      if (!actorState.getDirectionNavigation().isSelectionModeActive()) {
        setSelectionModeOn();
      }

      result = performGranularityFocusAction(focusAction, granularity, inputMode);
      feedbackReturner.returnFeedback(Performance.EVENT_ID_UNTRACKED, Feedback.selectionModeOff());
    }
    return result;
  }

  private void setSelectionModeOn() {
    feedbackReturner.returnFeedback(
        Performance.EVENT_ID_UNTRACKED, Feedback.selectionModeOn(getAccessibilityNodeInfoCompat()));
  }

  private AccessibilityNodeInfoCompat getAccessibilityNodeInfoCompat() {
    AccessibilityNodeInfoCompat node = focusFinder.findFocusCompat(FOCUS_ACCESSIBILITY);
    return node == null ? focusFinder.findFocusCompat(FOCUS_INPUT) : node;
  }
}
