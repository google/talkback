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

package com.android.talkback.tutorial.exercise;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.talkback.contextmenu.MenuActionInterceptor;
import com.android.talkback.contextmenu.MenuTransformer;
import com.google.android.marvin.talkback.TalkBackService;

public abstract class ContextMenuExercise extends Exercise {

    private SpeechController mSpeechController;
    private ImageView mImageView;

    private MenuTransformer mContextMenuTransformer = new MenuTransformer() {
        @Override
        public void transformMenu(Menu menu, int menuId) {
            int menuSize = menu.size();
            for (int index = 0; index < menuSize; index++) {
                menu.getItem(index).setEnabled(false);
                menu.getItem(index).setVisible(true);
            }
            menu.add(Menu.NONE, R.id.hear_lesson, Menu.NONE,
                    R.string.shortcut_hear_lesson);
        }
    };

    private MenuActionInterceptor mContextMenuActionInterceptor = new MenuActionInterceptor() {
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
            mSpeechController = service.getSpeechController();
        }
    }

    @Override
    public View getContentView(final LayoutInflater inflater, ViewGroup parent) {
        View view = inflater.inflate(R.layout.tutorial_content_image, parent, false);
        mImageView = (ImageView) view.findViewById(R.id.image);
        updateImage();
        return view;
    }

    protected void updateImage() {
        mImageView.setImageResource(getImageResource());
        mImageView.setContentDescription(getContentDescription(mImageView.getContext()));
    }

    public abstract int getImageResource();
    public abstract CharSequence getContentDescription(Context context);

    private void announceLesson() {
        TalkBackService service = TalkBackService.getInstance();
        if (mSpeechController == null || service == null) {
            return;
        }

        mSpeechController.speak(service.getString(R.string.tutorial_lesson_3_message), null,
                null, SpeechController.QUEUE_MODE_UNINTERRUPTIBLE, 0, 0,
                null, null, null);
    }

    private void announceDisabledMenuItem(CharSequence itemTitle) {
        if (mSpeechController == null) {
            return;
        }

        TalkBackService service = TalkBackService.getInstance();
        if (service == null) {
            return;
        }

        String text = service.getString(R.string.tutorial_disabled_item_clicked, itemTitle);
        mSpeechController.speak(text, null,
                null, SpeechController.QUEUE_MODE_UNINTERRUPTIBLE, 0, 0,
                null, null, null);
    }

    public MenuTransformer getContextMenuTransformer() {
        return mContextMenuTransformer;
    }

    public MenuActionInterceptor getContextMenuActionInterceptor() {
        return mContextMenuActionInterceptor;
    }

    public abstract void onMenuCancelButtonClicked();
}
