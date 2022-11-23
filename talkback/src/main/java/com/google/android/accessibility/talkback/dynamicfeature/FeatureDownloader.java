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
import androidx.annotation.VisibleForTesting;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.android.play.core.splitinstall.SplitInstallManager;
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory;
import com.google.android.play.core.splitinstall.SplitInstallRequest;
import com.google.android.play.core.splitinstall.SplitInstallSessionState;
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener;
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode;
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus;
import com.google.android.play.core.splitinstall.testing.FakeSplitInstallManager;
import com.google.android.play.core.splitinstall.testing.FakeSplitInstallManagerFactory;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;

/** Downloads the dynamic feature via {@link SplitInstallManager} */
public class FeatureDownloader {

  private static final String TAG = "FeatureDownloader";
  public static final SplitInstallSessionState DOWNLOAD_FAILED =
      SplitInstallSessionState.create(
          /* sessionId= */ 0,
          SplitInstallSessionStatus.FAILED,
          SplitInstallErrorCode.NETWORK_ERROR,
          /* bytesDownloaded= */ 0,
          /* totalBytesToDownload= */ 0,
          new ArrayList<>(),
          new ArrayList<>());

  private final String moduleName;
  private final SplitInstallManager splitInstallManager;
  private SplitInstallStateUpdatedListener installStateListener;
  // For testing the download locally.
  @VisibleForTesting boolean forTest = false;

  public FeatureDownloader(
      Context context, String moduleName, SplitInstallStateUpdatedListener installStateListener) {
    this.moduleName = moduleName;
    splitInstallManager =
        forTest
            ? FakeSplitInstallManagerFactory.create(context)
            : SplitInstallManagerFactory.create(context);
    this.installStateListener = installStateListener;
    splitInstallManager.registerListener(installStateListener);
  }

  /** Creates a request to install the module. */
  public void download() {
    splitInstallManager
        .startInstall(SplitInstallRequest.newBuilder().addModule(moduleName).build())
        .addOnSuccessListener(sessionId -> LogUtils.v(TAG, "Started to download %s.", moduleName))
        .addOnFailureListener(
            exception -> {
              LogUtils.v(TAG, "Failed to download. error=%s.", exception);
              installStateListener.onStateUpdate(DOWNLOAD_FAILED);
            });
  }

  /** Uninstalls the module. */
  public void uninstall() {
    splitInstallManager.deferredUninstall(ImmutableList.of(moduleName));
  }

  /** Checks if the modules is installed. */
  public boolean isInstalled() {
    return splitInstallManager.getInstalledModules().contains(moduleName);
  }

  public void registerListener(SplitInstallStateUpdatedListener installStateListener) {
    this.installStateListener = installStateListener;
    splitInstallManager.registerListener(installStateListener);
  }

  public void unregisterListener() {
    if (installStateListener == null) {
      return;
    }

    splitInstallManager.unregisterListener(installStateListener);
    installStateListener = null;
  }

  /** Local test of the download with the network error. */
  @VisibleForTesting
  void setNetworkError(boolean hasError) {
    if (splitInstallManager instanceof FakeSplitInstallManager) {
      ((FakeSplitInstallManager) splitInstallManager).setShouldNetworkError(hasError);
    }
  }
}
