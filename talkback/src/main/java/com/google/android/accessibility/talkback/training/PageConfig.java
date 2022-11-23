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
import com.google.android.accessibility.talkback.training.TrainingIpcClient.ServiceData;
import com.google.android.accessibility.talkback.training.content.Divider;
import com.google.android.accessibility.talkback.training.content.EditTextBox;
import com.google.android.accessibility.talkback.training.content.Heading;
import com.google.android.accessibility.talkback.training.content.Link;
import com.google.android.accessibility.talkback.training.content.Note;
import com.google.android.accessibility.talkback.training.content.PageButton;
import com.google.android.accessibility.talkback.training.content.PageButton.PageButtonOnClickListener;
import com.google.android.accessibility.talkback.training.content.PageContentConfig;
import com.google.android.accessibility.talkback.training.content.Text;
import com.google.android.accessibility.talkback.training.content.Text.Paragraph;
import com.google.android.accessibility.talkback.training.content.TextList;
import com.google.android.accessibility.talkback.training.content.TextWithIcon;
import com.google.android.accessibility.talkback.training.content.TextWithNumber;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
 * Page.builder(PageId.PAGE_ID, R.string.page_name)
 *     .setText(R.string.text)
 *     .setTextWithIcon(R.string.home_gesture_text, R.drawable.swipe_up_4_finger)
 *     .captureGesture(
 *         AccessibilityService.GESTURE_4_FINGER_SWIPE_UP,
 *         R.string.gesture_announcement)
 * }</pre>
 */
@AutoValue
public abstract class PageConfig {

  /**
   * Defines predicates that are evaluated whether the content needs to be shown on the Page or not.
   */
  public enum PageContentPredicate {
    GESTURE_CHANGED(ServiceData::isAnyGestureChanged),
    ACCESSIBILITY_SERVICE_TOGGLE_VIA_SHORTCUT(
        (data) ->
            isAccessibilityShortcutOrButtonEnabled(data.getContext())
                && !isAccessibilityFloatingButtonEnabled(data.getContext())),
    SUPPORT_SYSTEM_ACTIONS((data) -> FeatureSupport.supportGetSystemActions(data.getContext()));

    private final Predicate<ServiceData> predicate;

    PageContentPredicate(Predicate<ServiceData> predicate) {
      this.predicate = predicate;
    }

    public boolean test(ServiceData data) {
      return predicate.test(data);
    }
  }

  /** Unique identifiers for training pages. */
  public enum PageId {
    PAGE_ID_UNKNOWN,
    PAGE_ID_FINISHED,
    PAGE_ID_WELCOME_TO_UPDATED_TALKBACK_FOR_MULTIFINGER_GESTURES,
    PAGE_ID_WELCOME_TO_TALKBACK_WATCH,
    PAGE_ID_WELCOME_TO_TALKBACK,
    PAGE_ID_EXPLORE_BY_TOUCH,
    PAGE_ID_SCROLLING,
    PAGE_ID_GESTURES_PAGE_FOR_GESTURE_NAVIGATION_USER,
    PAGE_ID_GESTURES_PAGE_FOR_3_BUTTON_NAVIGATION_USER,
    PAGE_ID_MENUS,
    PAGE_ID_MENUS_PRE_R,
    PAGE_ID_TUTORIAL_FINISHED,
    PAGE_ID_TUTORIAL_INDEX,
    PAGE_ID_USING_TEXT_BOXES,
    PAGE_ID_TYPING_TEXT,
    PAGE_ID_MOVING_CURSOR,
    PAGE_ID_SELECTING_TEXT,
    PAGE_ID_SELECTING_TEXT_PRE_R,
    PAGE_ID_COPY_CUT_PASTE,
    PAGE_ID_COPY_CUT_PASTE_PRE_R,
    PAGE_ID_READ_BY_CHARACTER,
    PAGE_ID_READ_BY_CHARACTER_PRE_R,
    PAGE_ID_JUMP_BETWEEN_CONTROLS,
    PAGE_ID_JUMP_BETWEEN_CONTROLS_PRE_R,
    PAGE_ID_JUMP_BETWEEN_LINKS,
    PAGE_ID_JUMP_BETWEEN_LINKS_PRE_R,
    PAGE_ID_JUMP_BETWEEN_HEADINGS,
    PAGE_ID_JUMP_BETWEEN_HEADINGS_PRE_R,
    PAGE_ID_VOICE_COMMANDS,
    PAGE_ID_PRACTICE_GESTURES,
    PAGE_ID_PRACTICE_GESTURES_PRE_R,
    PAGE_ID_VOICE_COMMAND_OVERVIEW,
    PAGE_ID_VOICE_COMMAND_READING_CONTROLS,
    PAGE_ID_VOICE_COMMAND_FIND_ITEMS,
    PAGE_ID_VOICE_COMMAND_FIND_ITEMS_FOR_WATCH,
    PAGE_ID_VOICE_COMMAND_TEXT_EDITING,
    PAGE_ID_VOICE_COMMAND_DEVICE_NAVIGATION,
    PAGE_ID_VOICE_COMMAND_OTHER_COMMANDS,
    PAGE_ID_UPDATE_WELCOME_13_0,
    PAGE_ID_UPDATE_WELCOME_13_0_PRE_R,
    PAGE_ID_SUPPORT_FOR_BRAILLE_DISPLAYS,
    PAGE_ID_ACTIONS_IN_READING_CONTROLS,
    PAGE_ID_DESCRIBE_ICONS,
    PAGE_ID_TRY_LOOKOUT_AND_TALKBACK,
  }

  /**
   * Setting accessibility services which are toggled via the accessibility button or shortcut
   * gesture. Refers to android.provider.Settings.ACCESSIBILITY_BUTTON_TARGETS.
   */
  public static final String ACCESSIBILITY_BUTTON_TARGETS = "accessibility_button_targets";

  /**
   * The accessibility button mode. The setting value is 0, if the accessibility button is in
   * navigation bar; The setting value is 1, if the accessibility button is floating on the display.
   * Refers to android.provider.Settings.ACCESSIBILITY_BUTTON_MODE.
   */
  public static final String ACCESSIBILITY_BUTTON_MODE = "accessibility_button_mode";

  /**
   * Accessibility button mode value. The accessibility service can be toggled via the button in the
   * navigation bar. Refers to android.provider.Settings.ACCESSIBILITY_BUTTON_MODE_NAVIGATION_BAR.
   */
  public static final int ACCESSIBILITY_BUTTON_MODE_NAVIGATION_BAR = 0x0;

  /**
   * Accessibility button mode value. The accessibility service can be toggled via the button
   * floating on the display. Refers to
   * android.provider.Settings.ACCESSIBILITY_BUTTON_MODE_FLOATING_MENU.
   */
  public static final int ACCESSIBILITY_BUTTON_MODE_FLOATING_MENU = 0x1;

  public static final int UNKNOWN_ANNOUNCEMENT = -1;

  /**
   * When user performs a gesture, announcing the name of the action which is assigned to the
   * gesture.
   */
  public static final int ANNOUNCE_REAL_ACTION = 0;

  public abstract PageId getPageId();

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
      PageId pageId,
      @StringRes int pageName,
      ImmutableList<PageContentConfig> contents,
      ImmutableMap<Integer, Integer> gestures,
      boolean isOnlyOneFocus,
      boolean hasNavigationButtonBar,
      boolean showPageNumber,
      boolean isEndOfSection) {
    PageConfig pageConfig =
        new AutoValue_PageConfig(
            pageId,
            pageName,
            contents,
            gestures,
            isOnlyOneFocus,
            hasNavigationButtonBar,
            showPageNumber,
            isEndOfSection);
    return pageConfig;
  }

  @Nullable
  public static PageConfig getPage(PageId pageId) {
    switch (pageId) {
      case PAGE_ID_WELCOME_TO_UPDATED_TALKBACK_FOR_MULTIFINGER_GESTURES:
        return OnboardingInitiator.WELCOME_TO_UPDATED_TALKBACK_FOR_MULTIFINGER_GESTURES.build();
      case PAGE_ID_WELCOME_TO_TALKBACK_WATCH:
        return TutorialInitiator.WELCOME_TO_TALKBACK_WATCH_PAGE.build();
      case PAGE_ID_WELCOME_TO_TALKBACK:
        return TutorialInitiator.WELCOME_TO_TALKBACK_PAGE.build();
      case PAGE_ID_EXPLORE_BY_TOUCH:
        return TutorialInitiator.EXPLORE_BY_TOUCH_PAGE.build();
      case PAGE_ID_SCROLLING:
        return TutorialInitiator.SCROLLING_PAGE.build();
      case PAGE_ID_GESTURES_PAGE_FOR_GESTURE_NAVIGATION_USER:
        return TutorialInitiator.GESTURES_PAGE_FOR_GESTURE_NAVIGATION_USER.build();
      case PAGE_ID_GESTURES_PAGE_FOR_3_BUTTON_NAVIGATION_USER:
        return TutorialInitiator.GESTURES_PAGE_FOR_3_BUTTON_NAVIGATION_USER.build();
      case PAGE_ID_MENUS:
        return TutorialInitiator.MENUS_PAGE.build();
      case PAGE_ID_MENUS_PRE_R:
        return TutorialInitiator.MENUS_PAGE_PRE_R.build();
      case PAGE_ID_TUTORIAL_FINISHED:
        return TutorialInitiator.TUTORIAL_FINISHED_PAGE.build();
      case PAGE_ID_TUTORIAL_INDEX:
        return TutorialInitiator.TUTORIAL_INDEX_PAGE.build();
      case PAGE_ID_USING_TEXT_BOXES:
        return TutorialInitiator.USING_TEXT_BOXES_PAGE.build();
      case PAGE_ID_TYPING_TEXT:
        return TutorialInitiator.TYPING_TEXT_PAGE.build();
      case PAGE_ID_MOVING_CURSOR:
        return TutorialInitiator.MOVING_CURSOR_PAGE.build();
      case PAGE_ID_SELECTING_TEXT:
        return TutorialInitiator.SELECTING_TEXT_PAGE.build();
      case PAGE_ID_SELECTING_TEXT_PRE_R:
        return TutorialInitiator.SELECTING_TEXT_PAGE_PRE_R.build();
      case PAGE_ID_COPY_CUT_PASTE:
        return TutorialInitiator.COPY_CUT_PASTE_PAGE.build();
      case PAGE_ID_COPY_CUT_PASTE_PRE_R:
        return TutorialInitiator.COPY_CUT_PASTE_PAGE_PRE_R.build();
      case PAGE_ID_READ_BY_CHARACTER:
        return TutorialInitiator.READ_BY_CHARACTER.build();
      case PAGE_ID_READ_BY_CHARACTER_PRE_R:
        return TutorialInitiator.READ_BY_CHARACTER_PRE_R.build();
      case PAGE_ID_JUMP_BETWEEN_CONTROLS:
        return TutorialInitiator.JUMP_BETWEEN_CONTROLS.build();
      case PAGE_ID_JUMP_BETWEEN_CONTROLS_PRE_R:
        return TutorialInitiator.JUMP_BETWEEN_CONTROLS_PRE_R.build();
      case PAGE_ID_JUMP_BETWEEN_LINKS:
        return TutorialInitiator.JUMP_BETWEEN_LINKS.build();
      case PAGE_ID_JUMP_BETWEEN_LINKS_PRE_R:
        return TutorialInitiator.JUMP_BETWEEN_LINKS_PRE_R.build();
      case PAGE_ID_JUMP_BETWEEN_HEADINGS:
        return TutorialInitiator.JUMP_BETWEEN_HEADINGS.build();
      case PAGE_ID_JUMP_BETWEEN_HEADINGS_PRE_R:
        return TutorialInitiator.JUMP_BETWEEN_HEADINGS_PRE_R.build();
      case PAGE_ID_VOICE_COMMANDS:
        return TutorialInitiator.VOICE_COMMANDS.build();
      case PAGE_ID_PRACTICE_GESTURES:
        return TutorialInitiator.PRACTICE_GESTURES.build();
      case PAGE_ID_PRACTICE_GESTURES_PRE_R:
        return TutorialInitiator.PRACTICE_GESTURES_PRE_R.build();
      case PAGE_ID_VOICE_COMMAND_OVERVIEW:
        return VoiceCommandHelpInitiator.voiceCommandOverview.build();
      case PAGE_ID_VOICE_COMMAND_READING_CONTROLS:
        return VoiceCommandHelpInitiator.voiceCommandReadingControls.build();
      case PAGE_ID_VOICE_COMMAND_FIND_ITEMS:
        return VoiceCommandHelpInitiator.voiceCommandFindItems.build();
      case PAGE_ID_VOICE_COMMAND_FIND_ITEMS_FOR_WATCH:
        return VoiceCommandHelpInitiator.voiceCommandFindItemsForWatch.build();
      case PAGE_ID_VOICE_COMMAND_TEXT_EDITING:
        return VoiceCommandHelpInitiator.voiceCommandTextEditing.build();
      case PAGE_ID_VOICE_COMMAND_DEVICE_NAVIGATION:
        return VoiceCommandHelpInitiator.voiceCommandDeviceNavigation.build();
      case PAGE_ID_VOICE_COMMAND_OTHER_COMMANDS:
        return VoiceCommandHelpInitiator.voiceCommandOtherCommands.build();
      case PAGE_ID_UPDATE_WELCOME_13_0:
        return OnboardingInitiator.UPDATE_WELCOME_13_0.build();
      case PAGE_ID_UPDATE_WELCOME_13_0_PRE_R:
        return OnboardingInitiator.UPDATE_WELCOME_13_0_PRE_R.build();
      case PAGE_ID_SUPPORT_FOR_BRAILLE_DISPLAYS:
        return OnboardingInitiator.SUPPORT_FOR_BRAILLE_DISPLAYS.build();
      case PAGE_ID_ACTIONS_IN_READING_CONTROLS:
        return OnboardingInitiator.ACTIONS_IN_READING_CONTROLS.build();
      case PAGE_ID_DESCRIBE_ICONS:
        return OnboardingInitiator.DESCRIBE_ICONS.build();
      case PAGE_ID_TRY_LOOKOUT_AND_TALKBACK:
        return OnboardingInitiator.TRY_LOOKOUT_AND_TALKBACK.build();
      case PAGE_ID_UNKNOWN:
      case PAGE_ID_FINISHED:
      default:
        return null;
    }
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

  public static Builder builder(PageId pageId, @StringRes int pageName) {
    return new Builder(pageId, pageName);
  }

  /** Builder for page. */
  public static class Builder {

    private final PageId pageId;
    @StringRes private final int pageName;
    private final List<PageContentConfig> contents = new ArrayList<>();
    private final Map<Integer, Integer> captureGestureIdToAnnouncements = new HashMap<>();
    private boolean isOnlyOneFocus;
    private boolean hasNavigationButtonBar = true;
    private boolean showPageNumber = true;
    private boolean isEndOfSection = false;

    private Builder(PageId pageId, @StringRes int pageName) {
      this.pageId = pageId;
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
    public Builder addText(@StringRes int textResId) {
      this.contents.add(new Text(Paragraph.builder(textResId).build()));
      return this;
    }

    /** Adds one or multiple lines text with placeholders to the page. */
    public Builder addText(@StringRes int textResId, ImmutableList<Integer> textArgResIds) {
      this.contents.add(
          new Text(Paragraph.builder(textResId).setTextArgResIds(textArgResIds).build()));
      return this;
    }

    /**
     * Adds one or multiple lines text to the page, including a actual gesture. If no gesture
     * assigned, adds default text to the page.
     */
    public Builder addTextWithActualGesture(
        @StringRes int textWithActualGestureResId, int actionKey, @StringRes int defaultTextResId) {
      this.contents.add(
          new Text(
              Paragraph.builder(defaultTextResId)
                  .setTextWithActualGestureResId(textWithActualGestureResId)
                  .setActionKey(actionKey)
                  .build()));
      return this;
    }

    /** Adds a text with a bullet point with a predicate. */
    public Builder addTextWithBullet(@StringRes int textResId, PageContentPredicate predicate) {
      Text text = new Text(Paragraph.builder(textResId).setBulletPoint(true).build());
      text.setShowingPredicate(predicate);
      this.contents.add(text);
      return this;
    }

    /** Adds a text with a bullet point. */
    public Builder addTextWithBullet(@StringRes int textResId) {
      this.contents.add(new Text(Paragraph.builder(textResId).setBulletPoint(true).build()));
      return this;
    }

    /** Adds a text and a bullet item which have to be focused at the same time. */
    public Builder addTextAndBullet(@StringRes int textResId, @StringRes int textWithBulletResId) {
      this.contents.add(
          new Text(
              Paragraph.builder(textResId).build(),
              Paragraph.builder(textWithBulletResId).setBulletPoint(true).build()));
      return this;
    }

    /** Adds sub-text without margin between texts. */
    public Builder addSubText(@StringRes int textResId) {
      this.contents.add(new Text(Paragraph.builder(textResId).setSubText(true).build()));
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
              Paragraph.builder(defaultTextResId)
                  .setTextWithActualGestureResId(textWithActualGestureResId)
                  .setActionKey(actionKey)
                  .setBulletPoint(true)
                  .build()));
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

    /** Adds a button to the page. */
    public Builder addButton(@StringRes int textResId, PageButtonOnClickListener onClickListener) {
      this.contents.add(new PageButton(textResId, onClickListener));
      return this;
    }

    /** Adds a text with an empty {@link URLSpan}. */
    public Builder addTextWithLink(@StringRes int textResId) {
      this.contents.add(new Text(Paragraph.builder(textResId).setLink(true).build()));
      return this;
    }

    /** Adds a text with an {@link URLSpan}. */
    public Builder addTextWithLink(@StringRes int textResId, String urlLink) {
      this.contents.add(
          new Text(Paragraph.builder(textResId).setLink(true).setUrlLink(urlLink).build()));
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
          this.pageId,
          this.pageName,
          ImmutableList.copyOf(this.contents),
          ImmutableMap.copyOf(this.captureGestureIdToAnnouncements),
          isOnlyOneFocus,
          hasNavigationButtonBar,
          showPageNumber,
          isEndOfSection);
    }
  }

  /** Checks if any accessibility shortcut or button is enabled. */
  public static boolean isAccessibilityShortcutOrButtonEnabled(Context context) {
    return !TextUtils.isEmpty(
        Settings.Secure.getString(context.getContentResolver(), ACCESSIBILITY_BUTTON_TARGETS));
  }

  /** Checks if any accessibility button is floating on the display. */
  public static boolean isAccessibilityFloatingButtonEnabled(Context context) {
    return Settings.Secure.getInt(
            context.getContentResolver(),
            ACCESSIBILITY_BUTTON_MODE,
            /* def= */ ACCESSIBILITY_BUTTON_MODE_NAVIGATION_BAR)
        == ACCESSIBILITY_BUTTON_MODE_FLOATING_MENU;
  }
}
