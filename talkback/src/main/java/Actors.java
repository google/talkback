/*
 * Copyright (C) 2019 Google Inc.
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

package com.google.android.accessibility.talkback;

import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UNKNOWN;

import com.google.android.accessibility.talkback.Feedback.EditText;
import com.google.android.accessibility.talkback.Feedback.Focus;
import com.google.android.accessibility.talkback.Feedback.FocusDirection;
import com.google.android.accessibility.talkback.Feedback.NodeAction;
import com.google.android.accessibility.talkback.Feedback.Scroll;
import com.google.android.accessibility.talkback.Feedback.Sound;
import com.google.android.accessibility.talkback.Feedback.Speech;
import com.google.android.accessibility.talkback.Feedback.Vibration;
import com.google.android.accessibility.talkback.controller.DirectionNavigationActor;
import com.google.android.accessibility.talkback.focusmanagement.AutoScrollActor;
import com.google.android.accessibility.talkback.focusmanagement.FocusActor;
import com.google.android.accessibility.talkback.screensearch.SearchScreenNodeStrategy;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.SpeechControllerImpl;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Pipeline stage for feedback execution. See  */
class Actors {

  private static final String LOG_TAG = "Actors";

  //////////////////////////////////////////////////////////////////////////
  // Member data
  // TODO: Add more actors for braille, node-actions, UI-actions, text-editing.

  private final SpeechControllerImpl speaker;
  private final FeedbackController soundAndVibration;
  private final AutoScrollActor scroller;
  private final FocusActor focuser;
  private final DirectionNavigationActor directionNavigator;
  private final SearchScreenNodeStrategy searcher;
  private final TextEditActor editor;
  // TODO: Add more actors for braille, UI-actions.

  private final ActorStateWritable actorState;

  //////////////////////////////////////////////////////////////////////////
  // Construction methods

  public Actors(
      SpeechControllerImpl speaker,
      FeedbackController soundAndVibration,
      AutoScrollActor scroller,
      FocusActor focuser,
      DirectionNavigationActor directionNavigator,
      SearchScreenNodeStrategy searchScreenNodeStrategy,
      TextEditActor editor) {

    this.speaker = speaker;
    this.soundAndVibration = soundAndVibration;
    this.scroller = scroller;
    this.focuser = focuser;
    this.directionNavigator = directionNavigator;
    searcher = searchScreenNodeStrategy;
    this.editor = editor;

    actorState =
        new ActorStateWritable(
            speaker.state, scroller.stateReader, focuser.getHistory(), directionNavigator.state);
    // Focuser stores some actor-state in ActorState, because focuser does not use that state
    // internally, only for communication to interpeters.
    this.focuser.setActorState(actorState);
    this.directionNavigator.setActorState(new ActorState(actorState));
  }

  public void setPipelineEventReceiver(Pipeline.EventReceiver pipelineEventReceiver) {
    scroller.setPipelineEventReceiver(pipelineEventReceiver);
  }

  public void setPipelineFeedbackReturner(Pipeline.FeedbackReturner pipelineFeedbackReturner) {
    directionNavigator.setPipeline(pipelineFeedbackReturner);
    editor.setPipeline(pipelineFeedbackReturner);
  }

  public void recycle() {
    actorState.recycle();
  }

  //////////////////////////////////////////////////////////////////////////
  // Pipeline methods

  /** Returns a read-only actor state data structure. */
  public ActorState getState() {
    return new ActorState(actorState);
  }

  /** Executes feedback and modifies actorState. Returns success flag. */
  public boolean act(@Nullable EventId eventId, Feedback.Part part) {
    LogUtils.d(LOG_TAG, "act() eventId=%s part=%s", eventId, part);

    try {
      boolean success = true;

      // Speech
      @Nullable Speech speech = part.speech();
      if (speech != null && speech.action() != null) {
        switch (speech.action()) {
          case SPEAK:
            if (speech.text() != null) {
              speaker.speak(speech.text(), eventId, speech.options());
            }
            break;
          case SAVE_LAST:
            speaker.saveLastUtterance();
            break;
          case COPY_SAVED:
            speaker.copySavedUtteranceToClipboard(eventId);
            break;
          case REPEAT_SAVED:
            speaker.repeatSavedUtterance();
            break;
          case SPELL_SAVED:
            speaker.spellSavedUtterance();
            break;
        }
      }

      // Sound effects
      @Nullable Sound sound = part.sound();
      if (sound != null) {
        soundAndVibration.playAuditory(sound.resourceId(), sound.rate(), sound.volume(), eventId);
      }

      // Vibration
      @Nullable Vibration vibration = part.vibration();
      if (vibration != null) {
        soundAndVibration.playHaptic(vibration.resourceId(), eventId);
      }

      // Text editing
      @Nullable EditText edit = part.edit();
      if (edit != null) {
        switch (edit.action()) {
          case SELECT_ALL:
            success &= editor.selectAll(edit.node(), eventId);
            break;

          case START_SELECT:
            success &= editor.startSelect(edit.node(), eventId);
            break;

          case END_SELECT:
            success &= editor.endSelect(edit.node(), eventId);
            break;

          case COPY:
            success &= editor.copy(edit.node(), eventId);
            break;

          case CUT:
            success &= editor.cut(edit.node(), eventId);
            break;

          case PASTE:
            success &= editor.paste(edit.node(), eventId);
            break;

          case CURSOR_TO_BEGINNING:
            success &= editor.cursorToBeginning(edit.node(), edit.stopSelecting(), eventId);
            break;

          case CURSOR_TO_END:
            success &= editor.cursorToEnd(edit.node(), edit.stopSelecting(), eventId);
            break;

          case INSERT:
            success &= editor.insert(edit.node(), edit.text(), eventId);
            break;
        }
      }

      // Node action
      @Nullable NodeAction nodeAction = part.nodeAction();
      AccessibilityNode nodeActionTarget = (nodeAction == null) ? null : nodeAction.target();
      if (nodeAction != null && nodeActionTarget != null) {
        success &= nodeActionTarget.performAction(nodeAction.actionId(), eventId);
      }

      // Scrolling
      @Nullable Scroll scroll = part.scroll();
      if (scroll != null) {
        switch (scroll.action()) {
          case SCROLL:
            success &=
                scroller.scroll(
                    scroll.userAction(),
                    scroll.node(),
                    scroll.nodeCompat(),
                    scroll.nodeAction(),
                    scroll.source(),
                    eventId);
            break;

          case CANCEL_TIMEOUT:
            scroller.cancelTimeout();
            break;

          default:
            // Do nothing.
        }
      }

      // Focus
      @Nullable Focus focus = part.focus();
      if (focus != null && focus.action() != null) {
        switch (focus.action()) {
          case FOCUS:
            if (focus.target() != null) {
              success &=
                  focuser.setAccessibilityFocus(
                      focus.target(), focus.forceRefocus(), focus.focusActionInfo(), eventId);
            }
            break;

          case HTML_DIRECTION:
            if (focus.htmlElementType() != null && focus.direction() != SEARCH_FOCUS_UNKNOWN) {
              success &=
                  focuser.navigateToHtmlElement(
                      focus.start(),
                      focus.direction(),
                      focus.htmlElementType(),
                      focus.focusActionInfo(),
                      eventId);
            }
            break;
          case CLEAR:
            focuser.clearAccessibilityFocus(eventId);
            break;
          case CACHE:
            success &= focuser.cacheNodeToRestoreFocus();
            break;
          case MUTE_NEXT_FOCUS:
            focuser.setMuteNextFocus();
            break;
          case RESTORE_ON_NEXT_WINDOW:
            focuser.overrideNextFocusRestorationForContextMenu();
            break;
          case RESTORE:
            success &= focuser.restoreFocus(eventId);
            break;
          case CLEAR_CACHED:
            success &= focuser.popCachedNodeToRestoreFocus();
            break;
          case CLICK:
            success &= focuser.clickCurrentFocus(eventId);
            break;
          case LONG_CLICK:
            success &= focuser.longClickCurrentFocus(eventId);
            break;
          case CLICK_ANCESTOR:
            success &= focuser.clickCurrentHierarchical(eventId);
            break;
          case SEARCH_FROM_TOP:
            if (focus.searchKeyword() != null) {
              success &=
                  searcher.searchAndFocus(
                      /* startAtRoot= */ true, focus.searchKeyword(), directionNavigator);
            }
            break;
          case SEARCH_AGAIN:
            success &=
                searcher.searchAndFocus(
                    /* startAtRoot= */ false, searcher.getLastKeyword(), directionNavigator);
            break;
        }
      }

      // FocusDirection
      @Nullable FocusDirection direction = part.focusDirection();
      if (direction != null) {
        switch (direction.action()) {
          case NEXT:
            directionNavigator.navigateWithSpecifiedGranularity(
                SEARCH_FOCUS_FORWARD, direction.granularity(), direction.inputMode(), eventId);
            break;
          case FOLLOW:
            directionNavigator.followTo(direction.followNode(), direction.direction(), eventId);
            break;
          case NEXT_PAGE:
            directionNavigator.more(eventId);
            break;
          case PREVIOUS_PAGE:
            directionNavigator.less(eventId);
            break;
          case TOP:
            directionNavigator.jumpToTop(direction.inputMode(), eventId);
            break;
          case BOTTOM:
            directionNavigator.jumpToBottom(direction.inputMode(), eventId);
            break;
          case SET_GRANULARITY:
            directionNavigator.setGranularity(
                direction.granularity(), direction.fromUser(), eventId);
            break;
          case SAVE_GRANULARITY:
            directionNavigator.saveGranularityForContinuousReading();
            break;
          case APPLY_SAVED_GRANULARITY:
            directionNavigator.applySavedGranularityForContinuousReading(eventId);
            break;
          case CLEAR_SAVED_GRANULARITY:
            directionNavigator.clearSavedGranularityForContinuousReading();
            break;
          case NEXT_GRANULARITY:
            directionNavigator.nextGranularity(eventId);
            break;
          case PREVIOUS_GRANULARITY:
            directionNavigator.previousGranularity(eventId);
            break;
          case SELECTION_MODE_ON:
            directionNavigator.setSelectionModeActive(direction.selectionNode(), eventId);
            break;
          case SELECTION_MODE_OFF:
            directionNavigator.setSelectionModeInactive();
            break;

          case NAVIGATE:
            if (direction.toWindow()) {
              success &=
                  directionNavigator.navigateToNextOrPreviousWindow(
                      direction.direction(),
                      direction.defaultToInputFocus(),
                      direction.inputMode(),
                      eventId);
            } else if (direction.hasHtmlTargetType()) {
              success &=
                  directionNavigator.navigateToHtmlElement(
                      direction.htmlTargetType(),
                      direction.direction(),
                      direction.inputMode(),
                      eventId);
            } else if (direction.granularity() != null) {
              success &=
                  directionNavigator.navigateWithSpecifiedGranularity(
                      direction.direction(),
                      direction.granularity(),
                      direction.inputMode(),
                      eventId);
            } else {
              success &=
                  directionNavigator.navigate(
                      direction.direction(),
                      direction.wrap(),
                      direction.scroll(),
                      direction.defaultToInputFocus(),
                      direction.inputMode(),
                      eventId);
            }
            break;
        }
      }

      return success;
    } finally {
      part.recycle();
    }
  }

  ///////////////////////////////////////////////////////////////////////////////
  // Start and stop methods

  public void onBoot(boolean quiet) {
    speaker.updateTtsEngine(quiet);
  }

  public void onUnbind(float finalAnnouncementVolume) {
    // Main thread will be waiting during the TTS announcement, thus in this special case we should
    // not handle TTS callback in main thread.
    speaker.setHandleTtsCallbackInMainThread(false);
    // TalkBack is not allowed to display overlay at this state.
    speaker.setOverlayEnabled(false);
    speaker.setSpeechVolume(finalAnnouncementVolume);
  }

  public void interruptAllFeedback(boolean stopTtsSpeechCompletely) {
    speaker.interrupt(stopTtsSpeechCompletely);
    soundAndVibration.interrupt();
  }

  public void interruptSoundAndVibration() {
    soundAndVibration.interrupt();
  }

  public void shutdown() {
    speaker.shutdown();
  }

  /////////////////////////////////////////////////////////////////////////////////
  // Parameter setting pass-through methods
  // Keeping preference logic outside actors, in specific accessibility-service code.

  public void setOverlayEnabled(boolean enabled) {
    speaker.setOverlayEnabled(enabled);
  }

  public void setUseIntonation(boolean use) {
    speaker.setUseIntonation(use);
  }

  public void setSpeechPitch(float pitch) {
    speaker.setSpeechPitch(pitch);
  }

  public void setSpeechRate(float rate) {
    speaker.setSpeechRate(rate);
  }

  public void setUseAudioFocus(boolean use) {
    speaker.setUseAudioFocus(use);
  }

  public void setSpeechVolume(float volume) {
    speaker.setSpeechVolume(volume);
  }
}
