package com.google.android.accessibility.brailleime.keyboardview;

import android.content.Context;
import android.util.Size;
import android.view.View;
import android.widget.FrameLayout;
import com.google.android.accessibility.brailleime.Utils;

/** A sub-class of {@link KeyboardView} which uses the standard Ime input view. */
public class StandardKeyboardView extends KeyboardView {

  private final boolean fullScreen;

  public StandardKeyboardView(
      Context context, KeyboardViewCallback keyboardViewCallback, boolean fullScreen) {
    super(context, keyboardViewCallback);
    this.fullScreen = fullScreen;
  }

  @Override
  protected View createImeInputViewInternal() {
    if (imeInputView == null) {
      imeInputView = new ViewContainer(context);
    }
    return imeInputView;
  }

  @Override
  protected ViewContainer createViewContainerInternal() {
    if (viewContainer == null) {
      if (imeInputView == null) {
        imeInputView = createImeInputViewInternal();
      }
      viewContainer = (ViewContainer) imeInputView;
    }
    return viewContainer;
  }

  @Override
  protected void updateViewContainerInternal() {
    if (fullScreen) {
      Size viewSize = getScreenSize();
      FrameLayout.LayoutParams layoutParams =
          new FrameLayout.LayoutParams(viewSize.getWidth(), viewSize.getHeight());
      viewContainer.setLayoutParams(layoutParams);
      viewContainer.post(keyboardViewCallback::onViewUpdated);
    }
  }

  @Override
  public void setKeyboardViewTransparent(boolean isTransparent) {
    // Empty here.
  }

  @Override
  protected Size getScreenSize() {
    return Utils.getVisibleDisplaySizeInPixels(viewContainer);
  }

  @Override
  protected void tearDownInternal() {
    // Empty here.
  }
}
