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

package com.google.android.accessibility.talkback.trainingcommon;

import android.app.Application;
import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId;
import com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId;

/**
 * Wraps the method of {@link TrainingConfigMapper} and {@link PageConfigMapper} to populate the
 * content with given {@link TrainingId} on {@link TrainingActivity}. Must invoke {@link
 * #initialize(TrainingConfigMapper, PageConfigMapper)} in {@link Application#onCreate()} to ensure
 * the instance is available in another process.
 */
public class TrainingActivityInterfaceInjector {

  private static TrainingActivityInterfaceInjector trainingActivityInterfaceInjector;

  /** An interface to map {@link TrainingId} to {@link TrainingConfig}. */
  public interface TrainingConfigMapper {
    @Nullable
    TrainingConfig getTraining(TrainingId trainingId, @Nullable Context context);
  }

  /** An interface to map {@link PageId} to {@link PageConfig}. */
  public interface PageConfigMapper {
    @Nullable
    PageConfig getPage(PageId pageId, Context context, int vendorPageIndex);
  }

  /**
   * Initializes ths instance with given {@link TrainingConfigMapper} and {@link PageConfigMapper}.
   * Must be invoked in {@link Application#onCreate()}.
   */
  public static void initialize(
      TrainingConfigMapper trainingConfigMapper, PageConfigMapper pageConfigMapper) {
    trainingActivityInterfaceInjector =
        new TrainingActivityInterfaceInjector(trainingConfigMapper, pageConfigMapper);
  }

  // TODO: b/285329652
  public static TrainingActivityInterfaceInjector getInstance() {
    if (trainingActivityInterfaceInjector == null) {
      throw new IllegalStateException(
          "Instance is not initialized with TrainingConfigMapper and PageConfigMapper.");
    }
    return trainingActivityInterfaceInjector;
  }

  private final TrainingConfigMapper trainingConfigMapper;
  private final PageConfigMapper pageConfigMapper;

  private TrainingActivityInterfaceInjector(
      TrainingConfigMapper trainingConfigMapper, PageConfigMapper pageConfigMapper) {
    this.pageConfigMapper = pageConfigMapper;
    this.trainingConfigMapper = trainingConfigMapper;
  }

  // TODO: b/285329652
  @Nullable
  public TrainingConfig getTraining(TrainingId trainingId, Context context) {
    return trainingConfigMapper.getTraining(trainingId, context);
  }

  // TODO: b/285329652

  @Nullable
  public PageConfig getPage(PageId pageId, Context context, int vendorPageIndex) {
    return pageConfigMapper.getPage(pageId, context, vendorPageIndex);
  }
}
