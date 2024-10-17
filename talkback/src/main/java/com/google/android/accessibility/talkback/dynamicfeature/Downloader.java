/*
 * Copyright (C) 2024 Google Inc.
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
import com.google.android.accessibility.utils.caption.ImageCaptionUtils.CaptionType;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

/** A downloader to request to download libraries. */
public interface Downloader {

  /** Status of a failed download. */
  enum DownloadErrorCode {
    /** No error processing download. */
    NO_ERROR,
    /** Unknown error processing download. */
    UNKNOWN_ERROR,
    /** Download failed due to insufficient storage. */
    INSUFFICIENT_STORAGE,
    /** Download failed due to network problem. */
    NETWORK_ERROR,
  }

  /** The status of a download. */
  enum DownloadStatus {
    UNKNOWN,
    /** Download or installation has failed. */
    FAILED,
    /** The download is in progress. */
    DOWNLOADING,
    /** The libraries are downloaded but haven't been installed. */
    DOWNLOADED,
    /** The installation is in progress. */
    INSTALLING,
    /** Installation is complete. */
    INSTALLED,
  }

  /** The current state of a download request. */
  @AutoValue
  abstract class DownloadState {

    /** Returns module names that are requested to download. */
    public abstract ImmutableList<String> moduleNames();

    /** Returns the status of a download request. */
    public abstract DownloadStatus downloadStatus();

    /**
     * Returns the error code for a download, or {@link DownloadErrorCode#NO_ERROR} if the download
     * is successful or in progress.
     */
    public abstract DownloadErrorCode downloadErrorCode();

    /** Returns the number of bytes has been downloaded until now. */
    public abstract long bytesDownloaded();

    /** Returns the total number of bytes to download. */
    public abstract long totalBytesToDownload();

    /**
     * Creates a {@link DownloadState} with a error code for {@link DownloadStatus#FAILED} status.
     */
    public static DownloadState create(
        ImmutableList<String> moduleNames,
        DownloadStatus downloadStatus,
        DownloadErrorCode downloadErrorCode,
        long bytesDownloaded,
        long totalBytesToDownload) {
      return new AutoValue_Downloader_DownloadState(
          moduleNames, downloadStatus, downloadErrorCode, bytesDownloaded, totalBytesToDownload);
    }
  }

  /** A listener to receive the state updates of download requests. */
  interface DownloadStateUpdateListener {

    /** Called when the state has changed. */
    void onStateUpdate(DownloadState downloadState);
  }

  /** Returns a module name for the given {@link CaptionType}. */
  String getModuleName(CaptionType captionType);

  /** Requests to download libraries. */
  void download(String... name);

  /** Requests to uninstall libraries. */
  void uninstall(String... name);

  /** Check if there is a library that is downloading. */
  boolean isDownloading(String name);

  /** Checks if the given library has been downloaded and installed. */
  boolean isInstalled(String name);

  /** Registers a callback to receive updates on the download status. */
  void registerListener(DownloadStateUpdateListener listener);

  /** Unregisters a callback to stop receiving updates on the download status. */
  void unregisterListener(DownloadStateUpdateListener listener);

  /** Returns the download status of the given library. */
  DownloadStatus getDownloadStatus(String name);

  /** Updates the download status for the given library. */
  void updateDownloadStatus(String name, DownloadStatus status);

  /** Updates the download status for all libraries. */
  void updateAllDownloadStatus();

  /**
   * Installs libraries to allow immediate access to the code and resource of the downloaded
   * libraries.
   */
  void install(Context context);
}
