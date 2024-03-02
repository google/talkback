package com.google.android.libraries.accessibility.utils.concurrent;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import com.google.common.util.concurrent.AbstractListeningExecutorService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * A {@code ListeningExecutorService} which runs tasks sequentially and causes newly-added tasks to
 * preempt previously enqueued tasks. In this fashion, the service will always process the latest
 * available request. It can also preempt currently-running tasks if configured to do so.
 */
public class PreemptingExecutorService extends AbstractListeningExecutorService {

  final ExecutorService delegate;
  final boolean preemptRunning;

  @GuardedBy("this")
  @Nullable
  Future<?> enqueuedTask = null;

  public PreemptingExecutorService(boolean preemptRunning) {
    String nameFormat = "a11y-preempting-%s";
    if (!preemptRunning) {
      nameFormat = "a11y-non-preempting-%s";
    }
    delegate =
        Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat(nameFormat).build());
    this.preemptRunning = preemptRunning;
  }

  @Override
  public final boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  @Override
  public final boolean isShutdown() {
    return delegate.isShutdown();
  }

  @Override
  public final boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public final void shutdown() {
    delegate.shutdown();
  }

  @Override
  public final List<Runnable> shutdownNow() {
    return delegate.shutdownNow();
  }

  @Override
  public final void execute(Runnable runnable) {
    Future<?> future =
        (runnable instanceof Future) ? (Future<?>) runnable : new FutureTask<>(runnable, false);
    synchronized (this) {
      if (enqueuedTask != null) {
        // We interrupt regardless of preemptRunning, because this handles the edge case when the
        // task first starts, and after that point enqueuedTask will be null so we will not
        // interrupt later.
        enqueuedTask.cancel(true);
        enqueuedTask = null;
      }
      delegate.execute(
          () -> {
            synchronized (this) {
              if (Thread.currentThread().isInterrupted()) {
                // This case may (rarely) arise when the enqueuedTask has been canceled just after
                // the delegate has picked up the task. We preserve the interrupted status because
                // we cannot throw an InterruptedException from a Runnable.
                return;
              } else {
                // If we don't allow preempting a running task, set enqueuedTask to null so it
                // cannot be canceled after this point.
                if (!preemptRunning) {
                  enqueuedTask = null;
                }
              }
            }
            runnable.run();
          });
      enqueuedTask = future;
    }
  }
}
