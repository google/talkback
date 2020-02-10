/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItemBuilder;
import com.google.android.accessibility.talkback.labeling.CustomLabelManager;
import com.google.android.accessibility.talkback.labeling.LabelDialogManager;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.labeling.Label;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import java.util.ArrayList;
import java.util.List;

/** Processes {@link ImageView} nodes without text. */
class RuleUnlabeledImage implements NodeMenuRule {

  private final Pipeline.FeedbackReturner pipeline;

  public RuleUnlabeledImage(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  @Override
  public boolean accept(TalkBackService service, AccessibilityNodeInfoCompat node) {
    final @Role.RoleName int role = Role.getRole(node);
    final boolean isImage = (role == Role.ROLE_IMAGE || role == Role.ROLE_IMAGE_BUTTON);
    final boolean hasDescription = !TextUtils.isEmpty(AccessibilityNodeInfoUtils.getNodeText(node));

    return (isImage && !hasDescription);
  }

  @Override
  public List<ContextMenuItem> getMenuItemsForNode(
      TalkBackService service,
      ContextMenuItemBuilder menuItemBuilder,
      AccessibilityNodeInfoCompat node,
      boolean includeAncestors) {
    List<ContextMenuItem> items = new ArrayList<>();
    CustomLabelManager labelManager = service.getLabelManager();
    if (labelManager == null) {
      return items;
    }

    AccessibilityNodeInfoCompat nodeCopy = AccessibilityNodeInfoCompat.obtain(node);
    Label viewLabel = labelManager.getLabelForViewIdFromCache(nodeCopy.getViewIdResourceName());
    if (viewLabel == null) {
      final ContextMenuItem addLabel =
          menuItemBuilder.createMenuItem(
              service,
              Menu.NONE,
              R.id.labeling_breakout_add_label,
              Menu.NONE,
              service.getString(R.string.label_dialog_title_add));
      items.add(addLabel);
    } else {
      ContextMenuItem editLabel =
          menuItemBuilder.createMenuItem(
              service,
              Menu.NONE,
              R.id.labeling_breakout_edit_label,
              Menu.NONE,
              service.getString(R.string.label_dialog_title_edit));
      ContextMenuItem removeLabel =
          menuItemBuilder.createMenuItem(
              service,
              Menu.NONE,
              R.id.labeling_breakout_remove_label,
              Menu.NONE,
              service.getString(R.string.label_dialog_title_remove));
      items.add(editLabel);
      items.add(removeLabel);
    }

    for (ContextMenuItem item : items) {
      item.setOnMenuItemClickListener(
          new UnlabeledImageMenuItemClickListener(service, nodeCopy, viewLabel, pipeline));
      item.setShowsAlertDialog(true);
    }

    return items;
  }

  @Override
  public CharSequence getUserFriendlyMenuName(Context context) {
    return context.getString(R.string.title_labeling_controls);
  }

  @Override
  public boolean canCollapseMenu() {
    return true;
  }

  private static class UnlabeledImageMenuItemClickListener
      implements MenuItem.OnMenuItemClickListener {
    private final TalkBackService service;
    private final AccessibilityNodeInfoCompat node;
    private final Label existingLabel;
    private final Pipeline.FeedbackReturner pipeline;

    public UnlabeledImageMenuItemClickListener(
        TalkBackService service,
        AccessibilityNodeInfoCompat node,
        Label label,
        Pipeline.FeedbackReturner pipeline) {
      this.service = service;
      this.pipeline = pipeline;
      this.node = node;
      existingLabel = label;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      if (item == null) {
        node.recycle();
        return true;
      }
      final int itemId = item.getItemId();

      if (itemId == R.id.labeling_breakout_add_label) {
        if (!canAddLabel()) {
          SpeechController.SpeakOptions speakOptions =
              SpeechController.SpeakOptions.create()
                  .setQueueMode(SpeechController.QUEUE_MODE_FLUSH_ALL)
                  .setFlags(
                      FeedbackItem.FLAG_NO_HISTORY
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE);
          pipeline.returnFeedback(
              EVENT_ID_UNTRACKED,
              Feedback.speech(service.getString(R.string.cannot_add_label), speakOptions));
          return false;
        }

        return LabelDialogManager.addLabel(
            service, node, /* isFromLocalContextMenu= */ true, pipeline);
      } else if (itemId == R.id.labeling_breakout_edit_label) {
        return LabelDialogManager.editLabel(
            service, existingLabel, /* isFromLocalContextMenu= */ true, pipeline);
      } else if (itemId == R.id.labeling_breakout_remove_label) {
        return LabelDialogManager.removeLabel(
            service, existingLabel, /* isFromLocalContextMenu= */ true, pipeline);
      }

      node.recycle();
      return true;
    }

    private boolean canAddLabel() {
      final Pair<String, String> parsedId =
          CustomLabelManager.splitResourceName(node.getViewIdResourceName());
      final boolean hasParseableId = (parsedId != null);

      // TODO: There are a number of views that have a
      // different resource namespace than their parent application. It's
      // likely we'll need to refine the database structure to accommodate
      // these while also allowing the user to modify them through TalkBack
      // settings. For now, we'll simply not allow labeling of such views.
      boolean isFromKnownApp = false;
      if (hasParseableId) {
        try {
          service.getPackageManager().getPackageInfo(parsedId.first, 0);
          isFromKnownApp = true;
        } catch (NameNotFoundException e) {
          // Do nothing.
        }
      }

      return (hasParseableId && isFromKnownApp);
    }
  }
}
