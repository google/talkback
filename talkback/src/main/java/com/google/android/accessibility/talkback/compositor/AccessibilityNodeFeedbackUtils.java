/*
 * Copyright (C) 2022 Google Inc.
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
package com.google.android.accessibility.talkback.compositor;

import static com.google.android.accessibility.talkback.imagecaption.ImageCaptionUtils.constructCaptionTextForAuto;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.SpannableStringBuilder;
import android.text.style.LocaleSpan;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.ImageContents;
import com.google.android.accessibility.utils.LocaleUtils;
import com.google.android.accessibility.utils.PackageManagerUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.SpannableUtils;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.caption.Result;
import com.google.android.accessibility.utils.output.SpeechCleanupUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.base.Joiner;
import java.util.List;
import java.util.Locale;

/**
 * Utils class that provides common methods that provide accessibility information by {@link
 * AccessibilityNodeInfoCompat} for compositor event feedback output.
 */
public class AccessibilityNodeFeedbackUtils {
  private static final String TAG = "AccessibilityNodeFeedbackUtils";

  private AccessibilityNodeFeedbackUtils() {}

  /** Returns the node text. */
  public static CharSequence getNodeText(
      AccessibilityNodeInfoCompat node, Context context, Locale userPreferredLocale) {
    return prepareSpans(
        AccessibilityNodeInfoUtils.getText(node), node, context, userPreferredLocale);
  }

  /**
   * Returns the node text for description.
   *
   * <p>Note: If the node is on-screen keyboard key or PIN key and TalkBack is not allowed to speak
   * password , it should return Bullet.
   *
   * <p>Note: It returns the node content description if it is not empty. Or it fallbacks to return
   * node text.
   */
  public static boolean isLauncherNode(AccessibilityNodeInfoCompat node) {
    if (node == null) return false;
    CharSequence pkg = node.getPackageName();
    if (pkg == null) return false;
    String pkgName = pkg.toString();
    return pkgName.contains("launcher") 
        || pkgName.contains("sec.android.app.launcher") // One UI
        || pkgName.contains("microsoft.launcher")      // Microsoft Launcher
        || pkgName.contains("google.android.apps.nexuslauncher"); // Pixel
  }

  private static boolean containsNumber(CharSequence text) {
    if (TextUtils.isEmpty(text)) return false;
    for (int i = 0; i < text.length(); i++) {
        if (Character.isDigit(text.charAt(i))) return true;
    }
    return false;
  }

  private static boolean isDirectCheckableControl(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }
    int role = Role.getRole(node);
    String className = (node.getClassName() == null) ? "" : node.getClassName().toString();
    return role == Role.ROLE_SWITCH
        || role == Role.ROLE_CHECK_BOX
        || role == Role.ROLE_RADIO_BUTTON
        || role == Role.ROLE_TOGGLE_BUTTON
        || role == Role.ROLE_CHECKED_TEXT_VIEW
        || className.contains("Switch")
        || className.contains("Toggle")
        || className.contains("CheckBox")
        || className.contains("RadioButton");
  }

  private static boolean isSwitchLikeControl(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }
    int role = Role.getRole(node);
    String className = (node.getClassName() == null) ? "" : node.getClassName().toString();
    return role == Role.ROLE_SWITCH
        || role == Role.ROLE_TOGGLE_BUTTON
        || className.contains("Switch")
        || className.contains("Toggle");
  }

  private static boolean isAuxiliarySelectionControl(AccessibilityNodeInfoCompat node) {
    if (node == null || isSwitchLikeControl(node)) {
      return false;
    }
    int role = Role.getRole(node);
    String className = (node.getClassName() == null) ? "" : node.getClassName().toString();
    return role == Role.ROLE_CHECK_BOX
        || role == Role.ROLE_RADIO_BUTTON
        || role == Role.ROLE_CHECKED_TEXT_VIEW
        || className.contains("CheckBox")
        || className.contains("RadioButton")
        || (node.isCheckable() && !TextUtils.isEmpty(node.getText()));
  }

  private static boolean hasVisiblePrimaryText(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }
    return !TextUtils.isEmpty(node.getContentDescription())
        || !TextUtils.isEmpty(node.getText())
        || !TextUtils.isEmpty(AccessibilityNodeInfoUtils.getHintText(node));
  }

  private static boolean hasAuxiliaryCheckableChild(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }
    for (int i = 0; i < Math.min(node.getChildCount(), 6); i++) {
      AccessibilityNodeInfoCompat child = node.getChild(i);
      if (child == null) {
        continue;
      }
      if (isAuxiliarySelectionControl(child)) {
        return true;
      }
      for (int j = 0; j < Math.min(child.getChildCount(), 4); j++) {
        AccessibilityNodeInfoCompat grandChild = child.getChild(j);
        if (grandChild != null && isAuxiliarySelectionControl(grandChild)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasVisibleTextChild(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }
    for (int i = 0; i < Math.min(node.getChildCount(), 6); i++) {
      AccessibilityNodeInfoCompat child = node.getChild(i);
      if (child == null) {
        continue;
      }
      if (!TextUtils.isEmpty(child.getContentDescription()) || !TextUtils.isEmpty(child.getText())) {
        return true;
      }
      for (int j = 0; j < Math.min(child.getChildCount(), 4); j++) {
        AccessibilityNodeInfoCompat grandChild = child.getChild(j);
        if (grandChild != null
            && (!TextUtils.isEmpty(grandChild.getContentDescription())
                || !TextUtils.isEmpty(grandChild.getText()))) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean shouldSuppressAuxiliaryCheckableState(AccessibilityNodeInfoCompat node) {
    if (node == null || isLauncherNode(node) || isDirectCheckableControl(node) || node.getRangeInfo() != null) {
      return false;
    }
    if (!(node.isClickable() || node.isFocusable())) {
      return false;
    }
    return hasAuxiliaryCheckableChild(node)
        && (hasVisiblePrimaryText(node) || hasVisibleTextChild(node));
  }

  public static CharSequence getNodeTextDescription(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables) {
    if (node == null) return "";
    long startTime = System.currentTimeMillis();
    
    // Detailed node logging for debugging
    CharSequence cd = node.getContentDescription();
    CharSequence txt = node.getText();
    CharSequence sd = node.getStateDescription();
    CharSequence hint = AccessibilityNodeInfoUtils.getHintText(node);
    String viewId = node.getViewIdResourceName();
    android.util.Log.d("AndroTalkDebug", "Node Analysis: [Pkg: " + node.getPackageName() + "] " +
                    "[ID: " + viewId + "] " +
                    "[Class: " + node.getClassName() + "] " +
                    "[CD: " + cd + "] " +
                    "[Text: " + txt + "] " +
                    "[SD: " + sd + "] " +
                    "[Hint: " + hint + "]");

    if (globalVariables.getLastTextEditIsPassword()
        && !globalVariables.shouldSpeakPasswords()
        && (AccessibilityNodeInfoUtils.isKeyboard(node)
            || AccessibilityNodeInfoUtils.isPinKey(node))) {
      return context.getString(R.string.symbol_bullet);
    }

    // Capture all text sources
    CharSequence contentDescription = prepareSpans(cd, node, context, globalVariables.getUserPreferredLocale());
    CharSequence nodeText = prepareSpans(txt, node, context, globalVariables.getUserPreferredLocale());
    CharSequence stateDescription = prepareSpans(sd, node, context, globalVariables.getUserPreferredLocale());
    CharSequence hintText = prepareSpans(hint, node, context, globalVariables.getUserPreferredLocale());
    
    CharSequence combinedText;
    if (isLauncherNode(node)) {
        // FORCED JOIN for launchers: CD, Text, State, Hint
        // We ensure that numbers are preserved and name repeats are avoided.
        SpannableStringBuilder forcedSb = new SpannableStringBuilder();
        String primaryText = ""; // Usually the app name
        
        // 1. Prioritize Content Description (often contains name + badge)
        if (!TextUtils.isEmpty(contentDescription)) {
            forcedSb.append(contentDescription);
            primaryText = contentDescription.toString().split(",")[0].trim();
        }
        
        // 2. Check Text (often just the name or just the badge)
        if (!TextUtils.isEmpty(nodeText)) {
            String nt = nodeText.toString().trim();
            // If Text is just the name and we already have it in CD, skip it.
            // But if Text contains a number (badge), add it.
            if (containsNumber(nodeText) || !primaryText.equalsIgnoreCase(nt)) {
                if (forcedSb.length() > 0 && !forcedSb.toString().contains(nt)) {
                    forcedSb.append(", ").append(nodeText);
                } else if (forcedSb.length() == 0) {
                    forcedSb.append(nodeText);
                }
            }
        }
        
        // 3. Add State Description (some launchers put badge here)
        if (!TextUtils.isEmpty(stateDescription)) {
            if (forcedSb.length() > 0 && !forcedSb.toString().contains(stateDescription)) {
                forcedSb.append(", ").append(stateDescription);
            } else if (forcedSb.length() == 0) {
                forcedSb.append(stateDescription);
            }
        }
        
        // 4. Add Hint (backup location for badges)
        if (!TextUtils.isEmpty(hintText)) {
            if (forcedSb.length() > 0 && !forcedSb.toString().contains(hintText)) {
                forcedSb.append(", ").append(hintText);
            } else if (forcedSb.length() == 0) {
                forcedSb.append(hintText);
            }
        }
        combinedText = forcedSb;
    } else {
        // Standard dedup join for other nodes
        combinedText =
            shouldSuppressAuxiliaryCheckableState(node)
                ? CompositorUtils.dedupJoin(contentDescription, nodeText, "")
                : CompositorUtils.dedupJoin(contentDescription, nodeText, stateDescription);
        combinedText = CompositorUtils.dedupJoin(combinedText, hintText, "");
    }

    CharSequence finalResult = globalVariables.getGlobalSayCapital()
        ? CompositorUtils.prependCapital(combinedText, context)
        : combinedText;

    // Child/sibling badge harvesting is useful for launchers, but on regular screens it can
    // duplicate visible labels such as settings rows and menu items.
    if (isLauncherNode(node)) {
        CharSequence childText = getRecursiveChildText(node, context);
        if (!TextUtils.isEmpty(childText) && !finalResult.toString().contains(childText.toString())) {
            finalResult = CompositorUtils.joinCharSequences(finalResult, childText);
        }

        CharSequence neighborText = getBadgeFromNeighbors(node, context);
        if (!TextUtils.isEmpty(neighborText)
            && !finalResult.toString().contains(neighborText.toString())) {
            finalResult = CompositorUtils.joinCharSequences(finalResult, neighborText);
        }
    }
    
    long duration = System.currentTimeMillis() - startTime;
    android.util.Log.d("AndroTalkDebug", "Node Analysis End. Duration: [" + duration + "ms]. Final Result: [" + finalResult + "]");
    return finalResult;
  }

  private static boolean isBadgeText(CharSequence text) {
    if (TextUtils.isEmpty(text)) return false;
    String s = text.toString().trim();
    // Matches patterns like "5", "12", "99+", or just numbers.
    return s.matches("\\d+\\+?");
  }

  private static CharSequence getBadgeFromNeighbors(AccessibilityNodeInfoCompat node, Context context) {
    if (node == null) return "";
    AccessibilityNodeInfoCompat parent = node.getParent();
    if (parent == null) return "";
    
    SpannableStringBuilder sb = new SpannableStringBuilder();
    int siblingCount = parent.getChildCount();
    // Optimized: Limit sibling scan to 8 to avoid UI lag
    for (int i = 0; i < Math.min(siblingCount, 8); i++) {
        AccessibilityNodeInfoCompat sibling = parent.getChild(i);
        if (sibling != null && !sibling.equals(node)) {
            CharSequence text = sibling.getContentDescription();
            if (TextUtils.isEmpty(text)) text = sibling.getText();
            
            String viewId = sibling.getViewIdResourceName();
            boolean isBadgeId = (viewId != null && (viewId.contains("badge") || viewId.contains("notification_count")));
            
            if (isBadgeId || isBadgeText(text)) {
                if (!TextUtils.isEmpty(text)) {
                    StringBuilderUtils.appendWithSeparator(sb, text);
                }
            }
        }
    }
    return sb;
  }

  private static CharSequence getRecursiveChildText(AccessibilityNodeInfoCompat node, Context context) {
    if (node == null) return "";
    SpannableStringBuilder sb = new SpannableStringBuilder();
    int childCount = node.getChildCount();
    // Optimized: Drastically reduced child count to avoid performance issues
    childCount = Math.min(childCount, 8); 
    
    for (int i = 0; i < childCount; i++) {
        AccessibilityNodeInfoCompat child = node.getChild(i);
        if (child != null) {
            // Collect all available text from descendants
            CharSequence text = child.getContentDescription();
            if (TextUtils.isEmpty(text)) {
                text = child.getText();
            }
            if (TextUtils.isEmpty(text)) {
                text = child.getStateDescription();
            }
            
            if (!TextUtils.isEmpty(text)) {
                StringBuilderUtils.appendWithSeparator(sb, text);
            }
            
            // Recurse into children (max 1 levels deep for performance)
            if (child.getChildCount() > 0) {
                 for (int j = 0; j < Math.min(child.getChildCount(), 3); j++) {
                     AccessibilityNodeInfoCompat grandChild = child.getChild(j);
                     if (grandChild != null) {
                        CharSequence gcText = grandChild.getContentDescription();
                        if (TextUtils.isEmpty(gcText)) gcText = grandChild.getText();
                        if (TextUtils.isEmpty(gcText)) gcText = grandChild.getStateDescription();
                        if (!TextUtils.isEmpty(gcText)) {
                            StringBuilderUtils.appendWithSeparator(sb, gcText);
                        }
                     }
                 }
            }
        }
    }
    return sb;
  }

  /**
   * Returns the node text description or label description.
   *
   * <p>Note: Talkback doesn't need to read out unlabelled or view element IDs for the node that has
   * extra information, like CheckBox, which has state description, or like OCR text, icon label
   * recognized from captured screenshot.
   */
  public static CharSequence getNodeTextOrLabelDescription(
      AccessibilityNodeInfoCompat node,
      Context context,
      ImageContents imageContents,
      GlobalVariables globalVariables) {
    CharSequence nodeTextDescription = getNodeTextDescription(node, context, globalVariables);
    if (!TextUtils.isEmpty(nodeTextDescription)) {
      return nodeTextDescription;
    }
    // Fallbacks to node label.
    CharSequence nodeLabelText = getNodeLabelText(node, imageContents);
    if (!TextUtils.isEmpty(nodeLabelText)) {
      return globalVariables.getGlobalSayCapital()
          ? CompositorUtils.prependCapital(nodeLabelText, context)
          : nodeLabelText;
    }
    // Fallbacks to caption text.
    CharSequence nodeCaptionText =
        getNodeCaptionText(node, context, imageContents, globalVariables.getUserPreferredLocale());
    if (!TextUtils.isEmpty(nodeCaptionText)) {
      return nodeCaptionText;
    }
    return "";
  }

  /** Returns the node text description or label or view element ID description. */
  public static CharSequence getNodeTextOrLabelOrIdDescription(
      AccessibilityNodeInfoCompat node,
      Context context,
      ImageContents imageContents,
      GlobalVariables globalVariables) {
    CharSequence nodeTextOrLabel =
        getNodeTextOrLabelDescription(node, context, imageContents, globalVariables);
    if (!TextUtils.isEmpty(nodeTextOrLabel)) {
      return nodeTextOrLabel;
    }
    // Fallbacks to element IDs.
    return globalVariables.getSpeakElementIds()
        ? AccessibilityNodeInfoUtils.getViewIdText(node)
        : "";
  }

  /** Returns the node content description. */
  public static CharSequence getNodeContentDescription(
      AccessibilityNodeInfoCompat node, Context context, Locale userPreferredLocale) {
    return prepareSpans(node.getContentDescription(), node, context, userPreferredLocale);
  }

  /**
   * Returns the node hint.
   *
   * <p>Note: The content should be non-copyable text for "copy last spoken phrase"
   */
  public static CharSequence getNodeHint(AccessibilityNodeInfoCompat node) {
    return SpannableUtils.wrapWithNonCopyableTextSpan(AccessibilityNodeInfoUtils.getHintText(node));
  }

  /**
   * Returns the node state description.
   *
   * <p>Note: The content should be non-copyable text for "copy last spoken phrase"
   */
  public static CharSequence getNodeStateDescription(
      AccessibilityNodeInfoCompat node, Context context, Locale userPreferredLocale) {
    CharSequence state = prepareSpans(
            AccessibilityNodeInfoUtils.getState(node), node, context, userPreferredLocale);
    
    if (TextUtils.isEmpty(state) && !shouldSuppressAuxiliaryCheckableState(node)) {
        state = getRecursiveChildState(node, context);
    }
    
    return SpannableUtils.wrapWithNonCopyableTextSpan(state);
  }

  private static CharSequence getRecursiveChildState(AccessibilityNodeInfoCompat node, Context context) {
    if (node == null) return "";
    
    android.graphics.Rect focusedBounds = new android.graphics.Rect();
    node.getBoundsInScreen(focusedBounds);
    
    // 0. Check the node ITSELF first (Essential for Quick Settings tiles)
    CharSequence selfState = getNodeState(node, context);
    if (!TextUtils.isEmpty(selfState)) return selfState;
    
    // 1. Search focused node's own children (inside)
    CharSequence childState = searchForState(node, context, 0, focusedBounds, true);
    if (!TextUtils.isEmpty(childState)) return childState;
    
    // 2. Search neighbors (1st level siblings - around)
    AccessibilityNodeInfoCompat parent = node.getParent();
    if (parent != null) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            AccessibilityNodeInfoCompat sibling = parent.getChild(i);
            if (sibling != null && !sibling.equals(node)) {
                CharSequence siblingState = searchForState(sibling, context, 0, focusedBounds, false);
                if (!TextUtils.isEmpty(siblingState)) return siblingState;
            }
        }
        
        // 3. Optimized: DO NOT search the whole neighborhood tree by default
        // This was the primary cause of 3-4 swipe latency.
        /* 
        AccessibilityNodeInfoCompat grandParent = parent.getParent();
        if (grandParent != null) {
            CharSequence neighborhoodState = searchForState(grandParent, context, 0, focusedBounds, false);
            if (!TextUtils.isEmpty(neighborhoodState)) return neighborhoodState;
        }
        */
    }
    
    return getSamsungExtraState(node, context);
  }

  private static CharSequence getNodeState(AccessibilityNodeInfoCompat node, Context context) {
      if (node == null) return "";
    
    // 1. HIGH PRIORITY: RangeInfo (Sliders / SeekBars) - Fixes Quick Settings Brightness Slider focus
    if (node.getRangeInfo() != null) {
        AccessibilityNodeInfoCompat.RangeInfoCompat rangeInfo = node.getRangeInfo();
        float current = rangeInfo.getCurrent();
        float min = rangeInfo.getMin();
        float max = rangeInfo.getMax();
        if (max > min) {
            int percent = (int) (((current - min) / (max - min)) * 100);
            return "Yüzde " + percent; 
        }
    }
    
      int role = Role.getRole(node);
      String className = (node.getClassName() == null) ? "" : node.getClassName().toString();

      if (shouldSuppressAuxiliaryCheckableState(node)) {
          return "";
      }
      
      // Only switch-like widgets should be spoken as on/off. Other checkable widgets keep their
      // checked semantics.
      boolean isRealSwitch =
          role == Role.ROLE_SWITCH
              || role == Role.ROLE_TOGGLE_BUTTON
              || className.contains("Switch")
              || className.contains("Toggle");
      
      boolean isTile = className.contains("Tile") || className.contains("StatusTile");
  
      if (isRealSwitch) {
          return node.isChecked() ? context.getString(R.string.value_on) : context.getString(R.string.value_off);
      }
      if (role == Role.ROLE_CHECK_BOX || role == Role.ROLE_RADIO_BUTTON || role == Role.ROLE_CHECKED_TEXT_VIEW) {
          return node.isChecked()
              ? context.getString(R.string.value_checked)
              : context.getString(R.string.value_not_checked);
      }
      
      if (isTile) {
          return (node.isChecked() || node.isSelected()) ? context.getString(R.string.value_on) : context.getString(R.string.value_off);
      }
    
    // 3. Last resort: Samsung Extras (only if it looks like it might be interactive)
    if (node.isClickable()) {
        return getSamsungExtraState(node, context);
    }
    
    return "";
  }

  private static CharSequence getSamsungExtraState(AccessibilityNodeInfoCompat node, Context context) {
    try {
        android.os.Bundle extras = node.getExtras();
        if (extras != null) {
            for (String key : extras.keySet()) {
                String lowKey = key.toLowerCase();
                if (lowKey.contains("state") || lowKey.contains("checked") || lowKey.contains("sesl") || lowKey.contains("active")) {
                    Object val = extras.get(key);
                    if (val instanceof Boolean) {
                        return (Boolean) val ? context.getString(R.string.value_on) : context.getString(R.string.value_off);
                    }
                    if (val instanceof Integer) {
                        int v = (Integer) val;
                        if (v == 1) return context.getString(R.string.value_on);
                        if (v == 0) return context.getString(R.string.value_off);
                    }
                }
            }
        }
    } catch (Exception e) { }
    return "";
  }

  private static CharSequence searchForState(AccessibilityNodeInfoCompat node, Context context, int depth, android.graphics.Rect focusedBounds, boolean isSameBranch) {
    // Optimized: Reduced depth from 3 to 1 for much faster discovery
    if (depth > 1) return ""; 
    for (int i = 0; i < Math.min(node.getChildCount(), 10); i++) {
        AccessibilityNodeInfoCompat child = node.getChild(i);
        if (child != null) {
            String className = (child.getClassName() == null) ? "" : child.getClassName().toString();
            
            // Selective identity - only recurse if it's very likely a switch or slider
            boolean isCheckableOrSlider = child.isCheckable() || child.getRangeInfo() != null
                || className.contains("Switch") || className.contains("CheckBox") || className.contains("Toggle") || className.contains("Tile");
            
            if (isCheckableOrSlider) {
                // VPSD: Visual Proximity State Detection check
                android.graphics.Rect candBounds = new android.graphics.Rect();
                child.getBoundsInScreen(candBounds);
                
                int candCenterY = candBounds.centerY();
                // Tighter alignment (2 pixels padding)
                boolean onSameLine = candCenterY >= (focusedBounds.top - 2) && candCenterY <= (focusedBounds.bottom + 2);
                boolean verticalOverlap = candBounds.bottom > (focusedBounds.top - 2) && candBounds.top < (focusedBounds.bottom + 2);
                
                if (onSameLine || verticalOverlap || isSameBranch) {
                    CharSequence state = getNodeState(child, context);
                    if (!TextUtils.isEmpty(state)) return state;
                }
            }
            
            CharSequence descendantState = searchForState(child, context, depth + 1, focusedBounds, isSameBranch);
            if (!TextUtils.isEmpty(descendantState)) return descendantState;
        }
    }
    return "";
  }

  /**
   * Returns the default node role description text.
   *
   * <p>Note: The content should be non-copyable text for "copy last spoken phrase"
   */
  public static CharSequence defaultRoleDescription(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables) {
    if (!globalVariables.getSpeakRoles()) {
      return "";
    }
    CharSequence nodeRoleDescription = getNodeRoleDescription(node, context, globalVariables);
    if (!TextUtils.isEmpty(nodeRoleDescription)) {
      return nodeRoleDescription;
    }
    return getNodeRoleName(node, context);
  }

  /**
   * Returns the node role description, which should respect the AppLocale.
   *
   * <p>Note: The content should be non-copyable text for "copy last spoken phrase"
   */
  public static CharSequence getNodeRoleDescription(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables) {
    CharSequence roleDescription =
        prepareSpans(
            node.getRoleDescription(), node, context, globalVariables.getUserPreferredLocale());
    return SpannableUtils.wrapWithNonCopyableTextSpan(
        stripRedundantCheckableState(roleDescription, node, context, globalVariables));
  }

  private static CharSequence stripRedundantCheckableState(
      CharSequence roleDescription,
      AccessibilityNodeInfoCompat node,
      Context context,
      GlobalVariables globalVariables) {
    if (TextUtils.isEmpty(roleDescription) || node == null) {
      return roleDescription;
    }
    int role = Role.getRole(node);
    boolean isCheckableRole =
        node.isCheckable()
            || role == Role.ROLE_SWITCH
            || role == Role.ROLE_TOGGLE_BUTTON
            || role == Role.ROLE_CHECK_BOX
            || role == Role.ROLE_CHECKED_TEXT_VIEW;
    if (!isCheckableRole) {
      return roleDescription;
    }

    Locale locale =
        (globalVariables != null && globalVariables.getUserPreferredLocale() != null)
            ? globalVariables.getUserPreferredLocale()
            : AccessibilityNodeInfoUtils.getLocalesByNode(node);
    CharSequence stateDescription = getNodeStateDescription(node, context, locale);
    CharSequence sanitized = stripStandaloneToken(roleDescription, stateDescription);
    if (TextUtils.isEmpty(sanitized)) {
      CharSequence fallbackState =
          node.isChecked()
              ? context.getString(R.string.value_on)
              : context.getString(R.string.value_off);
      sanitized = stripStandaloneToken(roleDescription, fallbackState);
    }
    return sanitized;
  }

  private static CharSequence stripStandaloneToken(CharSequence text, CharSequence token) {
    if (TextUtils.isEmpty(text) || TextUtils.isEmpty(token)) {
      return text;
    }
    String original = text.toString().trim();
    String originalLower = original.toLowerCase(Locale.ROOT);
    String tokenLower = token.toString().trim().toLowerCase(Locale.ROOT);
    if (tokenLower.isEmpty()) {
      return text;
    }

    if (originalLower.equals(tokenLower)) {
      return "";
    }
    if (originalLower.startsWith(tokenLower + " ")) {
      original = original.substring(tokenLower.length()).trim();
      originalLower = original.toLowerCase(Locale.ROOT);
    }
    if (originalLower.startsWith(tokenLower + ",")) {
      original = original.substring(tokenLower.length() + 1).trim();
      originalLower = original.toLowerCase(Locale.ROOT);
    }
    if (originalLower.endsWith(" " + tokenLower)) {
      original = original.substring(0, original.length() - tokenLower.length()).trim();
      originalLower = original.toLowerCase(Locale.ROOT);
    }
    if (originalLower.endsWith("," + tokenLower)) {
      original = original.substring(0, original.length() - tokenLower.length() - 1).trim();
    }
    return original;
  }

  /**
   * Returns the node role name.
   *
   * <p>Note: The content should be non-copyable text for "copy last spoken phrase"
   */
  public static CharSequence getNodeRoleName(AccessibilityNodeInfoCompat node, Context context) {
    int role = Role.getRole(node);
    CharSequence roleName = "";
    switch (role) {
      case Role.ROLE_BUTTON:
      case Role.ROLE_IMAGE_BUTTON:
        roleName = context.getString(R.string.value_button);
        break;
      case Role.ROLE_CHECK_BOX:
        roleName = context.getString(R.string.value_checkbox);
        break;
      case Role.ROLE_DROP_DOWN_LIST:
        roleName = context.getString(R.string.value_spinner);
        break;
      case Role.ROLE_EDIT_TEXT:
        roleName = context.getString(R.string.value_edit_box);
        break;
      case Role.ROLE_GRID:
        roleName = context.getString(R.string.value_gridview);
        break;
      case Role.ROLE_IMAGE:
        roleName = context.getString(R.string.value_image);
        break;
      case Role.ROLE_LIST:
        roleName = context.getString(R.string.value_listview);
        break;
      case Role.ROLE_PAGER:
        roleName = context.getString(R.string.value_pager);
        break;
      case Role.ROLE_PROGRESS_BAR:
        roleName = context.getString(R.string.value_progress_bar);
        break;
      case Role.ROLE_RADIO_BUTTON:
        roleName = context.getString(R.string.value_radio_button);
        break;
      case Role.ROLE_SEEK_CONTROL:
        roleName = context.getString(R.string.value_seek_bar);
        break;
      case Role.ROLE_SWITCH:
      case Role.ROLE_TOGGLE_BUTTON:
        roleName = context.getString(R.string.value_switch);
        break;
      case Role.ROLE_TAB_BAR:
        roleName = context.getString(R.string.value_tabwidget);
        break;
      case Role.ROLE_WEB_VIEW:
        roleName = context.getString(R.string.value_webview);
        break;
      default:
        // ROLE_CHECKED_TEXT_VIEW, ROLE_VIEW_GROUP or else will return an empty Role name.
        return "";
    }

    return SpannableUtils.wrapWithNonCopyableTextSpan(roleName);
  }

  /**
   * Returns {@code R.string.value_unlabelled} if the node is unlabeled and needs a label, otherwise
   * returns an empty text.
   */
  public static CharSequence getUnlabelledNodeDescription(
      int role,
      AccessibilityNodeInfoCompat node,
      Context context,
      @Nullable ImageContents imageContents,
      GlobalVariables globalVariables) {
    boolean needsLabel = imageContents != null && imageContents.needsLabel(node);
    boolean srcIsCheckable = node.isCheckable();
    LogUtils.v(
        TAG,
        StringBuilderUtils.joinFields(
            " getUnlabelledNodeDescription, ",
            StringBuilderUtils.optionalTag("needsLabel", needsLabel),
            StringBuilderUtils.optionalTag("srcIsCheckable", srcIsCheckable),
            StringBuilderUtils.optionalText("role", Role.roleToString(role))));
    if (!needsLabel
        || srcIsCheckable
        || (role == Role.ROLE_SEEK_CONTROL || role == Role.ROLE_PROGRESS_BAR)) {
      return "";
    }

    CharSequence nodeDescription = defaultRoleDescription(node, context, globalVariables);
    CharSequence nodeStateDescription =
        getNodeStateDescription(
            node,
            context,
            (globalVariables != null)
                ? globalVariables.getUserPreferredLocale()
                : AccessibilityNodeInfoUtils.getLocalesByNode(node));
    CharSequence nodeTextOrLabelId =
        getNodeTextOrLabelOrIdDescription(node, context, imageContents, globalVariables);
    // To accommodate apps (like Chrome) which don't set the text, hint text and isShowingHintText
    // properly, use getNodeHint instead of getHintDescription below.
    CharSequence nodeHintText = getNodeHint(node);
    CharSequence nodeDescriptionFromLabelNode =
        getDescriptionFromLabelNode(node, context, imageContents, globalVariables);
    if (TextUtils.isEmpty(nodeDescription)
        && TextUtils.isEmpty(nodeStateDescription)
        && TextUtils.isEmpty(nodeTextOrLabelId)
        && TextUtils.isEmpty(nodeHintText)
        && TextUtils.isEmpty(nodeDescriptionFromLabelNode)) {
      LogUtils.v(TAG, " getUnlabelledNodeDescription return Unlabelled because no text info");
      return context.getString(R.string.value_unlabelled);
    }
    return "";
  }

  /**
   * Returns the node page role description.
   *
   * <p>Note: The content should be non-copyable text for "copy last spoken phrase"
   *
   * <p>TODO : move this method to PagerPageDescription after ParseTree design
   * obsoleted
   */
  public static CharSequence getPagerPageRoleDescription(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables) {
    if (globalVariables.getSpeakRoles()) {
      CharSequence roleDescription = getNodeRoleDescription(node, context, globalVariables);
      if (!TextUtils.isEmpty(roleDescription)) {
        return roleDescription;
      } else {
        return SpannableUtils.wrapWithNonCopyableTextSpan(
            context.getString(R.string.value_pager_page));
      }
    }
    return "";
  }

  /** Returns the node caption text with auto triggered approach. */
  public static CharSequence getNodeCaptionText(
      AccessibilityNodeInfoCompat node,
      Context context,
      @Nullable ImageContents imageContents,
      @Nullable Locale userPreferredLocale) {

    if (imageContents == null) {
      return "";
    }

    Locale locale =
        (userPreferredLocale != null)
            ? userPreferredLocale
            : AccessibilityNodeInfoUtils.getLocalesByNode(node);
    if (locale == null) {
      locale = Locale.getDefault();
    }

    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    @Nullable
    Result ocrText =
        SharedPreferencesUtils.getBooleanPref(
                prefs,
                context.getResources(),
                R.string.pref_auto_text_recognition_key,
                R.bool.pref_auto_text_recognition_default)
            ? imageContents.getCaptionResult(node)
            : null;
    @Nullable
    Result iconLabel =
        SharedPreferencesUtils.getBooleanPref(
                prefs,
                context.getResources(),
                R.string.pref_auto_icon_detection_key,
                R.bool.pref_auto_icon_detection_default)
            ? imageContents.getDetectedIconLabel(locale, node)
            : null;
    @Nullable
    Result imageDescription =
        SharedPreferencesUtils.getBooleanPref(
                prefs,
                context.getResources(),
                R.string.pref_auto_image_description_key,
                R.bool.pref_auto_image_description_default)
            ? imageContents.getImageDescriptionResult(node)
            : null;

    return constructCaptionTextForAuto(context, imageDescription, iconLabel, ocrText);
  }

  /** Returns the node label text. */
  public static CharSequence getNodeLabelText(
      AccessibilityNodeInfoCompat node, @Nullable ImageContents imageContents) {
    return imageContents == null ? "" : imageContents.getLabel(node);
  }

  /** Returns the node description text from the label node. */
  public static CharSequence getDescriptionFromLabelNode(
      AccessibilityNodeInfoCompat node,
      Context context,
      ImageContents imageContents,
      GlobalVariables globalVariables) {
    AccessibilityNodeInfoCompat labelNode = node.getLabeledBy();
    if (labelNode == null) {
      return "";
    }
    return AccessibilityNodeFeedbackUtils.getNodeTextOrLabelOrIdDescription(
        labelNode, context, imageContents, globalVariables);
  }

  /** Returns the node disabled state if the node should announce the disabled state. */
  public static CharSequence getDisabledStateText(
      AccessibilityNodeInfoCompat node, Context context) {
    return (node != null && announceDisabled(node))
        ? context.getString(R.string.value_disabled)
        : "";
  }

  /** Returns if the node should announce disabled state. */
  private static boolean announceDisabled(AccessibilityNodeInfoCompat node) {
    // In some situations Views marked as headings (see ViewCompat#setAccessibilityHeading)
    // are in the disabled state, even though being disabled is not very appropriate. An
    // example are TextViews styled as preferenceCategoryStyle in certain themes.
    if (node.isHeading()) {
      return false;
    }
    if (BuildVersionUtils.isAtLeastS()) {
      return !node.isEnabled();
    }
    return !node.isEnabled()
        && (WebInterfaceUtils.hasNativeWebContent(node)
            || AccessibilityNodeInfoUtils.isActionableForAccessibility(node));
  }

  /** Returns the node selected state. */
  public static CharSequence getSelectedStateText(
      AccessibilityNodeInfoCompat node, Context context) {
    return (node != null && node.isSelected()) ? context.getString(R.string.value_selected) : "";
  }

  /** Returns the node collapsed or expanded state. */
  public static CharSequence getCollapsedOrExpandedStateText(
      AccessibilityNodeInfoCompat node, Context context) {
    if (AccessibilityNodeInfoUtils.isExpandable(node)) {
      return context.getString(R.string.value_collapsed);
    } else if (AccessibilityNodeInfoUtils.isCollapsible(node)) {
      return context.getString(R.string.value_expanded);
    }
    return "";
  }

  /**
   * Returns the unique node tooltip.
   *
   * <p>Note: if tooltip is the same as node text, it will return empty string to prevent duplicate
   * content.
   *
   * <p>TODO : move this method to NodeRoleDescription after ParseTree design obsoleted
   */
  public static CharSequence getUniqueTooltipText(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables) {
    if (node == null) {
      return "";
    }
    CharSequence tooltipText = node.getTooltipText();
    if (!TextUtils.isEmpty(tooltipText)
        && !TextUtils.equals(tooltipText, getNodeTextDescription(node, context, globalVariables))) {
      return tooltipText;
    }
    return "";
  }

  /**
   * Returns the node hint for node tree description.
   *
   * <p>Note: Description for edit text should append the node hint if it is not already showing the
   * hint as its text (when the edit text is blank)
   *
   * <p>TODO : move this method to NodeRoleDescription after ParseTree design obsoleted
   */
  public static CharSequence getHintDescription(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return "";
    }
    return (Role.getRole(node) == Role.ROLE_EDIT_TEXT && node.isShowingHintText())
        ? ""
        : getNodeHint(node);
  }

  /**
   * Returns hint text for node actions that is in high verbosity.
   *
   * <p>Note: There are four cases of hint for the "Actions" item if it is available in the TalkBack
   * menu,
   *
   * <ul>
   *   <li>1. Custom-Action gesture is assigned. e.g. "Tap with 2 fingers" is configured to show
   *       custom action -> "Actions available, use tap with 2 fingers to view"
   *   <li>2. "Actions" item for EditText. The current setting may be reset to Character or
   *       Proofread later, so always use the menu shortcut as the hint of "Actions" item. e.g. "Tap
   *       with 3 fingers" is configured to open TalkBack menu -> "Actions available, use tap with 3
   *       fingers to view"
   *   <li>3. "Actions" item is available in the reading control and the current settings is
   *       "Actions". e.g. "Swipe up" and "Swipe down" are configured to select reading control ->
   *       "Actions available, swipe up or swipe down and double-tap to activate"
   *   <li>4. Otherwise, e.g. "Tap with 3 fingers" is configured to open TalkBack menu -> "Actions
   *       available, use tap with 3 fingers to view"
   * </ul>
   */
  public static CharSequence getHintForNodeActions(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables) {
    int role = Role.getRole(node);
    NodeMenuProvider nodeMenuProvider = globalVariables.getNodeMenuProvider();
    if (nodeMenuProvider != null && role != Role.ROLE_TEXT_ENTRY_KEY) {
      List<String> menuTypeList = nodeMenuProvider.getSelfNodeMenuActionTypes(node);
      String actionMenuItemName = nodeMenuProvider.getActionMenuName();
      StringBuilder hint = new StringBuilder();
      if (!TextUtils.isEmpty(actionMenuItemName) && menuTypeList.contains(actionMenuItemName)) {
        CharSequence hintArgument = globalVariables.getGestureStringForActionShortcut();
        if (!TextUtils.isEmpty(hintArgument)) {
          // Custom-Action is a configured gesture shortcut.
          hint.append(
                  context.getString(
                      R.string.template_hint_menu_type_high_verbosity,
                      actionMenuItemName,
                      hintArgument))
              .append(CompositorUtils.getSeparator());
          // Actions item hint exists, so it's unnecessary to generate another hint for it.
          menuTypeList.remove(actionMenuItemName);
        } else if (globalVariables.isReadingMenuActionCurrentSettings(context)
            && globalVariables.hasReadingMenuActionSettings()
            // The current settings is not fixed and maybe be reset to default action (Character or
            // Proofread) later, so always providing menu shortcut for the hint of "Actions" item.
            && role != Role.ROLE_EDIT_TEXT) {
          hintArgument = globalVariables.getGestureStringForNodeActionsInReadingControl();
          if (!TextUtils.isEmpty(hintArgument)) {
            String actionDescription = globalVariables.getReadingMenuActionSettingsDescription();
            if (!TextUtils.isEmpty(actionDescription)) {
              hint.append(
                      context.getString(
                          R.string.template_hint_for_reading_control_actions_in_high_verbosity,
                          actionDescription,
                          hintArgument))
                  .append(CompositorUtils.getSeparator());
              // Actions item hint exists, so it's unnecessary to generate another hint for it.
              menuTypeList.remove(actionMenuItemName);
            }
          }
        }
      }

      // Gets a hint for node actions in the TalkBack menu.
      if (!menuTypeList.isEmpty()) {
        hint.append(
            context.getString(
                R.string.template_hint_menu_type_high_verbosity,
                Joiner.on(CompositorUtils.getSeparator()).join(menuTypeList),
                globalVariables.getGestureStringForNodeActions()));
      }
      return hint;
    }
    return "";
  }

  /**
   * Returns the accessibility node enabled state text.
   *
   * <p>TODO : move this method to NodeRoleDescription after ParseTree design obsoleted
   */
  public static CharSequence getAccessibilityEnabledState(
      AccessibilityNodeInfoCompat node, Context context) {
    if (node != null && AccessibilityNodeInfoUtils.isSelfOrAncestorFocused(node)) {
      return node.isEnabled()
          ? context.getString(R.string.value_enabled)
          : context.getString(R.string.value_disabled);
    }
    return "";
  }

  /**
   * Returns the accessibility node error text for description.
   *
   * <p>TODO : move this method to NodeRoleDescription after ParseTree design obsoleted
   */
  public static CharSequence getAccessibilityNodeErrorText(
      AccessibilityNodeInfoCompat node, Context context) {
    if (node != null && node.isContentInvalid()) {
      CharSequence errorText = node.getError();
      return TextUtils.isEmpty(errorText)
          ? ""
          : context.getString(R.string.template_node_error_with_error_message, errorText);
    }
    return "";
  }

  /** Returns the node error state text. */
  public static CharSequence notifyErrorStateText(
      @Nullable AccessibilityNodeInfoCompat node, Context context) {
    return (node == null || TextUtils.isEmpty(node.getError()))
        ? ""
        : context.getString(R.string.template_text_error, node.getError());
  }

  /** Returns the max length reached state text. */
  public static CharSequence notifyMaxLengthReachedStateText(
      @Nullable AccessibilityNodeInfoCompat node, Context context) {
    if (node == null || TextUtils.isEmpty(node.getText())) {
      return "";
    }
    // Uses node.text to get correct text length because event.text would have a symbol character
    // transferred to a spoken description.
    int maxTextLength = node.getMaxTextLength();
    int nodeTextLength = node.getText().length();
    return (maxTextLength > -1 && nodeTextLength >= maxTextLength)
        ? context.getString(R.string.value_text_max_length)
        : "";
  }

  @NonNull
  private static CharSequence prepareSpans(
      @Nullable CharSequence text,
      AccessibilityNodeInfoCompat node,
      Context context,
      Locale userPreferredLocale) {
    // Cleans up the edit text's text if it has just 1 symbol.
    // Do not double clean up the password.
    if (!node.isPassword()) {
      text = SpeechCleanupUtils.collapseRepeatedCharactersAndCleanUp(context, text);
    }

    // Wrap the text with user preferred locale changed using language switcher, with an exception
    // for all talkback nodes. As talkback text is always in the system language.

    if (PackageManagerUtils.isTalkBackPackage(node.getPackageName())) {
      return text == null ? "" : text;
    }
    if (userPreferredLocale == null) {
      userPreferredLocale = AccessibilityNodeInfoUtils.getLocalesByNode(node);
    }
    // UserPreferredLocale will take precedence over any LocaleSpan that is attached to the
    // text except in case of IMEs.
    if (!AccessibilityNodeInfoUtils.isKeyboard(node) && userPreferredLocale != null) {
      if (text instanceof Spannable) {
        Spannable ss = (Spannable) text;
        LocaleSpan[] spans = ss.getSpans(0, text.length(), LocaleSpan.class);
        for (LocaleSpan span : spans) {
          ss.removeSpan(span);
        }
      }
      return text == null ? "" : LocaleUtils.wrapWithLocaleSpan(text, userPreferredLocale);
    }
    return text == null ? "" : text;
  }
}
