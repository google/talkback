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

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.TimePicker;

import com.android.talkbacktests.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class ValuePickerTest extends BaseTestContent implements View.OnClickListener,
        DatePickerDialog.OnDateSetListener, TimePickerDialog.OnTimeSetListener,
        NumberPicker.OnValueChangeListener {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEEE, MMMM d, yyyy");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("hh:mm a");

    private DatePickerDialog mDatePickerDialog;
    private TimePickerDialog mTimePickerDialog;
    private AlertDialog mNumberPickerDialog;

    private TextView mDateSummary;
    private TextView mTimeSummary;
    private TextView mNumberSummary;

    private Calendar mCalendar;

    public ValuePickerTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        mCalendar = Calendar.getInstance();

        View view = inflater.inflate(R.layout.test_value_picker, container, false);
        view.findViewById(R.id.test_date_picker_button).setOnClickListener(this);
        view.findViewById(R.id.test_time_picker_button).setOnClickListener(this);
        view.findViewById(R.id.test_number_picker_button).setOnClickListener(this);
        mDateSummary = (TextView) view.findViewById(R.id.test_date_picker_description);
        mTimeSummary = (TextView) view.findViewById(R.id.test_time_picker_description);
        mNumberSummary = (TextView) view.findViewById(R.id.test_number_picker_description);
        mDatePickerDialog = new DatePickerDialog(context, this,
                mCalendar.get(Calendar.YEAR),
                mCalendar.get(Calendar.MONTH),
                mCalendar.get(Calendar.DAY_OF_MONTH));
        mTimePickerDialog = new TimePickerDialog(context, this,
                mCalendar.get(Calendar.HOUR_OF_DAY),
                mCalendar.get(Calendar.MINUTE),
                false);

        mDateSummary.setText(DATE_FORMAT.format(mCalendar.getTime()));
        mTimeSummary.setText(TIME_FORMAT.format(mCalendar.getTime()));
        mNumberSummary.setText("0");

        NumberPicker np = new NumberPicker(context);
        np.setMaxValue(100);
        np.setMinValue(0);
        np.setValue(0);
        np.setWrapSelectorWheel(false);
        np.setOnValueChangedListener(this);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.number_picker_title);
        builder.setView(np);
        builder.setNeutralButton(R.string.alert_ok_button, null);
        mNumberPickerDialog = builder.create();
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.test_date_picker_button:
                mDatePickerDialog.show();
                break;
            case R.id.test_time_picker_button:
                mTimePickerDialog.show();
                break;
            case R.id.test_number_picker_button:
                mNumberPickerDialog.show();
                break;
        }
    }

    @Override
    public void onDateSet(DatePicker datePicker, int year, int month, int date) {
        mCalendar.set(year, month, date);
        mDateSummary.setText(DATE_FORMAT.format(mCalendar.getTime()));
    }

    @Override
    public void onTimeSet(TimePicker timePicker, int hour, int minute) {
        mCalendar.set(Calendar.HOUR_OF_DAY, hour);
        mCalendar.set(Calendar.MINUTE, minute);
        mTimeSummary.setText(TIME_FORMAT.format(mCalendar.getTime()));
    }

    @Override
    public void onValueChange(NumberPicker numberPicker, int oldVal, int newVal) {
        mNumberSummary.setText(newVal + "");
    }
}