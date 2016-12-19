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

package com.android.utils.labeling;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.talkback.BuildConfig;
import com.android.talkback.labeling.HasImportedLabelsRequest;
import com.android.talkback.labeling.LabelsFetchRequest;
import com.android.talkback.labeling.CustomLabelMigrationManager;
import com.android.talkback.labeling.DataConsistencyCheckRequest;
import com.android.talkback.labeling.DirectLabelFetchRequest;
import com.android.talkback.labeling.ImportLabelRequest;
import com.android.talkback.labeling.LabelAddRequest;
import com.android.talkback.labeling.LabelClientRequest;
import com.android.talkback.labeling.LabelRemoveRequest;
import com.android.talkback.labeling.LabelTask;
import com.android.talkback.labeling.LabelUpdateRequest;
import com.android.talkback.labeling.PackageLabelsFetchRequest;
import com.android.talkback.labeling.RevertImportedLabelsRequest;
import com.android.utils.AccessibilityEventListener;
import com.android.utils.LogUtils;
import com.android.utils.StringBuilderUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Manages logic for prefetching, retrieval, addition, updating, and removal of
 * custom view labels and their associated resources.
 * <p>
 * This class ties together an underlying label database with a LRU label cache.
 * It provides convenience methods for accessing and changing the state of
 * labels, both persisted and in memory. Methods in this class will often return
 * nothing, and may expose asynchronous callbacks wrapped by request classes to
 * return results from processing activities on different threads.
 * <p>
 * This class also serves as an {@link AccessibilityEventListener} for purposes
 * of automatically prefetching labels into the managed cache.
 */
// TODO Most public methods in this class should support optional callbacks.
@TargetApi(18)
public class CustomLabelManager {
    /** The minimum API level supported by the manager. */
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN_MR2;
    public static final int SOURCE_TYPE_USER = 0; // labels that were inserted by user
    public static final int SOURCE_TYPE_IMPORT = 1; // labels that were imported
    public static final int SOURCE_TYPE_BACKUP = 2; // labels that were overridden by import

    // Intent values for broadcasts to CustomLabelManager.
    public static final String ACTION_REFRESH_LABEL_CACHE =
            "com.google.android.marvin.talkback.labeling.REFRESH_LABEL_CACHE";
    public static final String EXTRA_STRING_ARRAY_PACKAGES = "EXTRA_STRING_ARRAY_PACKAGES";

    private static final String
            AUTHORITY = BuildConfig.APPLICATION_ID + ".providers.LabelProvider";

    /**
     * The substring separating a label's package and view ID name in a
     * fully-qualified resource identifier.
     */
    private static final Pattern RESOURCE_NAME_SPLIT_PATTERN = Pattern.compile(":id/");

    private static final IntentFilter REFRESH_INTENT_FILTER =
            new IntentFilter(ACTION_REFRESH_LABEL_CACHE);

    public static String getDefaultLocale() {
        String locale = Locale.getDefault().toString();
        return getLanguageLocale(locale);
    }

    public static String getLanguageLocale(String locale) {
        if (locale != null) {
            int localeDivider = locale.indexOf('_');
            if (localeDivider > 0) {
                return locale.substring(0, localeDivider);
            }
        }

        return locale;
    }

    private final NavigableSet<Label> mLabelCache = new TreeSet<>(new Comparator<Label>() {
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

    private final CacheRefreshReceiver mRefreshReceiver = new CacheRefreshReceiver();
    private final LocaleChangedReceiver mLocaleChangedReceiver = new LocaleChangedReceiver();

    private final Context mContext;
    private final PackageManager mPackageManager;
    private final LabelProviderClient mClient;

    // Used to manage release of resources based on task completion
    private boolean mShouldShutdownClient;
    private int mRunningTasks;

    private LabelTask.TrackedTaskCallback mTaskCallback =
            new LabelTask.TrackedTaskCallback() {
        @Override
        public void onTaskPreExecute(LabelClientRequest request) {
            checkUiThread();
            taskStarting(request);
        }

        @Override
        public void onTaskPostExecute(LabelClientRequest request) {
            checkUiThread();
            taskEnding(request);
        }
    };

    private DataConsistencyCheckRequest.OnDataConsistencyCheckCallback
            mDataConsistencyCheckCallback =
            new DataConsistencyCheckRequest.OnDataConsistencyCheckCallback() {
        @Override
        public void onConsistencyCheckCompleted(List<Label> labelsToRemove) {
            if (labelsToRemove == null || labelsToRemove.isEmpty()) {
                return;
            }

            LogUtils.log(this, Log.VERBOSE, "Found %d labels to remove during consistency check",
                    labelsToRemove.size());
            for (Label l : labelsToRemove) {
                removeLabel(l);
            }
        }
    };

    private OnLabelsInPackageChangeListener mLabelsInPackageChangeListener =
            new OnLabelsInPackageChangeListener() {
        @Override
        public void onLabelsInPackageChanged(String packageName) {
            sendCacheRefreshIntent(packageName);
        }
    };

    public CustomLabelManager(Context context) {
        mContext = context;
        mPackageManager = context.getPackageManager();
        mClient = new LabelProviderClient(context, AUTHORITY);
        mContext.registerReceiver(mRefreshReceiver, REFRESH_INTENT_FILTER);
        mContext.registerReceiver(mLocaleChangedReceiver,
                new IntentFilter(Intent.ACTION_LOCALE_CHANGED));
        refreshCache();
    }

    private void checkUiThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("run not on UI thread");
        }
    }

    /**
     * Performs various tasks to ensure the underlying Label database is in a
     * state consistent with the installed applications on the device. May alter
     * the database state by pruning labels in cases where the containing
     * applications are no longer present on the system or those applications
     * don't match stored signature data.
     * <p>
     * NOTE: This should be invoked by higher level service entities using this
     * class on startup after registering a {@link PackageRemovalReceiver}. It
     * is generally unnecessary to invoke this operation for disposable
     * instances of this class.
     */
    public void ensureDataConsistency() {
        if (!isInitialized()) {
            return;
        }

        DataConsistencyCheckRequest request = new DataConsistencyCheckRequest(mClient, mContext,
                mDataConsistencyCheckCallback);
        LabelTask<List<Label>> task = new LabelTask<>(request, mTaskCallback);
        task.execute();
    }

    /**
     * Retrieves a {@link Label} from the label cache given a fully-qualified
     * resource identifier name.
     *
     * @param resourceName The fully-qualified resource identifier, such as
     *            "com.android.deskclock:id/analog_appwidget", as provided by
     *            {@link AccessibilityNodeInfo#getViewIdResourceName()}
     * @return The {@link Label} matching the provided identifier, or
     *         {@code null} if no such label exists or has not yet been fetched
     *         from storage
     */
    public Label getLabelForViewIdFromCache(String resourceName) {
        if (!isInitialized()) {
            return null;
        }

        Pair<String, String> parsedId = splitResourceName(resourceName);
        if (parsedId == null) {
            return null;
        }

        Label search = new Label(parsedId.first, null, parsedId.second, null, null, 0, null, 0);
        Label result = mLabelCache.ceiling(search);
        // TODO: This can be done much simplier with modifying equals in Label but want to wait
        // until BrailleBack is integrate to ensure compatibility
        if (result != null &&
                search.getViewName() != null &&
                search.getViewName().equals(result.getViewName()) &&
                search.getPackageName() != null &&
                search.getPackageName().equals(result.getPackageName())) {
            return result;
        }

        return null;
    }

    /**
     * Retrieves a {@link Label} directly through the database and returns it
     * through a callback interface.
     *
     * @param labelId The id of the label to retrieve from the database as
     *            provided by {@link Label#getId()}
     * @param callback The {@link DirectLabelFetchRequest.OnLabelFetchedListener} to return the
     *                 label though
     */
    public void getLabelForLabelIdFromDatabase(long labelId,
                                       DirectLabelFetchRequest.OnLabelFetchedListener callback) {
        if (!isInitialized()) {
            return;
        }

        DirectLabelFetchRequest request = new DirectLabelFetchRequest(mClient, labelId, callback);
        LabelTask<Label> task = new LabelTask<>(request, mTaskCallback);
        task.execute();
    }

    /**
     * Retrieves a {@link Map} of view ID resource names to {@link Label}s for
     * labels in the given package name and returns it through a callback
     * interface.
     *
     * @param packageName The package name of the labels to retrieve
     * @param callback The {@link PackageLabelsFetchRequest.OnLabelsFetchedListener} to return the
     *                 labels through
     */
    public void getLabelsForPackageFromDatabase(
            String packageName, PackageLabelsFetchRequest.OnLabelsFetchedListener callback) {
        if (!isInitialized()) {
            return;
        }

        final PackageLabelsFetchRequest request = new PackageLabelsFetchRequest(mClient, mContext,
                packageName, callback);
        LabelTask<Map<String, Label>> task = new LabelTask<>(request, mTaskCallback);
        task.execute();
    }

    public void getLabelsFromDatabase(
            LabelsFetchRequest.OnLabelsFetchedListener callback) {
        if (!isInitialized()) {
            return;
        }

        final LabelsFetchRequest request = new LabelsFetchRequest(mClient, callback);
        LabelTask<List<Label>> task = new LabelTask<>(request, mTaskCallback);
        task.execute();
    }

    /**
     * Creates a {@link Label} and persists it to the label database, and
     * refreshes the label cache.
     *
     * @param resourceName The fully-qualified resource identifier, such as
     *            "com.android.deskclock:id/analog_appwidget", as provided by
     *            {@link AccessibilityNodeInfo#getViewIdResourceName()}
     * @param userLabel The label provided for the node by the user
     */
    public void addLabel(String resourceName, String userLabel) {
        if (!isInitialized()) {
            return;
        }

        final String finalLabel;
        if (userLabel == null) {
            throw new IllegalArgumentException(
                    "Attempted to add a label with a null userLabel value");
        } else {
            finalLabel = userLabel.trim();
            if (TextUtils.isEmpty(finalLabel)) {
                throw new IllegalArgumentException(
                        "Attempted to add a label with an empty userLabel value");
            }
        }

        Pair<String, String> parsedId = splitResourceName(resourceName);
        if (parsedId == null) {
            LogUtils.log(this, Log.WARN,
                    "Attempted to add a label with an invalid or poorly formed view ID.");
            return;
        }

        final PackageInfo packageInfo;
        try {
            packageInfo = mPackageManager.getPackageInfo(
                    parsedId.first, PackageManager.GET_SIGNATURES);
        } catch (NameNotFoundException e) {
            LogUtils.log(this, Log.WARN, "Attempted to add a label for an unknown package.");
            return;
        }

        String locale = getDefaultLocale();
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
            LogUtils.log(this, Log.WARN, "Unable to create SHA-1 MessageDigest");
        }

        // For the current implementation, screenshots are disabled
        Label label = new Label(parsedId.first, signatureHash, parsedId.second, finalLabel,
                locale, version, "", timestamp);
        LabelAddRequest request = new LabelAddRequest(mClient, label, SOURCE_TYPE_USER,
                mLabelsInPackageChangeListener);
        LabelTask<Label> task = new LabelTask<>(request, mTaskCallback);
        task.execute();
    }

    /**
     * Updates {@link Label}s in the label database and refreshes the label
     * cache.
     * <p>
     * NOTE: This method relies on the id field of the {@link Label}s being
     * populated, so callers must obtain fully populated objects from
     * {@link #getLabelForViewIdFromCache(String)} or
     * in order to update them.
     *
     * @param labels The {@link Label}s to remove
     */
    public void updateLabel(Label... labels) {
        if (!isInitialized()) {
            return;
        }

        if (labels == null || labels.length == 0) {
            LogUtils.log(this, Log.WARN, "Attempted to update a null or empty array of labels.");
            return;
        }

        for (Label l : labels) {
            if (l == null) {
                throw new IllegalArgumentException("Attempted to update a null label.");
            }

            if (TextUtils.isEmpty(l.getText())) {
                throw new IllegalArgumentException(
                        "Attempted to update a label with an empty text value");
            }

            LabelUpdateRequest request = new LabelUpdateRequest(mClient, l,
                    mLabelsInPackageChangeListener);
            LabelTask<Boolean> task = new LabelTask<>(request, mTaskCallback);
            task.execute();
        }
    }

    /**
     * Removes {@link Label}s from the label database and refreshes the label
     * cache.
     * <p>
     * NOTE: This method relies on the id field of the {@link Label}s being
     * populated, so callers must obtain fully populated objects from
     * {@link #getLabelForViewIdFromCache(String)} or
     * in order to remove them.
     *
     * @param labels The {@link Label}s to remove
     */
    public void removeLabel(Label... labels) {
        if (!isInitialized()) {
            return;
        }

        if (labels == null || labels.length == 0) {
            LogUtils.log(this, Log.WARN, "Attempted to delete a null or empty array of labels.");
            return;
        }

        for (Label l : labels) {
            LabelRemoveRequest request = new LabelRemoveRequest(mClient, l,
                    mLabelsInPackageChangeListener);
            LabelTask<Boolean> task = new LabelTask<>(request, mTaskCallback);
            task.execute();
        }
    }

    public void importLabels(List<Label> labels, boolean overrideExistentLabels,
                             final CustomLabelMigrationManager.OnLabelMigrationCallback callback) {
        ImportLabelRequest request = new ImportLabelRequest(mClient, labels, overrideExistentLabels,
                new ImportLabelRequest.OnImportLabelCallback() {
                    @Override
                    public void onLabelImported(int changedLabelsCount) {
                        sendCacheRefreshIntent();
                        if (callback != null) {
                            callback.onLabelImported(changedLabelsCount);
                        }
                    }
                });
        LabelTask<Integer> task = new LabelTask<>(request, mTaskCallback);
        task.execute();
    }

    public void hasImportedLabels(HasImportedLabelsRequest.OnHasImportedLabelsCompleteListener
                                          listener) {
        HasImportedLabelsRequest request = new HasImportedLabelsRequest(mClient, listener);
        LabelTask<Boolean> task = new LabelTask<>(request, mTaskCallback);
        task.execute();
    }

    public void revertImportedLabels(
            final RevertImportedLabelsRequest.OnImportLabelsRevertedListener listener) {
        RevertImportedLabelsRequest request = new RevertImportedLabelsRequest(mClient,
                new RevertImportedLabelsRequest.OnImportLabelsRevertedListener() {
            @Override
            public void onImportLabelsReverted() {
                sendCacheRefreshIntent();
                if (listener != null) {
                    listener.onImportLabelsReverted();
                }
            }
        });
        LabelTask<Boolean> task = new LabelTask<>(request, mTaskCallback);
        task.execute();
    }

    /**
     * Invalidates and rebuilds the cache of labels managed by this class.
     */
    private void refreshCache() {
        getLabelsFromDatabase(new LabelsFetchRequest.OnLabelsFetchedListener() {
            @Override
            public void onLabelsFetched(List<Label> results) {
                mLabelCache.clear();
                String currentLocale = getDefaultLocale();
                for (Label newLabel : results) {
                    String locale = newLabel.getLocale();
                    if (locale != null && locale.startsWith(currentLocale)) {
                        mLabelCache.add(newLabel);
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
        if (mLabelCache.isEmpty()) {
            refreshCache();
        }
    }

    /**
     * Splits a fully-qualified resource identifier name into its package and ID
     * name.
     *
     * @param resourceName The fully-qualified resource identifier, such as
     *            "com.android.deskclock:id/analog_appwidget", as provided by
     *            {@link AccessibilityNodeInfo#getViewIdResourceName()}
     * @return A {@link Pair} where the first value is the package name and
     *         second is the id name
     */
    public static Pair<String, String> splitResourceName(String resourceName) {
        if (TextUtils.isEmpty(resourceName)) {
            return null;
        }

        final String[] splitId = RESOURCE_NAME_SPLIT_PATTERN.split(resourceName, 2);
        if (splitId.length != 2 || TextUtils.isEmpty(splitId[0]) || TextUtils.isEmpty(splitId[1])) {
            // Invalid input
            LogUtils.log(CustomLabelManager.class, Log.WARN, "Failed to parse resource: %s",
                    resourceName);
            return null;
        }

        return new Pair<>(splitId[0], splitId[1]);
    }

    /**
     * Shuts down the manager and releases resources.
     */
    public void shutdown() {
        LogUtils.log(this, Log.VERBOSE, "Shutdown requested.");

        // We must immediately destroy registered receivers to prevent a leak,
        // as the context backing this registration is to be invalidated.
        mContext.unregisterReceiver(mRefreshReceiver);
        mContext.unregisterReceiver(mLocaleChangedReceiver);

        // We cannot shutdown resources related to the database until all tasks
        // have completed. Flip the flag to indicate a client of this manager
        // requested a shutdown and attempt the operation.
        mShouldShutdownClient = true;
        maybeShutdownClient();
    }

    /**
     * Returns whether the labeling client is properly initialized.
     * @return {@code true} if client is ready, or {@code false} otherwise.
     */
    public boolean isInitialized() {
        checkUiThread();
        return mClient.isInitialized();
    }

    /**
     * Shuts down the database resources held by an instance of this manager if
     * certain conditions are met. The database resource is released if and only
     * if a client has requested a shutdown operation and there are no
     * asynchronous operations running. To ensure completeness, this method is
     * invoked when a client of this manager requests a shutdown and when any
     * asynchronous operation completes.
     */
    private void maybeShutdownClient() {
        checkUiThread();
        if ((mRunningTasks == 0) && mShouldShutdownClient) {
            LogUtils.log(this, Log.VERBOSE,
                    "All tasks completed and shutdown requested.  Releasing database.");
            mClient.shutdown();
        }
    }

    /**
     * Updates the internals of the manager to track this task, keeping database
     * resources from being shutdown until all tasks complete.
     *
     * @param request The task that's starting
     */
    private void taskStarting(LabelClientRequest request) {
        LogUtils.log(this, Log.VERBOSE, "Task %s starting.", request);
        mRunningTasks++;
    }

    /**
     * Updates the internals of the manager to stop tracking this task. May
     * dispose of database resources if a shutdown requested by this classes's
     * client was requested prior to {@code task}'s completion.
     *
     * @param request The request that is ending
     */
    private void taskEnding(LabelClientRequest request) {
        LogUtils.log(this, Log.VERBOSE, "Task %s ending.", request);
        mRunningTasks--;
        maybeShutdownClient();
    }

    private void sendCacheRefreshIntent(String... packageNames) {
        final Intent refreshIntent = new Intent(ACTION_REFRESH_LABEL_CACHE);
        refreshIntent.putExtra(EXTRA_STRING_ARRAY_PACKAGES, packageNames);
        mContext.sendBroadcast(refreshIntent);
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
