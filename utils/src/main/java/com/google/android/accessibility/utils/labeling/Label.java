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

import com.google.android.accessibility.utils.LocaleUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/** A model class for a custom view label that TalkBack can speak. */
public class Label {
  /** The ID of a label that has not yet been stored in the database. */
  public static final long NO_ID = -1L;

  private static final String DEBUG_FORMAT_STRING =
      "%s[id=%d, packageName=%s, "
          + "packageSignature=%s, viewName=%s, text=%s, locale=%s, packageVersion=%d, "
          + "screenshotPath=%s, timestamp=%d]";

  private long mId;
  private String mPackageName;
  private String mPackageSignature;
  private String mViewName;
  private String mText;
  private String mLocale;
  private int mPackageVersion;
  private String mScreenshotPath;

  /**
   * The time the label was created or last modified, measured in milliseconds since midnight on
   * January 1, 1970 UTC.
   */
  private long mTimestampMillis;

  /**
   * Creates a new label, usually one that exists in the local database.
   *
   * @param labelId A unique local identifier for the label.
   * @param packageName The package name for the labeled application.
   * @param packageSignature A string that uniquely identifies the package.
   * @param viewName The fully qualified resource name for the labeled view.
   * @param text The text that should be displayed as the label.
   * @param locale The locale of the label text.
   * @param packageVersion The labeled application's package version code.
   * @param screenshotPath The local path to a screenshot of the labeled view.
   * @param timestampMillis The time the label was created or last modified.
   */
  public Label(
      long labelId,
      String packageName,
      String packageSignature,
      String viewName,
      String text,
      String locale,
      int packageVersion,
      String screenshotPath,
      long timestampMillis) {
    mId = labelId;
    mPackageName = packageName;
    mPackageSignature = packageSignature;
    mViewName = viewName;
    mText = text;
    setLocale(locale);
    mPackageVersion = packageVersion;
    mScreenshotPath = screenshotPath;
    mTimestampMillis = timestampMillis;
  }

  /**
   * Creates a new label, usually one that does not yet exist in the local database.
   *
   * @param packageName The package name for the labeled application.
   * @param packageSignature A string uniquely representing the package.
   * @param viewName The fully qualified resource name for the labeled view.
   * @param text The text that should be displayed as the label.
   * @param locale The locale of the label text.
   * @param packageVersion The labeled application's package version code.
   * @param screenshotPath The local path to a screenshot of the labeled view.
   * @param timestamp The time the label was created or last modified.
   */
  public Label(
      String packageName,
      String packageSignature,
      String viewName,
      String text,
      String locale,
      int packageVersion,
      String screenshotPath,
      long timestamp) {
    this(
        NO_ID,
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
   * Creates a label object that exists in the local database from a label object that does not
   * exist in the local database using a deep copy.
   *
   * <p>This effectively assigns an ID to an existing label.
   *
   * @param labelWithoutId The existing label to copy, without an ID.
   * @param labelId A unique local identifier for the label.
   */
  public Label(Label labelWithoutId, long labelId) {
    if (labelWithoutId.getId() != Label.NO_ID) {
      throw new IllegalArgumentException("Label to copy cannot have an ID already assigned.");
    }

    mId = labelId;
    mPackageName = labelWithoutId.mPackageName;
    mPackageSignature = labelWithoutId.mPackageSignature;
    mViewName = labelWithoutId.mViewName;
    mText = labelWithoutId.mText;
    setLocale(labelWithoutId.mLocale);
    mPackageVersion = labelWithoutId.mPackageVersion;
    mScreenshotPath = labelWithoutId.mScreenshotPath;
    mTimestampMillis = labelWithoutId.mTimestampMillis;
  }

  /**
   * @return A unique local identifier for the label, or {@link #NO_ID} if an identifier has not
   *     been assigned.
   */
  public long getId() {
    return mId;
  }

  /** @return The package name for the application containing the label. */
  public String getPackageName() {
    return mPackageName;
  }

  /** @param packageName The package name for the labeled application. */
  public void setPackageName(String packageName) {
    mPackageName = packageName;
  }

  /**
   * @return A hex-encoded SHA-1 hash of the signing certificates for the package containing the
   *     labeled application.
   */
  public String getPackageSignature() {
    return mPackageSignature;
  }

  /**
   * @param packageSignature A hex-encoded SHA-1 hash of the signing certificates for the package
   *     containing the labeled application.
   */
  public void setPackageSignature(String packageSignature) {
    mPackageSignature = packageSignature;
  }

  /** @return The fully qualified resource name for the labeled view. */
  public String getViewName() {
    return mViewName;
  }

  /** @param viewName The fully qualified resource name for the labeled view. */
  public void setViewName(String viewName) {
    mViewName = viewName;
  }

  /** @return The text that should be displayed as the label. */
  public String getText() {
    return mText;
  }

  /** @param text The text that should be displayed as the label. */
  public void setText(String text) {
    mText = text;
  }

  /** @return The locale of the label text. */
  public String getLocale() {
    return mLocale;
  }

  /** @param locale The locale of the label text. */
  public void setLocale(String locale) {
    mLocale = LocaleUtils.getLanguageLocale(locale);
  }

  /** @return The package version code of the labeled application. */
  public int getPackageVersion() {
    return mPackageVersion;
  }

  /** @param packageVersion The labeled application's package version code. */
  public void setPackageVersion(int packageVersion) {
    mPackageVersion = packageVersion;
  }

  /** @return The local path to a screenshot of the labeled view. */
  public String getScreenshotPath() {
    return mScreenshotPath;
  }

  /** @param screenshotPath The local path to a screenshot of the labeled view. */
  public void setScreenshotPath(String screenshotPath) {
    mScreenshotPath = screenshotPath;
  }

  /**
   * @return The time the label was created or last modified, measured in milliseconds since
   *     midnight on January 1, 1970 UTC.
   */
  public long getTimestamp() {
    return mTimestampMillis;
  }

  /**
   * @param timestampMillis The time the label was created or last modified, measured in
   *     milliseconds since midnight on Jan. 1, 1970 UTC.
   */
  public void setTimestamp(long timestampMillis) {
    mTimestampMillis = timestampMillis;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((mLocale == null) ? 0 : mLocale.hashCode());
    result = prime * result + ((mPackageName == null) ? 0 : mPackageName.hashCode());
    result = prime * result + ((mPackageSignature == null) ? 0 : mPackageSignature.hashCode());
    result = prime * result + ((mScreenshotPath == null) ? 0 : mScreenshotPath.hashCode());
    result = prime * result + ((mText == null) ? 0 : mText.hashCode());
    result = prime * result + (int) (mTimestampMillis ^ (mTimestampMillis >>> 32));
    result = prime * result + mPackageVersion;
    result = prime * result + ((mViewName == null) ? 0 : mViewName.hashCode());
    return result;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Label)) {
      return false;
    }

    final Label other = (Label) obj;

    if (mLocale == null) {
      if (other.mLocale != null) {
        return false;
      }
    } else if (!mLocale.equals(other.mLocale)) {
      return false;
    }

    if (mPackageName == null) {
      if (other.mPackageName != null) {
        return false;
      }
    } else if (!mPackageName.equals(other.mPackageName)) {
      return false;
    }

    if (mPackageSignature == null) {
      if (other.mPackageSignature != null) {
        return false;
      }
    } else if (!mPackageSignature.equals(other.mPackageSignature)) {
      return false;
    }

    if (mScreenshotPath == null) {
      if (other.mScreenshotPath != null) {
        return false;
      }
    } else if (!mScreenshotPath.equals(other.mScreenshotPath)) {
      return false;
    }

    if (mText == null) {
      if (other.mText != null) {
        return false;
      }
    } else if (!mText.equals(other.mText)) {
      return false;
    }

    if (mTimestampMillis != other.mTimestampMillis) {
      return false;
    }

    if (mPackageVersion != other.mPackageVersion) {
      return false;
    }

    if (mViewName == null) {
      if (other.mViewName != null) {
        return false;
      }
    } else if (!mViewName.equals(other.mViewName)) {
      return false;
    }

    return true;
  }

  /** @return A text representation of the object and its fields for debugging. */
  @Override
  public String toString() {
    return String.format(
        DEBUG_FORMAT_STRING,
        getClass().getSimpleName(),
        mId,
        mPackageName,
        mPackageSignature,
        mViewName,
        mText,
        mLocale,
        mPackageVersion,
        mScreenshotPath,
        mTimestampMillis);
  }
}
