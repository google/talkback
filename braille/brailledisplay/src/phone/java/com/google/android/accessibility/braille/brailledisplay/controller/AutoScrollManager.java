package com.google.android.accessibility.braille.brailledisplay.controller;

import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;
import static java.lang.Math.max;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.icu.text.NumberFormat;
import android.os.Handler;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayTalkBackSpeaker;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorDisplayer;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorNavigation;
import com.google.android.accessibility.braille.brailledisplay.controller.CellsContentManager.OnDisplayContentChangeListener;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.braille.common.TalkBackSpeaker.AnnounceType;
import java.util.Locale;

/**
 * A class for controlling braille display auto-scrolling feature.
 *
 * <p><em>Start or stop auto-scroll:</em>
 *
 * <ul>
 *   <li>It can start or stop it anytime.
 *   <li>It will automatically stop when reach the end of the content.
 *   <li>It will stop if any input when auto-scroll is active except increase/decrease auto-scroll
 *       duration command and pan up/down command.
 * </ul>
 *
 * <p><em>Adjust auto-scroll duration:</em>
 *
 * <ul>
 *   <li>It can increase or decrease duration when auto-scroll is active.
 *   <li>Duration would be reset when to change duration.
 *   <li>Automatically adjust duration depends on the length of the current shown content. It's
 *       default on.
 *   <li>Automatically adjust duration can be turn on/off by user.
 * </ul>
 *
 * <p><em>Reset auto-scroll duration:</em>
 *
 * <ul>
 *   <li>It will reset when the show braille be change on display like pan up/down command,
 *       previous/next item command or gesture.
 * </ul>
 */
public class AutoScrollManager {
  private static final int MILLIS_PER_SECOND = 1000;
  private Context context;
  private final BehaviorNavigation behaviorNavigation;
  private final FeedbackManager feedbackManager;
  private final BehaviorDisplayer behaviorDisplayer;
  private Handler handler;
  private int duration;
  private boolean autoAdjustDurationEnabled;

  public AutoScrollManager(
      Context context,
      BehaviorNavigation behaviorNavigation,
      FeedbackManager feedbackManager,
      BehaviorDisplayer behaviorDisplayer) {
    this.context = context;
    this.behaviorNavigation = behaviorNavigation;
    this.feedbackManager = feedbackManager;
    this.behaviorDisplayer = behaviorDisplayer;
    handler = new Handler();
  }

  /** Starts auto scroll. */
  public void start() {
    handler.removeCallbacksAndMessages(/* token= */ null);
    duration = BrailleUserPreferences.readAutoScrollDuration(context);
    autoAdjustDurationEnabled = BrailleUserPreferences.readAutoAdjustDurationEnable(context);
    handler.postDelayed(runnable, getDuration());
    feedbackManager.emitFeedback(FeedbackManager.TYPE_AUTO_SCROLL_START);
    behaviorDisplayer.addOnDisplayContentChangeListener(onDisplayContentChangeListener);
    BrailleUserPreferences.getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
  }

  /** Stops auto scroll. */
  public void stop() {
    if (isActive()) {
      feedbackManager.emitFeedback(FeedbackManager.TYPE_AUTO_SCROLL_STOP);
    }
    handler.removeCallbacksAndMessages(/* token= */ null);
    behaviorDisplayer.removeOnDisplayContentChangeListener(onDisplayContentChangeListener);
    BrailleUserPreferences.getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
  }

  /** checks if the auto scroll is active. */
  public boolean isActive() {
    return handler.hasCallbacks(runnable);
  }

  /** Increases auto scroll duration. */
  public void increaseDuration() {
    BrailleUserPreferences.increaseAutoScrollDuration(context);
    BrailleDisplayTalkBackSpeaker.getInstance().speak(getSpeakDuration(), AnnounceType.INTERRUPT);
  }

  /** Decreases auto scroll duration. */
  public void decreaseDuration() {
    BrailleUserPreferences.decreaseAutoScrollDuration(context);
    BrailleDisplayTalkBackSpeaker.getInstance().speak(getSpeakDuration(), AnnounceType.INTERRUPT);
  }

  private final Runnable runnable =
      new Runnable() {
        @Override
        public void run() {
          if (!behaviorNavigation.panDown()) {
            // Stop when reach to the end.
            stop();
          }
        }
      };

  private String getSpeakDuration() {
    float second = (float) duration / MILLIS_PER_SECOND;
    return addPrefix(
        context
            .getResources()
            .getQuantityString(
                R.plurals.bd_auto_scroll_duration,
                getQuantity(second),
                NumberFormat.getNumberInstance(Locale.getDefault()).format(second)));
  }

  private int getQuantity(float number) {
    // All number should be a plural except 1.
    return number == 1 ? 1 : 2;
  }

  private String addPrefix(String text) {
    if (duration == BrailleUserPreferences.MINIMUM_AUTO_SCROLL_DURATION_MS) {
      return context.getString(
          R.string.bd_auto_scroll_duration_prefix,
          context.getString(R.string.bd_auto_scroll_duration_minimum),
          text);
    } else if (duration == BrailleUserPreferences.MAXIMUM_AUTO_SCROLL_DURATION_MS) {
      return context.getString(
          R.string.bd_auto_scroll_duration_prefix,
          context.getString(R.string.bd_auto_scroll_duration_maximum),
          text);
    } else {
      return text;
    }
  }

  private int getDuration() {
    if (autoAdjustDurationEnabled) {
      // Automatically adjust duration depends on the length of the display content. For example, if
      // set duration is 3 seconds, max display cells are 14, shown content length is 7, duration *
      // shown content length / max display cells = 3000 * 7 / 14 = 1500ms.
      return max(
          BrailleUserPreferences.MINIMUM_AUTO_SCROLL_DURATION_MS,
          duration
              * behaviorDisplayer.getCurrentShowContentLength()
              / behaviorDisplayer.getMaxDisplayCells());
    }
    return duration;
  }

  private final OnDisplayContentChangeListener onDisplayContentChangeListener =
      () -> {
        handler.removeCallbacksAndMessages(/* token= */ null);
        handler.postDelayed(runnable, getDuration());
      };

  private final OnSharedPreferenceChangeListener onSharedPreferenceChangeListener =
      (SharedPreferences sharedPreferences, String key) -> {
        if (key.equals(context.getString(R.string.pref_bd_auto_scroll_duration_key))) {
          handler.removeCallbacksAndMessages(/* token= */ null);
          duration = BrailleUserPreferences.readAutoScrollDuration(context);
          handler.postDelayed(runnable, getDuration());
        } else if (key.equals(
            context.getString(R.string.pref_bd_auto_adjust_duration_enable_key))) {
          autoAdjustDurationEnabled = BrailleUserPreferences.readAutoAdjustDurationEnable(context);
        }
      };
}
