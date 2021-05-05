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

import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.List;

/**
 * Manages training page. Goes to next or previous page, and links from the index page.
 *
 * <p>This class is only for use within TrainingActivity.
 */
public class PageController {

  /** A callback to observe pages changes. */
  public interface OnPageChangeCallback {
    /** The method will be invoked when the page is switched. */
    void onPageSwitched(int pageNumber, @Nullable Pair<Integer, Integer> shownPageNumber);
  }

  private static final String TAG = "PageController";
  public static final int UNKNOWN_PAGE_NUMBER = -1;
  public static final int UNKNOWN_PAGE_NAME_RES_ID = -1;

  private final TrainingConfig training;
  private final OnPageChangeCallback onPageChangeCallback;
  private int currentPageNumber = UNKNOWN_PAGE_NUMBER;
  @Nullable private SectionInfo sectionInfo;

  public PageController(TrainingConfig training, OnPageChangeCallback onPageChangeCallback) {
    this.training = training;
    this.onPageChangeCallback = onPageChangeCallback;
  }

  /** Shows the first page. */
  public void initialize() {
    switchPage(0);
  }

  /** Goes to the specific page. */
  @VisibleForTesting
  public void switchPage(int targetPageNumber) {
    if (targetPageNumber < 0 || targetPageNumber >= getPageSize()) {
      throw new IllegalArgumentException(
          "Out of training range. pageNumber="
              + targetPageNumber
              + ", trainingSize="
              + training.getPages().size()
              + ", sectionInfo="
              + sectionInfo);
    }
    currentPageNumber = targetPageNumber;
    onPageChangeCallback.onPageSwitched(currentPageNumber, getShownPageNumber());
  }

  /**
   * Goes to the beginning page in a section and goes back to the current page after finishing
   * reading the last page in the section.
   */
  public void handleLink(@StringRes int firstPageInSectionNameResId) {
    int firstPageNumberInSection = findPageNumberByName(firstPageInSectionNameResId);
    if (firstPageNumberInSection < 0) {
      LogUtils.e(TAG, "Invalid section info. firstPageNumberInSection=" + firstPageNumberInSection);
      return;
    }

    // Gets a total number of pages in the section.
    List<PageConfig> pages = training.getPages();
    int totalNumber = 0;
    for (int i = firstPageNumberInSection; i < pages.size(); i++) {
      totalNumber++;
      if (pages.get(i).isEndOfSection()) {
        break;
      }
    }

    sectionInfo = new SectionInfo(currentPageNumber, firstPageNumberInSection, totalNumber);
    switchPage(firstPageNumberInSection);
  }

  /** Goes back to the previous page. Returns false if there is no previous page. */
  public boolean previousPage() {
    if (isFirstPage()) {
      return false;
    }

    if (sectionInfo != null && sectionInfo.isFirstPageInSection(currentPageNumber)) {
      // Goes back to the index page when clicking back button on the first in a section.
      // Clears section info before swiping page to avoids the back button is shown on the index
      // page which is the first page.
      int indexPageNumber = sectionInfo.indexPageNumber;
      sectionInfo = null;
      switchPage(indexPageNumber);
    } else {
      // Goes to the previous page.
      switchPage(currentPageNumber - 1);
    }
    return true;
  }

  /** Goes to the next page. Returns false if there is no next page. */
  public boolean nextPage() {
    if (isLastPage()) {
      return false;
    }

    // Goes to the next page.
    switchPage(currentPageNumber + 1);
    return true;
  }

  /** Goes back to the index page. Returns false if there is no index page. */
  public boolean backToLinkIndexPage() {
    if (sectionInfo == null) {
      return false;
    }

    int indexPageNumber = sectionInfo.indexPageNumber;
    // Clears section info before swiping page to avoids the back button is shown on the index page
    // which is the first page.
    sectionInfo = null;
    switchPage(indexPageNumber);
    return true;
  }

  /**
   * Goes back to the index page if it exists. Otherwise, goes back to the previous page. The method
   * is for the back gesture and the back key.
   */
  public boolean handleBackPressed() {
    return sectionInfo == null ? previousPage() : backToLinkIndexPage();
  }

  private int findPageNumberByName(@StringRes int pageNameResId) {
    if (pageNameResId == UNKNOWN_PAGE_NAME_RES_ID) {
      return UNKNOWN_PAGE_NUMBER;
    }

    List<PageConfig> pages = training.getPages();
    for (int i = 0; i < pages.size(); i++) {
      if (pages.get(i).getPageName() == pageNameResId) {
        return i;
      }
    }
    return UNKNOWN_PAGE_NUMBER;
  }

  private @Nullable Pair<Integer, Integer> getShownPageNumber() {
    PageConfig targetPage = training.getPages().get(currentPageNumber);
    int pageNumber = currentPageNumber;
    int totalNumber = training.getTotalPageNumber();
    if (sectionInfo != null) {
      pageNumber = sectionInfo.getCurrentPageNumberInSection(currentPageNumber);
      totalNumber = sectionInfo.getTotalPageNumberInSection();
    }

    // Don't show page number if showPageNumber flag is false or there is only one page in training.
    if (targetPage.showPageNumber() && totalNumber > 1) {
      return Pair.create(pageNumber + 1, totalNumber);
    }
    return null;
  }

  /**
   * Returns true if the current page is the first page. Always returns false for the page in a
   * section.
   */
  public boolean isFirstPage() {
    return sectionInfo == null && currentPageNumber == 0;
  }

  /** Returns true if the current page is the last page. */
  public boolean isLastPage() {
    return (currentPageNumber == getPageSize() - 1)
        || training.getPages().get(currentPageNumber).isEndOfSection();
  }

  /** Returns true if there is only one page in the training or in a section. */
  public boolean isOnePage() {
    return getPageSize() == 1 || (sectionInfo != null && sectionInfo.totalPageNumber == 1);
  }

  public int getCurrentPageNumber() {
    return currentPageNumber;
  }

  private int getPageSize() {
    return training == null ? 0 : training.getPages().size();
  }

  /**
   * Records a section that links from the index page to the first page and go back to the index
   * page from the last page.
   */
  private static class SectionInfo {
    private final int indexPageNumber;
    private final int firstPageNumber;
    private final int totalPageNumber;

    private SectionInfo(int indexPageNumber, int firstPageNumber, int totalPageNumber) {
      this.indexPageNumber = indexPageNumber;
      this.firstPageNumber = firstPageNumber;
      this.totalPageNumber = totalPageNumber;
    }

    private boolean isFirstPageInSection(int currentPageNumber) {
      return currentPageNumber == firstPageNumber;
    }

    private int getCurrentPageNumberInSection(int currentPageNumber) {
      return currentPageNumber - firstPageNumber;
    }

    private int getTotalPageNumberInSection() {
      return totalPageNumber;
    }

    @Override
    public String toString() {
      return "SectionInfo{"
          + "indexPageNumber="
          + indexPageNumber
          + ", firstPageNumber="
          + firstPageNumber
          + ", totalPageNumber="
          + totalPageNumber
          + '}';
    }
  }
}
