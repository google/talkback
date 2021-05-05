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

package com.google.android.accessibility.talkback.contextmenu;

import static com.google.android.accessibility.talkback.Feedback.Focus.Action.CACHE;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.MUTE_NEXT_FOCUS;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.RESTORE_ON_NEXT_WINDOW;
import static com.google.android.accessibility.talkback.Feedback.Speech.Action.SAVE_LAST;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem.DeferredType;
import com.google.android.accessibility.talkback.eventprocessor.EventState;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionRecord;
import com.google.android.accessibility.talkback.menurules.NodeMenuRuleProcessor;
import com.google.android.accessibility.talkback.utils.AlertDialogUtils;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.WindowEventInterpreter.EventInterpretation;
import com.google.android.accessibility.utils.WindowEventInterpreter.WindowEventHandler;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.widget.DialogUtils;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Controls list-style context menus. Uses {@link MenuTransformer} to configure menus.
 *
 * <p>Some context menu actions need to restore focus from last active window, for instance, "Read
 * from next", and some would be reset with {@link AccessibilityEvent#TYPE_WINDOWS_CHANGED}, for
 * example, "Navigation granularity", so we need to do these actions in target active window. We
 * rely on event {@link AccessibilityEvent#TYPE_VIEW_ACCESSIBILITY_FOCUSED} to check the target
 * active window is active because focusmanagement select initial accessibility focus when window
 * state changes.
 */
public class ListMenuManager implements WindowEventHandler, AccessibilityEventListener {
  /** Event types that are handled by ListMenuManager. */
  private static final int MASK_EVENTS_HANDLED_BY_LIST_MENU_MANAGER =
      AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;

  private static final int INVALID_RES_ID = -1;

  private TalkBackService service;
  private final Pipeline.FeedbackReturner pipeline;
  private final ActorState actorState;
  private final NodeMenuRuleProcessor nodeMenuRuleProcessor;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;
  private final TalkBackAnalytics analytics;
  private int menuShown;
  private final ContextMenuItemClickProcessor menuClickProcessor;
  @Nullable private DeferredAction deferredAction;
  @Nullable private Dialog currentDialog;
  private MenuTransformer menuTransformer;
  private MenuActionInterceptor menuActionInterceptor;
  private long lastMenuDismissUptimeMs;
  private AccessibilityNodeInfoCompat currentNode;
  private ContextMenu contextMenu;

  public ListMenuManager(
      TalkBackService service,
      Pipeline.FeedbackReturner pipeline,
      ActorState actorState,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      NodeMenuRuleProcessor nodeMenuRuleProcessor,
      TalkBackAnalytics analytics) {
    this.service = service;
    this.pipeline = pipeline;
    this.actorState = actorState;
    this.nodeMenuRuleProcessor = nodeMenuRuleProcessor;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.analytics = analytics;
    menuClickProcessor = new ContextMenuItemClickProcessor(service, pipeline);
  }

  public boolean showMenu(int menuId, EventId eventId) {
    return showMenu(menuId, eventId, INVALID_RES_ID);
  }

  public boolean showMenu(int menuId, EventId eventId, int failureStringResId) {
    /*
     * We get the last utterance at the time of menu creation.
     * The utterances produced by the user navigating the menu will go into the history.
     * Which would break last utterance functionality.
     */
    pipeline.returnFeedback(eventId, Feedback.speech(SAVE_LAST));
    dismissAll();
    pipeline.returnFeedback(eventId, Feedback.focus(CACHE));

    contextMenu = new ContextMenu(service);
    contextMenu.setDefaultListener(
        (item) -> {
          if (menuId == R.menu.context_menu && item != null) {
            analytics.onGlobalContextMenuAction(item.getItemId());
          }
          // This check for item == null seems to be redundant, but it is a preventive step for
          // null pointer exception.
          if (item == null) {
            return true;
          } else if (item.hasSubMenu()) {
            ContextMenu subMenu = (ContextMenu) item.getSubMenu();
            CharSequence[] subMenuItems = getItemsFromMenu(subMenu);
            showDialogMenu(subMenu.getTitle(), subMenuItems, subMenu, eventId);

            if (menuId == R.menu.context_menu) {
              for (int i = 0; i < subMenu.size(); i++) {
                if (menuClickProcessor.isItemSupported(subMenu.getItem(i))) {
                  subMenu
                      .getItem(i)
                      .setOnMenuItemClickListener(menuClickProcessor::onMenuItemClicked);
                }
              }
            }
          } else {
            menuClickProcessor.onMenuItemClicked(item);
          }

          return true;
        });
    clearCurrentNode();
    currentNode = accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);

    prepareMenu(contextMenu, menuId);
    if (menuTransformer != null) {
      menuTransformer.transformMenu(contextMenu, menuId);
    }

    if (contextMenu.size() == 0) {
      String text =
          (failureStringResId == INVALID_RES_ID)
              ? service.getString(R.string.title_local_breakout_no_items)
              : service.getString(failureStringResId);
      pipeline.returnFeedback(
          eventId,
          Feedback.speech(
              text,
              SpeakOptions.create()
                  .setQueueMode(SpeechController.QUEUE_MODE_FLUSH_ALL)
                  .setFlags(
                      FeedbackItem.FLAG_NO_HISTORY
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE)));
      return false;
    }
    if (menuId == R.menu.context_menu) {
      analytics.onGlobalContextMenuOpen(/* isListStyle= */ true);
    }
    showDialogMenu(contextMenu.getTitle(), getItemsFromMenu(contextMenu), contextMenu, eventId);
    return true;
  }

  private void prepareMenu(ContextMenu menu, int menuId) {
    if (menuId == R.menu.context_menu) {
      TalkbackMenuProcessor talkbackMenuProcessor =
          new TalkbackMenuProcessor(
              service, actorState, pipeline, nodeMenuRuleProcessor, currentNode);

      talkbackMenuProcessor.prepareMenu(menu);
      menu.setTitle(service.getString(R.string.talkback_menu_title));
    } else if ((menuId == R.id.custom_action_menu) || (menuId == R.id.editing_menu)) {
      final AccessibilityNodeInfoCompat currentNode =
          accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);
      if (currentNode == null) {
        return;
      }

      nodeMenuRuleProcessor.prepareRuleMenuForNode(menu, currentNode, menuId);

      menu.setTitle(
          service.getString(
              (menuId == R.id.custom_action_menu)
                  ? R.string.title_custom_action
                  : R.string.title_edittext_controls));
      currentNode.recycle();
    } else if (menuId == R.menu.language_menu) {
      // Menu for language switcher
      LanguageMenuProcessor.prepareLanguageMenu(service, pipeline, actorState, menu);
      menu.setTitle(service.getString(R.string.language_options));
    }
  }

  protected void showDialogMenu(
      String title, CharSequence[] items, final ContextMenu menu, EventId eventId) {
    if (items == null || items.length == 0) {
      return;
    }

    AlertDialog.Builder builder = AlertDialogUtils.createBuilder(service);
    builder.setTitle(title);
    View customview =
        prepareCustomView(
            items,
            (parent, view, position, id) -> {
              final ContextMenuItem menuItem = menu.getItem(position);
              if (!menuItem.isEnabled()) {
                return;
              }

              if (menuActionInterceptor != null) {
                if (menuActionInterceptor.onInterceptMenuClick(menuItem)) {
                  // If the click was intercepted, stop processing the
                  // event.
                  return;
                }
              }

              if (menuItem.shouldRestoreFocusOnScreenChange()) {
                pipeline.returnFeedback(eventId, Feedback.focus(RESTORE_ON_NEXT_WINDOW));
              }

              DeferredType deferredType = menuItem.getDeferActionType();
              if (deferredType != DeferredType.NONE) {
                // Defer the action only if we are about to close the menu.
                deferredAction = createDeferredAction(menuItem, deferredType);
              } else {
                deferredAction = null;
              }

              if (menuItem.needToSkipNextFocusAnnouncement()) {
                pipeline.returnFeedback(eventId, Feedback.focus(MUTE_NEXT_FOCUS));
              }

              if (currentDialog != null && currentDialog.isShowing()) {
                // Skip the window state announcements if a skip is requested...
                // - whether or not there is any saved node to restore
                // - but only if the action doesn't pop up a new dialog (we don't want to
                //   accidentally clobber the alert dialog announcement)
                if (menuItem.needToSkipNextWindowAnnouncement()) {
                  EventState.getInstance()
                      .setFlag(
                          EventState.EVENT_SKIP_WINDOWS_CHANGED_PROCESSING_AFTER_CURSOR_CONTROL);
                  EventState.getInstance()
                      .setFlag(
                          EventState
                              .EVENT_SKIP_WINDOW_STATE_CHANGED_PROCESSING_AFTER_CURSOR_CONTROL);
                }
                currentDialog.dismiss();
              }

              // Perform the action last (i.e. we want to make it almost like we're performing the
              // deferred action with a 0-ms delay).
              if (deferredAction == null) {
                menuItem.onClickPerformed();
              }
            });
    builder.setView(customview);
    builder.setNegativeButton(
        android.R.string.cancel,
        (dialog, which) -> {
          if (menuActionInterceptor != null) {
            menuActionInterceptor.onCancelButtonClicked();
          }

          dialog.dismiss();
          clearMenu();
        });
    AlertDialog alert = builder.create();
    alert.setOnDismissListener(
        (dialog) -> {
          lastMenuDismissUptimeMs = SystemClock.uptimeMillis();
          menuShown--;
          if (menuShown == 0) {
            currentDialog = null;
          }
        });

    DialogUtils.setWindowTypeToDialog(alert.getWindow());

    alert.show();
    currentDialog = alert;
    menuShown++;
  }

  private View prepareCustomView(CharSequence[] items, AdapterView.OnItemClickListener listener) {
    ListView view = new ListView(service);
    view.setBackground(null);
    view.setDivider(null);
    ArrayAdapter<CharSequence> adapter =
        new ArrayAdapter<CharSequence>(
            service, android.R.layout.simple_list_item_1, android.R.id.text1, items) {
          @Override
          public View getView(int position, @Nullable View convertView, ViewGroup parent) {
            TextView textView = (TextView) super.getView(position, convertView, parent);
            if (FeatureSupport.isWatch(service)) {
              textView.setTextColor(service.getResources().getColor(R.color.text_color));
            }
            return textView;
          }
        };
    view.setAdapter(adapter);
    view.setOnItemClickListener(listener);
    return view;
  }

  private DeferredAction createDeferredAction(
      final ContextMenuItem menuItem, final DeferredType type) {
    // Register focus event listener if needs to wait for the accessibility focus.
    if (type == DeferredType.ACCESSIBILITY_FOCUS_RECEIVED) {
      service.addEventListener(ListMenuManager.this);
    }

    DeferredAction action = new DeferredAction(menuItem, type);
    return action;
  }

  public CharSequence[] getItemsFromMenu(Menu menu) {
    int size = menu.size();
    List<CharSequence> items = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      MenuItem item = menu.getItem(i);
      if (item != null && item.isVisible()) {
        items.add(item.getTitle());
      }
    }

    return items.toArray(new CharSequence[items.size()]);
  }

  private void executeDeferredActionByType(DeferredType deferType) {
    if (deferredAction == null) {
      return;
    }

    if (deferredAction.type == deferType) {
      executeDeferredAction();
      if (deferType == DeferredType.ACCESSIBILITY_FOCUS_RECEIVED) {
        service.postRemoveEventListener(this);
      }
    }
  }

  @VisibleForTesting
  void executeDeferredAction() {
    final DeferredAction action = deferredAction;
    new Handler().post(() -> action.menuItem.onClickPerformed());
    deferredAction = null;
  }

  public boolean isMenuShowing() {
    return currentDialog != null && currentDialog.isShowing();
  }

  public boolean isMenuExist() {
    if (currentDialog != null || deferredAction != null) {
      return true;
    }

    // TalkBack always requests an accessibility focus on next window after context menu dismissed,
    // so considering it's a part of context menus process will let the logic more stable. For
    // features which needs to check the menu exist or not, confirming of the accessibility ensures
    // the screen is under stable state.
    FocusActionRecord record = actorState.getFocusHistory().getLastFocusActionRecord();
    return (record != null) && (record.getActionTime() < lastMenuDismissUptimeMs);
  }

  private void clearCurrentNode() {
    AccessibilityNodeInfoUtils.recycleNodes(currentNode);
    currentNode = null;
  }

  private void dismissCurrentDialog() {
    if (currentDialog != null && currentDialog.isShowing()) {
      currentDialog.dismiss();
      currentDialog = null;
    }
  }

  private void clearMenu() {
    if (contextMenu != null) {
      contextMenu.clear();
      contextMenu = null;
    }
    clearCurrentNode();
  }

  public void dismissAll() {
    dismissCurrentDialog();
    clearMenu();
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_LIST_MENU_MANAGER;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
        && deferredAction != null) {
      executeDeferredActionByType(DeferredType.ACCESSIBILITY_FOCUS_RECEIVED);
    }
  }

  @Override
  public void handle(EventInterpretation interpretation, @Nullable EventId eventId) {
    if (deferredAction != null) {
      if (interpretation.areWindowsStable()) {
        executeDeferredActionByType(DeferredType.WINDOWS_STABLE);
      }
    }
  }

  public void onGesture(int gesture) {}

  public void setMenuTransformer(MenuTransformer transformer) {
    menuTransformer = transformer;
  }

  public void setMenuActionInterceptor(MenuActionInterceptor actionInterceptor) {
    menuActionInterceptor = actionInterceptor;
  }

  /** Superclass to be extended by actions in the context menu which needs to be deferred. */
  @VisibleForTesting
  static class DeferredAction {
    final int actionId;
    /** The {@link MenuItem} action needs to be deferred. */
    final ContextMenuItem menuItem;
    /** The deferred type of this action. */
    final DeferredType type;

    DeferredAction(ContextMenuItem menuItem, DeferredType type) {
      this.menuItem = menuItem;
      this.type = type;
      actionId = menuItem.getItemId();
    }
  }
}
