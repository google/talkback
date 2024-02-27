package com.google.android.accessibility.talkback.trainingcommon.tv;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Variation of {@link LinearLayout} that ignores the accessibility action SHOW_ON_SCREEN.
 *
 * <p>The {@code ScrollView} would scroll to center.
 */
class TvTutorialPageView extends LinearLayout {
  public TvTutorialPageView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    setAccessibilityDelegate(
        new AccessibilityDelegate() {
          @Override
          public boolean performAccessibilityAction(
              @NonNull View host, int action, @Nullable Bundle args) {
            if (action == AccessibilityActionCompat.ACTION_SHOW_ON_SCREEN.getId()) {
              return false; // Do nothing.
            }
            return super.performAccessibilityAction(host, action, args);
          }
        });
  }
}
