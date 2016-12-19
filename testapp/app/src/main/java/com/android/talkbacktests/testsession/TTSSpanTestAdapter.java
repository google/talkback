/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.talkbacktests.testsession;

import android.content.Context;
import android.text.SpannableString;
import android.text.style.TtsSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.android.talkbacktests.R;

public class TTSSpanTestAdapter extends BaseAdapter {

    private final Context mContext;
    private final TtsSpan[] mTtsSpans;
    private final String[] mSpanTypes;
    private final String[] mTexts;

    public TTSSpanTestAdapter(Context context, TtsSpan[] ttsSpans,
                              String[] spanTypes, String[] texts) {
        mContext = context;
        mTtsSpans = ttsSpans;
        mSpanTypes = spanTypes;
        mTexts = texts;
    }

    @Override
    public int getCount() {
        return mTtsSpans.length;
    }

    @Override
    public Object getItem(int position) {
        return mTtsSpans[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext)
                    .inflate(R.layout.test_tts_span_item, parent, false);
            ViewHolder holder = new ViewHolder();
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.spannedText = (TextView) convertView.findViewById(R.id.spannedText);
            holder.unspannedText = (TextView) convertView.findViewById(R.id.unspannedText);
            convertView.setTag(holder);
        }

        ViewHolder holder = (ViewHolder) convertView.getTag();
        holder.title.setText(mSpanTypes[position]);
        holder.unspannedText.setText(mTexts[position]);

        SpannableString spannableString = new SpannableString(mTexts[position]);
        if (mTtsSpans[position] != null) {
            spannableString.setSpan(mTtsSpans[position], 0, mTexts[position].length(), 0);
        }
        holder.spannedText.setText(spannableString);

        return convertView;
    }

    private static final class ViewHolder {
        public TextView title;
        public TextView spannedText;
        public TextView unspannedText;
    }
}
