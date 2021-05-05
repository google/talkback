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
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.Pair;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Manages data for determining the output of events based on a JSON description. The output can be
 * based on a combination of the event type, variables, and constants.<br>
 * During initialization, errors will throw an InvalidStateException.<br>
 * During event handling, errors will be logged, and safe default behavior will be performed.<br>
 *
 * <h4>Types</h4>
 *
 * <ul>
 *   <li>Boolean: Can be true or false
 *   <li>Integer: Can be any integral value
 *   <li>Number: Double precision floating point value
 *   <li>String: A string that may have formatting information.
 *   <li>Enum: An integer that has a value from a list of named values.
 *   <li>Reference: Returns a variable delegate
 *   <li>Array: An array with each element being a String
 *   <li>Child Array: An array where each element returns a variable delegate.
 * </ul>
 *
 * <h4>Events</h4>
 *
 * <p>Each named event can be evaluated for each named output.
 *
 * <h4>Outputs</h4>
 *
 * <p>Each Output can be a Boolean, Integer, Number, Enum, or a String. It will be evaluated when
 * parseEventTo*() is called, and a default value will be returned if the output is not defined for
 * the event.
 *
 * <h4>Constants</h4>
 *
 * <p>Constants can be a Boolean, Integer, Number or a String. They are evaluated when the tree is
 * built, and cannot be changed.
 *
 * <h4>Variables</h4>
 *
 * <p>Variables can be any type, and are evaluated when an Event is evaluated. The value will be
 * requested from the VariableDelegate that is passed to the parseEventTo*() function.
 *
 * <h4>Functions</h4>
 *
 * <p>Functions can be provided to provide an arbitrary transformation of data. They should always
 * produce the same result within a call to parseEventTo*(), given the same VariableDelegate. The
 * function should be annotated with @UsedByReflection.
 *
 * <h3>JSON format</h3>
 *
 * <p>The JSON file should contain a map with two top level entries: "events" and "named_nodes"
 *
 * <h4>Events</h4>
 *
 * <p>"events" is a map with keys for each named event, each containing a map. This can have an
 * entry for each named output, and should map that output to the definition of a parse tree.
 *
 * <h4>Named Nodes</h4>
 *
 * <p>"named_nodes" is a map from an arbitrary name to a the definition of a parse tree.
 *
 * <h4>Parse Tree</h4>
 *
 * <p>A parse tree is a section of JSON that defines a tree of nodes that can be resolved to a
 * value. Each node can be a basic type (bool, int, float) that resolves directly to a value, or a
 * string, array or map that is evaluated into a node tree.
 *
 * <p>Strings are evaluated based on their context.
 *
 * <ul>
 *   <li>If they represent a string value, constants, named nodes, resources, variables and
 *       functions
 *   <li>will be evaluated in place, and all other characters will be left in place. Otherwise, they
 *   <li>will be evaluated to a boolean, integer, number, array, or child array as appropriate.
 * </ul>
 *
 * <p>JSON Arrays are evaluated to an Array node. Each entry is evaluated as a node to either a
 * string or array as appropriate. Strings are added to the array, and arrays have all their
 * elements added.
 *
 * <h5>Example</h5>
 *
 * <pre>{@code
 * {
 *   "node1": ["pie", "%node_2", "%node3"],
 *   "node2": ["cake", "rum"],
 *   "node3": "cola"
 * }
 *
 * }</pre>
 *
 * <p>node1 would evaluate to the array: ["pie, "cake", "rum", "cola"]
 *
 * <p>JSON Objects are evaluated based on their members. They should contain a member named the type
 * of node they represent.
 *
 * <p>If: An "if" node should have a condition, then a parse tree to evaluate based on the output of
 * that condition. At least one of "then" and "else" is required.
 *
 * <h5>Members</h5>
 *
 * <ul>
 *   <li>"if": The condition to evaluate. This should resolve to a boolean value.
 *   <li>"then": The parse tree to evaluate if the condition is true
 *   <li>"else": The parse tree to evaluate if the condition is false
 * </ul>
 *
 * <h5>Example</h5>
 *
 * <pre>{@code
 * {
 *   "if": "$variable.integer == 16",
 *   "then": "@string/true_string",
 *   "else": "%false_node"
 * }
 *
 * }</pre>
 *
 * <p>Fallback: A "fallback" node should have an array. Each node in the array will be evaluated in
 * order, and the first one that is not an empty string will be returned.
 *
 * <h5>Members</h5>
 *
 * <ul>
 *   <li>"fallback": The array of nodes to evaluate
 * </ul>
 *
 * <h5>Example</h5>
 *
 * <pre>{@code
 * {
 *   "fallback": [
 *     "%first_node",
 *     { "if": "%condition_node", "then": "$second_variable" },
 *     "@string/final_value"
 *   ]
 * }
 *
 * }</pre>
 *
 * <p>Join: A "join" node takes an array, and concatenates the strings.
 *
 * <h5>Members</h5>
 *
 * <ul>
 *   <li>"join": Array of values to join.
 *   <li>"separator": A string value that will be inserted between the values. Defaults to an empty
 *       string.
 *   <li>"prune_empty": A boolean value. If true, empty values in the array will be skipped.
 *       Defaults to false.
 * </ul>
 *
 * <h5>Example</h5>
 *
 * <pre>{@code
 * {
 *   "join": [ "@string/first_string", "%second_value", "$variable.third_value ],
 *   "separator: ", ",
 *   "prune_empty": true
 * }
 *
 * }</pre>
 *
 * <p>Switch: Takes an enum, and evaluates the node tree associated with its value.
 *
 * <h5>Members</h5>
 *
 * <ul>
 *   <li>"switch": An enum value. The case associated with it's value will be evaluated.
 *   <li>"cases": A map from each of the enum's possible values to the parse tree to evaluate.
 *   <li>"default": The parse tree to evaluate if the value isn't found in "cases". Optional.
 * </ul>
 *
 * <h5>Example</h5>
 *
 * <pre>{@code
 * {
 *   "switch": "$variable.enum",
 *   "cases": {
 *     "one": "@string/first_result",
 *     "two": "%second_result"
 *   },
 *   "default": "@string/not_found"
 * }
 *
 * }</pre>
 *
 * <p>For Reference: The provided variable must be of type "reference". The provided tree will be
 * evaluated using the delegate returned by the variable. The result will be the type of the
 * evaluated node.
 *
 * <h5>Members</h5>
 *
 * <ul>
 *   <li>"for_reference": A reference variable.
 *   <li>"evaluate": A parse tree to evaluate for the reference.
 * </ul>
 *
 * <h5>Example</h5>
 *
 * <pre>{@code
 * {
 *   "for_reference": "$variable.child",
 *   "evaluate": "$child.first_variable $child.second_variable"
 * }
 *
 * }</pre>
 *
 * <p>For Each Child: The provided variable must be of type "child array". For each element, the
 * provided tree will be evaluated using the delegate returned by the variable. The result will be
 * an array with the elements corresponding to each child in the original array.
 *
 * <h5>Members</h5>
 *
 * <ul>
 *   <li>"for_each_child": A child array.
 *   <li>"evaluate": A parse tree to evaluate for each element in the "for_each_child" array.
 * </ul>
 *
 * <h5>Example</h5>
 *
 * <pre>{@code
 * {
 *   "for_each_child": "$variable.child_array",
 *   "evaluate": "$child.first_variable $child.second_variable"
 * }
 *
 * }</pre>
 *
 * <h4>References</h4>
 *
 * <ul>
 *   <li>Constants: "#\w+", e.g. "#CONSTANT_NAME"
 *   <li>Named nodes: "%\w+", e.g. "%node_name"
 *   <li>Resources: "@(string|plurals|raw|array)/\w+" e.g. "@string/resource_name"
 *       <ul>
 *         <li>String resources can be formatted with a parameter list enclosed in parentheses, e.g
 *             "@string/formatted_string(123, 456, %node_param)"
 *       </ul>
 *   <li>Variables: "\$(\w+\.)*\w+" e.g. "$variable.name"
 *   <li>Functions: "\w[\w0-9]*" e.g. "FunctionName(123, 456, %node_param)"
 * </ul>
 */
public class ParseTree {

  private static final String TAG = "ParseTree";

  public ParseTree(Resources resources, String packageName) {
    mTreeInfo = new TreeInfo(resources, packageName);
  }

  /** An interface for supplying variables to the ParseTree */
  public interface VariableDelegate {
    public void cleanup();

    boolean getBoolean(int variableId);

    int getInteger(int variableId);

    double getNumber(int variableId);

    @Nullable
    CharSequence getString(int variableId);

    int getEnum(int variableId);

    @Nullable
    VariableDelegate getReference(int variableId);

    int getArrayLength(int variableId);

    @Nullable
    CharSequence getArrayStringElement(int variableId, int index);

    @Nullable
    VariableDelegate getArrayChildElement(int variableId, int index);
  }

  /** Enum representing the variable types. */
  @IntDef({
    VARIABLE_BOOL,
    VARIABLE_INTEGER,
    VARIABLE_NUMBER,
    VARIABLE_STRING,
    VARIABLE_ENUM,
    VARIABLE_REFERENCE,
    VARIABLE_ARRAY,
    VARIABLE_CHILD_ARRAY
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface VariableType {}

  static final int VARIABLE_BOOL = 0;
  static final int VARIABLE_INTEGER = 1;
  static final int VARIABLE_NUMBER = 2;
  static final int VARIABLE_STRING = 3;
  static final int VARIABLE_ENUM = 4;
  static final int VARIABLE_REFERENCE = 5;
  static final int VARIABLE_ARRAY = 6;
  static final int VARIABLE_CHILD_ARRAY = 7;

  @IntDef({
    OPERATOR_CLASS_NONE,
    OPERATOR_CLASS_PLUS,
    OPERATOR_CLASS_MULTIPLY,
    OPERATOR_CLASS_EQUALS,
    OPERATOR_CLASS_AND,
    OPERATOR_CLASS_TOKEN
  })
  @Retention(RetentionPolicy.SOURCE)
  @interface OperatorClass {}

  // These values are in decreasing order of precedence.
  private static final int OPERATOR_CLASS_TOKEN = 0;
  private static final int OPERATOR_CLASS_MULTIPLY = 1;
  private static final int OPERATOR_CLASS_PLUS = 2;
  private static final int OPERATOR_CLASS_EQUALS = 3;
  private static final int OPERATOR_CLASS_AND = 4;
  private static final int OPERATOR_CLASS_NONE = 5;

  @IntDef({
    OPERATOR_PLUS,
    OPERATOR_MINUS,
    OPERATOR_MULTIPLY,
    OPERATOR_DIVIDE,
    OPERATOR_EQUALS,
    OPERATOR_NEQUALS,
    OPERATOR_LT,
    OPERATOR_GT,
    OPERATOR_LE,
    OPERATOR_GE,
    OPERATOR_AND,
    OPERATOR_OR,
    OPERATOR_POW
  })
  @Retention(RetentionPolicy.SOURCE)
  @interface Operator {}

  static final int OPERATOR_PLUS = 1;
  static final int OPERATOR_MINUS = 2;
  static final int OPERATOR_MULTIPLY = 3;
  static final int OPERATOR_DIVIDE = 4;
  static final int OPERATOR_EQUALS = 5;
  static final int OPERATOR_NEQUALS = 6;
  static final int OPERATOR_LT = 7;
  static final int OPERATOR_GT = 8;
  static final int OPERATOR_LE = 9;
  static final int OPERATOR_GE = 10;
  static final int OPERATOR_AND = 11;
  static final int OPERATOR_OR = 12;
  static final int OPERATOR_POW = 13;

  private static class VariableInfo {
    VariableInfo(String inName, @VariableType int inVariableType) {
      name = inName;
      variableType = inVariableType;
      enumType = 0;
      id = 0;
    }

    VariableInfo(String inName, @VariableType int inVariableType, int inId) {
      name = inName;
      variableType = inVariableType;
      enumType = 0;
      id = inId;
    }

    VariableInfo(String inName, @VariableType int inVariableType, int inEnumType, int inId) {
      name = inName;
      variableType = inVariableType;
      enumType = inEnumType;
      id = inId;
    }

    final @VariableType int variableType;
    final int enumType;
    final int id;
    final String name;
  }

  private static class TreeInfo {
    private final Resources resources;
    private final String packageName;
    private final Map<String, ParseTreeNode> mNamedNodes = new HashMap<>();
    private final JSONObject mEventTree = new JSONObject();
    private final JSONObject mNodes = new JSONObject();
    private final Map<Integer, String> mEventNames = new HashMap<>();
    private final Map<Integer, String> mOutputNames = new HashMap<>();
    private final Map<String, VariableInfo> mOutputs = new HashMap<>();
    private final Map<String, ParseTreeNode> mConstants = new HashMap<>();
    private final Map<String, VariableInfo> mVariables = new HashMap<>();
    private final Map<Integer, Map<String, Integer>> mEnums = new HashMap<>();
    private final Map<String, Pair<Object, Method>> mFunctions = new HashMap<>();
    private final Set<String> mPendingNamedNodes = new HashSet<>();
    private final List<Pair<ParseTreeForEachChildNode, JSONObject>> mDeferredForEachChildNodes =
        new ArrayList<>();

    private TreeInfo(Resources resources, String packageName) {
      this.resources = resources;
      this.packageName = packageName;
    }
  }

  private final Map<Pair<Integer, Integer>, ParseTreeNode> mEvents = new HashMap<>();

  // Data used to build the parse tree.  It's released once the tree is built.
  private @Nullable TreeInfo mTreeInfo;

  private static final Pattern CONSTANT_PATTERN = Pattern.compile("#\\w+");
  private static final Pattern NODE_PATTERN = Pattern.compile("%\\w+");
  private static final Pattern RESOURCE_PATTERN =
      Pattern.compile("@(string|plurals|raw|array)/\\w+");
  private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$(\\w+\\.)*\\w+");
  private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d*\\.?\\d+");
  private static final Pattern OPERATOR_PATTERN = Pattern.compile("(\\|\\||&&|[!=<>]=|[-+/*<>^])");
  private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\\w[\\w0-9]*");

  private static final Pattern OPERATOR_CLASS_PLUS_PATTERN = Pattern.compile("[-+]");
  private static final Pattern OPERATOR_CLASS_MULTIPLY_PATTERN = Pattern.compile("[/*^]");
  private static final Pattern OPERATOR_CLASS_EQUALS_PATTERN = Pattern.compile("([!=<>]=|[<>])");
  private static final Pattern OPERATOR_CLASS_AND_PATTERN = Pattern.compile("(\\|\\||&&)");

  private static final String EVENT_FORMAT = "Getting output %s for event %s";

  /**
   * Creates an enum with the specified ID, and stores the mapping of its valid values.
   *
   * @param enumId ID of the enum. Must be unique.
   * @param values Mapping of enum value to the name of the value. This must uniquely map the two in
   *     either direction.
   */
  public void addEnum(int enumId, Map<Integer, String> values) {
    if (mTreeInfo != null) {
      TreeInfo treeInfo = mTreeInfo;
      if (treeInfo.mEnums.containsKey(enumId)) {
        throw new IllegalStateException("Can't add enum, ID " + enumId + " already in use");
      }

      Map<String, Integer> reverseEnum = new HashMap<>();
      for (Map.Entry<Integer, String> entry : values.entrySet()) {
        if (reverseEnum.containsKey(entry.getValue())) {
          throw new IllegalStateException(
              "Duplicate name: " + entry.getValue() + " in enum definition");
        }

        reverseEnum.put(entry.getValue(), entry.getKey());
      }

      treeInfo.mEnums.put(enumId, reverseEnum);
    } else {
      LogUtils.w(TAG, "Parse tree has been built and is immutable");
    }
  }

  /**
   * Assigns an id to a named event.
   *
   * @param eventName Name of the event.
   * @param eventId ID used to invoke the event. Must be unique.
   */
  public void addEvent(String eventName, int eventId) {
    if (mTreeInfo != null) {
      TreeInfo treeInfo = mTreeInfo;

      if (treeInfo.mEventNames.containsKey(eventId)) {
        throw new IllegalStateException(
            "Can't add event: " + eventName + ", ID " + eventId + " already in use");
      }

      treeInfo.mEventNames.put(eventId, eventName);
    } else {
      LogUtils.w(TAG, "Parse tree has been built and is immutable");
    }
  }

  /**
   * Assigns an id to a named output with a boolean value.
   *
   * @param outputName Name of the output.
   * @param outputId ID used to parse the output. Must be unique.
   */
  public void addBooleanOutput(String outputName, int outputId) {
    addOutput(outputName, new VariableInfo(outputName, VARIABLE_BOOL, outputId));
  }

  /**
   * Assigns an id to a named output with a integral value.
   *
   * @param outputName Name of the output.
   * @param outputId ID used to parse the output. Must be unique.
   */
  public void addIntegerOutput(String outputName, int outputId) {
    addOutput(outputName, new VariableInfo(outputName, VARIABLE_INTEGER, outputId));
  }

  /**
   * Assigns an id to a named output with a floating point value.
   *
   * @param outputName Name of the output.
   * @param outputId ID used to parse the output. Must be unique.
   */
  public void addNumberOutput(String outputName, int outputId) {
    addOutput(outputName, new VariableInfo(outputName, VARIABLE_NUMBER, outputId));
  }

  /**
   * Assigns an id to a named output with a string value.
   *
   * @param outputName Name of the output.
   * @param outputId ID used to parse the output. Must be unique.
   */
  public void addStringOutput(String outputName, int outputId) {
    addOutput(outputName, new VariableInfo(outputName, VARIABLE_STRING, outputId));
  }

  /**
   * Assigns an id to a named output with an enum value.
   *
   * @param outputName Name of the output.
   * @param outputId ID used to parse the output. Must be unique.
   */
  public void addEnumOutput(String outputName, int outputId, int enumType) {
    addOutput(outputName, new VariableInfo(outputName, VARIABLE_ENUM, enumType, outputId));
  }

  /**
   * Assigns an id to a named variable of Boolean type.
   *
   * @param varName Name of the variable.
   * @param varId ID used to identify the variable. Must be unique.
   */
  public void addBooleanVariable(String varName, int varId) {
    addVariable(varName, new VariableInfo(varName, VARIABLE_BOOL, varId));
  }

  /**
   * Assigns an id to a named variable of Integer type.
   *
   * @param varName Name of the variable.
   * @param varId ID used to identify the variable. Must be unique.
   */
  public void addIntegerVariable(String varName, int varId) {
    addVariable(varName, new VariableInfo(varName, VARIABLE_INTEGER, varId));
  }

  /**
   * Assigns an id to a named variable of Number type.
   *
   * @param varName Name of the variable.
   * @param varId ID used to identify the variable. Must be unique.
   */
  public void addNumberVariable(String varName, int varId) {
    addVariable(varName, new VariableInfo(varName, VARIABLE_NUMBER, varId));
  }

  /**
   * Assigns an id to a named variable of String type.
   *
   * @param varName Name of the variable.
   * @param varId ID used to identify the variable. Must be unique.
   */
  public void addStringVariable(String varName, int varId) {
    addVariable(varName, new VariableInfo(varName, VARIABLE_STRING, varId));
  }

  /**
   * Assigns an id to a named variable of Enum type.
   *
   * @param varName Name of the variable.
   * @param varId ID used to identify the variable. Must be unique.
   */
  public void addEnumVariable(String varName, int varId, int enumType) {
    addVariable(varName, new VariableInfo(varName, VARIABLE_ENUM, enumType, varId));
  }

  /**
   * Assigns an id to a named variable of reference type.
   *
   * @param varName Name of the variable.
   * @param varId ID used to identify the variable. Must be unique.
   */
  public void addReferenceVariable(String varName, int varId) {
    addVariable(varName, new VariableInfo(varName, VARIABLE_REFERENCE, varId));
  }

  /**
   * Assigns an id to a named variable of Array type.
   *
   * @param varName Name of the variable.
   * @param varId ID used to identify the variable. Must be unique.
   */
  public void addArrayVariable(String varName, int varId) {
    addVariable(varName, new VariableInfo(varName, VARIABLE_ARRAY, varId));
  }

  /**
   * Assigns an id to a named variable of Child Array type.
   *
   * @param varName Name of the variable.
   * @param varId ID used to identify the variable. Must be unique.
   */
  public void addChildArrayVariable(String varName, int varId) {
    addVariable(varName, new VariableInfo(varName, VARIABLE_CHILD_ARRAY, varId));
  }

  public void addFunction(String name, Object delegate) {
    Method method = null;
    for (Method currentMethod : delegate.getClass().getDeclaredMethods()) {
      if (currentMethod.getName().equals(name)) {
        if (method != null) {
          throw new IllegalStateException(
              "Function name '" + name + "' is ambiguous for delegate: " + delegate);
        }
        method = currentMethod;
      }
    }

    if (method == null) {
      throw new IllegalStateException(
          "No matching method name, or method wasn't annotated with @UsedByReflection(): " + name);
    }

    addFunction(name, delegate, method);
  }

  public void addFunction(String name, Object delegate, Method method) {
    if (mTreeInfo == null) {
      LogUtils.w(TAG, "Parse tree has been built and is immutable");
      return;
    }

    mTreeInfo.mFunctions.put(name, Pair.create(delegate, method));
  }

  /**
   * Merges a JSON tree into the parse tree definition. This overwrites any existing nodes or events
   * with the new one in definition.
   *
   * @param definition Contains the JSON representing the parse tree data to be added.
   */
  public void mergeTree(JSONObject definition) {
    if (mTreeInfo == null) {
      LogUtils.w(TAG, "Parse tree has been built and is immutable");
      return;
    }

    try {
      TreeInfo treeInfo = mTreeInfo;
      if (definition.has("events")) {
        JSONObject events = definition.getJSONObject("events");
        Iterator<String> eventNames = events.keys();
        while (eventNames.hasNext()) {
          String eventName = eventNames.next();

          // Unknown events are not allowed.
          if (!treeInfo.mEventNames.containsValue(eventName)) {
            throw new IllegalStateException("Unknown event name: " + eventName);
          }

          // Ensure there is an event registered for |eventName|
          JSONObject event;
          if (treeInfo.mEventTree.has(eventName)) {
            event = treeInfo.mEventTree.getJSONObject(eventName);
          } else {
            event = new JSONObject();
            treeInfo.mEventTree.put(eventName, event);
          }

          // Put the new event's outputs into the tree.
          JSONObject newEvent = events.getJSONObject(eventName);
          Iterator<String> outputs = newEvent.keys();
          while (outputs.hasNext()) {
            String outputName = outputs.next();
            // Unknown outputs are not allowed.
            if (!treeInfo.mOutputNames.containsValue(outputName)) {
              throw new IllegalStateException("Unknown output name: " + outputName);
            }
            event.put(outputName, newEvent.get(outputName));
          }
        }
      }
      if (definition.has("named_nodes")) {
        JSONObject nodes = definition.getJSONObject("named_nodes");
        Iterator<String> nodeNames = nodes.keys();
        while (nodeNames.hasNext()) {
          String nodeName = nodeNames.next();
          treeInfo.mNodes.put(nodeName, nodes.get(nodeName));
        }
      }
    } catch (JSONException e) {
      throw new IllegalStateException(e.toString());
    }
  }

  public void setConstantBool(String name, boolean value) {
    if (mTreeInfo != null) {
      mTreeInfo.mConstants.put(name, new ParseTreeBooleanConstantNode(value));
    } else {
      LogUtils.w(TAG, "Parse tree has been built and is immutable");
    }
  }

  public void setConstantInteger(String name, int value) {
    if (mTreeInfo != null) {
      mTreeInfo.mConstants.put(name, new ParseTreeIntegerConstantNode(value));
    } else {
      LogUtils.w(TAG, "Parse tree has been built and is immutable");
    }
  }

  public void setConstantNumber(String name, double value) {
    if (mTreeInfo != null) {
      mTreeInfo.mConstants.put(name, new ParseTreeNumberConstantNode(value));
    } else {
      LogUtils.w(TAG, "Parse tree has been built and is immutable");
    }
  }

  public void setConstantEnum(String name, int enumType, int value) {
    if (mTreeInfo != null) {
      mTreeInfo.mConstants.put(name, new ParseTreeIntegerConstantNode(value, enumType));
    } else {
      LogUtils.w(TAG, "Parse tree has been built and is immutable");
    }
  }

  public void setConstantString(String name, CharSequence value) {
    if (mTreeInfo != null) {
      mTreeInfo.mConstants.put(name, new ParseTreeStringConstantNode(value));
    } else {
      LogUtils.w(TAG, "Parse tree has been built and is immutable");
    }
  }

  /**
   * Build the parse tree. Once this function is called, the parse tree can no longer be modified.
   * The only valid functions to call then are parseEventTo*
   */
  // TODO: incompatible types in argument.
  @SuppressWarnings("nullness:argument.type.incompatible")
  public void build() {
    if (mTreeInfo == null) {
      LogUtils.w(TAG, "Parse tree has been built and is immutable");
      return;
    }

    TreeInfo treeInfo = mTreeInfo;
    mTreeInfo = null;
    for (int eventId : treeInfo.mEventNames.keySet()) {
      for (String outputName : treeInfo.mOutputs.keySet()) {
        VariableInfo outputInfo = treeInfo.mOutputs.get(outputName);
        String eventName = treeInfo.mEventNames.get(eventId);
        JSONObject eventDefinition = treeInfo.mEventTree.optJSONObject(eventName);
        if (eventDefinition != null && eventDefinition.has(outputName)) {
          switch (outputInfo.variableType) {
            case ParseTree.VARIABLE_BOOL:
            case ParseTree.VARIABLE_INTEGER:
            case ParseTree.VARIABLE_NUMBER:
            case ParseTree.VARIABLE_ENUM:
            case ParseTree.VARIABLE_STRING:
              mEvents.put(
                  Pair.create(eventId, outputInfo.id),
                  new ParseTreeCommentNode(
                      createParseTreeFromObject(
                          treeInfo, eventDefinition.opt(outputName), outputInfo),
                      EVENT_FORMAT,
                      new Object[] {outputName, eventName}));
              break;
            case ParseTree.VARIABLE_REFERENCE:
            case ParseTree.VARIABLE_ARRAY:
            case ParseTree.VARIABLE_CHILD_ARRAY:
            default:
              throw new IllegalStateException(
                  "Bad output type for: " + eventName + ":" + outputName);
          }
        }
      }
    }

    while (!treeInfo.mDeferredForEachChildNodes.isEmpty()) {
      Pair<ParseTreeForEachChildNode, JSONObject> current =
          treeInfo.mDeferredForEachChildNodes.remove(0);

      current.first.setFunction(
          createParseTreeFromObject(
              treeInfo,
              current.second.opt("evaluate"),
              new VariableInfo("function...", VARIABLE_STRING)));
    }
  }

  /**
   * Evaluates the specified event, returning the result as a boolean.
   *
   * @param eventId ID of the event to evaluate.
   * @param outputId Which of the event's outputs to evaluate.
   * @param defaultValue The value to return if the event is undefined.
   * @param delegate The delegate to retrieve variables from
   * @return The result of the event, coerced to a bool.
   */
  public boolean parseEventToBool(
      int eventId, int outputId, boolean defaultValue, VariableDelegate delegate) {
    ParseTreeNode eventNode = mEvents.get(Pair.create(eventId, outputId));
    if (eventNode != null) {
      return eventNode.resolveToBoolean(delegate, "");
    }
    return defaultValue;
  }

  /**
   * Evaluates the specified event, returning the result as an integer.
   *
   * @param eventId ID of the event to evaluate.
   * @param outputId Which of the event's outputs to evaluate.
   * @param defaultValue The value to return if the event is undefined.
   * @param delegate The delegate to retrieve variables from
   * @return The result of the event, coerced to an integer.
   */
  public int parseEventToInteger(
      int eventId, int outputId, int defaultValue, VariableDelegate delegate) {
    ParseTreeNode eventNode = mEvents.get(Pair.create(eventId, outputId));
    if (eventNode != null) {
      return eventNode.resolveToInteger(delegate, "");
    }
    return defaultValue;
  }

  /**
   * Evaluates the specified event, returning the result as a number.
   *
   * @param eventId ID of the event to evaluate.
   * @param outputId Which of the event's outputs to evaluate.
   * @param defaultValue The value to return if the event is undefined.
   * @param delegate The delegate to retrieve variables from
   * @return The result of the event, coerced to a number.
   */
  public double parseEventToNumber(
      int eventId, int outputId, double defaultValue, VariableDelegate delegate) {
    ParseTreeNode eventNode = mEvents.get(Pair.create(eventId, outputId));
    if (eventNode != null) {
      return eventNode.resolveToNumber(delegate, "");
    }
    return defaultValue;
  }

  /**
   * Evaluates the specified event, building a string as the output.
   *
   * @param eventId ID of the event to evaluate.
   * @param outputId Which of the event's outputs to evaluate.
   * @param delegate The delegate to retrieve variables from
   * @return A string built from evaluating the event's output definition.
   */
  public @Nullable CharSequence parseEventToString(
      int eventId, int outputId, VariableDelegate delegate) {
    ParseTreeNode eventNode = mEvents.get(Pair.create(eventId, outputId));
    if (eventNode != null) {
      return eventNode.resolveToString(delegate, "");
    }
    return null;
  }

  /**
   * Evaluates the specified event, mapping the output to an enumerated value.
   *
   * @param eventId ID of the event to evaluate.
   * @param outputId Which of the event's outputs to evaluate.
   * @param defaultValue The value to return if the mapping is unsuccessful.
   * @param delegate The delegate to retrieve variables from
   * @return An integer value corresponding to the value mapped the the string value of the output,
   *     or defaultValue if no mapping exists.
   */
  public int parseEventToEnum(
      int eventId, int outputId, int defaultValue, VariableDelegate delegate) {
    ParseTreeNode eventNode = mEvents.get(Pair.create(eventId, outputId));
    if (eventNode != null) {
      return eventNode.resolveToInteger(delegate, "");
    }
    return defaultValue;
  }

  private void addVariable(String varName, VariableInfo varInfo) {
    if (mTreeInfo != null) {
      TreeInfo treeInfo = mTreeInfo;
      if (treeInfo.mVariables.containsKey(varName)) {
        throw new IllegalStateException("Can't add variable: " + varName + ", name already in use");
      }

      for (VariableInfo info : treeInfo.mVariables.values()) {
        if (varInfo.id == info.id) {
          throw new IllegalStateException(
              "Can't add variable: " + varName + ", ID " + varInfo.id + " already in use");
        }
      }

      treeInfo.mVariables.put(varName, varInfo);
    } else {
      LogUtils.w(TAG, "Parse tree has been built and is immutable");
    }
  }

  private void addOutput(String outputName, VariableInfo type) {
    if (mTreeInfo != null) {
      TreeInfo treeInfo = mTreeInfo;
      if (treeInfo.mOutputs.containsKey(outputName)) {
        throw new IllegalStateException("Can't add output: " + outputName + " already in use");
      }

      if (treeInfo.mOutputNames.containsKey(type.id)) {
        throw new IllegalStateException(
            "Can't add output: " + outputName + ", ID " + type.id + " already in use");
      }

      treeInfo.mOutputNames.put(type.id, outputName);
      treeInfo.mOutputs.put(outputName, type);
    } else {
      LogUtils.w(TAG, "Parse tree has been built and is immutable");
    }
  }

  /**
   * Creates a ParseTreeNode from an object in the JSON parse tree definition.
   *
   * @param value The value in the JSON tree that represents this node.
   * @param hint The type of value that is expected to be returned by this node.
   * @return A ParseTreeNode that will follow the logic defined by |value| when evaluated.
   */
  private static ParseTreeNode createParseTreeFromObject(
      TreeInfo treeInfo, Object value, VariableInfo hint) {
    if (value instanceof Boolean) {
      // Boolean values are always constants.
      return new ParseTreeBooleanConstantNode((Boolean) value);
    } else if (value instanceof Double) {
      // Double values are always Number constants
      return new ParseTreeNumberConstantNode((Double) value);
    } else if (value instanceof Integer) {
      // Integer values are always constants.
      return new ParseTreeIntegerConstantNode((Integer) value);
    } else if (value instanceof String) {
      // Strings can be evaluated differently depending on the expected value.
      switch (hint.variableType) {
        case ParseTree.VARIABLE_BOOL:
        case ParseTree.VARIABLE_INTEGER:
        case ParseTree.VARIABLE_NUMBER:
          {
            // Booleans and number types imply that the string is a statement that should be
            // evaluated.
            ParseTreeNode result =
                createParseTreeFromStatement(
                    treeInfo, (String) value, 0, ((String) value).length());
            return new ParseTreeCommentNode(result, "Evaluating: %s", new Object[] {value});
          }

        case ParseTree.VARIABLE_ENUM:
          // Enums imply that the value of the string should be looked up in the enum
          // definition.
          return createEnumParseTreeFromString(treeInfo, (String) value, hint.enumType);

        case ParseTree.VARIABLE_STRING:
          {
            // Strings should have variables, constants, nodes and resources expanded.
            ParseTreeNode result = createStringParseTreeFromString(treeInfo, (String) value);
            return new ParseTreeCommentNode(result, "Evaluating: %s", new Object[] {value});
          }

        case ParseTree.VARIABLE_ARRAY:
          // If it's an array, this value will either be expanded to an array, or added as
          // a string.
          return createArrayChildParseTreeFromString(treeInfo, (String) value);

        case ParseTree.VARIABLE_REFERENCE:
        case ParseTree.VARIABLE_CHILD_ARRAY:
        default:
          throw new IllegalStateException(
              "String cannot be expanded to type: " + hint.variableType);
      }
    } else if (value instanceof JSONArray) {
      // Arrays should create a node that each element is merged into.
      JSONArray jsonArray = (JSONArray) value;
      int length = jsonArray.length();
      List<ParseTreeNode> children = new ArrayList<>();
      for (int i = 0; i < length; i++) {
        Object childTree = jsonArray.opt(i);
        if (childTree != null) {
          children.add(
              createParseTreeFromObject(
                  treeInfo, childTree, new VariableInfo("array...", VARIABLE_ARRAY)));
        } else if (i != length - 1) {
          // We allow the last element in an array to be null, since that just means there is a
          // trailing comma.
          throw new IllegalStateException("Array contains a null element at: " + i);
        }
      }
      return new ParseTreeArrayNode(children);
    } else if (value instanceof JSONObject) {
      // JSONObject implies a function of some sort.
      JSONObject jsonObject = (JSONObject) value;
      if (jsonObject.has("if")) {
        return createIfParseTreeFromObject(treeInfo, jsonObject, hint);
      } else if (jsonObject.has("join")) {
        return createJoinParseTreeFromObject(treeInfo, jsonObject);
      } else if (jsonObject.has("fallback")) {
        return createFallbackParseTreeFromObject(treeInfo, jsonObject);
      } else if (jsonObject.has("switch")) {
        return createSwitchParseTreeFromObject(treeInfo, jsonObject, hint);
      } else if (jsonObject.has("for_reference")) {
        return createForReferenceParseTreeNodeFromObject(treeInfo, jsonObject, hint);
      } else if (jsonObject.has("for_each_child")) {
        return createForEachChildParseTreeNodeFromObject(treeInfo, jsonObject);
      } else {
        StringBuilder keys = new StringBuilder();
        Iterator<String> keyIter = jsonObject.keys();
        while (keyIter.hasNext()) {
          keys.append(" ");
          keys.append(keyIter.next());
        }

        throw new IllegalStateException("Unknown function: " + keys);
      }
    } else {
      throw new IllegalStateException("Unknown type: " + value);
    }
  }

  private static ParseTreeNode createArrayParseTreeFromObject(TreeInfo treeInfo, Object value) {
    // If the object already evaluates to an array, return it.  Otherwise, it needs to be
    // wrapped.
    ParseTreeNode child =
        createParseTreeFromObject(treeInfo, value, new VariableInfo("array...", VARIABLE_ARRAY));
    if (child.getType() == VARIABLE_ARRAY) {
      return child;
    } else {
      List<ParseTreeNode> children = new ArrayList<>();
      children.add(child);
      return new ParseTreeArrayNode(children);
    }
  }

  private static ParseTreeNode createParseTreeFromStatement(
      TreeInfo treeInfo, String value, int start, int end) {
    ParseTreeNode lvalue = null;

    int offset = start;
    // Initialize to OPERATOR_PLUS to avoid a compile error.  The default value should
    // never be read.
    @Operator int operator = OPERATOR_PLUS;
    while (offset < end) {
      offset = skipWhitespace(value, offset);
      char current = value.charAt(offset);

      if (lvalue == null) {
        if (current == '!') {
          // Currently only applicable to "not" operator.
          offset++;

          int statementEnd = findStatementEnd(value, offset, OPERATOR_CLASS_TOKEN);
          ParseTreeNode childNode =
              createParseTreeFromStatement(treeInfo, value, offset, statementEnd);
          if (!childNode.canCoerceTo(VARIABLE_BOOL)) {
            throw new IllegalStateException(
                String.format(
                    "Cannot coerce not node child to bool (%d, %s): \"%s\"",
                    offset, variableTypeToString(childNode.getType()), value));
          }
          lvalue = new ParseTreeNotNode(childNode);
          offset = statementEnd;
        } else if (current == '$') {
          // Variable follows.
          int tokenEnd = findTokenEnd(value, offset);
          String name = value.substring(offset + 1, tokenEnd);
          lvalue = createVariableNode(treeInfo, name);
          offset = tokenEnd;
        } else if (current == '#') {
          // Constant follows.
          int tokenEnd = findTokenEnd(value, offset);
          String name = value.substring(offset + 1, tokenEnd);
          lvalue = createConstantNode(treeInfo, name);
          offset = tokenEnd;
        } else if (current == '@') {
          // Resource follows.
          int tokenEnd = findTokenEnd(value, offset);
          // Since this is handled as an integer, we ignore any parameters.
          lvalue =
              new ParseTreeResourceNode(
                  treeInfo.resources, value.substring(offset, tokenEnd), treeInfo.packageName);
          offset = tokenEnd;
        } else if (current == '%') {
          int tokenEnd = findTokenEnd(value, offset);
          String variableText = value.substring(offset + 1, tokenEnd);
          lvalue =
              getOrCreateNamedNode(
                  treeInfo, variableText, new VariableInfo(variableText, VARIABLE_NUMBER));
          offset = tokenEnd;
        } else if (isNumberStart(value, offset)) {
          int numberEnd = findNumberEnd(value, offset);
          String valueString = value.substring(offset, numberEnd);
          if (valueString.indexOf('.') == -1) {
            lvalue = new ParseTreeIntegerConstantNode(Integer.parseInt(valueString));
          } else {
            lvalue = new ParseTreeNumberConstantNode(Double.parseDouble(valueString));
          }
          offset = numberEnd;
        } else if (isFunctionStart(current)) {
          int tokenEnd = findTokenEnd(value, offset);
          if (tokenEnd >= end || value.charAt(tokenEnd) != '(') {
            throw new IllegalStateException("Function is missing parameter list: " + value);
          }
          int paramEnd = findMatchingParen(value, tokenEnd);
          lvalue = createFunctionNode(treeInfo, value, offset, tokenEnd, paramEnd);
          offset = paramEnd;
        } else if (current == '(') {
          int statementEnd = findMatchingParen(value, offset);
          lvalue = createParseTreeFromStatement(treeInfo, value, offset + 1, statementEnd - 1);
          offset = statementEnd;
        } else {
          throw new IllegalStateException("Cannot parse statement: " + value);
        }
      } else {
        // Since lvalue in not null, a full loop has been completed and operator should have
        // been set.
        ParseTreeNode rvalue;

        if ((operator == OPERATOR_EQUALS || operator == OPERATOR_NEQUALS)
            && lvalue.getType() == VARIABLE_ENUM
            && current == '\'') {
          // If an enum is being directly compared to a string constant, convert the
          // string to an enum value.
          int stringEnd = findStringEnd(value, offset);
          String enumValue = getString(value, offset, stringEnd);
          rvalue = createEnumParseTreeFromString(treeInfo, enumValue, lvalue.getEnumType());
          offset = stringEnd;
        } else {
          // Evaluate the next portion of the string as an rvalue.
          int statementEnd = findStatementEnd(value, offset, getOperatorClass(operator));
          rvalue = createParseTreeFromStatement(treeInfo, value, offset, statementEnd);
          offset = statementEnd;
        }

        if (!isValidLvalueType(lvalue.getType())) {
          throw new IllegalStateException(
              "Invalid lvalue type: " + variableTypeToString(lvalue.getType()));
        }

        if (!isValidRvalueType(rvalue.getType())) {
          throw new IllegalStateException(
              "Invalid rvalue type: " + variableTypeToString(rvalue.getType()));
        }

        lvalue = new ParseTreeOperatorNode(operator, lvalue, rvalue);
      }

      offset = skipWhitespace(value, offset);

      if (offset >= end) {
        break;
      }

      current = value.charAt(offset);

      if (!isOperatorStart(current)) {
        throw new IllegalStateException("Invalid operator in statement: " + value);
      }

      int operatorEnd = findOperatorEnd(value, offset);
      operator = getOperator(value.substring(offset, operatorEnd));
      offset = operatorEnd;
    }

    if (lvalue == null) {
      if (end == 0) {
        lvalue = new ParseTreeCommentNode(null, "Empty Node", true);

      } else {
        throw new IllegalStateException("Could not parse statement: " + value);
      }
    }
    return lvalue;
  }

  private static ParseTreeNode createStringParseTreeFromString(TreeInfo treeInfo, String value) {
    List<ParseTreeNode> parts = new ArrayList<>();

    int offset = 0;
    final int valueLength = value.length();
    while (offset < valueLength) {
      int nextStart = findNextTokenStartInString(value, offset);
      if (nextStart > 0) {
        // If there is anything before the first token, add it as a string constant.
        parts.add(new ParseTreeStringConstantNode(value.substring(offset, nextStart)));
      }

      offset = nextStart;
      if (offset < valueLength) {
        // Find the next function, variable, constant, node, or resource in the string and
        // add a node for it.
        int tokenEnd = findTokenEnd(value, offset);
        char current = value.charAt(offset);
        if (isFunctionStart(current)) {
          if (tokenEnd >= valueLength || value.charAt(tokenEnd) != '(') {
            throw new IllegalStateException("Function is missing parameter list: " + value);
          }
          int paramEnd = findMatchingParen(value, tokenEnd);
          parts.add(createFunctionNode(treeInfo, value, offset, tokenEnd, paramEnd));
          offset = paramEnd;
        } else {
          switch (current) {
            case '$':
              parts.add(createVariableNode(treeInfo, value.substring(offset + 1, tokenEnd)));
              offset = tokenEnd;
              break;
            case '#':
              parts.add(createConstantNode(treeInfo, value.substring(offset + 1, tokenEnd)));
              offset = tokenEnd;
              break;
            case '@':
              ParseTreeResourceNode node =
                  new ParseTreeResourceNode(
                      treeInfo.resources, value.substring(offset, tokenEnd), treeInfo.packageName);
              offset = tokenEnd;
              if (offset < valueLength && value.charAt(offset) == '(') {
                int paramEnd = findMatchingParen(value, offset);
                node.addParams(createParamListFromString(treeInfo, value, offset));
                offset = paramEnd;
              }
              parts.add(node);
              break;
            case '%':
              {
                String variableText = value.substring(offset + 1, tokenEnd);
                parts.add(
                    getOrCreateNamedNode(
                        treeInfo, variableText, new VariableInfo(variableText, VARIABLE_STRING)));
                offset = tokenEnd;
              }
              break;
            default: // fall out
          }
        }
      }
    }

    if (parts.isEmpty()) {
      return new ParseTreeStringConstantNode("");
    } else if (parts.size() == 1) {
      return parts.get(0);
    } else {
      ParseTreeNode arrayNode = new ParseTreeArrayNode(parts);
      return new ParseTreeJoinNode(arrayNode, null, false);
    }
  }

  private static ParseTreeNode createEnumParseTreeFromString(
      TreeInfo treeInfo, String value, int enumType) {
    if (TextUtils.isEmpty(value)) {
      throw new IllegalStateException("Empty value is invalid for enum");
    }

    if (value.charAt(0) == '$') {
      return createVariableNode(treeInfo, value.substring(1));
    } else if (value.charAt(0) == '%') {
      String variableText = value.substring(1);
      return getOrCreateNamedNode(
          treeInfo,
          variableText,
          new VariableInfo(variableText, VARIABLE_ENUM, enumType, 0 /* inId */));
    } else {
      Map<String, Integer> enumMap = treeInfo.mEnums.get(enumType);
      if (enumMap == null) {
        throw new IllegalStateException("Unknown enum type: " + enumType);
      }

      Integer enumValue = enumMap.get(value);
      if (enumValue == null) {
        throw new IllegalStateException("Invalid value for enum(" + enumType + ") type: " + value);
      }

      return new ParseTreeIntegerConstantNode(enumValue);
    }
  }

  private static ParseTreeNode createArrayChildParseTreeFromString(
      TreeInfo treeInfo, String value) {
    if (CONSTANT_PATTERN.matcher(value).matches()) {
      return createConstantNode(treeInfo, value.substring(1));
    } else if (NODE_PATTERN.matcher(value).matches()) {
      String variableText = value.substring(1);
      return getOrCreateNamedNode(
          treeInfo, variableText, new VariableInfo(variableText, VARIABLE_ARRAY));
    } else if (VARIABLE_PATTERN.matcher(value).matches()) {
      return createVariableNode(treeInfo, value.substring(1));
    } else {
      return createStringParseTreeFromString(treeInfo, value);
    }
  }

  private static final String IF_FORMAT = "if (%s):";

  private static ParseTreeNode createIfParseTreeFromObject(
      TreeInfo treeInfo, JSONObject value, VariableInfo hint) {
    Object ifDefinition = value.opt("if");
    Object thenDefinition = value.opt("then");
    Object elseDefinition = value.opt("else");

    if (thenDefinition == null && elseDefinition == null) {
      throw new IllegalStateException("'if' requires either 'then' or 'else'");
    }

    // TODO: incompatible types in argument.
    @SuppressWarnings("nullness:argument.type.incompatible")
    ParseTreeNode ifNode =
        createParseTreeFromObject(treeInfo, ifDefinition, new VariableInfo("if...", VARIABLE_BOOL));
    ParseTreeNode onTrue = null;
    ParseTreeNode onFalse = null;
    if (thenDefinition != null) {
      onTrue = createParseTreeFromObject(treeInfo, thenDefinition, hint);
    }

    if (elseDefinition != null) {
      onFalse = createParseTreeFromObject(treeInfo, elseDefinition, hint);
    }

    ParseTreeNode result = new ParseTreeIfNode(ifNode, onTrue, onFalse);

    String ifString = ifDefinition instanceof String ? (String) ifDefinition : "node";
    return new ParseTreeCommentNode(result, IF_FORMAT, new Object[] {ifString});
  }

  // TODO: incompatible types in argument.
  @SuppressWarnings("nullness:argument.type.incompatible")
  private static ParseTreeNode createJoinParseTreeFromObject(TreeInfo treeInfo, JSONObject value) {
    Object joinDefinition = value.opt("join");
    String separator = value.optString("separator", ", ");
    boolean pruneEmpty = value.optBoolean("prune_empty", true);

    return new ParseTreeJoinNode(
        createArrayParseTreeFromObject(treeInfo, joinDefinition), separator, pruneEmpty);
  }

  private static final String FALLBACK_FORMAT = "fallback (%d items):";

  private static ParseTreeNode createFallbackParseTreeFromObject(
      TreeInfo treeInfo, JSONObject value) {
    JSONArray fallbackDefinition = value.optJSONArray("fallback");
    if (fallbackDefinition == null) {
      throw new IllegalStateException("'fallback' must be an Array");
    }

    List<ParseTreeNode> children = new ArrayList<>();
    int length = fallbackDefinition.length();
    for (int i = 0; i < length; i++) {
      children.add(
          createParseTreeFromObject(
              treeInfo,
              fallbackDefinition.opt(i),
              new VariableInfo("fallback...", VARIABLE_STRING)));
    }
    ParseTreeNode result = new ParseTreeFallbackNode(children);
    return new ParseTreeCommentNode(result, FALLBACK_FORMAT, new Object[] {length});
  }

  private static final String SWITCH_FORMAT = "switch (%s):";
  private static final String CASE_FORMAT = "case %s:";

  private static ParseTreeNode createSwitchParseTreeFromObject(
      TreeInfo treeInfo, JSONObject value, VariableInfo hint) {
    String switchVariable = value.optString("switch");
    JSONObject casesDefinition = value.optJSONObject("cases");
    Object defaultDefinition = value.opt("default");

    if (switchVariable == null) {
      throw new IllegalStateException("'switch' condition is missing condition: " + value);
    }

    if (casesDefinition == null) {
      throw new IllegalStateException("'switch' requires valid cases");
    }

    ParseTreeNode switchNode;
    if (VARIABLE_PATTERN.matcher(switchVariable).matches()) {
      String switchVariableName = switchVariable.substring(1);
      VariableInfo variableInfo = treeInfo.mVariables.get(switchVariableName);
      if (variableInfo == null) {
        throw new IllegalStateException("Unknown variable: " + switchVariable);
      }

      if (variableInfo.variableType != VARIABLE_ENUM
          || !treeInfo.mEnums.containsKey(variableInfo.enumType)) {
        throw new IllegalStateException("'switch' requires a valid enum: " + switchVariable);
      }

      switchNode = createVariableNode(treeInfo, switchVariableName);
    } else if (CONSTANT_PATTERN.matcher(switchVariable).matches()) {
      switchNode = createConstantNode(treeInfo, switchVariable.substring(1));
    } else {
      throw new IllegalStateException(
          "'switch' condition must be a variable or constant: " + switchVariable);
    }

    int enumType = switchNode.getEnumType();
    Map<String, Integer> enums = treeInfo.mEnums.get(enumType);
    if (enums == null) {
      throw new IllegalStateException("Enum type " + enumType + " doesn't exist");
    }
    Map<Integer, ParseTreeNode> cases = new HashMap<>();

    ParseTreeNode defaultNode = null;
    if (defaultDefinition != null) {
      defaultNode = createParseTreeFromObject(treeInfo, defaultDefinition, hint);
    }

    Iterator<String> caseNames = casesDefinition.keys();
    while (caseNames.hasNext()) {
      String caseName = caseNames.next();
      Integer enumValue = enums.get(caseName);
      if (enumValue == null) {
        throw new IllegalStateException(
            "Enum type " + enumType + " doesn't contain value: " + caseName);
      }
      // TODO: incompatible types in argument.
      @SuppressWarnings("nullness:argument.type.incompatible")
      ParseTreeNode node = createParseTreeFromObject(treeInfo, casesDefinition.opt(caseName), hint);
      cases.put(
          enumValue, new ParseTreeCommentNode(node, CASE_FORMAT, new Object[] {caseName}, false));
    }

    ParseTreeNode result = new ParseTreeSwitchNode(switchNode, cases, defaultNode);
    return new ParseTreeCommentNode(result, SWITCH_FORMAT, new Object[] {switchVariable});
  }

  private static final String FOR_REFERENCE_FORMAT = "for_reference (%s):";

  private static ParseTreeNode createForReferenceParseTreeNodeFromObject(
      TreeInfo treeInfo, JSONObject value, VariableInfo hint) {
    String forReferenceVariable = value.optString("for_reference");

    if (forReferenceVariable == null || !VARIABLE_PATTERN.matcher(forReferenceVariable).matches()) {
      throw new IllegalStateException(
          "'for_reference' parameter must be a variable: " + forReferenceVariable);
    }

    if (!value.has("evaluate")) {
      throw new IllegalStateException("'for_reference' must have a node to evaluate");
    }

    String forReferenceVariableName = forReferenceVariable.substring(1);
    VariableInfo variableInfo = treeInfo.mVariables.get(forReferenceVariableName);
    if (variableInfo == null) {
      throw new IllegalStateException("Unknown variable: " + forReferenceVariable);
    }

    if (variableInfo.variableType != VARIABLE_REFERENCE) {
      throw new IllegalStateException(
          "'for_reference' requires a reference: " + forReferenceVariable);
    }

    // TODO: incompatible types in argument.
    @SuppressWarnings("nullness:argument.type.incompatible")
    ParseTreeNode function = createParseTreeFromObject(treeInfo, value.opt("evaluate"), hint);
    ParseTreeForReferenceNode result =
        new ParseTreeForReferenceNode(
            createVariableNode(treeInfo, forReferenceVariableName), function);
    return new ParseTreeCommentNode(
        result, FOR_REFERENCE_FORMAT, new Object[] {forReferenceVariable});
  }

  private static final String FOR_EACH_CHILD_FORMAT = "for_each_child (%s):";

  private static ParseTreeNode createForEachChildParseTreeNodeFromObject(
      TreeInfo treeInfo, JSONObject value) {
    String forEachChildVariable = value.optString("for_each_child");

    if (forEachChildVariable == null || !VARIABLE_PATTERN.matcher(forEachChildVariable).matches()) {
      throw new IllegalStateException(
          "'for_each_child' parameter must be a variable: " + forEachChildVariable);
    }

    if (!value.has("evaluate")) {
      throw new IllegalStateException("'for_each_child' must have a node to evaluate");
    }

    String forEachChildVariableName = forEachChildVariable.substring(1);
    VariableInfo variableInfo = treeInfo.mVariables.get(forEachChildVariableName);
    if (variableInfo == null) {
      throw new IllegalStateException("Unknown variable: " + forEachChildVariable);
    }

    if (variableInfo.variableType != VARIABLE_CHILD_ARRAY) {
      throw new IllegalStateException(
          "'for_each_child' requires a child array: " + forEachChildVariable);
    }

    ParseTreeForEachChildNode result =
        new ParseTreeForEachChildNode(createVariableNode(treeInfo, forEachChildVariableName));
    treeInfo.mDeferredForEachChildNodes.add(Pair.create(result, value));
    return new ParseTreeCommentNode(
        result, FOR_EACH_CHILD_FORMAT, new Object[] {forEachChildVariable});
  }

  private static final String NAMED_NODE_FORMAT = "%%%s";

  private static ParseTreeNode getOrCreateNamedNode(
      TreeInfo treeInfo, String name, VariableInfo hint) {
    ParseTreeNode node = treeInfo.mNamedNodes.get(name);
    if (node != null) {
      return node;
    }

    if (treeInfo.mPendingNamedNodes.contains(name)) {
      throw new IllegalStateException("Named node creates a cycle: " + name);
    }

    Object nodeDefinition = treeInfo.mNodes.opt(name);
    if (nodeDefinition == null) {
      throw new IllegalStateException("Missing named node: " + name);
    }
    treeInfo.mPendingNamedNodes.add(name);
    node = createParseTreeFromObject(treeInfo, nodeDefinition, hint);
    node = new ParseTreeCommentNode(node, NAMED_NODE_FORMAT, new Object[] {name}); // Wrap in trace.
    treeInfo.mNamedNodes.put(name, node);
    treeInfo.mPendingNamedNodes.remove(name);
    return node;
  }

  private static ParseTreeNode createVariableNode(TreeInfo treeInfo, String name) {
    VariableInfo varInfo = treeInfo.mVariables.get(name);
    if (varInfo == null) {
      throw new IllegalStateException("Unknown variable: " + name);
    }
    if (varInfo.variableType == VARIABLE_ENUM) {
      return new ParseTreeVariableNode(
          varInfo.name, varInfo.variableType, varInfo.id, varInfo.enumType);
    } else {
      return new ParseTreeVariableNode(varInfo.name, varInfo.variableType, varInfo.id);
    }
  }

  private static ParseTreeNode createConstantNode(TreeInfo treeInfo, String name) {
    ParseTreeNode node = treeInfo.mConstants.get(name);
    if (node == null) {
      throw new IllegalStateException("Unknown constant: " + name);
    }
    return node;
  }

  private static ParseTreeNode createFunctionNode(
      TreeInfo treeInfo, String value, int nameOffset, int paramOffset, int paramEnd) {
    ParseTreeNode result = null;
    String name = value.substring(nameOffset, paramOffset);
    if (TextUtils.equals(name, "length")) {
      List<ParseTreeNode> params = createParamListFromString(treeInfo, value, paramOffset);
      if (params.size() != 1) {
        throw new IllegalStateException("length() takes exactly one argument: " + value);
      }
      result = new ParseTreeLengthNode(params.get(0));
    } else {
      Pair<Object, Method> function = treeInfo.mFunctions.get(name);
      if (function == null) {
        throw new IllegalStateException("Unknown function: " + name);
      }
      List<ParseTreeNode> params = createParamListFromString(treeInfo, value, paramOffset);
      result = new ParseTreeFunctionNode(function.first, function.second, params);
    }

    return new ParseTreeCommentNode(
        result, "Evaluating: %s", new Object[] {value.substring(nameOffset, paramEnd)});
  }

  /**
   * Creates a list of ParseTreeNode from a comma separated list of statements. This method assumes
   * value is enclosed in ()
   *
   * @param value String to extract the parameter list from.
   * @param offset Offset in the string to start from.
   * @return A list of ParseTreeNodes generated from each statement in the list.
   */
  private static List<ParseTreeNode> createParamListFromString(
      TreeInfo treeInfo, String value, int offset) {
    List<ParseTreeNode> result = new ArrayList<>();

    // Move the offset past the initial '('
    offset += 1;
    while (true) {
      offset = skipWhitespace(value, offset);

      int end = findStatementEnd(value, offset, OPERATOR_CLASS_NONE);
      result.add(createParseTreeFromStatement(treeInfo, value, offset, end));
      offset = end;

      offset = skipWhitespace(value, offset);

      if (value.charAt(offset) == ')') {
        break;
      } else if (value.charAt(offset) != ',') {
        throw new IllegalStateException("Invalid param list: " + value);
      }
      offset++;
    }
    return result;
  }

  /** Returns the offset to the first non-whitespace character in value after offset. */
  private static int skipWhitespace(String value, int offset) {
    int length = value.length();
    while (offset < length && Character.isWhitespace(value.charAt(offset))) {
      offset++;
    }
    return offset;
  }

  /** Returns the offset to the character after the ')' that matches the current '(' */
  private static int findMatchingParen(String value, int offset) {
    if (value.charAt(offset) != '(') {
      throw new IllegalStateException("Expected '(' (" + offset + "): " + value);
    }
    offset++;
    int parenCount = 1;
    int length = value.length();
    while (offset < length) {
      char current = value.charAt(offset);
      if (current == '(') {
        parenCount++;
      } else if (current == ')') {
        parenCount--;
        if (parenCount == 0) {
          return offset + 1;
        }
      }
      offset++;
    }

    throw new IllegalStateException("Missing ending paren: " + value);
  }

  /** Returns the offset to the next token. Should only be used when parsing to a string. */
  private static int findNextTokenStartInString(String value, int offset) {
    final int length = value.length();
    while (offset < length) {
      if (isTokenStart(value, offset)) {
        return offset;
      }
      offset++;
    }
    return offset;
  }

  /** Returns true if the character at offset starts a token */
  private static boolean isTokenStart(String value, int offset) {
    char current = value.charAt(offset);
    return current == '@'
        || current == '#'
        || current == '$'
        || current == '%'
        || isFunctionStart(current);
  }

  /** Returns true if the character at offset starts a number */
  private static boolean isNumberStart(String value, int offset) {
    char current = value.charAt(offset);
    return current == '-' || current == '.' || Character.isDigit(current);
  }

  /** Returns true if the character starts an operator */
  private static boolean isOperatorStart(char value) {
    return value == '!'
        || value == '='
        || value == '>'
        || value == '<'
        || value == '+'
        || value == '-'
        || value == '/'
        || value == '*'
        || value == '|'
        || value == '&'
        || value == '^';
  }

  /** Returns true if the character starts a function */
  private static boolean isFunctionStart(char value) {
    return Character.isAlphabetic(value);
  }

  /**
   * Find the end of the statement in value starting from offset. A statement resolves to a single
   * node.
   *
   * @param value String to search for a statement
   * @param offset Starting point to search from.
   * @return Offset in the string to the end of the current statement.
   */
  private static int findStatementEnd(String value, int offset, @OperatorClass int leftOperator) {
    int valueLength = value.length();
    offset = skipWhitespace(value, offset);

    if (offset >= valueLength) {
      throw new IllegalStateException("Invalid statement(" + offset + "): " + value);
    }

    while (true) {
      if (value.charAt(offset) == '!') {
        offset++;
      }

      if (value.charAt(offset) == '(') {
        offset = findMatchingParen(value, offset);
      } else if (value.charAt(offset) == '\'') {
        offset = findStringEnd(value, offset);
      } else if (isFunctionStart(value.charAt(offset))) {
        offset = findTokenEnd(value, offset);
        offset = skipWhitespace(value, offset);
        offset = findMatchingParen(value, offset);
      } else if (isTokenStart(value, offset)) {
        offset = findTokenEnd(value, offset);
      } else if (isNumberStart(value, offset)) {
        offset = findNumberEnd(value, offset);
      } else {
        throw new IllegalStateException("Invalid statement(" + offset + "): " + value);
      }

      int result = offset;

      offset = skipWhitespace(value, offset);

      if (offset >= valueLength) {
        return result;
      }

      // If the next operator has precedence over leftOperator, then read and consume it and
      // the next operand.
      char current = value.charAt(offset);
      if (isOperatorStart(current)) {
        int end = findOperatorEnd(value, offset);
        @OperatorClass int operatorClass = getOperatorClass(value.substring(offset, end));

        if (leftOperator <= operatorClass) {
          return result;
        } else {
          offset = end;
        }
        offset = skipWhitespace(value, offset);
      } else if (current == ',' || current == ')') {
        return result;
      } else {
        throw new IllegalStateException("Invalid statement(" + offset + "): " + value);
      }
    }
  }

  /**
   * Find the end of the next token in the string.
   *
   * @param value String to search for a parsable element
   * @return Index of the character immediately after the end of the parsable string.
   */
  private static int findTokenEnd(String value, int offset) {
    final int length = value.length();
    offset = skipWhitespace(value, offset);

    if (offset >= length) {
      throw new IllegalStateException("Could not find token: " + value);
    }

    char current = value.charAt(offset);
    switch (current) {
      case '@':
        {
          Matcher matcher = RESOURCE_PATTERN.matcher(value);
          if (!matcher.find(offset)) {
            throw new IllegalStateException("Invalid resource string: " + value);
          }
          return matcher.end();
        }

      case '#':
        {
          Matcher matcher = CONSTANT_PATTERN.matcher(value);
          if (!matcher.find(offset)) {
            throw new IllegalStateException("Invalid constant string: " + value);
          }
          return matcher.end();
        }

      case '$':
        {
          Matcher matcher = VARIABLE_PATTERN.matcher(value);
          if (!matcher.find(offset)) {
            throw new IllegalStateException("Invalid variable string: " + value);
          }
          return matcher.end();
        }

      case '%':
        {
          Matcher matcher = NODE_PATTERN.matcher(value);
          if (!matcher.find(offset)) {
            throw new IllegalStateException("Invalid node string: " + value);
          }
          return matcher.end();
        }

      default:
        break;
    }

    if (isFunctionStart(current)) {
      Matcher matcher = IDENTIFIER_PATTERN.matcher(value);
      if (matcher.find(offset)) {
        return matcher.end();
      }
    }
    throw new IllegalStateException("Could not find token: " + value);
  }

  /**
   * Finds the end of a string constant starting at the current character.
   *
   * @param value String to search for a number.
   * @param offset Position in the string to start from.
   * @return offset of the character after the end of the string.
   */
  private static int findStringEnd(String value, int offset) {
    if (value.charAt(offset) != '\'') {
      throw new IllegalStateException("String doesn't start with ': " + value);
    }

    // Move past the initial "'"
    offset++;

    // Find the matching "'".  Skip any escaped characters.
    char current = value.charAt(offset);
    int length = value.length();
    while (current != '\'') {
      if (current == '\\') {
        offset++;
      }
      offset++;
      if (offset >= length) {
        throw new IllegalStateException("String missing end \"'\": " + value);
      }
      current = value.charAt(offset);
    }
    return offset + 1;
  }

  /**
   * Finds the end of a number starting at the current character.
   *
   * @param value String to search for a number.
   * @param offset Position in the string to start from.
   * @return offset of the character after the end of the number.
   */
  private static int findNumberEnd(String value, int offset) {
    Matcher matcher = NUMBER_PATTERN.matcher(value);
    if (!matcher.find(offset)) {
      throw new IllegalStateException("Invalid number in statement: " + value);
    }
    return matcher.end();
  }

  /**
   * Finds the end of an operator starting at the current character.
   *
   * @param value String to search for an operator.
   * @param offset Position in the string to start from.
   * @return offset of the character after the end of the operator.
   */
  private static int findOperatorEnd(String value, int offset) {
    Matcher matcher = OPERATOR_PATTERN.matcher(value);
    if (!matcher.find(offset)) {
      throw new IllegalStateException("Invalid operator in statement: " + value);
    }
    return matcher.end();
  }

  private static @ParseTree.OperatorClass int getOperatorClass(String value) {
    if (OPERATOR_CLASS_PLUS_PATTERN.matcher(value).matches()) {
      return OPERATOR_CLASS_PLUS;
    } else if (OPERATOR_CLASS_MULTIPLY_PATTERN.matcher(value).matches()) {
      return OPERATOR_CLASS_MULTIPLY;
    } else if (OPERATOR_CLASS_EQUALS_PATTERN.matcher(value).matches()) {
      return OPERATOR_CLASS_EQUALS;
    } else if (OPERATOR_CLASS_AND_PATTERN.matcher(value).matches()) {
      return OPERATOR_CLASS_AND;
    }
    throw new IllegalStateException("Unknown operator: " + value);
  }

  private static @ParseTree.OperatorClass int getOperatorClass(@ParseTree.Operator int operator) {
    switch (operator) {
      case OPERATOR_MULTIPLY:
      case OPERATOR_DIVIDE:
      case OPERATOR_POW:
        return OPERATOR_CLASS_MULTIPLY;
      case OPERATOR_PLUS:
      case OPERATOR_MINUS:
        return OPERATOR_CLASS_PLUS;
      case OPERATOR_EQUALS:
      case OPERATOR_NEQUALS:
      case OPERATOR_GT:
      case OPERATOR_LT:
      case OPERATOR_GE:
      case OPERATOR_LE:
        return OPERATOR_CLASS_EQUALS;
      case OPERATOR_AND:
      case OPERATOR_OR:
        return OPERATOR_CLASS_AND;
      default: // fall out
    }
    throw new IllegalStateException("Unknown operator: " + operator);
  }

  private static @ParseTree.Operator int getOperator(String value) {
    if (value.length() == 1) {
      switch (value.charAt(0)) {
        case '+':
          return OPERATOR_PLUS;
        case '-':
          return OPERATOR_MINUS;
        case '*':
          return OPERATOR_MULTIPLY;
        case '/':
          return OPERATOR_DIVIDE;
        case '<':
          return OPERATOR_LT;
        case '>':
          return OPERATOR_GT;
        case '^':
          return OPERATOR_POW;
        default: // fall out
      }
    } else if (value.length() == 2) {
      if (value.equals("&&")) {
        return OPERATOR_AND;
      } else if (value.equals("||")) {
        return OPERATOR_OR;
      } else if (value.charAt(1) == '=') {
        switch (value.charAt(0)) {
          case '=':
            return OPERATOR_EQUALS;
          case '!':
            return OPERATOR_NEQUALS;
          case '<':
            return OPERATOR_LE;
          case '>':
            return OPERATOR_GE;
          default: // fall out
        }
      }
    }
    throw new IllegalStateException("Unknown operator: " + value);
  }

  private static String getString(String value, int start, int end) {
    int offset = start;
    if (value.charAt(offset) != '\'') {
      throw new IllegalStateException("String doesn't start with ': " + value);
    }

    // Move past the initial "'"
    offset++;

    // Find the matching "'".  Evaluate any escaped characters.
    char current = value.charAt(offset);
    StringBuilder output = new StringBuilder();
    while (current != '\'') {
      if (current == '\\') {
        offset++;
        if (offset >= end) {
          throw new IllegalStateException("String missing end \"'\": " + value);
        }
        current = value.charAt(offset);
        switch (current) {
          case 'n':
            current = '\n';
            break;
          case 't':
            current = '\t';
            break;
          case '\\':
            current = '\\';
            break;
          case '\'':
            current = '\'';
            break;
          case '"':
            current = '"';
            break;
          default:
            /* Do nothing */
            break;
        }
      }

      // Add the current value to the output.
      output.append(current);

      offset++;
      if (offset >= end) {
        throw new IllegalStateException("String missing end \"'\": " + value);
      }

      current = value.charAt(offset);
    }
    return output.toString();
  }

  private static boolean isValidLvalueType(@VariableType int varType) {
    switch (varType) {
      case VARIABLE_BOOL:
      case VARIABLE_INTEGER:
      case VARIABLE_NUMBER:
      case VARIABLE_STRING:
      case VARIABLE_ENUM:
        return true;

      default:
        return false;
    }
  }

  private static boolean isValidRvalueType(@VariableType int varType) {
    // Currently, the rules for valid LValue and RValue are the same.
    return isValidLvalueType(varType);
  }

  public static String variableTypeToString(@VariableType int varType) {
    switch (varType) {
      case VARIABLE_BOOL:
        return "VARIABLE_BOOL";
      case VARIABLE_INTEGER:
        return "VARIABLE_INTEGER";
      case VARIABLE_NUMBER:
        return "VARIABLE_NUMBER";
      case VARIABLE_STRING:
        return "VARIABLE_STRING";
      case VARIABLE_ENUM:
        return "VARIABLE_ENUM";
      case VARIABLE_REFERENCE:
        return "VARIABLE_REFERENCE";
      case VARIABLE_ARRAY:
        return "VARIABLE_ARRAY";
      case VARIABLE_CHILD_ARRAY:
        return "VARIABLE_CHILD_ARRAY";
      default:
        return "(unhandled)";
    }
  }
}
