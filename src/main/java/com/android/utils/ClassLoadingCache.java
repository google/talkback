/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.utils;

import android.text.TextUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * This class manages efficient loading of classes.
 */
public class ClassLoadingCache {
    // TODO(KM): Use a LRU map instead?
    private static final HashMap<String, Class<?>> mCachedClasses = new HashMap<>();

    /**
     * Returns a class by given <code>className</code>. It tries to load from the current class
     * loader and caches them.
     *
     * @param className The name of the class to load.
     * @return The class if loaded successfully, null otherwise.
     */
    public static Class<?> loadOrGetCachedClass(String className) {
        if (TextUtils.isEmpty(className)) {
            LogUtils.log(Log.DEBUG, "Missing class name. Failed to load class.");
            return null;
        }

        if (mCachedClasses.containsKey(className)) return mCachedClasses.get(className);

        Class<?> insideClazz = null;
        try {
            insideClazz = ClassLoadingCache.class.getClassLoader().loadClass(className);
            if (insideClazz == null) {
                LogUtils.log(Log.DEBUG, "Failed to load class: %s", className);
            }
        } catch (ClassNotFoundException e) {
            LogUtils.log(Log.DEBUG, "Failed to load class: %s", className);
        }

        mCachedClasses.put(className, insideClazz);
        return insideClazz;
    }

    /**
     * Returns whether a target class is an instance of a reference class.
     */
    public static boolean checkInstanceOf(CharSequence targetClassName,
                                          CharSequence referenceClassName) {
        if ((targetClassName == null) || (referenceClassName == null)) return false;
        if (TextUtils.equals(targetClassName, referenceClassName)) return true;

        final Class<?> referenceClass = loadOrGetCachedClass(referenceClassName.toString());
        final Class<?> targetClass = loadOrGetCachedClass(targetClassName.toString());
        return referenceClass != null && targetClass != null &&
                referenceClass.isAssignableFrom(targetClass);
    }

    /**
     * Returns whether a target class is an instance of a reference class.
     */
    public static boolean checkInstanceOf(CharSequence targetClassName, Class<?> referenceClass) {
        if ((targetClassName == null) || (referenceClass == null)) return false;
        if (TextUtils.equals(targetClassName, referenceClass.getName())) return true;

        final Class<?> targetClass = loadOrGetCachedClass(targetClassName.toString());
        return targetClass != null && referenceClass.isAssignableFrom(targetClass);
    }
}
