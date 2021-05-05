package com.google.android.accessibility.talkback.utils;

import android.app.AlertDialog;
import android.content.Context;
import androidx.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import androidx.core.content.ContextCompat;
import com.google.android.accessibility.talkback.R;

/**
 * This class customizes the foreground text color to adapt the button background change when
 * getting input focus.
 */
public class AlertDialogAdaptiveContrast extends AlertDialog.Builder {
  private Context context;

  public AlertDialogAdaptiveContrast(Context context, int theme) {
    super(context, theme);
    this.context = context;
  }

  /**
   * To set focus change listener, the buttons are visible only after the container dialog's
   * created/shown.
   */
  @Override
  public AlertDialog create() {
    AlertDialog dialog = super.create();
    dialog.create();
    adjustTextColorViaFocus(dialog, context);
    return dialog;
  }

  /**
   * The background color of dialog button changes when it got input focus. That would affect the
   * contrast of display text and fail the GAR criteria. This method adjusts the color the
   * foreground text according to the applied theme (day/night).
   */
  private void adjustTextColorViaFocus(AlertDialog alertDialog, Context context) {
    View.OnFocusChangeListener focusChangeListener =
        (v, hasFocus) ->
            ((Button) v)
                .setTextColor(
                    ContextCompat.getColor(
                        context, hasFocus ? R.color.colorAccentFocused : R.color.colorAccent));

    @Nullable Button buttonPositive = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
    if (buttonPositive != null) {
      buttonPositive.setOnFocusChangeListener(focusChangeListener);
    }

    @Nullable Button buttonNegative = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
    if (buttonNegative != null) {
      buttonNegative.setOnFocusChangeListener(focusChangeListener);
    }
  }
}
