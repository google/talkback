package com.google.android.accessibility.utils;

import com.google.errorprone.annotations.CheckReturnValue;

/** Compat version of {@link java.util.function.Supplier} */
@CheckReturnValue
public interface Supplier<T> {

  /** Gets a result. */
  T get();
}
