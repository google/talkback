package com.google.android.libraries.accessibility.utils;

import android.content.ComponentName;

import java.util.regex.Pattern;

/**
 * A class to create {@link ComponentName}s that always hold the fully qualified class name.
 */
public class QualifiedComponentName {

  private static final Pattern QUALIFIED_CLASS_PATTERN = Pattern.compile(
      // starts with 1+ lowercase alphanumerics starting with a letter ending with a '.'
      "^([a-z][a-z0-9_]*\\.)+"
      + "[A-Z][a-zA-Z0-9_]*$");  // ends with alphanumeric beginning with a capital letter

  private static final Pattern RELATIVE_CLASS_PATTERN = Pattern.compile(
      // starts with '.' then 0+ lowercase alphanumerics starting with a letter ending with a '.'
      "^\\.([a-z][a-z0-9_]*\\.)*"
      + "[A-Z][a-zA-Z0-9_]*$");  // ends with alphanumeric beginning with a capital letter

  private static final Pattern SIMPLE_CLASS_PATTERN = Pattern.compile(
      "^[A-Z][a-zA-Z0-9_]*$");  // ends with alphanumeric beginning with a capital letter

  /**
   * Returns a {@link ComponentName} corresponding to the same component as the given
   * {@link ComponentName}, but with a fully qualified class name. If the class or package
   * name are empty or do not follow java naming scheme, this will return null.
   */
  public static ComponentName fromComponentName(ComponentName componentName) {
    String packageName = componentName.getPackageName();
    String className = componentName.getClassName();
    if (QUALIFIED_CLASS_PATTERN.matcher(className).matches()) {
      return componentName;
    } else if (RELATIVE_CLASS_PATTERN.matcher(className).matches()) {
      return new ComponentName(packageName, packageName + className);
    } else if (SIMPLE_CLASS_PATTERN.matcher(className).matches()) {
      return new ComponentName(packageName, packageName + "." + className);
    }
    return null;
  }

  public static ComponentName unflattenFromString(String str) {
    ComponentName nonqualifiedComponentName = ComponentName.unflattenFromString(str);
    if (nonqualifiedComponentName == null) {
      return null;
    }
    return fromComponentName(nonqualifiedComponentName);
  }
}
