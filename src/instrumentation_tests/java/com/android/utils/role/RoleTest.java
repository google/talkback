/*
 * Copyright (C) 2016 Google Inc.
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

package com.android.utils.role;

import com.android.utils.Role;

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.talkback.R;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;

public class RoleTest extends TalkBackInstrumentationTestCase {
    @MediumTest
    public void testRoles() {
        setContentView(R.layout.role_test);

        checkRole(R.id.role_button, Role.ROLE_BUTTON);
        checkRole(R.id.role_check_box, Role.ROLE_CHECK_BOX);
        checkRole(R.id.role_drop_down_list, Role.ROLE_DROP_DOWN_LIST);
        checkRole(R.id.role_edit_text, Role.ROLE_EDIT_TEXT);
        checkRole(R.id.role_grid, Role.ROLE_GRID);
        checkRoleAlternate(R.id.role_image, Role.ROLE_IMAGE);
        checkRole(R.id.role_image_button, Role.ROLE_IMAGE_BUTTON);
        checkRole(R.id.role_list, Role.ROLE_LIST);
        checkRole(R.id.role_radio_button, Role.ROLE_RADIO_BUTTON);
        checkRole(R.id.role_pager, Role.ROLE_PAGER);
        checkRole(R.id.role_seek_control, Role.ROLE_SEEK_CONTROL);
        checkRole(R.id.role_switch, Role.ROLE_SWITCH);
        checkRoleAlternate(R.id.role_tab_bar, Role.ROLE_TAB_BAR);
        checkRole(R.id.role_toggle_button, Role.ROLE_TOGGLE_BUTTON);
        checkRoleAlternate(R.id.role_view_group, Role.ROLE_VIEW_GROUP);
        checkRole(R.id.role_web_view, Role.ROLE_WEB_VIEW);
    }

    private void checkRole(int id, @Role.RoleName int expectedRole) {
        AccessibilityNodeInfoCompat node = getNodeForId(id);
        @Role.RoleName int role = Role.getRole(node);
        assertEquals(expectedRole, role);
    }

    // Some control types don't respond correctly to the test accessibility events, so we create
    // the accessibility node directly.
    private void checkRoleAlternate(int id, @Role.RoleName int expectedRole) {
        View view = getViewForId(id);
        AccessibilityNodeInfo node = view.createAccessibilityNodeInfo();
        AccessibilityNodeInfoCompat nodeCompat = new AccessibilityNodeInfoCompat(node);
        @Role.RoleName int role = Role.getRole(nodeCompat);
        assertEquals(expectedRole, role);
    }
}
