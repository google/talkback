/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.accessibility.talkback.menurules;

import android.content.Context;
import androidx.annotation.IdRes;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** A creator responsible for collecting {@link NodeMenuRule} and creating the menu for a node. */
public class NodeMenuRuleCreator {
  /** Enum for all supported {@link NodeMenuRule}s. */
  public enum MenuRules {
    // Rules are matched in the order they are added, but any rule that
    // accepts will be able to modify the menu.
    RULE_UNLABELLED(R.id.labeling_breakout_add_label),
    RULE_CUSTOM_ACTION(R.id.custom_action_menu),
    RULE_VIEWPAGER(R.id.viewpager_menu),
    RULE_GRANULARITY(R.id.granularity_menu),
    RULE_SPANNABLES(R.id.links_menu),
    RULE_IMAGE_CAPTION(R.id.image_caption_menu);

    @IdRes final int ruleId;

    MenuRules(@IdRes int ruleId) {
      this.ruleId = ruleId;
    }

    private static @Nullable MenuRules getRuleById(@IdRes int ruleId) {
      return Arrays.stream(MenuRules.values())
          .filter(rule -> ruleId == rule.ruleId)
          .findFirst()
          .orElse(null);
    }
  }

  private final NodeMenuRule ruleUnlabeledNode;
  private final NodeMenuRule ruleAction;
  private final NodeMenuRule ruleViewPager;
  private final NodeMenuRule ruleGranularity;
  private final NodeMenuRule ruleSpannables;
  private final NodeMenuRule ruleImageCaption;

  public NodeMenuRuleCreator(
      Pipeline.FeedbackReturner pipeline,
      ActorState actorState,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      TalkBackAnalytics analytics) {
    ruleUnlabeledNode = new RuleUnlabeledNode(pipeline, actorState, analytics);
    ruleAction = new RuleAction(pipeline, actorState, accessibilityFocusMonitor, analytics);
    ruleViewPager = new RuleViewPager(pipeline, analytics);
    ruleGranularity = new RuleGranularity(pipeline, actorState, analytics);
    ruleSpannables = new RuleSpannables(analytics);
    ruleImageCaption = new RuleImageCaption(pipeline, analytics);
  }

  /**
   * Provides a {@link List} of relevant {@link ContextMenuItem} on target node with given {@link
   * MenuRules}.
   *
   * @param rule the rule for creating menu
   * @param context the parent context
   * @param node the node to process
   * @param includeAncestors sets to {@code true} to find menu items from its ancestors
   */
  public List<ContextMenuItem> getNodeMenuByRule(
      @NonNull MenuRules rule,
      Context context,
      AccessibilityNodeInfoCompat node,
      boolean includeAncestors) {
    return getMenuRule(rule).getMenuItemsForNode(context, node, includeAncestors);
  }

  @Nullable NodeMenuRule getMenuRuleById(int ruleId) {
    return getMenuRule(MenuRules.getRuleById(ruleId));
  }

  @Nullable NodeMenuRule getMenuRule(@Nullable MenuRules rule) {
    if (rule == null) {
      return null;
    }

    switch (rule) {
      case RULE_UNLABELLED:
        return ruleUnlabeledNode;
      case RULE_CUSTOM_ACTION:
        return ruleAction;
      case RULE_VIEWPAGER:
        return ruleViewPager;
      case RULE_GRANULARITY:
        return ruleGranularity;
      case RULE_SPANNABLES:
        return ruleSpannables;
      case RULE_IMAGE_CAPTION:
        return ruleImageCaption;
      default:
        throw new AssertionError("Unsupported Menu Rule.");
    }
  }
}
