/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.talkback.tutorial;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Loads application labels and icons.
 */
@TargetApi(16)
class AppsAdapter extends ArrayAdapter<ResolveInfo> {
    private final PackageManager mPackageManager;
    private final int mTextViewResourceId;
    private final int mIconSize;

    public AppsAdapter(Context context, int resource, int textViewResourceId) {
        super(context, resource, textViewResourceId);

        mPackageManager = context.getPackageManager();
        mTextViewResourceId = textViewResourceId;
        mIconSize = context.getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);

        loadAllApps();
    }

    public CharSequence getLabel(int position) {
        final ResolveInfo appInfo = getItem(position);
        return appInfo.loadLabel(mPackageManager);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final View view = super.getView(position, convertView, parent);
        final ResolveInfo appInfo = getItem(position);
        final CharSequence label = appInfo.loadLabel(mPackageManager);
        final Drawable icon = appInfo.loadIcon(mPackageManager);
        final TextView text = (TextView) view.findViewById(mTextViewResourceId);

        text.setTag(position);
        icon.setBounds(0, 0, mIconSize, mIconSize);

        populateView(text, label, icon);

        return view;
    }

    /**
     * Populates the supplied {@link TextView} with the label and icon. Override
     * this method to customize icon placement.
     *
     * @param text The text view to populate.
     * @param label The label for the current app.
     * @param icon The icon for the current app.
     */
    void populateView(TextView text, CharSequence label, Drawable icon) {
        text.setText(label);
        text.setCompoundDrawables(null, icon, null, null);
    }

    private void loadAllApps() {
        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        new AsyncLoadApps().execute(mainIntent);
    }

    private class AsyncLoadApps extends AsyncTask<Intent, Void, List<ResolveInfo>> {
        @Override
        protected List<ResolveInfo> doInBackground(Intent... params) {
            final ArrayList<ResolveInfo> result = new ArrayList<>();

            for (Intent param : params) {
                result.addAll(mPackageManager.queryIntentActivities(param, 0));
            }

            sortAppsList(result);
            return result;
        }

        private void sortAppsList(List<ResolveInfo> apps) {
            if (apps == null) {
                return;
            }

            Collections.sort(apps, new Comparator<ResolveInfo>() {
                @Override
                public int compare(ResolveInfo lhs, ResolveInfo rhs) {
                    if (lhs == null || rhs == null) {
                        return compareIfNullObject(lhs, rhs);
                    }

                    CharSequence rLabel = rhs.loadLabel(mPackageManager);
                    CharSequence lLabel = lhs.loadLabel(mPackageManager);
                    if (lLabel == null || rLabel == null) {
                        return compareIfNullObject(lLabel, rLabel);
                    }

                    return lLabel.toString().toLowerCase()
                            .compareTo(rLabel.toString().toLowerCase());
                }

                private int compareIfNullObject(Object lObj, Object rObj) {
                    if (lObj == null && rObj == null) {
                        return 0;
                    }

                    return lObj == null ? -1: 1;
                }
            });
        }

        @Override
        protected void onPostExecute(List<ResolveInfo> result) {
            addAll(result);
        }
    }
}
