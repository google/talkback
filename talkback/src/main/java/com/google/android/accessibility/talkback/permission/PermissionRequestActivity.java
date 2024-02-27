/*
 * Copyright (C) 2021 Google Inc.
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

package com.google.android.accessibility.talkback.permission;

import android.Manifest.permission;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import android.text.TextUtils;
import androidx.core.app.ActivityCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.material.A11yAlertDialogWrapper;

/** Activity for TalkBack to request the permission. */
public class PermissionRequestActivity extends FragmentActivity {

  /** Extra key of the requesting permissions. */
  public static final String PERMISSIONS = "permissions";
  /** Extra key of the permissions grant results. */
  public static final String GRANT_RESULTS = "grant_results";

  /** Action key of permission granted. */
  public static final String ACTION_DONE = "done";
  /** Action key of permission rejected. */
  public static final String ACTION_REJECTED = "rejected";

  /** Create the activity screen to ask for the requesting permissions */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  /** Everytime this is activated, ask for the requesting permission. */
  @Override
  public void onResume() {
    super.onResume();

    String[] permissions = getIntent().getStringArrayExtra(PERMISSIONS);
    if (permissions == null || permissions.length == 0) {
      return;
    }

    if (TextUtils.equals(permissions[0], permission.READ_PHONE_STATE)
        && permissions.length == 1
        && shouldShowRequestPermissionRationale(permission.READ_PHONE_STATE)) {
      final A11yAlertDialogWrapper alertDialog =
          A11yAlertDialogWrapper.materialDialogBuilder(this, getSupportFragmentManager())
              .setTitle(R.string.title_request_phone_permission)
              .setMessage(R.string.message_request_phone_permission)
              .setPositiveButton(
                  R.string.continue_button,
                  (dialog, buttonClicked) -> requestPermission(permission.READ_PHONE_STATE))
              .setNegativeButton(android.R.string.cancel, (dialog, buttonClicked) -> finish())
              .create();
      alertDialog.setCanceledOnTouchOutside(true);
      alertDialog.show();
    } else {
      ActivityCompat.requestPermissions(this, permissions, 1);
    }
  }

  /** Depending on the response, send a broadcast intent either accepting or rejecting */
  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    Intent broadcastIntent = new Intent();
    // finish this activity
    finish();
    // send the broadcast
    broadcastIntent.setAction(ACTION_DONE);
    broadcastIntent.putExtra(PERMISSIONS, permissions);
    broadcastIntent.putExtra(GRANT_RESULTS, grantResults);
    sendBroadcast(broadcastIntent);
  }

  private void requestPermission(String permission) {
    ActivityCompat.requestPermissions(this, new String[] {permission}, 1);
  }
}
