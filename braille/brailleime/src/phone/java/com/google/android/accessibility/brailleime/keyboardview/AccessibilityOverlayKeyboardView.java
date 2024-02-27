package com.google.android.accessibility.brailleime.keyboardview;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.FrameLayout;
import com.google.android.accessibility.brailleime.Utils;

/**
 * A sub-class of {@link KeyboardView} which uses an Accessibility Overlay for taking touch input.
 */
public class AccessibilityOverlayKeyboardView extends KeyboardView {
  protected static final float SCREEN_HIDE_ALPHA = 0.001f;
  protected static final float SCREEN_SHOW_ALPHA = 1;

  public AccessibilityOverlayKeyboardView(
      Context context, KeyboardViewCallback keyboardViewCallback) {
    super(context, keyboardViewCallback);
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
    if (keyboardViewCallback.isHideScreenMode()) {
      viewContainer.setAlpha(SCREEN_HIDE_ALPHA);
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

  @Override
  public void setKeyboardViewTransparent(boolean isTransparent) {
    if (viewContainer != null) {
      float alpha = isTransparent ? SCREEN_HIDE_ALPHA : SCREEN_SHOW_ALPHA;
      viewContainer.setAlpha(alpha);
    }
  }

  private WindowManager.LayoutParams getWindowsLayoutParams() {
    WindowManager.LayoutParams params = new WindowManager.LayoutParams();
    params.format = PixelFormat.TRANSLUCENT;
    params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    // A workaround for b/258382964, which only occurs in T.
    if (Build.VERSION.SDK_INT == VERSION_CODES.TIRAMISU) {
      params.flags |= WindowManager.LayoutParams.FLAG_SPLIT_TOUCH;
    }
    params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    params.gravity = Gravity.TOP;
    params.gravity |= Utils.isNavigationBarLeftLocated(context) ? Gravity.RIGHT : Gravity.LEFT;
    final Size size = Utils.getDisplaySizeInPixels(context);
    params.height = size.getHeight();
    params.width = size.getWidth();

    return params;
  }
}
