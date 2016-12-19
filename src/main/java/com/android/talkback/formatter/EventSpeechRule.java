/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.android.talkback.FeedbackItem;
import com.android.talkback.R;

import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.Utterance;
import com.android.talkback.speechrules.NodeSpeechRuleProcessor;
import com.android.utils.AccessibilityEventUtils;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.ClassLoadingCache;
import com.android.utils.LogUtils;
import com.android.utils.NodeUtils;
import com.android.utils.PackageManagerUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.MissingFormatArgumentException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class represents a speech rule for an {@link AccessibilityEvent}. The
 * rule has a filter which determines if the rule applies to a given event. If
 * the rule applies to an event the formatter for that rule is used to generate
 * a formatted utterance.
 */
public class EventSpeechRule {
    // Node names.
    private static final String NODE_NAME_METADATA = "metadata";
    private static final String NODE_NAME_FILTER = "filter";
    private static final String NODE_NAME_FORMATTER = "formatter";
    private static final String NODE_NAME_CUSTOM = "custom";

    // Property names.
    private static final String PROPERTY_EVENT_TYPE = "eventType";
    private static final String PROPERTY_PACKAGE_NAME = "packageName";
    private static final String PROPERTY_CLASS_NAME = "className";
    private static final String PROPERTY_CLASS_NAME_STRICT = "classNameStrict";
    private static final String PROPERTY_TEXT = "text";
    private static final String PROPERTY_BEFORE_TEXT = "beforeText";
    private static final String PROPERTY_CONTENT_DESCRIPTION = "contentDescription";
    private static final String PROPERTY_CONTENT_DESCRIPTION_OR_TEXT = "contentDescriptionOrText";
    private static final String PROPERTY_NODE_DESCRIPTION_OR_FALLBACK = "nodeDescriptionOrFallback";
    private static final String PROPERTY_EVENT_TIME = "eventTime";
    private static final String PROPERTY_ITEM_COUNT = "itemCount";
    private static final String PROPERTY_CURRENT_ITEM_INDEX = "currentItemIndex";
    private static final String PROPERTY_FROM_INDEX = "fromIndex";
    private static final String PROPERTY_TO_INDEX = "toIndex";
    private static final String PROPERTY_SCROLLABLE = "scrollable";
    private static final String PROPERTY_SCROLL_X = "scrollX";
    private static final String PROPERTY_SCROLL_Y = "scrollY";
    private static final String PROPERTY_RECORD_COUNT = "recordCount";
    private static final String PROPERTY_CHECKED = "checked";
    private static final String PROPERTY_ENABLED = "enabled";
    private static final String PROPERTY_FULL_SCREEN = "fullScreen";
    private static final String PROPERTY_PASSWORD = "password";
    private static final String PROPERTY_ADDED_COUNT = "addedCount";
    private static final String PROPERTY_REMOVED_COUNT = "removedCount";
    private static final String PROPERTY_QUEUING = "queuing";
    private static final String PROPERTY_EARCON = "earcon";
    private static final String PROPERTY_VIBRATION = "vibration";
    private static final String PROPERTY_CUSTOM_EARCON = "customEarcon";
    private static final String PROPERTY_CUSTOM_VIBRATION = "customVibration";
    private static final String PROPERTY_VERSION_CODE = "versionCode";
    private static final String PROPERTY_VERSION_NAME = "versionName";
    private static final String PROPERTY_PLATFORM_RELEASE = "platformRelease";
    private static final String PROPERTY_PLATFORM_SDK = "platformSdk";
    private static final String PROPERTY_SKIP_DUPLICATES = "skipDuplicates";

    // Property types.
    private static final int PROPERTY_TYPE_UNKNOWN = 0;
    private static final int PROPERTY_TYPE_BOOLEAN = 1;
    private static final int PROPERTY_TYPE_FLOAT = 2;
    private static final int PROPERTY_TYPE_INTEGER = 3;
    private static final int PROPERTY_TYPE_STRING = 4;

    /**
     * Constant used for storing all speech rules that either do not define a
     * filter package or have custom filters.
     */
    private static final String UNDEFINED_PACKAGE_NAME = "undefined_package_name";

    /** Regular expression pattern for resource identifiers. */
    private static final Pattern mResourceIdentifier = Pattern.compile("@([\\w\\.]+:)?\\w+/\\w+");

    /** Reusable builder to avoid object creation. */
    private static final SpannableStringBuilder sTempBuilder = new SpannableStringBuilder();

    /** Mapping from event type name to its type. */
    private static final HashMap<String, Integer> sEventTypeNameToValueMap = new HashMap<>();
    static {
        sEventTypeNameToValueMap.put("TYPE_VIEW_CLICKED", AccessibilityEvent.TYPE_VIEW_CLICKED);
        sEventTypeNameToValueMap.put("TYPE_VIEW_LONG_CLICKED",
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
        sEventTypeNameToValueMap.put("TYPE_VIEW_SELECTED", AccessibilityEvent.TYPE_VIEW_SELECTED);
        sEventTypeNameToValueMap.put("TYPE_VIEW_FOCUSED", AccessibilityEvent.TYPE_VIEW_FOCUSED);
        sEventTypeNameToValueMap.put("TYPE_VIEW_TEXT_CHANGED",
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
        sEventTypeNameToValueMap.put("TYPE_WINDOW_STATE_CHANGED",
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        sEventTypeNameToValueMap.put("TYPE_NOTIFICATION_STATE_CHANGED",
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
        sEventTypeNameToValueMap.put("TYPE_VIEW_HOVER_ENTER",
                AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER);
        sEventTypeNameToValueMap.put("TYPE_VIEW_HOVER_EXIT",
                AccessibilityEventCompat.TYPE_VIEW_HOVER_EXIT);
        sEventTypeNameToValueMap.put("TYPE_TOUCH_EXPLORATION_GESTURE_START",
                AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_START);
        sEventTypeNameToValueMap.put("TYPE_TOUCH_EXPLORATION_GESTURE_END",
                AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_END);
        sEventTypeNameToValueMap.put("TYPE_WINDOW_CONTENT_CHANGED",
                AccessibilityEventCompat.TYPE_WINDOW_CONTENT_CHANGED);
        sEventTypeNameToValueMap.put("TYPE_VIEW_SCROLLED",
                AccessibilityEventCompat.TYPE_VIEW_SCROLLED);
        sEventTypeNameToValueMap.put("TYPE_VIEW_TEXT_SELECTION_CHANGED",
                AccessibilityEventCompat.TYPE_VIEW_TEXT_SELECTION_CHANGED);
        sEventTypeNameToValueMap.put("TYPE_ANNOUNCEMENT",
                AccessibilityEventCompat.TYPE_ANNOUNCEMENT);
        sEventTypeNameToValueMap.put("TYPE_VIEW_ACCESSIBILITY_FOCUSED",
                AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
        sEventTypeNameToValueMap.put("TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED",
                AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
        sEventTypeNameToValueMap.put("TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY",
                AccessibilityEventCompat.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY);
        sEventTypeNameToValueMap.put("TYPE_TOUCH_INTERACTION_START",
                AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_START);
        sEventTypeNameToValueMap.put("TYPE_TOUCH_INTERACTION_END",
                AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_END);
        sEventTypeNameToValueMap.put("TYPE_GESTURE_DETECTION_START",
                AccessibilityEventCompat.TYPE_GESTURE_DETECTION_START);
        sEventTypeNameToValueMap.put("TYPE_GESTURE_DETECTION_END",
                AccessibilityEventCompat.TYPE_GESTURE_DETECTION_END);
    }

    /**
     * Mapping from queue mode names to queue modes.
     */
    private static final HashMap<String, Integer> sQueueModeNameToQueueModeMap = new HashMap<>();
    static {
        sQueueModeNameToQueueModeMap.put("INTERRUPT", 0);
        sQueueModeNameToQueueModeMap.put("QUEUE", 1);
        sQueueModeNameToQueueModeMap.put("UNINTERRUPTIBLE", 2);
    }

    /**
     * Flags to add to the utterance.
     */
    private int mSpokenFlags = 0;

    /**
     * Meta-data of how the utterance should be spoken. It is a key value
     * mapping to enable extending the meta-data specification.
     */
    private final Bundle mMetadata = new Bundle();

    /**
     * List of earcons resource identifiers that should be played with an
     * utterance.
     */
    private final List<Integer> mEarcons = new LinkedList<>();

    /**
     * List of vibration pattern resource identifiers that should be played with
     * an utterance.
     */
    private final List<Integer> mVibrationPatterns = new LinkedList<>();

    /**
     * List of resource identifiers for preference keys that correspond to
     * custom earcons that should be played with an utterance.
     */
    private final List<Integer> mCustomEarcons = new LinkedList<>();

    /**
     * List of resource identifiers for preference keys that correspond to
     * custom vibration patterns that should be played with an utterance.
     */
    private final List<Integer> mCustomVibrations = new LinkedList<>();

    /** Mapping from property name to property matcher. */
    private final LinkedHashMap<String, PropertyMatcher> mPropertyMatchers = new LinkedHashMap<>();

    /** Filter for matching an event. */
    private final AccessibilityEventFilter mFilter;

    /** Formatter for building an utterance. */
    private final AccessibilityEventFormatter mFormatter;

    /** The index of this rule within the global rule list. */
    private final int mRuleIndex;

    /** The context in which this speech rule operates. */
    private final TalkBackService mContext;

    /** The package targeted by this rule. */
    private String mPackageName = UNDEFINED_PACKAGE_NAME;

    /**
     * The DOM node that defines this speech rule, or {@code null} if the node
     * is no longer needed.
     */
    private Node mNode;

    /** Lazily populated XML representation of this node. */
    private String mCachedXmlString;

    /**
     * Creates a new speech rule that loads resources form the given <code>context
     * </code> and classes from the APK specified by the <code>publicSourceDird</code>. The rule
     * is defined in the <code>speechStrategy</code> and is targeted to the <code>packageName</code>. If
     * the former is not provided resources are loaded form the TalkBack
     * context. If the latter is not provided class are loaded from the current
     * TalkBack APK. The speech rule content is loaded from a DOM <code>node</code>
     * and a <code>ruleIndex</code> is assigned to the rule.
     *
     * @throws IllegalStateException If the tries to load custom
     *             filter/formatter while <code>customInstancesSupported</code> is false;
     */
    private EventSpeechRule(TalkBackService context, Node node, int ruleIndex) {
        mContext = context;
        mNode = node;

        AccessibilityEventFilter filter = null;
        AccessibilityEventFormatter formatter = null;

        // Avoid call to Document#getNodesByTagName, since it traverses the
        // entire document.
        final NodeList children = node.getChildNodes();

        for (int i = 0, count = children.getLength(); i < count; i++) {
            final Node child = children.item(i);

            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            final String nodeName = getUnqualifiedNodeName(child);

            if (NODE_NAME_METADATA.equalsIgnoreCase(nodeName)) {
                populateMetadata(child);
            } else if (NODE_NAME_FILTER.equals(nodeName)) {
                filter = createFilter(child);
            } else if (NODE_NAME_FORMATTER.equals(nodeName)) {
                formatter = createFormatter(child);
            }
        }

        if (formatter instanceof ContextBasedRule) {
            ((ContextBasedRule) formatter).initialize(context);
        }

        if (filter instanceof ContextBasedRule) {
            ((ContextBasedRule) filter).initialize(context);
        }

        mFilter = filter;
        mFormatter = formatter;
        mRuleIndex = ruleIndex;
    }

    /**
     * @return The XML representation of this rule.
     */
    @Override
    public String toString() {
        if ((mCachedXmlString == null) && (mNode != null)) {
            mCachedXmlString = NodeUtils.asXmlString(mNode);
            mNode = null;
        }

        return mCachedXmlString;
    }

    /**
     * @return The package targeted by this rule.
     */
    public String getPackageName() {
        return mPackageName;
    }

    /**
     * @return This rule's filter.
     */
    public AccessibilityEventFilter getFilter() {
        return mFilter;
    }

    /**
     * @return This rule's formatter.
     */
    public AccessibilityEventFormatter getFormatter() {
        return mFormatter;
    }

    /**
     * Applies this rule's {@link AccessibilityEventFilter} to an
     * {@link AccessibilityEvent}.
     *
     * @param event The event to which this rule's filter will be applied.
     * @return {@code true} if the event was accepted by the filter,
     *         {@code false} otherwise.
     */
    public boolean applyFilter(AccessibilityEvent event) {
        // Rules without a filter will match all events.
        return mFilter == null || mFilter.accept(event, mContext);
    }

    /**
     * Uses this rule's {@link AccessibilityEventFormatter} to populate a
     * formatted {@link Utterance} based on an {@link AccessibilityEvent}
     *
     * @param event The event to be used by this rule's
     *            {@link AccessibilityEventFormatter}
     * @param utterance The utterance to format
     * @return {@code true} if the formatter successfully populated the
     *         utterance, {@code false} otherwise
     */
    public boolean applyFormatter(AccessibilityEvent event, Utterance utterance) {
        // No formatter indicates there is no utterance text.
        if ((mFormatter != null) && !mFormatter.format(event, mContext, utterance)) {
            return false;
        }

        utterance.getMetadata().putAll(mMetadata);
        utterance.addSpokenFlag(mSpokenFlags);
        utterance.addAllAuditory(mCustomEarcons);
        utterance.addAllHaptic(mCustomVibrations);

        return true;
    }

    /**
     * Populates the meta-data which determines how an {@link Utterance}
     * formatted by this rule should be announced.
     *
     * @param node The meta-data node to parse.
     */
    private void populateMetadata(Node node) {
        final NodeList metadata = node.getChildNodes();
        final int count = metadata.getLength();

        for (int i = 0; i < count; i++) {
            final Node child = metadata.item(i);

            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            final String unqualifiedName = getUnqualifiedNodeName(child);
            final String textContent = getTextContent(child);

            if (unqualifiedName != null) {
                switch (unqualifiedName) {
                    case PROPERTY_QUEUING:
                        final int mode = sQueueModeNameToQueueModeMap.get(textContent);
                        mMetadata.putInt(unqualifiedName, mode);
                        break;
                    case PROPERTY_EARCON: {
                        final int resId = getResourceIdentifierContent(mContext, textContent);
                        mEarcons.add(resId);
                        break;
                    }
                    case PROPERTY_VIBRATION: {
                        final int resId = getResourceIdentifierContent(mContext, textContent);
                        mVibrationPatterns.add(resId);
                        break;
                    }
                    case PROPERTY_CUSTOM_EARCON:
                        mCustomEarcons.add(getResourceIdentifierContent(mContext, textContent));
                        break;
                    case PROPERTY_CUSTOM_VIBRATION:
                        mCustomVibrations.add(getResourceIdentifierContent(mContext, textContent));
                        break;
                    case PROPERTY_SKIP_DUPLICATES:
                        final boolean skipDuplicates = unboxBoolean(
                                (Boolean) parsePropertyValue(unqualifiedName, textContent), false);
                        if (skipDuplicates) {
                            mSpokenFlags |= FeedbackItem.FLAG_SKIP_DUPLICATE;
                        }
                        break;
                    default:
                        final String value = (String) parsePropertyValue(unqualifiedName, textContent);
                        mMetadata.putString(unqualifiedName, value);
                        break;
                }
            }
        }
    }

    /**
     * Parses a property according to its expected type. Parsing failures are
     * logged and null is returned.
     *
     * @param name The property name.
     * @param value The property value.
     * @return The parsed value or null if parse error occurs.
     */
    private static Comparable<?> parsePropertyValue(String name, String value) {
        if (PROPERTY_EVENT_TYPE.equals(name)) {
            return sEventTypeNameToValueMap.get(value);
        }

        final int propertyType = getPropertyType(name);

        switch (propertyType) {
            case PROPERTY_TYPE_BOOLEAN:
                return Boolean.valueOf(value);
            case PROPERTY_TYPE_FLOAT:
                try {
                    return Float.valueOf(value);
                } catch (NumberFormatException nfe) {
                    LogUtils.log(EventSpeechRule.class, Log.WARN, "Property '%s' not float.", name);
                    return null;
                }
            case PROPERTY_TYPE_INTEGER:
                try {
                    return Integer.valueOf(value);
                } catch (NumberFormatException nfe) {
                    LogUtils.log(EventSpeechRule.class, Log.WARN, "Property '%s' not int.", name);
                    return null;
                }
            case PROPERTY_TYPE_STRING:
                return value;
            default:
                throw new IllegalArgumentException("Unknown property: " + name);
        }
    }

    private static boolean unboxBoolean(Boolean b, boolean defaultValue) {
        if (b != null) {
            return b.booleanValue();
        }

        return defaultValue;
    }

    private static int getPropertyType(String propertyName) {
        if (isBooleanProperty(propertyName)) {
            return PROPERTY_TYPE_BOOLEAN;
        } else if (isFloatProperty(propertyName)) {
            return PROPERTY_TYPE_FLOAT;
        } else if (isIntegerProperty(propertyName)) {
            return PROPERTY_TYPE_INTEGER;
        } else if (isStringProperty(propertyName)) {
            return PROPERTY_TYPE_STRING;
        } else {
            return PROPERTY_TYPE_UNKNOWN;
        }
    }

    /**
     * Returns if a property is an integer.
     *
     * @param propertyName The property name.
     * @return True if the property is an integer, false otherwise.
     */
    private static boolean isIntegerProperty(String propertyName) {
        return (PROPERTY_EVENT_TYPE.equals(propertyName)
                || PROPERTY_ITEM_COUNT.equals(propertyName)
                || PROPERTY_CURRENT_ITEM_INDEX.equals(propertyName)
                || PROPERTY_FROM_INDEX.equals(propertyName)
                || PROPERTY_TO_INDEX.equals(propertyName)
                || PROPERTY_SCROLL_X.equals(propertyName)
                || PROPERTY_SCROLL_Y.equals(propertyName)
                || PROPERTY_RECORD_COUNT.equals(propertyName)
                || PROPERTY_ADDED_COUNT.equals(propertyName)
                || PROPERTY_REMOVED_COUNT.equals(propertyName)
                || PROPERTY_QUEUING.equals(propertyName))
                || PROPERTY_VERSION_CODE.equals(propertyName)
                || PROPERTY_PLATFORM_SDK.equals(propertyName);
    }

    /**
     * Returns if a property is a float.
     *
     * @param propertyName The property name.
     * @return True if the property is a float, false otherwise.
     */
    private static boolean isFloatProperty(String propertyName) {
        return PROPERTY_EVENT_TIME.equals(propertyName);
    }

    /**
     * Returns if a property is a string.
     *
     * @param propertyName The property name.
     * @return True if the property is a string, false otherwise.
     */
    private static boolean isStringProperty(String propertyName) {
        return (PROPERTY_PACKAGE_NAME.equals(propertyName)
                || PROPERTY_CLASS_NAME.equals(propertyName)
                || PROPERTY_CLASS_NAME_STRICT.equals(propertyName)
                || PROPERTY_TEXT.equals(propertyName)
                || PROPERTY_BEFORE_TEXT.equals(propertyName)
                || PROPERTY_CONTENT_DESCRIPTION.equals(propertyName)
                || PROPERTY_CONTENT_DESCRIPTION_OR_TEXT.equals(propertyName)
                || PROPERTY_NODE_DESCRIPTION_OR_FALLBACK.equals(propertyName)
                || PROPERTY_VERSION_NAME.equals(propertyName)
                || PROPERTY_PLATFORM_RELEASE.equals(propertyName));
    }

    /**
     * Returns if a property is a boolean.
     *
     * @param propertyName The property name.
     * @return True if the property is a boolean, false otherwise.
     */
    private static boolean isBooleanProperty(String propertyName) {
        return (PROPERTY_CHECKED.equals(propertyName)
                || PROPERTY_ENABLED.equals(propertyName)
                || PROPERTY_FULL_SCREEN.equals(propertyName)
                || PROPERTY_SCROLLABLE.equals(propertyName)
                || PROPERTY_PASSWORD.equals(propertyName)
                || PROPERTY_SKIP_DUPLICATES.equals(propertyName));
    }

    /**
     * Create a {@link AccessibilityEventFilter} given a DOM <code>node</code>.
     *
     * @param node The node.
     * @return The created filter.
     */
    private AccessibilityEventFilter createFilter(Node node) {
        NodeList children = node.getChildNodes();

        // do we have a custom filter
        for (int i = 0, count = children.getLength(); i < count; i++) {
            Node child = children.item(i);

            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String nodeName = getUnqualifiedNodeName(child);
            if (NODE_NAME_CUSTOM.equals(nodeName)) {
                return createNewInstance(getTextContent(child));
            }
        }

        return new DefaultFilter(mContext, node);
    }

    /**
     * Creates a {@link AccessibilityEventFormatter} given a DOM <code>node</code>.
     *
     * @param node The node.
     * @return The created formatter.
     */
    private AccessibilityEventFormatter createFormatter(Node node) {
        NodeList children = node.getChildNodes();

        for (int i = 0, count = children.getLength(); i < count; i++) {
            Node child = children.item(i);

            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String nodeName = getUnqualifiedNodeName(child);
            if (NODE_NAME_CUSTOM.equals(nodeName)) {
                return createNewInstance(getTextContent(child));
            }
        }

        return new DefaultFormatter(node);
    }

    /**
     * Creates a new instance given a <code>className</code>.
     *
     * @param className the class name.
     * @return New instance if succeeded, null otherwise.
     */
    @SuppressWarnings("unchecked")
    private <T> T createNewInstance(String className) {
        try {
            final Class<T> clazz = (Class<T>) mContext.getClassLoader().loadClass(className);

            return clazz.newInstance();
        } catch (Exception e) {
            e.printStackTrace();

            LogUtils.log(EventSpeechRule.class, Log.ERROR, "Rule: #%d. Could not load class: '%s'.",
                    mRuleIndex, className);
        }

        return null;
    }

    /**
     * Factory method that creates all speech rules from the DOM representation
     * of a speechstrategy.xml. This class does not verify if the <code>document</code>
     * is well-formed and it is responsibility of the client to do that.
     *
     * @param context A {@link Context} instance for loading resources.
     * @param document The parsed XML.
     * @return The list of loaded speech rules.
     */
    public static ArrayList<EventSpeechRule> createSpeechRules(TalkBackService context,
            Document document) throws IllegalStateException {
        final ArrayList<EventSpeechRule> speechRules = new ArrayList<>();

        if (document == null || context == null) {
            return speechRules;
        }

        final NodeList children = document.getDocumentElement().getChildNodes();

        for (int i = 0, count = children.getLength(); i < count; i++) {
            final Node child = children.item(i);

            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            try {
                final EventSpeechRule rule = new EventSpeechRule(context, child, i);

                speechRules.add(rule);
            } catch (Exception e) {
                e.printStackTrace();

                LogUtils.log(EventSpeechRule.class, Log.WARN, "Failed loading speech rule: %s",
                        getTextContent(child));
            }
        }

        return speechRules;
    }

    /**
     * Returns from the given <code>context</code> the text content of a <code>node</code>
     * after it has been localized.
     */
    private static String getLocalizedTextContent(Context context, Node node) {
        final String textContent = getTextContent(node);
        final int resId = getResourceIdentifierContent(context, textContent);

        if (resId > 0) {
            return context.getString(resId);
        }

        return textContent;
    }

    /**
     * Returns a resource identifier from the given <code>context</code> for the text
     * content of a <code>node</code>.
     * <p>
     * Note: The resource identifier format is: @&lt;package
     * name&gt;:&lt;type&gt;/&lt;resource name&gt;
     * </p>
     *
     * @param context The parent context.
     * @param resName A valid resource name.
     * @return A resource identifier, or {@code -1} if the resource name is
     *         invalid.
     */
    private static int getResourceIdentifierContent(Context context, String resName) {
        if (resName == null) {
            return -1;
        }

        final Matcher matcher = mResourceIdentifier.matcher(resName);

        if (!matcher.matches()) {
            return -1;
        }

        final Resources res = context.getResources();
        final String defaultPackage = (matcher.groupCount() < 2) ? context.getPackageName() : null;
        final int resId = res.getIdentifier(resName.substring(1), null, defaultPackage);

        if (resId == 0) {
            LogUtils.log(EventSpeechRule.class, Log.ERROR, "Failed to load resource: %s", resName);
        }

        return resId;
    }

    /**
     * Returns the text content of a given <code>node</code> by performing a
     * preorder traversal of the tree rooted at that node. </p> Note: Android
     * Java implementation is not compatible with Java 5.0 which provides such a
     * method.
     *
     * @param node The node.
     * @return The text content.
     */
    private static String getTextContent(Node node) {
        SpannableStringBuilder builder = sTempBuilder;
        getTextContentRecursive(node, builder);
        String text = builder.toString();
        builder.delete(0, builder.length());
        return text;
    }

    /**
     * Performs a recursive preorder traversal of a DOM tree and aggregating the
     * text content.
     *
     * @param node The currently explored node.
     * @param builder Builder that aggregates the text content.
     */
    private static void getTextContentRecursive(Node node, SpannableStringBuilder builder) {
        NodeList children = node.getChildNodes();
        for (int i = 0, count = children.getLength(); i < count; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                builder.append(child.getNodeValue());
            }
            getTextContentRecursive(child, builder);
        }
    }

    /**
     * Returns the unqualified <code>node</code> name i.e. without the prefix.
     *
     * @param node The node.
     * @return The unqualified name.
     */
    private static String getUnqualifiedNodeName(Node node) {
        String nodeName = node.getNodeName();
        int colonIndex = nodeName.indexOf(":");
        if (colonIndex > -1) {
            nodeName = nodeName.substring(colonIndex + 1);
        }
        return nodeName;
    }

    private static String getAttribute(NamedNodeMap attributes, String attributeName) {
        if (attributes == null) {
            return null;
        }

        // Iterating over the entire map since we need to convert the keys to unqualified form.
        // Shouldn't be too bad since we'll only check attributes once per TalkBack initialization,
        // and most nodes don't have attributes anyways.
        int length = attributes.getLength();
        for (int i = 0; i < length; ++i) {
            Node attributeNode = attributes.item(i);
            if (attributeName.equals(getUnqualifiedNodeName(attributeNode))) {
                return attributeNode.getTextContent();
            }
        }

        return null;
    }

    /**
     * Represents a default filter determining if the rule applies to a given
     * {@link AccessibilityEvent}.
     */
    private class DefaultFilter implements AccessibilityEventFilter {
        public DefaultFilter(Context context, Node node) {
            NodeList properties = node.getChildNodes();

            for (int i = 0, count = properties.getLength(); i < count; i++) {
                Node child = properties.item(i);

                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                String unqualifiedName = getUnqualifiedNodeName(child);
                String textContent = getTextContent(child);
                PropertyMatcher propertyMatcher = new PropertyMatcher(context, unqualifiedName,
                        textContent, child.getAttributes());
                mPropertyMatchers.put(unqualifiedName, propertyMatcher);

                // If the speech rule specifies a target package, we use that
                // value rather the one passed as an argument to the rule
                // constructor.
                if (PROPERTY_PACKAGE_NAME.equals(unqualifiedName)) {
                    mPackageName = textContent;
                }
            }
        }

        @Override
        public boolean accept(AccessibilityEvent event, TalkBackService context) {
            for (PropertyMatcher matcher : mPropertyMatchers.values()) {
                if (!evaluatePropertyForEvent(context, matcher, event)) {
                    return false;
                }
            }

            return true;
        }

        private boolean evaluatePropertyForEvent(Context context, PropertyMatcher matcher,
                AccessibilityEvent event) {
            return matcher.accept(getPropertyValue(context, matcher.mPropertyName, event));
        }
    }

    /**
     * Returns the value of a given <code>property</code> of an <code>event</code>.
     *
     * @param property The property
     * @param event The event.
     * @return the value.
     */
    private Object getPropertyValue(Context context, String property, AccessibilityEvent event) {
        final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);

        if (property == null) {
            throw new IllegalArgumentException("Unknown property : " + property);
        }

        // TODO: Don't do so many string comparisons here.
        switch (property) {
            case PROPERTY_EVENT_TYPE:
                return event.getEventType();
            case PROPERTY_PACKAGE_NAME:
                return event.getPackageName();
            case PROPERTY_CLASS_NAME:
                return event.getClassName();
            case PROPERTY_CLASS_NAME_STRICT:
                return event.getClassName();
            case PROPERTY_TEXT:
                return AccessibilityEventUtils.getEventAggregateText(event);
            case PROPERTY_BEFORE_TEXT:
                return event.getBeforeText();
            case PROPERTY_CONTENT_DESCRIPTION:
                return event.getContentDescription();
            case PROPERTY_CONTENT_DESCRIPTION_OR_TEXT:
                return AccessibilityEventUtils.getEventTextOrDescription(event);
            case PROPERTY_NODE_DESCRIPTION_OR_FALLBACK:
                return getNodeDescriptionOrFallback(event);
            case PROPERTY_EVENT_TIME:
                return event.getEventTime();
            case PROPERTY_ITEM_COUNT:
                return event.getItemCount();
            case PROPERTY_CURRENT_ITEM_INDEX:
                return event.getCurrentItemIndex();
            case PROPERTY_FROM_INDEX:
                return event.getFromIndex();
            case PROPERTY_TO_INDEX:
                return record.getToIndex();
            case PROPERTY_SCROLLABLE:
                return record.isScrollable();
            case PROPERTY_SCROLL_X:
                return record.getScrollX();
            case PROPERTY_SCROLL_Y:
                return record.getScrollY();
            case PROPERTY_RECORD_COUNT:
                return AccessibilityEventCompat.getRecordCount(event);
            case PROPERTY_CHECKED:
                return event.isChecked();
            case PROPERTY_ENABLED:
                return event.isEnabled();
            case PROPERTY_FULL_SCREEN:
                return event.isFullScreen();
            case PROPERTY_PASSWORD:
                return event.isPassword();
            case PROPERTY_ADDED_COUNT:
                return event.getAddedCount();
            case PROPERTY_REMOVED_COUNT:
                return event.getRemovedCount();
            case PROPERTY_VERSION_CODE:
                return PackageManagerUtils.getVersionCode(context, event.getPackageName());
            case PROPERTY_VERSION_NAME:
                return PackageManagerUtils.getVersionName(context, event.getPackageName());
            case PROPERTY_PLATFORM_RELEASE:
                return Build.VERSION.RELEASE;
            case PROPERTY_PLATFORM_SDK:
                return Build.VERSION.SDK_INT;
            default:
                throw new IllegalArgumentException("Unknown property : " + property);
        }
    }

    /**
     * Attempts to obtain a description for an event, using the
     * {@link NodeSpeechRuleProcessor} to obtain a description for the source
     * node if possible or falling back on the event text or content description
     * otherwise.
     *
     * @param event The event to generate a description for.
     * @return A description of the event, or an empty string on failure.
     */
    private CharSequence getNodeDescriptionOrFallback(AccessibilityEvent event) {
        AccessibilityNodeInfoCompat source = null;

        try {
            source = AccessibilityEventCompat.asRecord(event).getSource();
            if (source != null) {
                final NodeSpeechRuleProcessor nodeProcessor = NodeSpeechRuleProcessor.getInstance();
                final CharSequence treeDescription = nodeProcessor.getDescriptionForTree(
                        source, event, source);
                if (!TextUtils.isEmpty(treeDescription)) {
                    return treeDescription;
                }
            }
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(source);
        }

        final CharSequence eventDescription =
                AccessibilityEventUtils.getEventTextOrDescription(event);
        if (!TextUtils.isEmpty(eventDescription)) {
            return eventDescription;
        }

        return "";
    }

    /**
     * This class is default formatter for building utterance for announcing an
     * {@link AccessibilityEvent}. The formatting strategy is to populate a
     * template with event properties or strings selected via a regular
     * expression from the event's text. If no template is provided the
     * properties or regular expression selections are concatenated with space
     * as delimiter.
     */
    private class DefaultFormatter implements AccessibilityEventFormatter {
        private static final int QUANTITY_UNDEFINED = -1;

        private static final String NODE_NAME_TEMPLATE = "template";
        private static final String NODE_NAME_QUANTITY = "quantity";
        private static final String NODE_NAME_PROPERTY = "property";

        private static final int MASK_EVENT_TYPES_SPEAK_STATE =
                AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                | AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER
                | AccessibilityEvent.TYPE_VIEW_FOCUSED
                | AccessibilityEvent.TYPE_VIEW_SELECTED;

        /**
         * Optional XML Node representing the formatter template to use.
         */
        private final Node mTemplateNode;

        /**
         * Optional quantity property that can be used to select a template
         * from a plurals resource.
         */
        private final String mQuantityProperty;

        private final List<Pair<String, String>> mSelectors;

        /**
         * Creates a new formatter from a given DOM {@link Node}.
         *
         * @param node The node.
         */
        public DefaultFormatter(Node node) {
            mSelectors = new ArrayList<>();
            Node templateNode = null;
            String quantityProperty = null;
            NodeList children = node.getChildNodes();
            for (int i = 0, count = children.getLength(); i < count; i++) {
                Node child = children.item(i);

                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                String unqualifiedName = getUnqualifiedNodeName(child);
                // some elements contain mandatory reference to a string resource
                if (unqualifiedName != null) {
                    switch (unqualifiedName) {
                        case NODE_NAME_TEMPLATE:
                            templateNode = child;
                            break;
                        case NODE_NAME_PROPERTY:
                            mSelectors.add(new Pair<>(unqualifiedName,
                                    getLocalizedTextContent(mContext, child)));
                            break;
                        case NODE_NAME_QUANTITY:
                            quantityProperty = getTextContent(child);
                            break;
                        default:
                            mSelectors.add(new Pair<>(unqualifiedName, getTextContent(child)));
                            break;
                    }
                } else {
                    mSelectors.add(new Pair<>(unqualifiedName, getTextContent(child)));
                }
            }

            mTemplateNode = templateNode;
            mQuantityProperty = quantityProperty;
        }

        @Override
        public boolean format(AccessibilityEvent event, TalkBackService context,
                Utterance utterance) {
            final List<Pair<String, String>> selectors = mSelectors;
            final Object[] arguments = new Object[selectors.size()];

            for (int i = 0, count = selectors.size(); i < count; i++) {
                final Pair<String, String> selector = selectors.get(i);
                final String selectorType = selector.first;
                final String selectorValue = selector.second;

                if (NODE_NAME_PROPERTY.equals(selectorType)) {
                    final Object propertyValue = getPropertyValue(context, selectorValue, event);
                    arguments[i] = (propertyValue != null) ? propertyValue : "";
                } else {
                    throw new IllegalArgumentException("Unknown selector type: [" + selector.first
                            + ", " + selector.second + "]");
                }
            }

            final int quantity = TextUtils.isEmpty(mQuantityProperty) ? QUANTITY_UNDEFINED
                    : (Integer) getPropertyValue(context, mQuantityProperty, event);

            formatTemplateOrAppendSpaceSeparatedValueIfNoTemplate(utterance, quantity, arguments);

            final boolean speakState = AccessibilityEventUtils.eventMatchesAnyType(
                    event, MASK_EVENT_TYPES_SPEAK_STATE);

            // Append the control's disabled state, if applicable.
            if (!utterance.getSpoken().isEmpty() && !event.isEnabled() && speakState) {
                utterance.addSpoken(mContext.getString(R.string.value_disabled));
            }
            if (utterance.getSpoken().isEmpty()) {
                utterance.addSpokenFlag(FeedbackItem.FLAG_NO_SPEECH);
            }
            return true;
        }

        /**
         * Replaces template parameters in the template for this
         * {@link EventSpeechRule} with the <code>arguments</code> in case such a
         * template was provided. If no template was provided the arguments are
         * concatenated using space as delimiter. The <code>utterance</code> is populated
         * with the generated text.
         *
         * @param utterance The builder to which to append the utterance.
         * @param quantity The quantity to apply if using a plural template or
         *            {@code Integer.MIN_VALUE} for non-plural templates.
         * @param arguments The formatting arguments.
         */
        private void formatTemplateOrAppendSpaceSeparatedValueIfNoTemplate(Utterance utterance,
                int quantity, Object[] arguments) {
            if (mTemplateNode != null) {
                final int templateRes = getResourceIdentifierContent(mContext,
                        getTextContent(mTemplateNode));
                final String templateString;
                if (templateRes < 0) {
                    // The template string provided was a hardcoded string, not
                    // a defined resource. Use the template string provided for
                    // formatting as-is.

                    // TODO: Do we really want to support this
                    // use case? Tests assume hard coded formatter templates are
                    // valid, but they're not used elsewhere.
                    templateString = getTextContent(mTemplateNode);
                } else {
                    if (quantity != QUANTITY_UNDEFINED) {
                        // The template string we have was combined with a
                        // quantity indicator. Obtain the appropriate quantity
                        // string for the resource.
                        templateString = mContext.getResources().getQuantityString(templateRes,
                                quantity);
                    } else {
                        templateString = getLocalizedTextContent(mContext, mTemplateNode);
                    }
                }
                try {
                    final String formatted = String.format(templateString, arguments);
                    if (!TextUtils.isEmpty(formatted)) {
                        utterance.addSpoken(formatted);
                    }
                } catch (MissingFormatArgumentException mfae) {
                    LogUtils.log(DefaultFormatter.class, Log.ERROR, "Speech rule: '%d' has "
                            + "inconsistency between template: '%s' and arguments: '%s'. "
                            + "Possibliy #template arguments does not "
                            + "match #parameters. %s", mRuleIndex, getTextContent(mTemplateNode),
                            arguments, mfae.toString());
                }
            } else {
                for (Object arg : arguments) {
                    CharSequence text;
                    if (arg instanceof CharSequence) {
                        text = (CharSequence) arg;
                    } else {
                        text = String.valueOf(arg);
                    }
                    if (!TextUtils.isEmpty(text)) {
                        utterance.addSpoken(text);
                    }
                }
            }
        }
    }

    /**
     * Helper class for managing pairs of objects.
     */
    private static class Pair<A, B> {
        public final A first;
        public final B second;

        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
    }

    /**
     * Helper class for matching properties.
     */
    private static class PropertyMatcher {

        /**
         * Match type if a property is equal to an expected value.
         */
        private static final int TYPE_EQUALS = 0;

        /**
         * Match type if a numeric property is less than or equal to an expected
         * value.
         */
        private static final int TYPE_LESS_THAN_OR_EQUAL = 1;

        /**
         * Match type if a numeric property is greater than or equal to an
         * expected value.
         */
        private static final int TYPE_GREATER_THAN_OR_EQUAL = 2;

        /**
         * Match type if a numeric property is less than an expected value.
         */
        private static final int TYPE_LESS_THAN = 3;

        /**
         * Match type if a numeric property is greater than an expected value.
         */
        private static final int TYPE_GREATER_THAN = 4;

        /**
         * Match type if a numeric property is equal to one of several expected
         * values.
         */
        private static final int TYPE_OR = 5;

        /**
         * Match type if a property is non-empty and non-null.
         */
        private static final int TYPE_REQUIRE_NON_EMPTY = 6;

        /**
         * Match type if a property is empty or null.
         */
        private static final int TYPE_REQUIRE_EMPTY = 7;

        /**
         * String for a regex pattern than matches float numbers.
         */
        private static final String PATTERN_STRING_FLOAT =
                "(\\s)*([+-])?((\\d)+(\\.(\\d)+)?|\\.(\\d)+)(\\s)*";

        /**
         * Pattern to match a less than or equal a numeric value constraint.
         */
        private static final Pattern PATTERN_LESS_THAN_OR_EQUAL = Pattern.compile("(\\s)*<="
                + PATTERN_STRING_FLOAT);

        /**
         * Pattern to match a greater than or equal a numeric value constraint.
         */
        private static final Pattern PATTERN_GREATER_THAN_OR_EQUAL = Pattern.compile("(\\s)*>="
                + PATTERN_STRING_FLOAT);

        /**
         * Pattern to match a less than a numeric value constraint.
         */
        private static final Pattern PATTERN_LESS_THAN = Pattern.compile("(\\s)*<"
                + PATTERN_STRING_FLOAT);

        /**
         * Pattern to match a greater than a numeric value constraint.
         */
        private static final Pattern PATTERN_GREATER_THAN = Pattern.compile("(\\s)*>"
                + PATTERN_STRING_FLOAT);

        /**
         * Pattern to match an or constraint.
         */
        private static final Pattern PATTERN_OR = Pattern.compile("(.)+\\|\\|(.)+(\\|\\|(.)+)*");

        /**
         * Pattern for splitting an or constraint into simple equal constraints.
         */
        private static final Pattern PATTERN_SPLIT_OR = Pattern.compile("(\\s)*\\|\\|(\\s)*");

        /**
         * The less than or equal string.
         */
        private static final String LESS_THAN_OR_EQUAL = "<=";

        /**
         * The greater than or equal string.
         */
        private static final String GREATER_THAN_OR_EQUAL = ">=";

        /**
         * The less than string.
         */
        private static final String LESS_THAN = "<";

        /**
         * The greater than string.
         */
        private static final String GREATER_THAN = ">";


        /**
         * Attribute specifying that a property can be any non-empty value or must be a null/empty
         * value.
         */
        private static final String ATTRIBUTE_REQUIRE = "require";

        /**
         * Attribute value specifying that the property matcher should be of type
         * {@link #TYPE_REQUIRE_NON_EMPTY}.
         */
        private static final String REQUIRE_NON_EMPTY = "nonEmpty";

        /**
         * Attribute value specifying that the property matcher should be of type
         * {@link #TYPE_REQUIRE_EMPTY}.
         */
        private static final String REQUIRE_EMPTY = "empty";

        /**
         * The name of the property matched by this instance.
         */
        private final String mPropertyName;

        /** The type of property matched by this instance. */
        private final int mPropertyType;

        /**
         * The type of matching to be performed.
         */
        private final int mType;

        /**
         * The values accepted by this matcher.
         */
        private final Object[] mAcceptedValues;

        /**
         * Context handled for accessing resources.
         */
        private final Context mContext;

        /**
         * Creates a new instance.
         *
         * @param context The Context for accessing resources.
         * @param propertyName The name of the matched property.
         * @param acceptedValue The not parsed accepted value.
         */
        public PropertyMatcher(Context context, String propertyName, String acceptedValue,
                NamedNodeMap attributes) {
            mContext = context;
            mPropertyName = propertyName;
            mPropertyType = getPropertyType(propertyName);

            if (acceptedValue == null) {
                mAcceptedValues = null;
                mType = TYPE_EQUALS;
                return;
            }

            final boolean isNumericPropertyType = (mPropertyType == PROPERTY_TYPE_FLOAT)
                    || (mPropertyType == PROPERTY_TYPE_INTEGER);

            if (isNumericPropertyType
                    && PATTERN_LESS_THAN_OR_EQUAL.matcher(acceptedValue).matches()) {
                mType = TYPE_LESS_THAN_OR_EQUAL;
                final int fromIndex = acceptedValue.indexOf(LESS_THAN_OR_EQUAL);
                final String valueString = acceptedValue.substring(fromIndex + 2).trim();
                mAcceptedValues = new Object[] {
                        parsePropertyValue(propertyName, valueString)
                };
            } else if (isNumericPropertyType
                    && PATTERN_GREATER_THAN_OR_EQUAL.matcher(acceptedValue).matches()) {
                mType = TYPE_GREATER_THAN_OR_EQUAL;
                final int fromIndex = acceptedValue.indexOf(GREATER_THAN_OR_EQUAL);
                final String valueString = acceptedValue.substring(fromIndex + 2).trim();
                mAcceptedValues = new Object[] {
                        parsePropertyValue(propertyName, valueString)
                };
            } else if (isNumericPropertyType
                    && PATTERN_LESS_THAN.matcher(acceptedValue).matches()) {
                mType = TYPE_LESS_THAN;
                final int fromIndex = acceptedValue.indexOf(LESS_THAN);
                final String valueString = acceptedValue.substring(fromIndex + 1).trim();
                mAcceptedValues = new Object[] {
                        parsePropertyValue(propertyName, valueString)
                };
            } else if (isNumericPropertyType
                    && PATTERN_GREATER_THAN.matcher(acceptedValue).matches()) {
                mType = TYPE_GREATER_THAN;
                final int fromIndex = acceptedValue.indexOf(GREATER_THAN);
                final String valueString = acceptedValue.substring(fromIndex + 1).trim();
                mAcceptedValues = new Object[] {
                        parsePropertyValue(propertyName, valueString)
                };
            } else if (REQUIRE_EMPTY.equals(getAttribute(attributes, ATTRIBUTE_REQUIRE))) {
                mType = TYPE_REQUIRE_EMPTY;
                mAcceptedValues = new Object[] {};
            } else if (REQUIRE_NON_EMPTY.equals(getAttribute(attributes, ATTRIBUTE_REQUIRE))) {
                mType = TYPE_REQUIRE_NON_EMPTY;
                mAcceptedValues = new Object[] {};
            } else if (PATTERN_OR.matcher(acceptedValue).matches()) {
                mType = TYPE_OR;
                final String[] acceptedValues = PATTERN_SPLIT_OR.split(acceptedValue);
                mAcceptedValues = new Object[acceptedValues.length];
                for (int i = 0, count = acceptedValues.length; i < count; i++) {
                    mAcceptedValues[i] = parsePropertyValue(propertyName, acceptedValues[i]);
                }
            } else {
                mType = TYPE_EQUALS;
                mAcceptedValues = new Object[] {
                        parsePropertyValue(propertyName, acceptedValue)
                };
            }
        }

        /**
         * @return True if the given <code>value</code> with specified <code>arguments</code>
         *         is accepted by this matcher.
         */
        public boolean accept(Object value) {
            if (mAcceptedValues == null) {
                return true;
            }

            if (value == null) {
                if (mPropertyType == PROPERTY_TYPE_STRING) {
                    value = "";
                } else {
                    return false;
                }
            }

            if (PROPERTY_CLASS_NAME.equals(mPropertyName)
                    || PROPERTY_CLASS_NAME_STRICT.equals(mPropertyName)) {
                final String eventClassName = (String) value;

                return acceptClassNameProperty(eventClassName,
                        PROPERTY_CLASS_NAME_STRICT.equals(mPropertyName));
            }

            return acceptProperty(value);
        }

        /**
         * @return True if this matcher accepts a <code>className</code> from the given
         *         <code>packageName</code> while comparing it against the filtered event
         *         (#PROPERTY_CLASS_NAME) from the <code>filteredPackageName</code>.
         */
        private boolean acceptClassNameProperty(String eventClassName, boolean requireExactMatch) {

            // Events with empty class names won't match the filter.
            if (TextUtils.isEmpty(eventClassName)) {
                return false;
            }

            for (Object acceptedValue : mAcceptedValues) {
                final String filteringClassName = (String) acceptedValue;

                // Try a shortcut for efficiency.
                if (filteringClassName.equals(eventClassName)) {
                    return true;
                } else if (requireExactMatch) {
                    return false;
                }

                if (ClassLoadingCache.checkInstanceOf(eventClassName, filteringClassName)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * @return True if this matcher accepts the given <code>value</code> for the
         *         property it matches.
         */
        private boolean acceptProperty(Object value) {
            // Convert CharSequences to Strings.
            if (value instanceof CharSequence) {
                value = value.toString();
            }

            if ((mType == TYPE_EQUALS) || (mType == TYPE_OR)) {
                for (Object acceptedValue : mAcceptedValues) {
                    if (value.equals(acceptedValue)) {
                        return true;
                    }
                }

                return false;
            }

            if (mType == TYPE_REQUIRE_NON_EMPTY) {
                if (value instanceof String && !TextUtils.isEmpty((String) value)) {
                    return true;
                }
            }

            if (mType == TYPE_REQUIRE_EMPTY) {
                if (value instanceof String && TextUtils.isEmpty((String) value)) {
                    return true;
                }
            }

            switch (mPropertyType) {
                case PROPERTY_TYPE_INTEGER:
                    return acceptComparableProperty((Integer) value, (Integer) mAcceptedValues[0]);
                case PROPERTY_TYPE_FLOAT:
                    return acceptComparableProperty((Float) value, (Float) mAcceptedValues[0]);
                default:
                    return false;
            }
        }

        private <T> boolean acceptComparableProperty(Comparable<T> value, T accepted) {
            final int result = value.compareTo(accepted);

            switch (mType) {
                case TYPE_LESS_THAN_OR_EQUAL:
                    return (result <= 0);
                case TYPE_GREATER_THAN_OR_EQUAL:
                    return (result >= 0);
                case TYPE_LESS_THAN:
                    return (result < 0);
                case TYPE_GREATER_THAN:
                    return (result > 0);
                default:
                    return (result == 0);
            }
        }
    }

    /**
     * This interface defines the contract for writing filters. A filter either
     * accepts or rejects an {@link AccessibilityEvent}.
     */
    public interface AccessibilityEventFilter {

        /**
         * Check if the filter accepts a given <code>event</code>.
         *
         * @param event The event.
         * @param context The context to be used for loading resources etc.
         * @return True if the event is accepted, false otherwise.
         */
        boolean accept(AccessibilityEvent event, TalkBackService context);
    }

    /**
     * This interface defines the contract for writing formatters. A formatter
     * populates a formatted {@link Utterance} from an
     * {@link AccessibilityEvent}.
     */
    public interface AccessibilityEventFormatter {
        /**
         * Formats an <code>utterance</code> form given <code>event</code>.
         *
         * @param event The event.
         * @param context The context to be used for loading resources etc.
         * @param utterance The utterance instance to populate.
         * @return {@code true} if the formatter produced output. Accepting an
         *         event in an {@link AccessibilityEventFilter} and returning
         *         {@code false} from an {@link AccessibilityEventFormatter}
         *         will cause the event to be dropped entirely.
         */
        public boolean format(AccessibilityEvent event, TalkBackService context,
                Utterance utterance);
    }

    /**
     * This interface is implemented by rules that need initialization.
     */
    public interface ContextBasedRule {
        /**
         * Initializes the rule with the specified context.
         *
         * @param context The parent service.
         */
        public void initialize(TalkBackService context);
    }
}
