/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.android.accessibility.switchaccess.feedback;

import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.switchaccess.SwitchAccessService;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.compositor.EventInterpretation;
import com.google.android.accessibility.compositor.HintEventInterpretation;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.SwitchAccessNodeCompat;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceUtils;
import com.google.android.accessibility.switchaccess.feedback.SwitchAccessFeedbackController.OnUtteranceCompleteListener;
import com.google.android.accessibility.switchaccess.treenodes.NonActionableItemNode;
import com.google.android.accessibility.switchaccess.treenodes.OverlayActionNode;
import com.google.android.accessibility.switchaccess.treenodes.ShowActionsMenuNode;
import com.google.android.accessibility.switchaccess.treenodes.ShowGlobalMenuNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanLeafNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanSelectionNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanSystemProvidedNode;
import com.google.android.accessibility.switchaccess.utils.FeedbackUtils;
import com.google.android.accessibility.utils.compat.CompatUtils;
import com.google.android.accessibility.utils.feedback.AccessibilityHintsManager;
import com.google.android.accessibility.utils.feedback.HintEventListener;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechControllerImpl;
import com.google.android.libraries.accessibility.utils.concurrent.ThreadUtils;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Controller used to provide spoken feedback for the highlighted items. This feedback can include
 * the content description or text of the highlighted items, and the usage hints for actionable
 * items.
 */
public class SwitchAccessHighlightFeedbackController implements HintEventListener {

  private final Context context;
  private final Compositor compositor;
  private final SpeechControllerImpl speechController;
  private final AccessibilityHintsManager hintsManager;

  private OnUtteranceCompleteListener utteranceCompleteListener;

  // Whether the last completed speech is the last spoken feedback speech for the highlighted
  // item. This variable is used to inform listeners when speech completes for the currently
  // highlighted item(s). If the "Speak usage hints" option is enabled, two speeches will be
  // spoken for each actionable item. The first speech describes the item (e.g. "Switch Access
  // Menu, Button"). After a short delay, the second speech will be started to give hints of
  // available actions. If auto-scan is enabled, Switch Access will only automatically scan the
  // next item after the second speech is spoken.
  private boolean isLastSpeech = false;

  // Whether Switch Access should provide spoken feedback for the global Switch Access Menu button
  // when onWindowChangeStarted() is called.
  private boolean isGlobalMenuButtonFeedbackPending = false;

  // Root node of the tree which consists all nodes in the currently highlighted groups. Switch
  // Access will provide spoken feedback for the highlighted groups when onWindowChangeStarted()
  // is called. This is done to avoid canceling feedback when drawing the menu button triggers a
  // window change event.
  @Nullable private TreeScanNode groupFeedbackPendingRoot;

  // Whether the currently highlighted node has multiple actions.
  private boolean currentNodeHasMultipleActions = false;

  // Global menu feedback is spoken after a window change event, but when auto-scanning the menu
  // button is immediately re-highlighted after a scanning loop completes. This means that the menu
  // button is not hidden nor shown, so no window change event is processed. Therefore, to prevent
  // auto-scanning from getting stuck on the global menu button when spoken feedback is enabled,
  // add a small delay to ensure that spoken feedback for the global menu button is spoken, even
  // if no window change event is called.
  private static final int
      DELAY_BEFORE_ATTEMPTING_TO_SPEAK_GLOBAL_MENU_FEEDBACK_WITHOUT_WINDOW_CHANGE_EVENT_MS = 1000;

  public SwitchAccessHighlightFeedbackController(
      Context context,
      Compositor compositor,
      SpeechControllerImpl speechController,
      AccessibilityHintsManager accessibilityHintsManager) {
    this.context = context;
    this.compositor = compositor;
    this.speechController = speechController;
    hintsManager = accessibilityHintsManager;
  }

  /**
   * Set a listener to be notified when speech ends, regardless of the reason (error, completion, or
   * interruption). The listener is guaranteed to be called at least once after each call to {@link
   * SwitchAccessHighlightFeedbackController#speakFeedback}. Note: Only the last listener to be set
   * will be notified.
   */
  void setOnUtteranceCompleteListener(OnUtteranceCompleteListener listener) {
    utteranceCompleteListener = listener;
  }

  /**
   * Provides the spoken feedback for the nodes rooted at the given {@link TreeScanNode}.
   *
   * @param root The root of all nodes that correspond to the currently highlighted items
   * @param isFocusingFirstNodeInTree Whether the given {@link TreeScanNode} is the first node to be
   *     highlighted; if true, this means that we have also just drawn the Menu button
   * @param isSwitchAccessMenuVisible Whether the currently highlighted items are on the Switch
   *     Access menu; if true, this means that we didn't draw the Menu button
   */
  void speakFeedback(
      TreeScanNode root, boolean isFocusingFirstNodeInTree, boolean isSwitchAccessMenuVisible) {
    currentNodeHasMultipleActions = false;
    // If only one item is being highlighted, Compositor will be used to provide descriptive spoken
    // feedback for the item. The feedback could include both a description of the type, state,
    // and name of the item, and a usage hint if the item is actionable.
    List<TreeScanLeafNode> children = ((TreeScanSelectionNode) root).getChild(0).getNodesList();
    boolean isGroupSelectionEnabled = SwitchAccessPreferenceUtils.isGroupSelectionEnabled(context);

    if (children.size() == 1 && !isGroupSelectionEnabled) {
      speakFeedbackWithCompositor(children.get(0));
      return;
    }

    // When we scan items on the Switch Access menu, the Switch Access global menu button is not
    // shown. As a result, no window change events are triggered when scanning starts, and
    // #onWindowChangeStarted is not called. Therefore, when the Switch Access menu is visible,
    // instead of providing spoken feedback when #onWindowChangeStarted is called, we provide spoken
    // feedback immediately.
    if (isGroupSelectionEnabled && isFocusingFirstNodeInTree && !isSwitchAccessMenuVisible) {
      groupFeedbackPendingRoot = root;
      return;
    }

    // If multiple items are being highlighted, Switch Access will provide general spoken feedback,
    // which is the title or content description of the highlighted items.
    speakFeedbackGeneral(root);
  }

  /**
   * Provides spoken feedback for the highlighted item or groups after a window change event. This
   * method should be called after the Switch Access menu button is drawn to avoid canceling
   * feedback when drawing the menu button triggers a window change event.
   */
  void speakPendingFeedbackAfterWindowChangeStarted() {
    speakPendingGlobalMenuButtonFeedback();

    if (groupFeedbackPendingRoot != null) {
      speakFeedbackGeneral(groupFeedbackPendingRoot);
      groupFeedbackPendingRoot = null;
    }
  }

  /** Called after focus is cleared. */
  void onFocusCleared() {
    // When focus is cleared, reset isGlobalMenuButtonFeedbackPending and groupFeedbackPendingRoot.
    // This guarantees that the spoken feedback for the Switch Access global menu button and group
    // selection that is generated at the time when the screen changes is not spoken after the tree
    // is rebuilt.
    isGlobalMenuButtonFeedbackPending = false;
    groupFeedbackPendingRoot = null;
  }

  /** Called after a speech is completed. */
  void onSpeechCompleted() {
    if (utteranceCompleteListener != null && isLastSpeech) {
      utteranceCompleteListener.onUtteranceComplete();
      isLastSpeech = false;
    }
  }

  // {@link HintEventListener} overrides

  @Override
  public void onFocusHint(
      int eventType,
      AccessibilityNodeInfoCompat accessibilityNodeInfoCompat,
      boolean hintForcedFeedbackAudioPlaybackActive,
      boolean hintForcedFeedbackMicrophoneActive) {
    // Make sure that AccessibilityNodeInfoCompats that correspond to OverlayActionNodes have their
    // original event type, TYPE_VIEW_ACCESSIBILITY_FOCUSED. This ensures that the hint is spoken.
    //
    // When an OverlayActionNode is selected, we change the type of the accompanying event from
    // TYPE_VIEW_ACCESSIBILITY_FOCUSED to TYPE_VIEW_FOCUSED. This ensures that the hint for the
    // OverlayActionNode is not canceled because of window state changes. Here, we need to
    // convert the event type back to its original type, TYPE_VIEW_ACCESSIBILITY_FOCUSED.
    if (accessibilityNodeInfoCompat != null
        && OverlayActionNode.class.getName().equals(accessibilityNodeInfoCompat.getClassName())
        && eventType == TYPE_VIEW_FOCUSED) {
      eventType = AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
    }

    @HintEventInterpretation.HintType
    int hintEventType =
        (eventType == TYPE_VIEW_FOCUSED)
            ? HintEventInterpretation.HINT_TYPE_INPUT_FOCUS
            : HintEventInterpretation.HINT_TYPE_ACCESSIBILITY_FOCUS;
    HintEventInterpretation hintInterp = new HintEventInterpretation(hintEventType);
    hintInterp.setForceFeedbackAudioPlaybackActive(hintForcedFeedbackAudioPlaybackActive);
    hintInterp.setForceFeedbackMicropphoneActive(hintForcedFeedbackMicrophoneActive);
    EventInterpretation eventInterp = new EventInterpretation(Compositor.EVENT_SPEAK_HINT);
    eventInterp.setHint(hintInterp);
    eventInterp.setHasMultipleSwitchAccessActions(currentNodeHasMultipleActions);

    // Send event to compositor to speak feedback.
    compositor.handleEvent(accessibilityNodeInfoCompat, EVENT_ID_UNTRACKED, eventInterp);
    isLastSpeech = true;
  }

  @Override
  public void onScreenHint(CharSequence charSequence) {
    HintEventInterpretation hintInterpretation =
        new HintEventInterpretation(HintEventInterpretation.HINT_TYPE_SCREEN);
    hintInterpretation.setText(charSequence);
    EventInterpretation eventInterpretation = new EventInterpretation(Compositor.EVENT_SPEAK_HINT);
    eventInterpretation.setHint(hintInterpretation);
    compositor.handleEvent(EVENT_ID_UNTRACKED, eventInterpretation);
  }

  private void speakFeedbackForGlobalMenuButton(boolean speakHints) {
    // Manually construct an AccessibilityEvent and an AccessibilityNodeInfoCompat, which will be
    // used by the compositor to compose spoken feedback, for the global Switch Access menu button.
    final AccessibilityEvent event = AccessibilityEvent.obtain();
    final AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();

    event.setEventType(TYPE_VIEW_FOCUSED);
    node.setEnabled(true);
    node.setContentDescription(
        context.getString(R.string.option_scanning_menu_button_content_description));
    node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
    node.setClassName(OverlayActionNode.class.getName());
    CompatUtils.invoke(
        node,
        null,
        CompatUtils.getMethod(AccessibilityNodeInfo.class, "setSealed", boolean.class),
        true);
    SwitchAccessNodeCompat nodeInfoCompat = new SwitchAccessNodeCompat(node);

    hintsManager.postHintForNode(event, nodeInfoCompat);

    compositor.handleEvent(
        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED, nodeInfoCompat, EVENT_ID_UNTRACKED);

    isLastSpeech = !speakHints;
  }

  /**
   * Given a {@link TreeScanLeafNode} that corresponds to the highlighted item on the screen,
   * provides user visible spoken feedback, which include both descriptions and usage hints of the
   * item, for blind and vision-impaired users. The feedback is generated and spoken using a {@link
   * Compositor} instance.
   *
   * <p>This method is called when the "Speak usage hints" option is enabled and only one item is
   * being highlighted.
   *
   * @param focus The {@link TreeScanLeafNode} that corresponds to the highlighted item on the
   *     screen
   */
  private void speakFeedbackWithCompositor(TreeScanLeafNode focus) {
    SwitchAccessNodeCompat nodeInfoCompat;
    AccessibilityEvent event = AccessibilityEvent.obtain();

    if (focus instanceof TreeScanSystemProvidedNode) {
      nodeInfoCompat = ((TreeScanSystemProvidedNode) focus).getNodeInfoCompat();
      event.setEventType(AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
    } else if (focus instanceof OverlayActionNode) {
      // The OverlayActionNode (e.g., the Switch Access Menu button) doesn't have any
      // AccessibilityNodeInfoCompat. In order to pass the node compat info associated such items to
      // the Compositor, we manually construct an AccessibilityNodeInfoCompat for the item.
      final AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();
      node.setEnabled(true);
      node.setContentDescription(focus.getSpeakableText().toString());
      node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
      node.setClassName(OverlayActionNode.class.getName());
      CompatUtils.invoke(
          node,
          null,
          CompatUtils.getMethod(AccessibilityNodeInfo.class, "setSealed", boolean.class),
          true);
      nodeInfoCompat = new SwitchAccessNodeCompat(node);
      // TYPE_VIEW_FOCUSED normally corresponds to input focus being placed, but we use this to
      // ensure the usage hint for this node is not canceled after window state changes.
      //
      // Selecting an OverlayActionNode triggers screen changes (e.g., selecting the
      // ShowGlobalMenuNode shows the Switch Access Menu button, triggering a sequence of window
      // change events). When the window state changes, {@link
      // AccessibilityHintsManager#onScreenStateChanged} is called to cancel pending hints that
      // correspond to window events with types other than TYPE_VIEW_FOCUSED. So, we set the event
      // type to TYPE_VIEW_FOCUSED to make sure that the hint is spoken.
      event.setEventType(TYPE_VIEW_FOCUSED);
    } else {
      // The node is an ClearFocusNode, which does nothing and just allows focus to be cleared.
      // Therefore, no spoken feedback should be given.
      return;
    }

    // If the current node is ShowGlobalMenuNode, we will provide spoken feedback for the global
    // menu button when onWindowChangeStarted() is called. This is because when ShowGlobalMenuNode
    // is selected, the Switch Access global menu button will be highlighted, and Switch Access
    // should speak a speech for the menu button. However, selecting ShowGlobalMenuNode also
    // triggers screen changes. When screen changes, OnWindowChangeStarted() will be called to
    // interrupt the speech controller and stop all speech, including the speech for the global menu
    // button. Therefore, we delay speaking the speech for the global menu button until
    // onWindowChangeStarted() is called.
    isGlobalMenuButtonFeedbackPending = focus instanceof ShowGlobalMenuNode;
    if (isGlobalMenuButtonFeedbackPending) {
      // When auto-scanning with spoken feedback, #onWindowChangeStarted is not called after the
      // first scanning loop because the menu button is already visible. Therefore, add a small
      // delay to ensure that spoken feedback is spoken for the menu button, even if it was already
      // visible.
      ThreadUtils.runOnMainThreadDelayed(
          SwitchAccessService::isActive,
          this::speakPendingGlobalMenuButtonFeedback,
          DELAY_BEFORE_ATTEMPTING_TO_SPEAK_GLOBAL_MENU_FEEDBACK_WITHOUT_WINDOW_CHANGE_EVENT_MS);

      return;
    }

    // Post a usage hint about the node. The hint will be spoken if this node is actionable and the
    // speak usage hint option is enabled. The hint will be spoken after the next utterance is
    // completed. Therefore, we post the hint right before we speak the description of the node.
    hintsManager.postHintForNode(event, nodeInfoCompat);

    if (nodeInfoCompat.isScrollable()) {
      // Announce “scrollable” for scrollable items.
      speechController.speak(
          context.getString(R.string.switch_access_spoken_feedback_item_scrollable),
          SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH,
          0, /* flags */
          null, /* speechParams */
          null); /* eventId */
    }

    if (focus instanceof ShowActionsMenuNode) {
      currentNodeHasMultipleActions =
          ((ShowActionsMenuNode) focus).hasMultipleSwitchAccessActions();
    }
    // Speak the description (name, state, and type) of the node.
    compositor.handleEvent(
        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED, nodeInfoCompat, EVENT_ID_UNTRACKED);

    // Explicitly checking whether the item has text misses cases where the compositor can provide
    // feedback based on the item's type. Instead, call the Compositor to provide feedback, then
    // check whether speech is being provided.
    if (!speechController.isSpeakingOrSpeechQueued()) {
      speechController.speak(focus.getSpeakableText().toString(), null, null);
    }

    // If the "Speak usage hints" option is disabled, or the highlighted item is non-actionable,
    // the utterance spoken is the last spoken feedback utterance for the item. Otherwise, another
    // utterance about available actions will be spoken after a timeout.
    isLastSpeech =
        !SwitchAccessPreferenceUtils.shouldSpeakHints(context)
            || (focus instanceof NonActionableItemNode);
  }

  /**
   * Provides general spoken feedback, which is the content description or title, for all {@link
   * TreeScanLeafNode}s rooted at the given {@link TreeScanNode}. The feedback is generated by
   * calling methods in {@link FeedbackUtils}.
   *
   * <p>This method is called when the "Speak usage hints" option is disabled, or multiple items are
   * being highlighted.
   *
   * @param root The root node of a node tree. The tree consists all currently highlighted {@link
   *     TreeScanLeafNode}s.
   */
  private void speakFeedbackGeneral(TreeScanNode root) {
    List<CharSequence> speakableText =
        FeedbackUtils.getSpeakableTextForTree(
            context,
            (TreeScanSelectionNode) root,
            SwitchAccessPreferenceUtils.isGroupSelectionEnabled(context),
            SwitchAccessPreferenceUtils.shouldSpeakFirstAndLastItem(context),
            SwitchAccessPreferenceUtils.shouldSpeakNumberOfItems(context),
            SwitchAccessPreferenceUtils.shouldSpeakAllItems(context),
            SwitchAccessPreferenceUtils.shouldSpeakHints(context));

    for (CharSequence charSequence : speakableText) {
      speechController.speak(charSequence, null, null);
    }
    isLastSpeech = true;
  }

  private void speakPendingGlobalMenuButtonFeedback() {
    if (isGlobalMenuButtonFeedbackPending) {
      speakFeedbackForGlobalMenuButton(SwitchAccessPreferenceUtils.shouldSpeakHints(context));
      isGlobalMenuButtonFeedbackPending = false;
    }
  }
}
