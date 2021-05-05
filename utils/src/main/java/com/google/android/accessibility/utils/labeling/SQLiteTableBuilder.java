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

import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

/** An object that builds and executes a query for creating a new SQLite table. */
public class SQLiteTableBuilder {
  private static final String CREATE_TABLE = "CREATE TABLE ";
  private static final String OPEN_PAREN = "(";
  private static final String CLOSE_PAREN = ")";
  private static final String COMMA = ", ";
  private static final String PRIMARY_KEY = " PRIMARY KEY";

  private static final String INTEGER = " INTEGER";
  private static final String REAL = " REAL";
  private static final String TEXT = " TEXT";
  private static final String BLOB = " BLOB";

  /*
   * Valid SQL identifiers are a sequence of uppercase or lowercase letters,
   * numbers, and underscores. They can't be empty or start with a number.
   */
  private static final String REGEX_SQL_IDENTIFIER = "^[a-zA-Z_][a-zA-Z0-9_]*$";

  public static final int TYPE_INTEGER = 1;
  private static final int TYPE_REAL = 2;
  public static final int TYPE_TEXT = 3;
  private static final int TYPE_BLOB = 4;

  private StringBuilder mStringBuilder;
  private SQLiteDatabase mDatabase;
  private boolean mHasColumns = false;
  private boolean mCreated = false;

  /**
   * Creates a new instance for creating a table with the given name in the specified database.
   *
   * @param database The database in which to create the table.
   * @param tableName A SQL identifier for the name of the new table.
   */
  public SQLiteTableBuilder(SQLiteDatabase database, String tableName) {
    if (database == null) {
      throw new IllegalArgumentException("Database cannot be null.");
    } else if (TextUtils.isEmpty(tableName)) {
      throw new IllegalArgumentException("Table name cannot be empty.");
    } else if (!tableName.matches(REGEX_SQL_IDENTIFIER)) {
      throw new IllegalArgumentException("Invalid table name.");
    }

    mDatabase = database;

    mStringBuilder = new StringBuilder();
    mStringBuilder.append(CREATE_TABLE);
    mStringBuilder.append(tableName);
    mStringBuilder.append(OPEN_PAREN);
  }

  /**
   * Adds a column to the current table that is not a primary key.
   *
   * @param columnName A SQL identifier representing the name of the column. A valid SQL identifier
   *     consists of uppercase or lowercase letters, numbers, and underscores. It cannot be empty or
   *     start with a number.
   * @param type Must be one of {@link #TYPE_INTEGER}, {@link #TYPE_REAL}, {@link #TYPE_TEXT}, or
   *     {@link #TYPE_BLOB}.
   * @return the current {@link SQLiteTableBuilder} instance, with the newly added column.
   */
  public SQLiteTableBuilder addColumn(String columnName, int type) {
    return addColumn(columnName, type, false);
  }

  /**
   * Adds a column to the current table.
   *
   * @param columnName A SQL identifier representing the name of the column. A valid SQL identifier
   *     consists of uppercase or lowercase letters, numbers, and underscores. It cannot be empty or
   *     start with a number.
   * @param type Must be one of {@link #TYPE_INTEGER}, {@link #TYPE_REAL}, {@link #TYPE_TEXT}, or
   *     {@link #TYPE_BLOB}.
   * @param primaryKey Whether or not the column is a primary key.
   * @return the current {@link SQLiteTableBuilder} instance, with the newly added column.
   */
  public SQLiteTableBuilder addColumn(String columnName, int type, boolean primaryKey) {
    if (TextUtils.isEmpty(columnName)) {
      throw new IllegalArgumentException("Column name cannot be empty.");
    } else if (!columnName.matches(REGEX_SQL_IDENTIFIER)) {
      throw new IllegalArgumentException("Invalid column name.");
    }

    if (mHasColumns) {
      mStringBuilder.append(COMMA);
    }

    mStringBuilder.append(columnName);
    appendType(type);
    if (primaryKey) {
      mStringBuilder.append(PRIMARY_KEY);
    }

    mHasColumns = true;

    return this;
  }

  private void appendType(int type) {
    switch (type) {
      case TYPE_INTEGER:
        mStringBuilder.append(INTEGER);
        break;
      case TYPE_REAL:
        mStringBuilder.append(REAL);
        break;
      case TYPE_TEXT:
        mStringBuilder.append(TEXT);
        break;
      case TYPE_BLOB:
        mStringBuilder.append(BLOB);
        break;
      default:
        throw new IllegalArgumentException("Unrecognized data type.");
    }
  }

  /** @return The query string to execute in order to create the table. */
  String buildQueryString() {
    return String.format("%s%s", mStringBuilder.toString(), CLOSE_PAREN);
  }

  /**
   * Executes the query to create the table.
   *
   * <p>Note: This method can only be called once per instance of this class.
   */
  public void createTable() throws SQLException {
    if (mCreated) {
      throw new IllegalStateException("createTable was already called on this instance.");
    }

    mDatabase.execSQL(buildQueryString());
    mCreated = true;
  }
}
