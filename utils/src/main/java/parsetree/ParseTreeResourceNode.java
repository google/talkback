/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.accessibility.utils.parsetree;

import android.content.res.Resources;
import android.support.annotation.IntDef;
import android.util.Log;
import com.google.android.accessibility.utils.LogUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ParseTreeResourceNode extends ParseTreeNode {
  private static final Pattern RESOURCE_PATTERN =
      Pattern.compile("@(string|plurals|raw|array)/(\\w+)");

  @IntDef({TYPE_STRING, TYPE_PLURALS, TYPE_RESOURCE_ID})
  @Retention(RetentionPolicy.SOURCE)
  @interface Type {}

  static final int TYPE_STRING = 0;
  static final int TYPE_PLURALS = 1;
  static final int TYPE_RESOURCE_ID = 2;

  private final Resources mResources;
  private final int mResourceId;
  private final @Type int mType;
  private final List<ParseTreeNode> mParams = new ArrayList<>();

  ParseTreeResourceNode(Resources resources, String resourceName, String packageName) {
    mResources = resources;

    Matcher matcher = RESOURCE_PATTERN.matcher(resourceName);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Resource parameter is malformed: " + resourceName);
    }
    String type = matcher.group(1);
    String name = matcher.group(2);

    switch (type) {
      case "string":
        mType = TYPE_STRING;
        break;
      case "plurals":
        mType = TYPE_PLURALS;
        break;
      case "raw":
      case "array":
        mType = TYPE_RESOURCE_ID;
        break;
      default:
        throw new IllegalArgumentException("Unknown resource type: " + type);
    }

    mResourceId = mResources.getIdentifier(name, type, packageName);

    if (mResourceId == 0) {
      throw new IllegalStateException("Missing resource: " + resourceName);
    }
  }

  void addParams(List<ParseTreeNode> params) {
    mParams.addAll(params);
  }

  @Override
  public int getType() {
    switch (mType) {
      case TYPE_STRING:
      case TYPE_PLURALS:
        return ParseTree.VARIABLE_STRING;

      case TYPE_RESOURCE_ID:
      default:
        return ParseTree.VARIABLE_INTEGER;
    }
  }

  @Override
  public int resolveToInteger(ParseTree.VariableDelegate delegate, String logIndent) {
    return mResourceId;
  }

  @Override
  public CharSequence resolveToString(ParseTree.VariableDelegate delegate, String logIndent) {
    switch (mType) {
      case TYPE_STRING:
        return mResources.getString(mResourceId, getParamList(mParams, 0, delegate, logIndent));

      case TYPE_PLURALS:
        if (mParams.isEmpty() || mParams.get(0).getType() != ParseTree.VARIABLE_INTEGER) {
          LogUtils.log(this, Log.ERROR, "First parameter for plurals must be the count");
          return "";
        }
        return mResources.getQuantityString(
            mResourceId,
            mParams.get(0).resolveToInteger(delegate, logIndent),
            getParamList(mParams, 1, delegate, logIndent));

      case TYPE_RESOURCE_ID:
        LogUtils.log(this, Log.ERROR, "Cannot resolve resource ID to string");
        return "";

      default:
        LogUtils.log(this, Log.ERROR, "Unknown resource type: " + mType);
        return "";
    }
  }

  private static Object[] getParamList(
      List<ParseTreeNode> params,
      int start,
      ParseTree.VariableDelegate delegate,
      String logIndent) {
    List<Object> result = new ArrayList<>();
    for (ParseTreeNode node : params.subList(start, params.size())) {
      switch (node.getType()) {
        case ParseTree.VARIABLE_BOOL:
          result.add(node.resolveToBoolean(delegate, logIndent));
          break;

        case ParseTree.VARIABLE_STRING:
          result.add(node.resolveToString(delegate, logIndent));
          break;

        case ParseTree.VARIABLE_INTEGER:
          result.add(node.resolveToInteger(delegate, logIndent));
          break;

        case ParseTree.VARIABLE_NUMBER:
          result.add(node.resolveToNumber(delegate, logIndent));
          break;

        case ParseTree.VARIABLE_ENUM:
        case ParseTree.VARIABLE_ARRAY:
        case ParseTree.VARIABLE_CHILD_ARRAY:
          LogUtils.log(
              ParseTreeResourceNode.class,
              Log.ERROR,
              "Cannot format string with type: " + node.getType());
          break;
      }
    }
    return result.toArray();
  }
}
