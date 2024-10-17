package com.google.android.accessibility.talkback.actor.gemini;

import android.content.Context;
import android.view.View;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.dialog.BaseDialog;

/** A dialog to display image description from Gemini. */
public class GeminiResultDialog extends BaseDialog {

  private String message;

  public GeminiResultDialog(
      Context context,
      @StringRes int dialogTitleResId,
      String message,
      @StringRes int positiveButtonResId) {
    super(context, dialogTitleResId, /* pipeline= */ null);
    this.message = message;
    setIncludeNegativeButton(false);
    setPositiveButtonStringRes(positiveButtonResId);
  }

  @Override
  public void handleDialogClick(int buttonClicked) {}

  @Override
  public void handleDialogDismiss() {}

  @Override
  public String getMessageString() {
    return message;
  }

  @Override
  public View getCustomizedView() {
    return null;
  }
}
