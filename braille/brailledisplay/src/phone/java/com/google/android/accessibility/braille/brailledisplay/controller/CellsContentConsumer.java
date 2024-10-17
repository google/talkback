/*
 * Copyright (C) 2023 Google Inc.
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
