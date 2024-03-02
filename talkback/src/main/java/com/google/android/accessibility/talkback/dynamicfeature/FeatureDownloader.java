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
import com.google.android.play.core.splitinstall.SplitInstallRequest;
import com.google.android.play.core.splitinstall.SplitInstallSessionState;
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener;
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode;
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A downloader stub for TalkBack dynamic features. It always reports successful for download
 * requests.
 */
public class FeatureDownloader {

  private static final String TAG = "FeatureDownloader";

  @Nullable private static FeatureDownloader instance;

  // Keeps all installed listeners which can be used during the uninstall phase.
  private final Set<SplitInstallStateUpdatedListener> installedListeners = new HashSet<>();
  private final Map<String, Integer> installStatus = new HashMap<>();
  private boolean isInstallStatusUpdated = false;

  private FeatureDownloader(Context context) {}

  public static FeatureDownloader getInstance(Context context) {
    if (instance == null) {
      instance = new FeatureDownloader(context);
    }
    return instance;
  }

  static String captionTypeToModuleName(CaptionType captionType) {
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
  public void download(SplitInstallStateUpdatedListener installStateListener, String moduleName) {
    ArrayList<String> moduleNameList = new ArrayList<>();
    moduleNameList.add(moduleName);
    SplitInstallSessionState fakeStateInstalled =
        SplitInstallSessionState.create(
            /* sessionId= */ 0,
            SplitInstallSessionStatus.INSTALLED,
            SplitInstallErrorCode.NO_ERROR,
            /* bytesDownloaded= */ 0,
            /* totalBytesToDownload= */ 0,
            moduleNameList,
            new ArrayList<>());

    for (SplitInstallStateUpdatedListener listener : installedListeners) {
      listener.onStateUpdate(fakeStateInstalled);
    }
  }

  /** Uninstalls the module. */
  public void uninstall(String moduleName) {
    // do nothing.
  }

  /** Returns {@code true} is the status is in the process of download or installation. */
  public boolean isDownloading(@SplitInstallSessionStatus int installStatus) {
    return installStatus == SplitInstallSessionStatus.DOWNLOADING
        || installStatus == SplitInstallSessionStatus.DOWNLOADED
        || installStatus == SplitInstallSessionStatus.INSTALLING;
  }

  /** Checks if the modules is installed. */
  public boolean isInstalled(String moduleName) {
    return installStatus.get(moduleName).equals(SplitInstallSessionStatus.INSTALLED);
  }

  public void registerListener(SplitInstallStateUpdatedListener installStateListener) {
    installedListeners.add(installStateListener);
  }

  public void unregisterListener(SplitInstallStateUpdatedListener installStateListener) {
    if (!installedListeners.contains(installStateListener)) {
      return;
    }

    installedListeners.remove(installStateListener);
    if (installedListeners.isEmpty()) {
      isInstallStatusUpdated = false;
    }
  }

  /**
   * Returns a {@link SplitInstallSessionStatus} of the {@link SplitInstallRequest} for the given
   * module.
   */
  @SplitInstallSessionStatus
  public int getInstallStatus(String moduleName) {
    Integer status = installStatus.get(moduleName);
    return status == null ? SplitInstallSessionStatus.UNKNOWN : status;
  }

  /**
   * Updates the {@link SplitInstallSessionStatus} of the {@link SplitInstallRequest} for the given
   * module.
   */
  public void updateInstallStatus(String moduleName, @SplitInstallSessionStatus int status) {
    installStatus.put(moduleName, status);
  }

  /**
   * Updates the {@link SplitInstallSessionStatus} of the {@link SplitInstallRequest} for all
   * module.
   */
  public synchronized void updateAllInstallStatuses() {
    if (isInstallStatusUpdated) {
      return;
    }

    isInstallStatusUpdated = true;
    installStatus.put(captionTypeToModuleName(ICON_LABEL), SplitInstallSessionStatus.INSTALLED);
    installStatus.put(
        captionTypeToModuleName(IMAGE_DESCRIPTION), SplitInstallSessionStatus.INSTALLED);
  }
}
