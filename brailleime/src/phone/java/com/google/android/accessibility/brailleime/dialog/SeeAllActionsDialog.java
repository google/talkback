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
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import com.google.android.accessibility.brailleime.Dialogs;
import com.google.android.accessibility.brailleime.R;

/** A dialog controller that presents see all actions for braille keyboard. */
public class SeeAllActionsDialog extends ViewAttachedDialog {

  /** Callback for see all actions dialog events. */
  interface Callback {
    void showContextMenu();
  }

  /** Gesture with icon and action description. */
  private enum Gestures {
    SWIPE_RIGHT_ONE(R.drawable.swipe_right_one, R.string.gesture_add_space),
    SWIPE_LEFT_ONE(R.drawable.swipe_left_one, R.string.gesture_delete),
    SWIPE_RIGHT_TWO(R.drawable.swipe_right_two, R.string.gesture_new_line),
    SWIPE_LEFT_TWO(R.drawable.swipe_left_two, R.string.gesture_delete_word),
    SWIPE_DOWN_TWO(R.drawable.swipe_down_two, R.string.gesture_hide_keyboard),
    SWIPE_DOWN_THREE(R.drawable.swipe_down_three, R.string.gesture_next_keyboard),
    SWIPE_UP_TWO(R.drawable.swipe_up_two, R.string.gesture_submit_text),
    SWIPE_UP_THREE(R.drawable.swipe_up_three, R.string.gesture_open_context_menu),
    ;

    private final int iconDrawableRes;
    private final int titleStringRes;

    Gestures(@DrawableRes int iconDrawableRes, @StringRes int titleStringRes) {
      this.iconDrawableRes = iconDrawableRes;
      this.titleStringRes = titleStringRes;
    }
  }

  private final Context context;
  private static AlertDialog seeAllActionsDialog;
  private final Callback callback;

  public SeeAllActionsDialog(Context context) {
    this(context, null);
  }

  public SeeAllActionsDialog(Context context, Callback callback) {
    this.context = context;
    this.callback = callback;
  }

  /** Shows the see all actions dialog. */
  public void show() {
    makeDialog();
    seeAllActionsDialog.show();
  }

  @Override
  protected Dialog makeDialog() {
    Context dialogContext = Dialogs.getDialogContext(context);
    RecyclerView recyclerView = new RecyclerView(dialogContext);
    recyclerView.setLayoutManager(new LinearLayoutManager(dialogContext));
    recyclerView.setAdapter(new GesturesAndActionsAdapter(dialogContext));
    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(dialogContext);
    dialogBuilder
        .setTitle(context.getString(R.string.braille_keyboard_gestures))
        .setView(recyclerView)
        .setPositiveButton(
            context.getString(R.string.done),
            (dialog, which) -> {
              if (callback != null) {
                callback.showContextMenu();
              }
            })
        .setOnCancelListener(
            dialog -> {
              if (callback != null) {
                callback.showContextMenu();
              }
            });
    seeAllActionsDialog = dialogBuilder.create();
    seeAllActionsDialog.setCanceledOnTouchOutside(false);
    return seeAllActionsDialog;
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
