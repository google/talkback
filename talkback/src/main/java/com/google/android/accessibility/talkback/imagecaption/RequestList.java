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

import androidx.annotation.NonNull;
import com.google.android.accessibility.talkback.imagecaption.RequestList.Request;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayDeque;

/**
 * A list of requests. Adds and starts a request to the list via {@link
 * RequestList#addRequest(Request)}. After a request is finished, invoke {@link
 * RequestList#performNextRequest()} to perform the next one.
 */
public class RequestList<T extends Request> {

  /** An image caption action. */
  public interface Request {
    /** Starts the action. */
    void perform();
  }

  private static final String TAG = "RequestListForCaption";
  private final SynchronizedArrayDeque<T> requests = new SynchronizedArrayDeque<>();
  private final int capacity;

  public RequestList(int capacity) {
    this.capacity = capacity;
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
      T firstRequest = requests.getFirst();
      firstRequest.perform();
    }
  }

  /**
   * Performs the next request. If there are too many requests waiting to be executed in the list,
   * discards the older requests.
   */
  public void performNextRequest() {
    if (requests.isEmpty()) {
      return;
    }

    T finishedRequest = requests.removeFirst();

    while (requests.size() > capacity) {
      T request = requests.removeFirst();
    }

    if (!requests.isEmpty()) {
      T request = requests.getFirst();
      request.perform();
    }
  }

  public int getWaitingRequestSize() {
    return max(0, requests.size() - 1);
  }

  public void clear() {
    while (!requests.isEmpty()) {
      T request = requests.removeFirst();
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
