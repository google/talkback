/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.talkback.speechrules;

import com.android.talkback.R;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableStringBuilder;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ListView;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.StringBuilderUtils;

/**
 * Rule for how to speak collection items.
 */
// TODO(KM): Update this to use the API instead when they become reliable.
public class RuleCollection extends RuleDefault {

    private static final String ROW_INDEX =
            "AccessibilityNodeInfo.CollectionItemInfo.rowIndex";
    private static final String COLUMN_INDEX =
            "AccessibilityNodeInfo.CollectionItemInfo.columnIndex";
    private static final String HEADING =
            "AccessibilityNodeInfo.CollectionItemInfo.heading";

    @Override
    public boolean accept(AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        if (event == null) {
            return false;
        }

        /* TODO Accept nodes with collection info whenever Chrome starts populating it:
        AccessibilityNodeInfo nodeInfo = (AccessibilityNodeInfo) node.getInfo();
        if (nodeInfo.getCollectionInfo() != null || nodeInfo.getCollectionItemInfo() != null) {
            return true;
        }
        */

        Parcelable parcelable = event.getParcelableData();
        if (parcelable instanceof Bundle) {
            Bundle bundle = (Bundle) parcelable;
            if (bundle.containsKey(ROW_INDEX) || bundle.containsKey(COLUMN_INDEX)
                    || bundle.containsKey(HEADING)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public CharSequence format(Context context, AccessibilityNodeInfoCompat node,
            AccessibilityEvent event) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        StringBuilderUtils.appendWithSeparator(
                builder, AccessibilityNodeInfoUtils.getNodeText(node));
        Parcelable parcelable = event.getParcelableData();

        Bundle bundle = (Bundle) parcelable;

        // TODO Get item/row/column number from collection info rather than bundle
        // whenever Chrome starts populating it

        if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(node.getParent(), ListView.class)) {
            // This is a list

            if (bundle.containsKey(ROW_INDEX)) {
                // Users expect to start at item 1, not item 0
                int itemNum = bundle.getInt(ROW_INDEX) + 1;

                StringBuilderUtils.appendWithSeparator(builder,
                        context.getString(R.string.item_index_template, itemNum));
            }
        } else {
            // This is a table

            if (bundle.containsKey(HEADING)) {
                StringBuilderUtils.appendWithSeparator(builder,
                        context.getString(R.string.heading_template));
            }

            if (bundle.containsKey(ROW_INDEX)) {
                // Users expect to start at row 1, not row 0
                int rowNum = bundle.getInt(ROW_INDEX) + 1;

                StringBuilderUtils.appendWithSeparator(builder,
                        context.getString(R.string.row_index_template, rowNum));
            }

            if (bundle.containsKey(COLUMN_INDEX)) {
                // Users expect to start at column 1, not column 0
                int columnNum = bundle.getInt(COLUMN_INDEX) + 1;

                StringBuilderUtils.appendWithSeparator(builder,
                        context.getString(R.string.column_index_template, columnNum));
            }
        }

        return builder;
    }
}
