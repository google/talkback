/*
 * Copyright 2020 Google Inc.
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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PointF;
import android.util.Size;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Reads and writes user preferences for the calibration points. */
public class UserPreferencesForDots {

  private static final String JSON_KEY_CALIBRATION_ORIENTATION = "orientation";
  private static final String JSON_KEY_CALIBRATION_POINTS = "points";
  private static final String JSON_KEY_CALIBRATION_POINTS_X = "x";
  private static final String JSON_KEY_CALIBRATION_POINTS_Y = "y";
  private static final String JSON_KEY_SCREEN_SIZE_WIDTH = "screen_width";
  private static final String JSON_KEY_SCREEN_SIZE_HEIGHT = "screen_height";

  static List<PointF> readLayoutPointsPhone(
      Context context,
      SharedPreferences sharedPreferences,
      boolean isTableTop,
      int orientation,
      Size screenSize)
      throws ParseException {
    String pointsString =
        sharedPreferences.getString(
            context.getString(
                isTableTop
                    ? R.string.pref_brailleime_calibration_points_phone_tabletop
                    : R.string.pref_brailleime_calibration_points_phone_screenaway),
            "");
    try {
      return pointsStringToPoints(orientation, screenSize, pointsString);
    } catch (JSONException e) {
      throw new ParseException(e.getMessage(), -1);
    }
  }

  static void writeLayoutPointsPhone(
      Context context,
      SharedPreferences sharedPreferences,
      boolean isTableTop,
      int orientation,
      Size screenSize,
      List<PointF> points)
      throws ParseException {
    try {
      sharedPreferences
          .edit()
          .putString(
              context.getString(
                  isTableTop
                      ? R.string.pref_brailleime_calibration_points_phone_tabletop
                      : R.string.pref_brailleime_calibration_points_phone_screenaway),
              generateLayoutPointsString(points, orientation, screenSize))
          .apply();
    } catch (JSONException e) {
      throw new ParseException(e.getMessage(), -1);
    }
  }

  static List<PointF> readLayoutPointsTablet(
      Context context, SharedPreferences sharedPreferences, int orientation) throws ParseException {
    try {
      String pointsString =
          sharedPreferences.getString(
              context.getString(
                  orientation == Configuration.ORIENTATION_PORTRAIT
                      ? R.string.pref_brailleime_calibration_points_tablet_tabletop_portrait
                      : R.string.pref_brailleime_calibration_points_tablet_tabletop_landscape),
              "");
      List<PointF> points = new ArrayList<>();
      if (!pointsString.isEmpty()) {
        points = parseLayoutPointsString(pointsString);
      }
      return points;
    } catch (JSONException e) {
      throw new ParseException(e.getMessage(), -1);
    }
  }

  static void writeLayoutPointsTablet(
      Context context,
      SharedPreferences sharedPreferences,
      int orientation,
      List<PointF> points,
      Size screenSize)
      throws ParseException {
    try {
      sharedPreferences
          .edit()
          .putString(
              context.getString(
                  orientation == Configuration.ORIENTATION_PORTRAIT
                      ? R.string.pref_brailleime_calibration_points_tablet_tabletop_portrait
                      : R.string.pref_brailleime_calibration_points_tablet_tabletop_landscape),
              generateLayoutPointsString(points, orientation, screenSize))
          .apply();
    } catch (JSONException e) {
      throw new ParseException(e.getMessage(), -1);
    }
  }

  private static List<PointF> pointsStringToPoints(
      int orientation, Size screenSize, String pointsString) throws JSONException {
    List<PointF> points = new ArrayList<>();
    if (!pointsString.isEmpty()) {
      int savedOrientation = new JSONObject(pointsString).getInt(JSON_KEY_CALIBRATION_ORIENTATION);
      int width =
          new JSONObject(pointsString).optInt(JSON_KEY_SCREEN_SIZE_WIDTH, screenSize.getWidth());
      int height =
          new JSONObject(pointsString).optInt(JSON_KEY_SCREEN_SIZE_HEIGHT, screenSize.getHeight());
      Size oldScreenSize = new Size(width, height);
      List<PointF> unorientedPoints = parseLayoutPointsString(pointsString);
      for (int i = 0; i < unorientedPoints.size(); i++) {
        PointF point = unorientedPoints.get(i);
        if (savedOrientation == Configuration.ORIENTATION_PORTRAIT
            && orientation == Configuration.ORIENTATION_LANDSCAPE) {
          point = Utils.mapPortraitToLandscapeForPhone(point, oldScreenSize, screenSize);
        } else if (savedOrientation == Configuration.ORIENTATION_LANDSCAPE
            && orientation == Configuration.ORIENTATION_PORTRAIT) {
          point = Utils.mapLandscapeToPortraitForPhone(point, oldScreenSize, screenSize);
        }
        points.add(point);
      }
    }
    return points;
  }

  private static List<PointF> parseLayoutPointsString(String pointsString) throws JSONException {
    List<PointF> points = new ArrayList<>();
    JSONObject jsonObject = new JSONObject(pointsString);
    JSONArray jArray = jsonObject.getJSONArray(JSON_KEY_CALIBRATION_POINTS);
    for (int i = 0; i < jArray.length(); i++) {
      float x = (float) jArray.getJSONObject(i).getDouble(JSON_KEY_CALIBRATION_POINTS_X);
      float y = (float) jArray.getJSONObject(i).getDouble(JSON_KEY_CALIBRATION_POINTS_Y);
      points.add(new PointF(x, y));
    }
    return points;
  }

  private static String generateLayoutPointsString(
      List<PointF> points, int orientation, Size screenSize) throws JSONException {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(JSON_KEY_CALIBRATION_ORIENTATION, orientation);
    jsonObject.put(JSON_KEY_SCREEN_SIZE_WIDTH, screenSize.getWidth());
    jsonObject.put(JSON_KEY_SCREEN_SIZE_HEIGHT, screenSize.getHeight());
    JSONArray pointArray = new JSONArray();
    for (PointF point : points) {
      JSONObject jPoint = new JSONObject();
      jPoint.put(JSON_KEY_CALIBRATION_POINTS_X, point.x);
      jPoint.put(JSON_KEY_CALIBRATION_POINTS_Y, point.y);
      pointArray.put(jPoint);
    }
    jsonObject.put(JSON_KEY_CALIBRATION_POINTS, pointArray);
    return jsonObject.toString();
  }

  private UserPreferencesForDots() {}
}
