package com.google.android.accessibility.talkback.training;

import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.training.content.PageContentConfig;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_PageConfig extends PageConfig {

  private final PageConfig.PageId getPageId;

  private final int getPageName;

  private final ImmutableList<PageContentConfig> getContents;

  private final ImmutableMap<Integer, Integer> getCaptureGestureIdToAnnouncements;

  private final boolean isOnlyOneFocus;

  private final boolean hasNavigationButtonBar;

  private final boolean showPageNumber;

  private final boolean isEndOfSection;

  AutoValue_PageConfig(
      PageConfig.PageId getPageId,
      int getPageName,
      ImmutableList<PageContentConfig> getContents,
      ImmutableMap<Integer, Integer> getCaptureGestureIdToAnnouncements,
      boolean isOnlyOneFocus,
      boolean hasNavigationButtonBar,
      boolean showPageNumber,
      boolean isEndOfSection) {
    if (getPageId == null) {
      throw new NullPointerException("Null getPageId");
    }
    this.getPageId = getPageId;
    this.getPageName = getPageName;
    if (getContents == null) {
      throw new NullPointerException("Null getContents");
    }
    this.getContents = getContents;
    if (getCaptureGestureIdToAnnouncements == null) {
      throw new NullPointerException("Null getCaptureGestureIdToAnnouncements");
    }
    this.getCaptureGestureIdToAnnouncements = getCaptureGestureIdToAnnouncements;
    this.isOnlyOneFocus = isOnlyOneFocus;
    this.hasNavigationButtonBar = hasNavigationButtonBar;
    this.showPageNumber = showPageNumber;
    this.isEndOfSection = isEndOfSection;
  }

  @Override
  public PageConfig.PageId getPageId() {
    return getPageId;
  }

  @StringRes
  @Override
  public int getPageName() {
    return getPageName;
  }

  @Override
  public ImmutableList<PageContentConfig> getContents() {
    return getContents;
  }

  @Override
  public ImmutableMap<Integer, Integer> getCaptureGestureIdToAnnouncements() {
    return getCaptureGestureIdToAnnouncements;
  }

  @Override
  public boolean isOnlyOneFocus() {
    return isOnlyOneFocus;
  }

  @Override
  public boolean hasNavigationButtonBar() {
    return hasNavigationButtonBar;
  }

  @Override
  public boolean showPageNumber() {
    return showPageNumber;
  }

  @Override
  public boolean isEndOfSection() {
    return isEndOfSection;
  }

  @Override
  public String toString() {
    return "PageConfig{"
        + "getPageId=" + getPageId + ", "
        + "getPageName=" + getPageName + ", "
        + "getContents=" + getContents + ", "
        + "getCaptureGestureIdToAnnouncements=" + getCaptureGestureIdToAnnouncements + ", "
        + "isOnlyOneFocus=" + isOnlyOneFocus + ", "
        + "hasNavigationButtonBar=" + hasNavigationButtonBar + ", "
        + "showPageNumber=" + showPageNumber + ", "
        + "isEndOfSection=" + isEndOfSection
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof PageConfig) {
      PageConfig that = (PageConfig) o;
      return this.getPageId.equals(that.getPageId())
          && this.getPageName == that.getPageName()
          && this.getContents.equals(that.getContents())
          && this.getCaptureGestureIdToAnnouncements.equals(that.getCaptureGestureIdToAnnouncements())
          && this.isOnlyOneFocus == that.isOnlyOneFocus()
          && this.hasNavigationButtonBar == that.hasNavigationButtonBar()
          && this.showPageNumber == that.showPageNumber()
          && this.isEndOfSection == that.isEndOfSection();
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= getPageId.hashCode();
    h$ *= 1000003;
    h$ ^= getPageName;
    h$ *= 1000003;
    h$ ^= getContents.hashCode();
    h$ *= 1000003;
    h$ ^= getCaptureGestureIdToAnnouncements.hashCode();
    h$ *= 1000003;
    h$ ^= isOnlyOneFocus ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= hasNavigationButtonBar ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= showPageNumber ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= isEndOfSection ? 1231 : 1237;
    return h$;
  }

}
