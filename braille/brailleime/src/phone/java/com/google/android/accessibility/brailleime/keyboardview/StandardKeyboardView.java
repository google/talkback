/*
 * Copyright (C) 2023 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

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
