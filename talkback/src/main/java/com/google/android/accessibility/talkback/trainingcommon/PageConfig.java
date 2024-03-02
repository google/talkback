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

package com.google.android.accessibility.talkback.trainingcommon;

import static com.google.android.accessibility.talkback.trainingcommon.content.PageContentConfig.UNKNOWN_RESOURCE_ID;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.FingerprintGestureController;
import android.content.Context;
import android.os.Message;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.style.URLSpan;
import androidx.annotation.ArrayRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.FeatureFlagReader;
import com.google.android.accessibility.talkback.trainingcommon.TrainingIpcClient.ServiceData;
import com.google.android.accessibility.talkback.trainingcommon.content.Divider;
import com.google.android.accessibility.talkback.trainingcommon.content.EditTextBox;
import com.google.android.accessibility.talkback.trainingcommon.content.ExitBanner;
import com.google.android.accessibility.talkback.trainingcommon.content.Heading;
import com.google.android.accessibility.talkback.trainingcommon.content.Link;
import com.google.android.accessibility.talkback.trainingcommon.content.Note;
import com.google.android.accessibility.talkback.trainingcommon.content.PageButton;
import com.google.android.accessibility.talkback.trainingcommon.content.PageButton.ButtonOnClickListener;
import com.google.android.accessibility.talkback.trainingcommon.content.PageContentConfig;
import com.google.android.accessibility.talkback.trainingcommon.content.Text;
import com.google.android.accessibility.talkback.trainingcommon.content.Text.Paragraph;
import com.google.android.accessibility.talkback.trainingcommon.content.Text.TextWithActualGestureParameter;
import com.google.android.accessibility.talkback.trainingcommon.content.TextList;
import com.google.android.accessibility.talkback.trainingcommon.content.TextWithIcon;
import com.google.android.accessibility.talkback.trainingcommon.content.TextWithNumber;
import com.google.android.accessibility.talkback.trainingcommon.content.Tip;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
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
    SUPPORT_SYSTEM_ACTIONS((data) -> FeatureSupport.supportGetSystemActions(data.getContext())),

    SUPPORT_EXIT_BANNER(
        (data) ->
            data.shouldShowTrainingExitBanner()
                && FeatureFlagReader.supportShowExitBanner(data.getContext())),

    IMAGE_DESCRIPTION_UNAVAILABLE(ServiceData::isImageDescriptionUnavailable),
    ICON_DETECTION_AND_IMAGE_DESCRIPTION_UNAVAILABLE(
        (data) -> data.isIconDetectionUnavailable() && data.isImageDescriptionUnavailable()),
    ICON_DETECTION_AVAILABLE_BUT_IMAGE_DESCRIPTION_UNAVAILABLE(
        (data) -> !data.isIconDetectionUnavailable() && data.isImageDescriptionUnavailable());

    @Immutable
    private interface ImmutablePredicate extends Predicate<ServiceData> {}

    private final ImmutablePredicate predicate;

    PageContentPredicate(ImmutablePredicate predicate) {
      this.predicate = predicate;
    }

    public boolean test(ServiceData data) {
      return data != null && predicate.test(data);
    }
  }

  /** Unique identifiers for training pages. */
  public enum PageId {
    PAGE_ID_UNKNOWN,
    PAGE_ID_FINISHED,
    // For multi-finger gestures.
    PAGE_ID_WELCOME_TO_UPDATED_TALKBACK_FOR_MULTIFINGER_GESTURES,
    // For TalkBack tutorial.
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
    PAGE_ID_ADDITIONAL_TIPS_MAKING_CALLS,
    PAGE_ID_ADDITIONAL_TIPS_SENDING_MESSAGES,
    PAGE_ID_ADDITIONAL_TIPS_READING_WEB_EMAILS,
    PAGE_ID_ADDITIONAL_TIPS_LOOKOUT,
    // For TB 14.1.
    PAGE_ID_UPDATE_WELCOME,
    PAGE_ID_DESCRIBE_IMAGES,
    PAGE_ID_SPELL_CHECK_FOR_BRAILLE_KEYBOARD,
    PAGE_ID_AUTO_SCROLL_FOR_BRAILLE_DISPLAY,
    PAGE_ID_NEW_BRAILLE_SHORTCUTS_AND_LANGUAGES,
    // For Watch.
    PAGE_ID_WELCOME_TO_TALKBACK_WATCH,
    PAGE_ID_WATCH_SCROLLING,
    PAGE_ID_WATCH_GO_BACK,
    PAGE_ID_WATCH_VOLUME_UP,
    PAGE_ID_WATCH_VOLUME_DOWN,
    PAGE_ID_WATCH_OPEN_TALKBACK_MENU,
    PAGE_ID_WATCH_END_TUTORIAL,
    // For TV.
    PAGE_ID_TV_OVERVIEW,
    PAGE_ID_TV_REMOTE,
    PAGE_ID_TV_SHORTCUT,
    PAGE_ID_TV_VENDOR,
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

  public static final int UNKNOWN_PAGE_INDEX = -1;

  public abstract PageId getPageId();

  @StringRes
  public abstract int getPageNameResId();

  @Nullable
  public abstract String getPageNameString();

  public abstract int getVendorPageIndex();

  public abstract ImmutableList<PageContentConfig> getContents();

  @Nullable
  public abstract ImmutableList<Integer> getNavigationButtons();

  @Nullable
  public abstract ExternalDrawableResource getImage();

  /**
   * The gestures will be captured by {@link TrainingFragment}. Mapping from gesture ID to
   * announcement resource ID.
   */
  public abstract ImmutableMap<Integer, Integer> getCaptureGestureIdToAnnouncements();

  /**
   * The fingerprint gestures will be captured by {@link TrainingFragment}. Mapping from fingerprint
   * gesture ID to announcement resource ID.
   */
  public abstract ImmutableMap<Integer, Integer> getCaptureFingerprintGestureIdToAnnouncements();

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

  /**
   * Returns {@link TrainingSwipeDismissListener} if the page can use the gesture to swipe right
   * with 2-fingers to go back to the previous page. This function only supports in the wear.
   */
  @Nullable
  public abstract TrainingSwipeDismissListener getSwipeDismissListener();

  /** Returns the extra dp value for the title's top margin. */
  public abstract int getExtraTitleMarginTop();

  /** Returns the extra dp value for the navigation button's top margin. */
  public abstract int getExtraNavigationButtonMarginTop();

  /** Returns true if we want to clear the horizontal margin of the title. */
  public abstract boolean clearTitleHorizontalMargin();

  private static PageConfig create(
      PageId pageId,
      @StringRes int pageNameResId,
      String pageNameString,
      int vendorPageIndex,
      ImmutableList<PageContentConfig> contents,
      @Nullable ImmutableList<Integer> navigationButtons,
      @Nullable ExternalDrawableResource image,
      ImmutableMap<Integer, Integer> gestures,
      ImmutableMap<Integer, Integer> fingerprintGestures,
      boolean isOnlyOneFocus,
      boolean hasNavigationButtonBar,
      boolean showPageNumber,
      boolean isEndOfSection,
      @Nullable TrainingSwipeDismissListener swipeDismissListener,
      int extraTitleMarginTop,
      int extraNavigationButtonMarginTop,
      boolean clearTitleHorizontalMargin) {
    return new AutoValue_PageConfig(
        pageId,
        pageNameResId,
        pageNameString,
        vendorPageIndex,
        contents,
        navigationButtons,
        image,
        gestures,
        fingerprintGestures,
        isOnlyOneFocus,
        hasNavigationButtonBar,
        showPageNumber,
        isEndOfSection,
        swipeDismissListener,
        extraTitleMarginTop,
        extraNavigationButtonMarginTop,
        clearTitleHorizontalMargin);
  }

  @Nullable
  public static PageConfig getPage(PageId pageId, Context context, int vendorPageIndex) {
    return TrainingActivityInterfaceInjector.getInstance()
        .getPage(pageId, context, vendorPageIndex);
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
    ImmutableMap<Integer, Integer> gestures = getCaptureGestureIdToAnnouncements();
    if (gestures.isEmpty()) {
      return UNKNOWN_ANNOUNCEMENT;
    }

    @Nullable Integer announcement = gestures.get(gestureId);
    return announcement == null ? UNKNOWN_ANNOUNCEMENT : announcement;
  }

  public String getPageNameFromStringOrRes(Context context) {
    return (getPageNameString() != null)
        ? getPageNameString()
        : context.getString(getPageNameResId());
  }

  public static Builder builder(PageId pageId, @StringRes int pageName) {
    return new Builder(pageId, pageName);
  }

  public static Builder builder(PageId pageId, @Nullable String pageTitle) {
    return new Builder(pageId, pageTitle);
  }

  /** Builder for page. */
  public static class Builder {

    private final PageId pageId;
    @StringRes private final int pageNameResId;
    private final String pageNameString;
    private final List<PageContentConfig> contents = new ArrayList<>();
    @Nullable private List<Integer> navigationButtons;
    private final Map<Integer, Integer> captureGestureIdToAnnouncements = new HashMap<>();
    private final Map<Integer, Integer> captureFingerprintGestureIdToAnnouncements =
        new HashMap<>();
    @Nullable private ExternalDrawableResource image;
    private int vendorPageIndex = UNKNOWN_PAGE_INDEX;
    private boolean isOnlyOneFocus;
    private boolean hasNavigationButtonBar = true;
    private boolean showPageNumber = true;
    private boolean isEndOfSection = false;
    @Nullable private TrainingSwipeDismissListener swipeDismissListener;

    private int extraTitleMarginTop;
    private int extraNavigationButtonMarginTop;
    private boolean clearTitleHorizontalMargin;

    private Builder(PageId pageId, @StringRes int pageNameResId) {
      this.pageId = pageId;
      this.pageNameResId = pageNameResId;
      this.pageNameString = null;
      this.swipeDismissListener = null;
    }

    private Builder(PageId pageId, @Nullable String pageNameString) {
      this.pageId = pageId;
      this.pageNameResId = PageContentConfig.UNKNOWN_RESOURCE_ID;
      this.pageNameString = pageNameString;
      this.swipeDismissListener = null;
    }

    @CanIgnoreReturnValue
    public Builder setTitleExtraMarginTop(int dp) {
      extraTitleMarginTop = dp;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder clearTitleHorizontalMargin() {
      clearTitleHorizontalMargin = true;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setVendorPageIndex(int vendorPageIndex) {
      this.vendorPageIndex = vendorPageIndex;
      return this;
    }

    /** Sets true if the entire page is in the same focus. */
    @CanIgnoreReturnValue
    public Builder setOnlyOneFocus(boolean isOnlyOneFocus) {
      this.isOnlyOneFocus = isOnlyOneFocus;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder hideNavigationButtonBar() {
      this.hasNavigationButtonBar = false;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder hidePageNumber() {
      this.showPageNumber = false;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setNavigationButtons(@Nullable List<Integer> navigationButtons) {
      this.navigationButtons = navigationButtons;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setNavigationButtonExtraMarginTop(int dp) {
      extraNavigationButtonMarginTop = dp;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setImage(@Nullable ExternalDrawableResource image) {
      this.image = image;
      return this;
    }

    /**
     * If the page is an end page of a section, going back to the index page when clicking the
     * Finish button.
     */
    @CanIgnoreReturnValue
    public Builder setEndOfSection() {
      this.isEndOfSection = true;
      return this;
    }

    /** Adds a callback invoked while a user is performing swipe right gesture. */
    @CanIgnoreReturnValue
    public Builder setTrainingSwipeDismissListener(
        TrainingSwipeDismissListener swipeDismissListener) {
      this.swipeDismissListener = swipeDismissListener;
      return this;
    }

    /** Adds one or multiple lines text to the page. */
    @CanIgnoreReturnValue
    public Builder addText(@StringRes int textResId) {
      this.contents.add(new Text(Paragraph.builder(textResId).build()));
      return this;
    }

    /** Adds one or multiple lines text to the page. */
    @CanIgnoreReturnValue
    public Builder addText(String textString) {
      this.contents.add(new Text(Paragraph.builder(textString).build()));
      return this;
    }

    /** Adds one or multiple lines text to the page. */
    @CanIgnoreReturnValue
    public Builder addText(@StringRes int textResId, PageContentPredicate predicate) {
      Text text = new Text(Paragraph.builder(textResId).build());
      text.setShowingPredicate(predicate);
      this.contents.add(text);
      return this;
    }

    /** Adds one or multiple lines text with placeholders to the page. */
    @CanIgnoreReturnValue
    public Builder addText(@StringRes int textResId, ImmutableList<Integer> textArgResIds) {
      this.contents.add(
          new Text(Paragraph.builder(textResId).setTextArgResIds(textArgResIds).build()));
      return this;
    }

    /**
     * Adds one or multiple lines text to the page including a actual gesture.
     *
     * @param textParameters the first {@link Text} whose predicate is matched will be add to the
     *     page
     */
    @CanIgnoreReturnValue
    public Builder addTextWithActualGesture(
        ImmutableList<TextWithActualGestureParameter> textParameters) {
      Text next = null;
      for (int i = textParameters.size() - 1; i >= 0; i--) {
        TextWithActualGestureParameter textParameter = textParameters.get(i);
        Text text =
            new Text(
                Paragraph.builder(UNKNOWN_RESOURCE_ID)
                    .setTextWithActualGestureResId(textParameter.textWithActualGestureResId)
                    .setActionKey(textParameter.actionKey)
                    .setDefaultGestureResId(textParameter.defaultGestureResId)
                    .build());
        if (next != null) {
          text.setShowingPredicate(textParameter.predicate, next);
        }
        next = text;
      }
      this.contents.add(next);
      return this;
    }

    /** Adds a text with a bullet point with a predicate. */
    @CanIgnoreReturnValue
    public Builder addTextWithBullet(@StringRes int textResId, PageContentPredicate predicate) {
      Text text = new Text(Paragraph.builder(textResId).setBulletPoint(true).build());
      text.setShowingPredicate(predicate);
      this.contents.add(text);
      return this;
    }

    /** Adds a text with a bullet point. */
    @CanIgnoreReturnValue
    public Builder addTextWithBullet(@StringRes int textResId) {
      this.contents.add(new Text(Paragraph.builder(textResId).setBulletPoint(true).build()));
      return this;
    }

    /** Adds a text and a bullet item which have to be focused at the same time. */
    @CanIgnoreReturnValue
    public Builder addTextAndBullet(@StringRes int textResId, @StringRes int textWithBulletResId) {
      this.contents.add(
          new Text(
              Paragraph.builder(textResId).build(),
              Paragraph.builder(textWithBulletResId).setBulletPoint(true).build()));
      return this;
    }

    /** Adds sub-text without margin between texts. */
    @CanIgnoreReturnValue
    public Builder addSubText(@StringRes int textResId) {
      this.contents.add(new Text(Paragraph.builder(textResId).setSubText(true).build()));
      return this;
    }

    /**
     * Adds a text that starts with a bullet point and a gesture that should be replaced with an
     * actual gesture. If no gesture is assigned, adds default text to the page. .
     */
    @CanIgnoreReturnValue
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
    @CanIgnoreReturnValue
    public Builder addTextWithNumber(@StringRes int textResId, int number) {
      this.contents.add(new TextWithNumber(textResId, number));
      return this;
    }

    /** Adds icon and description to the page with a predicate. */
    @CanIgnoreReturnValue
    public Builder addTextWithIcon(
        @StringRes int textResId, @DrawableRes int srcResId, PageContentPredicate predicate) {
      TextWithIcon textWithIcon = new TextWithIcon(textResId, srcResId);
      textWithIcon.setShowingPredicate(predicate);
      this.contents.add(textWithIcon);
      return this;
    }

    /** Adds icon and description to the page. */
    @CanIgnoreReturnValue
    public Builder addTextWithIcon(@StringRes int textResId, @DrawableRes int srcResId) {
      this.contents.add(new TextWithIcon(textResId, srcResId));
      return this;
    }

    /** Adds icon and description to the page. */
    @CanIgnoreReturnValue
    public Builder addTextWithIcon(
        @StringRes int textResId, @StringRes int subtextResId, @DrawableRes int srcResId) {
      this.contents.add(new TextWithIcon(textResId, subtextResId, srcResId));
      return this;
    }

    /** Adds a heading. */
    @CanIgnoreReturnValue
    public Builder addHeading(@StringRes int headingResId) {
      this.contents.add(new Heading(headingResId));
      return this;
    }

    /** Adds a note to the page. */
    @CanIgnoreReturnValue
    public Builder addNote(@StringRes int textResId, PageContentPredicate predicate) {
      Note note = new Note(textResId);
      note.setShowingPredicate(predicate);
      this.contents.add(note);
      return this;
    }

    /** Adds a list to the page. */
    @CanIgnoreReturnValue
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
    @CanIgnoreReturnValue
    public Builder addLink(
        @StringRes int textResId,
        @StringRes int subtextResId,
        @DrawableRes int srcResId,
        @StringRes int firstPageInSectionNameResId) {
      contents.add(new Link(textResId, subtextResId, srcResId, firstPageInSectionNameResId));
      return this;
    }

    /** Adds an editable text box with content to the page. */
    @CanIgnoreReturnValue
    public Builder addEditTextWithContent(@StringRes int textResId) {
      this.contents.add(new EditTextBox(textResId, UNKNOWN_RESOURCE_ID));
      return this;
    }

    /** Adds an empty editable text box with a hint to the page. */
    @CanIgnoreReturnValue
    public Builder addEditTextWithHint(@StringRes int hintResId) {
      this.contents.add(new EditTextBox(UNKNOWN_RESOURCE_ID, hintResId));
      return this;
    }

    /** Adds a button to the page. */
    @CanIgnoreReturnValue
    public Builder addButton(@StringRes int textResId) {
      this.contents.add(new PageButton(textResId));
      return this;
    }

    /** Adds a button to the page. A message will be sent to TalkBack when the button is clicked. */
    @CanIgnoreReturnValue
    public Builder addButton(
        @StringRes int textResId, Message message, PageContentPredicate predicate) {
      PageButton button = new PageButton(textResId);
      button.setMessage(message);
      button.setShowingPredicate(predicate);
      this.contents.add(button);
      return this;
    }

    /**
     * Adds a button to the page with given {@link ButtonOnClickListener}. The common actions of
     * button are defined in {@link PageButton.PageButtonAction}
     */
    @CanIgnoreReturnValue
    public Builder addButton(
        @StringRes int textResId, ButtonOnClickListener buttonOnClickListener) {
      this.contents.add(new PageButton(textResId, buttonOnClickListener));
      return this;
    }

    /** Adds a TalkBack-exit banner to the page. */
    @CanIgnoreReturnValue
    public PageConfig.Builder addExitBanner(PageContentPredicate predicate) {
      ExitBanner exitBanner = new ExitBanner();
      exitBanner.setShowingPredicate(predicate);
      this.contents.add(exitBanner);
      return this;
    }

    /** Adds a text with an empty {@link URLSpan}. */
    @CanIgnoreReturnValue
    public Builder addTextWithLink(@StringRes int textResId) {
      this.contents.add(new Text(Paragraph.builder(textResId).setLink(true).build()));
      return this;
    }

    /** Adds a text with an {@link URLSpan}. */
    @CanIgnoreReturnValue
    public Builder addTextWithLink(@StringRes int textResId, String urlLink) {
      this.contents.add(
          new Text(Paragraph.builder(textResId).setLink(true).setUrlLink(urlLink).build()));
      return this;
    }

    /** Adds a divider to the page. */
    @CanIgnoreReturnValue
    public Builder addDivider() {
      this.contents.add(new Divider());
      return this;
    }

    /** Adds a tip to the page. */
    @CanIgnoreReturnValue
    public Builder addTip(@StringRes int textResId) {
      Tip tip = new Tip(textResId);
      this.contents.add(tip);
      return this;
    }

    /** Records the gesture ID, which will be captured, and associated announcements. */
    @CanIgnoreReturnValue
    public Builder captureGesture(int gestureId, @StringRes int announcementResId) {
      this.captureGestureIdToAnnouncements.put(gestureId, announcementResId);
      return this;
    }

    /** Captures all gestures, except swipe right, swipe left and double tap with 1 finger. */
    @CanIgnoreReturnValue
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
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD, ANNOUNCE_REAL_ACTION);

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
      this.captureGestureIdToAnnouncements.put(
          AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD, ANNOUNCE_REAL_ACTION);

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

      // Fingerprint gestures.
      this.captureFingerprintGestureIdToAnnouncements.put(
          FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_UP, ANNOUNCE_REAL_ACTION);
      this.captureFingerprintGestureIdToAnnouncements.put(
          FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_DOWN, ANNOUNCE_REAL_ACTION);
      this.captureFingerprintGestureIdToAnnouncements.put(
          FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_LEFT, ANNOUNCE_REAL_ACTION);
      this.captureFingerprintGestureIdToAnnouncements.put(
          FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_RIGHT, ANNOUNCE_REAL_ACTION);

      return this;
    }

    /** Creates a {@link PageConfig} */
    public PageConfig build() {
      return PageConfig.create(
          this.pageId,
          this.pageNameResId,
          this.pageNameString,
          this.vendorPageIndex,
          ImmutableList.copyOf(this.contents),
          this.navigationButtons == null ? null : ImmutableList.copyOf(this.navigationButtons),
          image,
          ImmutableMap.copyOf(this.captureGestureIdToAnnouncements),
          ImmutableMap.copyOf(this.captureFingerprintGestureIdToAnnouncements),
          isOnlyOneFocus,
          hasNavigationButtonBar,
          showPageNumber,
          isEndOfSection,
          swipeDismissListener,
          extraTitleMarginTop,
          extraNavigationButtonMarginTop,
          clearTitleHorizontalMargin);
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
