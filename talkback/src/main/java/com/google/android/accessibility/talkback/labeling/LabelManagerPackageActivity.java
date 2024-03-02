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


import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.LocaleUtils;
import com.google.android.accessibility.utils.labeling.Label;
import com.google.android.accessibility.utils.labeling.LabelProviderClient;
import com.google.android.accessibility.utils.preference.BasePreferencesActivity;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** An activity for managing custom labels in TalkBack for a specific package. */
public class LabelManagerPackageActivity extends BasePreferencesActivity {

  private static final String TAG = "LabelManagerPackageAct";

  public static final String EXTRA_PACKAGE_NAME = "packageName";

  private LabelProviderClient labelProviderClient;
  private String packageName;
  private ListView labelList;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    super.setContentView(R.layout.label_manager_labels);

    final Intent intent = getIntent();
    if (!intent.hasExtra(EXTRA_PACKAGE_NAME)) {
      throw new IllegalArgumentException("Intent missing package name extra.");
    }

    packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);

    final PackageManager packageManager = getPackageManager();
    CharSequence applicationLabel;
    Drawable packageIcon;
    try {
      packageIcon = packageManager.getApplicationIcon(packageName);
      final PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
      applicationLabel = packageManager.getApplicationLabel(packageInfo.applicationInfo);
    } catch (NameNotFoundException e) {
      LogUtils.i(TAG, "Could not load package info for package %s.", packageName);

      packageIcon = packageManager.getDefaultActivityIcon();
      applicationLabel = packageName;
    }

    setTitle(getString(R.string.label_manager_package_title, applicationLabel));

    prepareActionBar(packageIcon);

    labelList = (ListView) findViewById(R.id.label_list);
    labelProviderClient = new LabelProviderClient(this, LabelProvider.AUTHORITY);
  }

  @Override
  protected void onResume() {
    super.onResume();

    new UpdateLabelsTask().execute();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();

    labelProviderClient.shutdown();
  }

  /** Finishes the activity when the up button is pressed on the action bar. */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  /** An adapter that processes information about labels for a given package. */
  private class LabelAdapter extends ArrayAdapter<Label> {
    private final LayoutInflater layoutInflater;

    public LabelAdapter(Context context, int textViewResourceId, List<Label> items) {
      super(context, textViewResourceId, items);

      layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
      if (view == null) {
        view = layoutInflater.inflate(R.layout.label_manager_label_row, parent, false);
      }

      final Label label = getItem(position);
      if (label == null) {
        return view;
      }

      final TextView textView = (TextView) view.findViewById(R.id.label_text);
      textView.setText(label.getText());

      final TextView timestampView = (TextView) view.findViewById(R.id.label_timestamp);
      final DateFormat dateFormat = new SimpleDateFormat();
      final Date date = new Date(label.getTimestamp());
      final String timestampMessage =
          getString(R.string.label_manager_timestamp_text, dateFormat.format(date));
      timestampView.setText(timestampMessage);

      view.setOnClickListener(
          itemView -> {
            final Context context = LabelManagerPackageActivity.this;
            boolean unused =
                LabelDialogManager.editLabel(
                    context,
                    label.getId(),
                    /* needToRestoreFocus= */ false,
                    /* pipeline= */ null,
                    () -> {
                      new UpdateLabelsTask().execute();
                    });

            // TODO: Also add intent for deleting the label.
          });
      return view;
    }
  }

  /**
   * A task for getting labels for the package displayed in the current activity and updating the
   * adapter with those labels.
   */
  private class UpdateLabelsTask extends AsyncTask<Void, Void, List<Label>> {
    private String locale;

    @Override
    protected void onPreExecute() {
      locale = LocaleUtils.getDefaultLocale();
    }

    @Override
    protected List<Label> doInBackground(Void... params) {
      LogUtils.v(
          TAG, "Spawning new UpdateLabelsTask(%d) for (%s, %s).", hashCode(), packageName, locale);

      final Map<String, Label> labelsMap =
          labelProviderClient.getLabelsForPackage(packageName, locale);
      return new ArrayList<>(labelsMap.values());
    }

    @Override
    protected void onPostExecute(List<Label> result) {
      LabelAdapter labelAdapter = null;
      if (labelList.getAdapter() instanceof LabelAdapter) {
        labelAdapter = (LabelAdapter) labelList.getAdapter();
      }

      if (labelAdapter == null) {
        labelList.setAdapter(
            new LabelAdapter(
                LabelManagerPackageActivity.this, R.layout.label_manager_label_row, result));
      } else {
        labelAdapter.clear();
        labelAdapter.addAll(result);
        labelAdapter.notifyDataSetChanged();
      }
    }
  }
}
