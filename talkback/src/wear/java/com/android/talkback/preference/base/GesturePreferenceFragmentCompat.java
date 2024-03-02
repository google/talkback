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

package com.google.android.accessibility.talkback.preference.base;

import static com.google.android.accessibility.talkback.preference.base.GestureListPreference.TYPE_ACTION_ITEM;
import static com.google.android.accessibility.talkback.preference.base.GestureListPreference.TYPE_TITLE;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.LayoutManager;
import androidx.recyclerview.widget.RecyclerView.Recycler;
import androidx.recyclerview.widget.RecyclerView.State;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import com.google.android.accessibility.talkback.preference.base.GestureListPreference.ActionItem;
import com.google.android.accessibility.utils.R;
import com.google.android.accessibility.utils.material.WrapSwipeDismissLayoutHelper;
import com.google.android.accessibility.utils.preference.AccessibilitySuitePreferenceCategory;
import com.google.android.accessibility.utils.preference.AccessibilitySuiteRadioButtonPreference;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import javax.annotation.Nullable;

/**
 * A fragment contains a customized wear material list view for TalkBack supported actions.
 *
 * <p>Note: This class is only for Wear.
 */
public class GesturePreferenceFragmentCompat extends TalkbackBaseFragment {
  private static final String TAG = "GesturePreferenceFragmentCompat";

  private final OnPreferenceChangeListener onPreferenceChangeListener =
      new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
          boolean checked = (Boolean) newValue;
          if (!checked) {
            // The selected action is clicked again and it is going to be unchecked. We should
            // return false to ignore it and don't set the value to targetGestureListPreference.
            popBackStack();
            return false;
          }

          ActionItem item = targetGestureListPreference.getActionItems()[preference.getOrder()];
          targetGestureListPreference.setValue(item.value);
          targetGestureListPreference.setSummary(item.text);

          popBackStack();
          return true;
        }
      };

  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable focusSelectedPreferenceRunnable =
      () -> {
        RecyclerView recyclerView = getListView();
        if (recyclerView == null) {
          LogUtils.w(TAG, "RecyclerView has been cleared.");
          return;
        }

        ViewHolder viewHolder =
            recyclerView.findViewHolderForAdapterPosition(getSelectedPositionInAdapter());
        if (viewHolder != null) {
          viewHolder.itemView.requestFocus();
          viewHolder.itemView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        }
      };
  private final Runnable scrollRunnable =
      () -> {
        RecyclerView recyclerView = getListView();
        if (recyclerView == null) {
          LogUtils.w(TAG, "RecyclerView has been cleared.");
          return;
        }

        LinearLayoutManager linearLayoutManager =
            (LinearLayoutManager) recyclerView.getLayoutManager();
        if (linearLayoutManager != null) {
          linearLayoutManager.scrollToPositionWithOffset(getSelectedPositionInAdapter(), 0);
          handler.postDelayed(focusSelectedPreferenceRunnable, 100);
        }
      };

  private View targetFragmentView;
  private GestureListPreference targetGestureListPreference;
  private int selectedPosition;

  /** Creates the fragment from given {@link GestureListPreference}. */
  public static GesturePreferenceFragmentCompat create(GestureListPreference preference) {
    GesturePreferenceFragmentCompat fragment = new GesturePreferenceFragmentCompat();
    Bundle args = new Bundle(1);
    args.putString(ARG_PREFERENCE_ROOT, preference.getKey());
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = super.onCreateView(inflater, container, savedInstanceState);
    // Setting accessibility pane title can make TB try to find an initial focus again.
    ViewCompat.setAccessibilityPaneTitle(view, getTitle());
    // Intentionally set it as a background color. Or, it will be a transparent background.
    view.setBackgroundColor(
        getResources()
            .getColor(R.color.a11y_wear_material_color_background, getContext().getTheme()));
    return view;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    if (savedInstanceState == null) {
      // We post a runnable to scroll to the selected position in the 1st time.
      handler.post(scrollRunnable);

      requireActivity()
          .getOnBackPressedDispatcher()
          .addCallback(
              this,
              new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                  popBackStack();
                }
              });
    }
  }

  @NonNull
  @Override
  public LayoutManager onCreateLayoutManager() {
    return new LinearLayoutManager(requireContext()) {
      @Override
      public int getSelectionModeForAccessibility(
          @NonNull Recycler recycler, @NonNull State state) {
        return CollectionInfoCompat.SELECTION_MODE_SINGLE;
      }
    };
  }

  @Override
  protected View wrapSwipeDismissLayout(View view) {
    // We override it since we want to pop the fragment in the parent fragment manager.
    return WrapSwipeDismissLayoutHelper.wrapSwipeDismissLayout(
        getActivity(),
        view,
        activity -> {
          popBackStack();
          return true;
        });
  }

  /** Pops back the fragment and restores the a11y importance attribute for the parent fragment. */
  private void popBackStack() {
    // When we pop back to the parent fragment, we need to restore the a11y importance attribute.
    targetFragmentView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    // To support Wear rotary input.
    targetFragmentView.requestFocus();
    getParentFragmentManager().popBackStackImmediate();
  }

  @Override
  protected CharSequence getTitle() {
    return targetGestureListPreference.getTitle();
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
    PreferenceFragmentCompat fragment = (PreferenceFragmentCompat) getTargetFragment();
    targetFragmentView = fragment.requireView();
    // We add this fragment onto the parent fragment, so we need to hide the parent's views.
    targetFragmentView.setImportantForAccessibility(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    targetGestureListPreference = fragment.findPreference(rootKey);
    setPreferenceScreen(createPreferenceScreen());
  }

  private PreferenceScreen createPreferenceScreen() {
    PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(getContext());
    String initialValue = targetGestureListPreference.getCurrentValue();
    ActionItem[] items = targetGestureListPreference.getActionItems();
    Context context = getContext();

    AccessibilitySuitePreferenceCategory category = null;
    ActionItem item;
    for (int order = 0; order < items.length; order++) {
      item = items[order];
      switch (item.viewType) {
        case TYPE_TITLE:
          category = new AccessibilitySuitePreferenceCategory(getContext());
          category.setTitle(item.text);
          preferenceScreen.addPreference(category);
          break;
        case TYPE_ACTION_ITEM:
          AccessibilitySuiteRadioButtonPreference radioButtonPreference =
              new AccessibilitySuiteRadioButtonPreference(context);
          radioButtonPreference.setTitle(item.text);
          boolean checked = TextUtils.equals(item.value, initialValue);
          radioButtonPreference.setChecked(checked);
          radioButtonPreference.setOnPreferenceChangeListener(onPreferenceChangeListener);
          radioButtonPreference.setOrder(order);
          if (category == null) {
            // We create a category without title to add the beginning preference (e.g., "Tap to
            // assign").
            category = new AccessibilitySuitePreferenceCategory(getContext());
            preferenceScreen.addPreference(category);
          }
          category.addPreference(radioButtonPreference);
          if (checked) {
            selectedPosition = order;
          }
          break;
        default: // fall out
      }
    }

    return preferenceScreen;
  }

  private int getSelectedPositionInAdapter() {
    // In the WearPreferenceFragment, the ConcatAdapter has 2 views (the id/title and the
    // divider) before the 1st action item view. Hence, we add 2 to the position parameter.
    return selectedPosition + 2;
  }
}
