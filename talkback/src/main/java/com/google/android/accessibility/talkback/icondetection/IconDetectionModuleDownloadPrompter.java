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

package com.google.android.accessibility.talkback.icondetection;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Pipeline.FeedbackReturner;

/**
 * Shows a confirmation dialog to guide users through the download of the icon detection module
 * dynamically.
 */
public class IconDetectionModuleDownloadPrompter {

  /** A callback to handle the download state. */
  public interface DownloadStateListener {
    /** Called when the module has been installed. */
    void onInstalled();

    /** Called when the module download is failed. */
    void onFailed();

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

  public IconDetectionModuleDownloadPrompter(
      Context context,
      boolean triggeredByTalkBackMenu,
      DownloadStateListener downloadStateListener) {}

  public void setPipeline(FeedbackReturner pipeline) {}

  public void setUninstallStateListener(@Nullable UninstallStateListener uninstallStateListener) {}

  public void setCaptionNode(@Nullable AccessibilityNodeInfoCompat node) {}

  public boolean isIconDetectionModuleAvailable() {
    return false;
  }

  public boolean isIconDetectionModuleDownloading() {
    return false;
  }

  public boolean needDownloadDialog() {
    return false;
  }

  public void showConfirmationDialog() {}

  public void showUninstallDialog() {}

  public void shutdown() {}
}
