/*
 * Copyright 2021 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.braille.brailledisplay.platform.lib;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Adds List<String> support to SharedPreferences.
 *
 * <p>Android's SharedPreferences API supports Set<String> but does not support List<String>. This
 * class implements List<String> support by prepending set members with an ordering index followed
 * by a delimiter. Here is an example of how the data structure is stored:
 *
 * <ol>
 *   <li>1$Item
 *   <li>3$Item
 *   <li>0$Item
 *   <li>2$Item
 * </ol>
 */
public class SharedPreferencesStringList {

  private static String DELIMITER = "$";

  /** Inserts a value at the front of the list associated with the given key. */
  public static void insertAtFront(SharedPreferences sharedPrefs, String prefKey, String value) {
    TreeMap<Integer, String> oldMap = readTreeMap(sharedPrefs, prefKey);
    TreeMap<Integer, String> newMap = new TreeMap<>();
    newMap.put(-1, value);
    for (int i = 0; i < oldMap.keySet().size(); i++) {
      String oldValue = oldMap.get(i);
      if (!value.equals(oldValue)) {
        newMap.put(i, oldValue);
      }
    }
    writeTreeMap(sharedPrefs, prefKey, newMap);
  }

  /** Removes the value associated with the given key, if present; otherwise does nothing. */
  public static void remove(SharedPreferences sharedPrefs, String prefKey, String deviceName) {
    TreeMap<Integer, String> treeMap = readTreeMap(sharedPrefs, prefKey);
    for (Iterator<Integer> iter = treeMap.navigableKeySet().iterator(); iter.hasNext(); ) {
      if (deviceName.equals(treeMap.get(iter.next()))) {
        iter.remove();
      }
    }
    writeTreeMap(sharedPrefs, prefKey, treeMap);
  }

  /** Gets the list associated with the given key, if present; otherwise returns an empty list. */
  public static List<String> getList(SharedPreferences sharedPrefs, String prefKey) {
    return new ArrayList<>(readTreeMap(sharedPrefs, prefKey).values());
  }

  private static void writeTreeMap(
      SharedPreferences sharedPrefs, String pref, TreeMap<Integer, String> treeMap) {
    LinkedHashSet<String> set = new LinkedHashSet<>();
    int i = 0;
    for (String value : treeMap.values()) {
      set.add(i + String.valueOf(DELIMITER) + value);
      i++;
    }
    sharedPrefs.edit().putStringSet(pref, set).apply();
  }

  public static TreeMap<Integer, String> readTreeMap(SharedPreferences sharedPrefs, String pref) {
    TreeMap<Integer, String> treeMap = new TreeMap<>();
    Set<String> deviceNamesWithOrderingPrefix = sharedPrefs.getStringSet(pref, new HashSet<>());
    if (deviceNamesWithOrderingPrefix.isEmpty()) {
      return new TreeMap<>();
    } else {
      for (String deviceNameWithOrderingPrefix : deviceNamesWithOrderingPrefix) {
        int indexOfDelimiter = deviceNameWithOrderingPrefix.indexOf(DELIMITER);
        if (indexOfDelimiter != -1) {
          String deviceName = deviceNameWithOrderingPrefix.substring(indexOfDelimiter + 1);
          if (!deviceName.isEmpty()) {
            try {
              int rank =
                  Integer.parseInt(deviceNameWithOrderingPrefix.substring(0, indexOfDelimiter));
              treeMap.put(rank, deviceName);
            } catch (NumberFormatException e) {
              // Corrupted record. Ignore it (which has the effect of removing it).
            }
          }
        } else {
          // Corrupted record. Ignore it (which has the effect of removing it).
        }
      }
    }
    return treeMap;
  }
}
