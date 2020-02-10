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
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.accessibility.talkback.BuildConfig;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.LocaleUtils;
import com.google.android.accessibility.utils.labeling.LabelProviderClient;
import com.google.android.accessibility.utils.labeling.PackageLabelInfo;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.io.File;
import java.util.List;

/** An activity for displaying a summary of custom labels in TalkBack. */
public class LabelManagerSummaryActivity extends AppCompatActivity implements OnClickListener {

  private static final String TAG = "LabelManagerSummaryAct";

  protected static final int SELECT_LABEL_FILE_REQUEST = 0;

  /** File provider for custom label share intent. */
  private static final String FILE_AUTHORITY =
      BuildConfig.APPLICATION_ID + ".providers.FileProvider";

  private LabelProviderClient labelProviderClient;
  private ListView packageList;
  private TextView noPackagesMessage;
  private View revertButton;
  private CustomLabelManager labelManager;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.label_manager_packages);

    final ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
    }

    packageList = (ListView) findViewById(R.id.package_list);
    noPackagesMessage = (TextView) findViewById(R.id.no_packages_message);

    labelProviderClient = new LabelProviderClient(this, LabelProvider.AUTHORITY);
    labelManager = new CustomLabelManager(this);

    initializeImportLabelButton();
    initializeExportLabelButton();
    initializeRevertImportButton();
  }

  @Override
  protected void onResume() {
    super.onResume();
    checkImportedLabels();
    updatePackageSummary();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    labelProviderClient.shutdown();
    labelManager.shutdown();
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

  @Override
  public void onClick(View view) {
    final int viewId = view.getId();
    if (viewId == R.id.import_labels) {
      onImportLabelButtonClicked();
    } else if (viewId == R.id.export_labels) {
      onExportLabelButtonClicked();
    } else if (viewId == R.id.revert_import) {
      onRevertImportButtonClicked();
    }
  }

  private void onImportLabelButtonClicked() {
    final Intent importIntent = new Intent();
    importIntent.setAction(Intent.ACTION_GET_CONTENT);
    importIntent.setType("application/json");
    importIntent.addCategory(Intent.CATEGORY_OPENABLE);

    final PackageManager manager = getApplicationContext().getPackageManager();
    final List<ResolveInfo> info = manager.queryIntentActivities(importIntent, 0);
    if (info.size() == 0) {
      Toast.makeText(getApplicationContext(), R.string.no_apps_to_import_labels, Toast.LENGTH_SHORT)
          .show();
    } else {
      String activityTitle = getResources().getString(R.string.label_choose_app_to_import);
      startActivityForResult(
          Intent.createChooser(importIntent, activityTitle), SELECT_LABEL_FILE_REQUEST);
    }
  }

  private void onExportLabelButtonClicked() {
    new CustomLabelMigrationManager(getApplicationContext()).exportLabels(exportLabelsCallBack);
  }

  private void onRevertImportButtonClicked() {
    labelManager.revertImportedLabels(
        new RevertImportedLabelsRequest.OnImportLabelsRevertedListener() {
          @Override
          public void onImportLabelsReverted() {
            if (isDestroyed()) {
              return;
            }

            checkImportedLabels();
            updatePackageSummary();
            Toast.makeText(
                    getApplicationContext(), R.string.imported_labels_reverted, Toast.LENGTH_SHORT)
                .show();
          }
        });
  }

  private void initializeImportLabelButton() {
    final Button importLabel = (Button) findViewById(R.id.import_labels);
    importLabel.setOnClickListener(this);
  }

  private void initializeExportLabelButton() {
    final Button exportLabel = (Button) findViewById(R.id.export_labels);
    exportLabel.setOnClickListener(this);
  }

  private void initializeRevertImportButton() {
    revertButton = findViewById(R.id.revert_import);
    revertButton.setOnClickListener(this);
  }

  private void checkImportedLabels() {
    revertButton.setEnabled(false);
    labelManager.hasImportedLabels(
        new HasImportedLabelsRequest.OnHasImportedLabelsCompleteListener() {
          @Override
          public void onHasImportedRequestCompleted(boolean hasImportedLabels) {
            if (isDestroyed()) {
              return;
            }
            revertButton.setEnabled(hasImportedLabels);
          }
        });
  }

  /** Fetches an updated package summary from the content provider and updates the adapter. */
  private void updatePackageSummary() {
    new UpdatePackageSummaryTask().execute();
  }

  private final CustomLabelMigrationManager.SimpleLabelMigrationCallback exportLabelsCallBack =
      new CustomLabelMigrationManager.SimpleLabelMigrationCallback() {
        @Override
        public void onLabelsExported(File file) {
          if (file == null) {
            notifyLabelExportFailure();
            return;
          }

          Uri uri;
          try {
            uri = FileProvider.getUriForFile(getApplicationContext(), FILE_AUTHORITY, file);
          } catch (IllegalArgumentException exception) {
            notifyLabelExportFailure();
            return;
          }
          Intent shareIntent = new Intent();
          shareIntent.setAction(Intent.ACTION_SEND);
          shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
          shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
          shareIntent.setType("application/json");

          final PackageManager manager = getApplicationContext().getPackageManager();
          final List<ResolveInfo> info = manager.queryIntentActivities(shareIntent, 0);
          if (info.size() == 0) {
            Toast.makeText(
                    getApplicationContext(), R.string.no_apps_to_export_labels, Toast.LENGTH_SHORT)
                .show();
          } else {
            String activityTitle = getResources().getString(R.string.label_choose_app_to_export);
            startActivity(Intent.createChooser(shareIntent, activityTitle));
          }
        }

        @Override
        public void onFail() {
          notifyLabelExportFailure();
        }
      };

  /** An adapter that processes information about packages and their labels. */
  private class PackageLabelInfoAdapter extends ArrayAdapter<PackageLabelInfo> {
    private final LayoutInflater layoutInflater;

    public PackageLabelInfoAdapter(
        Context context, int textViewResourceId, List<PackageLabelInfo> items) {
      super(context, textViewResourceId, items);

      layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
      if (view == null) {
        view = layoutInflater.inflate(R.layout.label_manager_package_row, parent, false);
      }

      final PackageLabelInfo packageLabelInfo = getItem(position);
      if (packageLabelInfo == null) {
        return view;
      }

      final PackageManager packageManager = getPackageManager();
      final String packageName = packageLabelInfo.getPackageName();
      CharSequence applicationLabel = null;
      Drawable applicationIcon = null;

      try {
        final PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
        applicationLabel = packageManager.getApplicationLabel(packageInfo.applicationInfo);
        applicationIcon = packageManager.getApplicationIcon(packageName);
      } catch (NameNotFoundException e) {
        LogUtils.i(
            TAG, "Could not load package info for package %s.", packageLabelInfo.getPackageName());
      } finally {
        if (TextUtils.isEmpty(applicationLabel)) {
          applicationLabel = packageName;
        }

        if (applicationIcon == null) {
          applicationIcon = packageManager.getDefaultActivityIcon();
        }
      }

      final TextView textView = (TextView) view.findViewById(R.id.package_label_info_text);
      textView.setText(applicationLabel);

      final TextView countView = (TextView) view.findViewById(R.id.package_label_info_count);
      countView.setText(Integer.toString(packageLabelInfo.getLabelCount()));

      final ImageView iconImage = (ImageView) view.findViewById(R.id.icon_image);
      iconImage.setImageDrawable(applicationIcon);

      final Intent packageActivityIntent =
          new Intent(LabelManagerSummaryActivity.this, LabelManagerPackageActivity.class);
      packageActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      packageActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      packageActivityIntent.putExtra(LabelManagerPackageActivity.EXTRA_PACKAGE_NAME, packageName);

      view.setOnClickListener(
          new OnClickListener() {
            @Override
            public void onClick(View view) {
              startActivity(packageActivityIntent);
            }
          });

      return view;
    }
  }

  /** A task for getting a package summary and updating the adapter. */
  private class UpdatePackageSummaryTask extends AsyncTask<Void, Void, List<PackageLabelInfo>> {
    private String locale;

    @Override
    protected void onPreExecute() {
      locale = LocaleUtils.getDefaultLocale();
    }

    @Override
    protected List<PackageLabelInfo> doInBackground(Void... params) {
      LogUtils.v(TAG, "Spawning new UpdatePackageSummaryTask(%d) for %s.", hashCode(), locale);

      return labelProviderClient.getPackageSummary(locale);
    }

    @Override
    protected void onPostExecute(List<PackageLabelInfo> result) {
      if (result != null && result.size() > 0) {
        packageList.setAdapter(
            new PackageLabelInfoAdapter(
                LabelManagerSummaryActivity.this, R.layout.label_manager_package_row, result));
        packageList.setVisibility(View.VISIBLE);
        noPackagesMessage.setVisibility(View.GONE);
      } else {
        packageList.setVisibility(View.GONE);
        noPackagesMessage.setVisibility(View.VISIBLE);
      }
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == SELECT_LABEL_FILE_REQUEST && data != null) {
      Uri selectedFile = data.getData();
      Intent launchImportIntent = new Intent(this, LabelImportActivity.class);
      launchImportIntent.setData(selectedFile);
      startActivity(launchImportIntent);
    }
  }

  private void notifyLabelExportFailure() {
    Toast.makeText(getApplicationContext(), R.string.label_export_failed, Toast.LENGTH_SHORT)
        .show();
  }
}
