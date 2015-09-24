/*
 * Copyright (C) 2015 Google Inc.
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

package com.android.switchaccess;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import com.android.talkback.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Node to display context menus. Context menus provide text-based options for situations where
 * highlighting Views doesn't provide enough information for users to make choices. Examples
 * include global actions, which aren't associated with any View, and situations where a View
 * exposes multiple actions, such as click and long click or scrolling forward vs backward.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ContextMenuNode extends OptionScanSelectionNode implements ContextMenuItem {

    public ContextMenuNode(
            ContextMenuItem child0, ContextMenuItem child1, ContextMenuItem... children) {
        super(child0, child1, children);
    }

    /**
     * Draw the menu and highlight options with the paint options
     */
    @Override
    public void showSelections(final OverlayController overlayController, final Paint[] paints) {
        Context context = overlayController.getContext();
        /* Create a layout to hold the Views */
        LinearLayout menuLayout = new LinearLayout(context);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setGravity(Gravity.CENTER);
        menuLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final List<List<View>> viewsForHighlight = new ArrayList<>();
        LayoutInflater layoutInflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        boolean optionScanningEnabled = SwitchAccessPreferenceActivity
                .isOptionScanningEnabled(context);
        for(int i = 0; i < getChildCount(); i++) {
            List<View> viewList = new ArrayList<>();
            viewsForHighlight.add(viewList);
            /* Cast is safe because constructor only takes ContextMenuItems */
            ContextMenuItem child = (ContextMenuItem) getChild(i);
            for (CharSequence actionLabel : child.getActionLabels(context)) {
                Button buttonForAction = (Button) layoutInflater
                        .inflate(R.layout.switch_access_context_menu_button, null);
                buttonForAction.setText(actionLabel);
                menuLayout.addView(buttonForAction);
                viewList.add(buttonForAction);
            }
            if (optionScanningEnabled && (i == getChildCount() - 1)) {
                Button buttonForAction = (Button) layoutInflater
                        .inflate(R.layout.switch_access_context_menu_button, null);
                buttonForAction.setText(context.getResources()
                        .getString(android.R.string.cancel));
                menuLayout.addView(buttonForAction);
                viewList.add(buttonForAction);
            }
        }

        overlayController.addViewAndShow(menuLayout);

        /**
         * Highlight the option Views in a separate pass after they are being shown (until they
         * are shown their locations are not known.)
         */
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < viewsForHighlight.size(); ++i) {
                    Set<Rect> rectsToHighlight = new HashSet<>();
                    for (View view : viewsForHighlight.get(i)) {
                        int[] locationOnScreen = new int[2];
                        view.getLocationOnScreen(locationOnScreen);
                        Rect highlightRect = new Rect(locationOnScreen[0], locationOnScreen[1],
                                locationOnScreen[0] + view.getWidth(),
                                locationOnScreen[1] + view.getHeight());
                        rectsToHighlight.add(highlightRect);
                    }
                    overlayController.highlightPerimeterOfRects(rectsToHighlight, paints[i]);
                }
            }
        });
    }

    public List<CharSequence> getActionLabels(Context context) {
        List<CharSequence> actionLabels = new ArrayList<>();
        for (OptionScanNode child : mChildren) {
            /* Cast is safe because constructor only takes ContextMenuItems */
            actionLabels.addAll(((ContextMenuItem) child).getActionLabels(context));
        }
        return Collections.unmodifiableList(actionLabels);
    }
}
