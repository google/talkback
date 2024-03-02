package com.google.android.accessibility.talkback.selector;

import static com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor.NUMBER_PICKER_FILTER_FOR_ADJUST;
import static com.google.android.accessibility.talkback.selector.SelectorController.Setting.ADJUSTABLE_WIDGET;

import android.content.Context;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.selector.SelectorController.ContextualSetting;
import com.google.android.accessibility.talkback.selector.SelectorController.Setting;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Role;

/** Contextual setting for adjustable widgets, like a TimePicker or SeekBar. */
public class AdjustableWidgetSetting implements ContextualSetting {

  @Override
  public Setting getSetting() {
    return ADJUSTABLE_WIDGET;
  }

  @Override
  public boolean isNodeSupportSetting(Context context, AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    AccessibilityNodeInfoCompat adjustableNode =
        AccessibilityNodeInfoUtils.getMatchingAncestor(node, NUMBER_PICKER_FILTER_FOR_ADJUST);

    return (Role.getRole(node) == Role.ROLE_SEEK_CONTROL) || (adjustableNode != null);
  }
}
