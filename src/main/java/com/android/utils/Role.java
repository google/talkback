package com.android.utils;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.talkback.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Utility methods for managing AccessibilityNodeInfo Roles.
 */
public class Role {
    @IntDef({ROLE_NONE, ROLE_BUTTON, ROLE_CHECK_BOX, ROLE_DROP_DOWN_LIST, ROLE_EDIT_TEXT,
            ROLE_GRID, ROLE_IMAGE, ROLE_IMAGE_BUTTON, ROLE_LIST, ROLE_PAGER, ROLE_RADIO_BUTTON,
            ROLE_SEEK_CONTROL, ROLE_SWITCH, ROLE_TAB_BAR, ROLE_TOGGLE_BUTTON, ROLE_VIEW_GROUP,
            ROLE_WEB_VIEW})
    @Retention(RetentionPolicy.SOURCE)
    public @interface RoleName {}

    public static final int ROLE_NONE = 0;
    public static final int ROLE_BUTTON = 1;
    public static final int ROLE_CHECK_BOX = 2;
    public static final int ROLE_DROP_DOWN_LIST = 3;
    public static final int ROLE_EDIT_TEXT = 4;
    public static final int ROLE_GRID = 5;
    public static final int ROLE_IMAGE = 6;
    public static final int ROLE_IMAGE_BUTTON = 7;
    public static final int ROLE_LIST = 8;
    public static final int ROLE_PAGER = 16;
    public static final int ROLE_RADIO_BUTTON = 9;
    public static final int ROLE_SEEK_CONTROL = 10;
    public static final int ROLE_SWITCH = 11;
    public static final int ROLE_TAB_BAR = 12;
    public static final int ROLE_TOGGLE_BUTTON = 13;
    public static final int ROLE_VIEW_GROUP = 14;
    public static final int ROLE_WEB_VIEW = 15;

    public static @RoleName int getRole(AccessibilityNodeInfoCompat node) {
        if (nodeMatchesClassByType(node,
                android.support.v4.view.ViewPager.class)) {
            return ROLE_PAGER;
        }

        if (nodeMatchesClassByType(node, android.webkit.WebView.class)) {
            return ROLE_WEB_VIEW;
        }

        if (nodeMatchesClassByType(node, android.widget.Spinner.class)) {
            return ROLE_DROP_DOWN_LIST;
        }

        if (nodeMatchesClassByType(node, android.widget.Switch.class)) {
            return ROLE_SWITCH;
        }

        if (nodeMatchesClassByType(node, android.widget.ToggleButton.class)) {
            return ROLE_TOGGLE_BUTTON;
        }

        if (nodeMatchesClassByType(node, android.widget.ImageView.class)) {
            return node.isClickable() ? ROLE_IMAGE_BUTTON : ROLE_IMAGE;
        }

        if (nodeMatchesClassByType(node, android.widget.RadioButton.class)) {
            return ROLE_RADIO_BUTTON;
        }

        if (nodeMatchesClassByType(node, android.widget.CompoundButton.class)) {
            return ROLE_CHECK_BOX;
        }

        if (nodeMatchesClassByType(node, android.widget.Button.class)) {
            return ROLE_BUTTON;
        }

        if (nodeMatchesClassByType(node, android.widget.EditText.class)) {
            return ROLE_EDIT_TEXT;
        }

        if (nodeMatchesClassByType(node, android.widget.SeekBar.class)) {
            return ROLE_SEEK_CONTROL;
        }

        if (nodeMatchesClassByType(node, android.widget.GridView.class)) {
            return ROLE_GRID;
        }

        if (nodeMatchesClassByType(node, android.widget.TabWidget.class)) {
            return ROLE_TAB_BAR;
        }

        if (nodeMatchesClassByType(node, android.widget.AbsListView.class)) {
            return ROLE_LIST;
        }

        if (node != null && node.getCollectionInfo() != null) {
            AccessibilityNodeInfoCompat.CollectionInfoCompat collection = node.getCollectionInfo();
            if (collection.getRowCount() > 1 && collection.getColumnCount() > 1) {
                return ROLE_GRID;
            } else {
                return ROLE_LIST;
            }
        }

        if (nodeMatchesClassByType(node, android.view.ViewGroup.class)) {
            return ROLE_VIEW_GROUP;
        }

        return ROLE_NONE;
    }

    public static @RoleName int getRole(AccessibilityNodeInfo node) {
        if (node == null) {
            return Role.ROLE_NONE;
        }

        AccessibilityNodeInfoCompat nodeCompat = new AccessibilityNodeInfoCompat(node);
        return getRole(nodeCompat);
    }

    /**
     * Convenience method for getting the role description of a given node or falling back to a
     * default TalkBack-provided role text. If neither is available, returns {@code null}.
     */
    public static @Nullable CharSequence getRoleDescriptionOrDefault(Context context,
            AccessibilityNodeInfoCompat node) {
        CharSequence roleDescription = node.getRoleDescription();
        if (!TextUtils.isEmpty(roleDescription)) {
            return roleDescription;
        }

        @RoleName int role = getRole(node);
        switch (role) {
            case ROLE_BUTTON:
                return context.getString(R.string.value_button);
            case ROLE_CHECK_BOX:
                return context.getString(R.string.value_checkbox);
            case ROLE_DROP_DOWN_LIST:
                return context.getString(R.string.value_spinner);
            case ROLE_EDIT_TEXT:
                return context.getString(R.string.value_edit_box);
            case ROLE_GRID:
                return context.getString(R.string.value_gridview);
            case ROLE_IMAGE:
                return context.getString(R.string.value_image);
            case ROLE_IMAGE_BUTTON:
                return context.getString(R.string.value_button); // Same as ROLE_BUTTON.
            case ROLE_LIST:
                return context.getString(R.string.value_listview);
            case ROLE_PAGER:
                return context.getString(R.string.value_pager);
            case ROLE_RADIO_BUTTON:
                return context.getString(R.string.value_radio_button);
            case ROLE_SEEK_CONTROL:
                return context.getString(R.string.value_seek_bar);
            case ROLE_SWITCH:
                return context.getString(R.string.value_switch);
            case ROLE_TAB_BAR:
                return context.getString(R.string.value_tabwidget);
            case ROLE_TOGGLE_BUTTON:
                return context.getString(R.string.value_switch); // Same as ROLE_SWITCH.
        }

        // Roles missing strings: ROLE_VIEW_GROUP, ROLE_WEB_VIEW.
        return null;
    }

    private static boolean nodeMatchesClassByType(AccessibilityNodeInfoCompat node,
                                                  Class<?> referenceClass) {
        return node != null &&
                ClassLoadingCache.checkInstanceOf(node.getClassName(), referenceClass);
    }
}
