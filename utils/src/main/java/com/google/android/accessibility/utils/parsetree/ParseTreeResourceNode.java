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
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import androidx.annotation.IntDef;
import androidx.annotation.VisibleForTesting;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ParseTreeResourceNode extends ParseTreeNode {

  private static final String TAG = "ParseTreeResourceNode";

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
    if (type == null || name == null) {
      throw new IllegalArgumentException("Resource parameter is malformed: " + resourceName);
    }

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
        Object[] stringParamList = getParamList(mParams, 0, delegate, logIndent);
        String templateString = mResources.getString(mResourceId);
        return SpannedStringUtils.getSpannedFormattedString(templateString, stringParamList);

      case TYPE_PLURALS:
        if (mParams.isEmpty() || mParams.get(0).getType() != ParseTree.VARIABLE_INTEGER) {
          LogUtils.e(TAG, "First parameter for plurals must be the count");
          return "";
        }

        Object[] pluralParamList = getParamList(mParams, 1, delegate, logIndent);
        String templatePlural =
            mResources.getQuantityString(
                mResourceId, mParams.get(0).resolveToInteger(delegate, logIndent));
        return SpannedStringUtils.getSpannedFormattedString(templatePlural, pluralParamList);

      case TYPE_RESOURCE_ID:
        LogUtils.e(TAG, "Cannot resolve resource ID to string");
        return "";

      default:
        LogUtils.e(TAG, "Unknown resource type: " + mType);
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
          LogUtils.e(TAG, "Cannot format string with type: " + node.getType());
          break;
        default: // fall out
      }
    }
    return result.toArray();
  }

  /** The utility class provide ways to keep spans in template string. */
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  protected static class SpannedStringUtils {
    /**
     * Creates CharSequence from {@code templateString} and its {@code parameters}. And spans in
     * parameters are keep in result.
     *
     * @param templateString template string that may contains parameters with spans.
     * @param parameters object arrays that are supposed but not necessary to be Spanned. If it is
     *     Spanned, the spans are keep in result Spannable.
     * @return CharSequence that composed by formatted template string and parameters.
     */
    private static CharSequence getSpannedFormattedString(
        String templateString, Object[] parameters) {
      List<CharSequence> stringTypeList = new ArrayList<>();
      for (Object param : parameters) {
        if (param instanceof CharSequence) {
          stringTypeList.add((CharSequence) param);
        }
      }

      String formattedString = String.format(templateString, parameters);
      if (stringTypeList.isEmpty()) {
        return formattedString;
      }

      CharSequence expandableTemplate = toExpandableTemplate(templateString, parameters);
      try {
        // It will throw IllegalArgumentException if the template requests a value that was not
        // provided, or if more than 9 values are provided.
        return TextUtils.expandTemplate(
            expandableTemplate, stringTypeList.toArray(new CharSequence[stringTypeList.size()]));
      } catch (IllegalArgumentException exception) {
        LogUtils.e(
            TAG,
            "TextUtils.expandTemplate fail then try copySpansFromTemplateParameters."
                + " Exception=%s ",
            exception);
        // This is a fall-back method that may copy spans inaccurately
        return copySpansFromTemplateParameters(
            formattedString, stringTypeList.toArray(new CharSequence[stringTypeList.size()]));
      }
    }

    /**
     * Creates CharSequence from template string by its parameters. The template string will be
     * transformed to contain "^1"-style placeholder values dynamically to match the format of
     * {@link TextUtils#expandTemplate(CharSequence, CharSequence...)} and formatted by other
     * none-string type parameters.
     *
     * @param templateString template string that may contains parameters with strings.
     * @param parameters object arrays that are supposed but not necessary to be string. If it is
     *     string, the corresponding placeholder value will be changed to "^1"-style. If not string
     *     type, the placeholder is kept and adjust the index.
     * @return CharSequence that composed by template string with "^1"-style placeholder values.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    protected static CharSequence toExpandableTemplate(String templateString, Object[] parameters) {
      String expandTemplateString = templateString;
      List<Object> otherTypeList = new ArrayList<>();

      int spanTypeIndex = 1;
      int otherTypeIndex = 1;
      for (int i = 1; i <= parameters.length; i++) {
        Object param = parameters[i - 1];
        if (param instanceof CharSequence) {
          // replaces string type "%1$s" or "%s" to "^1" and so on.
          if (expandTemplateString.contains("%" + i + "$s")) {
            expandTemplateString =
                expandTemplateString.replace(("%" + i + "$s"), ("^" + spanTypeIndex));
          } else if (expandTemplateString.contains("%s")) {
            expandTemplateString = expandTemplateString.replaceFirst("%s", ("^" + spanTypeIndex));
          }
          spanTypeIndex++;
        } else {
          // keeps and assigns correct index to other type parameters
          expandTemplateString = expandTemplateString.replace(("%" + i), ("%" + otherTypeIndex));
          otherTypeList.add(param);
          otherTypeIndex++;
        }
      }
      return String.format(expandTemplateString, otherTypeList.toArray());
    }

    /**
     * Creates spannable from text that includes some Spanned. If a template parameter occurs
     * multiple times in the final text, this function copies the parameter's spans to the first
     * instance.
     *
     * @param text some text that potentially contains CharSequence parameters.
     * @param templateParameters CharSequence arrays that contains spans and need to be copied to
     *     result Spannable.
     * @return Spannable object that contains incoming text and spans from templateParameters.
     */
    private static Spannable copySpansFromTemplateParameters(
        String text, CharSequence[] templateParameters) {
      SpannableString result = new SpannableString(text);
      for (CharSequence params : templateParameters) {
        if (params instanceof Spanned) {
          int index = text.indexOf(params.toString());
          if (index >= 0) {
            copySpans(result, (Spanned) params, index);
          }
        }
      }
      return result;
    }

    /**
     * Utility that copies spans from {@code fromSpan} to {@code toSpan}.
     *
     * @param toSpan Spannable that is supposed to contain fromSpan.
     * @param fromSpan Spannable that could contain spans that would be copied to toSpan.
     * @param toSpanStartIndex Starting index of occurrence fromSpan in toSpan.
     */
    private static void copySpans(Spannable toSpan, Spanned fromSpan, int toSpanStartIndex) {
      if (toSpanStartIndex < 0 || toSpanStartIndex >= toSpan.length()) {
        LogUtils.e(
            TAG,
            "startIndex parameter (%d) is out of toSpan length %d",
            toSpanStartIndex,
            toSpan.length());
        return;
      }

      Object[] spans = fromSpan.getSpans(0, fromSpan.length(), Object.class);
      if (spans != null && spans.length > 0) {
        for (Object span : spans) {
          int spanStartIndex = fromSpan.getSpanStart(span);
          int spanEndIndex = fromSpan.getSpanEnd(span);
          if (spanStartIndex >= spanEndIndex) {
            continue;
          }
          int spanFlags = fromSpan.getSpanFlags(span);
          toSpan.setSpan(
              span,
              (toSpanStartIndex + spanStartIndex),
              (toSpanStartIndex + spanEndIndex),
              spanFlags);
        }
      }
    }
  }
}
