/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.talkback.contextmenu;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.view.Menu;

import com.android.talkback.R;
import com.android.talkback.controller.DimScreenController;
import com.android.talkback.controller.DimScreenControllerApp;

import com.google.android.marvin.talkback.TalkBackService;

/**
 * Processes dynamic menu items on the global context menu.
 */
public class GlobalMenuProcessor {

    private TalkBackService mService;

    public GlobalMenuProcessor(TalkBackService service) {
        mService = service;
    }

    /**
     * Populates a {@link Menu} with dynamic items relevant to the current global TalkBack state.
     *
     * @param menu The menu to populate.
     * @return {@code true} if successful, {@code false} otherwise.
     */
    public boolean prepareMenu(ContextMenu menu) {
        // Decide whether to display TalkBack Settings, TTS Settings, and Dim Screen items.
        KeyguardManager keyguardManager =
                (KeyguardManager) mService.getSystemService(Context.KEYGUARD_SERVICE);
        boolean isUnlocked = !keyguardManager.inKeyguardRestrictedInputMode();

        // The Settings items can simply be hidden because they're not dynamically switched.
        menu.findItem(R.id.talkback_settings).setVisible(isUnlocked);
        menu.findItem(R.id.tts_settings).setVisible(isUnlocked);

        // The Dim Screen items need to be removed because they are dynamically added.
        menu.removeItem(R.id.enable_dimming);
        menu.removeItem(R.id.disable_dimming);

        // Add Dim Screen items depending on API level and current state.
        if (DimScreenControllerApp.IS_SUPPORTED_PLATFORM && isUnlocked) {
            // Decide whether to display the enable or disable dimming item.
            // We have to re-add them (as opposed to hiding) because they occupy the same
            // physical space in the radial menu.
            final DimScreenController dimScreenController = mService.getDimScreenController();
            if (dimScreenController.isDimmingEnabled()) {
                menu.add(R.id.group_corners, R.id.disable_dimming,
                        getIntResource(R.integer.corner_SW),
                        R.string.shortcut_disable_dimming);
            } else {
                menu.add(R.id.group_corners, R.id.enable_dimming,
                        getIntResource(R.integer.corner_SW),
                        R.string.shortcut_enable_dimming);
            }
        }

        // Read from top/read from next should not respeak current item description.
        setSkipRefocusEvents(menu, R.id.read_from_top, true);
        setSkipRefocusEvents(menu, R.id.read_from_current, true);

        return true;
    }

    private static void setSkipRefocusEvents(ContextMenu menu, int itemId, boolean skip) {
        ContextMenuItem item = menu.findItem(itemId);
        if (item != null) {
            item.setSkipRefocusEvents(skip);
        }
    }

    private int getIntResource(int resourceId) {
        return mService.getResources().getInteger(resourceId);
    }

}
