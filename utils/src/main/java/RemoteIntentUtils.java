/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.google.android.accessibility.utils;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** Utility class to access private API that allows system app to open URL on the phone. */
public class RemoteIntentUtils {
  // Intent action for requesting Home to open a link on the companion device
  private static final String OPEN_ON_PHONE_REMOTE_CONFIRM_ACTION =
      "com.google.android.clockwork.home.OPEN_ON_PHONE_ACTION";

  private static final String KEY_PREFIX = "com.google.android.clockwork.actions.RpcWithCallback.";
  public static final String KEY_RPC_DATA = KEY_PREFIX + "rpc_data";
  public static final String KEY_FEATURE_TAG = KEY_PREFIX + "feature_tag";
  public static final String KEY_RPC_PATH = KEY_PREFIX + "rpc_path";

  public static final String FEATURE_TAG = "remote_intent";
  public static final String PATH_RPC = "/rpc";

  public static final String KEY_ACTION = "action";
  public static final String KEY_URI_DATA = "uri_data";
  public static final String KEY_START_MODE = "start_mode";
  public static final String KEY_ACTIVITY_OPTIONS = "activity_options";
  public static final String ACTIVITY_OPTIONS_WAKE_PHONE = "activity_options_wake_phone";

  public static final int START_MODE_ACTIVITY = 0;

  /**
   * Convenience function to create an Intent that can be used with {@code Context.startActivity()}
   * or {@code Context.startActivityForResult()}. It will be received by Home and consequently
   * bounced over to the phone to open {@code uri}.
   *
   * <p>
   *
   * <p>Home will use a {@link com.google.android.clockwork.home.RemoteActionConfirmationActivity}
   * to either animate a success message or show a 'that didn't work' message if the phone is not
   * connected.
   *
   * @param uri the address to be opened
   * @return the Intent to be broadcast.
   */
  public static Intent intentToOpenUriOnPhone(Uri uri) {
    Bundle rpcData = new Bundle();
    rpcData.putString(KEY_ACTION, Intent.ACTION_VIEW);
    rpcData.putString(KEY_URI_DATA, uri.toString());
    rpcData.putInt(KEY_START_MODE, START_MODE_ACTIVITY);
    Bundle options = new Bundle();
    options.putBoolean(ACTIVITY_OPTIONS_WAKE_PHONE, true);
    rpcData.putBundle(KEY_ACTIVITY_OPTIONS, options);
    Intent openOnPhoneIntent = new Intent(OPEN_ON_PHONE_REMOTE_CONFIRM_ACTION);
    openOnPhoneIntent
        .putExtra(KEY_FEATURE_TAG, FEATURE_TAG)
        .putExtra(KEY_RPC_PATH, PATH_RPC)
        .putExtra(KEY_RPC_DATA, rpcData)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    // When there is no target 'NODE' extra, the companion device is assumed to be the target.
    return openOnPhoneIntent;
  }

  private RemoteIntentUtils() {}
}
