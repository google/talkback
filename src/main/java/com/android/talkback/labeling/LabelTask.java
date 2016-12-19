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

import android.os.AsyncTask;

public class LabelTask<Result> extends AsyncTask<Void, Void, Result> {

    public interface TrackedTaskCallback {
        public void onTaskPreExecute(LabelClientRequest request);
        public void onTaskPostExecute(LabelClientRequest request);
    }

    private LabelClientRequest<Result> mRequest;
    private TrackedTaskCallback mCallback;

    public LabelTask(LabelClientRequest<Result> request, TrackedTaskCallback callback) {
        mCallback = callback;
        mRequest = request;
    }

    @Override
    protected void onPreExecute() {
        if (mCallback != null) {
            mCallback.onTaskPreExecute(mRequest);
        }
        super.onPreExecute();
    }

    /**
     * See {@link AsyncTask#onPostExecute}.
     * <p>
     * If overridden in a child class, this method should be invoked after
     * any processing by the child is complete. Failing to do so, or doing
     * so out of order may result in failure to release or premature release
     * of resources.
     */
    @Override
    protected void onPostExecute(Result result) {
        mRequest.onPostExecute(result);
        if (mCallback != null) {
            mCallback.onTaskPostExecute(mRequest);
        }
        super.onPostExecute(result);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Result doInBackground(Void... params) {
        return mRequest.doInBackground();
    }
}
