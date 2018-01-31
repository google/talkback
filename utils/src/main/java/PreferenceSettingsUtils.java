package com.google.android.accessibility.utils;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import java.util.HashSet;
import java.util.Set;

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
   * Given the resource id corresponding to the actual String key for TalkBack preference, hide the
   * preference. This will also be done recursively form the root PreferenceGroup.
   */
  public static void hidePreference(final Context context, PreferenceGroup root, int prefKeyId) {
    hidePreferences(context, root, new int[] {prefKeyId});
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
}
