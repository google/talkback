/*
 * Copyright (C) 2018 Google Inc.
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
package com.google.android.accessibility.switchaccess;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import com.android.switchaccess.SwitchAccessService;
import com.google.android.accessibility.switchaccess.setupwizard.SetupWizardActivity;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Caches preferences via {@link SwitchAccessPreferenceUtils} and listens for changes to preferences
 * so that stale values can be removed. This class should only be accessed directly via {@link
 * SwitchAccessPreferenceUtils}.
 */
public final class SwitchAccessPreferenceCache implements OnSharedPreferenceChangeListener {
  private final HashMap<String, Object> preferenceKeyMap = new HashMap<>();

  @Nullable private static SwitchAccessPreferenceCache instance;

  /* Two separate contexts are needed to ensure that the cache isn't shut down too soon. */

  /** If SwitchAccessService needs the cache. */
  private boolean serviceNeedsCache;
  /** If SwitchAccessPreferenceActivity needs the cache. */
  private boolean preferenceActivityNeedsCache;
  /** If SetupWizardActivity needs the cache. */
  private boolean setupWizardActivityNeedsCache;

  /** Listeners that receive a callback when a Switch Access preference value has changed. */
  private List<SwitchAccessPreferenceChangedListener> listeners = new ArrayList<>();

  // This class should not be instantiated from outside this class.
  private SwitchAccessPreferenceCache(SwitchAccessService service) {
    serviceNeedsCache = true;
    registerOnSharedPreferenceChangeListener(service);
  }

  private SwitchAccessPreferenceCache(SwitchAccessPreferenceActivity activity) {
    preferenceActivityNeedsCache = true;
    registerOnSharedPreferenceChangeListener(activity);
  }

  private SwitchAccessPreferenceCache(SetupWizardActivity activity) {
    setupWizardActivityNeedsCache = true;
    registerOnSharedPreferenceChangeListener(activity);
  }

  /**
   * Gets the instance of {@link SwitchAccessPreferenceCache} or creates an instance if it doesn't
   * exist.
   *
   * <p>This method should only be called from {@link SwitchAccessPreferenceUtils}.
   *
   * @param context The context that will be used to create a cache if one does not already exist.
   *     Context must be of type SwitchAccessService, SwitchAccessPreferenceActivity, or
   *     SetupWizardActivity
   * @return The current instance of {@link SwitchAccessPreferenceCache} or {@code null} if the
   *     given context is not one of the above listed types
   */
  @Nullable
  public static SwitchAccessPreferenceCache getOrCreateInstance(Context context) {
    if (context instanceof SwitchAccessService) {
      if (instance == null) {
        instance = new SwitchAccessPreferenceCache((SwitchAccessService) context);
      } else if (!instance.serviceNeedsCache && (SwitchAccessService.getInstance() != null)) {
        // Check if SwitchAccessService.getInstance() is non-null to ensure that stale
        // SwitchAccessService values aren't registered to the cache.
        instance.serviceNeedsCache = true;
      }
    } else if (context instanceof SwitchAccessPreferenceActivity) {
      if (instance == null) {
        instance = new SwitchAccessPreferenceCache((SwitchAccessPreferenceActivity) context);
      } else if (!instance.preferenceActivityNeedsCache) {
        instance.preferenceActivityNeedsCache = true;
      }
    } else if (context instanceof SetupWizardActivity) {
      if (instance == null) {
        instance = new SwitchAccessPreferenceCache((SetupWizardActivity) context);
      } else if (!instance.setupWizardActivityNeedsCache) {
        instance.setupWizardActivityNeedsCache = true;
      }
    } else if (FeatureFlags.crashOnError()) {
      // Context is of an invalid type, so throw an exception on debug builds.
      throw new IllegalArgumentException(
          "Invalid context. Context must be SwitchAccessService, SwitchAccessPreferenceActivity, "
              + "or SetupWizardActivity.");
    }

    return instance;
  }

  /**
   * Gets the instance of {@link SwitchAccessPreferenceCache} if one exists, or {@code null}
   * otherwise.
   *
   * @return The current instance of {@link SwitchAccessPreferenceCache}
   */
  @Nullable
  public static SwitchAccessPreferenceCache getInstanceIfExists() {
    return instance;
  }

  /**
   * Unregister the context's OnSharedPreferenceChangeListener. Remove the current cache instance if
   * it exists and all contexts are unregistered.
   *
   * @param activity The SetupWizardActivity context that no longer needs the cache
   */
  public static void shutdownIfInitialized(SetupWizardActivity activity) {
    if ((instance == null) || (activity == null)) {
      return;
    }
    instance.setupWizardActivityNeedsCache = false;

    if (!instance.serviceNeedsCache && !instance.preferenceActivityNeedsCache) {
      instance.clearCacheAndUnregisterSharedPreferenceChangeListener(activity);

      // Don't reset the cache instance unless both the activity and serviceNeedsCache have
      // shutdown.
      instance = null;
    }
  }

  /**
   * Unregister the context's OnSharedPreferenceChangeListener. Remove the current cache instance if
   * it exists and all contexts are unregistered.
   *
   * @param activity The SwitchAccessPreferenceActivity context that no longer needs the cache
   */
  public static void shutdownIfInitialized(SwitchAccessPreferenceActivity activity) {
    if ((instance == null) || (activity == null)) {
      return;
    }
    instance.preferenceActivityNeedsCache = false;

    if (!instance.serviceNeedsCache && !instance.setupWizardActivityNeedsCache) {
      instance.clearCacheAndUnregisterSharedPreferenceChangeListener(activity);

      // Don't reset the cache instance unless both the activity and service have
      // shutdown.
      instance = null;
    }
  }

  /**
   * Unregister the context's OnSharedPreferenceChangeListener. Remove the current cache instance if
   * it exists and all contexts are unregistered.
   *
   * @param service The service context that no longer needs the cache
   */
  public static void shutdownIfInitialized(SwitchAccessService service) {
    if ((instance == null) || (service == null)) {
      return;
    }
    instance.serviceNeedsCache = false;

    if (!instance.setupWizardActivityNeedsCache && !instance.preferenceActivityNeedsCache) {
      instance.clearCacheAndUnregisterSharedPreferenceChangeListener(service);

      // Don't reset the cache instance unless both the activity and service have
      // shutdown.
      instance = null;
    }
  }

  /**
   * Registers a listener to be notified whenever a preference changes.
   *
   * <p>Listeners don't persist after {@link SwitchAccessPreferenceCache#shutdownIfInitialized} is
   * called.
   *
   * @param listener The listener to notify whenever a preference changes
   */
  // List#add expects a fully initialized object but we need to register the listeners during their
  // construction so they can begin listening immediately.
  @SuppressWarnings("initialization:argument.type.incompatible")
  public void registerSwitchAccessCachedPreferenceChangeListener(
      @UnknownInitialization SwitchAccessPreferenceChangedListener listener) {
    listeners.add(listener);
    // Call #onPreferenceChanged with null values to ensure that the listeners
    // update their information.
    listener.onPreferenceChanged(null /* sharedPrefs */, null /* key */);
  }

  /**
   * Unregisters a listener from being notified whenever a preference changes.
   *
   * @param listener The listener to unregister
   */
  public void unregisterSwitchAccessCachedPreferenceChangeListener(
      SwitchAccessPreferenceChangedListener listener) {
    listeners.remove(listener);
  }

  /**
   * Get a previously stored preference value.
   *
   * <p>Caching the value prevents {@link SwitchAccessPreferenceUtils} from needing to access shared
   * preferences each time the preference value is needed.
   *
   * @param key The key of the preference whose value should be retrieved
   * @return The value of the preference associated with the key, or {@code null} if no value exists
   */
  @Nullable
  public Object retrievePreferenceValue(String key) {
    return preferenceKeyMap.get(key);
  }

  /**
   * Store a preference value. The preference value will automatically be removed from the cache if
   * the preference changes.
   *
   * <p>Caching the value prevents {@link SwitchAccessPreferenceUtils} from needing to access shared
   * preferences each time the preference value is needed.
   *
   * @param key The key of the preference whose value should be stored
   * @param value The value of the preference
   */
  public void storePreferenceValue(String key, Object value) {
    preferenceKeyMap.put(key, value);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if (key != null) {
      preferenceKeyMap.remove(key);
    } else {
      preferenceKeyMap.clear();
    }

    for (SwitchAccessPreferenceChangedListener listener : listeners) {
      listener.onPreferenceChanged(sharedPreferences, key);
    }
  }

  private void registerOnSharedPreferenceChangeListener(Context context) {
    SharedPreferencesUtils.getSharedPreferences(context.getApplicationContext())
        .registerOnSharedPreferenceChangeListener(this);
  }

  private void clearCacheAndUnregisterSharedPreferenceChangeListener(Context context) {
    preferenceKeyMap.clear();
    SharedPreferencesUtils.getSharedPreferences(context.getApplicationContext())
        .unregisterOnSharedPreferenceChangeListener(this);
  }

  /**
   * Interface for conveying information of the preference key and updated value when a Switch
   * Access preference has changed. This is used instead of {@link OnSharedPreferenceChangeListener}
   * in order to cache preference values and ensure that the downstream observers are not accessing
   * stale values from the cache. {@link OnSharedPreferenceChangeListener}s are not guaranteed to be
   * called in any order, so since other listeners could be called before the cache, a custom
   * listener is used instead.
   */
  public interface SwitchAccessPreferenceChangedListener {
    /**
     * Called when a Switch Access preference value has changed. This is called after the {@link
     * OnSharedPreferenceChangeListener} associated with the {@link SwitchAccessPreferenceCache} has
     * been called.
     *
     * @param preferences The preferences that changed
     * @param key The key of the preference that changed
     */
    void onPreferenceChanged(SharedPreferences preferences, String key);
  }
}
