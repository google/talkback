package com.google.android.accessibility.brailleime.keyboardview;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Region;
import android.os.Build;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.FrameLayout;
import com.google.android.accessibility.brailleime.Utils;
import java.util.Optional;

/**
 * A sub-class of {@link KeyboardView} which uses an Accessibility Overlay for taking touch input.
 */
public class AccessibilityOverlayKeyboardView extends KeyboardView {

  public AccessibilityOverlayKeyboardView(
      Context context, KeyboardViewCallback keyboardViewCallback) {
    super(context, keyboardViewCallback);
  }

  @Override
  public Optional<Region> obtainViewContainerRegionOnTheScreen() {
    // We are using full screen accessibility overlay view here, so return empty Region.
    return Optional.of(new Region());
  }

  @Override
  protected View createImeInputViewInternal() {
    if (imeInputView == null) {
      if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
        View fullScreenView = new View(context);
        LayoutParams layoutParams =
            new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        fullScreenView.setLayoutParams(layoutParams);
        imeInputView = fullScreenView;
      } else {
        imeInputView = new FrameLayout(context);
      }
      // Ensure non-zero height to make sure framework considers the inputView significant enough to
      // include in the info sent to TalkBack.
      imeInputView.setMinimumHeight(1);
    }
    return imeInputView;
  }

  @Override
  protected ViewContainer createViewContainerInternal() {
    if (viewContainer == null) {
      viewContainer = new ViewContainer(context);
    }
    windowManager.addView(viewContainer, getWindowsLayoutParams());
    return viewContainer;
  }

  @Override
  protected void updateViewContainerInternal() {
    windowManager.updateViewLayout(viewContainer, getWindowsLayoutParams());
  }

  @Override
  protected Size getScreenSize() {
    return Utils.getDisplaySizeInPixels(context);
  }

  @Override
  protected void tearDownInternal() {
    if (windowManager != null) {
      windowManager.removeViewImmediate(viewContainer);
    }
  }

  private WindowManager.LayoutParams getWindowsLayoutParams() {
    WindowManager.LayoutParams params = new WindowManager.LayoutParams();
    params.format = PixelFormat.TRANSLUCENT;
    params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    params.gravity = Gravity.TOP;
    params.gravity |= Utils.isNavigationBarLeftLocated(context) ? Gravity.RIGHT : Gravity.LEFT;
    final Size size = Utils.getDisplaySizeInPixels(context);
    params.height = size.getHeight();
    params.width = size.getWidth();
    return params;
  }
}
