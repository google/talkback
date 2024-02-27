package com.google.android.accessibility.utils.material;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.android.accessibility.utils.R;

/**
 * This class customizes the foreground text color to adapt the button background change when
 * getting input focus.
 */
class AlertDialogAdaptiveContrastUtil {

  /**
   * Creates AlertDialogAdaptiveContrast for {@link android.app.AlertDialog.Builder} to customize
   * the dialog buttons can adapt the color of foreground text, when the input focus changed, to
   * comply the contrast criteria.
   *
   * @param context The current context
   * @param theme The theme applies for the alert dialog
   * @return {@code android.app.AlertDialog.Builder}
   */
  static AlertDialog.Builder appAlertDialogAdaptiveContrastBuilder(Context context, int theme) {
    return new AlertDialogAdaptiveContrastBuilder(context, theme);
  }

  /**
   * Creates AlertDialogAdaptiveContrast for {@link androidx.appcompat.app.AlertDialog.Builder} to
   * customize the dialog buttons can adapt the color of foreground text, when the input focus
   * changed, to comply the contrast criteria.
   *
   * @param context The current context
   * @param theme The theme applies for the alert dialog
   * @return {@code androidx.appcompat.app.AlertDialog.Builder}
   */
  static androidx.appcompat.app.AlertDialog.Builder v7AlertDialogAdaptiveContrastBuilder(
      Context context, int theme) {
    return new V7AlertDialogAdaptiveContrastBuilder(context, theme);
  }

  /**
   * This class supports {@link android.app.AlertDialog} and customizes the foreground text color to
   * adapt the button background change when getting input focus.
   */
  private static class AlertDialogAdaptiveContrastBuilder extends AlertDialog.Builder {

    private Context context;

    public AlertDialogAdaptiveContrastBuilder(Context context, int theme) {
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
      adjustTextColorViaFocus(
          context,
          dialog.getButton(DialogInterface.BUTTON_POSITIVE),
          dialog.getButton(DialogInterface.BUTTON_NEGATIVE));
      return dialog;
    }
  }

  /**
   * This class supports {@link androidx.appcompat.app.AlertDialog} and customizes the foreground
   * text color to adapt the button background change when getting input focus.
   */
  private static class V7AlertDialogAdaptiveContrastBuilder
      extends androidx.appcompat.app.AlertDialog.Builder {

    private Context context;

    public V7AlertDialogAdaptiveContrastBuilder(Context context, int theme) {
      super(context, theme);
      this.context = context;
    }

    /**
     * To set focus change listener, the buttons are visible only after the container dialog's
     * created/shown.
     */
    @Override
    public androidx.appcompat.app.AlertDialog create() {
      androidx.appcompat.app.AlertDialog dialog = super.create();
      dialog.create();
      adjustTextColorViaFocus(
          context,
          dialog.getButton(DialogInterface.BUTTON_POSITIVE),
          dialog.getButton(DialogInterface.BUTTON_NEGATIVE));
      return dialog;
    }
  }

  /**
   * The background color of dialog button changes when it got input focus. That would affect the
   * contrast of display text and fail the GAR criteria. This method adjusts the color the
   * foreground text according to the applied theme (day/night).
   */
  private static void adjustTextColorViaFocus(
      Context context, @Nullable Button buttonPositive, @Nullable Button buttonNegative) {
    View.OnFocusChangeListener focusChangeListener =
        (v, hasFocus) ->
            ((Button) v)
                .setTextColor(
                    ContextCompat.getColor(
                        context,
                        hasFocus
                            ? R.color.a11y_alert_dialog_button_focused_color
                            : R.color.a11y_alert_dialog_button_color));

    if (buttonPositive != null) {
      buttonPositive.setOnFocusChangeListener(focusChangeListener);
    }

    if (buttonNegative != null) {
      buttonNegative.setOnFocusChangeListener(focusChangeListener);
    }
  }
}
