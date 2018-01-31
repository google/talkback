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

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import com.google.android.accessibility.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.eventprocessor.EventState;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.EditTextActionHistory;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.input.TextCursorManager;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import java.util.ArrayList;
import java.util.List;

/**
 * Controls list-style context menus. Uses {@link ListMenuPreparer} and {@link MenuTransformer} to
 * configure menus.
 */
public class ListMenuManager implements MenuManager, AccessibilityEventListener {

  /** Minimum reset-focus delay to prevent flakiness. Should be hardly perceptible. */
  private static final long RESET_FOCUS_DELAY_SHORT = 50;
  /** Longer reset-focus delay for actions that explicitly request a delay. */
  private static final long RESET_FOCUS_DELAY_LONG = 1000;

  /** Event types that are handled by ListMenuManager. */
  private static final int MASK_EVENTS_HANDLED_BY_LIST_MENU_MANAGER =
      AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;

  private TalkBackService mService;
  private SpeechController mSpeechController;
  private int mMenuShown;
  private ContextMenuItemClickProcessor mMenuClickProcessor;
  private DeferredAction mDeferredAction;
  private Dialog mCurrentDialog;
  private FeedbackItem mLastUtterance;
  private MenuTransformer mMenuTransformer;
  private MenuActionInterceptor mMenuActionInterceptor;
  private final EditTextActionHistory mEditTextActionHistory;
  private final TextCursorManager mTextCursorManager;
  private final GlobalVariables mGlobalVariables;

  public ListMenuManager(
      TalkBackService service,
      EditTextActionHistory editTextActionHistory,
      TextCursorManager textCursorManager,
      GlobalVariables globalVariables) {
    mService = service;
    mEditTextActionHistory = editTextActionHistory;
    mTextCursorManager = textCursorManager;
    mSpeechController = service.getSpeechController();
    mMenuClickProcessor = new ContextMenuItemClickProcessor(service);
    mGlobalVariables = globalVariables;
  }

  @Override
  public boolean showMenu(int menuId, EventId eventId) {
    mLastUtterance = mSpeechController.getLastUtterance();
    dismissAll();

    mService.saveFocusedNode();
    final ListMenu menu = new ListMenu(mService);
    menu.setDefaultListener(
        new MenuItem.OnMenuItemClickListener() {
          @Override
          public boolean onMenuItemClick(MenuItem item) {
            EventId clickEventId = EVENT_ID_UNTRACKED; // Not tracking menu event performance.
            // This check for item == null seems to be redundant, but it is a preventive step for
            // null pointer exception.
            if (item == null) {
              return true;
            } else if (item.hasSubMenu()) {
              CharSequence[] items = getItemsFromMenu(item.getSubMenu());
              ListMenu menu = (ListMenu) item.getSubMenu();
              showDialogMenu(menu.getTitle(), items, menu);
            } else if (item.getItemId() == R.id.spell_last_utterance) {
              mService.getSpeechController().interrupt(true /* stopTtsSpeechCompletely */);
              mService.getSpeechController().spellUtterance(mLastUtterance.getAggregateText());
            } else if (item.getItemId() == R.id.repeat_last_utterance) {
              mService.getSpeechController().interrupt(true /* stopTtsSpeechCompletely */);
              mService.getSpeechController().repeatUtterance(mLastUtterance);
            } else if (item.getItemId() == R.id.copy_last_utterance_to_clipboard) {
              mService.getSpeechController().interrupt(true /* stopTtsSpeechCompletely */);
              mService
                  .getSpeechController()
                  .copyLastUtteranceToClipboard(mLastUtterance, clickEventId);
            } else {
              mMenuClickProcessor.onMenuItemClicked(item);
            }

            return true;
          }
        });

    ListMenuPreparer menuPreparer =
        new ListMenuPreparer(mService, mEditTextActionHistory, mTextCursorManager);
    menuPreparer.prepareMenu(menu, menuId);
    if (mMenuTransformer != null) {
      mMenuTransformer.transformMenu(menu, menuId);
    }

    if (menu.size() == 0) {
      mSpeechController.speak(
          mService.getString(R.string.title_local_breakout_no_items), /* Text */
          SpeechController.QUEUE_MODE_FLUSH_ALL, /* QueueMode */
          FeedbackItem.FLAG_NO_HISTORY | FeedbackItem.FLAG_FORCED_FEEDBACK, /* Flags */
          null, /* SpeechParams */
          eventId);
      return false;
    }
    showDialogMenu(menu.getTitle(), getItemsFromMenu(menu), menu);
    return true;
  }

  public void showDialogMenu(String title, CharSequence[] items, final ContextMenu menu) {
    if (items == null || items.length == 0) {
      return;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(mService);
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

                if (mMenuActionInterceptor != null) {
                  if (mMenuActionInterceptor.onInterceptMenuClick(menuItem)) {
                    // If the click was intercepted, stop processing the
                    // event.
                    return;
                  }
                }
                /*
                 * No delay is required when language menu items are clicked. Note the
                 * menuItems in languages menu have group ID "group_language".
                 */
                if (!menuItem.hasSubMenu()
                    && mService.hasSavedNode()
                    && menuItem.getGroupId() != R.id.group_language) {
                  // Defer the action only if we are about to close the menu and there's a saved
                  // node. In that case, we have to wait for it to regain accessibility focus
                  // before acting.
                  if (menuItem.getSkipRefocusEvents()) {
                    mGlobalVariables.setFlag(
                        GlobalVariables.EVENT_SKIP_FOCUS_PROCESSING_AFTER_CURSOR_CONTROL);
                    EventState.getInstance()
                        .setFlag(EventState.EVENT_SKIP_HINT_AFTER_CURSOR_CONTROL);
                  }
                  mDeferredAction = getDeferredAction(menuItem);
                } else {
                  mDeferredAction = null;
                }

                if (mCurrentDialog != null && mCurrentDialog.isShowing()) {
                  // Skip the window state announcements if a skip is requested...
                  // - whether or not there is any saved node to restore
                  // - but only if the action doesn't pop up a new dialog (we don't want to
                  //   accidentally clobber the alert dialog announcement)
                  if (menuItem.getSkipRefocusEvents() && !menuItem.getShowsAlertDialog()) {
                    EventState.getInstance()
                        .setFlag(
                            EventState.EVENT_SKIP_WINDOWS_CHANGED_PROCESSING_AFTER_CURSOR_CONTROL);
                    EventState.getInstance()
                        .setFlag(
                            EventState
                                .EVENT_SKIP_WINDOW_STATE_CHANGED_PROCESSING_AFTER_CURSOR_CONTROL);
                  }
                  mCurrentDialog.dismiss();
                }

                // Perform the action last (i.e. we want to make it almost like we're performing the
                // deferred action with a 0-ms delay).
                if (mDeferredAction == null) {
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
            if (mMenuActionInterceptor != null) {
              mMenuActionInterceptor.onCancelButtonClicked();
            }

            dialog.dismiss();
          }
        });
    AlertDialog alert = builder.create();
    alert.setOnDismissListener(
        new DialogInterface.OnDismissListener() {
          @Override
          public void onDismiss(DialogInterface dialog) {
            mMenuShown--;
            if (mMenuShown == 0) {
              // Sometimes, the node we want to refocus erroneously reports that it is
              // already accessibility focused right after windows change; to mitigate this,
              // we should wait a very short delay.
              long delay = RESET_FOCUS_DELAY_SHORT;
              if (mDeferredAction != null) {
                mService.addEventListener(ListMenuManager.this);

                // Actions that explicitly need a focus delay should get a much longer
                // focus delay.
                if (needFocusDelay(mDeferredAction.actionId)) {
                  delay = RESET_FOCUS_DELAY_LONG;
                }
              }
              EventState.getInstance()
                  .setFlag(EventState.EVENT_SKIP_FOCUS_SYNC_FROM_WINDOW_STATE_CHANGED);
              EventState.getInstance()
                  .setFlag(EventState.EVENT_SKIP_FOCUS_SYNC_FROM_WINDOWS_CHANGED);
              EventId eventId = EVENT_ID_UNTRACKED; // Not tracking menu event performance.
              mService.resetFocusedNode(delay, eventId);
              mCurrentDialog = null;
            }
          }
        });
    if (BuildVersionUtils.isAtLeastLMR1()) {
      alert.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
    } else {
      alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
    }

    alert.show();
    mCurrentDialog = alert;
    mMenuShown++;
  }

  private View prepareCustomView(CharSequence[] items, AdapterView.OnItemClickListener listener) {
    ListView view = new ListView(mService);
    view.setBackground(null);
    view.setDivider(null);
    ArrayAdapter<CharSequence> adapter =
        new ArrayAdapter<>(
            mService, android.R.layout.simple_list_item_1, android.R.id.text1, items);
    view.setAdapter(adapter);
    view.setOnItemClickListener(listener);
    return view;
  }

  // On pre-L_MR1 version focus events could be swallowed on platform after window state change,
  // so for actions that are rely on accessibility focus we need to delay focus request.
  private boolean needFocusDelay(int actionId) {
    if (BuildVersionUtils.isAtLeastLMR1()) {
      return false;
    }

    return actionId != R.id.pause_feedback
        && actionId != R.id.talkback_settings
        && actionId != R.id.enable_dimming
        && actionId != R.id.disable_dimming
        && actionId != R.id.tts_settings;
  }

  private DeferredAction getDeferredAction(final ContextMenuItem menuItem) {
    DeferredAction action =
        new DeferredAction() {
          @Override
          public void run() {
            menuItem.onClickPerformed();
          }
        };

    action.actionId = menuItem.getItemId();
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

  @Override
  public boolean isMenuShowing() {
    return false;
  }

  @Override
  public void dismissAll() {
    if (mCurrentDialog != null && mCurrentDialog.isShowing()) {
      mCurrentDialog.dismiss();
      mCurrentDialog = null;
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
        && mDeferredAction != null) {
      Handler handler = new Handler();
      handler.post(
          new Runnable() {
            @Override
            public void run() {
              if (mDeferredAction != null) {
                mDeferredAction.run();
                mDeferredAction = null;
              }
            }
          });
      mService.postRemoveEventListener(this);
    }
  }

  @Override
  public void onGesture(int gesture) {}

  @Override
  public void setMenuTransformer(MenuTransformer transformer) {
    mMenuTransformer = transformer;
  }

  @Override
  public void setMenuActionInterceptor(MenuActionInterceptor actionInterceptor) {
    mMenuActionInterceptor = actionInterceptor;
  }

  private abstract static class DeferredAction implements Runnable {
    public int actionId;
  }
}
