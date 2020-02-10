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

package com.google.android.accessibility.compositor;

import android.app.Notification;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.LocaleUtils;
import com.google.android.accessibility.utils.PackageManagerUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.SpeechCleanupUtils;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.parsetree.ParseTree;
import com.google.android.accessibility.utils.parsetree.ParseTree.VariableDelegate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.checkerframework.checker.nullness.qual.Nullable;

/** A VariableDelegate that maps data from AccessibilityEvent */
class EventVariables implements VariableDelegate {
  // IDs of enums.
  private static final int ENUM_NOTIFICATION_CATEGORY = 8500;

  // IDs of variables.
  private static final int EVENT_TEXT = 8000;
  private static final int EVENT_CONTENT_DESCRIPTION = 8001;
  private static final int EVENT_NOTIFICATION_DETAILS = 8003;
  private static final int EVENT_IS_CONTENT_DESCRIPTION_CHANGED = 8004;
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
  private static final int EVENT_IS_CONTENT_UNDEFINED = 8029;
  private static final int EVENT_IS_WINDOW_CONTENT_CHANGED = 8030;
  private static final int EVENT_SOURCE_IS_LIVE_REGION = 8031;

  // Constants used for ENUM_NOTIFICATION_CATEGORY.
  private static final int NOTIFICATION_CATEGORY_NONE = -1;
  private static final int NOTIFICATION_CATEGORY_CALL = 8501;
  private static final int NOTIFICATION_CATEGORY_MSG = 8502;
  private static final int NOTIFICATION_CATEGORY_EMAIL = 8503;
  private static final int NOTIFICATION_CATEGORY_EVENT = 8504;
  private static final int NOTIFICATION_CATEGORY_PROMO = 8505;
  private static final int NOTIFICATION_CATEGORY_ALARM = 8506;
  private static final int NOTIFICATION_CATEGORY_PROGRESS = 8507;
  private static final int NOTIFICATION_CATEGORY_SOCIAL = 8508;
  private static final int NOTIFICATION_CATEGORY_ERR = 8509;
  private static final int NOTIFICATION_CATEGORY_TRANSPORT = 8510;
  private static final int NOTIFICATION_CATEGORY_SYS = 8511;
  private static final int NOTIFICATION_CATEGORY_SERVICE = 8512;

  private final Context mContext;
  private final VariableDelegate mParent;
  private final AccessibilityEvent mEvent;
  private final AccessibilityNodeInfo mSource; // Recycled by cleanup()
  // Stores the user preferred locale changed using language switcher.
  private @Nullable final Locale mUserPreferredLocale;

  /**
   * Constructs an EventVariables, which contains context variables to help generate feedback for an
   * accessibility event. Caller must call {@code cleanup()} when done with this object.
   *
   * @param event The originating event.
   * @param source The source from the event. Will be recycled by cleanup().
   */
  EventVariables(
      Context context,
      VariableDelegate parent,
      AccessibilityEvent event,
      AccessibilityNodeInfo source,
      @Nullable Locale userPreferredLocale) {
    mUserPreferredLocale = userPreferredLocale;
    mContext = context;
    mParent = parent;
    mEvent = event;
    mSource = source;
  }

  @Override
  public void cleanup() {
    AccessibilityNodeInfoUtils.recycleNodes(mSource);
    if (mParent != null) {
      mParent.cleanup();
    }
  }

  @Override
  public boolean getBoolean(int variableId) {
    switch (variableId) {
      case EVENT_IS_CONTENT_DESCRIPTION_CHANGED:
        return (mEvent.getContentChangeTypes()
                & AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION)
            != 0;
      case EVENT_IS_CONTENT_UNDEFINED:
        return (mEvent.getContentChangeTypes() == AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED);
      case EVENT_SOURCE_IS_NULL:
        return (mSource == null);
      case EVENT_SOURCE_IS_KEYBOARD:
        return AccessibilityNodeInfoUtils.isKeyboard(mEvent, mSource);
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
        {
          /**
           * Wrap the text with user preferred locale changed using language switcher, with an
           * exception for all talkback created events. As talkback text is always in the system
           * language.
           */
          if (PackageManagerUtils.isTalkBackPackage(mEvent.getPackageName())) {
            return mEvent.getContentDescription();
          }
          // Note: mUserPreferredLocale will not override any LocaleSpan that is already attached
          // to the description. The content description will have just one LocaleSpan.
          return LocaleUtils.wrapWithLocaleSpan(
              mEvent.getContentDescription(), mUserPreferredLocale);
        }
      case EVENT_NOTIFICATION_DETAILS:
        return getNotificationDetails(AccessibilityEventUtils.extractNotification(mEvent));
      case EVENT_TEXT_0:
        {
          // TODO: Support event.text[0] in ParseTree.
          List<CharSequence> texts = mEvent.getText();
          CharSequence text = (texts == null || texts.size() == 0) ? null : texts.get(0);
          /**
           * Wrap the text with user preferred locale changed using language switcher, with an
           * exception for all talkback created events. As talkback text is always in the system
           * language.
           */
          if (!PackageManagerUtils.isTalkBackPackage(mEvent.getPackageName())) {
            // Note: mUserPreferredLocale will not override any LocaleSpan that is already attached
            // to the text. The text will have just one LocaleSpan.
            text = LocaleUtils.wrapWithLocaleSpan(text, mUserPreferredLocale);
          }
          return (text == null) ? "" : text;
        }
      case EVENT_BEFORE_TEXT:
        return mEvent.getBeforeText();
      case EVENT_SOURCE_ERROR:
        return (mSource == null) ? "" : mSource.getError();
      default:
        clean.set(true);
        return mParent.getString(variableId);
    }
  }

  @Override
  public int getEnum(int variableId) {
    switch (variableId) {
      case EVENT_NOTIFICATION_CATEGORY:
        return getNotificationCategory(AccessibilityEventUtils.extractNotification(mEvent));
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
          CharSequence eventText = mEvent.getText().get(index);
          /**
           * Wrap the text with user preferred locale changed using language switcher, with an
           * exception for all talkback created events. As talkback text is always in the system
           * language.
           */
          if (PackageManagerUtils.isTalkBackPackage(mEvent.getPackageName())) {
            return eventText;
          }
          return LocaleUtils.wrapWithLocaleSpan(eventText, mUserPreferredLocale);
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

    Map<Integer, String> notificationCategories = new HashMap<>();
    notificationCategories.put(NOTIFICATION_CATEGORY_NONE, "none");
    notificationCategories.put(NOTIFICATION_CATEGORY_CALL, "call");
    notificationCategories.put(NOTIFICATION_CATEGORY_MSG, "msg");
    notificationCategories.put(NOTIFICATION_CATEGORY_EMAIL, "email");
    notificationCategories.put(NOTIFICATION_CATEGORY_EVENT, "event");
    notificationCategories.put(NOTIFICATION_CATEGORY_PROMO, "promo");
    notificationCategories.put(NOTIFICATION_CATEGORY_ALARM, "alarm");
    notificationCategories.put(NOTIFICATION_CATEGORY_PROGRESS, "progress");
    notificationCategories.put(NOTIFICATION_CATEGORY_SOCIAL, "social");
    notificationCategories.put(NOTIFICATION_CATEGORY_ERR, "err");
    notificationCategories.put(NOTIFICATION_CATEGORY_TRANSPORT, "transport");
    notificationCategories.put(NOTIFICATION_CATEGORY_SYS, "sys");
    notificationCategories.put(NOTIFICATION_CATEGORY_SERVICE, "service");
    parseTree.addEnum(ENUM_NOTIFICATION_CATEGORY, notificationCategories);

    // Variables.
    // Events.
    parseTree.addArrayVariable("event.text", EVENT_TEXT);
    parseTree.addStringVariable("event.contentDescription", EVENT_CONTENT_DESCRIPTION);
    parseTree.addEnumVariable(
        "event.notificationCategory", EVENT_NOTIFICATION_CATEGORY, ENUM_NOTIFICATION_CATEGORY);
    parseTree.addStringVariable("event.notificationDetails", EVENT_NOTIFICATION_DETAILS);
    parseTree.addBooleanVariable(
        "event.isContentDescriptionChanged", EVENT_IS_CONTENT_DESCRIPTION_CHANGED);
    parseTree.addBooleanVariable("event.isContentUndefined", EVENT_IS_CONTENT_UNDEFINED);
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
    parseTree.addEnumVariable("event.sourceRole", EVENT_SOURCE_ROLE, Compositor.ENUM_ROLE);
    parseTree.addBooleanVariable("event.sourceIsNull", EVENT_SOURCE_IS_NULL);
    parseTree.addNumberVariable("event.scrollPercent", EVENT_SCROLL_PERCENT);
    parseTree.addNumberVariable("event.progressPercent", EVENT_PROGRESS_PERCENT);
    parseTree.addBooleanVariable("event.sourceIsKeyboard", EVENT_SOURCE_IS_KEYBOARD);
    parseTree.addBooleanVariable("event.isWindowContentChanged", EVENT_IS_WINDOW_CONTENT_CHANGED);
    parseTree.addBooleanVariable("event.sourceIsLiveRegion", EVENT_SOURCE_IS_LIVE_REGION);
  }

  private static int getNotificationCategory(@Nullable Notification notification) {
    if (notification == null || notification.category == null) {
      return NOTIFICATION_CATEGORY_NONE;
    }
    switch (notification.category) {
      case Notification.CATEGORY_CALL:
        return NOTIFICATION_CATEGORY_CALL;
      case Notification.CATEGORY_MESSAGE:
        return NOTIFICATION_CATEGORY_MSG;
      case Notification.CATEGORY_EMAIL:
        return NOTIFICATION_CATEGORY_EMAIL;
      case Notification.CATEGORY_EVENT:
        return NOTIFICATION_CATEGORY_EVENT;
      case Notification.CATEGORY_PROMO:
        return NOTIFICATION_CATEGORY_PROMO;
      case Notification.CATEGORY_ALARM:
        return NOTIFICATION_CATEGORY_ALARM;
      case Notification.CATEGORY_PROGRESS:
        return NOTIFICATION_CATEGORY_PROGRESS;
      case Notification.CATEGORY_SOCIAL:
        return NOTIFICATION_CATEGORY_SOCIAL;
      case Notification.CATEGORY_ERROR:
        return NOTIFICATION_CATEGORY_ERR;
      case Notification.CATEGORY_TRANSPORT:
        return NOTIFICATION_CATEGORY_TRANSPORT;
      case Notification.CATEGORY_SYSTEM:
        return NOTIFICATION_CATEGORY_SYS;
      case Notification.CATEGORY_SERVICE:
        return NOTIFICATION_CATEGORY_SERVICE;
      default:
        return NOTIFICATION_CATEGORY_NONE;
    }
  }

  private static CharSequence getNotificationDetails(@Nullable Notification notification) {
    if (notification == null) {
      return "";
    }

    List<CharSequence> notificationDetails = new ArrayList<CharSequence>();
    CharSequence notificationTickerText = notification.tickerText;

    if (notification.extras != null) {
      // Get notification title and text from the Notification Extras bundle.
      CharSequence notificationTitle = notification.extras.getCharSequence("android.title");
      CharSequence notificationText = notification.extras.getCharSequence("android.text");

      if (!TextUtils.isEmpty(notificationTitle)) {
        notificationDetails.add(notificationTitle);
      }

      if (!TextUtils.isEmpty(notificationText)) {
        notificationDetails.add(notificationText);
      } else {
        notificationDetails.add(notificationTickerText);
      }
    }

    CharSequence text =
        notificationDetails.isEmpty()
            ? null
            : StringBuilderUtils.getAggregateText(notificationDetails);
    return (text == null) ? "" : text;
  }
}
