/*
 * Copyright (C) 2023 Google Inc.
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

import static com.google.android.accessibility.talkback.dynamicfeature.ModuleDownloadPrompter.Requester.MENU;
import static com.google.android.accessibility.talkback.dynamicfeature.ModuleDownloadPrompter.Requester.ONBOARDING;
import static com.google.android.accessibility.talkback.dynamicfeature.ModuleDownloadPrompter.Requester.SETTINGS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.dialog.BaseDialog;
import com.google.android.accessibility.talkback.dynamicfeature.ModuleDownloadPrompter.Requester;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.DownloadDialogResources;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** A dialog to accept or reject the download of a module. */
public abstract class DownloadDialog extends BaseDialog {

  private static final String TAG = "DownloadDialog";

  /**
   * The download is requested in what situation. The field is set by {@link #showDialog(Requester)}
   * and used by {@link #getCustomizedView()}.
   */
  @Nullable private Requester requester;

  private final DownloadDialogResources resources;
  private CheckBox checkBox;

  public DownloadDialog(Context context, DownloadDialogResources resources) {
    super(context, resources.downloadTitleRes, /* pipeline= */ null);
    this.resources = resources;
    setPositiveButtonStringRes(R.string.confirm_download_positive_button_text);
    setNegativeButtonStringRes(R.string.confirm_download_negative_button_text);
  }

  /** Checks if the "Do not show again" checkbox is checked. */
  public boolean isNotShowAgain() {
    return checkBox != null && checkBox.isChecked();
  }

  @Override
  public String getMessageString() {
    return null;
  }

  @SuppressLint("InflateParams")
  @Override
  public View getCustomizedView() {
    LayoutInflater inflater = LayoutInflater.from(context);
    final ScrollView root =
        (ScrollView) inflater.inflate(R.layout.confirm_download_dialog, /* root= */ null);

    TextView subtitle = root.findViewById(R.id.confirm_download_dialog_subtitle);
    subtitle.setVisibility(View.VISIBLE);
    subtitle.setText(
        context.getString(
            isMobileDataConnected(context)
                ? R.string.confirm_download_subtitle_via_mobile_data
                : R.string.confirm_download_subtitle_via_wifi,
            resources.moduleSizeInMb));

    TextView message = root.findViewById(R.id.confirm_download_dialog_message);
    checkBox = root.findViewById(R.id.confirm_download_dialog_checkbox);
    if (requester == null) {
      requester = SETTINGS;
    }

    switch (requester) {
      case MENU:
        {
          message.setText(resources.downloadMessageForMenuRes);
          checkBox.setVisibility(View.VISIBLE);
          checkBox.setText(R.string.confirm_download_checkbox_text);
        }
        break;
      case ONBOARDING:
        {
          message.setVisibility(View.GONE);
        }
        break;
      case SETTINGS: // fall-through
      default:
        message.setText(resources.downloadMessageForSettingsRes);
    }
    return root;
  }

  /**
   * Shows a dialog to download module.
   *
   * @param requester the download is requested in what situation.
   */
  public void showDialog(Requester requester) {
    this.requester = requester;
    super.showDialog();
  }

  /** Checks if the device is connected on the mobile data. */
  private static boolean isMobileDataConnected(Context context) {
    ConnectivityManager connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    Network activeNetwork = connectivityManager.getActiveNetwork();
    if (activeNetwork == null) {
      LogUtils.v(TAG, "No active network.");
      return false;
    }

    NetworkCapabilities networkCapabilities =
        connectivityManager.getNetworkCapabilities(activeNetwork);
    if (networkCapabilities == null) {
      LogUtils.v(TAG, "Can't get the capability of the active network.");
      return false;
    }

    return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
  }
}
