package com.google.android.accessibility.talkback.trainingcommon.tv;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Variation of {@link ScrollView} that scrolls to top when it gets focus.
 *
 * <p>The {@code ScrollView} would scroll to center.
 */
class TvTutorialScrollView extends ScrollView {
  public TvTutorialScrollView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  /** Does nothing. */
  @Override
  public void scrollToDescendant(@NonNull View child) {
    // Do nothing.
  }
}
