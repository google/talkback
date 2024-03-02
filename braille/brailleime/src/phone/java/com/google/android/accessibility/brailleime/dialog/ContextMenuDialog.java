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
import android.app.KeyguardManager;
import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.StringRes;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.brailleime.BrailleIme;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.brailleime.Utils;
import com.google.android.accessibility.brailleime.analytics.BrailleImeAnalytics;
import com.google.android.accessibility.brailleime.analytics.BrailleImeAnalytics.ContextMenuSelections;
import com.google.android.accessibility.utils.material.MaterialComponentUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

/** A menu that has settings items for Braille keyboard. */
public class ContextMenuDialog extends ViewAttachedDialog {

  /** A callback to communicate with {@link BrailleIme}. */
  public interface Callback {

    void onDialogHidden();

    void onDialogShown();

    void onLaunchSettings();

    void onTutorialOpen();

    void onTutorialClosed();

    void onCalibration();
  }

  private AlertDialog contextMenuDialog;
  private ContextMenuListAdapter contextMenuListAdapter;
  private final BasicActionsDialog basicActionsDialog;
  private final Context context;
  private final List<MenuItem> itemsAndActions;
  private final Callback callback;
  private boolean tutorialMode;

  // LINT.IfChange(menu_options)
  public static final ImmutableList<Integer> ITEM_STRING_IDS =
      ImmutableList.of(
          R.string.context_menu_input_language_selection,
          R.string.context_menu_switch_contracted_status_selection,
          R.string.context_menu_layout_calibration,
          R.string.context_menu_review_gestures_selection,
          R.string.context_menu_tutorial_selection,
          R.string.context_menu_tutorial_finish,
          R.string.context_menu_settings_selection);
  // LINT.ThenChange(//depot/google3/logs/proto/wireless/android/aas/brailleime/brailleime_log.proto:context_menu_option)

  public ContextMenuDialog(Context context, Callback callback) {
    this.context = context;
    this.callback = callback;
    itemsAndActions = new ArrayList<>();
    basicActionsDialog = new BasicActionsMaterialDialog(context, this::dismiss);
  }

  @Override
  public void dismiss() {
    dismissContextMenuDialog();
    dismissBasicActionsDialog();
  }

  @Override
  protected Dialog makeDialog() {
    updateItemsAndActionsList();
    Context dialogContext = Dialogs.getDialogContext(context);
    AlertDialog.Builder dialogBuilder = MaterialComponentUtils.alertDialogBuilder(dialogContext);
    contextMenuListAdapter = new ContextMenuListAdapter(dialogContext, itemsAndActions);
    dialogBuilder
        .setTitle(context.getString(R.string.context_menu_title))
        .setAdapter(contextMenuListAdapter, null)
        // TODO: put onDialogHidden in onDismissListener.
        .setNegativeButton(
            android.R.string.cancel, (dialogInterface, i) -> callback.onDialogHidden())
        .setOnCancelListener(dialogInterface -> callback.onDialogHidden());
    contextMenuDialog = dialogBuilder.create();
    contextMenuDialog.setOnShowListener(dialogInterface -> callback.onDialogShown());
    contextMenuDialog.setCanceledOnTouchOutside(false);
    contextMenuDialog.getListView().setOnItemClickListener(itemClickListener);
    return contextMenuDialog;
  }

  private void showBasicActionsDialog() {
    basicActionsDialog.show(viewToAttach);
  }

  private void dismissContextMenuDialog() {
    if (contextMenuDialog != null) {
      contextMenuDialog.dismiss();
    }
  }

  private void dismissBasicActionsDialog() {
    basicActionsDialog.dismiss();
  }

  private void updateItemsAndActionsList() {
    itemsAndActions.clear();
    for (@StringRes int strRes : ITEM_STRING_IDS) {
      if (strRes == R.string.context_menu_switch_contracted_status_selection) {
        if (!BrailleUserPreferences.readCurrentActiveInputCodeAndCorrect(context)
            .isSupportsContracted(context)) {
          continue;
        }
        int nextContractedStatusStrRes =
            BrailleUserPreferences.readContractedMode(context)
                ? R.string.uncontracted
                : R.string.contracted;
        itemsAndActions.add(
            new MenuItem(
                context.getString(strRes, context.getString(nextContractedStatusStrRes)),
                /* closeWhenClick= */ false,
                generateItemAction(strRes)));
      } else if (strRes == R.string.context_menu_tutorial_selection) {
        if (!tutorialMode) {
          itemsAndActions.add(
              new MenuItem(
                  context.getString(strRes),
                  /* closeWhenClick= */ true,
                  generateItemAction(strRes)));
        }
      } else if (strRes == R.string.context_menu_tutorial_finish) {
        if (tutorialMode) {
          itemsAndActions.add(
              new MenuItem(
                  context.getString(strRes),
                  /* closeWhenClick= */ true,
                  generateItemAction(strRes)));
        }
      } else if (strRes == R.string.context_menu_input_language_selection) {
        if (BrailleUserPreferences.readAvailablePreferredCodes(context).size() <= 1) {
          continue;
        }
        String currentCode =
            BrailleUserPreferences.readCurrentActiveInputCodeAndCorrect(context)
                .getUserFacingName(context);
        String nextCode =
            BrailleUserPreferences.getNextInputCode(context).getUserFacingName(context);
        itemsAndActions.add(
            new MenuItem(
                context.getString(strRes, nextCode),
                context.getString(
                    R.string.context_menu_input_language_selection_summary, currentCode),
                /* closeWhenClick= */ false,
                generateItemAction(strRes)));
      } else if (strRes == R.string.context_menu_settings_selection) {
        // Removes settings if phone is locked.
        KeyguardManager keyguardManager =
            (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (!keyguardManager.isKeyguardLocked()) {
          itemsAndActions.add(
              new MenuItem(
                  context.getString(strRes),
                  /* closeWhenClick= */ true,
                  generateItemAction(strRes)));
        }
      } else if (strRes == R.string.context_menu_layout_calibration) {
        if (!tutorialMode) {
          itemsAndActions.add(
              new MenuItem(
                  context.getString(strRes),
                  /* closeWhenClick= */ true,
                  generateItemAction(strRes)));
        }
      } else {
        itemsAndActions.add(
            new MenuItem(
                context.getString(strRes),
                /* closeWhenClick= */ false,
                generateItemAction(strRes)));
      }
    }
  }

  /** The context menu items varies base on the tutorial mode. */
  public void setTutorialMode(boolean tutorialMode) {
    this.tutorialMode = tutorialMode;
  }

  /** Generates the Runnable for specific selections click actions. */
  private Runnable generateItemAction(@StringRes int strRes) {
    if (strRes == R.string.context_menu_input_language_selection) {
      return () ->
          BrailleUserPreferences.writeCurrentActiveInputCode(
              context, BrailleUserPreferences.getNextInputCode(context));
    } else if (strRes == R.string.context_menu_switch_contracted_status_selection) {
      return () ->
          BrailleUserPreferences.writeContractedMode(
              context, !BrailleUserPreferences.readContractedMode(context));
    } else if (strRes == R.string.context_menu_review_gestures_selection) {
      return this::showBasicActionsDialog;
    } else if (strRes == R.string.context_menu_tutorial_selection) {
      return callback::onTutorialOpen;
    } else if (strRes == R.string.context_menu_tutorial_finish) {
      return callback::onTutorialClosed;
    } else if (strRes == R.string.context_menu_settings_selection) {
      return callback::onLaunchSettings;
    } else if (strRes == R.string.context_menu_layout_calibration) {
      return callback::onCalibration;
    }
    return () -> {};
  }

  private static CharSequence[] convertItemContentToStringArray(
      Context context, List<MenuItem> menuItems) {
    return menuItems.stream()
        .map(
            menuItem -> {
              if (!TextUtils.isEmpty(menuItem.itemSummary)) {
                // Return title concatenated with grey summary string.
                return Utils.appendWithColoredString(
                    menuItem.itemTitle,
                    menuItem.itemSummary,
                    context.getColor(R.color.dialog_secondary_text));
              }
              return menuItem.itemTitle;
            })
        .toArray(CharSequence[]::new);
  }

  private final OnItemClickListener itemClickListener =
      new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
          itemsAndActions.get(i).action.run();
          // Logging option usage count.
          BrailleImeAnalytics.getInstance(context)
              .logContextMenuOptionCount(convertContextMenuSelections(i));
          if (itemsAndActions.get(i).closeWhenClick) {
            contextMenuDialog.dismiss();
          } else {
            updateItemsAndActionsList();
            contextMenuListAdapter.notifyDataSetChanged();
          }
        }

        private ContextMenuSelections convertContextMenuSelections(int optionPosition) {
          int stringRes = ContextMenuDialog.ITEM_STRING_IDS.get(optionPosition);
          if (stringRes == R.string.context_menu_input_language_selection) {
            return ContextMenuSelections.TYPING_LANGUAGE;
          } else if (stringRes == R.string.context_menu_switch_contracted_status_selection) {
            return ContextMenuSelections.SWITCH_CONTRACTED_STATUS;
          } else if (stringRes == R.string.context_menu_review_gestures_selection) {
            return ContextMenuSelections.SEE_ALL_ACTIONS;
          } else if (stringRes == R.string.context_menu_tutorial_selection) {
            return ContextMenuSelections.TUTORIAL_OPEN;
          } else if (stringRes == R.string.context_menu_tutorial_finish) {
            return ContextMenuSelections.TUTORIAL_FINISH;
          } else if (stringRes == R.string.context_menu_settings_selection) {
            return ContextMenuSelections.GO_TO_SETTINGS;
          } else if (stringRes == R.string.context_menu_layout_calibration) {
            return ContextMenuSelections.CALIBRATION;
          }
          return ContextMenuSelections.UNSPECIFIED_OPTION;
        }
      };

  private static class ContextMenuListAdapter extends ArrayAdapter<MenuItem> {
    private final Context context;
    private final List<MenuItem> menuItemList;

    private ContextMenuListAdapter(Context context, List<MenuItem> menuItemList) {
      super(context, android.R.layout.simple_list_item_1, menuItemList);
      this.context = context;
      this.menuItemList = menuItemList;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View view = super.getView(position, convertView, parent);
      TextView textView = view.findViewById(android.R.id.text1);
      textView.setTextSize(
          TypedValue.COMPLEX_UNIT_PX,
          context.getResources().getDimensionPixelSize(R.dimen.context_menu_item_text_size));
      int paddingHorizontalInPixels =
          context
              .getResources()
              .getDimensionPixelOffset(R.dimen.context_menu_item_padding_horizontal);
      textView.setPadding(paddingHorizontalInPixels, 0, paddingHorizontalInPixels, 0);
      textView.setText(convertItemContentToStringArray(context, menuItemList)[position]);
      return view;
    }
  }

  /** Context menu item. */
  public static class MenuItem {
    // Menu item title.
    private final CharSequence itemTitle;
    // Menu item summary.
    private final CharSequence itemSummary;
    // Menu should close after click or not.
    private final boolean closeWhenClick;
    // Menu item click action.
    private final Runnable action;

    private MenuItem(
        CharSequence itemTitle, CharSequence itemSummary, boolean closeWhenClick, Runnable action) {
      this.itemTitle = itemTitle;
      this.itemSummary = itemSummary;
      this.closeWhenClick = closeWhenClick;
      this.action = action;
    }

    private MenuItem(CharSequence itemTitle, boolean closeWhenClick, Runnable action) {
      this(itemTitle, "", closeWhenClick, action);
    }

    public CharSequence getItemTitle() {
      return itemTitle;
    }

    public CharSequence getItemSummary() {
      return itemSummary;
    }
  }
}
