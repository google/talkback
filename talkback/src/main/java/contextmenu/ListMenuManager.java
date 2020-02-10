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
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.CLEAR_SAVED_GRANULARITY;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.SAVE_GRANULARITY;
import static com.google.android.accessibility.talkback.Feedback.Speech.Action.SAVE_LAST;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Handler;
import androidx.annotation.VisibleForTesting;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem.DeferredType;
import com.google.android.accessibility.talkback.eventprocessor.EventState;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.menurules.NodeMenuRuleProcessor;
import com.google.android.accessibility.utils.AccessibilityEventListener;
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
 * Controls list-style context menus. Uses {@link ListMenuPreparer} and {@link MenuTransformer} to
 * configure menus.
 *
 * <p>Some context menu actions need to restore focus from last active window, for instance, "Read
 * from next", and some would be reset with {@link AccessibilityEvent#TYPE_WINDOWS_CHANGED}, for
 * example, "Navigation granularity", so we need to do these actions in target active window. We
 * rely on event {@link AccessibilityEvent#TYPE_VIEW_ACCESSIBILITY_FOCUSED} to check the target
 * active window is active because focusmanagement select initial accessibility focus when window
 * state changes.
 */
public class ListMenuManager
    implements MenuManager, WindowEventHandler, AccessibilityEventListener {
  private static final String TAG = "ListMenuManager";
  /** Event types that are handled by ListMenuManager. */
  private static final int MASK_EVENTS_HANDLED_BY_LIST_MENU_MANAGER =
      AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;

  private TalkBackService service;
  private final Pipeline.FeedbackReturner pipeline;
  private final NodeMenuRuleProcessor nodeMenuRuleProcessor;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;
  private int menuShown;
  private ContextMenuItemClickProcessor menuClickProcessor;
  private int menuIdClicked = -1;
  @Nullable private DeferredAction deferredAction;
  @Nullable private Dialog currentDialog;
  private MenuTransformer menuTransformer;
  private MenuActionInterceptor menuActionInterceptor;

  public ListMenuManager(
      TalkBackService service,
      Pipeline.FeedbackReturner pipeline,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      NodeMenuRuleProcessor nodeMenuRuleProcessor) {
    this.service = service;
    this.pipeline = pipeline;
    this.nodeMenuRuleProcessor = nodeMenuRuleProcessor;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    menuClickProcessor = new ContextMenuItemClickProcessor(service, pipeline);
  }

  @Override
  public boolean showMenu(int menuId, EventId eventId) {
    /*
     * We get the last utterance at the time of menu creation.
     * The utterances produced by the user navigating the menu will go into the history.
     * Which would break last utterance functionality.
     */
    pipeline.returnFeedback(eventId, Feedback.speech(SAVE_LAST));
    dismissAll();
    pipeline.returnFeedback(eventId, Feedback.focus(CACHE));

    if (menuId == R.menu.global_context_menu) {
      pipeline.returnFeedback(eventId, Feedback.focusDirection(SAVE_GRANULARITY));
    }
    final ListMenu menu = new ListMenu(service);
    menu.setDefaultListener(
        new MenuItem.OnMenuItemClickListener() {
          @Override
          public boolean onMenuItemClick(MenuItem item) {
            // This check for item == null seems to be redundant, but it is a preventive step for
            // null pointer exception.
            if (item == null) {
              return true;
            } else if (item.hasSubMenu()) {
              CharSequence[] items = getItemsFromMenu(item.getSubMenu());
              ListMenu menu = (ListMenu) item.getSubMenu();
              showDialogMenu(menu.getTitle(), items, menu, eventId);
            } else {
              menuClickProcessor.onMenuItemClicked(item);
            }

            return true;
          }
        });

    ListMenuPreparer menuPreparer =
        new ListMenuPreparer(service, accessibilityFocusMonitor, nodeMenuRuleProcessor);
    menuPreparer.prepareMenu(menu, menuId);
    if (menuTransformer != null) {
      menuTransformer.transformMenu(menu, menuId);
    }

    if (menu.size() == 0) {
      pipeline.returnFeedback(
          eventId,
          Feedback.speech(
              service.getString(R.string.title_local_breakout_no_items),
              SpeakOptions.create()
                  .setQueueMode(SpeechController.QUEUE_MODE_FLUSH_ALL)
                  .setFlags(
                      FeedbackItem.FLAG_NO_HISTORY
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE)));
      return false;
    }

    showDialogMenu(menu.getTitle(), getItemsFromMenu(menu), menu, eventId);
    return true;
  }

  private boolean isContinuousReadMenuItemClicked(int itemId) {
    return (itemId == R.id.read_from_current || itemId == R.id.read_from_top);
  }

  protected void showDialogMenu(
      String title, CharSequence[] items, final ContextMenu menu, EventId eventId) {
    if (items == null || items.length == 0) {
      return;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(service);
    builder.setTitle(title);
    View view =
        prepareCustomView(
            items,
            new AdapterView.OnItemClickListener() {
              @Override
              public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
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
                menuIdClicked = menuItem.getItemId();

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
              }
            });
    builder.setView(view);
    builder.setNegativeButton(
        android.R.string.cancel,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (menuActionInterceptor != null) {
              menuActionInterceptor.onCancelButtonClicked();
            }

            dialog.dismiss();
          }
        });
    AlertDialog alert = builder.create();
    alert.setOnDismissListener(
        new DialogInterface.OnDismissListener() {
          @Override
          public void onDismiss(DialogInterface dialog) {
            menuShown--;
            if (menuShown == 0) {
              currentDialog = null;
            }

            // Clear saveGranularityForContinuousReading if "read from top/read from next item" is
            // not selected.
            if (!isContinuousReadMenuItemClicked(menuIdClicked)) {
              pipeline.returnFeedback(eventId, Feedback.focusDirection(CLEAR_SAVED_GRANULARITY));
            }
            menuIdClicked = -1;
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
        new ArrayAdapter<>(service, android.R.layout.simple_list_item_1, android.R.id.text1, items);
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

  @Override
  public boolean isMenuShowing() {
    return false;
  }

  @Override
  public void dismissAll() {
    if (currentDialog != null && currentDialog.isShowing()) {
      currentDialog.dismiss();
      currentDialog = null;
    }
  }

  @Override
  public void clearCache() {
    // NoOp
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

  @Override
  public void onGesture(int gesture) {}

  @Override
  public void setMenuTransformer(MenuTransformer transformer) {
    menuTransformer = transformer;
  }

  @Override
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
