package com.google.android.accessibility.talkback.training;

import androidx.annotation.StringRes;
import com.google.common.collect.ImmutableList;
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_TrainingConfig extends TrainingConfig {

  private final int name;

  private final ImmutableList<PageConfig> pages;

  private final ImmutableList<Integer> buttons;

  private final boolean exitButtonOnlyShowOnLastPage;

  private final boolean supportNavigateUpArrow;

  AutoValue_TrainingConfig(
      int name,
      ImmutableList<PageConfig> pages,
      ImmutableList<Integer> buttons,
      boolean exitButtonOnlyShowOnLastPage,
      boolean supportNavigateUpArrow) {
    this.name = name;
    if (pages == null) {
      throw new NullPointerException("Null pages");
    }
    this.pages = pages;
    if (buttons == null) {
      throw new NullPointerException("Null buttons");
    }
    this.buttons = buttons;
    this.exitButtonOnlyShowOnLastPage = exitButtonOnlyShowOnLastPage;
    this.supportNavigateUpArrow = supportNavigateUpArrow;
  }

  @StringRes
  @Override
  public int getName() {
    return name;
  }

  @Override
  public ImmutableList<PageConfig> getPages() {
    return pages;
  }

  @Override
  public ImmutableList<Integer> getButtons() {
    return buttons;
  }

  @Override
  public boolean isExitButtonOnlyShowOnLastPage() {
    return exitButtonOnlyShowOnLastPage;
  }

  @Override
  public boolean isSupportNavigateUpArrow() {
    return supportNavigateUpArrow;
  }

  @Override
  public String toString() {
    return "TrainingConfig{"
        + "name=" + name + ", "
        + "pages=" + pages + ", "
        + "buttons=" + buttons + ", "
        + "exitButtonOnlyShowOnLastPage=" + exitButtonOnlyShowOnLastPage + ", "
        + "supportNavigateUpArrow=" + supportNavigateUpArrow
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof TrainingConfig) {
      TrainingConfig that = (TrainingConfig) o;
      return this.name == that.getName()
          && this.pages.equals(that.getPages())
          && this.buttons.equals(that.getButtons())
          && this.exitButtonOnlyShowOnLastPage == that.isExitButtonOnlyShowOnLastPage()
          && this.supportNavigateUpArrow == that.isSupportNavigateUpArrow();
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= name;
    h$ *= 1000003;
    h$ ^= pages.hashCode();
    h$ *= 1000003;
    h$ ^= buttons.hashCode();
    h$ *= 1000003;
    h$ ^= exitButtonOnlyShowOnLastPage ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= supportNavigateUpArrow ? 1231 : 1237;
    return h$;
  }

}
