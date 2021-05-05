/*
 * Copyright (C) 2020 Google Inc.
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
package com.google.android.accessibility.talkback.gesture;

import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.content.Context;
import android.net.Uri;
import androidx.core.content.FileProvider;
import android.view.MotionEvent;
import com.google.android.accessibility.talkback.BuildConfig;
import com.google.android.accessibility.talkback.actor.GestureReporter;
import com.google.android.accessibility.utils.compat.CompatUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/** Maintains a history of 10 latest gestures with motion events. */
public class GestureHistory {
  public static final String TAG = GestureReporter.TAG;
  private static final int MAX_SIZE = 10;

  /** Inner data-structure for gesture data. */
  @AutoValue
  public abstract static class GestureInfo {
    public abstract int id();

    public abstract List<MotionEvent> motionEvents();

    public static GestureInfo create(int id, List<MotionEvent> motionEvents) {
      return new AutoValue_GestureHistory_GestureInfo(id, ImmutableList.copyOf(motionEvents));
    }
  }

  private final Deque<GestureInfo> gestureInfoList;

  public GestureHistory() {
    gestureInfoList = new ArrayDeque<>();
  }

  /** Save {@code accessibilityGestureEvent} to the history queue and maintain the size. */
  public boolean save(AccessibilityGestureEvent accessibilityGestureEvent) {
    // TODO  : Remove the code to use reflection to get motion events after public API
    // is ready.
    @SuppressWarnings("unchecked")
    List<MotionEvent> motionEvents =
        (List<MotionEvent>)
            CompatUtils.invoke(
                accessibilityGestureEvent,
                new ArrayList<>(),
                CompatUtils.getMethod(AccessibilityGestureEvent.class, "getMotionEvents"));
    if (motionEvents.isEmpty()) {
      return false;
    }
    gestureInfoList.offer(
        GestureInfo.create(accessibilityGestureEvent.getGestureId(), motionEvents));
    while (gestureInfoList.size() > MAX_SIZE) {
      gestureInfoList.pollFirst();
    }
    return true;
  }

  /** Returns the url of gesture file for sharing. */
  public Uri getFileUri(Context context) {
    return FileUtil.writeFile(context, gestureInfoList);
  }

  /** Returns the gesture list as string to display. */
  public String getGestureListString(Context context) {
    StringBuilder builder = new StringBuilder();
    builder.append(LINE_SEPARATOR.value());
    Iterator<GestureInfo> iterator = gestureInfoList.iterator();
    int i = 0;
    while (iterator.hasNext()) {
      i++;
      GestureInfo nextGesture = iterator.next();
      builder.append(i);
      builder.append(" ");
      builder.append(GestureShortcutMapping.getGestureString(context, nextGesture.id()));
      builder.append(LINE_SEPARATOR.value());
    }
    return builder.toString();
  }

  /** Returns true if the history is empty. */
  public boolean isEmpty() {
    return gestureInfoList.isEmpty();
  }

  /** Utility class to handle files. */
  private static class FileUtil {
    private static final String FILE_FOLDER_NAME = "Gesture";
    private static final String FILE_NAME = "gesture.log";
    private static final String FILE_AUTHORITY =
        BuildConfig.APPLICATION_ID + ".providers.FileProvider";

    /** Writes gesture data into a file and return the file uri. */
    public static Uri writeFile(Context context, Deque<GestureInfo> gestureInfos) {
      File file = writeToFile(context, FILE_NAME, getFileContent(context, gestureInfos));
      LogUtils.v(TAG, "write gesture file with size:" + gestureInfos.size());
      return FileProvider.getUriForFile(context, FILE_AUTHORITY, file);
    }

    private static String getFileContent(Context context, Deque<GestureInfo> gestureInfos) {
      StringBuilder builder = new StringBuilder();
      Iterator<GestureInfo> iterator = gestureInfos.iterator();
      int i = 0;
      while (iterator.hasNext()) {
        i++;
        GestureInfo nextGesture = iterator.next();
        builder.append(getFileHeader(context, i, nextGesture.id()));
        builder.append(LINE_SEPARATOR.value());
        for (MotionEvent event : nextGesture.motionEvents()) {
          builder.append(event.toString());
          builder.append(LINE_SEPARATOR.value());
        }
      }
      return builder.toString();
    }

    private static String getFileHeader(Context context, int index, int id) {
      StringBuilder builder = new StringBuilder();
      builder.append(" * ");
      builder.append(FILE_FOLDER_NAME);
      builder.append(index);
      builder.append("_id");
      builder.append(id);
      builder.append(":");
      builder.append(GestureShortcutMapping.getGestureString(context, id));
      return builder.toString();
    }

    private static File writeToFile(Context context, String fileName, String fileContent) {
      File file = new File(context.getExternalFilesDir(FILE_FOLDER_NAME), fileName);
      try (FileOutputStream fop = new FileOutputStream(file)) {
        if (!file.exists()) {
          file.createNewFile();
        }
        byte[] contentInBytes = fileContent.getBytes();
        fop.write(contentInBytes);
        fop.flush();
      } catch (IOException e) {
        LogUtils.e(TAG, "writeToFile IOException:" + e);
      }
      return file;
    }
  }
}
