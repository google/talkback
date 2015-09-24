/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.talkback.formatter;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import android.view.accessibility.AccessibilityNodeInfo;
import com.android.talkback.FeedbackItem;
import com.android.talkback.PasteHistory;
import com.android.talkback.R;
import com.android.talkback.SpeechCleanupUtils;
import com.android.talkback.SpeechController;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.Utterance;
import com.android.talkback.controller.TextCursorController;
import com.android.utils.AccessibilityEventUtils;
import com.android.utils.LogUtils;
import com.android.utils.SharedPreferencesUtils;
import com.android.utils.compat.provider.SettingsCompatUtils;

import java.util.List;

/**
 * This class contains custom formatters for presenting text edits.
 */
public final class TextFormatters {
    /**
     * Default pitch adjustment for text added event feedback.
     */
    private static final float DEFAULT_ADD_PITCH = 1.2f;

    /**
     * Default pitch adjustment for text removed event feedback.
     */
    private static final float DEFAULT_REMOVE_PITCH = 1.2f;

    /**
     * Default rate adjustment for text event feedback.
     */
    private static final float DEFAULT_RATE = 1.0f;

    /**
     * Minimum delay between change and selection events.
     */
    private static final long SELECTION_DELAY = 150;

    /**
     * Minimum delay between change events without an intervening selection.
     */
    private static final long CHANGED_DELAY = 150;

    /**
     * Minimum delay between selection and movement at granularity events that could reflect
     * the same cursor movement information.
     */
    private static final long CURSOR_MOVEMENT_EVENTS_DELAY = 150;

    private static final int VERBOSE_UTTERANCE_THRESHOLD_CHARACTERS = 50;

    /**
     * Event time of the most recently processed change event.
     */
    private static long sChangedTimestamp = -1;

    /**
     * Package name of the most recently processed change event.
     */
    private static CharSequence sChangedPackage = null;

    /**
     * The number of automatic selection events we're expecting to receive as a
     * result of observed changed events. If this is > 0 and the selection delay
     * has not elapsed, drop both selection and change events.
     */
    private static int sAwaitingSelectionCount = 0;

    private TextFormatters() {
        // Not publicly instantiable.
    }

    /**
     * Formatter that returns an utterance to announce text replacement.
     */
    public static final class ChangedTextFormatter
            implements EventSpeechRule.AccessibilityEventFormatter {
        // These must be synchronized with @array/pref_keyboard_echo_values
        // and @array/pref_keyboard_echo_entries in values/donottranslate.xml.
        private static final int PREF_ECHO_ALWAYS = 0;
        private static final int PREF_ECHO_SOFTKEYS = 1;
        private static final int PREF_ECHO_NEVER = 2;

        private static final int REJECTED = 0;
        private static final int REMOVED = 1;
        private static final int REPLACED = 2;
        private static final int ADDED = 3;

        @Override
        public boolean format(AccessibilityEvent event, TalkBackService context,
                              Utterance utterance) {
            final long timestamp = event.getEventTime();

            // Drop change event if we're still waiting for a select event and
            // the change occurred too soon after the previous change.
            if (sAwaitingSelectionCount > 0) {
                final boolean hasDelayElapsed =
                        ((event.getEventTime() - sChangedTimestamp) >= CHANGED_DELAY);
                final boolean hasPackageChanged =
                        !TextUtils.equals(event.getPackageName(), sChangedPackage);

                // If the state is still consistent, update the count and drop
                // the event except when running on locales that don't support
                // text replacement due to character combination complexity.
                if (!hasDelayElapsed && !hasPackageChanged
                        && context.getResources().getBoolean(R.bool.supports_text_replacement)) {
                    sAwaitingSelectionCount++;
                    sChangedTimestamp = timestamp;
                    return false;
                }

                // The state became inconsistent, so reset the counter.
                sAwaitingSelectionCount = 0;
            }

            final int changeType = formatInternal(event, context, utterance);

            // Text changes should use a different voice from labels.
            final Bundle params = new Bundle();
            params.putFloat(SpeechController.SpeechParam.RATE, DEFAULT_RATE);
            utterance.getMetadata().putBundle(Utterance.KEY_METADATA_SPEECH_PARAMS, params);
            utterance.getMetadata().putInt(Utterance.KEY_UTTERANCE_GROUP,
                    SpeechController.UTTERANCE_GROUP_TEXT_SELECTION);
            utterance.addSpokenFlag(
                    FeedbackItem.FLAG_CLEAR_QUEUED_UTTERANCES_WITH_SAME_UTTERANCE_GROUP);
            utterance.addSpokenFlag(
                    FeedbackItem.FLAG_INTERRUPT_CURRENT_UTTERANCE_WITH_SAME_UTTERANCE_GROUP);
            if (!isVerboseUtterance(utterance)) {
                utterance.getMetadata().putInt(Utterance.KEY_METADATA_QUEUING,
                        SpeechController.QUEUE_MODE_UNINTERRUPTIBLE);
            }

            switch (changeType) {
                case ADDED:
                case REPLACED:
                    notifyMaxLengthReached(event, context, utterance);
                    notifyError(event, context, utterance);
                    params.putFloat(SpeechController.SpeechParam.PITCH, DEFAULT_ADD_PITCH);
                    // No auditory feedback for adding text.
                    break;
                case REMOVED:
                    notifyError(event, context, utterance);
                    params.putFloat(SpeechController.SpeechParam.PITCH, DEFAULT_REMOVE_PITCH);
                    // No auditory feedback for removing text.
                    break;
                case REJECTED:
                    return false;
            }

            sAwaitingSelectionCount = 1;
            sChangedTimestamp = timestamp;
            sChangedPackage = event.getPackageName();

            return shouldEchoKeyboard(context, changeType);
        }

        private void notifyMaxLengthReached(AccessibilityEvent event,
                                            TalkBackService context, Utterance utterance) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Check if entered text reached to maximum length
                final AccessibilityNodeInfo source = event.getSource();
                final CharSequence eventText = getEventText(event);
                if (source != null
                        && eventText != null
                        && eventText.length() == source.getMaxTextLength()) {
                    utterance.addSpoken(context.getString(R.string.value_text_max_length));
                }
            }
        }

        private void notifyError(AccessibilityEvent event,
                                 TalkBackService context, Utterance utterance) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                final AccessibilityNodeInfo source = event.getSource();
                if(source != null && !TextUtils.isEmpty(source.getError())) {
                    utterance.addSpoken(
                            context.getString(R.string.template_text_error,
                                    source.getError().toString()));
                }
            }
        }

        private boolean shouldEchoKeyboard(Context context, int changeType) {
            // Always echo text removal events.
            if (changeType == REMOVED) {
                return true;
            }

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            final Resources res = context.getResources();
            final int keyboardPref = SharedPreferencesUtils.getIntFromStringPref(prefs, res,
                    R.string.pref_keyboard_echo_key, R.string.pref_keyboard_echo_default);

            switch (keyboardPref) {
                case PREF_ECHO_ALWAYS:
                    return true;
                case PREF_ECHO_SOFTKEYS:
                    final Configuration config = res.getConfiguration();
                    return (config.keyboard == Configuration.KEYBOARD_NOKEYS) ||
                            (config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES);
                case PREF_ECHO_NEVER:
                    return false;
                default:
                    LogUtils.log(this, Log.ERROR, "Invalid keyboard echo preference value: %d",
                            keyboardPref);
                    return false;
            }
        }

        private int formatInternal(AccessibilityEvent event, TalkBackService context,
                                   Utterance utterance) {
            if (event.isPassword() && !shouldSpeakPasswords(context)) {
                return formatPassword(event, context, utterance);
            }

            if (!passesSanityCheck(event)) {
                LogUtils.log(this, Log.ERROR, "Inconsistent text change event detected");
                return REJECTED;
            }

            // If multi-character text was cleared, stop now.
            final boolean wasCleared = event.getRemovedCount() > 1
                    && event.getAddedCount() == 0
                    && event.getBeforeText().length() == event.getRemovedCount();
            if (wasCleared) {
                utterance.addSpoken(context.getString(R.string.value_text_cleared));
                return REMOVED;
            }

            CharSequence removedText = getRemovedText(event);
            CharSequence addedText = getAddedText(event);

            // Never say "replaced Hello with Hello".
            if (TextUtils.equals(addedText, removedText)) {
                LogUtils.log(this, Log.DEBUG, "Drop event, nothing changed");
                return REJECTED;
            }

            // Abort if either text is null (indicates an error).
            if ((removedText == null) || (addedText == null)) {
                LogUtils.log(this, Log.DEBUG, "Drop event, either added or removed was null");
                return REJECTED;
            }

            final int removedLength = removedText.length();
            final int addedLength = addedText.length();

            // Translate partial replacement into addition / deletion.
            if (removedLength > addedLength) {
                if (TextUtils.regionMatches(removedText, 0, addedText, 0, addedLength)) {
                    removedText = removedText.subSequence(addedLength, removedLength);
                    addedText = "";
                }
            } else if (addedLength > removedLength) {
                if (TextUtils.regionMatches(removedText, 0, addedText, 0, removedLength)) {
                    removedText = "";
                    addedText = addedText.subSequence(removedLength, addedLength);
                }
            }

            // Apply any speech clean up rules. Usually this means changing "A"
            // to "capital A" or "[" to "left bracket".
            final CharSequence cleanRemovedText = SpeechCleanupUtils.cleanUp(context, removedText);
            final CharSequence cleanAddedText = SpeechCleanupUtils.cleanUp(context, addedText);

            if (!TextUtils.isEmpty(cleanAddedText)) {
                // Text was added. This includes replacement.
                //noinspection StatementWithEmptyBody
                if (appendLastWordIfNeeded(event, utterance)) {
                    // Do nothing.
                } else if (TextUtils.isEmpty(cleanRemovedText)
                        || TextUtils.equals(cleanAddedText, cleanRemovedText)) {
                    utterance.addSpoken(cleanAddedText);
                } else if (!(context.getResources().getBoolean(R.bool.supports_text_replacement))) {
                    // The method of character substitution in some languages is
                    // identical to text replacement events. As such, we only
                    // speak the added text if the device locale matches one of
                    // these languages.
                    utterance.addSpoken(cleanAddedText);
                } else {
                    String replacedText = context.getString(
                            R.string.template_text_replaced, cleanAddedText, cleanRemovedText);
                    utterance.addSpoken(replacedText);

                    // If this text change event probably wasn't the result of a
                    // paste action, spell the added text aloud.
                    if (!PasteHistory.getInstance().hasActionAtTime(event.getEventTime())) {
                        appendSpellingToUtterance(context, utterance, addedText);
                    }

                    return REPLACED;
                }
                return ADDED;
            }

            if (!TextUtils.isEmpty(cleanRemovedText)) {
                // Text was only removed.
                utterance.addSpoken(context.getString(R.string.template_text_removed,
                        cleanRemovedText));
                return REMOVED;
            }

            LogUtils.log(this, Log.DEBUG, "Drop event, cleaned up text was empty");
            return REJECTED;
        }

        private boolean appendLastWordIfNeeded(AccessibilityEvent event, Utterance utterance) {
            final CharSequence text = getEventText(event);
            final CharSequence addedText = getAddedText(event);
            final int fromIndex = event.getFromIndex();

            if (fromIndex > text.length()) {
                LogUtils.log(this, Log.WARN, "Received event with invalid fromIndex: %s", event);
                return false;
            }

            // Check if any visible text was added.
            int trimmedLength = TextUtils.getTrimmedLength(addedText);
            if (trimmedLength > 0) {
                return false;
            }

            final int breakIndex = getPrecedingWhitespace(text, fromIndex);
            final CharSequence word = text.subSequence(breakIndex, fromIndex);

            // Did the user just type a word?
            if (TextUtils.getTrimmedLength(word) == 0) {
                return false;
            }

            utterance.addSpoken(word);

            return true;
        }

        private static void appendSpellingToUtterance(Context context, Utterance utterance,
                                                      CharSequence word) {
            // Only spell words that consist of multiple characters.
            if (word.length() <= 1) {
                return;
            }

            for (int i = 0; i < word.length(); i++) {
                final CharSequence character = Character.toString(word.charAt(i));
                final CharSequence cleaned = SpeechCleanupUtils.cleanUp(context, character);
                utterance.addSpoken(cleaned);
            }
        }

        private static int getPrecedingWhitespace(CharSequence text, int fromIndex) {
            for (int i = (fromIndex - 1); i > 0; i--) {
                if (Character.isWhitespace(text.charAt(i))) {
                    return i;
                }
            }

            return 0;
        }

        /**
         * Checks whether the event's reported properties match its actual
         * properties, e.g. does the added count minus the removed count reflect
         * the actual change in length between the current and previous text
         * contents.
         *
         * @param event The text changed event to validate.
         * @return {@code true} if the event properties are valid.
         */
        private boolean passesSanityCheck(AccessibilityEvent event) {
            final CharSequence afterText = getEventText(event);
            final CharSequence beforeText = event.getBeforeText();

            // Special case for deleting all the text in an EditText with a
            // hint, since the event text will contain the hint rather than an
            // empty string.
            if ((event.getAddedCount() == 0) && (event.getRemovedCount() == beforeText.length())) {
                return true;
            }

            if (afterText == null || beforeText == null) {
                return false;
            }

            final int diff = (event.getAddedCount() - event.getRemovedCount());

            return ((beforeText.length() + diff) == afterText.length());
        }

        /**
         * Attempts to extract the text that was added during an event.
         *
         * @param event The source event.
         * @return The added text, or {@code null} on error.
         */
        private CharSequence getAddedText(AccessibilityEvent event) {
            final List<CharSequence> textList = event.getText();
            //noinspection ConstantConditions
            if (textList == null || textList.size() > 1) {
                LogUtils.log(this, Log.WARN, "getAddedText: Text list was null or bad size");
                return null;
            }

            // If the text was empty, the list will be empty. See the
            // implementation for TextView.onPopulateAccessibilityEvent().
            if (textList.size() == 0) {
                return "";
            }

            final CharSequence text = textList.get(0);
            if (text == null) {
                LogUtils.log(this, Log.WARN, "getAddedText: First text entry was null");
                return null;
            }

            final int addedBegIndex = event.getFromIndex();
            final int addedEndIndex = addedBegIndex + event.getAddedCount();
            if (areInvalidIndices(text, addedBegIndex, addedEndIndex)) {
                LogUtils.log(this, Log.WARN, "getAddedText: Invalid indices (%d,%d) for \"%s\"",
                        addedBegIndex, addedEndIndex, text);
                return "";
            }

            return text.subSequence(addedBegIndex, addedEndIndex);
        }

        /**
         * Attempts to extract the text that was removed during an event.
         *
         * @param event The source event.
         * @return The removed text, or {@code null} on error.
         */
        private CharSequence getRemovedText(AccessibilityEvent event) {
            final CharSequence beforeText = event.getBeforeText();
            if (beforeText == null) {
                return null;
            }

            final int beforeBegIndex = event.getFromIndex();
            final int beforeEndIndex = beforeBegIndex + event.getRemovedCount();
            if (areInvalidIndices(beforeText, beforeBegIndex, beforeEndIndex)) {
                return "";
            }

            return beforeText.subSequence(beforeBegIndex, beforeEndIndex);
        }

        /**
         * Formats "secure" password feedback from event text.
         *
         * @param event     The source event.
         * @param context   The application context.
         * @param utterance The utterance to populate.
         * @return {@code false} on error.
         */
        private int formatPassword(AccessibilityEvent event, Context context, Utterance utterance) {
            int removed = event.getRemovedCount();
            int added = event.getAddedCount();

            // there is bug that sometimes web edit fields send negative indexes. we need to check
            // if index is negative
            if ((added <= 0) && (removed <= 0)) {
                return REJECTED;
            } else if ((added == 1) && (removed <= 0)) {
                utterance.addSpoken(context.getString(R.string.symbol_bullet));
                return ADDED;
            } else if ((added <= 0) && (removed == 1)) {
                utterance.addSpoken(context.getString(
                        R.string.template_text_removed, context.getString(R.string.symbol_bullet)));
                return REMOVED;
            } else {
                utterance.addSpoken(context.getString(R.string.template_replaced_characters,
                        removed, added));
                return REPLACED;
            }
        }
    }

    /**
     * Formatter that returns an utterance to announce text selection.
     */
    public static final class SelectedTextFormatter
            implements EventSpeechRule.AccessibilityEventFormatter {

        private AccessibilityEvent mLastProcessedEvent;

        @Override
        public boolean format(AccessibilityEvent event, TalkBackService context,
                              Utterance utterance) {
            boolean result = formatInternal(event, context, utterance);
            utterance.getMetadata().putInt(Utterance.KEY_UTTERANCE_GROUP,
                    SpeechController.UTTERANCE_GROUP_TEXT_SELECTION);
            utterance.addSpokenFlag(
                    FeedbackItem.FLAG_CLEAR_QUEUED_UTTERANCES_WITH_SAME_UTTERANCE_GROUP);
            utterance.addSpokenFlag(
                    FeedbackItem.FLAG_INTERRUPT_CURRENT_UTTERANCE_WITH_SAME_UTTERANCE_GROUP);
            if (!isVerboseUtterance(utterance)) {
                utterance.getMetadata().putInt(Utterance.KEY_METADATA_QUEUING,
                        SpeechController.QUEUE_MODE_UNINTERRUPTIBLE);
            }

            return result;
        }

        private boolean formatInternal(AccessibilityEvent event, TalkBackService context,
                                       Utterance utterance) {
            if (isProcessedEvent(event) || shouldDropEvent(event)) {
                return false;
            }

            final boolean isGranularTraversal = (event.getEventType() ==
                    AccessibilityEventCompat.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY);
            final CharSequence text;

            if (isGranularTraversal) {
                // Use the description (if present) or aggregate event text.
                text = AccessibilityEventUtils.getEventTextOrDescription(event);
            } else {
                // Only use the first item from getText().
                text = getEventText(event);
            }

            final int count = event.getItemCount();
            if (event.isPassword() && !shouldSpeakPasswords(context)) {
                return formatPassword(event, context, utterance);
            }

            // Don't provide selection feedback when there's no text. We have to
            // check the item count separately to avoid speaking hint text,
            // which always has an item count of zero even though the event text
            // is not empty.
            if (TextUtils.isEmpty(text) || (count == 0)) {
                return false;
            }

            int endIndex = event.getToIndex();
            int begIndex = event.getFromIndex();

            // if it is TYPE_VIEW_TEXT_SELECTION_CHANGED event that represent cursor movement
            // without selection get begining and end indexes from TextCursorController
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED &&
                    endIndex == begIndex) {
                TalkBackService service = TalkBackService.getInstance();
                if (service == null) {
                    return false;
                }

                TextCursorController controller = service.getTextCursorController();
                begIndex = controller.getPreviousCursorPosition();
                endIndex = controller.getCurrentCursorPosition();
            }

            if (begIndex > endIndex) {
                int temp = begIndex;
                begIndex = endIndex;
                endIndex = temp;
            }

            if (areInvalidIndices(text, begIndex, endIndex)) {
                return false;
            }

            processEvent(event, utterance, SpeechCleanupUtils.cleanUp(context,
                    text.subSequence(begIndex, endIndex)));
            return true;
        }

        private boolean isProcessedEvent(AccessibilityEvent event) {
            if (mLastProcessedEvent == null) {
                return false;
            }

            if (event.getEventTime() - mLastProcessedEvent.getEventTime() >
                    CURSOR_MOVEMENT_EVENTS_DELAY) {
                mLastProcessedEvent.recycle();
                mLastProcessedEvent = null;
                return false;
            }

            //noinspection SimplifiableIfStatement
            if (event.getEventType() == mLastProcessedEvent.getEventType()) {
                // if events have the same type they are results of different actions
                return false;
            }

            return (event.getToIndex() == mLastProcessedEvent.getToIndex()) ||
                    (event.getFromIndex() == mLastProcessedEvent.getFromIndex());
        }

        private void processEvent(AccessibilityEvent event, Utterance utterance,
                                  CharSequence text) {
            utterance.addSpoken(text);
            if (mLastProcessedEvent != null) {
                mLastProcessedEvent.recycle();
            }

            mLastProcessedEvent = AccessibilityEvent.obtain(event);
        }

        private boolean isCharacterTraversal(AccessibilityEvent event) {
            if (event.getEventType() ==
                    AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY &&
                    event.getMovementGranularity()
                            == AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER) {
                return true;
            }

            TalkBackService service = TalkBackService.getInstance();
            if (service == null) {
                return false;
            }

            TextCursorController textCursorController = service.getTextCursorController();
            int currentIndex = textCursorController.getCurrentCursorPosition();
            int previousIndex = textCursorController.getPreviousCursorPosition();
            //noinspection SimplifiableIfStatement,RedundantIfStatement
            if (currentIndex != TextCursorController.NO_POSITION &&
                    previousIndex != TextCursorController.NO_POSITION &&
                    currentIndex == event.getToIndex() &&
                    Math.abs(previousIndex - currentIndex) == 1) {
                return true;
            }

            return false;
        }

        /**
         * Returns {@code true} if the specified event is a selection event and
         * should be dropped without providing feedback. Always returns
         * {@code false} for non-selection events.
         */
        private boolean shouldDropEvent(AccessibilityEvent event) {
            // Only operate on selection events. Never drop granular movement
            // events or other event types.
            final int eventType = event.getEventType();
            if (eventType != AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
                return false;
            }

            // Drop selected events until we've matched the number of changed
            // events. This prevents TalkBack from speaking automatic cursor
            // movement events that result from typing.
            if (sAwaitingSelectionCount > 0) {
                final boolean hasDelayElapsed =
                        ((event.getEventTime() - sChangedTimestamp) >= SELECTION_DELAY);
                final boolean hasPackageChanged =
                        !TextUtils.equals(event.getPackageName(), sChangedPackage);

                // If the state is still consistent, update the count and drop
                // the event.
                if (!hasDelayElapsed && !hasPackageChanged) {
                    sAwaitingSelectionCount--;
                    return true;
                }

                // The state became inconsistent, so reset the counter.
                sAwaitingSelectionCount = 0;
            }

            // Drop selection events from views that don't have input focus.
            final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
            final AccessibilityNodeInfoCompat source = record.getSource();
            if ((source != null) && !source.isFocused()) {
                LogUtils.log(this, Log.VERBOSE, "Dropped selection event from non-focused field");
                return true;
            }

            return false;
        }

        /**
         * Formats "secure" password feedback from event text.
         *
         * @param event     The source event.
         * @param context   The application context.
         * @param utterance The utterance to populate.
         * @return {@code false} on error.
         */
        private boolean formatPassword(AccessibilityEvent event, Context context,
                                       Utterance utterance) {
            final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
            final int fromIndex = event.getFromIndex();
            final int toIndex = record.getToIndex();

            if (toIndex <= fromIndex) {
                return false;
            }

            final CharSequence formattedText = context.getString(
                    R.string.template_password_selected, fromIndex, toIndex);
            utterance.addSpoken(formattedText);
            return true;
        }
    }

    /**
     * Returns whether a set of indices are valid for a given
     * {@link CharSequence}.
     *
     * @param text  The sequence to examine.
     * @param begin The beginning index.
     * @param end   The end index.
     * @return {@code true} if the indices are valid.
     */
    private static boolean areInvalidIndices(CharSequence text, int begin, int end) {
        return (begin < 0) || (end > text.length()) || (begin >= end);
    }

    /**
     * Returns the text for an event sent from a {@link android.widget.TextView}
     * widget.
     *
     * @param event The source event.
     * @return The widget text, or {@code null}.
     */
    private static CharSequence getEventText(AccessibilityEvent event) {
        final List<CharSequence> eventText = event.getText();

        if (eventText.isEmpty()) {
            return "";
        }

        return eventText.get(0);
    }

    private static boolean shouldSpeakPasswords(TalkBackService service) {
        if (service == null) {
            return false;
        }

        return SettingsCompatUtils.SecureCompatUtils.shouldSpeakPasswords(service);
    }

    private static boolean isVerboseUtterance(Utterance utterance) {
        List<CharSequence> texts = utterance.getSpoken();
        int count = 0;
        int textCount = texts.size();
        for (int i = 0; i < textCount; i++) {
            CharSequence text = texts.get(i);
            if (!TextUtils.isEmpty(text)) {
                count += text.length();
            }
        }

        return count > VERBOSE_UTTERANCE_THRESHOLD_CHARACTERS;
    }
}
