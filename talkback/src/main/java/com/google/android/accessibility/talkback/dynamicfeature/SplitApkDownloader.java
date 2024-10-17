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

package com.google.android.accessibility.talkback.dynamicfeature;

import static com.google.android.accessibility.utils.caption.ImageCaptionUtils.CaptionType.ICON_LABEL;
import static com.google.android.accessibility.utils.caption.ImageCaptionUtils.CaptionType.IMAGE_DESCRIPTION;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.accessibility.utils.caption.ImageCaptionUtils.CaptionType;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A downloader stub for TalkBack dynamic features. It always reports successful for download
 * requests.
 */
public class SplitApkDownloader implements Downloader {

  private static final String TAG = "FeatureDownloader";

  @SuppressWarnings("NonFinalStaticField")
  @Nullable
  private static SplitApkDownloader instance;

  // Keeps all installed listeners which can be used during the uninstall phase.
  private final Set<DownloadStateUpdateListener> installedListeners = new HashSet<>();
  private final Map<String, DownloadStatus> installStatus = new HashMap<>();
  private boolean isInstallStatusUpdated = false;

  private SplitApkDownloader(Context unusedContext) {}

  public static SplitApkDownloader getInstance(Context context) {
    if (instance == null) {
      instance = new SplitApkDownloader(context);
    }
    return instance;
  }

  @Override
  public String getModuleName(CaptionType captionType) {
    switch (captionType) {
      case ICON_LABEL:
        return "fake_icon_detection_module_name";
      case IMAGE_DESCRIPTION:
        return "fake_image_description_module_name";
      default:
    }
    return "";
  }

  /** Creates a request to install the module. */
  @Override
  public void download(String... moduleNames) {
    ImmutableList<String> moduleNameList = ImmutableList.of(moduleNames[0]);
    DownloadState fakeStateInstalled =
        DownloadState.create(
            moduleNameList,
            DownloadStatus.INSTALLED,
            DownloadErrorCode.NO_ERROR,
            /* bytesDownloaded= */ 0,
            /* totalBytesToDownload= */ 0);

    for (DownloadStateUpdateListener listener : installedListeners) {
      listener.onStateUpdate(fakeStateInstalled);
    }
  }

  /** Uninstalls the module. */
  @Override
  public void uninstall(String... moduleName) {
    // do nothing.
  }

  /** Returns {@code true} is the status is in the process of download or installation. */
  @Override
  public boolean isDownloading(String moduleName) {
    DownloadStatus status = installStatus.get(moduleName);
    return (status == DownloadStatus.DOWNLOADING
        || status == DownloadStatus.DOWNLOADED
        || status == DownloadStatus.INSTALLING);
  }

  /** Checks if the modules is installed. */
  @Override
  public boolean isInstalled(String moduleName) {
    return installStatus.get(moduleName) == DownloadStatus.INSTALLED;
  }

  @Override
  public void registerListener(DownloadStateUpdateListener installStateListener) {
    installedListeners.add(installStateListener);
  }

  @Override
  public void unregisterListener(DownloadStateUpdateListener installStateListener) {
    if (!installedListeners.contains(installStateListener)) {
      return;
    }

    installedListeners.remove(installStateListener);
    if (installedListeners.isEmpty()) {
      isInstallStatusUpdated = false;
    }
  }

  /** Returns a {@link DownloadStatus} for the given module. */
  @Override
  @Nullable
  public DownloadStatus getDownloadStatus(String moduleName) {
    return installStatus.get(moduleName);
  }

  /** Updates the {@link DownloadStatus} for the given module. */
  @Override
  public void updateDownloadStatus(String moduleName, DownloadStatus status) {
    installStatus.put(moduleName, status);
  }

  /** Updates the {@link DownloadStatus} for all module. */
  @Override
  public synchronized void updateAllDownloadStatus() {
    if (isInstallStatusUpdated) {
      return;
    }

    isInstallStatusUpdated = true;
    installStatus.put(getModuleName(ICON_LABEL), DownloadStatus.INSTALLED);
    installStatus.put(getModuleName(IMAGE_DESCRIPTION), DownloadStatus.INSTALLED);
  }

  @Override
  public void install(Context context) {}
}
