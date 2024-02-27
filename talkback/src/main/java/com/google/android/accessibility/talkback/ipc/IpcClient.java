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

package com.google.android.accessibility.talkback.ipc;

import static com.google.android.accessibility.talkback.ipc.IpcService.MSG_ON_CLIENT_CONNECTED;
import static com.google.android.accessibility.talkback.ipc.IpcService.MSG_ON_CLIENT_DISCONNECTED;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Connects to {@link IpcService} to perform IPC with TalkBack. Follow these steps to connection or
 * disconnection between IpcClient and IpcService,
 *
 * <p>1. The client binds to IpcService by calling {@link IpcClient#bindService()}.
 *
 * <p>2. When the client has connected to the IpcService, {@link
 * IpcClient#onServiceConnected(ComponentName, IBinder)} is invoked. Then, the client can start to
 * communicate with the service.
 *
 * <p>3. To disconnect with IpcService by calling {@link IpcClient##unbindService()}.
 *
 * <p>4. {@link IpcClient#onServiceDisconnected(ComponentName)} is not called when the client
 * unbinds. The method is called only when the IpcService has crashed or been killed.
 */
public abstract class IpcClient implements ServiceConnection {

  private static final String TAG = "IpcClient";

  private final Context context;
  // To handle messages which receive from the service.
  private final Messenger clientMessenger;
  // To send messages to IpcService.
  private @Nullable Messenger serviceMessenger;

  public IpcClient(Context context) {
    this.context = context;
    clientMessenger =
        new Messenger(
            new Handler(Looper.myLooper()) {
              @Override
              public void handleMessage(@NonNull Message msg) {
                handleMessageFromService(msg);
              }
            });
  }

  public Context getContext() {
    return context;
  }

  /** Establishes a connection with {@link IpcService}. */
  public void bindService() {
    if (serviceMessenger != null) {
      return;
    }

    context.bindService(new Intent(context, IpcService.class), this, Context.BIND_AUTO_CREATE);
  }

  /** Finishes the connection with {@link IpcService}. */
  public void unbindService() {
    if (serviceMessenger == null) {
      return;
    }

    context.unbindService(this);
    // The method of unbindService won't necessarily trigger onServiceDisconnected.
    sendMessage(Message.obtain(/* h= */ null, MSG_ON_CLIENT_DISCONNECTED));
    serviceMessenger = null;
  }

  /** Called when the connection with service has been established. */
  @Override
  public void onServiceConnected(ComponentName name, IBinder binder) {
    serviceMessenger = new Messenger(binder);
    sendMessage(Message.obtain(/* h= */ null, MSG_ON_CLIENT_CONNECTED));
  }

  /** Called when the service has crashed or been killed. */
  @Override
  public final void onServiceDisconnected(ComponentName name) {
    sendMessage(Message.obtain(/* h= */ null, MSG_ON_CLIENT_DISCONNECTED));
    serviceMessenger = null;
  }

  /** Sends data to {@link IpcService} or asks {@link IpcService} for the data. */
  public void sendMessage(Message msg) {
    if (serviceMessenger == null) {
      LogUtils.e(TAG, "Service is unavailable.");
      return;
    }

    try {
      // IpcService can reply this message by the clientMessenger.
      msg.replyTo = clientMessenger;
      serviceMessenger.send(msg);
    } catch (RemoteException e) {
      LogUtils.e(TAG, "Fail to send message to IpcService. %s", e.getMessage());
    }
  }

  /** Handles messages which receive from {@link IpcService}. */
  public abstract void handleMessageFromService(@NonNull Message msg);
}
