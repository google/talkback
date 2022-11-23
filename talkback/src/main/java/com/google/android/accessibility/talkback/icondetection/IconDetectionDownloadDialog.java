package com.google.android.accessibility.talkback.icondetection;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.dialog.BaseDialog;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** A dialog to accept or reject the download of the icon detection module. */
public abstract class IconDetectionDownloadDialog extends BaseDialog {

  private static final String TAG = "ConfirmDownloadDialog";
  private final boolean triggeredByTalkBackMenu;
  private CheckBox checkBox;

  public IconDetectionDownloadDialog(Context context, boolean triggeredByTalkBackMenu) {
    super(context, R.string.confirm_download_icon_detection_title, /* pipeline= */ null);
    this.triggeredByTalkBackMenu = triggeredByTalkBackMenu;
    setPositiveButtonStringRes(R.string.confirm_download_icon_detection_positive_button_text);
    setNegativeButtonStringRes(R.string.confirm_download_icon_detection_negative_button_text);
  }

  /**
   * Checks if the "Do not show again" checkbox is checked.
   */
  public boolean isNotShowAgain() {
    return checkBox != null && checkBox.isChecked();
  }

  @Override
  public String getMessageString() {
    return null;
  }

  @Override
  public View getCustomizedView() {
    LayoutInflater inflater = LayoutInflater.from(context);
    final ScrollView root =
        (ScrollView) inflater.inflate(R.layout.confirm_download_dialog, /* root= */ null);

    TextView subtitle = root.findViewById(R.id.confirm_download_dialog_subtitle);
    subtitle.setVisibility(View.VISIBLE);
    subtitle.setText(
        isMobileDataConnected(context)
            ? R.string.confirm_download_icon_detection_subtitle_via_mobile_data
            : R.string.confirm_download_icon_detection_subtitle_via_wifi);

    TextView message = root.findViewById(R.id.confirm_download_dialog_message);
    message.setText(
        triggeredByTalkBackMenu
            ? R.string.confirm_download_icon_detection_message_via_menu
            : R.string.confirm_download_icon_detection_message_via_settings);

    checkBox = root.findViewById(R.id.confirm_download_dialog_checkbox);
    if (triggeredByTalkBackMenu) {
      checkBox.setVisibility(View.VISIBLE);
      checkBox.setText(R.string.confirm_download_icon_detection_checkbox_text);
    }

    return root;
  }

  /**
   * Checks if the device is connected on the mobile data.
   */
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