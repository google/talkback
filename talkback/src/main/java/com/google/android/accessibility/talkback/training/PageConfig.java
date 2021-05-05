/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.android.accessibility.talkback.training;

import static com.google.android.accessibility.talkback.training.content.PageContentConfig.UNKNOWN_RESOURCE_ID;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.style.URLSpan;
import androidx.annotation.ArrayRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.gesture.GestureController;
import com.google.android.accessibility.talkback.training.content.Divider;
import com.google.android.accessibility.talkback.training.content.EditTextBox;
import com.google.android.accessibility.talkback.training.content.Heading;
import com.google.android.accessibility.talkback.training.content.Link;
import com.google.android.accessibility.talkback.training.content.Note;
import com.google.android.accessibility.talkback.training.content.PageButton;
import com.google.android.accessibility.talkback.training.content.PageContentConfig;
import com.google.android.accessibility.talkback.training.content.Text;
import com.google.android.accessibility.talkback.training.content.TextList;
import com.google.android.accessibility.talkback.training.content.TextWithIcon;
import com.google.android.accessibility.talkback.training.content.TextWithNumber;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A page consists of some contents. The gestures is added by {@link
 * PageConfig.Builder#captureGesture(int, int)} will be captured when this page is shown as a test
 * pad.
 *
 * <p>For example: Creating a page with a title, text, 4-finger-swipe-up icon and description. When
 * the user swipes up with 4 fingers on the screen, the gesture announcement will be spoken.
 *
 * <pre>{@code
 * Page.builder(R.string.page_name)
 *     .setText(R.string.text)
 *     .setTextWithIcon(R.string.home_gesture_text, R.drawable.swipe_up_4_finger)
 *     .captureGesture(
 *         AccessibilityService.GESTURE_4_FINGER_SWIPE_UP,
 *         R.string.gesture_announcement)
 * }</pre>
 */
@AutoValue
public abstract class PageConfig implements Serializable {

  /** A serializable predicate. */
  public interface SerializablePredicate extends Predicate<Context>, Serializable {}

  /**
   * Defines predicates that are evaluated whether the content needs to be shown on the Page or not.
   */
  public enum PageContentPredicate {
    GESTURE_CHANGED((context) -> GestureController.isAnyGestureChanged(context)),
    ACCESSIBILITY_SERVICE_TOGGLE_VIA_SHORTCUT(
        (context) ->
            !TextUtils.isEmpty(
                Settings.Secure.getString(
                    context.getContentResolver(), ACCESSIBILITY_BUTTON_TARGETS))),
    SUPPORT_SYSTEM_ACTIONS((context) -> FeatureSupport.supportSystemActions());

    private final SerializablePredicate predicate;

    PageContentPredicate(SerializablePredicate predicate) {
      this.predicate = predicate;
    }

    public boolean test(Context context) {
      return predicate.test(context);
    }
  }

  /**
   * Setting accessibility services which are toggled via the accessibility button or shortcut
   * gesture. Refers to android.provider.Settings.ACCESSIBILITY_BUTTON_TARGETS.
   */
  public static final String ACCESSIBILITY_BUTTON_TARGETS = "accessibility_button_targets";

  public static final int UNKNOWN_ANNOUNCEMENT = -1;

  /**
   * When user performs a gesture, announcing the name of the action which is assigned to the
   * gesture.
   */
  public static final int ANNOUNCE_REAL_ACTION = 0;

  @StringRes
  public abstract int getPageName();

  public abstract ImmutableList<PageContentConfig> getContents();

  /**
   * The gestures will be captured by {@link TrainingFragment}. Mapping from gesture ID to
   * announcement resource ID.
   */
  public abstract ImmutableMap<Integer, Integer> getCaptureGestureIdToAnnouncements();

  /**
   * Returns true if the entire page is in the same focus means reading out all the text in the page
   * continuously.
   */
  public abstract boolean isOnlyOneFocus();

  /** Returns false if there is no button on this page. Default is true. */
  public abstract boolean hasNavigationButtonBar();

  /** Returns true if page number information should be shown on this page. Default is true. */
  public abstract boolean showPageNumber();

  /** Returns true if the page is the last page in a section. */
  public abstract boolean isEndOfSection();

  private static PageConfig create(
      @StringRes int pageName,
      ImmutableList<PageContentConfig> contents,
      ImmutableMap<Integer, Integer> gestures,
      boolean isOnlyOneFocus,
      boolean hasNavigationButtonBar,
      boolean showPageNumber,
      boolean isEndOfSection) {
    return new AutoValue_PageConfig(
        pageName,
        contents,
        gestures,
        isOnlyOneFocus,
        hasNavigationButtonBar,
        showPageNumber,
        isEndOfSection);
  }

  /**
   * Checks if the gesture is captured by the page for test pads.
   *
   * @return If the gesture is captured, returns a a feedback. Otherwise, return {@code
   *     UNKNOWN_ANNOUNCEMENT}.
   */
  @StringRes
  public int intercepts(int gestureId) {
    // For test pads. Gesture IDs and announcements are defined in
    // Page.getCaptureGestureIdToAnnouncements().
    Map<Integer, Integer> gestures = getCaptureGestureIdToAnnouncements();
    if (gestures.isEmpty()) {
      return UNKNOWN_ANNOUNCEMENT;
    }

    @Nullable Integer announcement = gestures.get(gestureId);
    return announcement == null ? UNKNOWN_ANNOUNCEMENT : announcement;
  }

  public static Builder builder(@StringRes int pageName) {
    return new Builder(pageName);
  }

  /** Builder for page. */
  public static class Builder {

    @StringRes private final int pageName;
    private final List<PageContentConfig> contents = new ArrayList<>();
    private final Map<Integer, Integer> captureGestureIdToAnnouncements = new HashMap<>();
    private boolean isOnlyOneFocus;
    private boolean hasNavigationButtonBar = true;
    private boolean showPageNumber = true;
    private boolean isEndOfSection = false;

    private Builder(@StringRes int pageName) {
      this.pageName = pageName;
    }

    /** Sets true if the entire page is in the same focus. */
    public Builder setOnlyOneFocus(boolean isOnlyOneFocus) {
      this.isOnlyOneFocus = isOnlyOneFocus;
      return this;
    }

    public Builder hideNavigationButtonBar() {
      this.hasNavigationButtonBar = false;
      return this;
    }

    public Builder hidePageNumber() {
      this.showPageNumber = false;
      return this;
    }

    /**
     * If the page is an end page of a section, going back to the index page when clicking the
     * Finish button.
     */
    public Builder setEndOfSection() {
      this.isEndOfSection = true;
      return this;
    }

    /** Adds one or multiple lines text to the page. */
    public Builder addText(@StringRes int textResId, int... textArgResIds) {
      this.contents.add(new Text(textResId, textArgResIds));
      return this;
    }

    // TODO Too many combinations of text, bullet and gesture. Switch to one function.
    /**
     * Adds one or multiple lines text to the page, including a actual gesture. If no gesture
     * assigned, adds default text to the page.
     */
    public Builder addTextWithActualGesture(
        @StringRes int textWithActualGestureResId, int actionKey, @StringRes int defaultTextResId) {
      this.contents.add(
          new Text(
              defaultTextResId,
              textWithActualGestureResId,
              actionKey,
              /* hasBulletPoint= */ false));
      return this;
    }

    /** Adds a text with a bullet point with a predicate. */
    public Builder addTextWithBullet(@StringRes int textResId, PageContentPredicate predicate) {
      Text text = new Text(textResId, true);
      text.setShowingPredicate(predicate);
      this.contents.add(text);
      return this;
    }

    /** Adds a text with a bullet point. */
    public Builder addTextWithBullet(@StringRes int textResId) {
      this.contents.add(new Text(textResId, true));
      return this;
    }

    /** Adds sub-text without margin between texts. */
    public Builder addSubText(@StringRes int textResId) {
      this.contents.add(new Text(textResId, false, true));
      return this;
    }

    /**
     * Adds a text that starts with a bullet point and a gesture that should be replaced with an
     * actual gesture. If no gesture is assigned, adds default text to the page. .
     */
    public Builder addTextWithActualGestureAndBullet(
        @StringRes int textWithActualGestureResId, int actionKey, @StringRes int defaultTextResId) {
      this.contents.add(
          new Text(
              defaultTextResId, textWithActualGestureResId, actionKey, /* hasBulletPoint= */ true));
      return this;
    }

    /** Adds a text that starts with a number. */
    public Builder addTextWithNumber(@StringRes int textResId, int number) {
      this.contents.add(new TextWithNumber(textResId, number));
      return this;
    }

    /** Adds icon and description to the page with a predicate. */
    public Builder addTextWithIcon(
        @StringRes int textResId, @DrawableRes int srcResId, PageContentPredicate predicate) {
      TextWithIcon textWithIcon = new TextWithIcon(textResId, srcResId);
      textWithIcon.setShowingPredicate(predicate);
      this.contents.add(textWithIcon);
      return this;
    }

    /** Adds icon and description to the page. */
    public Builder addTextWithIcon(@StringRes int textResId, @DrawableRes int srcResId) {
      this.contents.add(new TextWithIcon(textResId, srcResId));
      return this;
    }

    /** Adds icon and description to the page. */
    public Builder addTextWithIcon(
        @StringRes int textResId, @StringRes int subtextResId, @DrawableRes int srcResId) {
      this.contents.add(new TextWithIcon(textResId, subtextResId, srcResId));
      return this;
    }

    /** Adds a heading. */
    public Builder addHeading(@StringRes int headingResId) {
      this.contents.add(new Heading(headingResId));
      return this;
    }

    /** Adds a note to the page. */
    public Builder addNote(@StringRes int textResId, PageContentPredicate predicate) {
      Note note = new Note(textResId);
      note.setShowingPredicate(predicate);
      this.contents.add(note);
      return this;
    }

    /** Adds a list to the page. */
    public Builder addList(@ArrayRes int textsResId) {
      contents.add(new TextList(textsResId));
      return this;
    }

    /**
     * Adds a link to the page. The activity will link to the first page when the link is clicked,
     * then it'll go back to the current page when finishing reading the last page.
     *
     * @param firstPageInSectionNameResId The resource id of page name for the first page in a
     *     section. This page will be shown when user clicking the link
     */
    public Builder addLink(
        @StringRes int textResId,
        @StringRes int subtextResId,
        @DrawableRes int srcResId,
        @StringRes int firstPageInSectionNameResId) {
      contents.add(new Link(textResId, subtextResId, srcResId, firstPageInSectionNameResId));
      return this;
    }

    /** Adds an editable text box with content to the page. */
    public Builder addEditTextWithContent(@StringRes int textResId) {
      this.contents.add(new EditTextBox(textResId, UNKNOWN_RESOURCE_ID));
      return this;
    }

    /** Adds an empty editable text box with a hint to the page. */
    public Builder addEditTextWithHint(@StringRes int hintResId) {
      this.contents.add(new EditTextBox(UNKNOWN_RESOURCE_ID, hintResId));
      return this;
    }

    /** Adds a button to the page. */
    public Builder addButton(@StringRes int textResId) {
      this.contents.add(new PageButton(textResId));
      return this;
    }

    /** Adds a text with an empty {@link URLSpan}. */
    public Builder addTextWithLink(@StringRes int textResId) {
      this.contents.add(
          new Text(
              textResId,
              UNKNOWN_RESOURCE_ID,
              UNKNOWN_RESOURCE_ID,
              /* hasBulletPoint= */ false,
              /* isSubText= */ false,
              /* isLink= */ true));
      return this;
    }

    /** Adds a divider to the page. */
    public Builder addDivider() {
      this.contents.add(new Divider());
      return this;
    }

    /** Records the gesture ID, which will be captured, and associated announcements. */
    public Builder captureGesture(int gestureId, @StringRes int announcementResId) {
      this.captureGestureIdToAnnouncements.put(gestureId, announcementResId);
      return this;
    }

    /** Captures all gestures, except swipe right, swipe left and double tap with 1 finger. */
    public Builder captureAllGestures() {
      // 1 finger.
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_SWIPE_UP, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_SWIPE_DOWN, ANNOUNCE_REAL_ACTION);

      // 1 finger back-and-forth.
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT, ANNOUNCE_REAL_ACTION);

      // 1 finger angle.
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN, ANNOUNCE_REAL_ACTION);

      // 2 fingers.
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP, ANNOUNCE_REAL_ACTION);

      // 3 fingers.
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_3_FINGER_SWIPE_UP, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT, ANNOUNCE_REAL_ACTION);

      // 4 fingers.
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_4_FINGER_SINGLE_TAP, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_4_FINGER_TRIPLE_TAP, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_4_FINGER_SWIPE_UP, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_4_FINGER_SWIPE_DOWN, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_4_FINGER_SWIPE_LEFT, ANNOUNCE_REAL_ACTION);
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_4_FINGER_SWIPE_RIGHT, ANNOUNCE_REAL_ACTION);
      return this;
    }

    /** Creates a {@link PageConfig} */
    public PageConfig build() {
      return PageConfig.create(
          this.pageName,
          ImmutableList.copyOf(this.contents),
          ImmutableMap.copyOf(this.captureGestureIdToAnnouncements),
          isOnlyOneFocus,
          hasNavigationButtonBar,
          showPageNumber,
          isEndOfSection);
    }
  }
}
