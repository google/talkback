/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.preference.PreferenceFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.annotation.XmlRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Utilities for Android Preference Screens. */
public final class PreferenceSettingsUtils {
  // Cannot be instantiated.
  private PreferenceSettingsUtils() {}

  /**
   * Given an array of resource ids corresponding to the actual String keys for TalkBack
   * preferences, hide the preferences. This will also be done recursively form the root
   * PreferenceGroup.
   */
  public static void hidePreferences(
      final Context context, PreferenceGroup root, int[] prefKeyIds) {
    if (context == null) {
      return;
    }
    Set<String> hiddenPreferenceKeys = new HashSet<>();
    for (int hiddenPreferenceKeyId : prefKeyIds) {
      hiddenPreferenceKeys.add(context.getString(hiddenPreferenceKeyId));
    }
    hidePreferences(root, hiddenPreferenceKeys);
  }

  /**
   * Given a list of resource ids corresponding to the actual String keys for TalkBack preferences,
   * hide the preferences. This will also be done recursively form the root PreferenceGroup.
   */
  public static void hidePreferences(
      final Context context, PreferenceGroup root, List<Integer> prefKeyIds) {
    if (context == null) {
      return;
    }
    Set<String> hiddenPreferenceKeys = new HashSet<>();
    for (Integer hiddenPreferenceKeyId : prefKeyIds) {
      hiddenPreferenceKeys.add(context.getString(hiddenPreferenceKeyId.intValue()));
    }
    hidePreferences(root, hiddenPreferenceKeys);
  }

  private static void hidePreferences(PreferenceGroup root, Set<String> preferenceKeysToBeHidden) {
    for (int i = 0; i < root.getPreferenceCount(); i++) {
      Preference preference = root.getPreference(i);
      if (preferenceKeysToBeHidden.contains(preference.getKey())) {
        root.removePreference(preference);
        i--;
      } else if (preference instanceof PreferenceGroup) {
        hidePreferences((PreferenceGroup) preference, preferenceKeysToBeHidden);
      }
    }
  }

  /**
   * Given the resource id corresponding to the actual String key for TalkBack preference, hide the
   * preference. This will also be done recursively form the root PreferenceGroup.
   */
  public static void hidePreference(final Context context, PreferenceGroup root, int prefKeyId) {
    hidePreferences(context, root, new int[] {prefKeyId});
  }

  /**
   * Inflates the given XML resource and replaces the current preference hierarchy (if any) with the
   * preference hierarchy rooted at {@code key}.{@link
   * PreferenceFragmentCompat#setPreferencesFromResource(int, String)}
   */
  public static void setPreferencesFromResource(
      PreferenceFragmentCompat preferenceFragment, @XmlRes int preferencesResId, String key) {
    // Set preferences to use device-protected storage.
    if (BuildVersionUtils.isAtLeastN()) {
      preferenceFragment.getPreferenceManager().setStorageDeviceProtected();
    }
    preferenceFragment.setPreferencesFromResource(preferencesResId, key);
  }

  /**
   * Inflates the given XML resource and adds the preference hierarchy to the current preference
   * hierarchy. {@link PreferenceFragmentCompat#addPreferencesFromResource}
   */
  public static void addPreferencesFromResource(
      PreferenceFragmentCompat preferenceFragment, @XmlRes int preferencesResId) {
    // Set preferences to use device-protected storage.
    if (BuildVersionUtils.isAtLeastN()) {
      preferenceFragment.getPreferenceManager().setStorageDeviceProtected();
    }
    preferenceFragment.addPreferencesFromResource(preferencesResId);
  }

  /**
   * Inflates the given XML resource and adds the preference hierarchy to the current preference
   * hierarchy. {@link PreferenceFragment#addPreferencesFromResource}
   */
  public static void addPreferencesFromResource(
      PreferenceFragment preferenceFragment, @XmlRes int preferencesResId) {
    // Set preferences to use device-protected storage.
    if (BuildVersionUtils.isAtLeastN()) {
      preferenceFragment.getPreferenceManager().setStorageDeviceProtected();
    }
    preferenceFragment.addPreferencesFromResource(preferencesResId);
  }

  /**
   * Assigns an URL intent to the preference. When clicking the preference, it would jump to URL.
   */
  public static void assignWebIntentToPreference(
      PreferenceFragmentCompat fragment, Preference preference, String url) {
    if (!SettingsUtils.allowLinksOutOfSettings(fragment.getContext())) {
      return;
    }

    final boolean isWatch = FeatureSupport.isWatch(fragment.getContext());
    Uri uri = Uri.parse(url);
    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
    Activity activity = fragment.getActivity();
    if (activity != null) {
      if (isWatch) {
        intent = RemoteIntentUtils.intentToOpenUriOnPhone(uri);
      } else if (!systemCanHandleIntent(fragment.getActivity(), intent)) {
        intent = new Intent(activity, WebActivity.class);
        intent.setData(uri);
      }
    }

    preference.setIntent(intent);
  }

  /** Checks if the intent could be performed by a system. */
  private static boolean systemCanHandleIntent(Activity activity, Intent intent) {
    if (activity == null) {
      return false;
    }

    PackageManager manager = activity.getPackageManager();
    List<ResolveInfo> infos = manager.queryIntentActivities(intent, 0);
    return infos != null && !infos.isEmpty();
  }

  /**
   * Finds the preference by the key Id.
   *
   * @param activity FragmentActivity which contain Fragments.
   * @param prefKeyId key Id of Preference which likes to find
   * @return Preference by search key.
   */
  public static @Nullable Preference findPreference(FragmentActivity activity, int prefKeyId) {
    return findPreference(activity, activity.getString(prefKeyId));
  }

  /**
   * Finds the preference by the key string.
   *
   * @param activity FragmentActivity which contain Fragments.
   * @param key key string of Preference which likes to find
   * @return Preference by search key.
   */
  public static @Nullable Preference findPreference(FragmentActivity activity, String key) {
    List<Fragment> fragments = activity.getSupportFragmentManager().getFragments();
    for (Fragment fragment : fragments) {
      PreferenceFragmentCompat preferenceFragment = (PreferenceFragmentCompat) fragment;
      Preference preference = preferenceFragment.findPreference(key);
      if (preference != null) {
        return preference;
      }
    }

    return null;
  }
}
