package com.google.android.accessibility.talkback.icondetection;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.dialog.BaseDialog;

/** A dialog to uninstall the icon detection module. */
public abstract class IconDetectionUninstallDialog extends BaseDialog {

   private static final String TAG = "IconDetectionUninstallDialog";

   public IconDetectionUninstallDialog(Context context) {
      super(context, R.string.uninstall_icon_detection_dialog_title, /* pipeline= */ null);
      setPositiveButtonStringRes(R.string.uninstall_icon_detection_dialog_positive_button_text);
      setNegativeButtonStringRes(R.string.uninstall_icon_detection_dialog_negative_button_text);
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
      subtitle.setText(R.string.uninstall_icon_detection_dialog_subtitle);

      TextView message = root.findViewById(R.id.confirm_download_dialog_message);
      message.setText(R.string.uninstall_icon_detection_dialog_message);

      return root;
   }
}