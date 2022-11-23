package com.google.android.accessibility.braille.brailledisplay.controller;

import android.text.style.ClickableSpan;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import java.util.Optional;

/** Consumer of {@link CellsContent}. */
public interface CellsContentConsumer {
  void setContent(CellsContent content);

  /** Returns accessibility node at the given position. */
  AccessibilityNodeInfoCompat getAccessibilityNode(int routingKeyIndex);

  /** Returns the index in the whole content. */
  int getTextIndexInWhole(int routingKeyIndex);

  /** Gets the {@link android.text.style.ClickableSpan} in the specific index. */
  Optional<ClickableSpan[]> getClickableSpans(int routingKeyIndex);
}
