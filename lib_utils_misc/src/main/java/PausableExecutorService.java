package com.google.android.libraries.accessibility.utils.concurrent;

import androidx.annotation.GuardedBy;
import com.google.common.util.concurrent.AbstractListeningExecutorService;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A {@code ListeningExecutorService} which may be paused such that newly-submitted tasks are
 * enqueued until resumed. Pausing does not affect tasks which have already been submitted.
 * Compatible with same-thread executors without causing deadlock. May be used in unit tests to
 * temporarily block execution of newly-submitted tasks.
 */
public class PausableExecutorService extends AbstractListeningExecutorService {

  private final ListeningExecutorService delegate;

  @GuardedBy("this")
  private final List<Runnable> queue = new ArrayList<>();

  @GuardedBy("this")
  private boolean paused = false;

  public PausableExecutorService(ListeningExecutorService delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  @Override
  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public void shutdown() {
    delegate.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    return delegate.shutdownNow();
  }

  @Override
  public synchronized void execute(Runnable runnable) {
    if (paused) {
      queue.add(runnable);
    } else {
      delegate.execute(runnable);
    }
  }

  public synchronized void pause() {
    paused = true;
  }

  public synchronized void resume() {
    paused = false;
    for (Runnable runnable : queue) {
      delegate.execute(runnable);
    }
    queue.clear();
  }
}
