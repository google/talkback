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

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.gesture.GestureController;
import com.google.android.accessibility.talkback.gesture.GestureShortcutMapping;
import com.google.android.accessibility.talkback.training.PageConfig.PageId;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.HashMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** TalkBack communicates with activities which are in separate processes. */
public class IpcService extends Service {
  private static final String TAG = "IpcService";

  // For TrainingActivity.
  public static final int MSG_TRAINING_PAGE_SWITCHED = 0;
  public static final String EXTRA_TRAINING_PAGE_ID = "training_page_id";
  public static final int MSG_REQUEST_GESTURES = 1;
  public static final String EXTRA_IS_ANY_GESTURE_CHANGED = "is_any_gesture_changed";

  /**
   * Receives the data from {@link IpcClient} or sends the response to {@link IpcClient}.
   *
   * <p>If the client expects to get the response, the client has to pass a messenger by {@link
   * Message#replyTo} to this service.
   */
  private final Messenger messenger =
      new Messenger(
          new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
              LogUtils.v(TAG, "handleMessage(): %s", msg.what);

              switch (msg.what) {
                case MSG_REQUEST_GESTURES:
                  {
                    Messenger clientMessenger = msg.replyTo;
                    if (clientMessenger == null) {
                      LogUtils.e(TAG, "No client messenger.");
                      return;
                    }

                    GestureShortcutMapping mapping =
                        new GestureShortcutMapping(getApplicationContext());
                    HashMap<String, String> actionKeyToGestureText = mapping.getAllGestureTexts();

                    Message message = Message.obtain(/* h= */ null, MSG_REQUEST_GESTURES);
                    Bundle data = new Bundle();
                    actionKeyToGestureText.forEach(data::putString);
                    data.putBoolean(
                        EXTRA_IS_ANY_GESTURE_CHANGED,
                        GestureController.isAnyGestureChanged(getApplicationContext()));
                    message.setData(data);

                    try {
                      clientMessenger.send(message);
                    } catch (RemoteException e) {
                      LogUtils.w(TAG, "Fail to send gestures to client.");
                    }
                    break;
                  }
                case MSG_TRAINING_PAGE_SWITCHED:
                  {
                    @Nullable
                    PageId pageId = (PageId) msg.getData().getSerializable(EXTRA_TRAINING_PAGE_ID);
                    if (pageId == null) {
                      return;
                    }

                    TalkBackService.handleTrainingPageSwitched(IpcService.this, pageId);
                    break;
                  }
                default:
              }
            }
          });

  @Override
  public @Nullable IBinder onBind(Intent intent) {
    // Provides a messenger for IpcClient to send the data back.
    return messenger.getBinder();
  }
}
