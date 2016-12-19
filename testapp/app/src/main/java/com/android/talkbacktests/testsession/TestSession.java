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

import com.android.utils.JsonUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * A class containing information of one category of test.
 */
public class TestSession {
    private static final String JSON_KEY_TITLE = "title";
    private static final String JSON_KEY_DESCRIPTION = "description";
    private static final String JSON_KEY_TESTCONTENT = "content";

    private static final String JSON_KEY_SUBTITLE = "subtitle";
    private static final String JSON_KEY_CLASSNAME = "classname";

    private final String mTitle;
    private final String mDescription;
    private final int mId;
    /** One Test Session could have several pages of {@link BaseTestContent}. */
    private final BaseTestContent[] mTestContents;
    private int mAccessCount;
    private long mLastAccessTime;

    public TestSession(Context context, JSONObject test, int id) throws JSONException {
        final String titleResourceName = JsonUtils.getString(test, JSON_KEY_TITLE);
        final String descriptionResourceName = JsonUtils.getString(test, JSON_KEY_DESCRIPTION);

        mTitle = JsonUtils.readStringByResourceIdFromString(context, titleResourceName);
        mDescription = JsonUtils.readStringByResourceIdFromString(context, descriptionResourceName);
        mId = id;

        final JSONArray contents = JsonUtils.getJsonArray(test, JSON_KEY_TESTCONTENT);

        if (contents == null || contents.length() == 0) {
            mTestContents = new BaseTestContent[0];
        } else {
            final int contentCount = contents.length();
            mTestContents = new BaseTestContent[contentCount];
            for (int i = 0; i < contentCount; i++) {
                final JSONObject content = contents.getJSONObject(i);
                try {
                    mTestContents[i] = createContent(context, content);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to create test content object. "
                            + e.getMessage());
                }
            }
        }
    }

    public BaseTestContent getTestContent(int index) {
        return mTestContents[index];
    }

    public int getId() {
        return mId;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getDescription() {
        return mDescription;
    }

    public int getContentCount() {
        return mTestContents.length;
    }

    public void setAccessCount(int count) {
        mAccessCount = count;
    }

    public void setLastAccessTime(long time) {
        mLastAccessTime = time;
    }

    public int getAccessCount() {
        return mAccessCount;
    }

    public long getLastAccessTime() {
        return mLastAccessTime;
    }

    private BaseTestContent createContent(Context context, JSONObject content) throws
            ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
            InvocationTargetException, InstantiationException, JSONException {

        final String subtitleResourceName = JsonUtils.getString(content, JSON_KEY_SUBTITLE);
        final String descriptionResourceName = JsonUtils.getString(content, JSON_KEY_DESCRIPTION);
        final String className = JsonUtils.getString(content, JSON_KEY_CLASSNAME);
        final Class<?> clazz = Class.forName(className);
        final Constructor<?> constructor = clazz.getConstructor(
                Context.class, String.class, String.class);

        final String subtitle =
                JsonUtils.readStringByResourceIdFromString(context, subtitleResourceName);
        final String description =
                JsonUtils.readStringByResourceIdFromString(context, descriptionResourceName);
        final BaseTestContent testContent = (BaseTestContent) constructor.newInstance(
                context, subtitle, description);

        return testContent;
    }
}