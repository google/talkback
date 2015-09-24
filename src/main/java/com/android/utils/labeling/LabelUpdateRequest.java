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

class LabelUpdateRequest {

    private final Label mLabel;
    private final OnLabelUpdatedListener mListener;

    public LabelUpdateRequest(Label label, OnLabelUpdatedListener listener) {
        mLabel = label;
        mListener = listener;
    }

    public Label getLabel() {
        return mLabel;
    }

    protected void invokeCallback(boolean result) {
        if (mListener != null) {
            mListener.onLabelUpdated(result);
        }
    }

    public interface OnLabelUpdatedListener {
        void onLabelUpdated(boolean result);
    }
}
