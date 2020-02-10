/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.talkback.menurules;

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.RangeInfoCompat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItemBuilder;
import com.google.android.accessibility.talkback.dialog.BaseDialog;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Provides a LCM item to manually enter a percentage value for seek controls. This functionality is
 * only available on Android N and later.
 */
public class RuleSeekBar implements NodeMenuRule {

  private final Pipeline.FeedbackReturner pipeline;

  public RuleSeekBar(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  @Override
  public boolean accept(TalkBackService service, AccessibilityNodeInfoCompat node) {
    if (!BuildVersionUtils.isAtLeastN()) {
      return false;
    }

    return AccessibilityNodeInfoUtils.supportsAction(
        node, AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId());
  }

  @Override
  public List<ContextMenuItem> getMenuItemsForNode(
      TalkBackService service,
      ContextMenuItemBuilder menuItemBuilder,
      AccessibilityNodeInfoCompat node,
      boolean includeAncestors) {
    List<ContextMenuItem> items = new ArrayList<>();

    if (node != null) {
      final ContextMenuItem setLevel =
          menuItemBuilder.createMenuItem(
              service,
              Menu.NONE,
              R.id.seekbar_breakout_set_level,
              Menu.NONE,
              service.getString(R.string.title_seek_bar_edit));
      setLevel.setOnMenuItemClickListener(new SeekBarDialogManager(service, node, pipeline));
      setLevel.setShowsAlertDialog(true);
      items.add(setLevel);
    }

    return items;
  }

  @Override
  public CharSequence getUserFriendlyMenuName(Context context) {
    return context.getString(R.string.title_seek_bar_controls);
  }

  @Override
  public boolean canCollapseMenu() {
    return true;
  }

  private static int realToPercent(float real, float min, float max) {
    return (int) (100.0f * (real - min) / (max - min));
  }

  private static float percentToReal(int percent, float min, float max) {
    return min + (percent / 100.0f) * (max - min);
  }

  // Separate package-private method so we can test the logic.
  static void setProgress(AccessibilityNodeInfoCompat node, int progress) {
    RangeInfoCompat rangeInfo = node.getRangeInfo();
    if (rangeInfo != null && progress >= 0 && progress <= 100) {
      Bundle args = new Bundle();
      args.putFloat(
          AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,
          percentToReal(progress, rangeInfo.getMin(), rangeInfo.getMax()));
      EventId eventId = EVENT_ID_UNTRACKED; // Performance not tracked for menu events.
      PerformActionUtils.performAction(
          node, AccessibilityAction.ACTION_SET_PROGRESS.getId(), args, eventId);
    }
  }

  // Deals with opening the dialog from the menu item and controlling the dialog lifecycle.
  private static class SeekBarDialogManager extends BaseDialog
      implements MenuItem.OnMenuItemClickListener {
    private static final int INVALID_VALUE = -1;

    @Nullable private AccessibilityNodeInfoCompat seekBar; // Note: not final so we can null it out.
    private int oldValue = INVALID_VALUE;
    private int value = INVALID_VALUE;
    @Nullable private EditText editText;

    public SeekBarDialogManager(
        TalkBackService service,
        AccessibilityNodeInfoCompat seekBar,
        Pipeline.FeedbackReturner pipeline) {
      super(service, R.string.title_seek_bar_edit, pipeline);
      this.seekBar = AccessibilityNodeInfoCompat.obtain(seekBar);
      this.setIsFromLocalContextMenu(true);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      // Verify that node is OK and get the current seek control level first.
      final RangeInfoCompat rangeInfo = seekBar.getRangeInfo();
      if (rangeInfo == null) {
        return false;
      }

      oldValue = realToPercent(rangeInfo.getCurrent(), rangeInfo.getMin(), rangeInfo.getMax());
      if (showDialog() != null) {
        editText.setText(Integer.toString(oldValue));
        editText.setOnEditorActionListener(
            (v, actionId, event) -> {
              if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitDialog();
                return true;
              }
              return false;
            });
      }
      return true;
    }

    private void submitDialog() {
      if (editText == null) {
        return;
      }

      try {
        int percentValue = Integer.parseInt(editText.getText().toString());
        if (percentValue < 0 || percentValue > 100) {
          throw new IndexOutOfBoundsException();
        }

        // Need to delay setting value until the dialog is dismissed.
        value = percentValue;
        dismissDialog();
      } catch (NumberFormatException | IndexOutOfBoundsException ex) {
        // Set the error text popup.
        CharSequence instructions = context.getString(R.string.value_seek_bar_dialog_instructions);
        editText.setError(instructions);
      }
    }

    @Override
    public void handleDialogClick(int buttonClicked) {
      if (buttonClicked == DialogInterface.BUTTON_POSITIVE) {
        submitDialog();
      }
    }

    @Override
    public void handleDialogDismiss() {
      if (seekBar == null) {
        return;
      }
      // This will only set the value if the user clicked "OK" because only "OK" will
      // change the value field to not be INVALID_VALUE.
      if (value != INVALID_VALUE && value != oldValue) {
        setProgress(seekBar, value);
      }
      seekBar.recycle();
      seekBar = null;
      editText = null;
    }

    @Override
    public String getMessageString() {
      return null;
    }

    @Override
    public View getCustomizedView() {
      LayoutInflater inflater = LayoutInflater.from(context);
      View rootView = inflater.inflate(R.layout.seekbar_level_dialog, null);
      editText = rootView.findViewById(R.id.seek_bar_level);
      return rootView;
    }
  }
}
