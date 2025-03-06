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
import static com.google.android.accessibility.talkback.contextmenu.ListMenuManager.MenuId.CONTEXT;
import static com.google.android.accessibility.talkback.contextmenu.ListMenuManager.MenuId.CUSTOM_ACTION;
import static com.google.android.accessibility.talkback.contextmenu.ListMenuManager.MenuId.LANGUAGE;
import static com.google.android.accessibility.talkback.contextmenu.ListMenuManager.MenuId.LINKS;
import static com.google.android.accessibility.talkback.eventprocessor.EventState.EVENT_SKIP_FOCUS_SYNC_FROM_VIEW_FOCUSED;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
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
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.SettingsUtils;
import com.google.android.accessibility.utils.input.WindowEventInterpreter.EventInterpretation;
import com.google.android.accessibility.utils.input.WindowEventInterpreter.WindowEventHandler;
import com.google.android.accessibility.utils.material.A11yAlertDialogWrapper;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.widget.DialogUtils;
import com.google.android.accessibility.utils.widget.NonScrollableListView;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Controls list-style context menus.
 *
 * <p>Some context menu actions need to restore focus from last active window, for instance, "Read
 * from next", and some would be reset with {@link AccessibilityEvent#TYPE_WINDOWS_CHANGED}, for
 * example, "Navigation granularity", so we need to do these actions in target active window. We
 * rely on event {@link AccessibilityEvent#TYPE_VIEW_ACCESSIBILITY_FOCUSED} to check the target
 * active window is active because focusmanagement select initial accessibility focus when window
 * state changes.
 */
public class ListMenuManager implements WindowEventHandler, AccessibilityEventListener {
  private static final String TAG = "ListMenuManager";

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
  private @Nullable DeferredAction deferredAction;
  private @Nullable A11yAlertDialogWrapper currentDialog;
  private MenuActionInterceptor menuActionInterceptor;
  private long lastMenuDismissUptimeMs;
  private AccessibilityNodeInfoCompat currentNode;
  private ContextMenu contextMenu;
  private final FormFactorUtils formFactorUtils;

  /** Id to identify the menu content. */
  public enum MenuId {
    CONTEXT,
    CUSTOM_ACTION,
    LANGUAGE,
    LINKS,
  }

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
    this.formFactorUtils = FormFactorUtils.getInstance();
    menuClickProcessor = new ContextMenuItemClickProcessor(service, pipeline);
  }

  @CanIgnoreReturnValue
  public boolean showMenu(MenuId menuId, EventId eventId) {
    return showMenu(menuId, eventId, INVALID_RES_ID);
  }

  @CanIgnoreReturnValue
  public boolean showMenu(MenuId menuId, EventId eventId, int failureStringResId) {
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
          // This check for item == null seems to be redundant, but it is a preventive step for
          // null pointer exception.
          if (item == null) {
            return true;
          } else if (item.hasSubMenu()) {
            EventState.getInstance().setFlag(EVENT_SKIP_FOCUS_SYNC_FROM_VIEW_FOCUSED);
            ContextMenu subMenu = (ContextMenu) item.getSubMenu();
            CharSequence[] subMenuItems = getItemsFromMenu(subMenu);
            showDialogMenu(subMenu.getTitle(), subMenuItems, subMenu, eventId);

            if (menuId == CONTEXT) {
              for (int i = 0; i < subMenu.size(); i++) {
                if (menuClickProcessor.isItemSupported(subMenu.getItem(i))) {
                  subMenu
                      .getItem(i)
                      .setOnMenuItemClickListener(menuClickProcessor::onMenuItemClicked);
                }
              }
            }
          } else {
            EventState.getInstance().clearFlag(EVENT_SKIP_FOCUS_SYNC_FROM_VIEW_FOCUSED);
            menuClickProcessor.onMenuItemClicked(item);
          }

          return true;
        });
    clearCurrentNode();
    currentNode = accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);

    prepareMenu(contextMenu, menuId);

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
                          | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE
                          | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE
                          | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE)));
      return false;
    }
    if (menuId == CONTEXT) {
      analytics.onGlobalContextMenuOpen(/* isListStyle= */ true);
    }
    showDialogMenu(contextMenu.getTitle(), getItemsFromMenu(contextMenu), contextMenu, eventId);
    if (menuId == CONTEXT && !SettingsUtils.allowLinksOutOfSettings(service)) {
      String titleContentDescription =
          service.getString(R.string.talkback_menu_title_content_description);
      if (formFactorUtils.isAndroidTv()) {
        new Handler(Looper.getMainLooper())
            .post(() -> attachContentDescriptionOnTitle(currentDialog, titleContentDescription));
      } else {
        attachContentDescriptionOnTitle(currentDialog, titleContentDescription);
      }
    }
    return true;
  }

  private void prepareMenu(ContextMenu menu, MenuId menuId) {
    if (menuId == CONTEXT) {
      TalkbackMenuProcessor talkbackMenuProcessor =
          new TalkbackMenuProcessor(
              service, actorState, pipeline, nodeMenuRuleProcessor, currentNode);
      talkbackMenuProcessor.prepareMenu(menu);
      menu.setTitle(service.getString(R.string.talkback_menu_title));
    } else if (menuId == CUSTOM_ACTION) {
      final AccessibilityNodeInfoCompat currentNode =
          accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);
      if (currentNode == null) {
        return;
      }
      nodeMenuRuleProcessor.prepareRuleMenuForNode(menu, currentNode, R.id.custom_action_menu);

      menu.setTitle(service.getString(R.string.title_custom_action));
    } else if (menuId == LANGUAGE) {
      // Menu for language switcher
      LanguageMenuProcessor.prepareLanguageMenu(service, pipeline, actorState, menu);
      menu.setTitle(service.getString(R.string.language_options));
    } else if (menuId == LINKS) {
      // Menu for spannables
      nodeMenuRuleProcessor.prepareRuleMenuForNode(menu, currentNode, R.id.links_menu);
      menu.setTitle(service.getString(R.string.links));
    }
  }

  protected void showDialogMenu(
      String title, CharSequence[] items, final ContextMenu menu, EventId eventId) {
    if (items == null || items.length == 0) {
      return;
    }

    A11yAlertDialogWrapper.Builder builder = A11yAlertDialogWrapper.materialDialogBuilder(service);
    builder = builder.setTitle(title);
    View customView =
        prepareCustomView(
            items,
            (position) -> {
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
              analytics.onGlobalContextMenuAction(menuItem.getItemId());

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

    builder = builder.setView(customView);
    builder =
        builder.setNegativeButton(
            android.R.string.cancel,
            (dialog, which) -> {
              if (menuActionInterceptor != null) {
                menuActionInterceptor.onCancelButtonClicked();
              }

              dialog.dismiss();
              clearMenu();
            });
    builder = builder.setCancelable(true);
    A11yAlertDialogWrapper.Builder finalBuilder = builder;
    if (formFactorUtils.isAndroidTv()) {
      new Handler(Looper.getMainLooper()).post(() -> openAlert(finalBuilder));
    } else {
      openAlert(finalBuilder);
    }
  }

  private void openAlert(A11yAlertDialogWrapper.Builder builder) {
    A11yAlertDialogWrapper alert = builder.create();
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

  private void attachContentDescriptionOnTitle(
      A11yAlertDialogWrapper dialog, String contentDescription) {
    int titleId =
        service.getResources().getIdentifier("alertTitle", "id", service.getPackageName());
    if (titleId > 0) {
      TextView dialogTitle = dialog.getDialog().findViewById(titleId);
      if (dialogTitle != null) {
        dialogTitle.setContentDescription(contentDescription);
      }
    } else {
      LogUtils.w(TAG, "Cannot find the title TextView to add contentDescription.");
    }
  }

  private View prepareCustomView(CharSequence[] items, ListMenuClickListener listener) {
    View view = createListView(); // On TV this will be a RecyclerView (not a subclass of ListView).
    view.setId(R.id.talkback_menu_listview);
    view.setBackground(null);
    if (view instanceof ListView) {
      ((ListView) view).setDivider(null);
      ((ListView) view)
          .setDividerHeight(
              service
                  .getResources()
                  .getDimensionPixelSize(R.dimen.alertdialog_menuitem_divider_height));
    }
    view.setPaddingRelative(
        service.getResources().getDimensionPixelSize(R.dimen.alertdialog_padding_start),
        service.getResources().getDimensionPixelSize(R.dimen.alertdialog_padding_top),
        service.getResources().getDimensionPixelSize(R.dimen.alertdialog_padding_end),
        service.getResources().getDimensionPixelSize(R.dimen.alertdialog_padding_bottom));

    if (view instanceof ListView) {
      ArrayAdapter<CharSequence> listAdapter =
          new ArrayAdapter<>(
              new ContextThemeWrapper(service, com.google.android.accessibility.utils.R.style.A11yAlertDialogCustomViewTheme),
              R.layout.list_item_simple_framelayout,
              android.R.id.text1,
              items);
      ((ListView) view).setAdapter(listAdapter);
      ((ListView) view)
          .setOnItemClickListener((parent, v, position, id) -> listener.onItemClick(position));
    }
    if (view instanceof RecyclerView) {
      RecyclerViewAdapter recyclerAdapter =
          new RecyclerViewAdapter(ImmutableList.copyOf(items), listener);
      ((RecyclerView) view).setAdapter(recyclerAdapter);
      ((RecyclerView) view).setLayoutManager(new LinearLayoutManager(view.getContext()));
    }

    view.getContext().setTheme(com.google.android.accessibility.utils.R.style.A11yAlertDialogCustomViewTheme);
    if (formFactorUtils.isAndroidWear()) {
      // Support Wear rotary input
      view.requestFocus();
    }
    if (formFactorUtils.isAndroidTv()) {
      view.requestFocus();
    }
    return view;
  }

  private View createListView() {
    if (formFactorUtils.isAndroidWear()) {
      return new NonScrollableListView(service);
    }
    if (formFactorUtils.isAndroidTv()) {
      return new RecyclerView(service);
    }
    return new ListView(service);
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
  @Nullable ContextMenu getContextMenu() {
    return contextMenu;
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

  private static class ListItemViewHolder extends RecyclerView.ViewHolder {
    private final TextView textView;

    public ListItemViewHolder(@NonNull ViewGroup itemView) {
      super(itemView);
      textView = itemView.findViewById(android.R.id.text1);
    }

    public TextView getTextView() {
      return textView;
    }
  }

  private static class RecyclerViewAdapter extends RecyclerView.Adapter<ListItemViewHolder> {
    private final ImmutableList<CharSequence> items;
    private final ListMenuClickListener listener;

    public RecyclerViewAdapter(ImmutableList<CharSequence> items, ListMenuClickListener listener) {
      this.items = items;
      this.listener = listener;
    }

    @Override
    public @NonNull ListItemViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int type) {
      ViewGroup view =
          (ViewGroup)
              LayoutInflater.from(viewGroup.getContext())
                  .inflate(
                      R.layout.list_item_simple_framelayout, viewGroup, /* attachToRoot= */ false);
      return new ListItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ListItemViewHolder listItemViewHolder, int i) {
      listItemViewHolder.getTextView().setText(items.get(i));
      ((ViewGroup) listItemViewHolder.getTextView().getParent())
          .setOnClickListener((v) -> listener.onItemClick(i));
    }

    @Override
    public int getItemCount() {
      return items.size();
    }
  }

  private interface ListMenuClickListener {
    void onItemClick(int index);
  }
}
