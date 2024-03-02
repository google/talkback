/*
 * Copyright (C) 2022 Google Inc.
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

import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_TV_OVERVIEW;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_TV_REMOTE;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_TV_SHORTCUT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparingInt;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.talkback.trainingcommon.ExternalDrawableResource;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Reads tutorial customization from an external package.
 *
 * <p>The main API of this class consists of the method {@link #retrieveConfig}.
 *
 * <p>The external package must implement a {@code BroadcastReceiver} that listens for an intent
 * with the name {@link #CUSTOMIZATION_INTENT}. Packages in the device's system image (having {@link
 * ApplicationInfo#FLAG_SYSTEM}) are preferred so that they can not easily be overridden by others.
 * The customization itself is done in a JSON file in the app's {@code res/raw} directory.
 */
public class VendorConfigReader {

  private static final String TAG = "VendorConfigReader";
  private static final String CUSTOMIZATION_INTENT =
      "com.google.android.accessibility.talkback.training.tv.CUSTOMIZATION";
  private static final String CUSTOMIZATION_JSON_NAME = "talkback_tutorial_config";

  // Cache the VendorConfig because reading it is expensive and it is needed for each page.
  private static final Map<Context, Optional<VendorConfig>> vendorConfigCache = new HashMap<>();

  static final ImmutableList<PageId> CUSTOMIZABLE_PAGES =
      ImmutableList.of(PAGE_ID_TV_OVERVIEW, PAGE_ID_TV_REMOTE, PAGE_ID_TV_SHORTCUT);

  static final class InvalidConfigException extends Exception {
    InvalidConfigException(String message) {
      super(message);
    }

    InvalidConfigException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  @AutoValue
  abstract static class VendorPackage {
    abstract String packageName();

    abstract Resources resources();

    static VendorPackage create(String packageName, Resources resources) {
      return new AutoValue_VendorConfigReader_VendorPackage(packageName, resources);
    }
  }

  private VendorConfigReader() {} // Prevent instantiation.

  /** Returns a {@link VendorConfig} if a valid vendor package is present and null otherwise. */
  @Nullable
  public static VendorConfig retrieveConfig(@Nullable Context context) {
    if (context == null) {
      return null;
    }

    if (vendorConfigCache.containsKey(context)) {
      return Objects.requireNonNull(vendorConfigCache.get(context)).orElse(null);
    }

    PackageManager packageManager = context.getPackageManager();
    List<ResolveInfo> packages = retrievePackages(packageManager);
    ImmutableList<ResolveInfo> sortedPackages = sortPackagesSystemFirst(packages);
    for (ResolveInfo receiver : sortedPackages) {
      if (receiver.activityInfo == null || receiver.activityInfo.applicationInfo == null) {
        continue;
      }
      String packageName = receiver.activityInfo.packageName;

      Resources resources;
      try {
        resources = packageManager.getResourcesForApplication(packageName);
      } catch (NameNotFoundException e) {
        LogUtils.e(TAG, "Failed to find resources for: %s", packageName);
        continue;
      }

      LogUtils.i(TAG, "Found customization package %s", packageName);
      VendorConfig config = retrieveConfigFromPackage(VendorPackage.create(packageName, resources));
      // Once a valid config is found, return it; otherwise continue search in other packages.
      if (config != null) {
        vendorConfigCache.put(context, Optional.of(config));
        return config;
      }
    }

    vendorConfigCache.put(context, Optional.empty());
    return null;
  }

  @VisibleForTesting
  static ImmutableList<ResolveInfo> sortPackagesSystemFirst(List<ResolveInfo> packages) {
    return ImmutableList.sortedCopyOf(
        comparingInt(
                (ResolveInfo resolveInfo) ->
                    resolveInfo.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM)
            .reversed(),
        packages);
  }

  @SuppressLint("QueryPermissionsNeeded") // TalkBack has permission QUERY_ALL_PACKAGES.
  private static List<ResolveInfo> retrievePackages(PackageManager packageManager) {
    final Intent intent = new Intent(CUSTOMIZATION_INTENT);
    return packageManager.queryBroadcastReceivers(intent, 0);
  }

  @Nullable
  private static VendorConfig retrieveConfigFromPackage(VendorPackage vendorPackage) {
    Resources resources = vendorPackage.resources();
    String packageName = vendorPackage.packageName();
    @SuppressLint("DiscouragedApi") // Resource is not available at compile time.
    int resourceId = resources.getIdentifier(CUSTOMIZATION_JSON_NAME, "raw", packageName);
    if (resourceId == 0) {
      LogUtils.e(TAG, "JSON file (" + CUSTOMIZATION_JSON_NAME + ".json) not found.");
      return null;
    }

    InputStream inputStream;
    try {
      inputStream = resources.openRawResource(resourceId);
    } catch (NotFoundException e) {
      LogUtils.e(TAG, "JSON file (" + CUSTOMIZATION_JSON_NAME + ".json) could not be opened.");
      return null;
    }

    JSONObject json;
    try {
      json = parseJsonFromInputStream(inputStream);
    } catch (IOException | JSONException e) {
      LogUtils.e(TAG, e.getMessage());
      return null;
    }

    Map<PageId, TvPageConfig> defaultPages = new HashMap<>();
    for (PageId pageId : CUSTOMIZABLE_PAGES) {
      TvPageConfig page;
      try {
        page = getCustomizationForPage(pageId, json, vendorPackage);
      } catch (InvalidConfigException e) {
        LogUtils.e(TAG, e.getMessage());
        return null;
      }
      defaultPages.put(pageId, page);
    }

    ImmutableList<TvPageConfig> vendorPages;
    try {
      vendorPages = getCustomPages(json, vendorPackage);
    } catch (InvalidConfigException e) {
      LogUtils.e(TAG, e, "%s", e.getMessage());
      return null;
    }

    return VendorConfig.create(defaultPages, vendorPages);
  }

  @VisibleForTesting
  static TvPageConfig getCustomizationForPage(
      PageId pageId, JSONObject json, VendorPackage vendorPackageWithJson)
      throws InvalidConfigException {
    String jsonKey = getJsonKeyForPageId(pageId);
    if (!json.has(jsonKey)) {
      // It is perfectly fine if a page is not customized; return an empty page config.
      return TvPageConfig.builder().build();
    }
    try {
      JSONObject pageJson = json.getJSONObject(jsonKey);
      return readPageContentFromJson(pageJson, false, vendorPackageWithJson);
    } catch (JSONException e) {
      throw new InvalidConfigException("Invalid JSON for page " + jsonKey, e);
    }
  }

  static String getJsonKeyForPageId(PageId pageId) {
    switch (pageId) {
      case PAGE_ID_TV_OVERVIEW:
        return JsonKeywords.STEP_OVERVIEW;
      case PAGE_ID_TV_REMOTE:
        return JsonKeywords.STEP_REMOTE;
      case PAGE_ID_TV_SHORTCUT:
        return JsonKeywords.STEP_SHORTCUT;
      default:
        throw new AssertionError("No JSON keyword defined for PageId " + pageId + ".");
    }
  }

  @VisibleForTesting
  static ImmutableList<TvPageConfig> getCustomPages(JSONObject json, VendorPackage vendorPackage)
      throws InvalidConfigException {
    if (!json.has(JsonKeywords.CUSTOM_STEPS)) {
      return ImmutableList.of();
    }
    ArrayList<TvPageConfig> list = new ArrayList<>();
    try {
      JSONObject stepsWrapper = json.getJSONObject(JsonKeywords.CUSTOM_STEPS);
      if (stepsWrapper.has(JsonKeywords.STEPS)) {
        JSONArray steps = stepsWrapper.getJSONArray(JsonKeywords.STEPS);
        for (int i = 0; i < steps.length(); ++i) {
          JSONObject stepJson = steps.getJSONObject(i);
          TvPageConfig pageConfig = readPageContentFromJson(stepJson, true, vendorPackage);
          if (pageConfig.title() == null || pageConfig.title().length() == 0) {
            throw new InvalidConfigException("Custom steps must have a title.");
          }
          if (pageConfig.summary() == null || pageConfig.summary().length() == 0) {
            throw new InvalidConfigException("Custom steps must have a summary.");
          }
          list.add(pageConfig);
        }
      }
    } catch (JSONException e) {
      throw new InvalidConfigException("Invalid JSON for custom steps.", e);
    }
    return ImmutableList.copyOf(list);
  }

  @VisibleForTesting
  static TvPageConfig readPageContentFromJson(
      JSONObject stepJson, boolean includeImage, VendorPackage vendorPackage)
      throws JSONException, InvalidConfigException {
    TvPageConfig.Builder builder = TvPageConfig.builder();
    if (stepJson.has(JsonKeywords.ENABLED)) {
      builder.setEnabled(stepJson.getBoolean(JsonKeywords.ENABLED));
    }
    if (stepJson.has(JsonKeywords.TITLE)) {
      String title = stepJson.getString(JsonKeywords.TITLE);
      if (title.length() > 0) {
        builder.setTitle(title);
      }
    }
    if (stepJson.has(JsonKeywords.SUMMARY)) {
      String summary = stepJson.getString(JsonKeywords.SUMMARY);
      if (summary.length() > 0) {
        builder.setSummary(summary);
      }
    }
    if (includeImage && stepJson.has(JsonKeywords.IMAGE)) {
      Resources resources = vendorPackage.resources();
      String packageName = vendorPackage.packageName();
      String imageName = stepJson.getString(JsonKeywords.IMAGE);
      if (imageName.length() > 0) {
        @SuppressLint("DiscouragedApi") // Resource is not available at compile time.
        int resourceId = resources.getIdentifier(imageName, "drawable", packageName);
        if (resourceId == 0) {
          throw new InvalidConfigException("Image '" + imageName + "' not found.");
        }
        builder.setImage(ExternalDrawableResource.create(packageName, resourceId));
      }
    }
    return builder.build();
  }

  static JSONObject parseJsonFromInputStream(InputStream inputStream)
      throws JSONException, IOException {
    Reader reader = new InputStreamReader(inputStream, UTF_8);
    String jsonString = CharStreams.toString(reader);
    return new JSONObject(jsonString);
  }
}
