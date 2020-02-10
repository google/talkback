/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.google.android.accessibility.talkback.labeling;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.os.Looper;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.Pair;
import com.google.android.accessibility.talkback.BuildConfig;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.LocaleUtils;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.labeling.Label;
import com.google.android.accessibility.utils.labeling.LabelManager;
import com.google.android.accessibility.utils.labeling.LabelProviderClient;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Manages logic for prefetching, retrieval, addition, updating, and removal of custom view labels
 * and their associated resources.
 *
 * <p>This class ties together an underlying label database with a LRU label cache. It provides
 * convenience methods for accessing and changing the state of labels, both persisted and in memory.
 * Methods in this class will often return nothing, and may expose asynchronous callbacks wrapped by
 * request classes to return results from processing activities on different threads.
 *
 * <p>This class also serves as an {@link AccessibilityEventListener} for purposes of automatically
 * prefetching labels into the managed cache.
 */
// TODO: Most public methods in this class should support optional callbacks.
public class CustomLabelManager implements LabelManager {

  private static final String TAG = "CustomLabelManager";

  // Intent values for broadcasts to CustomLabelManager.
  public static final String ACTION_REFRESH_LABEL_CACHE =
      "com.google.android.marvin.talkback.labeling.REFRESH_LABEL_CACHE";
  public static final String EXTRA_STRING_ARRAY_PACKAGES = "EXTRA_STRING_ARRAY_PACKAGES";

  @VisibleForTesting
  static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".providers.LabelProvider";

  /**
   * The substring separating a label's package and view ID name in a fully-qualified resource
   * identifier.
   */
  private static final Pattern RESOURCE_NAME_SPLIT_PATTERN = Pattern.compile(":id/");

  private static final IntentFilter REFRESH_INTENT_FILTER =
      new IntentFilter(ACTION_REFRESH_LABEL_CACHE);

  /**
   * Gets the text of a <code>node</code> by returning the content description (if available) or by
   * returning the text. Will use the specified <code>CustomLabelManager</code> as a fall back if
   * both are null.
   *
   * @param node The node.
   * @param labelManager The label manager.
   * @return The node text.
   */
  public static @Nullable CharSequence getNodeText(
      AccessibilityNodeInfoCompat node, CustomLabelManager labelManager) {
    CharSequence text = AccessibilityNodeInfoUtils.getNodeText(node);
    if (!TextUtils.isEmpty(text)) {
      return text;
    }

    if (labelManager != null && labelManager.isInitialized()) {
      Label label = labelManager.getLabelForViewIdFromCache(node.getViewIdResourceName());
      if (label != null) {
        return label.getText();
      }
    }
    return null;
  }

  private final NavigableSet<Label> labelCache =
      new TreeSet<>(
          new Comparator<Label>() {
            // Note this comparator is not consistent with equals in Label, we should just implement
            // compareTo there, but will wait for BrailleBack to be merged with TalkBack
            @Override
            public int compare(Label first, Label second) {
              if (first == null) {
                if (second == null) {
                  return 0;
                } else {
                  return -1;
                }
              }

              if (second == null) return 1;

              int ret = first.getPackageName().compareTo(second.getPackageName());
              if (ret != 0) return ret;

              return first.getViewName().compareTo(second.getViewName());
            }
          });

  private final CacheRefreshReceiver refreshReceiver = new CacheRefreshReceiver();
  private final LocaleChangedReceiver localeChangedReceiver = new LocaleChangedReceiver();

  private final Context context;
  private final PackageManager packageManager;
  private final LabelProviderClient client;

  // Used to manage release of resources based on task completion
  private boolean shouldShutdownClient;
  private int runningTasks;

  private final LabelTask.TrackedTaskCallback taskCallback =
      new LabelTask.TrackedTaskCallback() {
        @Override
        public void onTaskPreExecute(LabelClientRequest<?> request) {
          checkUiThread();
          taskStarting(request);
        }

        @Override
        public void onTaskPostExecute(LabelClientRequest<?> request) {
          checkUiThread();
          taskEnding(request);
        }
      };

  private DataConsistencyCheckRequest.OnDataConsistencyCheckCallback dataConsistencyCheckCallback =
      new DataConsistencyCheckRequest.OnDataConsistencyCheckCallback() {
        @Override
        public void onConsistencyCheckCompleted(@Nullable List<Label> labelsToRemove) {
          if (labelsToRemove == null || labelsToRemove.isEmpty()) {
            return;
          }

          LogUtils.v(
              TAG, "Found %d labels to remove during consistency check", labelsToRemove.size());
          for (Label l : labelsToRemove) {
            removeLabel(l);
          }
        }
      };

  private OnLabelsInPackageChangeListener labelsInPackageChangeListener =
      new OnLabelsInPackageChangeListener() {
        @Override
        public void onLabelsInPackageChanged(String packageName) {
          sendCacheRefreshIntent(packageName);
        }
      };

  public CustomLabelManager(Context context) {
    this.context = context;
    packageManager = context.getPackageManager();
    client = new LabelProviderClient(context, AUTHORITY);
    this.context.registerReceiver(refreshReceiver, REFRESH_INTENT_FILTER);
    this.context.registerReceiver(
        localeChangedReceiver, new IntentFilter(Intent.ACTION_LOCALE_CHANGED));
    refreshCache();
  }

  private void checkUiThread() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      throw new IllegalStateException("run not on UI thread");
    }
  }

  /**
   * Performs various tasks to ensure the underlying Label database is in a state consistent with
   * the installed applications on the device. May alter the database state by pruning labels in
   * cases where the containing applications are no longer present on the system or those
   * applications don't match stored signature data.
   *
   * <p>NOTE: This should be invoked by higher level service entities using this class on startup
   * after registering a {@link PackageRemovalReceiver}. It is generally unnecessary to invoke this
   * operation for disposable instances of this class.
   */
  public void ensureDataConsistency() {
    if (!isInitialized()) {
      return;
    }

    DataConsistencyCheckRequest request =
        new DataConsistencyCheckRequest(client, context, dataConsistencyCheckCallback);
    LabelTask<@Nullable List<Label>> task = new LabelTask<>(request, taskCallback);
    task.execute();
  }

  /**
   * Retrieves a {@link Label} from the label cache given a fully-qualified resource identifier
   * name.
   *
   * @param resourceName The fully-qualified resource identifier, such as
   *     "com.android.deskclock:id/analog_appwidget", as provided by {@link
   *     AccessibilityNodeInfo#getViewIdResourceName()}
   * @return The {@link Label} matching the provided identifier, or {@code null} if no such label
   *     exists or has not yet been fetched from storage
   */
  @Override
  public @Nullable Label getLabelForViewIdFromCache(String resourceName) {
    if (!isInitialized()) {
      return null;
    }

    Pair<String, String> parsedId = splitResourceName(resourceName);
    if (parsedId == null) {
      return null;
    }

    Label search = new Label(parsedId.first, null, parsedId.second, null, null, 0, null, 0);
    Label result = labelCache.ceiling(search);
    // TODO: This can be done much simplier with modifying equals in Label but want to wait
    // until BrailleBack is integrate to ensure compatibility
    if (result != null
        && search.getViewName() != null
        && search.getViewName().equals(result.getViewName())
        && search.getPackageName() != null
        && search.getPackageName().equals(result.getPackageName())) {
      return result;
    }

    return null;
  }

  /**
   * Retrieves a {@link Label} directly through the database and returns it through a callback
   * interface.
   *
   * @param labelId The id of the label to retrieve from the database as provided by {@link
   *     Label#getId()}
   * @param callback The {@link
   *     com.google.android.accessibility.talkback.labeling.DirectLabelFetchRequest.OnLabelFetchedListener}
   *     to return the label though
   */
  public void getLabelForLabelIdFromDatabase(
      long labelId, DirectLabelFetchRequest.OnLabelFetchedListener callback) {
    if (!isInitialized()) {
      return;
    }

    DirectLabelFetchRequest request = new DirectLabelFetchRequest(client, labelId, callback);
    LabelTask<Label> task = new LabelTask<>(request, taskCallback);
    task.execute();
  }

  /**
   * Retrieves a {@link Map} of view ID resource names to {@link Label}s for labels in the given
   * package name and returns it through a callback interface.
   *
   * @param packageName The package name of the labels to retrieve
   * @param callback The {@link
   *     com.google.android.accessibility.talkback.labeling.PackageLabelsFetchRequest.OnLabelsFetchedListener}
   *     to return the labels through
   */
  public void getLabelsForPackageFromDatabase(
      String packageName, PackageLabelsFetchRequest.OnLabelsFetchedListener callback) {
    if (!isInitialized()) {
      return;
    }

    final PackageLabelsFetchRequest request =
        new PackageLabelsFetchRequest(client, context, packageName, callback);
    LabelTask<Map<String, Label>> task = new LabelTask<>(request, taskCallback);
    task.execute();
  }

  public void getLabelsFromDatabase(LabelsFetchRequest.OnLabelsFetchedListener callback) {
    if (!isInitialized()) {
      return;
    }

    final LabelsFetchRequest request = new LabelsFetchRequest(client, callback);
    LabelTask<List<Label>> task = new LabelTask<>(request, taskCallback);
    task.execute();
  }

  /**
   * Creates a {@link Label} and persists it to the label database, and refreshes the label cache.
   *
   * @param resourceName The fully-qualified resource identifier, such as
   *     "com.android.deskclock:id/analog_appwidget", as provided by {@link
   *     AccessibilityNodeInfo#getViewIdResourceName()}
   * @param userLabel The label provided for the node by the user
   */
  public void addLabel(String resourceName, String userLabel) {
    if (!isInitialized()) {
      return;
    }

    final String finalLabel;
    if (userLabel == null) {
      throw new IllegalArgumentException("Attempted to add a label with a null userLabel value");
    } else {
      finalLabel = userLabel.trim();
      if (TextUtils.isEmpty(finalLabel)) {
        throw new IllegalArgumentException(
            "Attempted to add a label with an empty userLabel value");
      }
    }

    Pair<String, String> parsedId = splitResourceName(resourceName);
    if (parsedId == null) {
      LogUtils.w(TAG, "Attempted to add a label with an invalid or poorly formed view ID.");
      return;
    }

    final PackageInfo packageInfo;
    try {
      packageInfo = packageManager.getPackageInfo(parsedId.first, PackageManager.GET_SIGNATURES);
    } catch (NameNotFoundException e) {
      LogUtils.w(TAG, "Attempted to add a label for an unknown package.");
      return;
    }

    String locale = LocaleUtils.getDefaultLocale();
    final int version = packageInfo.versionCode;
    final long timestamp = System.currentTimeMillis();
    String signatureHash = "";

    final Signature[] sigs = packageInfo.signatures;
    try {
      final MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
      for (Signature s : sigs) {
        messageDigest.update(s.toByteArray());
      }

      signatureHash = StringBuilderUtils.bytesToHexString(messageDigest.digest());
    } catch (NoSuchAlgorithmException e) {
      LogUtils.w(TAG, "Unable to create SHA-1 MessageDigest");
    }

    // For the current implementation, screenshots are disabled
    Label label =
        new Label(
            parsedId.first,
            signatureHash,
            parsedId.second,
            finalLabel,
            locale,
            version,
            "",
            timestamp);
    LabelAddRequest request =
        new LabelAddRequest(client, label, SOURCE_TYPE_USER, labelsInPackageChangeListener);
    LabelTask<Label> task = new LabelTask<>(request, taskCallback);
    task.execute();
  }

  /**
   * Updates {@link Label}s in the label database and refreshes the label cache.
   *
   * <p>NOTE: This method relies on the id field of the {@link Label}s being populated, so callers
   * must obtain fully populated objects from {@link #getLabelForViewIdFromCache(String)} or in
   * order to update them.
   *
   * @param labels The {@link Label}s to remove
   */
  public void updateLabel(Label... labels) {
    if (!isInitialized()) {
      return;
    }

    if (labels == null || labels.length == 0) {
      LogUtils.w(TAG, "Attempted to update a null or empty array of labels.");
      return;
    }

    for (Label l : labels) {
      if (l == null) {
        throw new IllegalArgumentException("Attempted to update a null label.");
      }

      if (TextUtils.isEmpty(l.getText())) {
        throw new IllegalArgumentException("Attempted to update a label with an empty text value");
      }

      LabelUpdateRequest request = new LabelUpdateRequest(client, l, labelsInPackageChangeListener);
      LabelTask<Boolean> task = new LabelTask<>(request, taskCallback);
      task.execute();
    }
  }

  /**
   * Removes {@link Label}s from the label database and refreshes the label cache.
   *
   * <p>NOTE: This method relies on the id field of the {@link Label}s being populated, so callers
   * must obtain fully populated objects from {@link #getLabelForViewIdFromCache(String)} or in
   * order to remove them.
   *
   * @param labels The {@link Label}s to remove
   */
  public void removeLabel(Label... labels) {
    if (!isInitialized()) {
      return;
    }

    if (labels == null || labels.length == 0) {
      LogUtils.w(TAG, "Attempted to delete a null or empty array of labels.");
      return;
    }

    for (Label l : labels) {
      LabelRemoveRequest request = new LabelRemoveRequest(client, l, labelsInPackageChangeListener);
      LabelTask<Boolean> task = new LabelTask<>(request, taskCallback);
      task.execute();
    }
  }

  public void importLabels(
      List<Label> labels,
      boolean overrideExistentLabels,
      final CustomLabelMigrationManager.OnLabelMigrationCallback callback) {
    ImportLabelRequest request =
        new ImportLabelRequest(
            client,
            labels,
            overrideExistentLabels,
            new ImportLabelRequest.OnImportLabelCallback() {
              @Override
              public void onLabelImported(int changedLabelsCount) {
                sendCacheRefreshIntent();
                if (callback != null) {
                  callback.onLabelImported(changedLabelsCount);
                }
              }
            });
    LabelTask<Integer> task = new LabelTask<>(request, taskCallback);
    task.execute();
  }

  public void hasImportedLabels(
      HasImportedLabelsRequest.OnHasImportedLabelsCompleteListener listener) {
    HasImportedLabelsRequest request = new HasImportedLabelsRequest(client, listener);
    LabelTask<Boolean> task = new LabelTask<>(request, taskCallback);
    task.execute();
  }

  public void revertImportedLabels(
      final RevertImportedLabelsRequest.OnImportLabelsRevertedListener listener) {
    RevertImportedLabelsRequest request =
        new RevertImportedLabelsRequest(
            client,
            new RevertImportedLabelsRequest.OnImportLabelsRevertedListener() {
              @Override
              public void onImportLabelsReverted() {
                sendCacheRefreshIntent();
                if (listener != null) {
                  listener.onImportLabelsReverted();
                }
              }
            });
    LabelTask<Boolean> task = new LabelTask<>(request, taskCallback);
    task.execute();
  }

  /** Invalidates and rebuilds the cache of labels managed by this class. */
  private void refreshCache() {
    getLabelsFromDatabase(
        new LabelsFetchRequest.OnLabelsFetchedListener() {
          @Override
          public void onLabelsFetched(List<Label> results) {
            labelCache.clear();
            String currentLocale = LocaleUtils.getDefaultLocale();
            for (Label newLabel : results) {
              String locale = newLabel.getLocale();
              if (locale != null && locale.startsWith(currentLocale)) {
                labelCache.add(newLabel);
              }
            }
          }
        });
  }

  /**
   * If there are no cached labels (possibly because CE storage was not yet available when the
   * CustomLabelManager instance was constructed), refreshes the labels from the label provider.
   */
  public void ensureLabelsLoaded() {
    if (labelCache.isEmpty()) {
      refreshCache();
    }
  }

  /**
   * Splits a fully-qualified resource identifier name into its package and ID name.
   *
   * @param resourceName The fully-qualified resource identifier, such as
   *     "com.android.deskclock:id/analog_appwidget", as provided by {@link
   *     AccessibilityNodeInfo#getViewIdResourceName()}
   * @return A {@link Pair} where the first value is the package name and second is the id name
   */
  public static @Nullable Pair<String, String> splitResourceName(String resourceName) {
    if (TextUtils.isEmpty(resourceName)) {
      return null;
    }

    final String[] splitId = RESOURCE_NAME_SPLIT_PATTERN.split(resourceName, 2);
    if (splitId.length != 2 || TextUtils.isEmpty(splitId[0]) || TextUtils.isEmpty(splitId[1])) {
      // Invalid input
      LogUtils.w(TAG, "Failed to parse resource: %s", resourceName);
      return null;
    }

    return new Pair<>(splitId[0], splitId[1]);
  }

  /** Shuts down the manager and releases resources. */
  public void shutdown() {
    LogUtils.v(TAG, "Shutdown requested.");

    // We must immediately destroy registered receivers to prevent a leak,
    // as the context backing this registration is to be invalidated.
    context.unregisterReceiver(refreshReceiver);
    context.unregisterReceiver(localeChangedReceiver);

    // We cannot shutdown resources related to the database until all tasks
    // have completed. Flip the flag to indicate a client of this manager
    // requested a shutdown and attempt the operation.
    shouldShutdownClient = true;
    maybeShutdownClient();
  }

  /**
   * Returns whether the labeling client is properly initialized.
   *
   * @return {@code true} if client is ready, or {@code false} otherwise.
   */
  public boolean isInitialized() {
    checkUiThread();
    return client.isInitialized();
  }

  /**
   * Shuts down the database resources held by an instance of this manager if certain conditions are
   * met. The database resource is released if and only if a client has requested a shutdown
   * operation and there are no asynchronous operations running. To ensure completeness, this method
   * is invoked when a client of this manager requests a shutdown and when any asynchronous
   * operation completes.
   */
  private void maybeShutdownClient() {
    checkUiThread();
    if ((runningTasks == 0) && shouldShutdownClient) {
      LogUtils.v(TAG, "All tasks completed and shutdown requested.  Releasing database.");
      client.shutdown();
    }
  }

  /**
   * Updates the internals of the manager to track this task, keeping database resources from being
   * shutdown until all tasks complete.
   *
   * @param request The task that's starting
   */
  private void taskStarting(LabelClientRequest<?> request) {
    LogUtils.v(TAG, "Task %s starting.", request);
    runningTasks++;
  }

  /**
   * Updates the internals of the manager to stop tracking this task. May dispose of database
   * resources if a shutdown requested by this classes's client was requested prior to {@code
   * task}'s completion.
   *
   * @param request The request that is ending
   */
  private void taskEnding(LabelClientRequest<?> request) {
    LogUtils.v(TAG, "Task %s ending.", request);
    runningTasks--;
    maybeShutdownClient();
  }

  private void sendCacheRefreshIntent(String... packageNames) {
    final Intent refreshIntent = new Intent(ACTION_REFRESH_LABEL_CACHE);
    refreshIntent.putExtra(EXTRA_STRING_ARRAY_PACKAGES, packageNames);
    context.sendBroadcast(refreshIntent);
  }

  private class LocaleChangedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      refreshCache();
    }
  }

  private class CacheRefreshReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      refreshCache();
    }
  }

  public interface OnLabelsInPackageChangeListener {
    public void onLabelsInPackageChanged(String packageName);
  }
}
