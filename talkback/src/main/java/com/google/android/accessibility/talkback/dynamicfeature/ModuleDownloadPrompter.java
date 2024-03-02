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

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.imagecaption.CaptionRequest.ErrorCode;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.DownloadDialogResources;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.ImageCaptionPreferenceKeys;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.UninstallDialogResources;
import com.google.android.accessibility.utils.caption.ImageCaptionUtils.CaptionType;

/** Shows a confirmation dialog to guide users through the download of a module dynamically. */
public abstract class ModuleDownloadPrompter {

  /** A callback to handle the download state. */
  public interface DownloadStateListener {
    /** Called when the module has been installed. */
    void onInstalled();

    /** Called when the module download is failed. */
    void onFailed(@ErrorCode int errorCode);

    /** Called when the download has been accepted. */
    void onAccepted();

    /** Called when the download has been rejected. */
    void onRejected();

    /** Called when the dialog is dismissed. */
    void onDialogDismissed(@Nullable AccessibilityNodeInfoCompat queuedNode);
  }

  /** A callback to handle the uninstall state. */
  public interface UninstallStateListener {
    /** Called when the uninstallation has been accepted. */
    void onAccepted();

    /** Called when the uninstallation has been rejected. */
    void onRejected();
  }

  /** Requests the download in what situation. */
  public enum Requester {
    /** The request from TalkBack menu. */
    MENU,
    /** The request from TalkBack settings page. */
    SETTINGS,
    /** The request from onboarding page. */
    ONBOARDING
  }

  public ModuleDownloadPrompter(
      Context context,
      FeatureDownloader featureDownloader,
      CaptionType captionType,
      ImageCaptionPreferenceKeys preferenceKeys,
      DownloadDialogResources downloadDialogResources,
      UninstallDialogResources uninstallDialogResources) {}

  public void setDownloadStateListener(@Nullable DownloadStateListener downloadStateListener) {}

  public void setUninstallStateListener(@Nullable UninstallStateListener uninstallStateListener) {}

  public void setCaptionNode(@Nullable AccessibilityNodeInfoCompat node) {}

  public boolean isModuleAvailable() {
    return false;
  }

  public boolean isModuleDownloading() {
    return false;
  }

  public boolean isUninstalled() {
    return true;
  }

  public boolean needDownloadDialog(Requester requester) {
    return false;
  }

  public void showDownloadDialog(Requester requester) {}

  public void showUninstallDialog() {}

  public void shutdown() {}

  protected abstract boolean initModule();
}
