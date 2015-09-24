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
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.ListView;

/**
 * Extends {@link ListView} by adding an accessibility action listener.
 */
@TargetApi(16)
public class InstrumentedListView extends ListView {
    private final Handler mHandler = new Handler();

    private ListViewListener mListener;

    public InstrumentedListView(Context context) {
        super(context);
    }

    public InstrumentedListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public InstrumentedListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setInstrumentation(ListViewListener listener) {
        mListener = listener;
    }

    @Override
    public boolean performAccessibilityAction(final int action, Bundle arguments) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) {
                    mListener.onPerformAccessibilityAction(action);
                }
            }
        });

        return super.performAccessibilityAction(action, arguments);
    }

    public interface ListViewListener {
        public void onPerformAccessibilityAction(int action);
    }
}
