/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.android.accessibility.utils;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.content.res.Configuration.ORIENTATION_UNDEFINED;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Locale.Category;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Utility class for tracking performance statistic per various event types & processing stages.
 *
 * <p>Stages are not strictly sequential, but rather overlap.
 *
 * <p>Latency statistics for {@code STAGE_FEEDBACK_HEARD} is currently inaccurate, as event-to-audio
 * matching is approximate.
 */
public class Performance {

  private static final String TAG = "Performance";

  private static final Logger DEFAULT_LOGGER = (format, args) -> LogUtils.v(TAG, format, args);

  // Randomly choose the threshold first. Feel free to change it.
  private static final long TTS_LATENCY_THRESHOLD_MS = 500;
  private static final long FEEDBACK_COMPOSED_THRESHOLD_MS = 150;
  private static final long FEEDBACK_QUEUED_THRESHOLD_MS = 1000;
  private static final long FEEDBACK_HEARD_THRESHOLD_MS = 1000;

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Constants

  /** Stages that each event goes through, where we want to measure latency. */
  @IntDef({
    STAGE_FRAMEWORK,
    STAGE_INLINE_HANDLING,
    STAGE_FEEDBACK_QUEUED,
    STAGE_FEEDBACK_HEARD,
    STAGE_BETWEEN_FEEDBACK_QUEUED_AND_FEEDBACK_HEARD,
    STAGE_FEEDBACK_COMPOSED,
  })
  public @interface StageId {}

  public static final int STAGE_FRAMEWORK = 0; // Latency before TalkBack
  public static final int STAGE_INLINE_HANDLING = 1; // Time during synchronous event handlers
  public static final int STAGE_FEEDBACK_QUEUED = 2; // Time until first speech is queued
  public static final int STAGE_FEEDBACK_HEARD = 3; // Time until speech is heard.
  public static final int STAGE_BETWEEN_FEEDBACK_QUEUED_AND_FEEDBACK_HEARD =
      4; // Time between speech is queued and heard.
  public static final int STAGE_FEEDBACK_COMPOSED = 5; // Time until speech is composed.
  public static final ImmutableList<String> STAGE_NAMES =
      ImmutableList.of(
          "STAGE_FRAMEWORK",
          "STAGE_INLINE_HANDLING",
          "STAGE_FEEDBACK_QUEUED",
          "STAGE_FEEDBACK_HEARD",
          "STAGE_BETWEEN_FEEDBACK_QUEUED_AND_FEEDBACK_HEARD",
          "STAGE_FEEDBACK_COMPOSED");

  /**
   * Event types for which we want to measure latency.
   *
   * <p>The purpose of this event type is to uniquely identify an event occurring at a point in
   * time. Each event type has sub-types, since many AccessibilityEvent or KeyEvent may occur at the
   * same time, differentiated only by their sub-types.
   *
   * <p>EVENT_TYPE_ACCESSIBILITY: Subtype is AccessibilityEvent event type, from getEventType().
   * EVENT_TYPE_KEY: Subtype is key-code. EVENT_TYPE_KEY_COMBO: Subtype is combo id.
   * EVENT_TYPE_VOLUME_KEY_COMBO: Subtype is combo id. EVENT_TYPE_GESTURE: Subtype is gesture id
   * (perhaps unnecessary since concurrent gestures have not been observed).
   * EVENT_TYPE_FINGERPRINT_GESTURE: Subtype is gesture id. EVENT_TYPE_ROTATE: Subtype is
   * orientation.
   */
  @IntDef({
    EVENT_TYPE_ACCESSIBILITY,
    EVENT_TYPE_KEY,
    EVENT_TYPE_KEY_COMBO,
    EVENT_TYPE_VOLUME_KEY_COMBO,
    EVENT_TYPE_GESTURE,
    EVENT_TYPE_ROTATE,
    EVENT_TYPE_FINGERPRINT_GESTURE
  })
  public @interface EventTypeId {}

  public static final int EVENT_TYPE_ACCESSIBILITY = 0;
  public static final int EVENT_TYPE_KEY = 1;
  public static final int EVENT_TYPE_KEY_COMBO = 2;
  public static final int EVENT_TYPE_VOLUME_KEY_COMBO = 3;
  public static final int EVENT_TYPE_GESTURE = 4;
  public static final int EVENT_TYPE_ROTATE = 5;
  public static final int EVENT_TYPE_FINGERPRINT_GESTURE = 6;
  public static final ImmutableList<String> EVENT_TYPE_NAMES =
      ImmutableList.of(
          "EVENT_TYPE_ACCESSIBILITY",
          "EVENT_TYPE_KEY",
          "EVENT_TYPE_KEY_COMBO",
          "EVENT_TYPE_VOLUME_KEY_COMBO",
          "EVENT_TYPE_GESTURE",
          "EVENT_TYPE_ROTATE",
          "EVENT_TYPE_FINGERPRINT_GESTURE");

  public static final @Nullable EventId EVENT_ID_UNTRACKED = null;

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Member data

  /** Enable to track the latency and compute the statistics accordingly. */
  private boolean computeStatsEnabled = false;

  /** Recent events for which we are collecting stage latencies */
  protected static final int MAX_RECENT_EVENTS = 100;

  protected ArrayDeque<EventId> eventQueue = new ArrayDeque<>();
  protected HashMap<EventId, EventData> eventIndex = new HashMap<>();
  private final HashMap<String, EventId> utteranceToEvent = new HashMap<>();
  protected final Object lockRecentEvents = new Object();

  /** Latency statistics for various event/label types */
  protected HashMap<StatisticsKey, Statistics> labelToStats = new HashMap<>();

  /**
   * Latency statistics for detecting gesture which could be in framework or Talkback side. we
   * should do this testing only on the default display for convenience.
   */
  protected SparseArray<Statistics> gestureDetectionToStats = new SparseArray<>();

  protected final Object lockGestureDetectionToStats = new Object();

  /**
   * The time interaction start obtained from the event time of {@link
   * AccessibilityEvent#TYPE_TOUCH_INTERACTION_START}, which is {@link SystemClock#uptimeMillis()}
   * stamp time base
   */
  private long timeInteractionStart;

  protected final Object lockLabelToStats = new Object();
  protected Statistics allEventStats = new Statistics();

  private final ArrayMap<LatencyTracker, Executor> latencyTrackers = new ArrayMap<>();

  private static final Performance instance = new Performance();

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public static Performance getInstance() {
    return instance;
  }

  protected Performance() {}

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Simple getters/setters

  public boolean getComputeStatsEnabled() {
    return computeStatsEnabled;
  }

  public void setComputeStatsEnabled(boolean computeStatsEnabled) {
    this.computeStatsEnabled = computeStatsEnabled;
  }

  public Statistics getAllEventStats() {
    return allEventStats;
  }

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Methods to track events

  /**
   * Method to start tracking processing latency for an event. Uses event type as statistics
   * segmentation label.
   *
   * @param event An event just received by TalkBack
   * @return An event id that can be used to track performance through later stages.
   */
  public EventId onEventReceived(@NonNull AccessibilityEvent event) {
    @NonNull EventId eventId = toEventId(event);
    if (!trackEvents()) {
      return eventId;
    }

    if (event.getEventType() == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
      timeInteractionStart = event.getEventTime();
    }

    // Segment events based on type.
    String label = AccessibilityEventUtils.typeToString(event.getEventType());
    String[] labels = {label};

    onEventReceived(eventId, labels);
    return eventId;
  }

  /**
   * Method to start tracking processing latency for a key event.
   *
   * @param event A key event just received by TalkBack
   * @return An event id that can be used to track performance through later stages.
   */
  public EventId onEventReceived(@NonNull KeyEvent event) {
    int keycode = event.getKeyCode();
    EventId eventId = toEventId(event);
    if (!trackEvents()) {
      return eventId;
    }

    // Segment key events based on key groups.
    String label = "KeyEvent-other";
    if (KeyEvent.KEYCODE_0 <= keycode && keycode <= KeyEvent.KEYCODE_9) {
      label = "KeyEvent-numeric";
    } else if (KeyEvent.KEYCODE_A <= keycode && keycode <= KeyEvent.KEYCODE_Z) {
      label = "KeyEvent-alpha";
    } else if (KeyEvent.KEYCODE_DPAD_UP <= keycode && keycode <= KeyEvent.KEYCODE_DPAD_CENTER) {
      label = "KeyEvent-dpad";
    } else if (KeyEvent.KEYCODE_VOLUME_UP <= keycode && keycode <= KeyEvent.KEYCODE_VOLUME_DOWN) {
      label = "KeyEvent-volume";
    }
    String[] labels = {label};

    onEventReceived(eventId, labels);
    return eventId;
  }

  protected void onEventReceived(@NonNull EventId eventId, String[] labels) {
    if (!trackEvents()) {
      return;
    }

    // Create event data.
    EventData eventData = new EventData(getTime(), getUptime(), labels, eventId);

    // Collect event data.
    addRecentEvent(eventId, eventData);
    trimRecentEvents(MAX_RECENT_EVENTS);

    if (!computeStatsEnabled) {
      return;
    }
    @StageId int prevStage = STAGE_INLINE_HANDLING - 1;
    long prevStageLatency = getUptime() - eventId.getEventTimeMs(); // Event times are uptime.
    allEventStats.increment(prevStageLatency);

    // Increment statistics for each event label.
    if (eventData.labels != null) {
      int numLabels = eventData.labels.length;
      for (int labelIndex = 0; labelIndex < numLabels; ++labelIndex) {
        String label = eventData.labels[labelIndex];
        Statistics stats = getOrCreateStatistics(label, prevStage);
        stats.increment(prevStageLatency);
      }
    }
  }

  /**
   * Constructs an EventId without tracking the event's times. Useful for recreating an id from an
   * event that was tracked by onEventReceived(), when the event is available but id is not. Try to
   * use this as little as possible, and instead pass the EventId from onEventReceived().
   *
   * @param event Event that has already been tracked by onEventReceived()
   * @return EventId of event
   */
  @NonNull
  public EventId toEventId(@NonNull AccessibilityEvent event) {
    return new EventId(event.getEventTime(), EVENT_TYPE_ACCESSIBILITY, event.getEventType());
  }

  @NonNull
  public EventId toEventId(@NonNull KeyEvent event) {
    return new EventId(event.getEventTime(), EVENT_TYPE_KEY, event.getKeyCode());
  }

  /**
   * Method to start tracking processing latency for a gesture event. Uses event type as statistics
   * segmentation label.
   *
   * @param gestureId A gesture just recognized by TalkBack
   * @return An event id that can be used to track performance through later stages.
   */
  public EventId onGestureEventReceived(int gestureId) {
    EventId eventId = new EventId(getUptime(), EVENT_TYPE_GESTURE, gestureId);
    if (!trackEvents()) {
      return eventId;
    }

    if (computeStatsEnabled) {
      logGestureDetectionDuration(gestureId);
    }
    // Segment events based on gesture id.
    String label = AccessibilityServiceCompatUtils.gestureIdToString(gestureId);
    String[] labels = {label};

    onEventReceived(eventId, labels);
    return eventId;
  }

  private void logGestureDetectionDuration(int gestureId) {
    synchronized (lockGestureDetectionToStats) {
      Statistics statistics = gestureDetectionToStats.get(gestureId);
      if (statistics == null) {
        statistics = new Statistics();
        gestureDetectionToStats.put(gestureId, statistics);
      }
      statistics.increment(getUptime() - timeInteractionStart);
    }
  }

  /**
   * Method to start tracking processing latency for a fingerprint gesture event. Uses event type as
   * statistics segmentation label.
   *
   * @param fingerprintGestureId A fingerprint gesture just recognized by TalkBack
   * @return An event id that can be used to track performance through later stages.
   */
  public EventId onFingerprintGestureEventReceived(int fingerprintGestureId) {
    EventId eventId =
        new EventId(getUptime(), EVENT_TYPE_FINGERPRINT_GESTURE, fingerprintGestureId);
    if (!trackEvents()) {
      return eventId;
    }

    // Segment events based on fingerprint gesture id.
    String[] labels = {
      AccessibilityServiceCompatUtils.fingerprintGestureIdToString(fingerprintGestureId)
    };

    onEventReceived(eventId, labels);
    return eventId;
  }

  public EventId onKeyComboEventReceived(int keyComboId) {
    EventId eventId = new EventId(getUptime(), EVENT_TYPE_KEY_COMBO, keyComboId);
    if (!trackEvents()) {
      return eventId;
    }

    // Segment events based on key combo id.
    String label = Integer.toString(keyComboId);
    String[] labels = {label};

    onEventReceived(eventId, labels);
    return eventId;
  }

  public EventId onVolumeKeyComboEventReceived(int keyComboId) {
    EventId eventId = new EventId(getUptime(), EVENT_TYPE_VOLUME_KEY_COMBO, keyComboId);
    if (!trackEvents()) {
      return eventId;
    }

    // Segment events based on key combo id.
    String label = Integer.toString(keyComboId);
    String[] labels = {label};

    onEventReceived(eventId, labels);
    return eventId;
  }

  public EventId onRotateEventReceived(int orientation) {
    EventId eventId = new EventId(getUptime(), EVENT_TYPE_ROTATE, orientation);
    if (!trackEvents()) {
      return eventId;
    }

    // Segment events based on orientation.
    String[] labels = {orientationToSymbolicName(orientation)};

    onEventReceived(eventId, labels);
    return eventId;
  }

  /**
   * Track event latency between receiving event, and finishing synchronous event handling.
   *
   * @param eventId Identity of an event just handled by TalkBack
   */
  public void onHandlerDone(@NonNull EventId eventId) {
    if (!trackEvents()) {
      return;
    }

    // If recent event not found... then labels are not available to increment statistics.
    EventData eventData = getRecentEvent(eventId);
    if (eventData == null) {
      return;
    }
    // If time already collected for this event & stage... do not update.
    if (eventData.timeInlineHandled != -1) {
      return;
    }

    // Compute stage latency.
    long now = getTime();
    eventData.timeInlineHandled = now;
    if (!computeStatsEnabled) {
      return;
    }

    long stageLatency = now - eventData.timeReceivedAtTalkback;

    // Increment the stage latency statistics for each event label.
    if (eventData.labels != null) {
      for (String label : eventData.labels) {
        Statistics stats = getOrCreateStatistics(label, STAGE_INLINE_HANDLING);
        stats.increment(stageLatency);
      }
    }
  }

  /**
   * Track event latency between receiving event, and the spoken feedback is composed.
   *
   * @param eventId Identity of an event handled by TalkBack
   */
  public void onFeedbackComposed(@NonNull EventId eventId) {
    if (!trackEvents()) {
      return;
    }

    // If recent event not found... then labels are not available to increment statistics.
    EventData eventData = getRecentEvent(eventId);
    if (eventData == null) {
      return;
    }

    long now = getTime();
    eventData.setFeedbackComposed(now);
    if (!computeStatsEnabled) {
      return;
    }

    long stageLatency = now - eventData.timeReceivedAtTalkback;

    // Increment the stage latency statistics for each event label.
    if (eventData.labels != null) {
      for (String label : eventData.labels) {
        Statistics stats = getOrCreateStatistics(label, STAGE_FEEDBACK_COMPOSED);
        stats.increment(stageLatency);

        if (stageLatency > FEEDBACK_COMPOSED_THRESHOLD_MS) {
          LogUtils.d(
              TAG,
              "Feedback composed latency exceeds %s ms : %s",
              FEEDBACK_COMPOSED_THRESHOLD_MS,
              stageLatency);
        }
      }
    }
  }

  /**
   * Track event latency between receiving event, and queueing first piece of spoken feedback.
   *
   * @param eventId Identity of an event handled by TalkBack
   * @param utteranceId Identity of a piece of spoken feedback, resulting from the event.
   */
  public void onFeedbackQueued(@NonNull EventId eventId, @NonNull String utteranceId) {
    if (!trackEvents()) {
      return;
    }

    // If recent event not found... then labels are not available to increment statistics.
    EventData eventData = getRecentEvent(eventId);
    if (eventData == null) {
      return;
    }
    // If utterance already matched with this event... do not update.
    if (eventData.getUtteranceId() != null) {
      return;
    }

    // Compute stage latency.
    long now = getTime();
    eventData.setFeedbackQueued(now, utteranceId);
    indexRecentUtterance(utteranceId, eventId);
    if (!computeStatsEnabled) {
      return;
    }

    long stageLatency = now - eventData.timeReceivedAtTalkback;
    // Increment the stage latency statistics for each event label.
    if (eventData.labels != null) {
      for (String label : eventData.labels) {
        Statistics stats = getOrCreateStatistics(label, STAGE_FEEDBACK_QUEUED);
        stats.increment(stageLatency);

        if (stageLatency > FEEDBACK_QUEUED_THRESHOLD_MS) {
          LogUtils.d(
              TAG,
              "Feedback queued latency exceeds %s ms : %s",
              FEEDBACK_QUEUED_THRESHOLD_MS,
              stageLatency);
        }
      }
    }
  }

  /**
   * Tracks event latency between receiving event, and hearing audio feedback. We also track the
   * latency between queueing first piece of spoken feedback and hearing audio feedback.
   */
  public void onFeedbackOutput(@NonNull String utteranceId) {
    if (!trackEvents()) {
      return;
    }

    // If recent event not found... then labels are not available to increment statistics.
    EventId eventId = getRecentUtterance(utteranceId);
    if (eventId == null) {
      return;
    }
    EventData eventData = getRecentEvent(eventId);
    if (eventData == null) {
      return;
    }

    // If speech is not already matched with this event...
    if (eventData.getTimeFeedbackOutput() <= 0) {
      // Compute stage latency.
      long now = getTime();
      eventData.setFeedbackOutput(now);
      notifyLatencyTracker(eventData);
      if (computeStatsEnabled) {
        long stageLatency = now - eventData.timeReceivedAtTalkback;

        // Increment the stage latency statistics for each event label.
        if (eventData.labels != null) {
          for (String label : eventData.labels) {
            Statistics stats = getOrCreateStatistics(label, STAGE_FEEDBACK_HEARD);
            stats.increment(stageLatency);

            if (stageLatency > FEEDBACK_HEARD_THRESHOLD_MS) {
              LogUtils.d(
                  TAG,
                  "Feedback heard latency exceeds %s ms : %s",
                  FEEDBACK_HEARD_THRESHOLD_MS,
                  stageLatency);
            }

            // Track latency between stage queued and stage heard.
            if (eventData.getTimeFeedbackQueued() > 0) {
              long latency = now - eventData.getTimeFeedbackQueued();
              if (latency > TTS_LATENCY_THRESHOLD_MS) {
                LogUtils.d(
                    TAG,
                    "TTS latency of %s exceeds %s ms : %s",
                    utteranceId,
                    TTS_LATENCY_THRESHOLD_MS,
                    latency);
              }
              stats =
                  getOrCreateStatistics(label, STAGE_BETWEEN_FEEDBACK_QUEUED_AND_FEEDBACK_HEARD);
              stats.increment(latency);
            }
          }
        }
      }
    }

    // Clear the recent event, since we have no more use for it after tracking all stages.
    collectMissingLatencies(eventData);
    removeRecentEvent(eventId);
    removeRecentUtterance(utteranceId);
  }

  /**
   * Adds {@link LatencyTracker} to track the latency.
   *
   * @param latencyTracker The callback invoked when the latency is measured.
   * @param executor Executor on which to run the callback.
   */
  public void addLatencyTracker(LatencyTracker latencyTracker, Executor executor) {
    synchronized (latencyTrackers) {
      latencyTrackers.put(latencyTracker, executor);
    }
  }

  /**
   * Removes {@link LatencyTracker}.
   *
   * @param latencyTracker The callback invoked when the latency is measured.
   */
  public void removeLatencyTracker(LatencyTracker latencyTracker) {
    synchronized (latencyTrackers) {
      latencyTrackers.remove(latencyTracker);
    }
  }

  private void notifyLatencyTracker(EventData eventData) {
    ArrayMap<LatencyTracker, Executor> trackers;
    synchronized (latencyTrackers) {
      if (latencyTrackers.isEmpty()) {
        return;
      }
      trackers = new ArrayMap<>(latencyTrackers);
    }

    for (Map.Entry<LatencyTracker, Executor> entry : trackers.entrySet()) {
      entry.getValue().execute(() -> entry.getKey().onFeedbackOutput(eventData));
    }
  }

  /** Pop recent events off the queue, and increment their statistics as "missing" */
  protected void trimRecentEvents(int targetSize) {
    while (getNumRecentEvents() > targetSize) {
      EventData eventData = popOldestRecentEvent();
      if (eventData != null) {
        collectMissingLatencies(eventData);
      }
    }
  }

  /** Increment statistics for missing stages. */
  private void collectMissingLatencies(@NonNull EventData eventData) {
    // For each label x unreached stage... collect latency=missing.
    if (eventData != null && eventData.labels != null) {
      for (String label : eventData.labels) {
        if (eventData.timeInlineHandled <= 0) {
          incrementNumMissing(label, STAGE_INLINE_HANDLING);
        }
        if (eventData.getTimeFeedbackQueued() <= 0) {
          incrementNumMissing(label, STAGE_FEEDBACK_QUEUED);
        }
        if (eventData.getTimeFeedbackOutput() <= 0) {
          incrementNumMissing(label, STAGE_FEEDBACK_HEARD);
        }
      }
    }
  }

  private void incrementNumMissing(@NonNull String label, @StageId int stageId) {
    Statistics stats = getStatistics(label, stageId);
    if (stats != null) {
      stats.incrementNumMissing();
    }
  }

  /** Make clock override-able for testing. */
  protected long getTime() {
    return System.currentTimeMillis();
  }

  protected long getUptime() {
    return SystemClock.uptimeMillis();
  }

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Methods to access recent event collection

  protected void addRecentEvent(@NonNull EventId eventId, @NonNull EventData eventData) {
    synchronized (lockRecentEvents) {
      eventQueue.add(eventId);
      eventIndex.put(eventId, eventData);
    }
  }

  private void indexRecentUtterance(@NonNull String utteranceId, @NonNull EventId eventId) {
    synchronized (lockRecentEvents) {
      utteranceToEvent.put(utteranceId, eventId);
    }
  }

  protected EventData getRecentEvent(@NonNull EventId eventId) {
    synchronized (lockRecentEvents) {
      return eventIndex.get(eventId);
    }
  }

  protected EventId getRecentUtterance(@NonNull String utteranceId) {
    synchronized (lockRecentEvents) {
      return utteranceToEvent.get(utteranceId);
    }
  }

  protected int getNumRecentEvents() {
    synchronized (lockRecentEvents) {
      return eventQueue.size();
    }
  }

  protected @Nullable EventData popOldestRecentEvent() {
    synchronized (lockRecentEvents) {
      if (eventQueue.isEmpty()) {
        return null;
      }
      EventId eventId = eventQueue.remove();
      EventData eventData = eventIndex.remove(eventId);
      String utteranceId = (eventData == null) ? null : eventData.getUtteranceId();
      if (utteranceId != null) {
        utteranceToEvent.remove(eventData.getUtteranceId());
      }
      return eventData;
    }
  }

  protected void removeRecentEvent(@NonNull EventId eventId) {
    synchronized (lockRecentEvents) {
      eventIndex.remove(eventId);
      eventQueue.remove(eventId);
    }
  }

  public void clearRecentEvents() {
    synchronized (lockRecentEvents) {
      eventIndex.clear();
      eventQueue.clear();
    }
  }

  protected void removeRecentUtterance(@NonNull String utteranceId) {
    synchronized (lockRecentEvents) {
      utteranceToEvent.remove(utteranceId);
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Methods to access latency statistics collection

  /**
   * Looks up current latency statistics for a given label and stage.
   *
   * @param label The label used for events.
   * @param stage The talkback processing {@code @StageId}
   * @return The statistics for requested label & stage, or null if no such label & stage found.
   */
  public Statistics getStatistics(@NonNull String label, @StageId int stage) {
    synchronized (lockLabelToStats) {
      StatisticsKey statsKey = new StatisticsKey(label, stage);
      return labelToStats.get(statsKey);
    }
  }

  public void clearAllStatsAndRecords(Logger logger) {
    clearAllStats();
    clearRecentEvents();
    logger.log("performance statistic is cleared");
  }

  public void clearAllStats() {
    synchronized (lockLabelToStats) {
      labelToStats.clear();
    }
    allEventStats.clear();

    synchronized (lockGestureDetectionToStats) {
      gestureDetectionToStats.clear();
    }
  }

  protected Statistics getOrCreateStatistics(@NonNull String label, @StageId int stage) {
    synchronized (lockLabelToStats) {
      StatisticsKey statsKey = new StatisticsKey(label, stage);
      Statistics stats = labelToStats.get(statsKey);
      if (stats == null) {
        stats = new Statistics();
        labelToStats.put(statsKey, stats);
      }
      return stats;
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Methods to display results

  /** Displays label-vs-label comparisons for each summary statistic. */
  public void displayStatToLabelCompare() {
    displayStatToLabelCompare(DEFAULT_LOGGER);
  }

  /** Displays label-vs-label comparisons for each summary statistic. */
  public void displayStatToLabelCompare(Logger logger) {
    display(logger, "displayStatToLabelCompare()");

    StatisticsKey[] labelsSorted = new StatisticsKey[labelToStats.size()];
    labelsSorted = labelToStats.keySet().toArray(labelsSorted);
    Arrays.sort(labelsSorted);

    ArrayList<BarInfo> barsMissing = new ArrayList<>(labelsSorted.length);
    ArrayList<BarInfo> barsCount = new ArrayList<BarInfo>(labelsSorted.length);
    ArrayList<BarInfo> barsMean = new ArrayList<BarInfo>(labelsSorted.length);
    ArrayList<BarInfo> barsMedian = new ArrayList<BarInfo>(labelsSorted.length);
    ArrayList<BarInfo> barsStdDev = new ArrayList<BarInfo>(labelsSorted.length);

    // For each label... collect summary statistics.
    for (StatisticsKey label : labelsSorted) {
      Statistics stats = labelToStats.get(label);
      barsMissing.add(new BarInfo(label.toString(), stats.getNumMissing()));
      barsCount.add(new BarInfo(label.toString(), stats.getCount()));
      barsMean.add(new BarInfo(label.toString(), stats.getMean()));
      barsMedian.add(
          new BarInfo(
              label.toString(), stats.getMedianBinStart(), (2 * stats.getMedianBinStart())));
      barsStdDev.add(new BarInfo(label.toString(), (float) stats.getStdDev()));
    }

    // For each summary statistic... display comparison bar graph.
    displayBarGraph(logger, "  ", "missing", barsMissing, /* barUnits= */ "");
    displayBarGraph(logger, "  ", "count", barsCount, /* barUnits= */ "");
    displayBarGraph(logger, "  ", "mean", barsMean, /* barUnits= */ "ms");
    displayBarGraph(logger, "  ", "median", barsMedian, /* barUnits= */ "ms");
    displayBarGraph(logger, "  ", "stddev", barsStdDev, /* barUnits= */ "ms");
  }

  /** Displays latency statistics for each label. */
  public void displayLabelToStats() {
    displayLabelToStats(DEFAULT_LOGGER);
  }

  /** Displays latency statistics for each label. */
  public void displayLabelToStats(Logger logger) {
    display(logger, "displayLabelToStats()");

    // For each label...
    StatisticsKey[] labelsSorted = new StatisticsKey[labelToStats.size()];
    labelsSorted = labelToStats.keySet().toArray(labelsSorted);
    Arrays.sort(labelsSorted);
    for (StatisticsKey labelAndStage : labelsSorted) {
      Statistics stats = labelToStats.get(labelAndStage);
      display(logger, "  %s", labelAndStage);
      displayStatistics(logger, stats);
    }
  }

  public void dump(Logger logger) {
    if (!getComputeStatsEnabled()) {
      logger.log("performance statistic is not enabled");
      return;
    }
    displayLabelToStats(logger);
    displayStatToLabelCompare(logger);
    displayAllEventStats(logger);
    displayGestureDetectionStats(logger);
  }

  public void displayAllEventStats() {
    displayAllEventStats(DEFAULT_LOGGER);
  }

  public void displayAllEventStats(Logger logger) {
    display(logger, "displayAllEventStats()");
    displayStatistics(logger, allEventStats);
  }

  private void displayGestureDetectionStats(Logger logger) {
    display(logger, "displayGestureDetectionStats()");
    synchronized (lockGestureDetectionToStats) {
      for (int i = 0; i < gestureDetectionToStats.size(); ++i) {
        int gestureId = gestureDetectionToStats.keyAt(i);
        display(logger, AccessibilityServiceCompatUtils.gestureIdToString(gestureId));
        displayStatistics(logger, gestureDetectionToStats.get(gestureId));
      }
    }
  }

  @VisibleForTesting
  public boolean trackEvents() {
    return computeStatsEnabled || !latencyTrackers.isEmpty();
  }

  public static void displayStatistics(Statistics stats) {
    displayStatistics(DEFAULT_LOGGER, stats);
  }

  public static void displayStatistics(Logger logger, Statistics stats) {
    // Display summary statistics.
    display(
        logger,
        "    missing=%s, count=%s, mean=%sms, stdDev=%sms, median=%sms, 90th percentile=%sms, 99th"
            + " percentile=%sms",
        stats.getNumMissing(),
        stats.getCount(),
        stats.getMean(),
        stats.getStdDev(),
        stats.getPercentile(50),
        stats.getPercentile(90),
        stats.getPercentile(99));

    // Display latency distribution.
    ArrayList<BarInfo> bars = new ArrayList<>(stats.histogram.size());
    for (int bin = 0; bin < stats.histogram.size(); ++bin) {
      long binStart = stats.histogramBinToStartValue(bin);
      bars.add(
          new BarInfo(
              "" + binStart + "-" + (2 * binStart) + "ms", stats.histogram.get(bin).longValue()));
    }
    displayBarGraph(logger, "      ", "distribution=", bars, "count");
  }

  /**
   * Displays a bar graph.
   *
   * @param prefix Indentation to prepend to each bar line
   * @param title Title of graph
   * @param bars Series of bar labels & sizes
   * @param barUnits Units to append to each bar value
   */
  private static void displayBarGraph(
      Logger logger, String prefix, String title, ArrayList<BarInfo> bars, String barUnits) {
    if (!TextUtils.isEmpty(title)) {
      display(logger, "  %s", title);
    }

    // Find multiplier to scale bars.
    float maxValue = 0.0f;
    for (BarInfo barInfo : bars) {
      maxValue = Math.max(maxValue, barInfo.value);
    }
    float maxBarLength = 40.0f;
    float barScale = maxBarLength / maxValue;

    // For each bar... scale bar size, display bar.
    String barCharacter = "#";
    for (BarInfo barInfo : bars) {
      int barLength = (int) ((float) barInfo.value * barScale);
      String bar = repeat(barCharacter, barLength + 1);
      StringBuilder line = new StringBuilder();
      line.append(prefix + bar + " " + floatToString(barInfo.value));
      if (barInfo.rangeEnd != -1) {
        line.append("-" + floatToString(barInfo.rangeEnd));
      }
      line.append(barUnits + " for " + barInfo.label);
      display(logger, line.toString());
    }
    display(logger, "");
  }

  private static String floatToString(float value) {
    // If float is an integer... do not show fractional part of number.
    return ((int) value == value) ? String.format("%d", (int) value) : String.format("%f", value);
  }

  public void displayRecentEvents() {
    display("perf.mEventQueue=");
    for (EventId i : eventQueue) {
      display("\t" + i);
    }
    display("perf.mEventIndex=");
    for (EventId i : eventIndex.keySet()) {
      display("\t" + i + ":" + eventIndex.get(i));
    }
  }

  static void display(Logger logger, String format, Object... args) {
    logger.log(format, args);
  }

  private static void display(String format, Object... args) {
    LogUtils.i(TAG, format, args);
  }

  protected static String repeat(String string, int repetitions) {
    StringBuilder repeated = new StringBuilder(string.length() * repetitions);
    for (int r = 0; r < repetitions; ++r) {
      repeated.append(string);
    }
    return repeated.toString();
  }

  public @Nullable Statistics getStatisticsByLabelAndStageId(String label, @StageId int stageId) {
    StatisticsKey[] labelsSorted = new StatisticsKey[labelToStats.size()];
    labelsSorted = labelToStats.keySet().toArray(labelsSorted);

    for (StatisticsKey labelAndStage : labelsSorted) {
      if (TextUtils.equals(labelAndStage.label, label) && labelAndStage.stage == stageId) {
        return labelToStats.get(labelAndStage);
      }
    }

    return null;
  }

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Inner classes for recent events

  /** Key for looking up EventData in HashMap. */
  public static class EventId {
    private final long eventTimeMs;
    @EventTypeId private final int eventType;

    /**
     * The actual event type. Could be {@link AccessibilityEvent#getEventType()} or {@link
     * AccessibilityGestureEvent#getGestureId()}.
     */
    private final int eventSubtype;

    /**
     * Create a small event identifier for tracking event through processing stages.
     *
     * @param time Time in milliseconds.
     * @param type Event object type.
     * @param subtype Event object subtype from {@link AccessibilityEvent#getEventType()} or {@link
     *     AccessibilityGestureEvent#getGestureId()}.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public EventId(long time, @EventTypeId int type, int subtype) {
      eventTimeMs = time; // Event creation times use system uptime.
      eventType = type;
      eventSubtype = subtype;
    }

    public long getEventTimeMs() {
      return eventTimeMs;
    }

    @EventTypeId
    public int getEventType() {
      return eventType;
    }

    public int getEventSubtype() {
      return eventSubtype;
    }

    @Override
    public boolean equals(@Nullable Object otherObj) {
      if (this == otherObj) {
        return true;
      }
      if (!(otherObj instanceof EventId)) {
        return false;
      }
      EventId other = (EventId) otherObj;
      return this.eventTimeMs == other.eventTimeMs
          && this.eventType == other.eventType
          && this.eventSubtype == other.eventSubtype;
    }

    @Override
    public int hashCode() {
      return Objects.hash(eventTimeMs, eventType, eventSubtype);
    }

    @Override
    public String toString() {
      String subtypeString;
      switch (eventType) {
        case EVENT_TYPE_ACCESSIBILITY:
          subtypeString = AccessibilityEventUtils.typeToString(eventSubtype);
          break;
        case EVENT_TYPE_KEY:
          subtypeString = KeyEvent.keyCodeToString(eventSubtype);
          break;
        case EVENT_TYPE_GESTURE:
          subtypeString = AccessibilityServiceCompatUtils.gestureIdToString(eventSubtype);
          break;
        case EVENT_TYPE_FINGERPRINT_GESTURE:
          subtypeString =
              AccessibilityServiceCompatUtils.fingerprintGestureIdToString(eventSubtype);
          break;
        case EVENT_TYPE_KEY_COMBO:
          subtypeString =
              String.format(Locale.getDefault(Category.FORMAT), "KEY_COMBO_%d", eventSubtype);
          break;
        case EVENT_TYPE_ROTATE:
          subtypeString = orientationToSymbolicName(eventSubtype);
          break;
        case EVENT_TYPE_VOLUME_KEY_COMBO:
          subtypeString =
              String.format(
                  Locale.getDefault(Category.FORMAT), "VOLUME_KEY_COMBO_%d", eventSubtype);
          break;

        default:
          subtypeString = Integer.toString(eventSubtype);
      }
      return "type:"
          + EVENT_TYPE_NAMES.get(eventType)
          + " subtype:"
          + subtypeString
          + " time:"
          + eventTimeMs;
    }
  }

  private static String orientationToSymbolicName(int orientation) {
    switch (orientation) {
      case ORIENTATION_UNDEFINED:
        return "ORIENTATION_UNDEFINED";
      case ORIENTATION_LANDSCAPE:
        return "ORIENTATION_LANDSCAPE";
      case ORIENTATION_PORTRAIT:
        return "ORIENTATION_PORTRAIT";
      default:
        return "ORIENTATION_" + orientation;
    }
  }

  /** Tracking the stage start times for an event. */
  public static final class EventData {

    // Members set when event is received at TalkBack.
    final String[] labels;
    public final EventId eventId; // This EventData's key in mEventIndex.

    /** The timestamp retrieved from {@link System#currentTimeMillis()} when receiving the event. */
    public final long timeReceivedAtTalkback;

    /** The timestamp retrieved from {@link SystemClock#uptimeMillis()} when receiving the event. */
    public final long uptimeReceivedAtTalkback;

    long timeInlineHandled = -1;

    private long timeFeedbackComposed = -1;

    // Members set when feedback is queued.
    private long timeFeedbackQueued = -1;
    private String utteranceId; // Updates may come from TalkBack or TextToSpeech threads.

    private long timeFeedbackOutput = -1;

    private EventData(
        long timeReceivedAtTalkback,
        long uptimeReceivedAtTalkback,
        String[] labels,
        EventId eventId) {
      this.labels = labels;
      this.eventId = eventId;
      this.timeReceivedAtTalkback = timeReceivedAtTalkback;
      this.uptimeReceivedAtTalkback = uptimeReceivedAtTalkback;
    }

    // Synchronized because this method may be called from a separate audio handling thread.
    synchronized void setFeedbackComposed(long timeFeedbackComposed) {
      this.timeFeedbackComposed = timeFeedbackComposed;
    }

    // Synchronized because this method may be called from a separate audio handling thread.
    synchronized void setFeedbackQueued(long timeFeedbackQueued, String utteranceId) {
      this.timeFeedbackQueued = timeFeedbackQueued;
      this.utteranceId = utteranceId;
    }

    // Synchronized because this method may be called from a separate audio handling thread.
    synchronized void setFeedbackOutput(long timeFeedbackOutput) {
      this.timeFeedbackOutput = timeFeedbackOutput;
    }

    public synchronized long getTimeFeedbackComposed() {
      return timeFeedbackComposed;
    }

    public synchronized long getTimeFeedbackQueued() {
      return timeFeedbackQueued;
    }

    public synchronized String getUtteranceId() {
      return utteranceId;
    }

    public synchronized long getTimeFeedbackOutput() {
      return timeFeedbackOutput;
    }

    @Override
    public String toString() {
      return " labels="
          + TextUtils.join(",", labels)
          + " timeReceivedAtTalkback="
          + timeReceivedAtTalkback
          + " mTimeFeedbackQueued="
          + timeFeedbackQueued
          + " mTimeFeedbackOutput="
          + timeFeedbackOutput
          + " timeInlineHandled="
          + timeInlineHandled
          + String.format(" mUtteranceId=%s", utteranceId);
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Inner classes for latency statistics

  /** Key for looking up Statistics by event label & stage. */
  // REFERTO
  @SuppressWarnings("ComparableType")
  public static class StatisticsKey implements Comparable<Object> {
    private final String label;
    @StageId private final int stage;

    public StatisticsKey(String label, @StageId int stage) {
      this.label = label;
      this.stage = stage;
    }

    public String getLabel() {
      return label;
    }

    public int getStage() {
      return stage;
    }

    @Override
    public int compareTo(Object otherObj) {
      if (otherObj == null || !(otherObj instanceof StatisticsKey)) {
        return 1;
      }
      if (this == otherObj) {
        return 0;
      }
      StatisticsKey other = (StatisticsKey) otherObj;
      // Compare stage.
      int stageCompare = stage - other.getStage();
      if (stageCompare != 0) {
        return stageCompare;
      }
      // Compare label.
      return label.compareTo(other.getLabel());
    }

    @Override
    public boolean equals(@Nullable Object otherObj) {
      if (!(otherObj instanceof StatisticsKey)) {
        return false;
      }
      if (this == otherObj) {
        return true;
      }
      StatisticsKey other = (StatisticsKey) otherObj;
      return this.stage == other.stage && this.label.equals(other.getLabel());
    }

    @Override
    public int hashCode() {
      return Objects.hash(label, stage);
    }

    @Override
    public String toString() {
      return label + "-" + STAGE_NAMES.get(stage);
    }
  }

  /** General-purpose summary & distribution statistics for a group of values. */
  public static class Statistics {
    private static final int MAX_RAW_DATA_SIZE = 300;
    protected long numMissing;
    protected long count;
    protected long sum;
    protected long sumSquares;
    private final Queue<Long> rawData;

    /** Bin start value = 2^(index-1) , except index=0 holds bin start value=0. */
    protected ArrayList<AtomicLong> histogram = new ArrayList<>();

    public Statistics() {
      rawData = EvictingQueue.create(MAX_RAW_DATA_SIZE);
      clear();
    }

    public synchronized void clear() {
      numMissing = 0;
      count = 0;
      sum = 0;
      sumSquares = 0;
      histogram.clear();
      rawData.clear();
    }

    public synchronized void incrementNumMissing() {
      ++numMissing;
    }

    public synchronized void increment(long value) {
      // Increment summary statistics.
      ++count;
      sum += value;
      sumSquares += value * value;

      // Ensure histogram is big enough to hold this value.
      int binIndex = valueToHistogramBin(value);
      if (histogram.size() < binIndex + 1) {
        histogram.ensureCapacity(binIndex + 1);
        while (histogram.size() <= binIndex) {
          histogram.add(new AtomicLong(0));
        }
      }
      // Increment histogram count.
      AtomicLong binCount = histogram.get(binIndex);
      binCount.set(binCount.longValue() + 1);
      rawData.add(value);
    }

    public long getNumMissing() {
      return numMissing;
    }

    public long getCount() {
      return count;
    }

    public long getMean() {
      return (count <= 0) ? 0 : (sum / count);
    }

    /**
     * Computes standard devication based on the mistaken assumption that values have gaussian
     * distribution.
     *
     * @return Standard deviation of {@code increment(value)}
     */
    public double getStdDev() {
      if (count <= 0) {
        return 0;
      }
      double mean = (double) sum / (double) count;
      double meanOfSquares = (double) sumSquares / (double) count;
      double variance = meanOfSquares - (mean * mean);
      return Math.sqrt(variance);
    }

    public long getMedianBinStart() {
      if (count <= 0) {
        return 0;
      }
      // For each histogram bin, in order...
      long medianCount = count / 2;
      long sumBins = 0;
      for (int binIndex = 0; binIndex < histogram.size(); ++binIndex) {
        // If bin contains mCount/2... return bin start.
        sumBins += histogram.get(binIndex).longValue();
        if (sumBins >= medianCount) {
          return histogramBinToStartValue(binIndex);
        }
      }
      return histogramBinToStartValue(histogram.size());
    }

    public int valueToHistogramBin(long value) {
      return valueToPower(value) + 1;
    }

    public long histogramBinToStartValue(int index) {
      return (index < 1) ? 0L : (1L << (index - 1));
    }

    /**
     * Gets the percentile with the given rank.
     *
     * @param rank The rank, between 0 < rank <= 100.
     * @return The percentile value otherwise -1 if {@code rawData} is invalid,
     */
    public long getPercentile(int rank) {
      if (rawData == null || rawData.isEmpty()) {
        return -1L;
      }

      List<Long> sortedData = new ArrayList<>(rawData);
      Collections.sort(sortedData);
      int index = ((rank * sortedData.size() + 99) / 100) - 1;

      return sortedData.get(index);
    }

    /**
     * Converts a positive value to the exponent of preceding 2^P. Returns the largest integer
     * exponent "P" such that 2^P < value. Returns -1 for value <= 0.
     */
    public static int valueToPower(long value) {
      if (value < 1) {
        return -1;
      }
      // For each power that leaves a remainder... increment power.
      long power = -1;
      for (long remainder = value; remainder > 0; remainder >>= 1) {
        ++power;
      }
      return (int) power;
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Inner classes

  /** Holds data for one bar in a bar graph. */
  protected static class BarInfo {
    public String label = "";
    public float value = 0;
    public float rangeEnd = -1;

    public BarInfo(String labelArg, float valueArg) {
      label = labelArg;
      value = valueArg;
    }

    public BarInfo(String labelArg, float valueArg, float rangeEndArg) {
      label = labelArg;
      value = valueArg;
      rangeEnd = rangeEndArg;
    }
  }

  /** A message object with a corresponding EventId, for use by deferred event handlers. */
  public static class EventIdAnd<T> {
    public final T object;
    public final @Nullable EventId eventId;

    public EventIdAnd(T objectArg, @Nullable EventId eventIdArg) {
      object = objectArg;
      eventId = eventIdArg;
    }
  }
}
