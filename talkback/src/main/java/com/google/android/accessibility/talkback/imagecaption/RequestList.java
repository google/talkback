/*
 * Copyright (C) 2021 Google Inc.
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

package com.google.android.accessibility.talkback.imagecaption;

import static java.lang.Math.max;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;

/**
 * A list of requests. Adds and starts a request to the list via {@link
 * RequestList#addRequest(Request)}. After a request is finished, invoke {@link
 * RequestList#performNextRequest()} to perform the next one.
 */
public class RequestList<T extends Request> {

  private static final String TAG = "RequestsForCaption";
  @VisibleForTesting static final int MSG_RETRY_TO_PERFORM = 1;
  private final SynchronizedArrayDeque<T> requests = new SynchronizedArrayDeque<>();
  private final int capacity;

  /** The interval time of performing requests. */
  private final Duration minIntervalTime;

  private final Handler handler =
      new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
          super.handleMessage(msg);
          if (msg.what == MSG_RETRY_TO_PERFORM) {
            LogUtils.v(TAG, "Retry to perform request");
            performNextRequest(/* shouldRemoveFinishedRequest= */ false);
          }
        }
      };

  private Instant lastRequestExecutionTime = Instant.EPOCH;

  public RequestList(int capacity) {
    this(capacity, /* minIntervalTime= */ Duration.ZERO);
  }

  public RequestList(int capacity, Duration minIntervalTime) {
    this.capacity = capacity;
    this.minIntervalTime = minIntervalTime;
  }

  /**
   * Adds the request to the list. Then, the request is started if there is no other request.
   * Otherwise, the request has to wait for the previous requests to be finished.
   */
  public void addRequest(T request) {
    requests.add(request);

    if (requests.size() > 1) {
      LogUtils.v(
          TAG,
          "addRequest() waiting... %d %s",
          requests.size() - 1,
          request.getClass().getSimpleName());
    } else {
      performNextRequest(/* shouldRemoveFinishedRequest= */ false);
    }
  }

  /**
   * Removed the finished request and performs the first pending request.
   *
   * <p>Discards the older requests if there are too many requests waiting to be executed.
   */
  public void performNextRequest() {
    performNextRequest(/* shouldRemoveFinishedRequest= */ true);
  }

  /**
   * Performs the first pending request or waits until the take-screenshot function is ready.
   *
   * <p>Discards the older requests if there are too many requests waiting to be executed.
   *
   * @param shouldRemoveFinishedRequest {@code true} if the first request that is finished should be
   *     removed
   */
  private void performNextRequest(boolean shouldRemoveFinishedRequest) {
    if (requests.isEmpty()) {
      return;
    }

    if (shouldRemoveFinishedRequest) {
      // Updates lastRequestExecutionTime by the end timestamp which is more accurate than start
      // timestamp.
      T finishedRequest = requests.removeFirst();
      @Nullable Instant finishedRequestEndTimestamp = finishedRequest.getEndTimestamp();
      if (finishedRequestEndTimestamp != null) {
        lastRequestExecutionTime = finishedRequestEndTimestamp;
      }
      if (requests.isEmpty()) {
        return;
      }
    }

    while (requests.size() > capacity) {
      T request = requests.removeFirst();
      LogUtils.v(TAG, "cancel %s size=%d ", request.getClass().getSimpleName(), requests.size());
    }

    Duration intervalTime = Duration.between(lastRequestExecutionTime, Instant.now());
    long waitingTime = minIntervalTime.minus(intervalTime).toMillis();
    if (waitingTime > 0) {
      LogUtils.v(TAG, "waiting... %d ms", waitingTime);
      Message message = new Message();
      message.what = MSG_RETRY_TO_PERFORM;
      T request = requests.getFirst();
      if (handler.sendMessageDelayed(message, waitingTime)) {
        request.onPending(true, intervalTime);
      } else {
        LogUtils.e(TAG, "Fail to send message to the handler.");
        request.onPending(false, intervalTime);
      }
      return;
    }

    T request = requests.getFirst();
    lastRequestExecutionTime = Instant.now();
    request.perform();
    LogUtils.v(TAG, "perform %s", request.getClass().getSimpleName());
  }

  public int getWaitingRequestSize() {
    return max(0, requests.size() - 1);
  }

  @VisibleForTesting
  Handler getHandler() {
    return handler;
  }

  public void clear() {
    while (!requests.isEmpty()) {
      T unused = requests.removeFirst();
    }
  }

  /** A synchronized ArrayDeque which prohibits null elements. */
  private static final class SynchronizedArrayDeque<E> {
    final ArrayDeque<E> arrayDeque = new ArrayDeque<>();
    final Object mutex = new Object();

    private SynchronizedArrayDeque() {}

    public boolean add(@NonNull E request) {
      synchronized (mutex) {
        return arrayDeque.add(request);
      }
    }

    @NonNull
    public E getFirst() {
      synchronized (mutex) {
        return arrayDeque.getFirst();
      }
    }

    @NonNull
    public E removeFirst() {
      synchronized (mutex) {
        return arrayDeque.removeFirst();
      }
    }

    public int size() {
      return arrayDeque.size();
    }

    public boolean isEmpty() {
      return arrayDeque.isEmpty();
    }
  }
}
