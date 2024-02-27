package com.google.android.accessibility.utils;

import androidx.annotation.Nullable;

/** The functional interface for logging method. */
public interface Logger {
  void log(String format, @Nullable Object... args);
}
