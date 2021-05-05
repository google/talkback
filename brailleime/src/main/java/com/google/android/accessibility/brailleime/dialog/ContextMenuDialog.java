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
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import androidx.annotation.StringRes;
import com.google.android.accessibility.brailleime.BrailleIme;
import com.google.android.accessibility.brailleime.BrailleLanguages;
import com.google.android.accessibility.brailleime.Dialogs;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.brailleime.UserPreferences;
import com.google.android.accessibility.brailleime.Utils;
import com.google.android.accessibility.brailleime.analytics.BrailleAnalytics;
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

    void onSwitchContractedMode();

    void onTutorialOpen();

    void onTutorialClosed();

    void onTypingLanguageChanged();

    boolean isLanguageContractionSupported();
  }

  private AlertDialog contextMenuDialog;
  private final SeeAllActionsDialog seeAllActionsDialog;
  private final TypingLanguageDialog typingLanguageDialog;
  private final Context context;
  private final List<MenuItem> itemsAndActions;
  private final Callback callback;
  private final SeeAllActionsDialog.Callback seeAllActionsCallback = () -> show(viewToAttach);
  private final TypingLanguageDialog.Callback typingLanguageCallback =
      new TypingLanguageDialog.Callback() {
        @Override
        public void showContextMenu() {
          show(viewToAttach);
        }

        @Override
        public void onTypingLanguageChanged() {
          callback.onTypingLanguageChanged();
        }
      };
  private boolean tutorialMode;

  // LINT.IfChange(menu_options)
  public static final ImmutableList<Integer> ITEM_STRING_IDS =
      ImmutableList.of(
          R.string.context_menu_typing_language_selection,
          R.string.context_menu_switch_contracted_status_selection,
          R.string.context_menu_see_all_gestures_selection,
          R.string.context_menu_tutorial_selection,
          R.string.context_menu_tutorial_finish,
          R.string.context_menu_settings_selection);
  // LINT.ThenChange(//depot/google3/logs/proto/wireless/android/aas/brailleime/brailleime_log.proto:context_menu_option)

  public ContextMenuDialog(Context context, Callback callback) {
    this.context = context;
    this.callback = callback;
    itemsAndActions = new ArrayList<>();
    seeAllActionsDialog = new SeeAllActionsDialog(context, seeAllActionsCallback);
    typingLanguageDialog = new TypingLanguageDialog(context, typingLanguageCallback);
  }

  @Override
  public void dismiss() {
    dismissContextMenuDialog();
    dismissSeeAllActionsDialog();
    dismissTypingLanguageDialog();
  }

  @Override
  protected Dialog makeDialog() {
    updateItemsAndActionsList();
    AlertDialog.Builder dialogBuilder = Dialogs.getAlertDialogBuilder(context);
    CharSequence[] items = convertItemContentToStringArray(context, itemsAndActions);
    dialogBuilder
        .setTitle(context.getString(R.string.context_menu_title))
        .setItems(
            items,
            (dialog, which) -> {
              itemsAndActions.get(which).action.run();
              // Logging option usage count.
              BrailleAnalytics.getInstance(ContextMenuDialog.this.context)
                  .logContextMenuOptionCount(which);
            })
        // TODO: put onDialogHidden in onDismissListener.
        .setNegativeButton(
            android.R.string.cancel, (dialogInterface, i) -> callback.onDialogHidden())
        .setOnCancelListener(dialogInterface -> callback.onDialogHidden());
    contextMenuDialog = dialogBuilder.create();
    contextMenuDialog.setOnShowListener(dialogInterface -> callback.onDialogShown());
    contextMenuDialog.setCanceledOnTouchOutside(false);
    return contextMenuDialog;
  }

  private void showSeeAllActionsDialog() {
    seeAllActionsDialog.show(viewToAttach);
  }

  private void showTypingLanguageDialog() {
    typingLanguageDialog.show(viewToAttach);
  }

  private void dismissContextMenuDialog() {
    if (contextMenuDialog != null) {
      contextMenuDialog.dismiss();
    }
  }

  private void dismissSeeAllActionsDialog() {
    seeAllActionsDialog.dismiss();
  }

  private void dismissTypingLanguageDialog() {
    typingLanguageDialog.dismiss();
  }

  private void updateItemsAndActionsList() {
    itemsAndActions.clear();
    for (@StringRes int strRes : ITEM_STRING_IDS) {
      if (strRes == R.string.context_menu_switch_contracted_status_selection) {
        if (!callback.isLanguageContractionSupported()) {
          continue;
        }
        int nextContractedStatusStrRes =
            UserPreferences.readContractedMode(context)
                ? R.string.uncontracted
                : R.string.contracted;
        itemsAndActions.add(
            new MenuItem(
                context.getString(strRes, context.getString(nextContractedStatusStrRes)),
                generateItemAction(strRes)));
      } else if (strRes == R.string.context_menu_tutorial_selection) {
        if (!tutorialMode) {
          itemsAndActions.add(new MenuItem(context.getString(strRes), generateItemAction(strRes)));
        }
      } else if (strRes == R.string.context_menu_tutorial_finish) {
        if (tutorialMode) {
          itemsAndActions.add(new MenuItem(context.getString(strRes), generateItemAction(strRes)));
        }
      } else if (strRes == R.string.context_menu_typing_language_selection) {
        if (BrailleLanguages.getSelectedCodes(context).size() <= 1) {
          continue;
        }
        CharSequence currentLanguage =
            BrailleLanguages.getCurrentCodeAndCorrect(context)
                .getUserFacingName(context.getResources());
        itemsAndActions.add(
            new MenuItem(
                context.getString(strRes),
                context.getString(
                    R.string.context_menu_typing_language_selection_summary, currentLanguage),
                generateItemAction(strRes)));
      } else {
        itemsAndActions.add(new MenuItem(context.getString(strRes), generateItemAction(strRes)));
      }
    }
  }

  /** The context menu items varies base on the tutorial mode. */
  public void setTutorialMode(boolean tutorialMode) {
    this.tutorialMode = tutorialMode;
  }

  /** Generates the Runnable for specific selections click actions. */
  private Runnable generateItemAction(@StringRes int strRes) {
    if (strRes == R.string.context_menu_typing_language_selection) {
      return this::showTypingLanguageDialog;
    } else if (strRes == R.string.context_menu_switch_contracted_status_selection) {
      return callback::onSwitchContractedMode;
    } else if (strRes == R.string.context_menu_see_all_gestures_selection) {
      return this::showSeeAllActionsDialog;
    } else if (strRes == R.string.context_menu_tutorial_selection) {
      return callback::onTutorialOpen;
    } else if (strRes == R.string.context_menu_tutorial_finish) {
      return callback::onTutorialClosed;
    } else if (strRes == R.string.context_menu_settings_selection) {
      return callback::onLaunchSettings;
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
                    context.getColor(R.color.google_grey700));
              }
              return menuItem.itemTitle;
            })
        .toArray(CharSequence[]::new);
  }

  private static class MenuItem {
    // Menu item title.
    private final CharSequence itemTitle;
    // Menu item summary.
    private final CharSequence itemSummary;
    // Menu item click action.
    private final Runnable action;

    private MenuItem(CharSequence itemTitle, CharSequence itemSummary, Runnable action) {
      this.itemTitle = itemTitle;
      this.itemSummary = itemSummary;
      this.action = action;
    }

    private MenuItem(CharSequence itemTitle, Runnable action) {
      this(itemTitle, "", action);
    }
  }
}
