package com.google.android.libraries.accessibility.utils;

import android.os.Handler;
import android.os.Looper;

import android.util.Log;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.CountDownLatch;

/**
 * An {@link UncaughtExceptionHandler} for use in Android services to allow for removal of UI
 * upon an uncaught exception from any thread. This should be set before any UI is displayed.
 */
public abstract class ServiceUncaughtExceptionHandler implements UncaughtExceptionHandler {

  private static final UncaughtExceptionHandler DEFAULT_UNCAUGHT_EXCEPTION_HANDLER =
      Thread.getDefaultUncaughtExceptionHandler();
  private final Handler mHandler = new Handler(Looper.getMainLooper());
  private final CountDownLatch mUncaughtExceptionLatch = new CountDownLatch(1);
  private final String mTag;

  /**
   * Create a {@code ServiceUncaughtExceptionHandler} with the default logcat tag.
   */
  public ServiceUncaughtExceptionHandler() {
    this(null);
  }

  /**
   * Create a {@code ServiceUncaughtExceptionHandler} with a custom logcat tag.
   *
   * @param tag The tag to use to log the uncaught exception to logcat. If {@code null}, this class'
   * simple name will be used.
   */
  public ServiceUncaughtExceptionHandler(String tag) {
    mTag = (tag == null)
        ? ServiceUncaughtExceptionHandler.class.getSimpleName()
        : tag;
  }

  /**
   * Logs the caught {@link Throwable} to logcat, calls {@link #shutdown()} on the main thread, and
   * then re-throws the exception to the previous default {@link UncaughtExceptionHandler}.
   */
  @Override
  public void uncaughtException(Thread thread, Throwable throwable) {
    // Not all Android versions will print the stack trace automatically
    Log.e(mTag, "Uncaught exception thrown from thread: " + thread.getName(), throwable);

    if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
      // Can't post to the main handler, as that looper is no longer looping due to the exception
      try {
        shutdown();
      } catch (Throwable t) {
        // If we don't catch all here, we will just infinitely recurse uncaughtException()
      }
    } else {
      mHandler.post(new Runnable() {
          @Override
          public void run() {
            try {
              shutdown();
            } catch (Throwable t) {
              // If we don't catch all here, we will just infinitely recurse uncaughtException()
            } finally {
              mUncaughtExceptionLatch.countDown();
            }
          }
        });
      while (mUncaughtExceptionLatch.getCount() > 0) {
        try {
          mUncaughtExceptionLatch.await();
        } catch (InterruptedException e) {
          // Spurious wakeup, loop and await() again
        }
      }
    }

    if (DEFAULT_UNCAUGHT_EXCEPTION_HANDLER != null) {
      // re-throw the exception so that the android system knows the app has crashed
      DEFAULT_UNCAUGHT_EXCEPTION_HANDLER.uncaughtException(thread, throwable);
    }
  }

  /**
   * This is called on the main thread before rethrowing the uncaught exception. All UI should be
   * removed in this method.
   */
  public abstract void shutdown();

}
