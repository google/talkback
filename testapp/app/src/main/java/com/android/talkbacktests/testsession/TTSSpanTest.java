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
import android.os.Build;
import android.text.style.TtsSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.android.talkbacktests.R;

public class TTSSpanTest extends BaseTestContent {

    public TTSSpanTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_tts_span, container, false);
        ListView list = (ListView) view.findViewById(R.id.test_tts_span_list);

        TtsSpan[] ttsSpans = initTtsSpans();
        String[] spanTypes = container.getResources()
                .getStringArray(R.array.test_tts_span_type_array);
        String[] texts = context.getResources().getStringArray(R.array.test_tts_span_text_array);

        TTSSpanTestAdapter adapter = new TTSSpanTestAdapter(context,
                ttsSpans, spanTypes, texts);

        list.setAdapter(adapter);

        return view;
    }

    private TtsSpan[] initTtsSpans() {
        TtsSpan[] ttsSpans = new TtsSpan[7];
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ttsSpans[0] = new TtsSpan.CardinalBuilder("1234").build();
            ttsSpans[1] = new TtsSpan.DateBuilder().setYear(1998).setMonth(9).setDay(4).build();
            ttsSpans[2] = new TtsSpan.DecimalBuilder().setArgumentsFromDouble(Math.PI, 0, 5)
                    .build();
            ttsSpans[3] = new TtsSpan.DigitsBuilder().setDigits("2048").build();
            ttsSpans[4] = new TtsSpan.MoneyBuilder().setCurrency("USD").setIntegerPart(3057000)
                    .build();
            ttsSpans[5] = new TtsSpan.OrdinalBuilder().setNumber(21).build();
            ttsSpans[6] = new TtsSpan.TextBuilder().setText("Spoken Text").build();
        }
        //TODO add more TtsSpans

        return ttsSpans;
    }
}