/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.compositor;

import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.Nullable;

/**
 * Interprets {@link AccessibilityEvent#TYPE_VIEW_ACCESSIBILITY_FOCUSED} events. attaches source
 * action information to the event interpretation.
 */
public interface AccessibilityFocusEventInterpreter {

  /** Extract an accessibility focused event interpretation data from event. May return null. */
  @Nullable
  AccessibilityFocusEventInterpretation interpret(AccessibilityEvent event);
}
