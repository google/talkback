/*
 * Copyright (C) 2013 Google Inc.
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

package com.google.android.accessibility.utils;

import androidx.collection.SparseArrayCompat;
import java.util.Iterator;
import java.util.NoSuchElementException;

/** Extension of {@link SparseArrayCompat} that implements {@link Iterable}. */
public class SparseIterableArray<T> extends SparseArrayCompat<T> implements Iterable<T> {
  @Override
  public Iterator<T> iterator() {
    return new SparseIterator();
  }

  private class SparseIterator implements Iterator<T> {
    private int mIndex = 0;

    @Override
    public boolean hasNext() {
      return (mIndex < size());
    }

    @Override
    public T next() {
      if (mIndex >= size()) {
        throw new NoSuchElementException();
      }

      return valueAt(mIndex++);
    }

    @Override
    public void remove() {
      if ((mIndex < 0) || (mIndex >= size())) {
        throw new IllegalStateException();
      }

      removeAt(mIndex--);
    }
  }
}
