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

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.Pipeline.FeedbackReturner;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.dynamicfeature.FeatureDownloader;
import com.google.android.accessibility.talkback.utils.SplitCompatUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.android.play.core.splitinstall.SplitInstallSessionState;
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener;
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus;

/**
 * Shows a confirmation dialog to guide users through the download of the icon detection module
 * dynamically.
 */
public class IconDetectionModuleDownloadPrompter implements SplitInstallStateUpdatedListener {

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

  private static final String TAG = "ModuleDownloadPrompter";
  public static final String ICON_DETECTION_MODULE_NAME = "screenunderstanding_feature_module";
  // TODO(b/213546002): Use SplitInstallManager.getSessionStates() to get the state of the download.
  @Nullable private static SplitInstallSessionState installState;

  private final Context context;
  private final boolean triggerByTalkBackMenu;
  private final SharedPreferences prefs;
  private final FeatureDownloader featureDownloader;
  private final IconDetectionDownloadDialog downloadDialog;
  private final IconDetectionUninstallDialog uninstallDialog;
  private final DownloadStateListener downloadStateListener;
  @Nullable private UninstallStateListener uninstallStateListener;
  @Nullable private AccessibilityNodeInfoCompat queuedNode;
  @Nullable private Pipeline.FeedbackReturner pipeline;

  public IconDetectionModuleDownloadPrompter(
      Context context,
      boolean triggeredByTalkBackMenu,
      DownloadStateListener downloadStateListener) {
    this.context = context;
    this.triggerByTalkBackMenu = triggeredByTalkBackMenu;
    prefs = SharedPreferencesUtils.getSharedPreferences(context);
    featureDownloader = new FeatureDownloader(context, ICON_DETECTION_MODULE_NAME, this);
    downloadDialog =
        new IconDetectionDownloadDialog(context, triggeredByTalkBackMenu) {
          @Override
          public void handleDialogClick(int buttonClicked) {
            IconDetectionModuleDownloadPrompter.this.handleDialogClick(buttonClicked);
          }

          @Override
          public void handleDialogDismiss() {
            IconDetectionModuleDownloadPrompter.this.handleDialogDismiss();
          }
        };
    uninstallDialog =
        new IconDetectionUninstallDialog(context) {
          @Override
          public void handleDialogClick(int buttonClicked) {
            IconDetectionModuleDownloadPrompter.this.handleUninstallDialogClick(buttonClicked);
          }

          @Override
          public void handleDialogDismiss() {}
        };
    this.downloadStateListener = downloadStateListener;
  }

  public void setPipeline(FeedbackReturner pipeline) {
    this.pipeline = pipeline;
    downloadDialog.setPipeline(pipeline);
  }

  public void setUninstallStateListener(@Nullable UninstallStateListener uninstallStateListener) {
    this.uninstallStateListener = uninstallStateListener;
  }

  /**
   * Sets the node which is waiting for recognition. The node is set when the dialog is shown and is
   * cleared when the user accept the download.
   *
   * <p>TalkBack has to performed other image captions for the given node when the user reject the
   * download. The node will be set when the dialog is shown and be cleared when the user accept the
   * download.
   */
  public void setCaptionNode(@Nullable AccessibilityNodeInfoCompat node) {
    queuedNode = node;
  }

  /** Checks if the icon detection is installed. */
  public boolean isIconDetectionModuleAvailable() {
    // Always true since the library is preloaded.
    return true;
  }

  public boolean isIconDetectionModuleDownloading() {
    return false;
  }

  /**
   * Checks if the download dialog of icon detection should be shown to the user.
   *
   * @return false, if the user checks the "Do not show again" button, or the dialog has been
   *     rejected more than three times.
   */
  public boolean needDownloadDialog() {
    return false;
  }

  /**
   * Shows a confirmation dialog to reject or download the icon detection module.
   *
   * <p>If other captions have to be performed when the confirmation is rejected, invoking {@link
   * #setCaptionNode(AccessibilityNodeInfoCompat)} to set the current focus.
   */
  public void showConfirmationDialog() {
    // Allows immediate access to the code and resource of the dynamic feature module.
    SplitCompatUtils.installActivity(context);
    downloadDialog.showDialog();
  }

  /** Shows a confirmation dialog of uninstalling the icon detection module. */
  public void showUninstallDialog() {
    uninstallDialog.showDialog();
  }

  public void shutdown() {
    featureDownloader.unregisterListener();
  }

  @Override
  public void onStateUpdate(@NonNull SplitInstallSessionState state) {
    installState = state;
    switch (state.status()) {
      case SplitInstallSessionStatus.FAILED:
        LogUtils.w(TAG, "Download Failed. %d", state.errorCode());
        downloadStateListener.onFailed();
        break;
      case SplitInstallSessionStatus.DOWNLOADING:
        long total = state.totalBytesToDownload();
        long percent = (total == 0) ? 0 : (state.bytesDownloaded() * 100) / total;
        LogUtils.v(TAG, "Downloading... %d", percent);
        break;
      case SplitInstallSessionStatus.DOWNLOADED:
        LogUtils.v(TAG, "Downloaded");
        break;
      case SplitInstallSessionStatus.INSTALLING:
        LogUtils.v(TAG, "Installing...");
        break;
      case SplitInstallSessionStatus.INSTALLED:
        LogUtils.v(TAG, "Installed");
        if (pipeline != null) {
          pipeline.returnFeedback(EVENT_ID_UNTRACKED, Feedback.initializeIconDetection());
        }
        downloadStateListener.onInstalled();
        break;
      default:
        // Do nothing.
    }
  }

  private void handleUninstallDialogClick(int buttonClicked) {
    switch (buttonClicked) {
      case DialogInterface.BUTTON_POSITIVE:
        LogUtils.v(TAG, "Starts to uninstall.");
        installState = null;
        featureDownloader.uninstall();
        if (uninstallStateListener != null) {
          uninstallStateListener.onAccepted();
        }
        break;
      case DialogInterface.BUTTON_NEGATIVE:
        LogUtils.v(TAG, "Rejects the uninstallation.");
        if (uninstallStateListener != null) {
          uninstallStateListener.onRejected();
        }
        break;
      default:
        // Do nothing.
    }
  }

  private void handleDialogClick(int buttonClicked) {
    switch (buttonClicked) {
      case DialogInterface.BUTTON_POSITIVE:
        LogUtils.v(TAG, "Starts the download.");
        // Clears the node which is waiting for recognition because it's unnecessary to perform
        // other captions if the user accepts the download.
        setCaptionNode(null);
        featureDownloader.download();
        downloadStateListener.onAccepted();
        break;
      case DialogInterface.BUTTON_NEGATIVE:
        LogUtils.v(TAG, "Rejects the download.");
        downloadStateListener.onRejected();
        break;
      default:
        // Do nothing.
    }
  }

  private void handleDialogDismiss() {
    downloadStateListener.onDialogDismissed(queuedNode);
    if (downloadDialog.isNotShowAgain()) {
      prefs
          .edit()
          .putBoolean(
              context.getString(R.string.pref_icon_detection_download_dialog_do_no_show), true)
          .apply();
    }
  }
}