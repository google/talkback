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

import android.content.res.Configuration;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Objects;
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

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Constants

  /** Stages that each event goes through, where we want to measure latency. */
  @IntDef({STAGE_FRAMEWORK, STAGE_INLINE_HANDLING, STAGE_FEEDBACK_QUEUED, STAGE_FEEDBACK_HEARD})
  public @interface StageId {}

  public static final int STAGE_FRAMEWORK = 0; // Latency before TalkBack
  public static final int STAGE_INLINE_HANDLING = 1; // Time during synchronous event handlers
  public static final int STAGE_FEEDBACK_QUEUED = 2; // Time until first speech is queued
  public static final int STAGE_FEEDBACK_HEARD = 3; // Time until speech is heard.
  public static final String[] STAGE_NAMES = {
    "STAGE_FRAMEWORK", "STAGE_INLINE_HANDLING", "STAGE_FEEDBACK_QUEUED", "STAGE_FEEDBACK_HEARD"
  };

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
  public static final String[] EVENT_TYPE_NAMES = {
    "EVENT_TYPE_ACCESSIBILITY",
    "EVENT_TYPE_KEY",
    "EVENT_TYPE_KEY_COMBO",
    "EVENT_TYPE_VOLUME_KEY_COMBO",
    "EVENT_TYPE_GESTURE",
    "EVENT_TYPE_ROTATE",
    "EVENT_TYPE_FINGERPRINT_GESTURE"
  };

  @Nullable public static final EventId EVENT_ID_UNTRACKED = null;

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Member data

  protected boolean mEnabled = false;

  /** Recent events for which we are collecting stage latencies */
  protected static final int MAX_RECENT_EVENTS = 100;

  protected LinkedList<EventId> mEventQueue = new LinkedList<EventId>();
  protected HashMap<EventId, EventData> mEventIndex = new HashMap<EventId, EventData>();
  private HashMap<String, EventId> mUtteranceToEvent = new HashMap<String, EventId>();
  protected final Object mLockRecentEvents = new Object();

  /** Latency statistics for various event/label types */
  protected HashMap<StatisticsKey, Statistics> mLabelToStats =
      new HashMap<StatisticsKey, Statistics>();

  protected final Object mLockLabelToStats = new Object();
  protected Statistics mAllEventStats = new Statistics();

  private static Performance sInstance = new Performance();

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public static Performance getInstance() {
    return sInstance;
  }

  protected Performance() {}

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Simple getters/setters

  public boolean getEnabled() {
    return mEnabled;
  }

  public void setEnabled(boolean enabled) {
    mEnabled = enabled;
  }

  public Statistics getAllEventStats() {
    return mAllEventStats;
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
    if (!mEnabled) {
      return eventId;
    }

    // Segment events based on type.
    String label = AccessibilityEventUtils.typeToString(event.getEventType());
    String[] labels = {label};

    onEventReceived(eventId, labels);
    return eventId;
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

  /**
   * Method to start tracking processing latency for a key event.
   *
   * @param event A key event just received by TalkBack
   * @return An event id that can be used to track performance through later stages.
   */
  public EventId onEventReceived(@NonNull KeyEvent event) {
    int keycode = event.getKeyCode();
    EventId eventId = new EventId(event.getEventTime(), EVENT_TYPE_KEY, keycode);
    if (!mEnabled) {
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

  /**
   * Method to start tracking processing latency for a gesture event. Uses event type as statistics
   * segmentation label.
   *
   * @param gestureId A gesture just recognized by TalkBack
   * @return An event id that can be used to track performance through later stages.
   */
  public EventId onGestureEventReceived(int gestureId) {
    EventId eventId = new EventId(getUptime(), EVENT_TYPE_GESTURE, gestureId);
    if (!mEnabled) {
      return eventId;
    }

    // Segment events based on gesture id.
    String label = AccessibilityServiceCompatUtils.gestureIdToString(gestureId);
    String[] labels = {label};

    onEventReceived(eventId, labels);
    return eventId;
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
    if (!mEnabled) {
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
    if (!mEnabled) {
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
    if (!mEnabled) {
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
    if (!mEnabled) {
      return eventId;
    }

    // Segment events based on orientation.
    String label = "ORIENTATION_UNDEFINED";
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      label = "ORIENTATION_PORTRAIT";
    } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
      label = "ORIENTATION_LANDSCAPE";
    }
    String[] labels = {label};

    onEventReceived(eventId, labels);
    return eventId;
  }

  protected void onEventReceived(@NonNull EventId eventId, String[] labels) {
    if (!mEnabled) {
      return;
    }

    // Create event data.
    EventData eventData = new EventData(getTime(), labels, eventId);

    // Collect event data.
    addRecentEvent(eventId, eventData);
    trimRecentEvents(MAX_RECENT_EVENTS);

    @StageId int prevStage = STAGE_INLINE_HANDLING - 1;
    long prevStageLatency = getUptime() - eventId.getEventTimeMs(); // Event times are uptime.
    mAllEventStats.increment(prevStageLatency);

    // For each event label... increment statistics.
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
   * Track event latency between receiving event, and finishing synchronous event handling.
   *
   * @param eventId Identity of an event just handled by TalkBack
   */
  public void onHandlerDone(@NonNull EventId eventId) {
    if (!mEnabled) {
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
    long stageLatency = now - eventData.timeReceivedAtTalkback;

    // For each event label... increment stage latency statistics.
    if (eventData.labels != null) {
      for (String label : eventData.labels) {
        Statistics stats = getOrCreateStatistics(label, STAGE_INLINE_HANDLING);
        stats.increment(stageLatency);
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
    if (!mEnabled) {
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
    long stageLatency = now - eventData.timeReceivedAtTalkback;

    // For each event label... increment stage latency statistics.
    if (eventData.labels != null) {
      for (String label : eventData.labels) {
        Statistics stats = getOrCreateStatistics(label, STAGE_FEEDBACK_QUEUED);
        stats.increment(stageLatency);
      }
    }
  }

  /** Track event latency between receiving event, and hearing audio feedback. */
  public void onFeedbackOutput(@NonNull String utteranceId) {
    if (!mEnabled) {
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
      long stageLatency = now - eventData.timeReceivedAtTalkback;

      // For each event label... increment stage latency statistics.
      if (eventData.labels != null) {
        for (String label : eventData.labels) {
          Statistics stats = getOrCreateStatistics(label, STAGE_FEEDBACK_HEARD);
          stats.increment(stageLatency);
        }
      }
    }

    // Clear the recent event, since we have no more use for it after tracking all stages.
    collectMissingLatencies(eventData);
    removeRecentEvent(eventId);
    removeRecentUtterance(utteranceId);
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
    synchronized (mLockRecentEvents) {
      mEventQueue.add(eventId);
      mEventIndex.put(eventId, eventData);
    }
  }

  private void indexRecentUtterance(@NonNull String utteranceId, @NonNull EventId eventId) {
    synchronized (mLockRecentEvents) {
      mUtteranceToEvent.put(utteranceId, eventId);
    }
  }

  protected EventData getRecentEvent(@NonNull EventId eventId) {
    synchronized (mLockRecentEvents) {
      return mEventIndex.get(eventId);
    }
  }

  protected EventId getRecentUtterance(@NonNull String utteranceId) {
    synchronized (mLockRecentEvents) {
      return mUtteranceToEvent.get(utteranceId);
    }
  }

  protected int getNumRecentEvents() {
    synchronized (mLockRecentEvents) {
      return mEventQueue.size();
    }
  }

  protected @Nullable EventData popOldestRecentEvent() {
    synchronized (mLockRecentEvents) {
      if (mEventQueue.size() == 0) {
        return null;
      }
      EventId eventId = mEventQueue.remove();
      EventData eventData = mEventIndex.remove(eventId);
      String utteranceId = (eventData == null) ? null : eventData.getUtteranceId();
      if (utteranceId != null) {
        mUtteranceToEvent.remove(eventData.getUtteranceId());
      }
      return eventData;
    }
  }

  protected void removeRecentEvent(@NonNull EventId eventId) {
    synchronized (mLockRecentEvents) {
      mEventIndex.remove(eventId);
      mEventQueue.remove(eventId);
    }
  }

  public void clearRecentEvents() {
    synchronized (mLockRecentEvents) {
      mEventIndex.clear();
      mEventQueue.clear();
    }
  }

  protected void removeRecentUtterance(@NonNull String utteranceId) {
    synchronized (mLockRecentEvents) {
      mUtteranceToEvent.remove(utteranceId);
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
    synchronized (mLockLabelToStats) {
      StatisticsKey statsKey = new StatisticsKey(label, stage);
      return mLabelToStats.get(statsKey);
    }
  }

  public void clearAllStats() {
    synchronized (mLockLabelToStats) {
      mLabelToStats.clear();
    }
    mAllEventStats.clear();
  }

  protected Statistics getOrCreateStatistics(@NonNull String label, @StageId int stage) {
    synchronized (mLockLabelToStats) {
      StatisticsKey statsKey = new StatisticsKey(label, stage);
      Statistics stats = mLabelToStats.get(statsKey);
      if (stats == null) {
        stats = new Statistics();
        mLabelToStats.put(statsKey, stats);
      }
      return stats;
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Methods to display results

  /** Display label-vs-label comparisons for each summary statistic. */
  public void displayStatToLabelCompare() {
    display("displayStatToLabelCompare()");

    StatisticsKey[] labelsSorted = new StatisticsKey[mLabelToStats.size()];
    labelsSorted = mLabelToStats.keySet().toArray(labelsSorted);
    Arrays.sort(labelsSorted);

    ArrayList<BarInfo> barsMissing = new ArrayList<BarInfo>(labelsSorted.length);
    ArrayList<BarInfo> barsCount = new ArrayList<BarInfo>(labelsSorted.length);
    ArrayList<BarInfo> barsMean = new ArrayList<BarInfo>(labelsSorted.length);
    ArrayList<BarInfo> barsMedian = new ArrayList<BarInfo>(labelsSorted.length);
    ArrayList<BarInfo> barsStdDev = new ArrayList<BarInfo>(labelsSorted.length);

    // For each label... collect summary statistics.
    for (StatisticsKey label : labelsSorted) {
      Statistics stats = mLabelToStats.get(label);
      barsMissing.add(new BarInfo(label.toString(), stats.getNumMissing()));
      barsCount.add(new BarInfo(label.toString(), stats.getCount()));
      barsMean.add(new BarInfo(label.toString(), stats.getMean()));
      barsMedian.add(
          new BarInfo(
              label.toString(), stats.getMedianBinStart(), (2 * stats.getMedianBinStart())));
      barsStdDev.add(new BarInfo(label.toString(), (float) stats.getStdDev()));
    }

    // For each summary statistic... display comparison bar graph.
    displayBarGraph("  ", "missing", barsMissing, "" /* barUnits */);
    displayBarGraph("  ", "count", barsCount, "" /* barUnits */);
    displayBarGraph("  ", "mean", barsMean, "ms");
    displayBarGraph("  ", "median", barsMedian, "ms");
    displayBarGraph("  ", "stddev", barsStdDev, "ms");
  }

  /** Display latency statistics for each label. */
  public void displayLabelToStats() {
    display("displayLabelToStats()");

    // For each label...
    StatisticsKey[] labelsSorted = new StatisticsKey[mLabelToStats.size()];
    labelsSorted = mLabelToStats.keySet().toArray(labelsSorted);
    Arrays.sort(labelsSorted);
    for (StatisticsKey labelAndStage : labelsSorted) {
      Statistics stats = mLabelToStats.get(labelAndStage);
      display("  %s", labelAndStage);
      displayStatistics(stats);
    }
  }

  public void displayAllEventStats() {
    display("displayAllEventStats()");
    displayStatistics(mAllEventStats);
  }

  private void displayStatistics(Statistics stats) {
    // Display summary statistics.
    display(
        "    missing=%s count=%s  mean=%sms  stdDev=%sms  median=%sms",
        stats.getNumMissing(),
        stats.getCount(),
        stats.getMean(),
        stats.getStdDev(),
        stats.getMedianBinStart());

    // Display latency distribution.
    ArrayList<BarInfo> bars = new ArrayList<BarInfo>(stats.mHistogram.size());
    for (int bin = 0; bin < stats.mHistogram.size(); ++bin) {
      long binStart = stats.histogramBinToStartValue(bin);
      bars.add(
          new BarInfo(
              "" + binStart + "-" + (2 * binStart) + "ms", stats.mHistogram.get(bin).longValue()));
    }
    displayBarGraph("      ", "distribution=", bars, "count");
  }

  /**
   * Display a bar graph.
   *
   * @param prefix Indentation to prepend to each bar line
   * @param title Title of graph
   * @param bars Series of bar labels & sizes
   * @param barUnits Units to append to each bar value
   */
  protected void displayBarGraph(
      String prefix, String title, ArrayList<BarInfo> bars, String barUnits) {
    if (!TextUtils.isEmpty(title)) {
      display("  %s", title);
    }

    // Find multiplier to scale bars.
    float maxValue = 0.0f;
    for (BarInfo barInfo : bars) {
      maxValue = Math.max(maxValue, barInfo.value);
    }
    float maxBarLength = 40.0f;
    float barScale = maxBarLength / maxValue;

    // For each bar... scale bar size, display bar.
    String barCharacter = "\u001B[7m#\u001B[0m"; // Use ANSI escape code to invert color.
    for (BarInfo barInfo : bars) {
      int barLength = (int) ((float) barInfo.value * barScale);
      String bar = repeat(barCharacter, barLength + 1);
      StringBuilder line = new StringBuilder();
      line.append(prefix + bar + " " + floatToString(barInfo.value));
      if (barInfo.rangeEnd != -1) {
        line.append("-" + floatToString(barInfo.rangeEnd));
      }
      line.append(barUnits + " for " + barInfo.label);
      display(line.toString());
    }
    display("");
  }

  protected String floatToString(float value) {
    // If float is an integer... do not show fractional part of number.
    return ((int) value == value) ? String.format("%d", (int) value) : String.format("%f", value);
  }

  public void displayRecentEvents() {
    display("perf.mEventQueue=");
    for (EventId i : mEventQueue) {
      display("\t" + i);
    }
    display("perf.mEventIndex=");
    for (EventId i : mEventIndex.keySet()) {
      display("\t" + i + ":" + mEventIndex.get(i));
    }
  }

  protected void display(String format, Object... args) {
    LogUtils.i(TAG, format, args);
  }

  protected static String repeat(String string, int repetitions) {
    StringBuilder repeated = new StringBuilder(string.length() * repetitions);
    for (int r = 0; r < repetitions; ++r) {
      repeated.append(string);
    }
    return repeated.toString();
  }

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Inner classes for recent events

  /** Key for looking up EventData in HashMap. */
  public static class EventId {
    private final long mEventTimeMs;
    private final @EventTypeId int mEventType;
    private final int mEventSubtype;

    /**
     * Create a small event identifier for tracking event through processing stages, even after
     * AccessibilityEvent has been recycled.
     *
     * @param time Time in milliseconds.
     * @param type Event object type.
     * @param subtype Event object subtype from AccessibilityEvent.getEventType() or gesture id.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public EventId(long time, @EventTypeId int type, int subtype) {
      mEventTimeMs = time; // Event creation times use system uptime.
      mEventType = type;
      mEventSubtype = subtype;
    }

    public long getEventTimeMs() {
      return mEventTimeMs;
    }

    public @EventTypeId int getEventType() {
      return mEventType;
    }

    public int getEventSubtype() {
      return mEventSubtype;
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
      return this.mEventTimeMs == other.mEventTimeMs
          && this.mEventType == other.mEventType
          && this.mEventSubtype == other.mEventSubtype;
    }

    @Override
    public int hashCode() {
      return Objects.hash(mEventTimeMs, mEventType, mEventSubtype);
    }

    @Override
    public String toString() {
      String subtypeString;
      switch (mEventType) {
        case EVENT_TYPE_ACCESSIBILITY:
          subtypeString = AccessibilityEventUtils.typeToString(mEventSubtype);
          break;
        case EVENT_TYPE_KEY:
          subtypeString = KeyEvent.keyCodeToString(mEventSubtype);
          break;
        case EVENT_TYPE_GESTURE:
          subtypeString = AccessibilityServiceCompatUtils.gestureIdToString(mEventSubtype);
          break;
        case EVENT_TYPE_FINGERPRINT_GESTURE:
          subtypeString =
              AccessibilityServiceCompatUtils.fingerprintGestureIdToString(mEventSubtype);
          break;
        default:
          subtypeString = Integer.toString(mEventSubtype);
      }
      return "type:"
          + EVENT_TYPE_NAMES[mEventType]
          + " subtype:"
          + subtypeString
          + " time:"
          + mEventTimeMs;
    }
  }

  /** Tracking the stage start times for an event. */
  protected static class EventData {

    // Members set when event is received at TalkBack.
    public final String[] labels;
    public final EventId eventId; // This EventData's key in mEventIndex.
    public final long timeReceivedAtTalkback;

    public long timeInlineHandled = -1;

    // Members set when feedback is queued.
    private long mTimeFeedbackQueued = -1;
    private String mUtteranceId; // Updates may come from TalkBack or TextToSpeech threads.

    private long mTimeFeedbackOutput = -1;

    public EventData(long timeReceivedAtTalkbackArg, String[] labelsArg, EventId eventIdArg) {
      labels = labelsArg;
      eventId = eventIdArg;
      timeReceivedAtTalkback = timeReceivedAtTalkbackArg;
    }

    // Synchronized because this method may be called from a separate audio handling thread.
    public synchronized void setFeedbackQueued(long timeFeedbackQueued, String utteranceId) {
      mTimeFeedbackQueued = timeFeedbackQueued;
      mUtteranceId = utteranceId;
    }

    // Synchronized because this method may be called from a separate audio handling thread.
    public synchronized void setFeedbackOutput(long timeFeedbackOutput) {
      mTimeFeedbackOutput = timeFeedbackOutput;
    }

    public synchronized long getTimeFeedbackQueued() {
      return mTimeFeedbackQueued;
    }

    public synchronized String getUtteranceId() {
      return mUtteranceId;
    }

    public synchronized long getTimeFeedbackOutput() {
      return mTimeFeedbackOutput;
    }

    @Override
    public String toString() {
      return " labels="
          + TextUtils.join(",", labels)
          + " timeReceivedAtTalkback="
          + timeReceivedAtTalkback
          + " mTimeFeedbackQueued="
          + mTimeFeedbackQueued
          + " mTimeFeedbackOutput="
          + mTimeFeedbackOutput
          + " timeInlineHandled="
          + timeInlineHandled
          + String.format(" mUtteranceId=%s", mUtteranceId);
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Inner classes for latency statistics

  /** Key for looking up Statistics by event label & stage. */
  // REFERTO
  @SuppressWarnings("ComparableType")
  public static class StatisticsKey implements Comparable<Object> {
    private final String mLabel;
    private final @StageId int mStage;

    public StatisticsKey(String label, @StageId int stage) {
      mLabel = label;
      mStage = stage;
    }

    public String getLabel() {
      return mLabel;
    }

    public int getStage() {
      return mStage;
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
      int stageCompare = mStage - other.getStage();
      if (stageCompare != 0) {
        return stageCompare;
      }
      // Compare label.
      return mLabel.compareTo(other.getLabel());
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
      return this.mStage == other.mStage && this.mLabel.equals(other.getLabel());
    }

    @Override
    public int hashCode() {
      return Objects.hash(mLabel, mStage);
    }

    @Override
    public String toString() {
      return mLabel + "-" + STAGE_NAMES[mStage];
    }
  }

  /** General-purpose summary & distribution statistics for a group of values. */
  public static class Statistics {
    protected long mNumMissing;
    protected long mCount;
    protected long mSum;
    protected long mSumSquares;

    /** Bin start value = 2^(index-1) , except index=0 holds bin start value=0. */
    protected ArrayList<AtomicLong> mHistogram = new ArrayList<AtomicLong>();

    public Statistics() {
      clear();
    }

    public synchronized void clear() {
      mNumMissing = 0;
      mCount = 0;
      mSum = 0;
      mSumSquares = 0;
      mHistogram.clear();
    }

    public synchronized void incrementNumMissing() {
      ++mNumMissing;
    }

    public synchronized void increment(long value) {
      // Increment summary statistics.
      ++mCount;
      mSum += value;
      mSumSquares += value * value;

      // Ensure histogram is big enough to hold this value.
      int binIndex = valueToHistogramBin(value);
      if (mHistogram.size() < binIndex + 1) {
        mHistogram.ensureCapacity(binIndex + 1);
        while (mHistogram.size() <= binIndex) {
          mHistogram.add(new AtomicLong(0));
        }
      }
      // Increment histogram count.
      AtomicLong binCount = mHistogram.get(binIndex);
      binCount.set(binCount.longValue() + 1);
    }

    public long getNumMissing() {
      return mNumMissing;
    }

    public long getCount() {
      return mCount;
    }

    public long getMean() {
      return (mCount <= 0) ? 0 : (mSum / mCount);
    }

    /**
     * Computes standard devication based on the mistaken assumption that values have gaussian
     * distribution.
     *
     * @return Standard deviation of {@code increment(value)}
     */
    public double getStdDev() {
      if (mCount <= 0) {
        return 0;
      }
      double mean = (double) mSum / (double) mCount;
      double meanOfSquares = (double) mSumSquares / (double) mCount;
      double variance = meanOfSquares - (mean * mean);
      return Math.sqrt(variance);
    }

    public long getMedianBinStart() {
      if (mCount <= 0) {
        return 0;
      }
      // For each histogram bin, in order...
      long medianCount = mCount / 2;
      long sumBins = 0;
      for (int binIndex = 0; binIndex < mHistogram.size(); ++binIndex) {
        // If bin contains mCount/2... return bin start.
        sumBins += mHistogram.get(binIndex).longValue();
        if (sumBins >= medianCount) {
          return histogramBinToStartValue(binIndex);
        }
      }
      return histogramBinToStartValue(mHistogram.size());
    }

    public int valueToHistogramBin(long value) {
      return valueToPower(value) + 1;
    }

    public long histogramBinToStartValue(int index) {
      return (index < 1) ? 0L : (1L << (index - 1));
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
    @Nullable public final EventId eventId;

    public EventIdAnd(T objectArg, @Nullable EventId eventIdArg) {
      object = objectArg;
      eventId = eventIdArg;
    }
  }
}
