/*
 * Copyright (C) 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.android.accessibility.switchaccess.setupwizard.radiobutton;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RadioGroup;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A type of {@link RadioGroup} that allows other elements to select a radio button, such as a
 * subheading. This is not supported in the default {@link RadioGroup} API.
 *
 * <p>{@link RadioButtonRowWithSubheading}s should be the only direct children of this class, which
 * will wrap the {@link android.widget.RadioButton}s and their subheadings. This class manages the
 * checked state of its {@link android.widget.RadioButton} descendants.
 *
 * <p>Tapping one {@link RadioButtonRowWithSubheading} will deselect the other {@link
 * android.widget.RadioButton}s in the view hierarchy.
 */
public final class RadioGroupWithSubheadings extends RadioGroup {

  /** The listener to be notified of changes to checked radio button state. */
  private OnCheckedChangeListener listener;

  /** The id of the currently selected radio button, -1 if nothing is selected. */
  private int currentlyCheckedRadioButton = -1;

  // RadioGroup's constructor doesn't annotate its AttributeSet parameter as nullable, but its super
  // class does. attrs is used for two things: passing into the super constructor, and passing into
  // #obtainStyledAttributes. This method takes nullable even though it's not annotated as such
  // because RadioGroup's super class passes null into the method.
  @SuppressWarnings("nullness:argument.type.incompatible")
  public RadioGroupWithSubheadings(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void check(int radioButtonId) {
    boolean wasRadioButtonChecked = false;
    for (int i = 0; i < this.getChildCount(); i++) {
      View child = getChildAt(i);
      if (child instanceof RadioButtonRowWithSubheading) {
        RadioButtonRowWithSubheading row = (RadioButtonRowWithSubheading) child;
        boolean shouldCheckRow = (row.getRadioButtonId() == radioButtonId);
        row.check(shouldCheckRow);
        wasRadioButtonChecked |= shouldCheckRow;
      }
    }
    // If no radio button was checked, set the current checked id to -1.
    currentlyCheckedRadioButton = wasRadioButtonChecked ? radioButtonId : -1;
    if (listener != null) {
      listener.onCheckedChanged(this, radioButtonId);
    }
  }

  @Override
  public void setOnCheckedChangeListener(OnCheckedChangeListener onCheckedChangeListener) {
    listener = onCheckedChangeListener;
  }

  @Override
  public int getCheckedRadioButtonId() {
    return currentlyCheckedRadioButton;
  }
}
