/*
 * Copyright 2020 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.brailleime.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.brailleime.settings.BrailleImeGestureActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** A dialog controller that presents basic actions for braille keyboard. */
public abstract class BasicActionsDialog extends ViewAttachedDialog {

  /** Gesture with icon and action description. */
  private enum Gestures {
    SWIPE_RIGHT_ONE(R.drawable.ic_one_finger_right, R.string.gesture_add_space),
    SWIPE_LEFT_ONE(R.drawable.ic_one_finger_left, R.string.gesture_delete),
    SWIPE_RIGHT_TWO(R.drawable.ic_two_fingers_right, R.string.gesture_new_line),
    SWIPE_LEFT_TWO(R.drawable.ic_two_fingers_left, R.string.gesture_delete_word),
    SWIPE_UP_ONE(R.drawable.ic_one_finger_up, R.string.gesture_move_cursor_backward),
    SWIPE_DOWN_ONE(R.drawable.ic_one_finger_down, R.string.gesture_move_cursor_forward),
    SWIPE_DOWN_TWO(R.drawable.ic_two_fingers_down, R.string.gesture_hide_keyboard),
    SWIPE_DOWN_THREE(R.drawable.ic_three_fingers_down, R.string.gesture_next_keyboard),
    SWIPE_UP_TWO(R.drawable.ic_two_fingers_up, R.string.gesture_submit_text),
    SWIPE_UP_THREE(R.drawable.ic_three_fingers_up, R.string.gesture_open_context_menu),
    ;

    private final int iconDrawableRes;
    private final int titleStringRes;

    Gestures(@DrawableRes int iconDrawableRes, @StringRes int titleStringRes) {
      this.iconDrawableRes = iconDrawableRes;
      this.titleStringRes = titleStringRes;
    }
  }

  /** Callback for basic gestures dialog. */
  public interface BasicActionsCallback {
    void onClickMoreGestures();
  }

  protected final Context context;
  private static AlertDialog basicActionsDialog;
  private final BasicActionsCallback callback;

  public BasicActionsDialog(Context context, BasicActionsCallback callback) {
    this.context = context;
    this.callback = callback;
  }

  /**
   * Creates {@link AlertDialog.Builder} by {@link androidx.appcompat.app.AlertDialog} or {@link
   * MaterialAlertDialogBuilder}
   *
   * @return {@link AlertDialog.Builder}
   */
  protected abstract AlertDialog.Builder dialogBuilder();

  /** Shows the basic actions dialog. */
  public void show() {
    makeDialog();
    basicActionsDialog.show();
  }

  @CanIgnoreReturnValue
  @Override
  protected Dialog makeDialog() {
    Context dialogContext = Dialogs.getDialogContext(context);
    RecyclerView recyclerView = new RecyclerView(dialogContext);
    recyclerView.setLayoutManager(new LinearLayoutManager(dialogContext));
    recyclerView.setAdapter(new GesturesAndActionsAdapter(dialogContext));

    // To sync with dialog in the Settings, the dialog will use v7 AlertDialog (Target to change to
    // material dialog on T). The other situation, the dialog will use material dialog.
    AlertDialog.Builder dialogBuilder = dialogBuilder();
    dialogBuilder
        .setTitle(context.getString(R.string.basic_gestures))
        .setView(recyclerView)
        .setNegativeButton(
            context.getString(R.string.all_gestures),
            (dialog, which) -> {
              Intent intent = new Intent(context, BrailleImeGestureActivity.class);
              intent.addFlags(
                  Intent.FLAG_ACTIVITY_NEW_TASK
                      | Intent.FLAG_ACTIVITY_CLEAR_TOP
                      | Intent.FLAG_ACTIVITY_SINGLE_TOP);
              context.startActivity(intent);
              callback.onClickMoreGestures();
            })
        .setPositiveButton(context.getString(android.R.string.cancel), null);

    basicActionsDialog = dialogBuilder.create();
    basicActionsDialog.setCanceledOnTouchOutside(false);
    return basicActionsDialog;
  }

  /** Adapter provides gestures' icon in left and action descriptions in right. */
  private static class GesturesAndActionsAdapter
      extends RecyclerView.Adapter<GesturesAndActionsAdapter.ViewHolder> {
    private final Context context;

    public GesturesAndActionsAdapter(Context context) {
      this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
      View view =
          LayoutInflater.from(context)
              .inflate(android.R.layout.select_dialog_item, viewGroup, false);
      return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
      holder.item.setTextSize(
          TypedValue.COMPLEX_UNIT_PX,
          context.getResources().getDimension(R.dimen.actions_item_text_size));
      // Setup icon.
      Drawable icon = context.getDrawable(Gestures.values()[position].iconDrawableRes);
      int drawableSizeInPixels =
          context.getResources().getDimensionPixelSize(R.dimen.actions_drawable_size);
      icon.setBounds(0, 0, drawableSizeInPixels, drawableSizeInPixels);
      holder.item.setCompoundDrawables(
          icon, /* top= */ null, /* right= */ null, /* bottom= */ null);
      holder.item.setCompoundDrawablePadding(
          context.getResources().getDimensionPixelSize(R.dimen.padding_between_drawable_and_text));
      // Setup title.
      holder.item.setText(Gestures.values()[position].titleStringRes);
    }

    @Override
    public int getItemCount() {
      return Gestures.values().length;
    }

    private static class ViewHolder extends RecyclerView.ViewHolder {
      private final TextView item;

      public ViewHolder(View itemView) {
        super(itemView);
        item = itemView.findViewById(android.R.id.text1);
      }
    }
  }
}
