/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.compositor;

import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.ENUM_ROLE;

import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.talkback.compositor.parsetree.ParseTree;
import com.google.android.accessibility.talkback.compositor.parsetree.ParseTree.VariableDelegate;
import com.google.android.accessibility.talkback.compositor.rule.EventTypeNotificationStateChangedFeedbackRule;
import com.google.android.accessibility.talkback.compositor.rule.ScrollPositionFeedbackRule;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.output.SpeechCleanupUtils;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.checkerframework.checker.nullness.qual.Nullable;

/** A VariableDelegate that maps data from AccessibilityEvent */
class EventVariables implements VariableDelegate {
  // IDs of enums.
  private static final int ENUM_CONTENT_CHANGE_TYPE = 8400;

  // IDs of variables.
  private static final int EVENT_TEXT = 8000;
  private static final int EVENT_CONTENT_DESCRIPTION = 8001;
  private static final int EVENT_NOTIFICATION_DETAILS = 8003;
  private static final int EVENT_CONTENT_CHANGE_TYPE = 8004;
  private static final int EVENT_ITEM_COUNT = 8005;
  private static final int EVENT_CURRENT_ITEM_INDEX = 8006;
  private static final int EVENT_REMOVED_COUNT = 8007;
  private static final int EVENT_ADDED_COUNT = 8008;
  private static final int EVENT_TEXT_0 = 8009;
  private static final int EVENT_BEFORE_TEXT = 8011;
  private static final int EVENT_FROM_INDEX = 8017;
  private static final int EVENT_TO_INDEX = 8018;
  private static final int EVENT_SOURCE_ERROR = 8021;
  private static final int EVENT_SOURCE_MAX_TEXT_LENGTH = 8022;
  private static final int EVENT_SOURCE_ROLE = 8023;
  private static final int EVENT_SOURCE_IS_NULL = 8024;
  private static final int EVENT_SCROLL_PERCENT = 8025;
  private static final int EVENT_PROGRESS_PERCENT = 8026;
  private static final int EVENT_NOTIFICATION_CATEGORY = 8027;
  private static final int EVENT_SOURCE_IS_KEYBOARD = 8028;
  private static final int EVENT_IS_WINDOW_CONTENT_CHANGED = 8030;
  private static final int EVENT_SOURCE_IS_LIVE_REGION = 8031;
  private static final int EVENT_PAGER_INDEX_COUNT = 8032;
  private static final int EVENT_SCROLL_POSITION_OUTPUT = 8033;
  private static final int EVENT_DESCRIPTION = 8034;
  private static final int EVENT_AGGREGATE_TEXT = 8035;
  private static final int EVENT_PROGRESS_BAR_EARCON_RATE = 8036;

  // Constants used for ENUM_CONTENT_CHANGE_TYPE.
  private static final int CONTENT_CHANGE_TYPE_OTHER = -1;
  private static final int CONTENT_CHANGE_TYPE_UNDEFINED = 8401;
  private static final int CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION = 8402;
  private static final int CONTENT_CHANGE_TYPE_TEXT = 8403;
  private static final int CONTENT_CHANGE_TYPE_STATE_DESCRIPTION = 8404;
  private static final int CONTENT_CHANGE_TYPE_DRAG_STARTED = 8405;
  private static final int CONTENT_CHANGE_TYPE_DRAG_DROPPED = 8406;
  private static final int CONTENT_CHANGE_TYPE_DRAG_CANCELLED = 8407;
  private static final int CONTENT_CHANGE_TYPE_ERROR = 8408;
  private static final int CONTENT_CHANGE_TYPE_ENABLED = 8409;

  private final Context mContext;
  private final VariableDelegate mParent;
  private final AccessibilityEvent mEvent;
  private final AccessibilityNodeInfo mSource;
  // Stores the user preferred locale changed using language switcher.
  private final @Nullable Locale mUserPreferredLocale;
  private final GlobalVariables globalVariables;

  /**
   * Constructs an EventVariables, which contains context variables to help generate feedback for an
   * accessibility event. Caller must call {@code cleanup()} when done with this object.
   *
   * @param event The originating event.
   * @param source The source from the event.
   */
  EventVariables(
      Context context,
      VariableDelegate parent,
      AccessibilityEvent event,
      AccessibilityNodeInfo source,
      GlobalVariables globalVariables) {
    this.globalVariables = globalVariables;
    mUserPreferredLocale = globalVariables.getUserPreferredLocale();
    mContext = context;
    mParent = parent;
    mEvent = event;
    mSource = source;
  }

  @Override
  public boolean getBoolean(int variableId) {
    switch (variableId) {
      case EVENT_SOURCE_IS_NULL:
        return (mSource == null);
      case EVENT_SOURCE_IS_KEYBOARD:
        return AccessibilityNodeInfoUtils.isKeyboard(mSource);
      case EVENT_IS_WINDOW_CONTENT_CHANGED:
        return mEvent.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
      case EVENT_SOURCE_IS_LIVE_REGION:
        return (mSource != null)
            && (mSource.getLiveRegion() != View.ACCESSIBILITY_LIVE_REGION_NONE);
      default:
        return mParent.getBoolean(variableId);
    }
  }

  @Override
  public int getInteger(int variableId) {
    switch (variableId) {
      case EVENT_ITEM_COUNT:
        return mEvent.getItemCount();
      case EVENT_CURRENT_ITEM_INDEX:
        return mEvent.getCurrentItemIndex();
      case EVENT_REMOVED_COUNT:
        return mEvent.getRemovedCount();
      case EVENT_ADDED_COUNT:
        return mEvent.getAddedCount();
      case EVENT_FROM_INDEX:
        return mEvent.getFromIndex();
      case EVENT_TO_INDEX:
        return mEvent.getToIndex();
      case EVENT_SOURCE_MAX_TEXT_LENGTH:
        return (mSource == null) ? 0 : mSource.getMaxTextLength();
      default:
        return mParent.getInteger(variableId);
    }
  }

  @Override
  public double getNumber(int variableId) {
    switch (variableId) {
      case EVENT_SCROLL_PERCENT:
        return AccessibilityEventUtils.getScrollPercent(mEvent, 50.0f);
      case EVENT_PROGRESS_PERCENT:
        return AccessibilityEventUtils.getProgressPercent(mEvent);
      case EVENT_PROGRESS_BAR_EARCON_RATE:
        return EarconFeedbackUtils.getProgressBarChangeEarconRate(
            mEvent, AccessibilityNodeInfoUtils.toCompat(mSource));
      default:
        return mParent.getNumber(variableId);
    }
  }

  @Override
  public @Nullable CharSequence getString(int variableId) {
    // TODO: Remove collapseRepeatedCharactersAndCleanUp() from VariableDelegate classes.
    // Instead, apply collapseRepeatedCharactersAndCleanUp() to Compositor ttsOutput result whenever
    // Compositor output ttsOutputClean returns true (default is true).
    // TODO: Use spans to mark which parts of composed text are already clean (or should
    // never be cleaned).
    AtomicBoolean textIsClean = new AtomicBoolean(false);
    CharSequence text = getStringInternal(variableId, textIsClean);
    if (!textIsClean.get()) {
      text = SpeechCleanupUtils.collapseRepeatedCharactersAndCleanUp(mContext, text);
    }
    return text;
  }

  /** Modifies parameter clean, to indicate which results need cleaning by getString(). */
  private @Nullable CharSequence getStringInternal(int variableId, AtomicBoolean clean) {
    clean.set(false);
    switch (variableId) {
      case EVENT_CONTENT_DESCRIPTION:
        return AccessibilityEventFeedbackUtils.getEventContentDescription(
            mEvent, mUserPreferredLocale);
      case EVENT_NOTIFICATION_CATEGORY:
        return EventTypeNotificationStateChangedFeedbackRule.getNotificationCategoryStateText(
            mContext, AccessibilityEventUtils.extractNotification(mEvent));
      case EVENT_NOTIFICATION_DETAILS:
        return EventTypeNotificationStateChangedFeedbackRule.getNotificationDetailsStateText(
            AccessibilityEventUtils.extractNotification(mEvent));
      case EVENT_TEXT_0:
        return AccessibilityEventFeedbackUtils.getEventTextFromArrayString(
            mEvent, 0, mUserPreferredLocale);
      case EVENT_BEFORE_TEXT:
        return mEvent.getBeforeText();
      case EVENT_SOURCE_ERROR:
        return (mSource == null) ? "" : mSource.getError();
      case EVENT_PAGER_INDEX_COUNT:
        return AccessibilityEventFeedbackUtils.getPagerIndexCount(
            mEvent, mContext, globalVariables);
      case EVENT_SCROLL_POSITION_OUTPUT:
        return ScrollPositionFeedbackRule.getScrollPositionText(mEvent, mContext, globalVariables);
      case EVENT_DESCRIPTION:
        return AccessibilityEventFeedbackUtils.getEventContentDescriptionOrEventAggregateText(
            mEvent, mUserPreferredLocale);
      case EVENT_AGGREGATE_TEXT:
        return AccessibilityEventFeedbackUtils.getEventAggregateText(mEvent, mUserPreferredLocale);
      default:
        clean.set(true);
        return mParent.getString(variableId);
    }
  }

  @Override
  public int getEnum(int variableId) {
    switch (variableId) {
      case EVENT_CONTENT_CHANGE_TYPE:
        return getContentChangeType(mEvent.getContentChangeTypes());
      case EVENT_SOURCE_ROLE:
        return Role.getSourceRole(mEvent);
      default:
        return mParent.getEnum(variableId);
    }
  }

  @Override
  public @Nullable VariableDelegate getReference(int variableId) {
    return mParent.getReference(variableId);
  }

  @Override
  public int getArrayLength(int variableId) {
    switch (variableId) {
      case EVENT_TEXT:
        return mEvent.getText().size();
      default: // fall out
    }
    return mParent.getArrayLength(variableId);
  }

  @Override
  public @Nullable CharSequence getArrayStringElement(int variableId, int index) {
    switch (variableId) {
      case EVENT_TEXT:
        {
          return AccessibilityEventFeedbackUtils.getEventTextFromArrayString(
              mEvent, index, mUserPreferredLocale);
        }
      default:
        return mParent.getArrayStringElement(variableId, index);
    }
  }

  /** Caller must call VariableDelegate.cleanup() on returned instance. */
  @Override
  public @Nullable VariableDelegate getArrayChildElement(int variableId, int index) {
    return mParent.getArrayChildElement(variableId, index);
  }

  static void declareVariables(ParseTree parseTree) {

    Map<Integer, String> contentChangeTypes = new HashMap<>();
    contentChangeTypes.put(CONTENT_CHANGE_TYPE_OTHER, "other");
    contentChangeTypes.put(CONTENT_CHANGE_TYPE_UNDEFINED, "undefined");
    contentChangeTypes.put(CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION, "content_description");
    contentChangeTypes.put(CONTENT_CHANGE_TYPE_TEXT, "text");
    contentChangeTypes.put(CONTENT_CHANGE_TYPE_STATE_DESCRIPTION, "state_description");
    contentChangeTypes.put(CONTENT_CHANGE_TYPE_DRAG_STARTED, "drag_started");
    contentChangeTypes.put(CONTENT_CHANGE_TYPE_DRAG_DROPPED, "drag_dropped");
    contentChangeTypes.put(CONTENT_CHANGE_TYPE_DRAG_CANCELLED, "drag_cancelled");
    contentChangeTypes.put(CONTENT_CHANGE_TYPE_ERROR, "error");
    contentChangeTypes.put(CONTENT_CHANGE_TYPE_ENABLED, "enabled");
    parseTree.addEnum(ENUM_CONTENT_CHANGE_TYPE, contentChangeTypes);

    // Variables.
    // Events.
    parseTree.addArrayVariable("event.text", EVENT_TEXT);
    parseTree.addStringVariable("event.contentDescription", EVENT_CONTENT_DESCRIPTION);
    parseTree.addStringVariable("event.notificationCategory", EVENT_NOTIFICATION_CATEGORY);
    parseTree.addEnumVariable(
        "event.contentChangeTypes", EVENT_CONTENT_CHANGE_TYPE, ENUM_CONTENT_CHANGE_TYPE);
    parseTree.addStringVariable("event.notificationDetails", EVENT_NOTIFICATION_DETAILS);
    parseTree.addIntegerVariable("event.itemCount", EVENT_ITEM_COUNT);
    parseTree.addIntegerVariable("event.currentItemIndex", EVENT_CURRENT_ITEM_INDEX);
    parseTree.addIntegerVariable("event.removedCount", EVENT_REMOVED_COUNT);
    parseTree.addIntegerVariable("event.addedCount", EVENT_ADDED_COUNT);
    parseTree.addStringVariable("event.text0", EVENT_TEXT_0);
    parseTree.addStringVariable("event.beforeText", EVENT_BEFORE_TEXT);
    parseTree.addIntegerVariable("event.fromIndex", EVENT_FROM_INDEX);
    parseTree.addIntegerVariable("event.toIndex", EVENT_TO_INDEX);
    parseTree.addStringVariable("event.sourceError", EVENT_SOURCE_ERROR);
    parseTree.addIntegerVariable("event.sourceMaxTextLength", EVENT_SOURCE_MAX_TEXT_LENGTH);
    parseTree.addEnumVariable("event.sourceRole", EVENT_SOURCE_ROLE, ENUM_ROLE);
    parseTree.addBooleanVariable("event.sourceIsNull", EVENT_SOURCE_IS_NULL);
    parseTree.addNumberVariable("event.scrollPercent", EVENT_SCROLL_PERCENT);
    parseTree.addNumberVariable("event.progressPercent", EVENT_PROGRESS_PERCENT);
    parseTree.addBooleanVariable("event.sourceIsKeyboard", EVENT_SOURCE_IS_KEYBOARD);
    parseTree.addBooleanVariable("event.isWindowContentChanged", EVENT_IS_WINDOW_CONTENT_CHANGED);
    parseTree.addBooleanVariable("event.sourceIsLiveRegion", EVENT_SOURCE_IS_LIVE_REGION);
    parseTree.addStringVariable("event.pagerIndexCount", EVENT_PAGER_INDEX_COUNT);
    parseTree.addStringVariable("event.scrollPositionOutput", EVENT_SCROLL_POSITION_OUTPUT);
    parseTree.addStringVariable("event.description", EVENT_DESCRIPTION);
    parseTree.addStringVariable("event.aggregateText", EVENT_AGGREGATE_TEXT);
    parseTree.addNumberVariable("event.progressBarEarconRate", EVENT_PROGRESS_BAR_EARCON_RATE);
  }

  private static int getContentChangeType(int contentChangeTypes) {
    if ((contentChangeTypes & AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION) != 0) {
      return CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION;
    }
    if ((contentChangeTypes & AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT) != 0) {
      return CONTENT_CHANGE_TYPE_TEXT;
    }
    if ((contentChangeTypes & AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION) != 0) {
      return CONTENT_CHANGE_TYPE_STATE_DESCRIPTION;
    }
    if ((contentChangeTypes & AccessibilityEvent.CONTENT_CHANGE_TYPE_DRAG_STARTED) != 0) {
      return CONTENT_CHANGE_TYPE_DRAG_STARTED;
    }

    if ((contentChangeTypes & AccessibilityEvent.CONTENT_CHANGE_TYPE_DRAG_DROPPED) != 0) {
      return CONTENT_CHANGE_TYPE_DRAG_DROPPED;
    }

    if ((contentChangeTypes & AccessibilityEvent.CONTENT_CHANGE_TYPE_DRAG_CANCELLED) != 0) {
      return CONTENT_CHANGE_TYPE_DRAG_CANCELLED;
    }
    if (contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED) {
      return CONTENT_CHANGE_TYPE_UNDEFINED;
    }
    // TODO: b/258703440 Integrate with new added CONTENT_CHANGE_TYPE_*.
    // AccessibilityEventUtils.CONTENT_CHANGE_TYPE_ERROR is
    // the integer value of AccessibilityEvent.CONTENT_CHANGE_TYPE_ERROR.
    if ((contentChangeTypes & AccessibilityEventUtils.CONTENT_CHANGE_TYPE_ERROR) != 0) {
      return CONTENT_CHANGE_TYPE_ERROR;
    }
    // TODO: b/258703440 Integrate with new added CONTENT_CHANGE_TYPE_*.
    // AccessibilityEventUtils.CONTENT_CHANGE_TYPE_ENABLED is
    // the integer value of AccessibilityEvent.CONTENT_CHANGE_TYPE_ENABLED.
    if ((contentChangeTypes & AccessibilityEventUtils.CONTENT_CHANGE_TYPE_ENABLED) != 0) {
      return CONTENT_CHANGE_TYPE_ENABLED;
    }

    return CONTENT_CHANGE_TYPE_OTHER;
  }
}
