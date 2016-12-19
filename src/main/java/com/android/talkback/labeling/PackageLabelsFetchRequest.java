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

package com.android.talkback.labeling;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import com.android.utils.LogUtils;
import com.android.utils.labeling.CustomLabelManager;
import com.android.utils.labeling.Label;
import com.android.utils.labeling.LabelProviderClient;

import java.util.Map;

public class PackageLabelsFetchRequest extends LabelClientRequest<Map<String, Label>> {

    private final Context mContext;
    private final String mPackageName;
    private final OnLabelsFetchedListener mOnLabelFetchedListener;

    public PackageLabelsFetchRequest(LabelProviderClient client, Context context,
                                     String packageName,
                                     OnLabelsFetchedListener listener) {
        super(client);
        mContext = context;
        mPackageName = packageName;
        mOnLabelFetchedListener = listener;
    }

    @Override
    public Map<String, Label> doInBackground() {
        int versionCode = Integer.MAX_VALUE;
        try {
            final PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(
                    mPackageName, 0);
            versionCode = packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            LogUtils.log(this, Log.WARN,
                    "Unable to resolve package info during prefetch for %s", mPackageName);
        }

        return mClient.getLabelsForPackage(mPackageName, CustomLabelManager.getDefaultLocale(),
                versionCode);
    }

    @Override
    public void onPostExecute(Map<String, Label> result) {
        if (mOnLabelFetchedListener != null) {
            mOnLabelFetchedListener.onLabelsFetched(result);
        }
    }

    public interface OnLabelsFetchedListener {

        /**
         * Invoked by the task performing the fetch request with the results of
         * the lookup.
         *
         * @param results A {@link java.util.Map} containing that labels for the requested
         *            package name where the the key is the label's view
         *            resource ID name and the value is the associated
         *            {@link Label}.
         */
        void onLabelsFetched(Map<String, Label> results);
    }
}
