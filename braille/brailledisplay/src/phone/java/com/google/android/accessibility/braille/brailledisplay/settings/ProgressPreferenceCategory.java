package com.google.android.accessibility.braille.brailledisplay.settings;

import static android.view.View.GONE;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.os.BuildCompat;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;
import com.google.android.accessibility.braille.brailledisplay.R;

/** {@link PreferenceCategory} with a progress icon on the top right side. */
public class ProgressPreferenceCategory extends PreferenceCategory {
  private boolean progressActive;

  public ProgressPreferenceCategory(
      Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    setLayoutResource(R.layout.preference_progress_category);
  }

  public ProgressPreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setLayoutResource(R.layout.preference_progress_category);
  }

  public ProgressPreferenceCategory(Context context, AttributeSet attrs) {
    super(context, attrs);
    setLayoutResource(R.layout.preference_progress_category);
  }

  public ProgressPreferenceCategory(Context context) {
    super(context);
    setLayoutResource(R.layout.preference_progress_category);
  }

  @Override
  public void onBindViewHolder(PreferenceViewHolder view) {
    super.onBindViewHolder(view);
    final View progressBar = view.findViewById(R.id.scanning_progress);
    // Material design, start from Android S, generally does not have icon for preference. Default
    // hide it.
    if (BuildCompat.isAtLeastS()) {
      view.findViewById(R.id.icon_container).setVisibility(GONE);
    }
    progressBar.setVisibility(progressActive ? View.VISIBLE : GONE);
  }

  /** Whether the progress icon on or off. */
  public void setProgressActive(boolean progressActive) {
    this.progressActive = progressActive;
    notifyChanged();
  }
}
