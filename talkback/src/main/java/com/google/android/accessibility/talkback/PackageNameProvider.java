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
package com.google.android.accessibility.talkback;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Telephony;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.List;

/** A provider for component package names on the current device. */
public class PackageNameProvider {

  private static final String TAG = "PackageNameProvider";

  private static boolean isInitialized = false;

  private static String CELL_BROADCAST_RECEIVER_PACKAGE_NAME;

  private static List<ResolveInfo> HOME_ACTIVITY_RESOLVED_INFO;

  // System API, copied from frameworks/base/core/res/AndroidManifest.xml
  @VisibleForTesting
  public static final String PERMISSION_RECEIVE_EMERGENCY_BROADCAST =
      "android.permission.RECEIVE_EMERGENCY_BROADCAST";

  public static void initialize(Context context) {
    CELL_BROADCAST_RECEIVER_PACKAGE_NAME = getDefaultCellBroadcastReceiverPackageName(context);
    LogUtils.d(TAG, "CELL_BROADCAST_RECEIVER_PACKAGE_NAME=" + CELL_BROADCAST_RECEIVER_PACKAGE_NAME);

    HOME_ACTIVITY_RESOLVED_INFO = getDefaultHomeActivityResolvedInfo(context);
    LogUtils.d(TAG, "HOME_ACTIVITY_RESOLVED_INFO=" + HOME_ACTIVITY_RESOLVED_INFO);

    isInitialized = true;
  }

  /**
   * Get the name for the package which can resolve {@link
   * Telephony.Sms.Intents#SMS_CB_RECEIVED_ACTION}.
   */
  public static String getCellBroadcastReceiverPackageName() {
    if (!isInitialized) {
      throw new IllegalStateException("We should initialize PackageNameProvider before querying.");
    }

    return CELL_BROADCAST_RECEIVER_PACKAGE_NAME;
  }

  public static List<ResolveInfo> getHomeActivityResolvedInfo() {
    if (!isInitialized) {
      throw new IllegalStateException("We should initialize PackageNameProvider before querying.");
    }

    return HOME_ACTIVITY_RESOLVED_INFO;
  }

  // modified based on
  // frameworks/base/telephony/common/com/android/internal/telephony/CellBroadcastUtils.java
  /** Utility method to query the default CBR's package name. */
  @Nullable
  private static String getDefaultCellBroadcastReceiverPackageName(Context context) {
    PackageManager packageManager = context.getPackageManager();
    ResolveInfo resolveInfo =
        packageManager.resolveActivity(
            new Intent(Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION),
            PackageManager.MATCH_SYSTEM_ONLY);

    if (resolveInfo == null) {
      LogUtils.d(TAG, "getDefaultCellBroadcastReceiverPackageName: no package found");
      return null;
    }

    String packageName = resolveInfo.activityInfo.applicationInfo.packageName;
    LogUtils.d(TAG, "getDefaultCellBroadcastReceiverPackageName: found package: %s", packageName);

    if (TextUtils.isEmpty(packageName)
        || packageManager.checkPermission(PERMISSION_RECEIVE_EMERGENCY_BROADCAST, packageName)
            == PackageManager.PERMISSION_DENIED) {
      LogUtils.d(
          TAG,
          "getDefaultCellBroadcastReceiverPackageName: returning null; permission check failed for"
              + " : %s",
          packageName);
      return null;
    }

    return packageName;
  }

  private static List<ResolveInfo> getDefaultHomeActivityResolvedInfo(Context context) {
    return context
        .getPackageManager()
        .queryIntentActivities(
            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY);
  }
}
