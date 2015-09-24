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

import java.util.List;

public class AllLabelsFetchRequest {

    private final OnAllLabelsFetchedListener mListener;

    public AllLabelsFetchRequest(OnAllLabelsFetchedListener onAllLabelsFetchedListener) {
        mListener = onAllLabelsFetchedListener;
    }

    void invokeCallback(List<Label> result) {
        if (mListener != null) {
            mListener.onAllLabelsFetched(result);
        }
    }

    public interface OnAllLabelsFetchedListener {
        void onAllLabelsFetched(List<Label> results);
    }
}
