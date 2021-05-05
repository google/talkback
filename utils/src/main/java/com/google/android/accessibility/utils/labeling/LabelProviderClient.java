/*
 * Copyright (C) 2013 Google Inc.
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

package com.google.android.accessibility.utils.labeling;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A client for storing and retrieving custom TalkBack view labels using the {@link Label} model
 * class and a connection to a {@link android.content.ContentProvider} for labels.
 */
public class LabelProviderClient {

  private static final String TAG = "LabelProviderClient";

  private static final String EQUALS_ARG = " = ?";
  private static final String NOT_EQUALS_ARG = " != ? ";
  private static final String LEQ_ARG = " <= ?";
  private static final String STARTS_WITH_ARG = " LIKE ?";
  private static final String AND = " AND ";
  private static final String GET_LABELS_FOR_APPLICATION_QUERY_WHERE =
      LabelsTable.KEY_PACKAGE_NAME
          + EQUALS_ARG
          + AND
          + LabelsTable.KEY_LOCALE
          + STARTS_WITH_ARG
          + AND
          + LabelsTable.KEY_PACKAGE_VERSION
          + LEQ_ARG
          + AND
          + LabelsTable.KEY_SOURCE_TYPE
          + NOT_EQUALS_ARG;
  private static final String PACKAGE_SUMMARY_QUERY_WHERE =
      LabelsTable.KEY_LOCALE + STARTS_WITH_ARG + AND + LabelsTable.KEY_SOURCE_TYPE + NOT_EQUALS_ARG;

  private static final String DELETE_LABEL_SELECTION =
      LabelsTable.KEY_PACKAGE_NAME
          + EQUALS_ARG
          + AND
          + LabelsTable.KEY_VIEW_NAME
          + EQUALS_ARG
          + AND
          + LabelsTable.KEY_LOCALE
          + STARTS_WITH_ARG
          + AND
          + LabelsTable.KEY_PACKAGE_VERSION
          + LEQ_ARG
          + AND
          + LabelsTable.KEY_SOURCE_TYPE
          + EQUALS_ARG;

  private static final String LABELS_PATH = "labels";
  private static final String PACKAGE_SUMMARY_PATH = "packageSummary";

  private ContentProviderClient mClient;
  private final Uri mLabelsContentUri;
  private final Uri mPackageSummaryContentUri;

  /**
   * Constructs a new client instance for the provider at the given URI.
   *
   * @param context The current context.
   * @param authority The authority of the labels content provider to access.
   */
  public LabelProviderClient(Context context, String authority) {
    mLabelsContentUri =
        new Uri.Builder().scheme("content").authority(authority).path(LABELS_PATH).build();
    mPackageSummaryContentUri =
        new Uri.Builder().scheme("content").authority(authority).path(PACKAGE_SUMMARY_PATH).build();

    final ContentResolver contentResolver = context.getContentResolver();
    mClient = contentResolver.acquireContentProviderClient(mLabelsContentUri);

    if (mClient == null) {
      LogUtils.w(TAG, "Failed to acquire content provider client.");
    }
  }

  /**
   * Inserts the specified label into the labels database via a client for the labels {@link
   * android.content.ContentProvider}.
   *
   * <p>Don't run this method on the UI thread. Use {@link android.os.AsyncTask}.
   *
   * @param label The model object for the label to store in the database.
   * @return A new label object with the assigned label ID from the database, or {@code null} if the
   *     insert operation failed.
   */
  public Label insertLabel(Label label, int sourceType) {
    LogUtils.d(TAG, "Inserting label: %s.", label);

    if (label == null) {
      return null;
    }

    final long labelId = label.getId();
    if (label.getId() != Label.NO_ID) {
      LogUtils.w(TAG, "Cannot insert label with existing ID (id=%d).", labelId);
      return null;
    }

    if (!checkClient()) {
      return null;
    }

    final ContentValues values = buildContentValuesForLabel(label);
    values.put(LabelsTable.KEY_SOURCE_TYPE, sourceType);

    final Uri resultUri;
    try {
      resultUri = mClient.insert(mLabelsContentUri, values);
    } catch (RemoteException e) {
      LogUtils.e(TAG, e.toString());
      return null;
    }

    if (resultUri == null) {
      LogUtils.w(TAG, "Failed to insert label.");
      return null;
    }

    final long newLabelId = Long.parseLong(resultUri.getLastPathSegment());
    return new Label(label, newLabelId);
  }

  /**
   * Gets a list of all labels in the label database.
   *
   * <p>Don't run this method on the UI thread. Use {@link android.os.AsyncTask}.
   *
   * @return An unmodifiable list of all labels in the database, or an empty list if the query
   *     returns no results, or {@code null} if the query fails.
   */
  public List<Label> getCurrentLabels() {
    LogUtils.d(TAG, "Querying all labels.");

    if (!checkClient()) {
      return null;
    }

    Cursor cursor = null;
    try {
      String selection = LabelsTable.KEY_SOURCE_TYPE + " != " + LabelManager.SOURCE_TYPE_BACKUP;
      cursor =
          mClient.query(
              mLabelsContentUri,
              LabelsTable.ALL_COLUMNS /* projection */,
              selection,
              null /* whereArgs */,
              null /* sortOrder */);

      return getLabelListFromCursor(cursor);
    } catch (RemoteException e) {
      LogUtils.e(TAG, e.toString());
      return null;
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
  }

  public boolean hasImportedLabels() {
    LogUtils.d(TAG, "Has imported label request");

    if (!checkClient()) {
      return false;
    }

    Cursor cursor = null;
    try {
      String selection = LabelsTable.KEY_SOURCE_TYPE + " = " + LabelManager.SOURCE_TYPE_IMPORT;
      cursor =
          mClient.query(
              mLabelsContentUri,
              LabelsTable.ALL_COLUMNS /* projection */,
              selection,
              null /* whereArgs */,
              null /* sortOrder */);

      return cursor != null && cursor.getCount() > 0;
    } catch (RemoteException e) {
      LogUtils.e(TAG, e.toString());
      return false;
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
  }

  /**
   * Gets a summary of label info for each package from the label database.
   *
   * <p>Don't run this method on the UI thread. Use {@link android.os.AsyncTask}.
   *
   * @return An unmodifiable list of {@link PackageLabelInfo} objects, or an empty map if the query
   *     returns no results, or {@code null} if the query fails.
   */
  public List<PackageLabelInfo> getPackageSummary(String locale) {
    LogUtils.d(TAG, "Querying package summary.");

    if (!checkClient()) {
      return null;
    }

    final String[] whereArgs = {locale + "%", String.valueOf(LabelManager.SOURCE_TYPE_BACKUP)};

    Cursor cursor = null;
    try {
      cursor =
          mClient.query(
              mPackageSummaryContentUri,
              null /* projection */,
              PACKAGE_SUMMARY_QUERY_WHERE,
              whereArgs,
              null /* sortOrder */);

      return getPackageSummaryFromCursor(cursor);
    } catch (RemoteException e) {
      LogUtils.e(TAG, e.toString());
      return null;
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
  }

  /**
   * Queries for labels matching a particular package and locale.
   *
   * <p>Don't run this method on the UI thread. Use {@link android.os.AsyncTask}.
   *
   * @param packageName The package name to match.
   * @param locale The locale to match.
   * @param maxPackageVersion The maximum package version for result labels.
   * @return An unmodifiable map from view names to label objects that contains all labels matching
   *     the criteria, or {@code null} if the query failed.
   */
  public Map<String, Label> getLabelsForPackage(
      String packageName, String locale, int maxPackageVersion) {
    LogUtils.d(
        TAG,
        "Querying labels for package: packageName=%s, locale=%s, maxPackageVersion=%s.",
        packageName,
        locale,
        maxPackageVersion);

    if (!checkClient()) {
      return null;
    }

    final String[] whereArgs =
        new String[] {
          packageName,
          locale + "%",
          String.valueOf(maxPackageVersion),
          String.valueOf(LabelManager.SOURCE_TYPE_BACKUP)
        };

    Cursor cursor = null;
    try {
      cursor =
          mClient.query(
              mLabelsContentUri,
              LabelsTable.ALL_COLUMNS /* projection */,
              GET_LABELS_FOR_APPLICATION_QUERY_WHERE,
              whereArgs,
              null /* sortOrder */);

      return getLabelMapFromCursor(cursor);
    } catch (RemoteException e) {
      LogUtils.e(TAG, e.toString());
      return null;
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
  }

  /**
   * Queries for labels matching a particular package and locale for all versions of that package.
   *
   * <p>Don't run this method on the UI thread. Use {@link android.os.AsyncTask}.
   *
   * @param packageName The package name to match.
   * @param locale The locale to match.
   * @return An unmodifiable map from view names to label objects that contains all labels matching
   *     the criteria, or {@code null} if the query failed.
   */
  public Map<String, Label> getLabelsForPackage(String packageName, String locale) {
    return getLabelsForPackage(packageName, locale, Integer.MAX_VALUE);
  }

  /**
   * Queries for a single label by its label ID.
   *
   * <p>Don't run this method on the UI thread. Use {@link android.os.AsyncTask}.
   *
   * @param id The ID of the label to find.
   * @return The label with the given ID, or {@code null} if no such label was found.
   */
  public Label getLabelById(long id) {
    LogUtils.d(TAG, "Querying single label: id=%d.", id);

    if (!checkClient()) {
      return null;
    }

    final Uri uri = ContentUris.withAppendedId(mLabelsContentUri, id);
    Cursor cursor = null;
    try {
      cursor =
          mClient.query(
              uri,
              LabelsTable.ALL_COLUMNS,
              null /* where */,
              null /* whereArgs */,
              null /* sortOrder */);

      return getLabelFromCursor(cursor);
    } catch (RemoteException e) {
      LogUtils.e(TAG, e.toString());
      return null;
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
  }

  /**
   * Updates a single label.
   *
   * <p>Don't run this method on the UI thread. Use {@link android.os.AsyncTask}.
   *
   * @param label The label with updated values to store.
   * @return {@code true} if the update succeeded, or {@code false} otherwise.
   */
  public boolean updateLabel(Label label, int newSourceType) {
    LogUtils.d(TAG, "Updating label: %s.", label);

    if (label == null) {
      return false;
    }

    if (!checkClient()) {
      return false;
    }

    final long labelId = label.getId();

    if (labelId == Label.NO_ID) {
      LogUtils.w(TAG, "Cannot update label with no ID.");
      return false;
    }

    final Uri uri = ContentUris.withAppendedId(mLabelsContentUri, labelId);
    final ContentValues values = buildContentValuesForLabel(label);
    values.put(LabelsTable.KEY_SOURCE_TYPE, newSourceType);

    try {
      final int rowsAffected =
          mClient.update(uri, values, null /* selection */, null /* selectionArgs */);
      return rowsAffected > 0;
    } catch (RemoteException e) {
      LogUtils.e(TAG, e.toString());
      return false;
    }
  }

  public boolean updateLabelSourceType(long labelId, int newSourceType) {
    LogUtils.d(TAG, "Updating label source type");

    if (!checkClient()) {
      return false;
    }

    if (labelId == Label.NO_ID) {
      LogUtils.w(TAG, "Cannot update label with no ID.");
      return false;
    }

    final Uri uri = ContentUris.withAppendedId(mLabelsContentUri, labelId);
    ContentValues values = new ContentValues();
    values.put(LabelsTable.KEY_SOURCE_TYPE, newSourceType);

    try {
      final int rowsAffected = mClient.update(uri, values, null, null);
      return rowsAffected > 0;
    } catch (RemoteException e) {
      LogUtils.e(TAG, e.toString());
      return false;
    }
  }

  public boolean updateSourceType(int currentSourceType, int newSourceType) {
    LogUtils.d(TAG, "Updating source type");

    if (!checkClient()) {
      return false;
    }

    ContentValues values = new ContentValues();
    values.put(LabelsTable.KEY_SOURCE_TYPE, newSourceType);

    try {
      String selection = LabelsTable.KEY_SOURCE_TYPE + "=" + currentSourceType;
      final int rowsAffected = mClient.update(mLabelsContentUri, values, selection, null);
      return rowsAffected > 0;
    } catch (RemoteException e) {
      LogUtils.e(TAG, e.toString());
      return false;
    }
  }

  /**
   * Deletes a single label.
   *
   * <p>Don't run this method on the UI thread. Use {@link android.os.AsyncTask}.
   *
   * @param labelId The label_id to delete.
   * @return {@code true} if the delete succeeded, or {@code false} otherwise.
   */
  public boolean deleteLabel(long labelId) {
    LogUtils.d(TAG, "Deleting label: %s.", labelId);

    if (!checkClient()) {
      return false;
    }

    if (labelId == Label.NO_ID) {
      LogUtils.w(TAG, "Cannot delete label with no ID.");
      return false;
    }

    final Uri uri = ContentUris.withAppendedId(mLabelsContentUri, labelId);

    try {
      final int rowsAffected = mClient.delete(uri, null /* selection */, null /* selectionArgs */);
      return rowsAffected > 0;
    } catch (RemoteException e) {
      LogUtils.e(TAG, e.toString());
      return false;
    }
  }

  public boolean deleteLabel(
      String packageName, String viewName, String locale, int packageVersion, int sourceType) {
    LogUtils.d(
        TAG,
        "Deleting label: package name: %s, view name: %s,"
            + " locale: %s, package version: %d, source type: %d",
        packageName,
        viewName,
        locale,
        packageVersion,
        sourceType);

    if (!checkClient()) {
      return false;
    }

    try {
      final String[] whereArgs =
          new String[] {
            packageName,
            viewName,
            locale + "%",
            Integer.toString(packageVersion),
            Integer.toString(sourceType)
          };
      final int rowsAffected = mClient.delete(mLabelsContentUri, DELETE_LABEL_SELECTION, whereArgs);
      return rowsAffected > 0;
    } catch (RemoteException e) {
      LogUtils.e(TAG, e.toString());
      return false;
    }
  }

  public boolean deleteLabels(int sourceType) {
    LogUtils.d(TAG, "Deleting backup labels");

    if (!checkClient()) {
      return false;
    }

    try {
      String selection = LabelsTable.KEY_SOURCE_TYPE + " = " + sourceType;
      int rowsAffected = mClient.delete(mLabelsContentUri, selection, null);
      return rowsAffected > 0;
    } catch (RemoteException e) {
      LogUtils.e(TAG, e.toString());
      return false;
    }
  }

  /** Shuts down the client and releases any resources. */
  public void shutdown() {
    if (checkClient()) {
      mClient.release();
      mClient = null;
    }
  }

  /**
   * Returns whether the client was properly initialized (non-null).
   *
   * @return {@code true} if client is non-null, or {@code false} otherwise.
   */
  public boolean isInitialized() {
    return mClient != null;
  }

  /**
   * Builds content values for the fields of a label.
   *
   * @param label The source of the values.
   * @return A set of values representing the label.
   */
  private static ContentValues buildContentValuesForLabel(Label label) {
    final ContentValues values = new ContentValues();

    values.put(LabelsTable.KEY_PACKAGE_NAME, label.getPackageName());
    values.put(LabelsTable.KEY_PACKAGE_SIGNATURE, label.getPackageSignature());
    values.put(LabelsTable.KEY_VIEW_NAME, label.getViewName());
    values.put(LabelsTable.KEY_TEXT, label.getText());
    values.put(LabelsTable.KEY_LOCALE, label.getLocale());
    values.put(LabelsTable.KEY_PACKAGE_VERSION, label.getPackageVersion());
    values.put(LabelsTable.KEY_SCREENSHOT_PATH, label.getScreenshotPath());
    values.put(LabelsTable.KEY_TIMESTAMP, label.getTimestamp());

    return values;
  }

  /**
   * Gets a {@link Label} object from the data in the given cursor at the current row position.
   *
   * @param cursor The cursor to use to get the label.
   * @return The label at the current cursor position, or {@code null} if the current cursor
   *     position has no row.
   */
  private Label getLabelFromCursorAtCurrentPosition(Cursor cursor) {
    if (cursor == null || cursor.isClosed() || cursor.isAfterLast()) {
      LogUtils.w(TAG, "Failed to get label from cursor.");
      return null;
    }

    final long labelId = cursor.getLong(LabelsTable.INDEX_ID);
    final String packageName = cursor.getString(LabelsTable.INDEX_PACKAGE_NAME);
    final String packageSignature = cursor.getString(LabelsTable.INDEX_PACKAGE_SIGNATURE);
    final String viewName = cursor.getString(LabelsTable.INDEX_VIEW_NAME);
    final String text = cursor.getString(LabelsTable.INDEX_TEXT);
    final String locale = cursor.getString(LabelsTable.INDEX_LOCALE);
    final int packageVersion = cursor.getInt(LabelsTable.INDEX_PACKAGE_VERSION);
    final String screenshotPath = cursor.getString(LabelsTable.INDEX_SCREENSHOT_PATH);
    final long timestamp = cursor.getLong(LabelsTable.INDEX_TIMESTAMP);

    return new Label(
        labelId,
        packageName,
        packageSignature,
        viewName,
        text,
        locale,
        packageVersion,
        screenshotPath,
        timestamp);
  }

  /**
   * Gets a pair of package name and label count from the data in the given cursor at the current
   * row position.
   *
   * @param cursor The cursor to use to get the label.
   * @return A pair of package name and label count, or {@code null} if the current cursor position
   *     has no row.
   */
  private PackageLabelInfo getPackageLabelInfoFromCursor(Cursor cursor) {
    if (cursor == null || cursor.isClosed() || cursor.isAfterLast()) {
      LogUtils.w(TAG, "Failed to get PackageLabelInfo from cursor.");
      return null;
    }

    final String packageName = cursor.getString(0);
    final int labelCount = cursor.getInt(1);

    return new PackageLabelInfo(packageName, labelCount);
  }

  /**
   * Gets a single label from a cursor as the result of a query.
   *
   * @param cursor The cursor from which to get the label.
   * @return The label returned from the query, or {@code null} if no valid label was returned.
   */
  private Label getLabelFromCursor(Cursor cursor) {
    if (cursor == null) {
      return null;
    }

    cursor.moveToFirst();
    final Label result = getLabelFromCursorAtCurrentPosition(cursor);

    logResult(result);

    return result;
  }

  /**
   * Gets an unmodifiable list of labels from a cursor resulting from a query.
   *
   * @param cursor The cursor from which to get the labels.
   * @return The unmodifiable list of labels returned from the query.
   */
  private List<Label> getLabelListFromCursor(Cursor cursor) {
    if (cursor == null) {
      return Collections.emptyList();
    }

    final List<Label> result = new ArrayList<>();
    while (cursor.moveToNext()) {
      final Label label = getLabelFromCursorAtCurrentPosition(cursor);
      if (label != null) {
        result.add(label);
      }
    }

    logResult(result);

    return Collections.unmodifiableList(result);
  }

  /**
   * Gets an unmodifiable list of {@link PackageLabelInfo} objects from a cursor resulting from a
   * query.
   *
   * @param cursor The cursor from which to get the package summary.
   * @return The unmodifiable list built from the query.
   */
  private List<PackageLabelInfo> getPackageSummaryFromCursor(Cursor cursor) {
    if (cursor == null) {
      return Collections.emptyList();
    }

    final List<PackageLabelInfo> result = new ArrayList<>();
    while (cursor.moveToNext()) {
      final PackageLabelInfo packageLabelInfo = getPackageLabelInfoFromCursor(cursor);
      if (packageLabelInfo != null) {
        result.add(packageLabelInfo);
      }
    }

    return Collections.unmodifiableList(result);
  }

  /**
   * Gets an unmodifiable map of labels from a cursor resulting from a query.
   *
   * @param cursor The cursor from which to get the labels.
   * @return An unmodifiable map from view names to label objects containing all labels returned
   *     from the query.
   */
  private Map<String, Label> getLabelMapFromCursor(Cursor cursor) {
    if (cursor == null) {
      return Collections.emptyMap();
    }

    final int labelCount = cursor.getCount(); // can return -1
    final int initialCapacity = Math.max(labelCount, 0);

    final Map<String, Label> result = new HashMap<>(initialCapacity);
    while (cursor.moveToNext()) {
      final Label label = getLabelFromCursorAtCurrentPosition(cursor);
      if (label != null) {
        result.put(label.getViewName(), label);
      }
    }

    logResult(result.values());

    return Collections.unmodifiableMap(result);
  }

  /**
   * Logs a label resulting from a query.
   *
   * @param label The label to log.
   */
  private void logResult(Label label) {
    LogUtils.v(TAG, "Query result: %s.", label);
  }

  /**
   * Logs labels resulting from a query.
   *
   * @param result The labels to log.
   */
  private void logResult(Iterable<Label> result) {
    if (LogUtils.getLogLevel() >= Log.VERBOSE) {
      final StringBuilder logMessageBuilder = new StringBuilder("Query result: [");
      for (Label label : result) {
        logMessageBuilder.append("\n  ");
        logMessageBuilder.append(label);
      }
      logMessageBuilder.append("].");

      LogUtils.v(TAG, logMessageBuilder.toString());
    }
  }

  /**
   * Checks for a client and logs a warning if it is {@code null}.
   *
   * @return Whether the client is non-{@code null}.
   */
  private boolean checkClient() {
    if (mClient == null) {
      LogUtils.w(TAG, "Aborting operation: the client failed to initialize or already shut down.");
      return false;
    }

    return true;
  }
}
