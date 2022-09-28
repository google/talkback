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

import android.app.Activity;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.google.android.accessibility.talkback.BuildConfig;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.BasePreferencesActivity;
import com.google.android.accessibility.utils.LocaleUtils;
import com.google.android.accessibility.utils.labeling.LabelProviderClient;
import com.google.android.accessibility.utils.labeling.PackageLabelInfo;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.io.File;
import java.util.List;

/** An activity for displaying a summary of custom labels in TalkBack. */
public class LabelManagerSummaryActivity extends BasePreferencesActivity
    implements OnClickListener {

  private static final String TAG = "LabelManagerSummaryAct";

  protected static final int SELECT_LABEL_FILE_REQUEST = 0;

  /** File provider for custom label share intent. */
  private static final String FILE_AUTHORITY =
      BuildConfig.LIBRARY_PACKAGE_NAME + ".providers.FileProvider";

  private RecyclerView packageList;
  private PackageLabelInfoAdapter packageLabelInfoAdapter;
  private CustomLabelManager labelManager;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    super.setContentView(R.layout.label_manager_packages);

    prepareActionBar(/* icon= */ null);

    packageList = (RecyclerView) findViewById(R.id.package_list);

    labelManager = new CustomLabelManager(this);

    packageList.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
    packageLabelInfoAdapter = new PackageLabelInfoAdapter(this, this, labelManager);
    packageList.setAdapter(packageLabelInfoAdapter);
    packageList.setVisibility(View.VISIBLE);
  }

  @Override
  protected void onResume() {
    super.onResume();
    updatePackageSummary();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
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
        () -> {
          if (isDestroyed()) {
            return;
          }

          updatePackageSummary();
          Toast.makeText(
                  getApplicationContext(), R.string.imported_labels_reverted, Toast.LENGTH_SHORT)
              .show();
        });
  }

  /** Fetches an updated package summary from the content provider and updates the adapter. */
  private void updatePackageSummary() {
    new UpdatePackageSummaryTask(getApplicationContext(), packageLabelInfoAdapter).execute();
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

  /**
   * An adapter that processes information about UI(buttons and message) and packages and their
   * labels.
   */
  private static class PackageLabelInfoAdapter
      extends RecyclerView.Adapter<PackageLabelInfoAdapter.PackageLabelViewHolder> {
    // The type of item is button.
    private static final int TYPE_BUTTON = 0;
    // The type of item is label.
    private static final int TYPE_LABEL = 1;
    // The type of item is message TextView.
    private static final int TYPE_MESSAGE = 2;

    private final Activity activity;
    private final LayoutInflater layoutInflater;
    private List<PackageLabelInfo> items;
    private OnClickListener onClickListener;
    private CustomLabelManager labelManager;
    private Button revertButton;

    PackageLabelInfoAdapter(
        Activity activity, OnClickListener onClickListener, CustomLabelManager labelManager) {
      this.activity = activity;
      this.onClickListener = onClickListener;
      this.labelManager = labelManager;
      layoutInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getItemCount() {
      // There are only 2 items, button and message, if items have no any label item. Otherwise,
      // itemCount is equal to the size of items plus 1 (button).
      return ((items == null) || (items.isEmpty())) ? 2 : items.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
      if (position == 0) {
        return TYPE_BUTTON;
      }

      // The message will show when there is no label and itemCount is 2.
      if ((items == null) || (items.isEmpty()) && (position == 1)) {
        return TYPE_MESSAGE;
      }
      return TYPE_LABEL;
    }

    @Override
    public PackageLabelViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      View view = null;
      if (viewType == TYPE_BUTTON) {
        view =
            layoutInflater.inflate(
                R.layout.label_manager_buttons, parent, /* attachToRoot= */ false);
        revertButton = view.findViewById(R.id.revert_import);
        revertButton.setOnClickListener(onClickListener);
        checkImportedLabels();

        Button importButton = view.findViewById(R.id.import_labels);
        importButton.setOnClickListener(onClickListener);
        Button exportButton = view.findViewById(R.id.export_labels);
        exportButton.setOnClickListener(onClickListener);
      } else if (viewType == TYPE_MESSAGE) {
        view =
            layoutInflater.inflate(
                R.layout.label_manager_no_package_message, parent, /* attachToRoot= */ false);
      } else if (viewType == TYPE_LABEL) {
        view =
            layoutInflater.inflate(
                R.layout.label_manager_package_row, parent, /* attachToRoot= */ false);
      }

      return new PackageLabelViewHolder(activity.getApplicationContext(), view);
    }

    @Override
    public void onBindViewHolder(PackageLabelViewHolder holder, int position) {
      // Do nothing when item is button (position is 0) or message (no label item).
      int viewType = getItemViewType(position);
      if ((viewType == TYPE_BUTTON) || (viewType == TYPE_MESSAGE)) {
        return;
      }

      // Label items always start from position 1. "No custom labels" message will be shown if there
      // are label items.
      PackageLabelInfo packageLabelInfo = items.get(position - 1);
      holder.setLabelItemView(packageLabelInfo);
    }

    void setLabelItemList(List<PackageLabelInfo> items) {
      this.items = items;
      checkImportedLabels();
    }

    private void checkImportedLabels() {
      if (revertButton == null) {
        return;
      }
      revertButton.setEnabled(false);
      labelManager.hasImportedLabels(
          (hasImportedLabels) -> {
            if (activity.isDestroyed()) {
              return;
            }
            revertButton.setEnabled(hasImportedLabels);
          });
    }

    private static class PackageLabelViewHolder extends RecyclerView.ViewHolder {
      private final Context context;
      private final View view;

      PackageLabelViewHolder(Context context, View view) {
        super(view);
        this.context = context;
        this.view = view;
      }

      /**
       * Sets the info of package label to the view of the item in the view of the package list.
       *
       * @param packageLabelInfo The {@link PackageLabelInfo} which shows on the view of label item.
       */
      void setLabelItemView(PackageLabelInfo packageLabelInfo) {
        final TextView textView = (TextView) view.findViewById(R.id.package_label_info_text);
        final TextView countView = (TextView) view.findViewById(R.id.package_label_info_count);
        final ImageView iconImage = (ImageView) view.findViewById(R.id.icon_image);

        final PackageManager packageManager = context.getPackageManager();
        final String packageName = packageLabelInfo.getPackageName();
        CharSequence applicationLabel = null;
        Drawable applicationIcon = null;

        try {
          final PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
          applicationLabel = packageManager.getApplicationLabel(packageInfo.applicationInfo);
          applicationIcon = packageManager.getApplicationIcon(packageName);
        } catch (NameNotFoundException e) {
          LogUtils.i(
              TAG,
              "Could not load package info for package %s.",
              packageLabelInfo.getPackageName());
        } finally {
          if (TextUtils.isEmpty(applicationLabel)) {
            applicationLabel = packageName;
          }

          if (applicationIcon == null) {
            applicationIcon = packageManager.getDefaultActivityIcon();
          }
        }

        textView.setText(applicationLabel);
        countView.setText(Integer.toString(packageLabelInfo.getLabelCount()));
        iconImage.setImageDrawable(applicationIcon);

        final Intent packageActivityIntent = new Intent(context, LabelManagerPackageActivity.class);
        packageActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        packageActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        packageActivityIntent.putExtra(LabelManagerPackageActivity.EXTRA_PACKAGE_NAME, packageName);

        view.setOnClickListener(
            (view) -> {
              context.startActivity(packageActivityIntent);
            });
      }
    }
  }

  /** A task for getting a package summary and updating the adapter. */
  private static class UpdatePackageSummaryTask
      extends AsyncTask<Void, Void, List<PackageLabelInfo>> {
    private String locale;
    private final LabelProviderClient labelProviderClient;
    private PackageLabelInfoAdapter packageLabelInfoAdapter;

    UpdatePackageSummaryTask(Context context, PackageLabelInfoAdapter packageLabelInfoAdapter) {
      this.labelProviderClient = new LabelProviderClient(context, LabelProvider.AUTHORITY);
      this.packageLabelInfoAdapter = packageLabelInfoAdapter;
    }

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
      packageLabelInfoAdapter.setLabelItemList(result);
      packageLabelInfoAdapter.notifyDataSetChanged();
      labelProviderClient.shutdown();
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
