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

package com.google.android.accessibility.brailleime;

import static android.content.Context.WINDOW_SERVICE;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.URLSpan;
import android.util.DisplayMetrics;
import android.util.Size;
import android.util.TypedValue;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.braille.common.ImeConnection;
import java.util.Locale;
import java.util.Optional;

/** Static convenience methods for Braille Keyboard. */
public class Utils {
  private static final String TAG = "Utils";
  // Refer from NAV_BAR_MODE_GESTURAL in
  // frameworks/base/core/java/android/view/WindowManagerPolicyConstants.java
  private static final int NAV_BAR_MODE_GESTURAL = 2;

  private Utils() {}

  /**
   * Returns the float value associated with a resource id by delegating to {@link
   * TypedValue#getFloat}.
   */
  public static float getResourcesFloat(Resources resources, int id) {
    TypedValue typedValue = new TypedValue();
    resources.getValue(id, typedValue, true);
    return typedValue.getFloat();
  }

  /** Converts millimeters to pixels in a device-specific manner. */
  public static float mmToPixels(Resources resources, int mm) {
    return mm
        * TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_MM, 1 /* value */, resources.getDisplayMetrics());
  }

  /** Computes the euclidean distance between two {@link PointF} objects. */
  public static double distance(PointF p1, PointF p2) {
    return Math.hypot(p1.x - p2.x, p1.y - p2.y);
  }

  /**
   * Returns the size of the display in pixels by delegating to {@link Display#getRealSize(Point)}.
   */
  public static Size getDisplaySizeInPixels(Context context) {
    WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    Point displaySize = new Point();
    windowManager.getDefaultDisplay().getRealSize(displaySize);
    int width = displaySize.x;
    int height = displaySize.y;
    return new Size(width, height);
  }

  /**
   * Returns the size of the visible display for specific View in pixels by delegating to {@link
   * View#getWindowVisibleDisplayFrame(Rect)}.
   */
  public static Size getVisibleDisplaySizeInPixels(View view) {
    Rect rect = new Rect();
    view.getWindowVisibleDisplayFrame(rect);
    return new Size(rect.width(), rect.height());
  }

  /**
   * Returns the rotation angle of the current configuration, by delegating to {@link
   * Display#getRotation()}.
   */
  public static int getDisplayRotationDegrees(Context context) {
    WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    return windowManager.getDefaultDisplay().getRotation();
  }

  /**
   * Returns the baseline height in pixels of {@code paint} by delegating to {@link
   * Paint#getFontMetrics}.
   */
  public static int getPaintTextBaselineInPixels(Paint paint) {
    Paint.FontMetrics fontMetrics = paint.getFontMetrics();
    int baseline = (int) ((fontMetrics.bottom - fontMetrics.top) / 2 - fontMetrics.bottom);
    return baseline;
  }

  /**
   * Returns {@code true} if the absolute values of the given coordinates have a large enough ratio
   * that the 2-D vector formed from them is nearly cardinal, where "nearly cardinal" is defined as
   * any ratio that is greater than or equal to {@code ratioThreshold}.
   *
   * <p>If either (but not both) of the coordinates are {@code 0}, then true is returned.
   *
   * <p>If both of the coordinates are {@code 0}, then false is returned.
   */
  public static boolean isVectorNearlyCardinal(PointF vector, float ratioThreshold) {
    if (vector.x == 0 && vector.y == 0) {
      return false;
    }
    if (vector.x == 0 || vector.y == 0) {
      return true;
    }
    return Math.max(Math.abs(vector.x / vector.y), Math.abs(vector.y / vector.x)) > ratioThreshold;
  }

  /** Inserts space between characters. Example: "abc" -> "a b c". */
  public static String insertSpacesInto(String arg) {
    return arg.replace("", " ").trim();
  }

  /** Returns {@code true} if the runtime is Robolectric. */
  public static boolean isRobolectric() {
    return "robolectric".equals(Build.FINGERPRINT);
  }

  /** Formats {@code substring} as {@code drawable}. */
  public static SpannableString formatSubstringAsDrawable(
      SpannableString spannableString, String substring, Drawable drawable) {
    String token = " ";
    int indexIconStart = spannableString.toString().indexOf(substring);
    int indexIconEnd = indexIconStart + substring.length();
    if (indexIconStart == -1) {
      return spannableString;
    }
    SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(spannableString);
    spannableStringBuilder.replace(indexIconStart, indexIconEnd, token);
    drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
    spannableStringBuilder.setSpan(
        new ImageSpan(drawable),
        indexIconStart,
        indexIconStart + token.length(),
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return SpannableString.valueOf(spannableStringBuilder);
  }

  /** Formats {@param substring} as {@code url} link. Returns true if success; otherwise false. */
  public static boolean formatSubstringAsUrl(
      SpannableString spannableString, String substring, String url) {
    int indexStart = spannableString.toString().indexOf(substring);
    int indexEnd = indexStart + substring.length();
    if (indexStart == -1) {
      return false;
    }
    spannableString.setSpan(
        new URLSpan(url), indexStart, indexEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return true;
  }

  /**
   * Enables/disables {@code component}.
   *
   * <p>Returns absent if the PackageManager does not recognize the component. Otherwise, return
   * true if the PackageManager was told to enable the component.
   */
  public static Optional<Boolean> setComponentEnabled(
      Context context, ComponentName component, boolean enabled) {
    try {
      PackageManager packageManager = context.getPackageManager();
      int newEnablement =
          enabled
              ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
              : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
      packageManager.setComponentEnabledSetting(
          component, newEnablement, PackageManager.DONT_KILL_APP);
      return Optional.of(enabled);
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  /**
   * Attaches a Settings Highlight description {@link Bundle} with the given {@link Intent}.
   *
   * <p>Only works on Pixel devices; has no effect on other devices.
   *
   * <p>For more information, see
   * https://docs.google.com/document/d/1LnnoitwKYd-dNxQ7HE9PRynBp_vLa2aT-s-3D4VD8u4
   */
  public static void attachSettingsHighlightBundle(Intent intent, ComponentName componentName) {
    Bundle bundle = new Bundle();
    bundle.putString(":settings:fragment_args_key", componentName.flattenToString());
    intent.putExtra(":settings:show_fragment_args", bundle);
  }

  /**
   * Parses a string as int or returns {@code defValue} if the string is null or if parsing fails.
   */
  public static int parseIntWithDefault(String putativeIntegerString, int defValue) {
    if (putativeIntegerString == null) {
      return defValue;
    }
    try {
      return Integer.parseInt(putativeIntegerString);
    } catch (NumberFormatException e) {
      return defValue;
    }
  }

  /**
   * Concatenates two strings together, separated by a newline, and accents the second string with
   * the given color.
   */
  public static CharSequence appendWithColoredString(
      CharSequence firstString, CharSequence secondString, @ColorInt int color) {
    SpannableString coloredSecondString = new SpannableString(secondString);
    coloredSecondString.setSpan(
        new ForegroundColorSpan(color),
        /* start= */ 0,
        coloredSecondString.length(),
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return TextUtils.concat(firstString, "\n", coloredSecondString);
  }

  public static boolean isDebugBuild() {
    return "eng".equals(Build.TYPE) || "userdebug".equals(Build.TYPE);
  }

  /** Use Ime supplied View as keyboard input view; otherwise use Accessibility Overlay. */
  public static boolean useImeSuppliedInputWindow() {
    // return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    // We encounter the problem that system UI has probabilities to invade the keyboard area and
    // make the system UI easily touched by the user. It is a bad user experience, so set it to
    // false until we come out a good solution about it.
    return false;
  }

  /** Returns {@code true} if sensor event values indicate the device is in a flat orientation. */
  public static boolean isFlat(float[] sensorEventValues) {
    float x = sensorEventValues[0];
    float y = sensorEventValues[1];
    float z = sensorEventValues[2];
    float normOfGravity = (float) Math.sqrt(x * x + y * y + z * z);
    int inclination = (int) Math.round(Math.toDegrees(Math.acos(z / normOfGravity)));
    return inclination < 25 || inclination > 155;
  }

  /**
   * Adjust sensor values based on device rotation. For example, some tablet is default landscape.
   * Its screen has been reorientated. We need to compensate the values.
   */
  public static float[] adjustAccelOrientation(int displayRotation, float[] sensorEventValues) {
    float[] adjustedValues = new float[3];
    final int[][] axisSwap = {
      {1, -1, 0, 1}, // ROTATION_0
      {-1, -1, 1, 0}, // ROTATION_90
      {-1, 1, 0, 1}, // ROTATION_180
      {1, 1, 1, 0} // ROTATION_270
    };
    final int[] as = axisSwap[displayRotation];
    adjustedValues[0] = (float) as[0] * sensorEventValues[as[2]];
    adjustedValues[1] = (float) as[1] * sensorEventValues[as[3]];
    adjustedValues[2] = sensorEventValues[2];
    return adjustedValues;
  }

  /** Takes the average of two colors. */
  public static int averageColors(int color1, int color2) {
    return (int) new ArgbEvaluator().evaluate(0.5f, color1, color2);
  }

  /** Whether the device's natural orientation is portrait. */
  public static boolean isDeviceDefaultPortrait(Context context) {
    int rotation =
        ((WindowManager) context.getSystemService(WINDOW_SERVICE))
            .getDefaultDisplay()
            .getRotation();
    DisplayMetrics dm = new DisplayMetrics();
    ((WindowManager) context.getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getMetrics(dm);
    int width = dm.widthPixels;
    int height = dm.heightPixels;
    return ((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) && height > width)
        || ((rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)
            && width > height);
  }

  /**
   * Returns whether navigation bar is on the left. Nav bar could be located on the left side
   * starting from Android 7.1. Nav bar could be located on the bottom if full gestural navigation
   * is enabled.
   */
  public static boolean isNavigationBarLeftLocated(Context context) {
    return !isFullGesturalNavigationEnabled(context)
        && getDisplayRotationDegrees(context) == Surface.ROTATION_270;
  }

  /** Returns whether full gestural navigation is enabled by user. */
  public static boolean isFullGesturalNavigationEnabled(Context context) {
    Resources resources = context.getResources();
    int resId = resources.getIdentifier("config_navBarInteractionMode", "integer", "android");
    return resId > 0 && resources.getInteger(resId) == NAV_BAR_MODE_GESTURAL;
  }

  /**
   * Capitalize the first letter of a string. Supports Unicode.
   *
   * @param str The input {@link String} for which to capitalize the first letter
   * @return The input {@link String} with the first letter capitalized
   */
  public static String capitalizeFirstLetter(String str) {
    if (TextUtils.isEmpty(str)) {
      return str;
    }
    return Character.isUpperCase(str.charAt(0))
        ? str
        : str.substring(0, 1).toUpperCase(Locale.getDefault()) + str.substring(1);
  }

  /** Returns the braille keyboard display name. */
  public static String getBrailleKeyboardDisplayName(Context context) {
    String name;
    if (BrailleUserPreferences.readAvailablePreferredCodes(context).size() == 1) {
      name = context.getString(R.string.braille_ime_displayed_name);
    } else {
      String codeUserFacingName =
          BrailleUserPreferences.readCurrentActiveInputCodeAndCorrect(context)
              .getUserFacingName(context);
      name =
          context.getString(
              R.string.multiple_languages_braille_ime_displayed_name, codeUserFacingName);
    }
    return name;
  }

  public static boolean isPhonePermissionGranted(Context context) {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
        == PackageManager.PERMISSION_GRANTED;
  }

  /** Returns text of the editor action. */
  public static CharSequence getActionLabel(
      InputMethodService inputMethodService, EditorInfo editorInfo) {
    CharSequence action = null;
    if (editorInfo == null) {
      return "";
    }
    if (editorInfo.actionLabel != null) {
      action = editorInfo.actionLabel;
    }
    if (action == null) {
      action = inputMethodService.getTextForImeAction(editorInfo.imeOptions);
    }
    if (action == null) {
      return "";
    }
    return action;
  }

  /** Returns hint of the edit field. */
  public static CharSequence getHint(ImeConnection imeConnection) {
    CharSequence hint = imeConnection.editorInfo.hintText;
    if (!TextUtils.isEmpty(hint)) {
      return hint;
    }
    ExtractedText extractedText =
        imeConnection.inputConnection.getExtractedText(new ExtractedTextRequest(), 0);
    if (extractedText != null
        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        && !TextUtils.isEmpty(extractedText.hint)) {
      return extractedText.hint;
    }
    return "";
  }
}
