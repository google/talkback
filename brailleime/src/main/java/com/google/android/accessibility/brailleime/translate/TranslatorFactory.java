/*
 * Copyright 2019 Google Inc.
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

package com.google.android.accessibility.brailleime.translate;

import android.content.Context;
import com.google.android.accessibility.brailleime.BrailleLanguages.Code;
import com.google.common.base.Ascii;

/**
 * Factory for creating {@link Translator}.
 *
 * <p>Every implementation has an implicit {@code name} associated with it, which is simply the
 * unqualified (simple) name of the implementation class. In order for the implementation class to
 * be found at runtime, its fully-qualified package name must be of the following form:
 * com.google.android.accessibility.brailleime.translate.name.Name where {@code name} is the lower
 * case of {@code Name}.
 */
public interface TranslatorFactory {

  /** Creates the translator. */
  Translator create(Context context, Code code, boolean contractedMode);

  /** Returns the name associated with {@param aClass}. */
  static String getNameFromClass(Class<? extends TranslatorFactory> aClass) {
    return aClass.getSimpleName();
  }

  /** Returns the {@link TranslatorFactory} associated with {@param name}. */
  static TranslatorFactory forName(String name) {
    try {
      String parentPackageName = TranslatorFactory.class.getPackage().getName();
      String packageName = parentPackageName + "." + Ascii.toLowerCase(name) + "." + name;
      Class<?> aClass = Class.forName(packageName);
      return (TranslatorFactory) aClass.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalArgumentException("Could not find TranslatorFactory for name " + name, e);
    }
  }
}
