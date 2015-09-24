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

package com.android.talkback.eventprocessor;

import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.util.SparseIntArray;
import android.view.accessibility.AccessibilityEvent;
import com.android.utils.AccessibilityEventUtils;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * This class is a queue that tracks events that TalkBack will process. This
 * queue self-prunes events that exceed a maximum threshold for certain event
 * types.
 */
class EventQueue {

    /**
     * The maximum number of events for each type included in
     * {@code MASK_LIMITED_EVENT_TYPES} that may remain in the queue.
     */
    private static final int MAXIMUM_QUALIFYING_EVENTS = 2;

    /**
     * The types of events that should be pruned if there are more than
     * {@code MAXIMUM_QUALIFYING_EVENTS} of these events in the queue.
     */
    private static final int MASK_LIMITED_EVENT_TYPES =
            AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER |
            AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED;

    /**
     * The list responsible for maintaining events in the event queue.
     */
    private final ArrayList<AccessibilityEvent> mEventQueue = new ArrayList<>();

    /**
     * The current number of events in the queue for each event type that match
     * a type defined in {@code MASK_LIMITED_EVENT_TYPES}.
     */
    private final SparseIntArray mQualifyingEvents = new SparseIntArray();

    /**
     * Adds an {@link AccessibilityEvent} to the queue for processing. If this
     * addition causes the queue to exceed the maximum allowable events for an
     * event's type, earlier events of this type will be pruned from the queue.
     *
     * @param event The event to add to the queue
     */
    public void enqueue(AccessibilityEvent event) {
        final AccessibilityEvent clone = AccessibilityEvent.obtain(event);
        final int eventType = clone.getEventType();

        if (AccessibilityEventUtils.eventMatchesAnyType(clone, MASK_LIMITED_EVENT_TYPES)) {
            final int eventCountOfType = mQualifyingEvents.get(eventType, 0);
            mQualifyingEvents.put(eventType, (eventCountOfType + 1));
        }

        mEventQueue.add(clone);
        enforceEventLimits();
    }

    /**
     * Removes and returns an AccessibilityEvent from the front of the event queue.
     *
     * @return The event at the front of the queue.
     */
    public AccessibilityEvent dequeue() {
        if (mEventQueue.isEmpty()) {
            return null;
        }

        final AccessibilityEvent event = mEventQueue.remove(0);

        if (event != null
                && AccessibilityEventUtils.eventMatchesAnyType(event, MASK_LIMITED_EVENT_TYPES)) {
            final int eventType = event.getEventType();
            final int eventCountOfType = mQualifyingEvents.get(eventType, 0);
            mQualifyingEvents.put(eventType, (eventCountOfType - 1));
        }
        return event;
    }

    /**
     * Clears the event queue and discards all events waiting for processing.
     */
    public void clear() {
        mEventQueue.clear();
        mQualifyingEvents.clear();
    }

    /**
     * Determines if the event queue is empty.
     *
     * @return {@code true} if the queue is empty, {@code false} otherwise
     */
    public boolean isEmpty() {
        return mEventQueue.isEmpty();
    }

    /**
     * Enforces that the event queue has no more than
     * {@code MAXIMUM_QUALIFYING_EVENTS} events of each type defined by
     * {@code MASK_LIMITED_EVENT_TYPES}. The excessive events are pruned by
     * removing the oldest event first.
     */
    private void enforceEventLimits() {
        int eventTypesToPrune = 0;
        // Locate event types which exceed the allowable limit
        for (int i = 0; i < mQualifyingEvents.size(); i++) {
            final int eventType = mQualifyingEvents.keyAt(i);
            final int eventsOfType = mQualifyingEvents.valueAt(i);
            if (eventsOfType > MAXIMUM_QUALIFYING_EVENTS) {
                eventTypesToPrune |= eventType;
            }
        }

        final Iterator<AccessibilityEvent> iterator = mEventQueue.iterator();
        while (iterator.hasNext() && (eventTypesToPrune != 0)) {
            final AccessibilityEvent next = iterator.next();

            // Prune offending events
            if (AccessibilityEventUtils.eventMatchesAnyType(next, eventTypesToPrune)) {
                final int eventType = next.getEventType();
                int eventCountOfType = mQualifyingEvents.get(eventType, 0);
                eventCountOfType--;
                mQualifyingEvents.put(eventType, eventCountOfType);
                iterator.remove();

                // Stop pruning further events of this type if the number of
                // events is below the limit
                if (eventCountOfType <= MAXIMUM_QUALIFYING_EVENTS) {
                    eventTypesToPrune &= ~eventType;
                }
            }
        }
    }
}
