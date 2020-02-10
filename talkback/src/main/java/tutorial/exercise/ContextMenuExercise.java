/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.accessibility.talkback.tutorial.exercise;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.contextmenu.MenuActionInterceptor;
import com.google.android.accessibility.talkback.contextmenu.MenuTransformer;
import com.google.android.accessibility.utils.output.SpeechController;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class ContextMenuExercise extends Exercise {

  @Nullable private SpeechController speechController;
  @Nullable private ImageView imageView;

  private MenuTransformer contextMenuTransformer =
      new MenuTransformer() {
        @Override
        public void transformMenu(Menu menu, int menuId) {
          int menuSize = menu.size();
          for (int index = 0; index < menuSize; index++) {
            menu.getItem(index).setEnabled(false);
            menu.getItem(index).setVisible(true);
          }
          menu.add(Menu.NONE, R.id.hear_lesson, Menu.NONE, R.string.shortcut_hear_lesson);
        }
      };

  private MenuActionInterceptor contextMenuActionInterceptor =
      new MenuActionInterceptor() {
        @Override
        public boolean onInterceptMenuClick(MenuItem item) {
          if (item.getItemId() == R.id.hear_lesson) {
            announceLesson();
          } else {
            announceDisabledMenuItem(item.getTitle());
          }
          return true;
        }

        @Override
        public void onCancelButtonClicked() {
          onMenuCancelButtonClicked();
        }
      };

  public ContextMenuExercise() {
    TalkBackService service = TalkBackService.getInstance();
    if (service != null) {
      speechController = service.getSpeechController();
    }
  }

  @Override
  public View getContentView(final LayoutInflater inflater, ViewGroup parent) {
    View view = inflater.inflate(R.layout.tutorial_content_image, parent, false);
    imageView = (ImageView) view.findViewById(R.id.image);
    updateImage();
    return view;
  }

  protected void updateImage() {
    ImageView view = this.imageView;
    if (view != null) {
      view.setImageResource(getImageResource());
      view.setContentDescription(getContentDescription(view.getContext()));
    }
  }

  public abstract int getImageResource();

  public abstract CharSequence getContentDescription(Context context);

  private void announceLesson() {
    TalkBackService service = TalkBackService.getInstance();
    if (speechController == null || service == null) {
      return;
    }

    speechController.speak(
        service.getString(R.string.tutorial_lesson_3_message),
        null,
        null,
        SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH,
        0,
        0,
        null,
        null,
        null);
  }

  private void announceDisabledMenuItem(CharSequence itemTitle) {
    if (speechController == null) {
      return;
    }

    TalkBackService service = TalkBackService.getInstance();
    if (service == null) {
      return;
    }

    String text = service.getString(R.string.tutorial_disabled_item_clicked, itemTitle);
    speechController.speak(
        text,
        null,
        null,
        SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH,
        0,
        0,
        null,
        null,
        null);
  }

  @Override
  public MenuTransformer getContextMenuTransformer() {
    return contextMenuTransformer;
  }

  @Override
  public MenuActionInterceptor getContextMenuActionInterceptor() {
    return contextMenuActionInterceptor;
  }

  public abstract void onMenuCancelButtonClicked();
}
