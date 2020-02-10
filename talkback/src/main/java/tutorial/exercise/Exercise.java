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
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.contextmenu.MenuActionInterceptor;
import com.google.android.accessibility.talkback.contextmenu.MenuTransformer;
import com.google.android.accessibility.talkback.tutorial.TutorialLessonPage;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.Performance.EventId;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class Exercise implements AccessibilityEventListener {

  @Nullable private ExerciseCallback callback;
  @Nullable protected TutorialLessonPage mPage;

  public void setTutorialLessonPage(TutorialLessonPage page) {
    mPage = page;
  }

  public abstract View getContentView(LayoutInflater inflater, ViewGroup parent);

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {}

  public void onAction(Context context, String action) {}

  public void onInitialized(Context context) {}

  public void clear() {}

  public void setExerciseCallBack(ExerciseCallback callback) {
    this.callback = callback;
  }

  public boolean needScrollableContainer() {
    return false;
  }

  protected boolean notifyExerciseCompleted(boolean autoSwitchLesson, int completeMessageResId) {
    if (callback == null) {
      return false;
    }

    callback.onExerciseCompleted(autoSwitchLesson, completeMessageResId);
    return true;
  }

  public interface ExerciseCallback {
    public void onExerciseCompleted(boolean autoSwitchLesson, int completeMessageResId);
  }

  public @Nullable MenuTransformer getContextMenuTransformer() {
    return null;
  }

  public @Nullable MenuActionInterceptor getContextMenuActionInterceptor() {
    return null;
  }
}
