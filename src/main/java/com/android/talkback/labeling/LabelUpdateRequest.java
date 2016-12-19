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

import android.util.Log;
import com.android.utils.LogUtils;
import com.android.utils.labeling.CustomLabelManager;
import com.android.utils.labeling.Label;
import com.android.utils.labeling.LabelProviderClient;

public class LabelUpdateRequest extends LabelClientRequest<Boolean> {

    private Label mLabel;
    private final CustomLabelManager.OnLabelsInPackageChangeListener mListener;

    public LabelUpdateRequest(LabelProviderClient client, Label label,
                              CustomLabelManager.OnLabelsInPackageChangeListener listener) {
        super(client);
        mLabel = label;
        mListener = listener;
    }

    @Override
    public Boolean doInBackground() {
        LogUtils.log(this, Log.VERBOSE, "Spawning new LabelUpdateRequest(%d) for label: %s",
                hashCode(), mLabel);

        if (mLabel == null || mLabel.getId() == Label.NO_ID) {
            return false;
        }

        boolean result = mClient.updateLabel(mLabel, CustomLabelManager.SOURCE_TYPE_USER);
        if (result) {
            // remove copy of the label from import backup
            mClient.deleteLabel(mLabel.getPackageName(), mLabel.getViewName(), mLabel.getLocale(),
                    mLabel.getPackageVersion(), CustomLabelManager.SOURCE_TYPE_BACKUP);
        }

        return result;
    }

    @Override
    public void onPostExecute(Boolean result) {
        LogUtils.log(this, Log.VERBOSE, "LabelUpdateRequest(%d) complete. Result: %s",
                hashCode(), result);

        if (mListener != null && result) {
            mListener.onLabelsInPackageChanged(mLabel.getPackageName());
        }
    }
}
