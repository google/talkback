/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.android.accessibility.utils.compat;

import android.app.AppOpsManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Compatibility utilities for interacting with {@link AppOpsManager} */
public final class AppOpsManagerCompatUtils {
  private static final Class<?> CLASS = AppOpsManager.class;

  private static final Method METHOD_checkOpNoThrow =
      CompatUtils.getMethod(CLASS, "checkOpNoThrow", int.class, int.class, String.class);

  private static final Field FIELD_OP_PROJECT_MEDIA =
      CompatUtils.getField(CLASS, "OP_PROJECT_MEDIA");

  private static final int OP_CODE_UNRESOLVED = -1;

  public static final int OP_PROJECT_MEDIA =
      (Integer) CompatUtils.getFieldValue(null, OP_CODE_UNRESOLVED, FIELD_OP_PROJECT_MEDIA);

  private AppOpsManagerCompatUtils() {
    // Not instantiable
  }

  /**
   * Like {@link AppOpsManager#checkOp(String,int,String)} but instead of throwing a {@link
   * SecurityException} it returns {@link AppOpsManager#MODE_ERRORED}.
   *
   * @param manager The {@link AppOpsManager} instance to invoke
   * @param op The operation to check. One of the OP_* constants
   * @param uid The user id of the application attempting to perform the operation
   * @param pName The package name of the application attempting to perform the operation
   * @return {@link AppOpsManager#MODE_ALLOWED} if the operation is allowed, {@link
   *     AppOpsManager#MODE_IGNORED} if it is not allowed and should be silently ignored (without
   *     causing the app to crash), {@link AppOpsManager#MODE_ERRORED} if a {@link
   *     SecurityException} would have been through if invoked via {@link
   *     AppOpsManager#checkOp(String, int, String)}, or {@code -1} if another error occurs
   */
  public static int checkOpNoThrow(AppOpsManager manager, int op, int uid, String pName) {
    return (Integer) CompatUtils.invoke(manager, -1, METHOD_checkOpNoThrow, op, uid, pName);
  }
}
