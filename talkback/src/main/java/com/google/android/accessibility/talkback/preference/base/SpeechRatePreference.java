package com.google.android.accessibility.talkback.preference.base;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.widget.SeekBar;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.preference.PreferenceViewHolder;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.actor.SpeechRateActor;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Preference for adjusting talkback speech rate. The state description and increase/decrease value
 * actions are customized to match the SpeechRateActor used in selector.
 */
public class SpeechRatePreference extends AccessibilitySeekBarPreference {

  private SeekBar seekBar = null;

  public SpeechRatePreference(@NonNull Context context) {
    super(context);
  }

  public SpeechRatePreference(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
    super.onBindViewHolder(holder);
    seekBar = (SeekBar) holder.findViewById(androidx.preference.R.id.seekbar);

    if (seekBar == null) {
      return;
    }

    ViewCompat.setStateDescription(
        seekBar, getContext().getString(R.string.template_percent, String.valueOf(getValue())));

    ViewCompat.replaceAccessibilityAction(
        seekBar,
        AccessibilityActionCompat.ACTION_SCROLL_BACKWARD,
        null,
        (view, arguments) -> {
          int newRate =
              (int)
                  Math.max(
                      getValue() / SpeechRateActor.RATE_STEP, SpeechRateActor.RATE_MINIMUM * 100);
          setSeekBarValue(newRate);
          return true;
        });

    ViewCompat.replaceAccessibilityAction(
        seekBar,
        AccessibilityActionCompat.ACTION_SCROLL_FORWARD,
        null,
        (view, arguments) -> {
          int newRate =
              (int)
                  Math.min(
                      getValue() * SpeechRateActor.RATE_STEP, SpeechRateActor.RATE_MAXIMUM * 100);
          setSeekBarValue(newRate);
          return true;
        });
  }

  public @Nullable SeekBar getSeekBar() {
    return seekBar;
  }

  private void setSeekBarValue(int newValue) {
    if (seekBar == null) {
      return;
    }

    int forcedValue =
        (int) (SpeechRateActor.forceRateToDefaultWhenClose((float) newValue / 100.0f) * 100);

    // We can't call seekBar.setProgress() because it calls setProgressInternal(int progress,
    // boolean fromUser, boolean animate) with fromUser being false. Use set progress accessibility
    // action instead so that fromUser will be true when calling setProgressInternal(int progress,
    // boolean fromUser, boolean animate). SeekBarPreference only calls OnPreferenceChangeListener
    // when fromUser is true.
    Bundle arg = new Bundle();
    arg.putFloat(
        AccessibilityNodeInfoCompat.ACTION_ARGUMENT_PROGRESS_VALUE, forcedValue - getMin());
    seekBar.performAccessibilityAction(android.R.id.accessibilityActionSetProgress, arg);
  }
}
