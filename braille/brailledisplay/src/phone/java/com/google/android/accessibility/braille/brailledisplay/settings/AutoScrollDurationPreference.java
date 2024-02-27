package com.google.android.accessibility.braille.brailledisplay.settings;

import android.content.Context;
import android.icu.text.NumberFormat;
import android.util.AttributeSet;
import android.widget.TextView;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.material.slider.Slider;
import java.util.Locale;

/** A preference for adjust the auto scroll duration. */
public class AutoScrollDurationPreference extends Preference {
  private static final int MILLIS_PER_SECOND = 1000;
  private Slider slider;

  public AutoScrollDurationPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    setLayoutResource(R.layout.auto_scroll_slider);
  }

  @Override
  public void onBindViewHolder(PreferenceViewHolder holder) {
    super.onBindViewHolder(holder);
    holder.itemView.setClickable(false);

    TextView textValue = (TextView) holder.findViewById(R.id.slider_value);

    slider = (Slider) holder.findViewById(R.id.slider);
    slider.setValueTo(
        (float) BrailleUserPreferences.MAXIMUM_AUTO_SCROLL_DURATION_MS / MILLIS_PER_SECOND);
    slider.setValueFrom(
        (float) BrailleUserPreferences.MINIMUM_AUTO_SCROLL_DURATION_MS / MILLIS_PER_SECOND);
    slider.setStepSize(
        (float) BrailleUserPreferences.AUTO_SCROLL_DURATION_INTERVAL_MS / MILLIS_PER_SECOND);
    slider.setValue(getCurrentDuration());
    slider.addOnChangeListener(
        (slider, value, fromUser) -> {
          float currentDuration = getCurrentDuration();
          if (currentDuration > value) {
            BrailleUserPreferences.decreaseAutoScrollDuration(getContext());
          } else if (currentDuration < value) {
            BrailleUserPreferences.increaseAutoScrollDuration(getContext());
          }
          textValue.setText(
              getContext()
                  .getString(
                      R.string.bd_auto_scroll_duration_value_text,
                      String.valueOf(
                          NumberFormat.getNumberInstance(Locale.getDefault())
                              .format(slider.getValue()))));
          slider.announceForAccessibility(getPrefix());
        });
    slider.setLabelFormatter(
        value -> getContext().getString(R.string.bd_auto_scroll_duration_unit));

    textValue.setText(
        getContext()
            .getString(
                R.string.bd_auto_scroll_duration_value_text,
                String.valueOf(
                    NumberFormat.getNumberInstance(Locale.getDefault())
                        .format(slider.getValue()))));
  }

  /** Sets value of auto scroll duration slider. */
  public void setValue(int durationMs) {
    if (slider != null) {
      slider.setValue((float) durationMs / MILLIS_PER_SECOND);
    }
  }

  private float getCurrentDuration() {
    return (float) BrailleUserPreferences.readAutoScrollDuration(getContext()) / MILLIS_PER_SECOND;
  }

  private String getPrefix() {
    if (slider.getValue() == slider.getValueFrom()) {
      return getContext().getString(R.string.bd_auto_scroll_duration_minimum);
    } else if (slider.getValue() == slider.getValueTo()) {
      return getContext().getString(R.string.bd_auto_scroll_duration_maximum);
    } else {
      return "";
    }
  }
}
