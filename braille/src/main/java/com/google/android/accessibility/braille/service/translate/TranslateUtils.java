/*
 * Copyright 2020 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.braille.service.translate;

import android.content.res.Resources;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/** Utils for translation. */
public class TranslateUtils {
  private static final String TAG = "TranslateUtils";

  public static boolean extractTables(Resources resources, int rawResId, File output) {
    List<File> extractedFiles = new ArrayList<>();
    final InputStream stream = resources.openRawResource(rawResId);
    final ZipInputStream zipStream = new ZipInputStream(new BufferedInputStream(stream));
    try {
      extractEntries(zipStream, output, extractedFiles);
      return true;
    } catch (Exception e) {
      LogUtils.e(TAG, "Exception during extractEntries()", e);
      removeExtractedFiles(extractedFiles);
      return false;
    } finally {
      try {
        zipStream.close();
      } catch (IOException e) {
        LogUtils.e(TAG, "Exception during zipStream.close()", e);
      }
    }
  }

  private static void extractEntries(
      ZipInputStream zipStream, File output, List<File> extractedFiles) throws IOException {
    final byte[] buffer = new byte[10240];
    int bytesRead;
    ZipEntry entry;

    while ((entry = zipStream.getNextEntry()) != null) {
      final File outputFile = newFile(output, entry);
      extractedFiles.add(outputFile);
      if (entry.isDirectory()) {
        outputFile.mkdirs();
        makeReadable(outputFile);
        continue;
      }

      // Ensure the target path exists.
      outputFile.getParentFile().mkdirs();
      final FileOutputStream outputStream = new FileOutputStream(outputFile);

      while ((bytesRead = zipStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, bytesRead);
      }

      outputStream.close();
      zipStream.closeEntry();

      // Make sure the output file is readable.
      makeReadable(outputFile);
    }
  }

  private static void removeExtractedFiles(List<File> extractedFiles) {
    for (File extractedFile : extractedFiles) {
      if (!extractedFile.isDirectory()) {
        extractedFile.delete();
      }
    }

    extractedFiles.clear();
  }

  private static void makeReadable(File file) {
    if (!file.canRead()) {
      file.setReadable(true);
    }
  }

  /**
   * Create a new File underneath a directory that corresponds to the relative path of a ZipEntry.
   *
   * @param parent the parent directory under which the zip file should be extracted
   * @param zipEntry the ZipEntry of the file that will be extracted
   * @return a new {@link File} below the {@code parent} directory
   * @throws ZipException if {@code zipEntry} will be outside of the parent directory due to path
   *     traversal
   * @throws IOException if unable to verify that the file will be under the parent directory
   */
  private static File newFile(File parent, ZipEntry zipEntry) throws IOException {
    String name = zipEntry.getName();
    File f = new File(parent, name);
    String canonicalPath = f.getCanonicalPath();
    if (!canonicalPath.startsWith(parent.getCanonicalPath())) {
      throw new ZipException("Illegal name: " + name);
    }
    return f;
  }

  private TranslateUtils() {}
}
