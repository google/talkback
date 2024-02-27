/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.accessibility.utils.material;

import static com.google.android.accessibility.utils.material.A11yAlertDialogWrapper.AlertDialogStyle.ALERT_DIALOG_CLASSIC;

import android.content.Context;
import androidx.fragment.app.FragmentManager;
import androidx.annotation.Nullable;
import com.google.android.accessibility.utils.material.A11yAlertDialogWrapper.AlertDialogStyle;

/** The class is used to create the form-specific alert dialog builder for the phone platform. */
public final class AccessibilitySuiteAlertDialogWrapperFactory {

  public static A11yAlertDialogWrapper.Builder createA11yAlertDialogWrapperBuilder(
      Context context, AlertDialogStyle style) {
    if (style == ALERT_DIALOG_CLASSIC) {
      return new PhoneAlertDialogWrapperBuilder(AlertDialogUtils.v7Builder(context));

    } else {
      return new PhoneAlertDialogWrapperBuilder(MaterialComponentUtils.alertDialogBuilder(context));
    }
  }

  public static A11yAlertDialogWrapper.Builder createA11yAlertDialogWrapperBuilder(
      Context context, AlertDialogStyle style, @Nullable FragmentManager fragmentManager) {
    return createA11yAlertDialogWrapperBuilder(context, style);
  }

  private AccessibilitySuiteAlertDialogWrapperFactory() {}
}
