/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.android.accessibility.talkback.training.content;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/** A clickable {@link PageContentConfig}. Navigates to some pages when the content is clicked. */
public abstract class ClickableContent extends PageContentConfig {

  /** Handles the clickable action. */
  public interface LinkHandler {
    /**
     * Links to the first page in a section and goes back to the current page after finishing
     * reading the last page in the section.
     */
    void handle(@StringRes int firstPageInSectionNameResId);
  }

  // The handler is set via ClickableContent.setLinkHandler() after supplying the PageConfig
  // argument for TrainingFragment. It must be ignored when serializing PageConfig, to
  // prevent crashing.
  @Nullable protected transient LinkHandler linkHandler;

  public void setLinkHandler(LinkHandler linkHandler) {
    this.linkHandler = linkHandler;
  }
}
