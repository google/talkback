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

package com.google.android.accessibility.utils;

import android.text.TextUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.HashMap;
import org.checkerframework.checker.nullness.qual.Nullable;

/** This class manages efficient loading of classes. */
public class ClassLoadingCache {

  private static final String TAG = "ClassLoadingCache";

  // TODO: Use a LRU map instead?
  private static final HashMap<String, @Nullable Class<?>> mCachedClasses = new HashMap<>();

  /**
   * Returns a class by given <code>className</code>. It tries to load from the current class loader
   * and caches them.
   *
   * @param className The name of the class to load.
   * @return The class if loaded successfully, null otherwise.
   */
  public static @Nullable Class<?> loadOrGetCachedClass(String className) {
    if (TextUtils.isEmpty(className)) {
      LogUtils.d(TAG, "Missing class name. Failed to load class.");
      return null;
    }

    if (mCachedClasses.containsKey(className)) {
      return mCachedClasses.get(className);
    }

    Class<?> insideClazz = null;
    try {
      ClassLoader classLoader = ClassLoadingCache.class.getClassLoader();
      if (classLoader != null) {
        insideClazz = classLoader.loadClass(className);
      }
      if (insideClazz == null) {
        LogUtils.d(TAG, "Failed to load class: %s", className);
      }
    } catch (ClassNotFoundException e) {
      LogUtils.d(TAG, "Failed to load class: %s", className);
    }

    mCachedClasses.put(className, insideClazz);
    return insideClazz;
  }

  /** Returns whether a target class is an instance of a reference class. */
  public static boolean checkInstanceOf(
      CharSequence targetClassName, CharSequence referenceClassName) {
    if ((targetClassName == null) || (referenceClassName == null)) return false;
    if (TextUtils.equals(targetClassName, referenceClassName)) return true;

    final Class<?> referenceClass = loadOrGetCachedClass(referenceClassName.toString());
    final Class<?> targetClass = loadOrGetCachedClass(targetClassName.toString());
    return referenceClass != null
        && targetClass != null
        && referenceClass.isAssignableFrom(targetClass);
  }

  /** Returns whether a target class is an instance of a reference class. */
  public static boolean checkInstanceOf(CharSequence targetClassName, Class<?> referenceClass) {
    if ((targetClassName == null) || (referenceClass == null)) return false;
    if (TextUtils.equals(targetClassName, referenceClass.getName())) return true;

    final Class<?> targetClass = loadOrGetCachedClass(targetClassName.toString());
    return targetClass != null && referenceClass.isAssignableFrom(targetClass);
  }
}
