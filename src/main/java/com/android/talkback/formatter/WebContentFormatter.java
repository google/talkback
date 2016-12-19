/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.Xml;
import android.view.accessibility.AccessibilityEvent;

import com.android.talkback.R;

import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.Utterance;
import com.android.utils.AccessibilityEventUtils;
import com.android.utils.WebContentHandler;
import org.xml.sax.SAXException;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Formatter for web content. This is used for the built-in, non-Chrome-based WebView in Jelly Bean.
 * The non-Chrome-based WebView provides accessibility information that contains raw HTML
 * code; this formatter extracts the inner text from the HTML elements and provides it as
 * feedback to the user.
 *
 * Note: this class does nothing in current releases of Chrome, nor does it do anything in
 * older Chrome-based WebViews that used ChromeVox. Therefore it is safe to ignore this formatter
 * on KitKat or above.
 */
@SuppressWarnings("unused")
public final class WebContentFormatter implements EventSpeechRule.AccessibilityEventFormatter {
    private static final int ACTION_SET_CURRENT_AXIS = 0;
    private static final int ACTION_TRAVERSE_CURRENT_AXIS = 1;
    private static final int ACTION_TRAVERSE_GIVEN_AXIS = 2;
    private static final int ACTION_PERFORM_AXIS_TRANSITION = 3;
    private static final int ACTION_TRAVERSE_DEFAULT_WEB_VIEW_BEHAVIOR_AXIS = 4;

    private static String[] sAxisNames;

    /**
     * A template to apply to markup before sending it to an XML parser.
     */
    private static final String XML_TEMPLATE =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><div>%s</div>";

    /**
     * Regular expression that matches all HTML tags.
     */
    private final Pattern mStripMarkupPattern = Pattern.compile("<(.)+?>");

    /**
     * Regular expression that matches all entity codes.
     */
    private final Pattern mStripEntitiesPattern = Pattern.compile("&(.)+?;");

    /**
     * Regular expression that matches all div or span tags.
     */
    private final Pattern mStripDivSpanPattern = Pattern.compile("</?(div|span).*?>",
            Pattern.CASE_INSENSITIVE);

    /**
     * Regular expression that matches some common singleton tags.
     */
    private final Pattern mCloseTagPattern = Pattern.compile("(<(img|input|br).+?)>",
            Pattern.CASE_INSENSITIVE);

    /**
     * A handler for processing HTML and generating output for speaking.
     */
    private WebContentHandler mHtmlHandler = null;

    private final Action mTempAction = new Action();

    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        // for now ... lookup and announce axis transitions
        final CharSequence contentDescription = event.getContentDescription();
        if (!TextUtils.isEmpty(contentDescription)) {
            final Action action = mTempAction;
            action.init(contentDescription.toString());

            final int actionCode = mTempAction.mActionCode;
            if (actionCode == ACTION_PERFORM_AXIS_TRANSITION) {
                final String axisAnnouncement = getAxisAnnouncement(
                        context, action.mSecondArgument);
                utterance.addSpoken(axisAnnouncement);
                // axisAnnouncement is chosen from an array and always not empty,
                // so always return true here.
                return true;
            }
        }

        // for now ... disregard content description
        final String markup =
                AccessibilityEventUtils.getEventAggregateText(event).toString();
        final String noTags = mStripMarkupPattern.matcher(markup).replaceAll("");
        final String cleaned = cleanMarkup(markup);

        if (mHtmlHandler == null) {
            final Map<String, String> htmlInputMap =
                    loadMapFromStringArrays(context, R.array.html_input_to_desc_keys,
                            R.array.html_input_to_desc_values);
            final Map<String, String> htmlRoleMap =
                    loadMapFromStringArrays(context, R.array.html_role_to_desc_keys,
                            R.array.html_role_to_desc_values);
            final Map<String, String> htmlTagMap =
                    loadMapFromStringArrays(context, R.array.html_tag_to_desc_keys,
                            R.array.html_tag_to_desc_values);

            mHtmlHandler = new WebContentHandler(htmlInputMap, htmlRoleMap, htmlTagMap);
        }

        try {
            Xml.parse(cleaned, mHtmlHandler);
            final String speech = mHtmlHandler.getOutput();
            utterance.addSpoken(speech);
        } catch (final SAXException e) {
            e.printStackTrace();
            utterance.addSpoken(noTags);
        }

        return !utterance.getSpoken().isEmpty();
    }

    /**
     * Process HTML to remove markup that can't be handled by the SAX parser.
     *
     * @param markup Input HTML generated by system.
     * @return A string of cleaned HTML.
     */
    public String cleanMarkup(String markup) {
        final String noDivOrSpan = mStripDivSpanPattern.matcher(markup).replaceAll("");
        final String noEntities = mStripEntitiesPattern.matcher(noDivOrSpan).replaceAll(" ");
        final String tagsClosed = mCloseTagPattern.matcher(noEntities).replaceAll("$1/>");

        return String.format(XML_TEMPLATE, tagsClosed);
    }

    /**
     * Gets an announcement for a navigation axis given its code.
     *
     * @param context Context for loading resources.
     * @param axisCode The code the the axis.
     * @return The axis announcement.
     */
    private String getAxisAnnouncement(Context context, int axisCode) {
        if (sAxisNames == null) {
            sAxisNames =
                    new String[] {
                            context.getString(R.string.axis_character),
                            context.getString(R.string.axis_word),
                            context.getString(R.string.axis_sentence),
                            context.getString(R.string.axis_heading),
                            context.getString(R.string.axis_sibling),
                            context.getString(R.string.axis_parent_first_child),
                            context.getString(R.string.axis_document),
                            context.getString(R.string.axis_default_web_view_behavior)
                    };
        }

        return sAxisNames[axisCode];
    }

    /**
     * Loads a map of key strings to value strings from array resources.
     *
     * @param context The parent context.
     * @param keysResource A resource identifier for the array of key strings.
     * @param valuesResource A resource identifier for the array of value
     *            strings.
     * @return A map of keys to values.
     */
    private static Map<String, String> loadMapFromStringArrays(Context context, int keysResource,
            int valuesResource) {
        final Resources res = context.getResources();
        final String[] keys = res.getStringArray(keysResource);
        final String[] values = res.getStringArray(valuesResource);

        if (keys.length != values.length) {
            throw new IllegalArgumentException("Array size mismatch");
        }

        final Map<String, String> map = new HashMap<>();

        for (int i = 0; i < keys.length; i++) {
            map.put(keys[i], values[i]);
        }

        return map;
    }

    /**
     * Represents an action.
     */
    private class Action {
        private static final int ACTION_OFFSET = 24;
        private static final int ACTION_MASK = 0xFF000000;

        private static final int FIRST_ARGUMENT_OFFSET = 16;
        private static final int FIRST_ARGUMENT_MASK = 0x00FF0000;

        private static final int SECOND_ARGUMENT_OFFSET = 8;
        private static final int SECOND_ARGUMENT_MASK = 0x0000FF00;

        private static final int THIRD_ARGUMENT_MASK = 0x000000FF;

        private int mActionCode;
        private int mFirstArgument;
        private int mSecondArgument;
        private int mThirdArgument;

        public void init(String encodedActionString) {
            int encodedAction;

            try {
                // hack
                encodedAction = Integer.decode("0x" + encodedActionString);
            } catch (final NumberFormatException nfe) {
                return;
            }

            mActionCode = (encodedAction & ACTION_MASK) >> ACTION_OFFSET;
            mFirstArgument = (encodedAction & FIRST_ARGUMENT_MASK) >> FIRST_ARGUMENT_OFFSET;
            mSecondArgument = (encodedAction & SECOND_ARGUMENT_MASK) >> SECOND_ARGUMENT_OFFSET;
            mThirdArgument = (encodedAction & THIRD_ARGUMENT_MASK);
        }
    }
}
