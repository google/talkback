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
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.talkback.R;
import com.android.utils.LogUtils;
import com.android.utils.labeling.LabelProviderClient;
import com.android.utils.labeling.PackageLabelInfo;

import java.util.List;
import java.util.Locale;

/**
 * An activity for displaying a summary of custom labels in TalkBack.
 */
public class LabelManagerSummaryActivity extends Activity {
    /** Labeling requires new accessibility APIs in JellyBean MR2 (API 18). */
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN_MR2;

    private LabelProviderClient mLabelProviderClient;
    private ListView mPackageList;
    private TextView mNoPackagesMessage;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.label_manager_packages);

        getActionBar().setDisplayHomeAsUpEnabled(true);

        mPackageList = (ListView) findViewById(R.id.package_list);
        mNoPackagesMessage = (TextView) findViewById(R.id.no_packages_message);
        mLabelProviderClient = new LabelProviderClient(this, LabelProvider.AUTHORITY);
    }

    @Override
    protected void onResume() {
        super.onResume();

        updatePackageSummary();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mLabelProviderClient.shutdown();
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
                view = mLayoutInflater.inflate(R.layout.label_manager_package_row, null);
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
            mLocale = Locale.getDefault().toString();
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
}
