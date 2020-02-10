/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.google.android.accessibility.talkback.contextmenu;

import static com.google.android.accessibility.talkback.Feedback.Speech.Action.SAVE_LAST;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_INTERRUPT;

import android.os.Handler;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.WindowManager;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.menurules.NodeMenuRuleProcessor;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.widget.DialogUtils;
import com.google.android.accessibility.utils.widget.SimpleOverlay;

/**
 * Controls radial-style context menus. Uses {@link GlobalMenuProcessor} and {@link MenuTransformer}
 * to configure menus.
 */
public class RadialMenuManager implements MenuManager {
  /** Delay in milliseconds before speaking the radial menu usage hint. */
  /*package*/ static final int DELAY_RADIAL_MENU_HINT = 2000;

  /** The scales used to represent menus of various sizes. */
  private static final int[] SCALES = {
    R.raw.radial_menu_1,
    R.raw.radial_menu_2,
    R.raw.radial_menu_3,
    R.raw.radial_menu_4,
    R.raw.radial_menu_5,
    R.raw.radial_menu_6,
    R.raw.radial_menu_7,
    R.raw.radial_menu_8
  };

  /** Cached radial menus. */
  private final SparseArray<RadialMenuOverlay> cachedRadialMenus = new SparseArray<>();

  private final TalkBackService service;

  /** Client that responds to menu item selection and click. */
  private RadialMenuClient client;

  /** How many radial menus are showing. */
  private int isRadialMenuShowing;

  /** Whether we have queued hint speech and it has not completed yet. */
  private boolean hintSpeechPending;

  private final boolean isTouchScreen;

  private MenuTransformer menuTransformer;
  private MenuActionInterceptor menuActionInterceptor;
  private final Pipeline.FeedbackReturner pipeline;

  public RadialMenuManager(
      boolean isTouchScreen,
      TalkBackService service,
      Pipeline.FeedbackReturner pipeline,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      NodeMenuRuleProcessor nodeMenuRuleProcessor) {
    this.isTouchScreen = isTouchScreen;
    this.service = service;
    this.pipeline = pipeline;
    client =
        new TalkBackRadialMenuClient(
            service, pipeline, accessibilityFocusMonitor, nodeMenuRuleProcessor);
  }

  /**
   * Shows the specified menu resource as a radial menu.
   *
   * @param menuId The identifier of the menu to display.
   * @return {@code true} if the menu could be shown.
   */
  @Override
  public boolean showMenu(int menuId, EventId eventId) {
    if (!isTouchScreen) {
      return false;
    }

    pipeline.returnFeedback(eventId, Feedback.speech(SAVE_LAST));

    RadialMenuOverlay overlay = cachedRadialMenus.get(menuId);

    if (overlay == null) {
      overlay = new RadialMenuOverlay(service, menuId, false);
      overlay.setListener(overlayListener);

      final WindowManager.LayoutParams params = overlay.getParams();
      params.type = DialogUtils.getDialogType();
      params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
      overlay.setParams(params);

      final RadialMenu menu = overlay.getMenu();
      menu.setDefaultSelectionListener(onSelection);
      menu.setDefaultListener(onClick);

      final RadialMenuView view = overlay.getView();
      view.setSubMenuMode(RadialMenuView.SubMenuMode.LIFT_TO_ACTIVATE);

      if (client != null) {
        client.onCreateRadialMenu(menuId, menu);
      }

      cachedRadialMenus.put(menuId, overlay);
    }

    if ((client != null) && !client.onPrepareRadialMenu(menuId, overlay.getMenu())) {
      pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
      return false;
    }

    if (menuTransformer != null) {
      menuTransformer.transformMenu(overlay.getMenu(), menuId);
    }

    overlay.showWithDot();
    return true;
  }

  @Override
  public boolean isMenuShowing() {
    return (isRadialMenuShowing > 0);
  }

  @Override
  public void onGesture(int gestureId) {
    dismissAll();
  }

  @Override
  public void setMenuTransformer(MenuTransformer transformer) {
    menuTransformer = transformer;
  }

  @Override
  public void setMenuActionInterceptor(MenuActionInterceptor actionInterceptor) {
    menuActionInterceptor = actionInterceptor;
  }

  @Override
  public void dismissAll() {
    for (int i = 0; i < cachedRadialMenus.size(); ++i) {
      final RadialMenuOverlay menu = cachedRadialMenus.valueAt(i);

      if (menu.isVisible()) {
        menu.dismiss();
      }
    }
  }

  @Override
  public void clearCache() {
    cachedRadialMenus.clear();
  }

  /**
   * Plays an F# harmonic minor scale with a number of notes equal to the number of items in the
   * specified menu, up to 8 notes.
   *
   * @param menu to play scale for
   */
  private void playScaleForMenu(Menu menu) {
    final int size = menu.size();
    if (size <= 0) {
      return;
    }

    pipeline.returnFeedback(EVENT_ID_UNTRACKED, Feedback.sound(SCALES[Math.min(size - 1, 7)]));
  }

  /** Handles selecting menu items. */
  private final RadialMenuItem.OnMenuItemSelectionListener onSelection =
      new RadialMenuItem.OnMenuItemSelectionListener() {
        @Override
        public boolean onMenuItemSelection(RadialMenuItem menuItem) {
          handler.removeCallbacks(radialMenuHint);

          EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance of menu events.
          pipeline.returnFeedback(
              eventId,
              Feedback.vibration(R.array.view_actionable_pattern).sound(R.raw.focus_actionable));

          final boolean handled = (client != null) && client.onMenuItemHovered();

          if (!handled) {
            final CharSequence text;
            if (menuItem == null) {
              text = service.getString(android.R.string.cancel);
            } else if (menuItem.hasSubMenu()) {
              text = service.getString(R.string.template_menu, menuItem.getTitle());
            } else {
              text = menuItem.getTitle();
            }
            pipeline.returnFeedback(
                eventId,
                Feedback.speech(
                    text,
                    SpeakOptions.create()
                        .setQueueMode(QUEUE_MODE_INTERRUPT)
                        .setFlags(
                            FeedbackItem.FLAG_NO_HISTORY
                                | FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                                | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                                | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE)));
          }

          return true;
        }
      };

  /** Handles clicking on menu items. */
  private final OnMenuItemClickListener onClick =
      new OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
          handler.removeCallbacks(radialMenuHint);

          EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance of menu events.
          pipeline.returnFeedback(
              eventId, Feedback.vibration(R.array.view_clicked_pattern).sound(R.raw.tick));

          boolean handled =
              menuActionInterceptor != null && menuActionInterceptor.onInterceptMenuClick(menuItem);

          if (!handled) {
            handled = client != null && client.onMenuItemClicked(menuItem);
          }

          if (!handled && (menuItem == null)) {
            pipeline.returnFeedback(
                EVENT_ID_UNTRACKED, Feedback.part().setInterruptAllFeedback(true));
          }

          if ((menuItem != null) && menuItem.hasSubMenu()) {
            playScaleForMenu(menuItem.getSubMenu());
          }

          return true;
        }
      };

  /** Handles feedback from showing and hiding radial menus. */
  private final SimpleOverlay.SimpleOverlayListener overlayListener =
      new SimpleOverlay.SimpleOverlayListener() {
        @Override
        public void onShow(SimpleOverlay overlay) {
          final RadialMenu menu = ((RadialMenuOverlay) overlay).getMenu();

          handler.postDelayed(radialMenuHint, DELAY_RADIAL_MENU_HINT);

          // TODO: Find an alternative or just speak the number of items.
          // Play a note in a C major scale for each item in the menu.
          playScaleForMenu(menu);

          isRadialMenuShowing++;
        }

        @Override
        public void onHide(SimpleOverlay overlay) {
          handler.removeCallbacks(radialMenuHint);

          if (hintSpeechPending) {
            pipeline.returnFeedback(
                EVENT_ID_UNTRACKED, Feedback.part().setInterruptAllFeedback(true));
          }

          isRadialMenuShowing--;
        }
      };

  /** Runnable that speaks a usage hint for the radial menu. */
  private final Runnable radialMenuHint =
      new Runnable() {
        @Override
        public void run() {
          final String hintText = service.getString(R.string.hint_radial_menu);

          hintSpeechPending = true;
          EventId eventId = EVENT_ID_UNTRACKED; // Hints occur after other feedback.
          pipeline.returnFeedback(
              eventId,
              Feedback.speech(
                  hintText,
                  SpeakOptions.create()
                      .setQueueMode(SpeechController.QUEUE_MODE_QUEUE)
                      .setFlags(
                          FeedbackItem.FLAG_NO_HISTORY
                              | FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                              | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                              | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE)
                      .setUtteranceGroup(SpeechController.UTTERANCE_GROUP_DEFAULT)
                      .setCompletedAction(hintSpeechCompleted)));
        }
      };

  /** Runnable that confirms the hint speech has completed. */
  private final SpeechController.UtteranceCompleteRunnable hintSpeechCompleted =
      new SpeechController.UtteranceCompleteRunnable() {
        @Override
        public void run(int status) {
          hintSpeechPending = false;
        }
      };

  private final Handler handler = new Handler();

  public interface RadialMenuClient {
    public void onCreateRadialMenu(int menuId, RadialMenu menu);

    public boolean onPrepareRadialMenu(int menuId, RadialMenu menu);

    public boolean onMenuItemHovered();

    public boolean onMenuItemClicked(MenuItem menuItem);
  }
}
