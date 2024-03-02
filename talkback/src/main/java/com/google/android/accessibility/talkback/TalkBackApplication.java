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

package com.google.android.accessibility.talkback;

import com.google.android.accessibility.talkback.training.PageConfigMapperImpl;
import com.google.android.accessibility.talkback.training.TrainingConfigMapperImpl;
import com.google.android.accessibility.talkback.trainingcommon.TrainingActivityInterfaceInjector;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.play.core.splitcompat.SplitCompatApplication;

/**
 * A top level Application for TalkBack. It extends SplitCompatApplication to enable SplitCompat,
 * which allows our app to access code and resources from a downloaded module.
 */
public class TalkBackApplication extends SplitCompatApplication {

  @Override
  public void onCreate() {
    super.onCreate();

    FormFactorUtils.initialize(this);
    TrainingActivityInterfaceInjector.initialize(
        new TrainingConfigMapperImpl(), new PageConfigMapperImpl());
  }
}
