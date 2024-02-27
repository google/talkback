/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.android.accessibility.talkback.actor;

import android.content.Context;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.SuggestionSpan;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import org.checkerframework.checker.nullness.qual.Nullable;

/** This class supports to navigation over content with SuggestionSpans. */
public class TypoNavigator {
  private static final String TAG = "TypoNavigator";
  private final Context context;
  private Pipeline.FeedbackReturner pipeline;
  private final TextEditActor editor;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  public TypoNavigator(
      Context context, TextEditActor editor, AccessibilityFocusMonitor accessibilityFocusMonitor) {
    this.context = context;
    this.editor = editor;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  /**
   * If any SuggestionSpan before/after the cursor, this method will move the nearest position with
   * such span.
   *
   * @param eventId EventId for performance tracking.
   * @param isNext Direction (forward/backward) of navigation.
   * @return true when the position is found.
   */
  public boolean navigate(EventId eventId, boolean isNext, boolean useInputFocusIfEmpty) {
    boolean result = false;
    CharSequence currentSpanned = null;
    @Nullable AccessibilityNodeInfoCompat node;
    node =
        accessibilityFocusMonitor.getNodeForEditingActions(
            accessibilityFocusMonitor.getAccessibilityFocus(useInputFocusIfEmpty));
    if (node == null) {
      feedbackNoTypo(eventId);
      return false;
    }
    CharSequence text = node.getText();
    if (TextUtils.isEmpty(text)) {
      feedbackNoTypo(eventId);
      return false;
    }
    if (text instanceof Spannable) {
      int cursorPosition = node.getTextSelectionStart();
      Spanned spanned = (Spanned) node.getText();
      SuggestionSpan[] spansAfterCursor =
          spanned.getSpans(cursorPosition, spanned.length(), SuggestionSpan.class);
      SuggestionSpan[] spansBeforeCursor =
          spanned.getSpans(0, cursorPosition, SuggestionSpan.class);
      if (spansBeforeCursor != null
          && spansBeforeCursor.length == 0
          && spansAfterCursor != null
          && spansAfterCursor.length == 0) {
        feedbackNoTypo(eventId);
        return false;
      }
      SuggestionSpan[] spans = isNext ? spansAfterCursor : spansBeforeCursor;
      if (spans != null) {
        int recordIndex = isNext ? Integer.MAX_VALUE : -1;
        SuggestionSpan targetSpan = null;
        for (SuggestionSpan span : spans) {
          int start = spanned.getSpanStart(span);
          if (start == -1) {
            continue;
          }
          if (isNext) {
            if (cursorPosition < start && start < recordIndex) {
              recordIndex = start;
              targetSpan = span;
            }
          } else {
            if (cursorPosition > start && start > recordIndex) {
              recordIndex = start;
              targetSpan = span;
            }
          }
        }
        if (targetSpan != null) {
          result = feedbackTypo(node, eventId, targetSpan);
        }
        if (!result) {
          SuggestionSpan[] currentSpans =
              spanned.getSpans(cursorPosition, cursorPosition, SuggestionSpan.class);
          if (currentSpans.length > 0) {
            int start = spanned.getSpanStart(currentSpans[0]);
            int end = spanned.getSpanEnd(currentSpans[0]);
            if (start <= cursorPosition && cursorPosition <= end) {
              currentSpanned = spanned.subSequence(start, end);
            }
          }
        }
      }
    }
    if (!result) {
      feedbackNoTypo(eventId, currentSpanned, isNext);
    }
    return result;
  }

  private void feedbackNoTypo(EventId eventId) {
    pipeline.returnFeedback(
        eventId,
        getSpeechFeedbackBuilder(context.getString(R.string.hint_no_typo_found), R.raw.complete));
  }

  private void feedbackNoTypo(EventId eventId, @Nullable CharSequence currentTypo, boolean isNext) {
    if (TextUtils.isEmpty(currentTypo)) {
      currentTypo = "";
    }
    String announcement =
        context.getString(
            isNext ? R.string.hint_no_next_typo_found : R.string.hint_no_previous_typo_found,
            currentTypo);
    pipeline.returnFeedback(eventId, getSpeechFeedbackBuilder(announcement, R.raw.complete));
  }

  private boolean feedbackTypo(
      AccessibilityNodeInfoCompat node, EventId eventId, SuggestionSpan targetSpan) {
    Spanned spanned = (Spanned) node.getText();
    int cursor = spanned.getSpanStart(targetSpan);
    int end = spanned.getSpanEnd(targetSpan);
    if (cursor != Integer.MAX_VALUE && cursor != -1 && end != -1) {
      boolean result =
          (cursor == node.getTextSelectionStart()) || editor.moveCursor(node, cursor, eventId);
      if (result) {
        pipeline.returnFeedback(
            eventId, getSpeechFeedbackBuilder(spanned.subSequence(cursor, end), R.raw.typo));
      }
      return result;
    }
    return false;
  }

  private Feedback.Part.Builder getSpeechFeedbackBuilder(CharSequence speech, int soundRes) {
    return Feedback.speech(
            speech,
            SpeakOptions.create()
                .setQueueMode(
                    SpeechController.QUEUE_MODE_INTERRUPT_AND_UNINTERRUPTIBLE_BY_NEW_SPEECH)
                .setFlags(
                    FeedbackItem.FLAG_NO_HISTORY
                        | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE
                        | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE
                        | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE
                        | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_PHONE_CALL_ACTIVE
                        | FeedbackItem.FLAG_SKIP_DUPLICATE))
        .sound(soundRes)
        .vibration(R.array.typo_pattern);
  }
}
