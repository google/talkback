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

import android.app.ActionBar;
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
import com.android.utils.labeling.CustomLabelManager;
import com.android.utils.labeling.Label;
import com.android.utils.labeling.LabelProviderClient;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * An activity for managing custom labels in TalkBack for a specific package.
 */
public class LabelManagerPackageActivity extends Activity {
    /** Labeling requires new accessibility APIs in JellyBean MR2 (API 18). */
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN_MR2;

    public static final String EXTRA_PACKAGE_NAME = "packageName";

    private LabelProviderClient mLabelProviderClient;
    private String mPackageName;
    private ListView mLabelList;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.label_manager_labels);

        final Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_PACKAGE_NAME)) {
            throw new IllegalArgumentException("Intent missing package name extra.");
        }

        mPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);

        final PackageManager packageManager = getPackageManager();
        CharSequence applicationLabel;
        Drawable packageIcon;
        try {
            packageIcon = packageManager.getApplicationIcon(mPackageName);
            final PackageInfo packageInfo = packageManager.getPackageInfo(mPackageName, 0);
            applicationLabel = packageManager.getApplicationLabel(packageInfo.applicationInfo);
        } catch (NameNotFoundException e) {
            LogUtils.log(this, Log.INFO, "Could not load package info for package %s.",
                    mPackageName);

            packageIcon = packageManager.getDefaultActivityIcon();
            applicationLabel = mPackageName;
        }

        setTitle(getString(R.string.label_manager_package_title, applicationLabel));

        final ActionBar actionBar = getActionBar();
        actionBar.setIcon(packageIcon);
        actionBar.setDisplayHomeAsUpEnabled(true);

        mLabelList = (ListView) findViewById(R.id.label_list);
        mLabelProviderClient = new LabelProviderClient(this, LabelProvider.AUTHORITY);
    }

    @Override
    protected void onResume() {
        super.onResume();

        new UpdateLabelsTask().execute();
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
     * An adapter that processes information about labels for a given package.
     */
    private class LabelAdapter extends ArrayAdapter<Label> {
        private final LayoutInflater mLayoutInflater;

        public LabelAdapter(Context context, int textViewResourceId, List<Label> items) {
            super(context, textViewResourceId, items);

            mLayoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @SuppressWarnings("unchecked")
        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                view = mLayoutInflater.inflate(R.layout.label_manager_label_row, parent, false);
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
            final String timestampMessage
                    = getString(R.string.label_manager_timestamp_text, dateFormat.format(date));
            timestampView.setText(timestampMessage);

            final ImageView iconImage = (ImageView) view.findViewById(R.id.icon_image);
            new LoadScreenshotTask(label, iconImage).execute();

            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    final Context context = LabelManagerPackageActivity.this;
                    LabelDialogManager.editLabel(context, label, false /* overlay */);

                    // TODO: Also add intent for deleting the label.
                }
            });

            return view;
        }
    }

    /**
     * A task for getting labels for the package displayed in the current
     * activity and updating the adapter with those labels.
     */
    private class UpdateLabelsTask extends AsyncTask<Void, Void, List<Label>> {
        private String mLocale;

        @Override
        protected void onPreExecute() {
            mLocale = CustomLabelManager.getDefaultLocale();
        }

        @Override
        protected List<Label> doInBackground(Void... params) {
            LogUtils.log(this, Log.VERBOSE, "Spawning new UpdateLabelsTask(%d) for (%s, %s).",
                    hashCode(), mPackageName, mLocale);

            final Map<String, Label> labelsMap
                    = mLabelProviderClient.getLabelsForPackage(mPackageName, mLocale);
            return new ArrayList<>(labelsMap.values());
        }

        @Override
        protected void onPostExecute(List<Label> result) {
            mLabelList.setAdapter(new LabelAdapter(LabelManagerPackageActivity.this,
                    R.layout.label_manager_label_row, result));
        }
    }

    /**
     * A task for loading a screenshot from a label into a view.
     */
    private class LoadScreenshotTask extends AsyncTask<Void, Void, Drawable> {
        private Label mLabel;
        private ImageView mImageView;

        /**
         * Constructs a new task for loading a screenshot.
         *
         * @param label The label from which to load the screenshot.
         * @param imageView The view into which to load the screenshot.
         */
        public LoadScreenshotTask(Label label, ImageView imageView) {
            mLabel = label;
            mImageView = imageView;
        }

        @Override
        protected Drawable doInBackground(Void... params) {
            final String screenshotPath = mLabel.getScreenshotPath();

            LogUtils.log(this, Log.VERBOSE, "Spawning new LoadScreenshotTask(%d) for %s.",
                    hashCode(), screenshotPath);

            return Drawable.createFromPath(screenshotPath);
        }

        @Override
        protected void onPostExecute(Drawable result) {
            mImageView.setImageDrawable(result);
            mImageView.invalidate();
        }
    }
}
