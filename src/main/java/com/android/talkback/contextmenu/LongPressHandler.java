/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.talkback.contextmenu;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Detects long presses.
 */
class LongPressHandler extends Handler implements View.OnTouchListener {
    private static final long LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private static final int MSG_LONG_PRESS = 1;

    private final int TOUCH_SLOP_SQUARED;

    private LongPressListener mListener;
    private MotionEvent mPreviousEvent;

    private float mMoved;

    /**
     * Creates a new long press handler.
     *
     * @param context The parent context.
     */
    public LongPressHandler(Context context) {
        super(context.getMainLooper());

        final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        TOUCH_SLOP_SQUARED = touchSlop * touchSlop;
    }

    /**
     * Sets the listener that receives long press callbacks.
     *
     * @param listener The listener to set.
     */
    public void setListener(LongPressListener listener) {
        mListener = listener;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_HOVER_ENTER:
                mMoved = 0;
                //$FALL-THROUGH$
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_HOVER_MOVE:
                if (mPreviousEvent != null) {
                    final float dX = event.getX() - mPreviousEvent.getX();
                    final float dY = event.getY() - mPreviousEvent.getY();
                    final float moved = (dX * dX) + (dY * dY);

                    mMoved += moved;
                }

                if (mMoved > TOUCH_SLOP_SQUARED) {
                    mMoved = 0;

                    removeMessages(MSG_LONG_PRESS);

                    final Message msg = obtainMessage(MSG_LONG_PRESS, mPreviousEvent);
                    sendMessageDelayed(msg, LONG_PRESS_TIMEOUT);
                }

                mPreviousEvent = MotionEvent.obtain(event);

                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_HOVER_EXIT:
                removeMessages(MSG_LONG_PRESS);

                if (mPreviousEvent != null) {
                    mPreviousEvent.recycle();
                    mPreviousEvent = null;
                }

                break;
        }

        return false;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_LONG_PRESS:
                if (mListener != null) {
                    mListener.onLongPress();
                }
                break;
        }
    }

    /**
     * Handles long press callbacks.
     */
    public interface LongPressListener {
        /**
         * Called when a long press is detected.
         */
        public void onLongPress();
    }
}
