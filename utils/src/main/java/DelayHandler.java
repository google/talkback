/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.utils;

import android.os.Handler;
import android.os.Message;

/**
 * A base class to conveniently delay calling a method.
 *
 * <p>Example:
 *
 * <pre><code>
 * private Delay<MyArgClass> mDelayMyFunc = new DelayHandler<MyArgClass>() {
 *     {@literal @}Override
 *     handle(MyArgClass argInstance) {
 *       // Do something
 *     }
 *   };
 * mDelayMyFunc.delay(delayMs, argInstance);
 * </code></pre>
 */
public abstract class DelayHandler<T> extends Handler {

  private static final int MESSAGE_ID = 1;

  /** Make a delayed call to handle(handlerArg). handlerArg may be null. */
  public void delay(long delayMs, T handlerArg) {
    Message message = obtainMessage(MESSAGE_ID, handlerArg);
    sendMessageDelayed(message, delayMs);
  }

  public void removeMessages() {
    removeMessages(MESSAGE_ID);
  }

  @Override
  public void handleMessage(Message message) {
    if (message.what == MESSAGE_ID) {
      @SuppressWarnings("unchecked") // message.obj type T enforced by delay(long, T)
      T messageObj = (T) message.obj;
      handle(messageObj);
    }
  }

  /** Method that will be called after delay. */
  public abstract void handle(T arg);
}
