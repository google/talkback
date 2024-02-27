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

package com.google.android.accessibility.talkback.trainingcommon.tv;

import static com.google.android.accessibility.talkback.trainingcommon.NavigationButtonBar.BUTTON_TYPE_BACK;
import static com.google.android.accessibility.talkback.trainingcommon.NavigationButtonBar.BUTTON_TYPE_EXIT;
import static com.google.android.accessibility.talkback.trainingcommon.NavigationButtonBar.BUTTON_TYPE_NEXT;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_TV_OVERVIEW;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_TV_REMOTE;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_TV_SHORTCUT;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_TV_VENDOR;

import android.content.Context;
import android.os.Build;
import androidx.annotation.Nullable;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId;
import com.google.android.accessibility.talkback.trainingcommon.TrainingConfig;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Sets up the TalkBack tutorial content on TV. */
public class TvTutorialInitiator {

  @Nullable
  public static PageConfig getPageConfigForDefaultPage(
      @Nullable Context context, @Nullable VendorConfig vendorConfig, PageId pageId) {
    PageConfig.Builder builder = getDefaultPageBuilders(context, vendorConfig).get(pageId);
    if (builder == null) {
      return null;
    }
    return builder.build();
  }

  @Nullable
  public static PageConfig getPageConfigForVendorPage(
      @Nullable VendorConfig vendorConfig, int vendorPageIndex) {
    try {
      PageConfig.Builder builder = getVendorPageBuilders(vendorConfig).get(vendorPageIndex);
      return builder.build();
    } catch (IndexOutOfBoundsException e) {
      return null;
    }
  }

  /**
   * Returns whether the tutorial should be shown or not.
   *
   * <p>The tutorial contains device-specific (more precisely remote control-specific) information.
   * The default content is written for google devices (Chromecast). Other vendors must include
   * their own config in the system image or the tutorial does not make sense to be shown.
   */
  public static boolean shouldShowTraining(@Nullable VendorConfig vendorConfig) {
    return (vendorConfig != null) || isDeviceWithChromecastRemote() || isDeveloperDevice();
  }

  private static boolean isDeviceWithChromecastRemote() {
    return Build.BRAND.equals("google")
        && (Build.PRODUCT.equals("sabrina") || Build.PRODUCT.equals("boreal"));
  }

  private static boolean isDeveloperDevice() {
    return Build.BRAND.equals("ADT-3") || Build.BRAND.equals("ADT-4");
  }

  @Nullable
  public static TrainingConfig getTraining(
      @Nullable Context context, @Nullable VendorConfig vendorConfig) {
    if (context == null || !shouldShowTraining(vendorConfig)) {
      return null;
    }

    return TrainingConfig.builder(R.string.talkback_tutorial_title)
        .addPages(
            getDefaultPageBuilders(context, vendorConfig)
                .values()
                .toArray(new PageConfig.Builder[0]))
        .addPages(getVendorPageBuilders(vendorConfig).toArray(new PageConfig.Builder[0]))
        .setButtons(ImmutableList.of(BUTTON_TYPE_BACK, BUTTON_TYPE_NEXT, BUTTON_TYPE_EXIT))
        .setExitButtonOnlyShowOnLastPage(true)
        .setPrevButtonShownOnFirstPage(true)
        .build();
  }

  private static LinkedHashMap<PageId, PageConfig.Builder> getDefaultPageBuilders(
      Context context, VendorConfig vendorConfig) {
    LinkedHashMap<PageId, PageConfig.Builder> defaultPageBuilders = new LinkedHashMap<>();
    getOverviewPageIfEnabled(context, vendorConfig)
        .ifPresent(page -> defaultPageBuilders.put(PAGE_ID_TV_OVERVIEW, page));
    getRemotePageIfEnabled(context, vendorConfig)
        .ifPresent(page -> defaultPageBuilders.put(PAGE_ID_TV_REMOTE, page));
    getShortcutPageIfEnabled(context, vendorConfig)
        .ifPresent(page -> defaultPageBuilders.put(PAGE_ID_TV_SHORTCUT, page));
    return defaultPageBuilders;
  }

  private static List<PageConfig.Builder> getVendorPageBuilders(
      @Nullable VendorConfig vendorConfig) {
    List<PageConfig.Builder> vendorPages = new ArrayList<>();
    if (vendorConfig != null) {
      int index = 0;
      for (TvPageConfig config : vendorConfig.vendorPages()) {
        PageConfig.Builder pageBuilder = tvPageConfigToPageConfigBuilder(PAGE_ID_TV_VENDOR, config);
        pageBuilder.setVendorPageIndex(index++);
        vendorPages.add(pageBuilder);
      }
    }
    return vendorPages;
  }

  private static Optional<PageConfig.Builder> getOverviewPageIfEnabled(
      Context context, @Nullable VendorConfig vendorConfig) {
    TvPageConfig defaultContent =
        TvPageConfig.builder()
            .setTitle(context.getString(R.string.tv_training_overview_step_title))
            .setSummary(context.getString(R.string.tv_training_overview_step_summary))
            .build();
    return customizeAndCreatePageIfEnabled(vendorConfig, defaultContent, PAGE_ID_TV_OVERVIEW);
  }

  private static Optional<PageConfig.Builder> getRemotePageIfEnabled(
      Context context, @Nullable VendorConfig vendorConfig) {
    TvPageConfig defaultContent =
        TvPageConfig.builder()
            .setTitle(context.getString(R.string.tv_training_remote_step_title))
            .setSummary(context.getString(R.string.tv_training_remote_step_summary))
            .build();
    return customizeAndCreatePageIfEnabled(vendorConfig, defaultContent, PAGE_ID_TV_REMOTE);
  }

  private static Optional<PageConfig.Builder> getShortcutPageIfEnabled(
      Context context, @Nullable VendorConfig vendorConfig) {
    TvPageConfig defaultContent =
        TvPageConfig.builder()
            .setTitle(context.getString(R.string.tv_training_shortcut_step_title))
            .setSummary(context.getString(R.string.tv_training_shortcut_step_summary))
            .build();
    return customizeAndCreatePageIfEnabled(vendorConfig, defaultContent, PAGE_ID_TV_SHORTCUT);
  }

  private static Optional<PageConfig.Builder> customizeAndCreatePageIfEnabled(
      @Nullable VendorConfig vendorConfig, TvPageConfig config, PageId pageId) {
    if (vendorConfig != null) {
      TvPageConfig customization = vendorConfig.defaultPages().get(pageId);
      if (customization != null) {
        config = TvPageConfig.combine(config, customization);
      }
    }
    return config.enabled()
        ? Optional.of(tvPageConfigToPageConfigBuilder(pageId, config))
        : Optional.empty();
  }

  private static PageConfig.Builder tvPageConfigToPageConfigBuilder(
      PageId pageId, TvPageConfig tvPageConfig) {
    return PageConfig.builder(pageId, tvPageConfig.title())
        .setOnlyOneFocus(true)
        .setImage(tvPageConfig.image())
        .addText(Objects.requireNonNull(tvPageConfig.summary()));
  }
}
