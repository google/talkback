package com.google.android.accessibility.talkback.overlay;

import android.content.Context;

import com.google.android.accessibility.talkback.Feedback;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Implementation for log overlay tool */
public class DevInfoOverlayController {

    private static final String TAG = "DevInfoOverlayController";

    private boolean enabled;
    private @NonNull Context context;
    private @Nullable BlockOutOverlay blockOutOverlay;


    public DevInfoOverlayController(Context context) {
        this.context = context;
    }

    /** Highlights focused View after a focus-action. */
    public void displayFeedback(Feedback feedback) {
        if (!enabled || blockOutOverlay == null) {
            return;
        }

        Feedback.@Nullable Part failover =
                (feedback.failovers() == null || feedback.failovers().isEmpty()
                        ? null
                        : feedback.failovers().get(0));
        if (failover == null) {
            return;
        }
        // Filter for FOCUS and FOCUS DIRECTION actions,
        // which mark beg/end of swipe gesture + associated focus
        if (failover.focus() == null
                && failover.focusDirection() == null
                && failover.scroll() == null) {
            return;
        }
        /** Check to make sure eventID isn't null before checking for gestures */
        if (feedback.eventId() == null) {
            return;
        }

//    if (feedback.eventId().getEventSubtype() == AccessibilityEvent.TYPE_WINDOWS_CHANGED
//        || feedback.eventId().getEventSubtype() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
//        || failover.scroll() != null) {
//      blurOutOverlay.clearHighlight();
//    }

        if (failover.focus() != null) {
            Feedback.@Nullable Focus focus = failover.focus();
            if (focus.target() != null) {
                blockOutOverlay.refresh(focus.target());
            }
        }
    }

    public void setOverlayEnabled(boolean enabled) {
        if (enabled == this.enabled) {
            return;
        }
        if (enabled) {
            if (blockOutOverlay == null) {
                blockOutOverlay = new BlockOutOverlay(context);
            }
        } else {
            if (blockOutOverlay != null) {
                blockOutOverlay.hide();
                blockOutOverlay = null;
            }
        }
        this.enabled = enabled;
    }
}