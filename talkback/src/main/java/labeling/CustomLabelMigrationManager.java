/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import androidx.annotation.NonNull;
import android.text.TextUtils;
import android.widget.Toast;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.labeling.Label;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CustomLabelMigrationManager {

  private static final String TAG = "CustomLabelMigrManager";

  private static final String JSON_LABELS_ARRAY = "labels_array";
  private static final String JSON_LABEL_PACKAGE_NAME = "package_name";
  private static final String JSON_LABEL_PACKAGE_SIGNATURE = "package_signature";
  private static final String JSON_LABEL_VIEW_NAME = "view_name";
  private static final String JSON_LABEL_TEXT = "label_text";
  private static final String JSON_LABEL_LOCALE = "locale";
  private static final String JSON_PACKAGE_VERSION = "package_version";
  private static final String JSON_TIMESTAMP = "timestamp";

  public interface OnLabelMigrationCallback {
    public void onLabelsExported(File file);

    public void onLabelImported(int updateCount);

    public void onFail();
  }

  public static class SimpleLabelMigrationCallback implements OnLabelMigrationCallback {
    @Override
    public void onLabelsExported(File file) {}

    @Override
    public void onLabelImported(int updateCount) {}

    @Override
    public void onFail() {}
  }

  private ExecutorService executor;
  private Handler handler;
  private CustomLabelManager manager;
  private Context context;

  public CustomLabelMigrationManager(Context context) {
    executor = Executors.newSingleThreadExecutor();
    handler = new Handler();
    TalkBackService service = TalkBackService.getInstance();
    if (service != null) {
      manager = service.getLabelManager();
    } else {
      manager = new CustomLabelManager(context);
    }
    this.context = context;
  }

  public void exportLabels(final OnLabelMigrationCallback callback) {
    manager.getLabelsFromDatabase(
        new LabelsFetchRequest.OnLabelsFetchedListener() {
          @Override
          public void onLabelsFetched(List<Label> results) {
            if (results != null && results.size() > 0) {
              createLabelFileAsync(results, callback);
            } else {
              Toast.makeText(context, R.string.label_export_empty, Toast.LENGTH_SHORT).show();
            }
          }
        });
  }

  private void createLabelFileAsync(
      final List<Label> labels, final OnLabelMigrationCallback callback) {
    executor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              String jsonText = generateJsonText(labels);
              if (jsonText != null) {
                final File file = getFilePath();
                writeToFile(jsonText, file);
                if (callback != null) {
                  handler.post(
                      new Runnable() {
                        @Override
                        public void run() {
                          callback.onLabelsExported(file);
                        }
                      });
                }
              } else {
                notifyFailure(callback);
              }
            } catch (Exception e) {
              notifyFailure(callback);
              LogUtils.e(TAG, "Failed to export labels");
            }
          }
        });
  }

  private void notifyFailure(final OnLabelMigrationCallback callback) {
    if (callback != null) {
      handler.post(
          new Runnable() {
            @Override
            public void run() {
              callback.onFail();
            }
          });
    }
  }

  // public visibility for tests
  public String generateJsonText(List<Label> labels) throws JSONException {
    JSONObject root = new JSONObject();
    JSONArray labelsArray = new JSONArray();
    for (Label label : labels) {
      if (label != null) {
        JSONObject labelObject = new JSONObject();
        labelObject.put(JSON_LABEL_PACKAGE_NAME, label.getPackageName());
        labelObject.put(JSON_LABEL_PACKAGE_SIGNATURE, label.getPackageSignature());
        labelObject.put(JSON_LABEL_VIEW_NAME, label.getViewName());
        labelObject.put(JSON_LABEL_TEXT, label.getText());
        labelObject.put(JSON_LABEL_LOCALE, label.getLocale());
        labelObject.put(JSON_PACKAGE_VERSION, label.getPackageVersion());
        labelObject.put(JSON_TIMESTAMP, label.getTimestamp());
        labelsArray.put(labelObject);
      }
    }

    root.put(JSON_LABELS_ARRAY, labelsArray);
    return root.toString();
  }

  private File getFilePath() throws IOException {
    File outputDir = context.getExternalCacheDir();
    String fileName =
        String.format(
            "Talkback_custom_labels_%s.tbl", new SimpleDateFormat("MMddyyyy").format(new Date()));
    return new File(outputDir, fileName);
  }

  private void writeToFile(String content, File file) throws IOException {
    Writer writer = new BufferedWriter(new FileWriter(file));
    writer.write(content);
    writer.close();
  }

  public void importLabels(
      Uri contentUri, boolean overrideExistingLabels, OnLabelMigrationCallback callback) {
    try {
      String text = readText(contentUri);
      if (text == null) {
        return;
      }

      List<Label> labels = parseLabels(text);
      if (labels.size() == 0) {
        return;
      }

      manager.importLabels(labels, overrideExistingLabels, callback);
    } catch (Exception e) {
      notifyFailure(callback);
      LogUtils.e(TAG, "failed to import labels");
    }
  }

  private String readText(Uri contentUri) throws IOException {
    StringBuilder text = new StringBuilder();
    InputStream is = context.getContentResolver().openInputStream(contentUri);
    BufferedReader br = new BufferedReader(new InputStreamReader(is));
    String line;

    while ((line = br.readLine()) != null) {
      text.append(line);
      text.append('\n');
    }
    br.close();
    return text.toString();
  }

  // public visibility for tests
  public @NonNull List<Label> parseLabels(String jsonText) throws JSONException {
    JSONObject root = new JSONObject(jsonText);
    JSONArray labelsArray = root.getJSONArray(JSON_LABELS_ARRAY);
    if (labelsArray == null) {
      return Collections.emptyList();
    }

    int count = labelsArray.length();
    List<Label> result = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      JSONObject labelObject = labelsArray.getJSONObject(i);
      String packageName = labelObject.getString(JSON_LABEL_PACKAGE_NAME);
      if (TextUtils.isEmpty(packageName)) {
        continue;
      }
      String packageSignature = labelObject.getString(JSON_LABEL_PACKAGE_SIGNATURE);
      String viewName = labelObject.getString(JSON_LABEL_VIEW_NAME);
      if (TextUtils.isEmpty(viewName)) {
        continue;
      }
      String labelText = labelObject.getString(JSON_LABEL_TEXT);
      if (TextUtils.isEmpty(labelText)) {
        continue;
      }
      String locale = labelObject.getString(JSON_LABEL_LOCALE);
      int packageVersion = labelObject.getInt(JSON_PACKAGE_VERSION);
      long timestamp = labelObject.getLong(JSON_TIMESTAMP);
      Label label =
          new Label(
              packageName,
              packageSignature,
              viewName,
              labelText,
              locale,
              packageVersion,
              "",
              timestamp);
      result.add(label);
    }

    return result;
  }
}
