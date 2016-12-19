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

package com.android.talkbacktests;

/**
 * An interface containing callbacks of navigation among test sessions.
 */
public interface NavigationCallback {

    /**
     * Callback when a certain test session is selected.
     * @param sessionId ID of the selected test session.
     */
    void onTestSessionSelected(int sessionId);

    /**
     * Callback to navigate to the next test content.
     * @param sessionId ID of current test session.
     * @param contentIndex Index of current test content page.
     */
    void onNextContentClicked(int sessionId, int contentIndex);

    /**
     * Callback to navigate to the previous test content.
     * @param sessionId ID of current test session.
     * @param contentIndex Index of current test content page.
     */
    void onPreviousContentClicked(int sessionId, int contentIndex);

}
