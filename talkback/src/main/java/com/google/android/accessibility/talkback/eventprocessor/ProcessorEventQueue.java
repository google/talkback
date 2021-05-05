/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.google.android.accessibility.talkback.eventprocessor;

import android.os.Message;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.compositor.EventFilter;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * Manages the event feedback queue. Queued events are run through the {@link Compositor} to
 * generate spoken, haptic, and audible feedback.
 */
public class ProcessorEventQueue implements AccessibilityEventListener {

  private static final String TAG = "ProcessorEventQueue";

  /** Manages pending speech events. */
  private final ProcessorEventHandler handler = new ProcessorEventHandler(this);

  /** Event types that are handled by ProcessorEventQueue. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_EVENT_QUEUE =
      AccessibilityEvent.TYPES_ALL_MASK;

  /**
   * We keep the accessibility events to be processed. If a received event is the same type as the
   * previous one it replaces the latter, otherwise it is added to the queue. All events in this
   * queue are processed while we speak and this occurs after a certain timeout since the last
   * received event.
   */
  private final EventQueue eventQueue = new EventQueue();

  private EventFilter eventFilter;

  public ProcessorEventQueue(EventFilter eventFilter) {
    this.eventFilter = eventFilter;
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_PROCESSOR_EVENT_QUEUE;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {

    synchronized (eventQueue) {
      eventQueue.enqueue(event);
      handler.postSpeak();
    }
  }

  public void clearQueue() {
    handler.removeMessages(WHAT_SPEAK);
  }

  /**
   * Processes an <code>event</code> by asking the {@link Compositor} to match it against its rules
   * and in case an utterance is generated it is spoken. This method is responsible for recycling of
   * the processed event.
   *
   * @param event The event to process.
   */
  private void processAndRecycleEvent(AccessibilityEvent event, EventId eventId) {
    if (event == null) {
      return;
    }
    LogUtils.d(TAG, "Processing event: %s", event);

    eventFilter.sendEvent(event, eventId);

    event.recycle();
  }

  private static final int WHAT_SPEAK = 1;

  private static class ProcessorEventHandler extends WeakReferenceHandler<ProcessorEventQueue> {
    /** Speak action. */

    public ProcessorEventHandler(ProcessorEventQueue parent) {
      super(parent);
    }

    @Override
    public void handleMessage(Message message, ProcessorEventQueue parent) {
      switch (message.what) {
        case WHAT_SPEAK:
          processAllEvents(parent);
          break;
        default: // fall out
      }
    }

    /** Attempts to process all events in the queue. */
    private void processAllEvents(ProcessorEventQueue parent) {
      while (true) {
        final AccessibilityEvent event;

        synchronized (parent.eventQueue) {
          if (parent.eventQueue.isEmpty()) {
            return;
          }

          event = parent.eventQueue.dequeue();
        }

        // Re-generate event id -- slower than passing event id, but avoids modifying
        // the EventQueue to hold event ids.
        EventId eventId = Performance.getInstance().toEventId(event);

        parent.processAndRecycleEvent(event, eventId);
      }
    }

    /**
     * Sends {@link #WHAT_SPEAK} to the speech handler. This method cancels the old message (if such
     * exists) since it is no longer relevant.
     */
    public void postSpeak() {
      if (!hasMessages(WHAT_SPEAK)) {
        sendEmptyMessage(WHAT_SPEAK);
      }
    }
  }
}
