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

package com.android.utils.labeling;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import com.android.utils.LogUtils;

/**
 * A table for storing custom TalkBack labels.
 */
public class LabelsTable {
    public static final String TABLE_NAME = "labels";

    public static final String KEY_ID = "_id";
    public static final String KEY_PACKAGE_NAME = "packageName";
    public static final String KEY_PACKAGE_SIGNATURE = "packageSignature";
    public static final String KEY_VIEW_NAME = "viewName";
    public static final String KEY_TEXT = "text";
    public static final String KEY_LOCALE = "locale";
    public static final String KEY_PACKAGE_VERSION = "packageVersion";
    public static final String KEY_SCREENSHOT_PATH = "screenshotPath";
    public static final String KEY_TIMESTAMP = "timestamp";

    public static final int INDEX_ID = 0;
    public static final int INDEX_PACKAGE_NAME = 1;
    public static final int INDEX_PACKAGE_SIGNATURE = 2;
    public static final int INDEX_VIEW_NAME = 3;
    public static final int INDEX_TEXT = 4;
    public static final int INDEX_LOCALE = 5;
    public static final int INDEX_PACKAGE_VERSION = 6;
    public static final int INDEX_SCREENSHOT_PATH = 7;
    public static final int INDEX_TIMESTAMP = 8;

    public static final String[] ALL_COLUMNS = new String[] {
        KEY_ID, KEY_PACKAGE_NAME, KEY_PACKAGE_SIGNATURE, KEY_VIEW_NAME, KEY_TEXT, KEY_LOCALE,
        KEY_PACKAGE_VERSION, KEY_SCREENSHOT_PATH, KEY_TIMESTAMP };

    public static void onCreate(SQLiteDatabase database) {
        LogUtils.log(LabelsTable.class, Log.INFO, "Creating table: %s.", TABLE_NAME);

        new SQLiteTableBuilder(database, TABLE_NAME)
            .addColumn(KEY_ID, SQLiteTableBuilder.TYPE_INTEGER, true)
            .addColumn(KEY_PACKAGE_NAME, SQLiteTableBuilder.TYPE_TEXT)
            .addColumn(KEY_PACKAGE_SIGNATURE, SQLiteTableBuilder.TYPE_TEXT)
            .addColumn(KEY_VIEW_NAME, SQLiteTableBuilder.TYPE_TEXT)
            .addColumn(KEY_TEXT, SQLiteTableBuilder.TYPE_TEXT)
            .addColumn(KEY_LOCALE, SQLiteTableBuilder.TYPE_TEXT)
            .addColumn(KEY_PACKAGE_VERSION, SQLiteTableBuilder.TYPE_INTEGER)
            .addColumn(KEY_SCREENSHOT_PATH, SQLiteTableBuilder.TYPE_TEXT)
            .addColumn(KEY_TIMESTAMP, SQLiteTableBuilder.TYPE_INTEGER)
            .createTable();
    }

    public static void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        // DB version 2 adds KEY_PACKAGE_SIGNATURE column. Since this was added
        // prior to release, we'll just clear the table.
        if (oldVersion < 2) {
            LogUtils.log(LabelsTable.class, Log.INFO,
                    "Dropping table %s to upgrade from version %d to version %d.", TABLE_NAME,
                    oldVersion, newVersion);
            database.execSQL(String.format("DROP TABLE IF EXISTS %s", TABLE_NAME));

            // Recreate table.
            onCreate(database);
        } else {
            throw new UnsupportedOperationException(
                    "Attempted database upgrade from unsupported database version.");
        }
    }
}
