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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.Log;
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

import com.android.talkback.BuildConfig;
import com.android.talkback.R;
import com.android.utils.LogUtils;
import com.android.utils.labeling.CustomLabelManager;
import com.android.utils.labeling.LabelProviderClient;
import com.android.utils.labeling.PackageLabelInfo;

import java.io.File;
import java.util.List;

/**
 * An activity for displaying a summary of custom labels in TalkBack.
 */
public class LabelManagerSummaryActivity extends Activity {
    /** Labeling requires new accessibility APIs in JellyBean MR2 (API 18). */
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN_MR2;

    private static final int SELECT_LABEL_FILE_REQUEST = 0;

    /** File provider for custom label share intent. */
    private static final String FILE_AUTHORITY =
            BuildConfig.APPLICATION_ID + ".providers.FileProvider";

    private LabelProviderClient mLabelProviderClient;
    private ListView mPackageList;
    private TextView mNoPackagesMessage;
    private View mRevertButton;
    private CustomLabelManager mLabelManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.label_manager_packages);

        getActionBar().setDisplayHomeAsUpEnabled(true);

        mPackageList = (ListView) findViewById(R.id.package_list);
        mNoPackagesMessage = (TextView) findViewById(R.id.no_packages_message);
        mRevertButton = findViewById(R.id.revert_import);
        mRevertButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mLabelManager.revertImportedLabels(
                        new RevertImportedLabelsRequest.OnImportLabelsRevertedListener() {
                    @Override
                    public void onImportLabelsReverted() {
                        if (isDestroyed()) {
                            return;
                        }

                        checkImportedLabels();
                        updatePackageSummary();
                        Toast.makeText(getApplicationContext(), R.string.imported_labels_reverted,
                                Toast.LENGTH_SHORT).show();
                   }
                });
            }
        });
        mLabelProviderClient = new LabelProviderClient(this, LabelProvider.AUTHORITY);
        mLabelManager = new CustomLabelManager(this);
        addImportCustomLabelClickListener();
        addExportCustomLabelClickListener();
    }

    private void checkImportedLabels() {
        mRevertButton.setEnabled(false);
        mLabelManager.hasImportedLabels(new HasImportedLabelsRequest.OnHasImportedLabelsCompleteListener() {
            @Override
            public void onHasImportedRequestCompleted(boolean hasImportedLabels) {
                if (isDestroyed()) {
                    return;
                }
                mRevertButton.setEnabled(hasImportedLabels);
            }
        });
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
        mLabelProviderClient.shutdown();
        mLabelManager.shutdown();
    }

    /**
     * Finishes the activity when the up button is pressed on the action bar.
     */
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

    /**
     * Fetches an updated package summary from the content provider and updates
     * the adapter.
     */
    private void updatePackageSummary() {
        new UpdatePackageSummaryTask().execute();
    }

    /**
     * An adapter that processes information about packages and their labels.
     */
    private class PackageLabelInfoAdapter extends ArrayAdapter<PackageLabelInfo> {
        private final LayoutInflater mLayoutInflater;

        public PackageLabelInfoAdapter(Context context, int textViewResourceId,
                List<PackageLabelInfo> items) {
            super(context, textViewResourceId, items);

            mLayoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                view = mLayoutInflater.inflate(R.layout.label_manager_package_row, parent, false);
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
                LogUtils.log(this, Log.INFO, "Could not load package info for package %s.",
                        packageLabelInfo.getPackageName());
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

            final Intent packageActivityIntent = new Intent(LabelManagerSummaryActivity.this,
                    LabelManagerPackageActivity.class);
            packageActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            packageActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            packageActivityIntent.putExtra(LabelManagerPackageActivity.EXTRA_PACKAGE_NAME,
                    packageName);

            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    startActivity(packageActivityIntent);
                }
            });

            return view;
        }
    }

    /**
     * A task for getting a package summary and updating the adapter.
     */
    private class UpdatePackageSummaryTask extends AsyncTask<Void, Void, List<PackageLabelInfo>> {
        private String mLocale;

        @Override
        protected void onPreExecute() {
            mLocale = CustomLabelManager.getDefaultLocale();
        }

        @Override
        protected List<PackageLabelInfo> doInBackground(Void... params) {
            LogUtils.log(this, Log.VERBOSE, "Spawning new UpdatePackageSummaryTask(%d) for %s.",
                    hashCode(), mLocale);

            return mLabelProviderClient.getPackageSummary(mLocale);
        }

        @Override
        protected void onPostExecute(List<PackageLabelInfo> result) {
            if (result != null && result.size() > 0) {
                mPackageList.setAdapter(new PackageLabelInfoAdapter(
                        LabelManagerSummaryActivity.this, R.layout.label_manager_package_row,
                        result));
                mPackageList.setVisibility(View.VISIBLE);
                mNoPackagesMessage.setVisibility(View.GONE);
            } else {
                mPackageList.setVisibility(View.GONE);
                mNoPackagesMessage.setVisibility(View.VISIBLE);
            }
        }
    }

    private void addImportCustomLabelClickListener() {
        final Button importLabel = (Button) findViewById(R.id.import_labels);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            importLabel.setVisibility(View.GONE);
            return;
        }
        importLabel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent importIntent = new Intent();
                importIntent.setAction(Intent.ACTION_GET_CONTENT);
                importIntent.setType("application/json");
                importIntent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(importIntent, SELECT_LABEL_FILE_REQUEST);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SELECT_LABEL_FILE_REQUEST && data != null) {
            Uri selectedFile = data.getData();
            Intent launchImportIntent = new Intent(this, LabelImportActivity.class);
            launchImportIntent.setData(selectedFile);
            startActivity(launchImportIntent);
        }
    }

    private void addExportCustomLabelClickListener() {
        final Button exportLabel = (Button) findViewById(R.id.export_labels);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            exportLabel.setVisibility(View.GONE);
            return;
        }

        exportLabel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                CustomLabelMigrationManager exporter = new CustomLabelMigrationManager(
                        getApplicationContext());
                exporter.exportLabels(
                        new CustomLabelMigrationManager.SimpleLabelMigrationCallback() {
                            @Override
                            public void onLabelsExported(File file) {
                                if (file == null) {
                                    notifyLabelExportFailure();
                                    return;
                                }

                                Uri uri = FileProvider.getUriForFile(getApplicationContext(),
                                        FILE_AUTHORITY, file);
                                Intent shareIntent = new Intent();
                                shareIntent.setAction(Intent.ACTION_SEND);
                                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                shareIntent.setType("application/json");

                                String activityTitle = getResources().getString(
                                        R.string.label_choose_app_to_export);
                                startActivity(Intent.createChooser(shareIntent, activityTitle));
                            }

                            @Override
                            public void onFail() {
                                notifyLabelExportFailure();
                            }
                        });
                return;
            }
        });
    }

    private void notifyLabelExportFailure() {
        Toast.makeText(getApplicationContext(), R.string.label_export_failed, Toast.LENGTH_SHORT)
                .show();
    }
}
