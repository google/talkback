/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.talkback;

import android.graphics.Rect;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Implements text segment iterators for accessibility support. Contains only character, word and
 * paragraph iterators.
 *
 * <p>Note: Such iterators are needed in Talkback since we want to be able to iterator over content
 * description or hint text in case of edit texts and that is not supported by framework.
 */
public final class GranularityIterator {

  /** Interface defining functions to be supported by iterators */
  public interface TextSegmentIterator {

    /**
     * Returns the segment just after the cursor.
     *
     * <p>Incase the text on which it is called is an empty string or the current cursor position is
     * at the end of the text, it returns {@code null}.
     *
     * @param current the current cursor position.
     * @return the segment just after the cursor or {@code null}.
     */
    @Nullable
    int[] following(int current);

    /**
     * Returns the segment just preceding the cursor.
     *
     * <p>Incase the text on which it is called is an empty string or the current cursor position is
     * at the start of the text, it returns {@code null}.
     *
     * @param current the cursor position.
     * @return the segment just preceding the cursor or {@code null}.
     */
    @Nullable
    int[] preceding(int current);
  }

  /**
   * Gets the iterator for granularity traversal. Talkback can handle only character, word and
   * paragraph granularity movements. If the granularity is not supported by Talkback or the text is
   * null, it returns {@code null}.
   *
   * @param text on which granularity traversal has to be performed.
   * @param granularity that has been requested by the user.
   * @return the iterator for text traversal or {@code null}.
   */
  @Nullable
  static TextSegmentIterator getIteratorForGranularity(CharSequence text, int granularity) {

    switch (granularity) {
      case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER:
        {
          CharacterTextSegmentIterator iterator =
              CharacterTextSegmentIterator.getInstance(Locale.getDefault());
          iterator.initialize(text.toString());
          return iterator;
        }
      case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD:
        {
          WordTextSegmentIterator iterator =
              WordTextSegmentIterator.getInstance(Locale.getDefault());
          iterator.initialize(text.toString());
          return iterator;
        }
      case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH:
        {
          ParagraphTextSegmentIterator iterator = ParagraphTextSegmentIterator.getInstance();
          iterator.initialize(text.toString());
          return iterator;
        }
    }
    return null;
  }

  /**
   * Gets the iterator for line granularity traversal.
   *
   * @param node the node users wish to navigate within is used to get the location of text.
   * @param text the text users wish to navigate within.
   * @return the iterator for text traversal.
   */
  static TextSegmentIterator getLineIterator(AccessibilityNodeInfoCompat node, CharSequence text) {
    LineTextSegmentIterator iterator = LineTextSegmentIterator.getInstance();
    iterator.initialize(node, text);
    return iterator;
  }

  /** Top level class for Talkback iterators */
  private abstract static class AbstractTextSegmentIterator implements TextSegmentIterator {

    private String iteratorText;

    private final int[] segment = new int[2];

    String getIteratorText() {
      return iteratorText;
    }

    void initialize(String text) {
      iteratorText = text;
    }

    /**
     * Returns the range of the indices in an array format or {@code null} incase the start and end
     * positions seem invalid.
     *
     * @param start the start index.
     * @param end the end index.
     * @return an array consisting of the start and end index or {@code null}.
     */
    @Nullable
    int[] getRange(int start, int end) {
      if (start < 0 || end < 0 || start == end) {
        return null;
      }
      segment[0] = start;
      segment[1] = end;
      return segment;
    }
  }

  private static class CharacterTextSegmentIterator extends AbstractTextSegmentIterator {
    // TODO: For BreakIterator, migrating to android.icu APIs from other Android SDK
    // APIs.
    final BreakIterator breakIterator;

    private static CharacterTextSegmentIterator instance;
    private Locale iteratorLocale;

    private CharacterTextSegmentIterator(Locale locale) {
      this(locale, BreakIterator.getCharacterInstance(locale));
    }

    CharacterTextSegmentIterator(Locale locale, BreakIterator breakIterator) {
      setLocale(locale);
      this.breakIterator = breakIterator;
    }

    private static CharacterTextSegmentIterator getInstance(Locale locale) {
      // If the locale changes, the instance formed using the previous locale is not required.
      // We just need one instance but with the correct locale.
      if (instance == null || !instance.getLocale().equals(locale)) {
        instance = new CharacterTextSegmentIterator(locale);
      }
      return instance;
    }

    @Override
    void initialize(String text) {
      super.initialize(text);
      breakIterator.setText(text);
    }

    @Override
    @Nullable
    public int[] following(int offset) {
      final int textLegth = getIteratorText().length();
      if (textLegth <= 0) {
        return null;
      }
      if (offset >= textLegth) {
        return null;
      }
      int start = offset;
      if (start < 0) {
        start = 0;
      }
      while (!breakIterator.isBoundary(start)) {
        start = breakIterator.following(start);
        if (start == BreakIterator.DONE) {
          return null;
        }
      }
      final int end = breakIterator.following(start);
      if (end == BreakIterator.DONE) {
        return null;
      }
      return getRange(start, end);
    }

    @Override
    @Nullable
    public int[] preceding(int offset) {
      final int textLegth = getIteratorText().length();
      if (textLegth <= 0) {
        return null;
      }
      if (offset <= 0) {
        return null;
      }
      int end = offset;
      if (end > textLegth) {
        end = textLegth;
      }
      while (!breakIterator.isBoundary(end)) {
        end = breakIterator.preceding(end);
        if (end == BreakIterator.DONE) {
          return null;
        }
      }
      final int start = breakIterator.preceding(end);
      if (start == BreakIterator.DONE) {
        return null;
      }
      return getRange(start, end);
    }

    Locale getLocale() {
      return iteratorLocale;
    }

    void setLocale(Locale locale) {
      iteratorLocale = locale;
    }
  }

  private static class WordTextSegmentIterator extends CharacterTextSegmentIterator {
    private static WordTextSegmentIterator instance;

    private WordTextSegmentIterator(Locale locale) {
      super(locale, BreakIterator.getWordInstance(locale));
    }

    private static WordTextSegmentIterator getInstance(Locale locale) {
      if (instance == null || !instance.getLocale().equals(locale)) {
        instance = new WordTextSegmentIterator(locale);
      }
      return instance;
    }

    @Override
    @Nullable
    public int[] following(int offset) {
      final int textLegth = getIteratorText().length();
      if (textLegth <= 0) {
        return null;
      }
      if (offset >= getIteratorText().length()) {
        return null;
      }
      int start = offset;
      if (start < 0) {
        start = 0;
      }
      while (!isLetterOrDigit(start)) {
        start = breakIterator.following(start);
        if (start == BreakIterator.DONE) {
          return null;
        }
      }
      final int end = breakIterator.following(start);
      if (end == BreakIterator.DONE) {
        return null;
      }
      return getRange(start, end);
    }

    @Override
    @Nullable
    public int[] preceding(int offset) {
      final int textLegth = getIteratorText().length();
      if (textLegth <= 0) {
        return null;
      }
      if (offset <= 0) {
        return null;
      }
      int end = offset;
      if (end > textLegth) {
        end = textLegth;
      }
      while (end > 0 && !isLetterOrDigit(end - 1)) {
        end = breakIterator.preceding(end);
        if (end == BreakIterator.DONE) {
          return null;
        }
      }
      int start = breakIterator.preceding(end);
      if (start == BreakIterator.DONE) {
        return null;
      }
      return getRange(start, end);
    }

    private boolean isLetterOrDigit(int index) {
      if (index >= 0 && index < getIteratorText().length()) {
        final int codePoint = getIteratorText().codePointAt(index);
        return Character.isLetterOrDigit(codePoint);
      }
      return false;
    }
  }

  /**
   * An iterator for line granularity traversal.
   *
   * <p>The next segment is the following line if the cursor is at the end of the line. Otherwise,
   * the text after the cursor in the current line is the next segment.
   *
   * <p>The previous segment is the preceding line if the cursor is at the beginning of the line.
   * Otherwise, the text before the cursor in the current line is the previous segment.
   */
  // TODO: It's better to take these private static classes out as package-private
  // classes to maintain easily because they have large logic.
  private static class LineTextSegmentIterator extends AbstractTextSegmentIterator {

    private static class LazyHolder {
      static final LineTextSegmentIterator INSTANCE = new LineTextSegmentIterator();
    }

    private static final int INVALID_INDEX = -1;

    private LineTextSegmentIterator() {}

    private static LineTextSegmentIterator getInstance() {
      return LazyHolder.INSTANCE;
    }

    private AccessibilityNodeInfoCompat node;

    void initialize(AccessibilityNodeInfoCompat node, CharSequence text) {
      super.initialize(text.toString());
      this.node = node;
    }

    /**
     * {@inheritDoc}
     *
     * <p>If the text location list for the visible text in the node is empty, the returned value is
     * also {@code null}.
     */
    @Nullable
    @Override
    public int[] following(int current) {
      CharSequence text = getIteratorText();
      if (TextUtils.isEmpty(text) || node == null) {
        return null;
      }

      int textLength = text.length();
      if (current >= textLength) {
        return null;
      }
      if (current < 0) {
        current = 0;
      }

      List<Rect> visibleTextLocations = new ArrayList<>();
      int visibleTextIndex = getTextLocationsInNode(node, text, current, visibleTextLocations);
      if (visibleTextIndex == INVALID_INDEX) {
        return null;
      }

      int start = current;
      int end = current;
      do {
        visibleTextIndex++;
        end++;
      } while (!isEndBoundary(visibleTextIndex, end, text, visibleTextLocations));

      return getRange(start, end);
    }

    /**
     * {@inheritDoc}
     *
     * <p>If the text location list for the visible text in the node is empty, the returned value is
     * also {@code null}.
     */
    @Nullable
    @Override
    public int[] preceding(int current) {
      CharSequence text = getIteratorText();
      if (TextUtils.isEmpty(text) || node == null) {
        return null;
      }

      int textLength = getIteratorText().length();
      if (current <= 0) {
        return null;
      }
      if (current > textLength) {
        current = textLength;
      }

      List<Rect> visibleTextLocations = new ArrayList<>();
      int visibleTextIndex = getTextLocationsInNode(node, text, current, visibleTextLocations);
      if (visibleTextIndex == INVALID_INDEX) {
        return null;
      }

      int start = current;
      int end = current;

      do {
        visibleTextIndex--;
        start--;
      } while (!isStartBoundary(visibleTextIndex, visibleTextLocations));

      return getRange(start, end);
    }

    /**
     * Gets the location of visible characters in the given node.
     *
     * @param node the node being queried.
     * @param text the accessibility text of {@code node}.
     * @param current the current index of the cursor in {@code} text.
     * @param visibleTextLocations a list of location for all visible characters
     * @return the index of the cursor in the visible text location list, or {@link
     *     LineTextSegmentIterator#INVALID_INDEX} if the visible text location list is empty
     */
    private static int getTextLocationsInNode(
        AccessibilityNodeInfoCompat node,
        CharSequence text,
        int current,
        List<Rect> visibleTextLocations) {
      int textLength = text.length();
      // Gets locations of visible characters which are before the cursor.
      if (current > 0) {
        List<Rect> preTextLocations =
            AccessibilityNodeInfoUtils.getTextLocations(node, text, 0, current);
        if (preTextLocations != null) {
          visibleTextLocations.addAll(preTextLocations);
        }
      }

      // AccessibilityNodeInfoUtils.getTextLocations() only returns location of characters which are
      // shown on the screen.
      int visibleTextIndex = visibleTextLocations.size();

      // Gets location of visible characters which are after the cursor
      if (current < textLength) {
        List<Rect> postTextLocations =
            AccessibilityNodeInfoUtils.getTextLocations(node, text, current, textLength);
        if (postTextLocations != null) {
          visibleTextLocations.addAll(postTextLocations);
        }
      }

      if (visibleTextLocations.isEmpty()) {
        return INVALID_INDEX;
      }

      return visibleTextIndex;
    }

    private static boolean isStartBoundary(int index, List<Rect> textLocations) {
      return index <= 0 || textLocations.get(index - 1).top < textLocations.get(index).top;
    }

    private static boolean isEndBoundary(
        int index, int visibleTextIndex, CharSequence text, List<Rect> textLocations) {
      int size = textLocations.size();
      if (index < 0) {
        return false;
      } else if (visibleTextIndex < text.length() && text.charAt(visibleTextIndex) == '\n') {
        return true;
      } else if (index >= size) {
        return true;
      } else if (index == size - 1) {
        return false;
      }
      return textLocations.get(index).top < textLocations.get(index + 1).top;
    }
  }

  private static class ParagraphTextSegmentIterator extends AbstractTextSegmentIterator {

    private static class LazyHolder {
      static final ParagraphTextSegmentIterator PARAGRAPH_TEXT_SEGMENT_ITERATOR =
          new ParagraphTextSegmentIterator();
    }

    private static ParagraphTextSegmentIterator getInstance() {
      return LazyHolder.PARAGRAPH_TEXT_SEGMENT_ITERATOR;
    }

    @Override
    @Nullable
    public int[] following(int offset) {
      final int textLength = getIteratorText().length();
      if (textLength <= 0) {
        return null;
      }
      if (offset >= textLength) {
        return null;
      }
      int start = offset;
      if (start < 0) {
        start = 0;
      }
      while (start < textLength
          && getIteratorText().charAt(start) == '\n'
          && !isStartBoundary(start)) {
        start++;
      }
      if (start >= textLength) {
        return null;
      }
      int end = start + 1;
      while (end < textLength && !isEndBoundary(end)) {
        end++;
      }
      return getRange(start, end);
    }

    @Override
    @Nullable
    public int[] preceding(int offset) {
      final int textLength = getIteratorText().length();
      if (textLength <= 0) {
        return null;
      }
      if (offset <= 0) {
        return null;
      }
      int end = offset;
      if (end > textLength) {
        end = textLength;
      }
      while (end > 0 && getIteratorText().charAt(end - 1) == '\n' && !isEndBoundary(end)) {
        end--;
      }
      if (end <= 0) {
        return null;
      }
      int start = end - 1;
      while (start > 0 && !isStartBoundary(start)) {
        start--;
      }
      return getRange(start, end);
    }

    private boolean isStartBoundary(int index) {
      return (getIteratorText().charAt(index) != '\n'
          && (index == 0 || getIteratorText().charAt(index - 1) == '\n'));
    }

    private boolean isEndBoundary(int index) {
      return (index > 0
          && getIteratorText().charAt(index - 1) != '\n'
          && (index == getIteratorText().length() || getIteratorText().charAt(index) == '\n'));
    }
  }
}
