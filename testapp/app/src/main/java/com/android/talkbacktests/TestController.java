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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.IntDef;

import com.android.PrimitiveUtils;
import com.android.talkbacktests.testsession.TestSession;
import com.android.utils.JsonUtils;
import com.android.utils.StringUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A class containing information of all {@link TestSession}s.
 */
public class TestController {
    @IntDef({ORDER_DEFAULT, ORDER_ALPHABET, ORDER_MOST_FREQUENTLY_USED, ORDER_MOST_RECENTLY_USED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface OrderType {
    }

    public static final int ORDER_DEFAULT = 0;
    public static final int ORDER_ALPHABET = 1;
    public static final int ORDER_MOST_FREQUENTLY_USED = 2;
    public static final int ORDER_MOST_RECENTLY_USED = 3;

    private static final String JSON_KEY_TESTS = "testsessions";

    // Prefixes of SharedPreference keys
    private static final String PREF_KEY_PREFIX_COUNT = "access_count_";
    private static final String PREF_KEY_PREFIX_TIME = "access_time_";
    private static final String PREF_KEY_ORDER_TYPE = "order_type";

    private static TestController sInstance;

    // List of raw TestSessions
    private TestSession[] mTestSessions;

    // List of TestSessions to display, which is already sorted and filtered with keyword.
    private List<TestSession> mTestSessionsOrderedFiltered;

    private int mOrderType;
    // TODO: Implement the functionality to search for TestSessions with keyword.
    private String mKeyword;

    private Comparator<TestSession> mTestCaseComparator = new Comparator<TestSession>() {
        @Override
        public int compare(TestSession a, TestSession b) {
            switch (mOrderType) {
                case ORDER_ALPHABET:
                    return a.getTitle().compareToIgnoreCase(
                            b.getTitle());
                case ORDER_MOST_FREQUENTLY_USED:
                    return PrimitiveUtils.compare(b.getAccessCount(), a.getAccessCount());
                case ORDER_MOST_RECENTLY_USED:
                    return PrimitiveUtils.compare(b.getLastAccessTime(), a.getLastAccessTime());
                case ORDER_DEFAULT:
                default:
                    return PrimitiveUtils.compare(a.getId(), b.getId());
            }
        }
    };

    public static TestController getInstance() {
        if (sInstance == null) {
            sInstance = new TestController();
        }
        return sInstance;
    }

    private TestController() {
    }

    public void init(Context context) throws Exception {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final JSONObject testJson = JsonUtils.readFromRawFile(context, R.raw.test);
        final JSONArray tests = JsonUtils.getJsonArray(testJson, JSON_KEY_TESTS);
        if (tests == null || tests.length() == 0) {
            mTestSessions = new TestSession[0];
        } else {
            final int testCount = tests.length();
            mTestSessions = new TestSession[testCount];
            for (int i = 0; i < testCount; i++) {
                final JSONObject test = tests.getJSONObject(i);
                mTestSessions[i] = new TestSession(context, test, i);
                mTestSessions[i].setAccessCount(prefs.getInt(PREF_KEY_PREFIX_COUNT + i, 0));
                mTestSessions[i].setLastAccessTime(prefs.getLong(PREF_KEY_PREFIX_TIME + i, 0L));
            }
        }
        mOrderType = prefs.getInt(PREF_KEY_ORDER_TYPE, ORDER_DEFAULT);
        mKeyword = null;
        mTestSessionsOrderedFiltered = new ArrayList<>(mTestSessions.length);
        refreshData();
    }

    /**
     * Get the total number of {@link TestSession}s to display.
     */
    public int getSessionCount() {
        return mTestSessionsOrderedFiltered.size();
    }

    /**
     * Get the {@link TestSession} by index.
     *
     * @param index Index of the {@link TestSession} in the list.
     * @return Queried {@link TestSession}.
     */
    public TestSession getSessionByIndex(int index) {
        return mTestSessionsOrderedFiltered.get(index);
    }

    /**
     * Get the {@link TestSession} by ID.
     *
     * @param id ID of the {@link TestSession}.
     * @return Queried {@link TestSession}.
     */
    public TestSession getSessionById(int id) {
        return mTestSessions[id];
    }

    /**
     * Get the next {@link TestSession}.
     *
     * @param id ID of the current {@link TestSession}.
     * @return Next {@link TestSession}.
     */
    public TestSession getNextSessionById(int id) {
        final int index = mTestSessionsOrderedFiltered.indexOf(mTestSessions[id]);
        if (index >= 0 && index < mTestSessionsOrderedFiltered.size() - 1) {
            return mTestSessionsOrderedFiltered.get(index + 1);
        } else {
            return null;
        }
    }

    /**
     * Get the previous {@link TestSession}.
     *
     * @param id ID of the current {@link TestSession}.
     * @return Previous {@link TestSession}.
     */
    public TestSession getPreviousSessionById(int id) {
        final int index = mTestSessionsOrderedFiltered.indexOf(mTestSessions[id]);
        if (index > 0 && index < mTestSessionsOrderedFiltered.size()) {
            return mTestSessionsOrderedFiltered.get(index - 1);
        } else {
            return null;
        }
    }

    /**
     * Record the user action to access a {@link TestSession}.
     *
     * @param context   Context object.
     * @param sessionId ID of the accessed {@link TestSession}.
     */
    public void recordTestSessionAccessed(Context context, int sessionId) {
        final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        final int count = sharedPrefs.getInt(PREF_KEY_PREFIX_COUNT + sessionId, 0) + 1;
        final long time = System.currentTimeMillis();
        final SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(PREF_KEY_PREFIX_COUNT + sessionId, count);
        editor.putLong(PREF_KEY_PREFIX_TIME + sessionId, time);
        editor.apply();

        mTestSessions[sessionId].setAccessCount(count);
        mTestSessions[sessionId].setLastAccessTime(time);
    }

    /**
     * Set the {@link OrderType} to sort the list of {@link TestSession}s.
     */
    public void setOrderType(Context context, @OrderType int orderType) {
        mOrderType = orderType;
        final SharedPreferences.Editor editor =
                PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putInt(PREF_KEY_ORDER_TYPE, orderType);
        editor.apply();
        refreshData();
    }

    private void refreshData() {
        final boolean shouldFilterKeyword = mKeyword != null && mKeyword.length() != 0;
        mTestSessionsOrderedFiltered.clear();
        for (TestSession session : mTestSessions) {
            if (!shouldFilterKeyword
                    || StringUtils.containsIgnoreCase(session.getTitle(), mKeyword)
                    || StringUtils.containsIgnoreCase(session.getDescription(), mKeyword)) {
                mTestSessionsOrderedFiltered.add(session);
            }
        }
        Collections.sort(mTestSessionsOrderedFiltered, mTestCaseComparator);
    }
}