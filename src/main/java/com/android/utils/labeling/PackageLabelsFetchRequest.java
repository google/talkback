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

package com.android.utils.labeling;

import java.util.Map;

class PackageLabelsFetchRequest {

    private final String mPackageName;
    private final OnLabelsFetchedListener mListener;

    public PackageLabelsFetchRequest(
            String packageName, OnLabelsFetchedListener onLabelsFetchedListener) {
        mPackageName = packageName;
        mListener = onLabelsFetchedListener;
    }

    public String getPackageName() {
        return mPackageName;
    }

    void invokeCallback(Map<String, Label> result) {
        if (mListener != null) {
            mListener.onLabelsFetched(result);
        }
    }

    public interface OnLabelsFetchedListener {

        /**
         * Invoked by the task performing the fetch request with the results of
         * the lookup.
         *
         * @param results A {@link Map} containing that labels for the requested
         *            package name where the the key is the label's view
         *            resource ID name and the value is the associated
         *            {@link Label}.
         */
        void onLabelsFetched(Map<String, Label> results);
    }
}
