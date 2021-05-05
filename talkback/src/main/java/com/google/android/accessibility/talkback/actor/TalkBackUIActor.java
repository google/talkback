/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.android.accessibility.talkback.actor;

import android.content.Context;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.quickmenu.QuickMenuOverlay;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Controls TalkBack's UIs.
 *
 * <p>Note: Checks if new overlay can coexist with others before adding it into TalkBackActor.
 */
public class TalkBackUIActor {

  /** Types of TalkBack UIs. */
  public enum Type {
    /** Shows the current selector item after navigating the selector. */
    SELECTOR_MENU_ITEM_OVERLAY_SINGLE_FINGER,
    /**
     * Shows the current selector item after navigating the selector if the device doesn't support
     * multi-finger gestures.
     */
    SELECTOR_MENU_ITEM_OVERLAY_MULTI_FINGER,
    /** Shows the current action after adjusting the selected item via selector. */
    SELECTOR_ITEM_ACTION_OVERLAY,
  }

  private final Map<Type, QuickMenuOverlay> typeToOverlay = new EnumMap<>(Type.class);

  public TalkBackUIActor(Context context) {
    createOverlays(context);
  }

  private void createOverlays(Context context) {
    typeToOverlay.clear();
    typeToOverlay.put(
        Type.SELECTOR_MENU_ITEM_OVERLAY_SINGLE_FINGER,
        new QuickMenuOverlay(context, R.layout.quick_menu_item_overlay));
    typeToOverlay.put(
        Type.SELECTOR_MENU_ITEM_OVERLAY_MULTI_FINGER,
        new QuickMenuOverlay(
            context, R.layout.quick_menu_item_overlay_without_multifinger_gesture));
    typeToOverlay.put(
        Type.SELECTOR_ITEM_ACTION_OVERLAY,
        new QuickMenuOverlay(context, R.layout.quick_menu_item_action_overlay));
  }

  /**
   * Shows the specific overlay on the screen with the given message.
   *
   * <p>The show method always hides other overlays before showing the new overlay.
   */
  public boolean showQuickMenu(Type type, @Nullable CharSequence message, boolean showIcon) {
    @Nullable QuickMenuOverlay overlay = typeToOverlay.get(type);
    if (overlay == null) {
      return false;
    }

    hideOtherOverlays(overlay);
    overlay.setMessage(message);
    overlay.show(showIcon);
    return true;
  }

  /** Hides the specific overlay from the screen. */
  public boolean hide(Type type) {
    @Nullable QuickMenuOverlay overlay = typeToOverlay.get(type);
    if (overlay == null) {
      return false;
    }
    if (overlay.isShowing()) {
      overlay.hide();
    }
    return true;
  }

  public boolean setSupported(Type type, boolean supported) {
    @Nullable QuickMenuOverlay overlay = typeToOverlay.get(type);
    if (overlay == null) {
      return false;
    }
    overlay.setSupported(supported);
    return true;
  }

  /** Hides all overlays from the screen except the given overlay. */
  private void hideOtherOverlays(QuickMenuOverlay quickMenuOverlay) {
    Collection<QuickMenuOverlay> overlays = typeToOverlay.values();
    for (QuickMenuOverlay overlay : overlays) {
      if (quickMenuOverlay != overlay) {
        overlay.hide();
      }
    }
  }

  @VisibleForTesting
  public boolean isShowing(Type type) {
    @Nullable QuickMenuOverlay overlay = typeToOverlay.get(type);
    return (overlay != null) && overlay.isShowing();
  }
}
