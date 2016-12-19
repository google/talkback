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

package com.android.talkback.menurules;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import com.android.talkback.FeedbackItem;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.talkback.labeling.LabelDialogManager;
import com.android.utils.Role;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.contextmenu.ContextMenuItem;
import com.android.talkback.contextmenu.ContextMenuItemBuilder;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.labeling.CustomLabelManager;
import com.android.utils.labeling.Label;

import java.util.LinkedList;
import java.util.List;

/**
 * Processes {@link ImageView} nodes without text.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class RuleUnlabeledImage implements NodeMenuRule {

    @Override
    public boolean accept(TalkBackService service, AccessibilityNodeInfoCompat node) {
        final @Role.RoleName int role = Role.getRole(node);
        // TODO: node.hasImage();
        final boolean isImage = (role == Role.ROLE_IMAGE || role == Role.ROLE_IMAGE_BUTTON);
        final boolean hasDescription = !TextUtils.isEmpty(
                AccessibilityNodeInfoUtils.getNodeText(node));

        return (isImage && !hasDescription);
    }

    @Override
    public List<ContextMenuItem> getMenuItemsForNode(TalkBackService service,
                     ContextMenuItemBuilder menuItemBuilder, AccessibilityNodeInfoCompat node) {
        List<ContextMenuItem> items = new LinkedList<>();
        CustomLabelManager labelManager = service.getLabelManager();
        if (labelManager == null) {
            return items;
        }

        AccessibilityNodeInfoCompat nodeCopy = AccessibilityNodeInfoCompat.obtain(node);
        Label viewLabel = labelManager.getLabelForViewIdFromCache(nodeCopy.getViewIdResourceName());
        if (viewLabel == null) {
            final ContextMenuItem addLabel = menuItemBuilder.createMenuItem(service,
                    Menu.NONE, R.id.labeling_breakout_add_label, Menu.NONE,
                    service.getString(R.string.label_dialog_title_add));
            items.add(addLabel);
        } else {
            ContextMenuItem editLabel = menuItemBuilder.createMenuItem(service,
                    Menu.NONE, R.id.labeling_breakout_edit_label, Menu.NONE,
                    service.getString(R.string.label_dialog_title_edit));
            ContextMenuItem removeLabel = menuItemBuilder.createMenuItem(service,
                    Menu.NONE, R.id.labeling_breakout_remove_label, Menu.NONE,
                    service.getString(R.string.label_dialog_title_remove));
            items.add(editLabel);
            items.add(removeLabel);
        }

        for (ContextMenuItem item : items) {
            item.setOnMenuItemClickListener(
                    new UnlabeledImageMenuItemClickListener(service, nodeCopy, viewLabel));

            // Prevent re-speaking the node description right before showing the dialog.
            item.setSkipRefocusEvents(true);
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
        private final TalkBackService mContext;
        private final AccessibilityNodeInfoCompat mNode;
        private final Label mExistingLabel;

        public UnlabeledImageMenuItemClickListener(
                TalkBackService service, AccessibilityNodeInfoCompat node, Label label) {
            mContext = service;
            mNode = node;
            mExistingLabel = label;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            if (item == null) {
                mNode.recycle();
                return true;
            }

            mContext.saveFocusedNode();
            final int itemId = item.getItemId();

            if (itemId == R.id.labeling_breakout_add_label) {
                if (!canAddLabel()) {
                    mContext.getSpeechController().speak(
                            mContext.getString(R.string.cannot_add_label),
                            SpeechController.QUEUE_MODE_FLUSH_ALL,
                            FeedbackItem.FLAG_NO_HISTORY, null);
                    return false;
                }

                return LabelDialogManager.addLabel(mContext, mNode, true /* overlay */);
            } else if (itemId == R.id.labeling_breakout_edit_label) {
                return LabelDialogManager.editLabel(mContext, mExistingLabel, true /* overlay */);
            } else if (itemId == R.id.labeling_breakout_remove_label) {
                return LabelDialogManager.removeLabel(mContext, mExistingLabel, true /* overlay */);
            }

            mNode.recycle();
            return true;
        }

        private boolean canAddLabel() {
            final Pair<String, String> parsedId = CustomLabelManager.splitResourceName(
                    mNode.getViewIdResourceName());
            final boolean hasParseableId = (parsedId != null);

            // TODO: There are a number of views that have a
            // different resource namespace than their parent application. It's
            // likely we'll need to refine the database structure to accommodate
            // these while also allowing the user to modify them through TalkBack
            // settings. For now, we'll simply not allow labeling of such views.
            boolean isFromKnownApp = false;
            if (hasParseableId) {
                try {
                    mContext.getPackageManager().getPackageInfo(parsedId.first, 0);
                    isFromKnownApp = true;
                } catch (NameNotFoundException e) {
                    // Do nothing.
                }
            }

            return (hasParseableId && isFromKnownApp);
        }
    }
}
