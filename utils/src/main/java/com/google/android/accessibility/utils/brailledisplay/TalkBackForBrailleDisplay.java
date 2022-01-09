package com.google.android.accessibility.utils.brailledisplay;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.FocusFinder;

/** Exposes some TalkBack behavior to BrailleDisplay. */
public interface TalkBackForBrailleDisplay {
  /** Performs specific actions for screen reader. */
  boolean performAction(ScreenReaderAction action);

  /** Gets accessibility focus node. */
  AccessibilityNodeInfoCompat getAccessibilityFocusNode(boolean fallbackOnRoot);

  /** Creates {@link FocusFinder} instance. */
  FocusFinder createFocusFinder();

  /** Shows custom label dialog for the Accessibility node to add or edit a label. */
  boolean showLabelDialog(CustomLabelAction action, AccessibilityNodeInfoCompat node);

  /**
   * Gets the content description of a {@param AccessibilityNodeInfoCompat node} (if available) or
   * defined custom label.
   */
  CharSequence getNodeText(AccessibilityNodeInfoCompat node);

  /** Returns whether {@param AccessibilityNodeInfoCompat node} needs a label. */
  boolean needsLabel(AccessibilityNodeInfoCompat node);

  /** Screen reader actions. */
  public enum ScreenReaderAction {
    NEXT_ITEM,
    PREVIOUS_ITEM,
    NEXT_LINE,
    PREVIOUS_LINE,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    NAVIGATE_TO_TOP,
    NAVIGATE_TO_BOTTOM,
    ACTIVATE_CURRENT,
    NEXT_SECTION,
    PREVIOUS_SECTION,
    CONTROL_NEXT,
    CONTROL_PREVIOUS,
    NEXT_LIST,
    PREVIOUS_LIST,
    GLOBAL_HOME,
    GLOBAL_BACK,
    GLOBAL_RECENTS,
    GLOBAL_NOTIFICATIONS,
  }

  /** Custom label actions. */
  enum CustomLabelAction {
    ADD_LABEL,
    EDIT_LABEL
  }
}
