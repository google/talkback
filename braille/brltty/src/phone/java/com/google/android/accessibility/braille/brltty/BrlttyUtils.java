/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.android.accessibility.braille.brltty;

import android.content.Context;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Utility class for BRLTTY. */
public class BrlttyUtils {
  private BrlttyUtils() {}

  private static Map<String, BrailleCharacter> dotKeyStringsToDotCache;

  /**
   * Removes "DotN" strings from a list and accumulates the removed dots into a {@link
   * BrailleCharacter}.
   *
   * <p>For example, [ "Foo", "Dot1", "Dot2" ] -> ["Foo"], new BrailleCharacter("12")
   */
  public static BrailleCharacter extractBrailleCharacter(Context context, List<String> keyNames) {
    BrailleCharacter brailleCharacter = new BrailleCharacter();
    if (dotKeyStringsToDotCache == null) {
      dotKeyStringsToDotCache = new LinkedHashMap<>();
      Map<Integer, BrailleCharacter> dotKeyStringsToDot = new LinkedHashMap<>();
      dotKeyStringsToDot.put(R.string.key_Dot1, BrailleCharacter.DOT1);
      dotKeyStringsToDot.put(R.string.key_Dot2, BrailleCharacter.DOT2);
      dotKeyStringsToDot.put(R.string.key_Dot3, BrailleCharacter.DOT3);
      dotKeyStringsToDot.put(R.string.key_Dot4, BrailleCharacter.DOT4);
      dotKeyStringsToDot.put(R.string.key_Dot5, BrailleCharacter.DOT5);
      dotKeyStringsToDot.put(R.string.key_Dot6, BrailleCharacter.DOT6);
      dotKeyStringsToDot.put(R.string.key_Dot7, BrailleCharacter.DOT7);
      dotKeyStringsToDot.put(R.string.key_Dot8, BrailleCharacter.DOT8);
      for (int keyDotStringResId : dotKeyStringsToDot.keySet()) {
        String keyName = context.getString(keyDotStringResId);
        dotKeyStringsToDotCache.put(keyName, dotKeyStringsToDot.get(keyDotStringResId));
      }
    }
    for (Iterator<String> iter = keyNames.iterator(); iter.hasNext(); ) {
      String keyName = iter.next();
      if (dotKeyStringsToDotCache.containsKey(keyName)) {
        brailleCharacter.unionMutate(dotKeyStringsToDotCache.get(keyName));
        iter.remove();
      }
    }
    return brailleCharacter;
  }
}
