package com.google.android.accessibility.braille.brailledisplay.controller;

import android.text.style.ClickableSpan;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import java.util.Optional;

/** Consumer of {@link CellsContent}. */
public interface CellsContentConsumer {

  /** Reason for update {@link CellsContent}. */
  enum Reason {
    START_UP,
    NAVIGATE_TO_NEW_NODE,
    WINDOW_CHANGED,
    SCREEN_OFF,
  }

  /** Sets common content. */
  void setContent(CellsContent content, Reason reason);

  /** Sets timed content with specific duration. */
  void setTimedContent(CellsContent content, int durationInMilliseconds);

  /** Returns accessibility node at the given position. */
  AccessibilityNodeInfoCompat getAccessibilityNode(int routingKeyIndex);

  /** Returns the index in the whole content. */
  int getTextIndexInWhole(int routingKeyIndex);

  /** Gets the {@link android.text.style.ClickableSpan} in the specific index. */
  Optional<ClickableSpan[]> getClickableSpans(int routingKeyIndex);

  /** Clears timed message. */
  void clearTimedMessage();

  /** Returns whether timed message is displaying. */
  boolean isTimedMessageDisplaying();
}
