/*
 * Copyright (C) 2023 Google Inc.
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
package com.google.android.accessibility.talkback.compositor.roledescription;

import android.content.Context;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.AccessibilityNodeFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.CompositorUtils;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.ImageContents;
import com.google.android.accessibility.utils.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Role description for {@link Role.ROLE_EDIT_TEXT}. */
public final class EditTextDescription implements RoleDescription {

  private final ImageContents imageContents;

  EditTextDescription(ImageContents imageContents) {
    this.imageContents = imageContents;
  }

  @Override
  public CharSequence nodeName(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables) {
    return nameDescription(node, context, imageContents, globalVariables);
  }

  @Override
  public CharSequence nodeRole(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables) {
    return AccessibilityNodeFeedbackUtils.defaultRoleDescription(node, context, globalVariables);
  }

  @Override
  public CharSequence nodeState(
      AccessibilityEvent event,
      AccessibilityNodeInfoCompat node,
      Context context,
      GlobalVariables globalVariables) {
    return stateDescription(node, context, globalVariables);
  }

  /** Returns EditText node name description text. */
  public static CharSequence nameDescription(
      AccessibilityNodeInfoCompat node,
      Context context,
      ImageContents imageContents,
      GlobalVariables globalVariables) {
    List<CharSequence> arrayList = new ArrayList<>();
    Locale preferredLocale =
        (globalVariables.getUserPreferredLocale() != null)
            ? globalVariables.getUserPreferredLocale()
            : AccessibilityNodeInfoUtils.getLocalesByNode(node);
    if (node.isPassword()) {
      // Treat as hidden-password input field.
      CharSequence contentDescription =
          AccessibilityNodeFeedbackUtils.getNodeContentDescription(node, context, preferredLocale);
      // Input type
      if (!TextUtils.isEmpty(contentDescription)) {
        arrayList.add(contentDescription);
      } else {
        arrayList.add(context.getString(R.string.value_password));
      }
      // Input content
      CharSequence nodeText =
          AccessibilityNodeFeedbackUtils.getNodeText(node, context, preferredLocale);
      if (!TextUtils.isEmpty(nodeText)) {
        if (AccessibilityNodeInfoUtils.supportsAction(
            node, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION)) {
          int textLength = nodeText.length();
          arrayList.add(
              context
                  .getResources()
                  .getQuantityString(
                      R.plurals.template_password_character_count, textLength, textLength));
        } else {
          arrayList.add(nodeText);
        }
      } else {
        arrayList.add(AccessibilityNodeFeedbackUtils.getNodeLabelText(node, imageContents));
      }
    } else {
      // Treat as normal text input field.
      CharSequence nodeText =
          AccessibilityNodeFeedbackUtils.getNodeText(node, context, preferredLocale);
      CharSequence contentDescription =
          AccessibilityNodeFeedbackUtils.getNodeContentDescription(node, context, preferredLocale);
      CharSequence labelText = AccessibilityNodeFeedbackUtils.getNodeLabelText(node, imageContents);
      if (!TextUtils.isEmpty(nodeText)) {
        arrayList.add(nodeText);
      } else if (!TextUtils.isEmpty(contentDescription)) {
        arrayList.add(contentDescription);
      } else if (!TextUtils.isEmpty(labelText)) {
        arrayList.add(labelText);
      }
    }
    return CompositorUtils.joinCharSequences(arrayList, CompositorUtils.getSeparator(), true);
  }

  /** Returns EditText node state description text. */
  public static CharSequence stateDescription(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables) {
    List<CharSequence> arrayList = new ArrayList<>();
    CharSequence stateDescription =
        AccessibilityNodeFeedbackUtils.getNodeStateDescription(
            node,
            context,
            (globalVariables.getUserPreferredLocale() != null)
                ? globalVariables.getUserPreferredLocale()
                : AccessibilityNodeInfoUtils.getLocalesByNode(node));
    if (!TextUtils.isEmpty(stateDescription)) {
      arrayList.add(stateDescription);
    }
    if (node.isFocused() && globalVariables.isKeyBoardActive()) {
      // Editing,  isCurrentlyEditing
      arrayList.add(context.getString(R.string.value_edit_box_editing));
    }
    if (globalVariables.getSelectionModeActive()) {
      // Selection mode on
      arrayList.add(context.getString(R.string.notification_type_selection_mode_on));
    }
    return CompositorUtils.joinCharSequences(arrayList, CompositorUtils.getSeparator(), true);
  }
}
