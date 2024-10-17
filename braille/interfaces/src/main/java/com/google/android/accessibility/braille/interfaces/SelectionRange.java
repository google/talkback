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

package com.google.android.accessibility.braille.interfaces;

import java.util.Objects;

/** Index of start and end of selected text. */
public class SelectionRange {
  /** The start selection index. */
  public final int start;
  /** The end selection index. */
  public final int end;

  public SelectionRange(int start, int end) {
    this.start = start;
    this.end = end;
  }

  /** Gets the upper index within {@link SelectionRange#start} and {@link SelectionRange#end}. */
  public int getUpper() {
    return Math.max(start, end);
  }

  /** Gets the lower index within {@link SelectionRange#start} and {@link SelectionRange#end}. */
  public int getLower() {
    return Math.min(start, end);
  }

  @Override
  public int hashCode() {
    return Objects.hash(start, end);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SelectionRange)) {
      return false;
    }
    SelectionRange selection = (SelectionRange) o;
    return start == selection.start && end == selection.end;
  }

  @Override
  public String toString() {
    return String.format("SelectionRange {start=%s, end=%s}", start, end);
  }
}
