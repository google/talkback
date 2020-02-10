package com.google.android.libraries.accessibility.utils.concurrent;

import android.os.Handler;
import java.util.concurrent.Executor;

/**
 * Simple executor that posts runnables to the provided handler. Note that much has been written
 * against this approach internally at Google, because when used in an Activity (or Fragment),
 * typically the operation destined for the UI thread will leak a reference to the Activity during
 * the background task execution in a way that is not lifecycle aware. See  and
 *  for some details. This should not affect our Service, which has a simpler
 * lifecycle than an Activity. Specifically, Services are only created once and are destroyed only
 * when the service is being stopped and the process will be destroyed:
 * https://developer.android.com/guide/components/services#Lifecycle
 */
public class HandlerExecutor implements Executor {

  private final Handler handler;

  public HandlerExecutor(Handler handler) {
    this.handler = handler;
  }

  @Override
  public void execute(Runnable runnable) {
    handler.post(runnable);
  }
}
