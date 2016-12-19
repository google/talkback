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

package com.android.talkback.labeling;

import android.annotation.TargetApi;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.test.ProviderTestCase2;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import com.android.utils.LogUtils;
import com.android.utils.labeling.LabelsTable;

import java.util.Locale;

/**
 * Tests for {@link com.android.talkback.labeling.LabelProvider}.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class LabelProviderTest extends ProviderTestCase2<LabelProvider> {
    private static final Uri INVALID_AUTHORITY_URI = new Uri.Builder()
            .scheme("content")
            .authority("invalid.authority")
            .path(LabelProvider.LABELS_PATH)
            .build();
    private static final Uri INVALID_PATH_URI = new Uri.Builder()
            .scheme("content")
            .authority(LabelProvider.AUTHORITY)
            .path("invalid/path")
            .build();
    private static final Uri LABEL_NEGATIVE_1_URI =
            ContentUris.withAppendedId(LabelProvider.LABELS_CONTENT_URI, -1L);
    private static final Uri LABEL_1_URI =
            ContentUris.withAppendedId(LabelProvider.LABELS_CONTENT_URI, 1L);

    private Locale mOriginalLocale;
    private int mOriginalLogLevel;

    public LabelProviderTest() {
        super(LabelProvider.class, LabelProvider.AUTHORITY);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Run the entire test with TalkBack locale set to ar_EG (Arabic/Egypt) locale.
        mOriginalLocale = getMockContext().getResources().getConfiguration().locale;
        setLocale(new Locale("ar", "EG"));

        mOriginalLogLevel = LogUtils.LOG_LEVEL;
        LogUtils.setLogLevel(Log.VERBOSE);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        setLocale(mOriginalLocale);
        LogUtils.LOG_LEVEL = mOriginalLogLevel;
    }

    private void setLocale(Locale locale) {
        Locale.setDefault(locale);

        Resources resources = getMockContext().getResources();
        Configuration config = resources.getConfiguration();
        config.locale = locale;
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    /**
     * Tests URI matching in {@link com.android.talkback.labeling.LabelProvider}.
     * <p>
     * Note: This behavior seems to change slightly between framework versions,
     * e.g. between version 17 and 18. This test checks to see if the
     * {@link android.content.UriMatcher} works as expected in the provider code.
     */
    @SmallTest
    public void testUriMatcher() {
        try {
            LabelProvider.sUriMatcher.match(null);

            fail("Expected exception.");
        } catch (NullPointerException expected) {
            // Expected
        }

        assertEquals(UriMatcher.NO_MATCH, LabelProvider.sUriMatcher.match(INVALID_AUTHORITY_URI));
        assertEquals(UriMatcher.NO_MATCH, LabelProvider.sUriMatcher.match(INVALID_PATH_URI));
        assertEquals(LabelProvider.LABELS,
                LabelProvider.sUriMatcher.match(LabelProvider.LABELS_CONTENT_URI));
        assertEquals(UriMatcher.NO_MATCH, LabelProvider.sUriMatcher.match(LABEL_NEGATIVE_1_URI));
        assertEquals(LabelProvider.LABELS_ID, LabelProvider.sUriMatcher.match(LABEL_1_URI));
    }

    /**
     * Tests {@link com.android.talkback.labeling.LabelProvider#insert(android.net.Uri, android.content.ContentValues)} with an invalid
     * URI argument.
     */
    @SmallTest
    public void testInsert_invalidUri() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();

        final Uri resultUri;
        try {
            resultUri = client.insert(INVALID_PATH_URI, new ContentValues());
        } finally {
            client.release();
        }

        assertNull(resultUri);
    }

    /**
     * Tests {@link com.android.talkback.labeling.LabelProvider#insert(android.net.Uri, android.content.ContentValues)} with {@code null}
     * content values.
     */
    @SmallTest
    public void testInsert_nullContentValues() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();

        final Uri resultUri;
        try {
            resultUri = client.insert(LabelProvider.LABELS_CONTENT_URI, null);
        } finally {
            client.release();
        }

        assertNull(resultUri);
    }

    /**
     * Tests {@link com.android.talkback.labeling.LabelProvider#insert(android.net.Uri, android.content.ContentValues)} with empty content
     * values.
     */
    @SmallTest
    public void testInsert_emptyContentValues() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();
        final ContentValues contentValues = new ContentValues();

        final Uri resultUri;
        try {
            resultUri = client.insert(LabelProvider.LABELS_CONTENT_URI, contentValues);
        } finally {
            client.release();
        }

        assertNull(resultUri);
    }

    /**
     * Tests {@link com.android.talkback.labeling.LabelProvider#insert(android.net.Uri, android.content.ContentValues)} with an extra
     * content value.
     */
    @SmallTest
    public void testInsert_extraContentValues() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();

        final ContentValues contentValues = getValidLabelContentValues();
        contentValues.put("invalid_key", 100);

        final Uri resultUri;
        try {
            resultUri = client.insert(LabelProvider.LABELS_CONTENT_URI, contentValues);
        } finally {
            client.release();
        }

        assertNull(resultUri);
    }

    /**
     * Tests {@link com.android.talkback.labeling.LabelProvider#insert(android.net.Uri, android.content.ContentValues)} with specified
     * label ID.
     */
    @SmallTest
    public void testInsert_specifiedlabelId() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();

        final ContentValues contentValues = getValidLabelContentValues();
        contentValues.put(LabelsTable.KEY_ID, 1L);

        final Uri resultUri;
        try {
            resultUri = client.insert(LabelProvider.LABELS_CONTENT_URI, contentValues);
        } finally {
            client.release();
        }

        assertNull(resultUri);
    }

    /**
     * Tests {@link com.android.talkback.labeling.LabelProvider#insert(android.net.Uri, android.content.ContentValues)} with valid content
     * values.
     */
    @SmallTest
    public void testInsert_success() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();

        final ContentValues contentValues = getValidLabelContentValues();

        final Uri resultUri;
        try {
            resultUri = client.insert(LabelProvider.LABELS_CONTENT_URI, contentValues);
        } finally {
            client.release();
        }

        final Uri expectedUri = ContentUris.withAppendedId(LabelProvider.LABELS_CONTENT_URI, 1L);
        assertEquals(expectedUri, resultUri);
    }

    /**
     * Tests the {@link android.content.ContentProvider} query method
     * {@link com.android.talkback.labeling.LabelProvider#query(android.net.Uri, String[], String, String[], String)} with
     * an invalid URI argument.
     */
    @SmallTest
    public void testQuery_invalidUri() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();

        final Cursor resultCursor;
        try {
            resultCursor = client.query(
                    INVALID_PATH_URI, new String[] {}, "", new String[] {}, null);
        } finally {
            client.release();
        }

        assertNull(resultCursor);
    }

    /**
     * Tests the {@link android.content.ContentProvider} query method
     * {@link com.android.talkback.labeling.LabelProvider#query(android.net.Uri, String[], String, String[], String)} with
     * the labels content URI argument.
     */
    @SmallTest
    public void testQuery_labels() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();

        final Cursor resultCursor;
        try {
            resultCursor = client.query(LabelProvider.LABELS_CONTENT_URI, LabelsTable.ALL_COLUMNS,
                    null, null, null);
        } finally {
            client.release();
        }

        assertNotNull(resultCursor);
    }

    /**
     * Tests the {@link android.content.ContentProvider} query method
     * {@link com.android.talkback.labeling.LabelProvider#query(android.net.Uri, String[], String, String[], String)} with
     * the labels ID content URI argument.
     */
    @SmallTest
    public void testQuery_labelsId() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();

        final Cursor resultCursor;
        try {
            resultCursor = client.query(LABEL_1_URI, LabelsTable.ALL_COLUMNS, null, null, null);
        } finally {
            client.release();
        }

        assertNotNull(resultCursor);
    }

    /**
     * Tests {@link com.android.talkback.labeling.LabelProvider#update(android.net.Uri, android.content.ContentValues, String, String[])}
     * with an invalid URI parameter.
     */
    @SmallTest
    public void testUpdate_invalidUri() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();

        final int affectedRows;
        try {
            affectedRows =
                    client.update(INVALID_PATH_URI, getValidLabelContentValues(), null, null);
        } finally {
            client.release();
        }

        assertEquals(0, affectedRows);
    }

    /**
     * Tests {@link com.android.talkback.labeling.LabelProvider#update(android.net.Uri, android.content.ContentValues, String, String[])}
     * with the labels URI parameter (not supported).
     */
    @SmallTest
    public void testUpdate_labelsUri() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();

        final int affectedRows;
        try {
            affectedRows = client.update(LabelProvider.LABELS_CONTENT_URI,
                    getValidLabelContentValues(), null, null);
        } finally {
            client.release();
        }

        assertEquals(0, affectedRows);
    }

    /**
     * Tests {@link com.android.talkback.labeling.LabelProvider#update(android.net.Uri, android.content.ContentValues, String, String[])}
     * with an invalid label ID.
     */
    @SmallTest
    public void testUpdate_invalidId() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();

        final int affectedRows;
        try {
            affectedRows = client.update(LABEL_NEGATIVE_1_URI, getValidLabelContentValues(),
                    null, null);
        } finally {
            client.release();
        }

        assertEquals(0, affectedRows);
    }


    /**
     * Tests {@link com.android.talkback.labeling.LabelProvider#update(android.net.Uri, android.content.ContentValues, String, String[])}
     * with valid arguments.
     * <p>
     * Note: This test depends on {@link com.android.talkback.labeling.LabelProvider#insert} functionality.
     */
    @SmallTest
    public void testUpdate_success() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();
        final ContentValues contentValues = getValidLabelContentValues();

        final Uri insertResult = client.insert(LabelProvider.LABELS_CONTENT_URI, contentValues);

        final int affectedRows;
        try {
            affectedRows = client.update(LABEL_1_URI, contentValues, null, null);
        } finally {
            client.release();
        }

        assertEquals(LABEL_1_URI, insertResult);
        assertEquals(1, affectedRows);
    }

    /**
     * Tests {@link com.android.talkback.labeling.LabelProvider#update(android.net.Uri, android.content.ContentValues, String, String[])}
     * with a selection argument.
     * <p>
     * Note: This test depends on {@link LabelProviderTest#testUpdate_success}.
     */
    @SmallTest
    public void testUpdate_selection() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();
        final ContentValues contentValues = getValidLabelContentValues();

        final Uri insertResult = client.insert(LabelProvider.LABELS_CONTENT_URI, contentValues);

        final String selection = String.format("%s = ?", LabelsTable.KEY_ID);
        final String[] selectionArgs = { Integer.toString(2) };

        final int affectedRows;
        try {
            affectedRows = client.update(LABEL_1_URI, contentValues, selection, selectionArgs);
        } finally {
            client.release();
        }

        assertEquals(LABEL_1_URI, insertResult);
        assertEquals(0, affectedRows);
    }

    /**
     * Tests {@link com.android.talkback.labeling.LabelProvider#delete(android.net.Uri, String, String[])}
     * with an invalid URI parameter.
     */
    @SmallTest
    public void testDelete_invalidUri() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();

        final int affectedRows;
        try {
            affectedRows = client.delete(INVALID_PATH_URI, null, null);
        } finally {
            client.release();
        }

        assertEquals(0, affectedRows);
    }

    /**
     * Tests {@link com.android.talkback.labeling.LabelProvider#delete(android.net.Uri, String, String[])}
     * with the labels URI parameter (not supported).
     */
    @SmallTest
    public void testDelete_labelsUri() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();

        final int affectedRows;
        try {
            affectedRows = client.delete(LabelProvider.LABELS_CONTENT_URI, null, null);
        } finally {
            client.release();
        }

        assertEquals(0, affectedRows);
    }

    /**
     * Tests {@link com.android.talkback.labeling.LabelProvider#delete(android.net.Uri, String, String[])}
     * with an invalid label ID.
     */
    @SmallTest
    public void testDelete_invalidId() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();

        final int affectedRows;
        try {
            affectedRows = client.delete(LABEL_NEGATIVE_1_URI, null, null);
        } finally {
            client.release();
        }

        assertEquals(0, affectedRows);
    }

    /**
     * Tests {@link com.android.talkback.labeling.LabelProvider#delete(android.net.Uri, String, String[])} with valid
     * arguments.
     * <p>
     * Note: This test depends on {@link com.android.talkback.labeling.LabelProvider#insert} functionality.
     */
    @SmallTest
    public void testDelete_success() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();
        final ContentValues contentValues = getValidLabelContentValues();

        final Uri insertResult = client.insert(LabelProvider.LABELS_CONTENT_URI, contentValues);

        final int affectedRows;
        try {
            affectedRows = client.delete(LABEL_1_URI, null, null);
        } finally {
            client.release();
        }

        assertEquals(LABEL_1_URI, insertResult);
        assertEquals(1, affectedRows);
    }

    /**
     * Tests {@link com.android.talkback.labeling.LabelProvider#delete(android.net.Uri, String, String[])} with a
     * selection argument.
     * <p>
     * Note: This test depends on {@link LabelProviderTest#testDelete_success}.
     */
    @SmallTest
    public void testDelete_selection() throws RemoteException {
        final ContentProviderClient client = getContentProviderClient();
        final ContentValues contentValues = getValidLabelContentValues();

        final Uri insertResult = client.insert(LabelProvider.LABELS_CONTENT_URI, contentValues);

        final String selection = String.format("%s = ?", LabelsTable.KEY_ID);
        final String[] selectionArgs = { Integer.toString(2) };

        final int affectedRows;
        try {
            affectedRows = client.delete(LABEL_1_URI, selection, selectionArgs);
        } finally {
            client.release();
        }

        assertEquals(LABEL_1_URI, insertResult);
        assertEquals(0, affectedRows);
    }

    private ContentProviderClient getContentProviderClient() {
        final ContentResolver resolver = getMockContentResolver();
        return resolver.acquireContentProviderClient(LabelProvider.LABELS_CONTENT_URI);
    }

    private ContentValues getValidLabelContentValues() {
        final ContentValues contentValues = new ContentValues();
        contentValues.put(LabelsTable.KEY_PACKAGE_NAME, "packageName");
        contentValues.put(LabelsTable.KEY_VIEW_NAME, "viewName");
        contentValues.put(LabelsTable.KEY_TEXT, "text");
        contentValues.put(LabelsTable.KEY_LOCALE, "locale");
        contentValues.put(LabelsTable.KEY_PACKAGE_VERSION, 2);
        contentValues.put(LabelsTable.KEY_SCREENSHOT_PATH, "screenshothPath");
        contentValues.put(LabelsTable.KEY_TIMESTAMP, 3L);

        return contentValues;
    }
}