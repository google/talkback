/*
 * Copyright (C) 2016 Google Inc.
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

package com.android.talkback.formatter;

import android.test.suitebuilder.annotation.MediumTest;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Switch;

import com.android.talkback.R;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;

public class ClickFormatterTest extends TalkBackInstrumentationTestCase {
    @MediumTest
    public void testCheckBox_checked() throws Throwable {
        setContentView(R.layout.compound_button);
        startRecordingUtterances();

        final View checkBox = getViewForId(R.id.check_box);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                checkBox.performClick();
            }
        });

        stopRecordingAndAssertUtterance("checked");
    }

    @MediumTest
    public void testCheckBox_unchecked() throws Throwable {
        setContentView(R.layout.compound_button);
        startRecordingUtterances();

        final CheckBox checkBox = (CheckBox) getViewForId(R.id.check_box);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                checkBox.setChecked(true);
                checkBox.performClick();
            }
        });

        stopRecordingAndAssertUtterance("not checked");
    }

    @MediumTest
    public void testSwitch_on() throws Throwable {
        setContentView(R.layout.compound_button);
        startRecordingUtterances();

        final View switchView = getViewForId(R.id.switch_basic);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                switchView.performClick();
            }
        });

        stopRecordingAndAssertUtterance("on");
    }

    @MediumTest
    public void testSwitch_off() throws Throwable {
        setContentView(R.layout.compound_button);
        startRecordingUtterances();

        final Switch switchView = (Switch) getViewForId(R.id.switch_basic);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                switchView.setChecked(true);
                switchView.performClick();
            }
        });

        stopRecordingAndAssertUtterance("off");
    }

    @MediumTest
    public void testCheckBox_nested() throws Throwable {
        setContentView(R.layout.checkable_complex);
        final View parent = getViewForId(R.id.check_box_parent);
        final CheckBox checkBox = (CheckBox) getViewForId(R.id.check_box);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                parent.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        checkBox.performClick();
                    }
                });
            }
        });
        getInstrumentation().waitForIdleSync();

        startRecordingUtterances();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                parent.performClick();
            }
        });

        stopRecordingAndAssertUtterance("checked");
    }
    @MediumTest
    public void testSwitch_nested() throws Throwable {
        setContentView(R.layout.checkable_complex);
        final View parent = getViewForId(R.id.check_box_parent);
        final Switch switchView = (Switch) getViewForId(R.id.switch_view);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                parent.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        switchView.performClick();
                    }
                });
            }
        });
        getInstrumentation().waitForIdleSync();

        startRecordingUtterances();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                parent.performClick();
            }
        });

        stopRecordingAndAssertUtterance("on");
    }
}