package com.google.android.accessibility.braille.brltty.device;

/** Provides BRLTTY parameters to connect. */
public abstract class ParameterProvider {
  public static final String DELIMITER = "+";

  /** Gets parameters that needed for establishing connection. */
  public abstract String getParameters();
}
