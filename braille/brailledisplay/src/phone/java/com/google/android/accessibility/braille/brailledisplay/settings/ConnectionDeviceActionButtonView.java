package com.google.android.accessibility.braille.brailledisplay.settings;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import com.google.android.accessibility.braille.brailledisplay.R;
import java.util.List;

/** View with actionable buttons. */
public class ConnectionDeviceActionButtonView extends LinearLayout {
  private final Context context;
  private final List<ActionButton> actionButtons;

  public ConnectionDeviceActionButtonView(Context context, List<ActionButton> actionButtons) {
    super(context);
    this.context = context;
    this.actionButtons = actionButtons;
    setOrientation(LinearLayout.VERTICAL);
    int horizontalPaddingInPixel =
        context
            .getResources()
            .getDimensionPixelOffset(
                R.dimen.connection_device_action_button_view_padding_horizontal);
    int verticalPaddingInPixel =
        context
            .getResources()
            .getDimensionPixelOffset(R.dimen.connection_device_action_button_view_padding_vertical);
    setPadding(
        horizontalPaddingInPixel,
        verticalPaddingInPixel,
        horizontalPaddingInPixel,
        verticalPaddingInPixel);
    init();
  }

  private void init() {
    for (ActionButton actionButton : actionButtons) {
      ViewGroup viewGroup =
          (ViewGroup) LayoutInflater.from(context).inflate(R.layout.device_detail_button, null);
      Button button = viewGroup.findViewById(R.id.button);
      button.setText(actionButton.buttonText);
      button.setOnClickListener(actionButton.onClickListener);
      addView(viewGroup);
    }
  }

  /** Action button attributes. */
  public static class ActionButton {
    private final String buttonText;
    private final OnClickListener onClickListener;

    public ActionButton(String buttonText, OnClickListener onClickListener) {
      this.buttonText = buttonText;
      this.onClickListener = onClickListener;
    }
  }
}
