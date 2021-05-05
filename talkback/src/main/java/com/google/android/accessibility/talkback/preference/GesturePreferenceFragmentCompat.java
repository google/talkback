/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.android.accessibility.talkback.preference;

import static com.google.android.accessibility.talkback.preference.GestureListPreference.TYPE_ACTION_ITEM;
import static com.google.android.accessibility.talkback.preference.GestureListPreference.TYPE_TITLE;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.ViewCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;
import androidx.preference.PreferenceDialogFragmentCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.preference.GestureListPreference.ActionItem;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** A dialog fragment contains a customized list view for TalkBack supported actions. */
public class GesturePreferenceFragmentCompat extends PreferenceDialogFragmentCompat {
  private static final String TAG = "GesturePreferenceFragmentCompat";
  private static final String ARG_ACTIONS = "actions";

  /** Creates the fragment from given {@link GestureListPreference}. */
  public static GesturePreferenceFragmentCompat newInstance(GestureListPreference preference) {
    GesturePreferenceFragmentCompat fragment = new GesturePreferenceFragmentCompat();
    Bundle args = new Bundle(2);
    args.putString(PreferenceDialogFragmentCompat.ARG_KEY, preference.getKey());
    args.putParcelableArray(ARG_ACTIONS, preference.getActionItems());
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  protected View onCreateDialogView(Context context) {
    ListView listView = new ListView(getActivity());
    Parcelable[] items = getArguments().getParcelableArray(ARG_ACTIONS);
    ActionAdapter adapter = new ActionAdapter(getActivity(), items, getPreferenceValue());

    listView.setAdapter(adapter);
    listView.setBackground(null);
    listView.setDivider(null);
    listView.setOnItemClickListener(this::selectActionInList);

    // Set the initial position to the checked item in list.
    String value = getPreferenceValue();
    for (int i = 0; i < items.length; ++i) {
      if (items[i] instanceof ActionItem) {
        ActionItem item = (ActionItem) items[i];
        if (TextUtils.equals(item.value, value)) {
          int initialPosition = i;
          listView.post(() -> listView.setSelection(initialPosition));
          break;
        }
      }
    }

    return listView;
  }

  @Override
  public void onDialogClosed(boolean positiveResult) {}

  @VisibleForTesting
  void selectActionInList(AdapterView<?> parent, View view, int position, long unusedId) {
    if (!(view instanceof CheckedTextView)) {
      return;
    }

    CheckedTextView checkedTextView = (CheckedTextView) view;
    if (checkedTextView.isChecked()) {
      // To avoid storing the default value, we don't set the value to the preference if
      // it doesn't change.
      dismiss();
      return;
    }

    checkedTextView.setChecked(true);
    ActionItem item = (ActionItem) parent.getItemAtPosition(position);
    setValue(item.value);
    getPreference().setSummary(item.text);
    dismiss();
  }

  private void setValue(String value) {
    if (getPreference() instanceof GestureListPreference) {
      ((GestureListPreference) getPreference()).setValue(value);

      return;
    }

    LogUtils.e(
        TAG, "Unexpected usage, the preference fragment should work with a GestureListPreference.");
  }

  @Nullable
  private String getPreferenceValue() {
    if (getPreference() instanceof GestureListPreference) {
      return ((GestureListPreference) getPreference()).getCurrentValue();
    }

    LogUtils.e(
        TAG, "Unexpected usage, the preference fragment should work with a GestureListPreference.");
    return null;
  }

  /**
   * A {@link BaseAdapter} for presenting {@link ActionItem}. It supports two styles of view: the
   * category heading, which contains a single text, and the shortcut text with a checkable button.
   */
  @VisibleForTesting
  protected static class ActionAdapter extends BaseAdapter {
    final Context context;
    final Parcelable[] actionItems;
    final String initialValue;

    ActionAdapter(Context context, Parcelable[] actionItems, String initialValue) {
      this.context = context;
      this.actionItems = actionItems;
      this.initialValue = initialValue;
    }

    @Override
    public int getCount() {
      return actionItems.length;
    }

    @Override
    public Object getItem(int position) {
      return actionItems[position];
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public boolean isEnabled(int position) {
      if (!(actionItems[position] instanceof ActionItem)) {
        return false;
      }

      ActionItem item = (ActionItem) actionItems[position];
      return item.viewType == TYPE_ACTION_ITEM;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      if (!(actionItems[position] instanceof ActionItem)) {
        return convertView;
      }

      final View view;
      ActionItem item = (ActionItem) actionItems[position];
      switch (item.viewType) {
          // A Checkable text view for shortcuts.
        case TYPE_ACTION_ITEM:
          view = LayoutInflater.from(context).inflate(R.layout.list_item_radio, parent, false);
          CheckedTextView checkedTextView = (CheckedTextView) view;
          checkedTextView.setText(item.text);
          if (TextUtils.equals(item.value, initialValue)) {
            checkedTextView.setChecked(true);
          } else {
            checkedTextView.setChecked(false);
          }

          return view;

          // Single text view for shortcut categories.
        case TYPE_TITLE:
        default:
          view = LayoutInflater.from(context).inflate(R.layout.list_item_category, parent, false);
          TextView textView = (TextView) view;
          ViewCompat.setAccessibilityHeading(textView, true);
          textView.setText(item.text);
          return view;
      }
    }
  }
}
