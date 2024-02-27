/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.android.accessibility.talkback.trainingcommon.tv;

/**
 * Contains constant string keys used in the vendor JSON config.
 *
 * <p>An example config with explanatory comments can be found in talkback_tutorial_config.json.
 */
final class JsonKeywords {
  static final String ENABLED = "enabled";
  static final String TITLE = "title";
  static final String SUMMARY = "summary";
  static final String IMAGE = "image";
  static final String STEPS = "steps";
  static final String CUSTOM_STEPS = "custom_steps";
  static final String STEP_OVERVIEW = "overview_step";
  static final String STEP_REMOTE = "remote_step";
  static final String STEP_SHORTCUT = "shortcut_step";

  private JsonKeywords() {} // Prevent instantiation.
}
