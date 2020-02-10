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
import android.view.View.OnClickListener;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A View that contains a {@link RadioButton} and associated content, such as a subheading. This is
 * used to allow a {@link RadioButton} to be toggled by tapping other View content, per Google
 * Material guidelines.
 *
 * <p>Using a row to wrap both a radio button and its associated content is necessary to prevent the
 * tappable content from being exposed to accessibility multiple times.
 *
 * <p>This view should be a direct descendant of {@link RadioGroupWithSubheadings}.
 */
public final class RadioButtonRowWithSubheading extends LinearLayout {

  /**
   * The {@link RadioButton} that is a direct child of this {@link RadioButtonRowWithSubheading}.
   */
  private RadioButton radioButton;

  public RadioButtonRowWithSubheading(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    // The entire row should be not important for accessibility. This prevents multiple touch
    // targets for the same element, as the child views, including the radio button and subheading,
    // will be exposed to accessibility on their own.
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    // Wait until this view is fully constructed before passing it into the
    // RadioButtonRowOnClickListener constructor because it expects a fully constructed
    // RadioButtonRowWithSubheading.
    this.setOnClickListener(new RadioButtonRowOnClickListener(this));

    // We need to wait until the view has finished inflating to initialize to ensure all the child
    // views are non-null.
    for (int i = 0; i < getChildCount(); i++) {
      View childView = getChildAt(i);
      if (childView instanceof RadioButton) {
        // The first radio button in the hierarchy will be used as the radio button for this row.
        // Additional radio buttons in the hierarchy will be ignored.
        radioButton = (RadioButton) childView;
        radioButton.setOnCheckedChangeListener(
            (buttonView, isChecked) -> {
              ViewParent parent = getParent();
              if (isChecked && parent instanceof RadioGroupWithSubheadings) {
                ((RadioGroupWithSubheadings) parent).check(radioButton.getId());
              }
            });
        break;
      }
    }
  }

  /**
   * Checks or unchecks the radio button associated with this row.
   *
   * @param isChecked {@code true} if the radio button should be checked
   */
  public void check(boolean isChecked) {
    if (radioButton != null && radioButton.isEnabled()) {
      radioButton.setChecked(isChecked);
    }
  }

  /** Returns the id of the {@link RadioButton} associated with this row */
  public int getRadioButtonId() {
    return radioButton != null ? radioButton.getId() : -1;
  }

  /**
   * {@link RadioButtonRowWithSubheading}'s that should toggle a radio button when clicked (i.e. a
   * TextView that contains a particular RadioButton's description) should use this as an
   * OnClickListener.
   */
  static class RadioButtonRowOnClickListener implements OnClickListener {
    private final RadioButtonRowWithSubheading radioButtonRowWithSubheading;

    RadioButtonRowOnClickListener(RadioButtonRowWithSubheading radioButtonRowWithSubheading) {
      this.radioButtonRowWithSubheading = radioButtonRowWithSubheading;
    }

    @Override
    public void onClick(View v) {
      radioButtonRowWithSubheading.check(true);
    }
  }
}
