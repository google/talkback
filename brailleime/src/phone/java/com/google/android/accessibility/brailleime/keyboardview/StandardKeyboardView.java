package com.google.android.accessibility.brailleime.keyboardview;

import android.content.Context;
import android.graphics.Region;
import android.util.Size;
import android.view.View;
import android.widget.FrameLayout;
import com.google.android.accessibility.brailleime.Utils;
import java.util.Optional;

/** A sub-class of {@link KeyboardView} which uses the standard Ime input view. */
public class StandardKeyboardView extends KeyboardView {

  public StandardKeyboardView(Context context, KeyboardViewCallback keyboardViewCallback) {
    super(context, keyboardViewCallback);
  }

  @Override
  public Optional<Region> obtainViewContainerRegionOnTheScreen() {
    if (viewContainer == null) {
      return Optional.empty();
    }
    int[] location = new int[2];
    viewContainer.getLocationOnScreen(location);
    return Optional.of(
        new Region(
            location[0],
            location[1],
            location[0] + viewContainer.getWidth(),
            location[1] + viewContainer.getHeight()));
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
    Size viewSize = getScreenSize();
    FrameLayout.LayoutParams layoutParams =
        new FrameLayout.LayoutParams(viewSize.getWidth(), viewSize.getHeight());
    viewContainer.setLayoutParams(layoutParams);
    viewContainer.post(keyboardViewCallback::onViewUpdated);
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
