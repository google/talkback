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
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import androidx.annotation.MainThread;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId;
import com.google.android.accessibility.utils.caption.ImageCaptionUtils.CaptionType;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
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
  public static final int MSG_TRAINING_FINISH = 2;
  public static final int MSG_ON_CLIENT_CONNECTED = 3;
  public static final int MSG_ON_CLIENT_DISCONNECTED = 4;
  public static final int MSG_SERVER_DESTROYED = 5;
  public static final int MSG_REQUEST_DISABLE_TALKBACK = 6;
  public static final int MSG_REQUEST_AVAILABLE_FEATURES = 7;
  public static final String EXTRA_IS_ICON_DETECTION_UNAVAILABLE = "is_icon_detection_unavailable";
  public static final String EXTRA_IS_IMAGE_DESCRIPTION_UNAVAILABLE =
      "is_image_description_unavailable";
  public static final int MSG_DOWNLOAD_ICON_DETECTION = 8;
  public static final int MSG_DOWNLOAD_IMAGE_DESCRIPTION = 9;

  /**
   * Receives the data from {@link IpcClient} or sends the response to {@link IpcClient}.
   *
   * <p>If the client expects to get the response, the client has to pass a messenger by {@link
   * Message#replyTo} to this service.
   */
  private final Messenger messenger =
      new Messenger(
          new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
              LogUtils.v(TAG, "handleMessage(): %s", msg.what);

              switch (msg.what) {
                case MSG_REQUEST_GESTURES:
                  {
                    Messenger clientMessenger = msg.replyTo;
                    if (clientMessenger == null || sClientCallback == null) {
                      LogUtils.e(TAG, "No client messenger or clientCallback.");
                      return;
                    }
                    Bundle data =
                        sClientCallback.onRequestGesture(IpcService.this.getApplicationContext());
                    Message message = Message.obtain(/* h= */ null, MSG_REQUEST_GESTURES);
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
                    @Nullable PageId pageId =
                        (PageId) msg.getData().getSerializable(EXTRA_TRAINING_PAGE_ID);
                    if (pageId == null) {
                      return;
                    }
                    if (sClientCallback == null) {
                      LogUtils.w(TAG, "clientCallback is null.");
                      return;
                    }
                    sClientCallback.onPageSwitched(pageId);
                    break;
                  }
                case MSG_TRAINING_FINISH:
                  if (sClientCallback == null) {
                    LogUtils.w(TAG, "clientCallback is null.");
                    return;
                  }
                  sClientCallback.onTrainingFinish();
                  break;
                case MSG_ON_CLIENT_CONNECTED:
                  if (sClientCallback == null) {
                    LogUtils.w(
                        TAG,
                        "clientCallback is null and we tell the client that the server is"
                            + " destroyed.");
                    sendServerDestroyMsg(msg.replyTo);
                    return;
                  }
                  sClientCallback.onClientConnected(new ServerOnDestroyListenerImpl(msg.replyTo));
                  break;
                case MSG_ON_CLIENT_DISCONNECTED:
                  if (sClientCallback == null) {
                    LogUtils.w(TAG, "clientCallback is null.");
                    return;
                  }
                  sClientCallback.onClientDisconnected();
                  break;
                case MSG_REQUEST_DISABLE_TALKBACK:
                  if (sClientCallback == null) {
                    LogUtils.w(TAG, "clientCallback is null.");
                    return;
                  }
                  sClientCallback.onRequestDisableTalkBack();
                  break;
                case MSG_REQUEST_AVAILABLE_FEATURES:
                  {
                    Messenger clientMessenger = msg.replyTo;
                    if (clientMessenger == null || sClientCallback == null) {
                      LogUtils.e(TAG, "No client messenger or clientCallback.");
                      return;
                    }
                    Message message = Message.obtain(/* h= */ null, MSG_REQUEST_AVAILABLE_FEATURES);
                    message.setData(
                        sClientCallback.onRequestDynamicFeatureState(
                            IpcService.this.getApplicationContext()));
                    try {
                      clientMessenger.send(message);
                    } catch (RemoteException e) {
                      LogUtils.w(TAG, "Fail to send gestures to client.");
                    }
                  }
                  break;
                case MSG_DOWNLOAD_ICON_DETECTION:
                  {
                    if (sClientCallback == null) {
                      LogUtils.e(TAG, "No client clientCallback.");
                      return;
                    }
                    sClientCallback.onRequestDownloadLibrary(CaptionType.ICON_LABEL);
                  }

                  break;
                case MSG_DOWNLOAD_IMAGE_DESCRIPTION:
                  {
                    if (sClientCallback == null) {
                      LogUtils.e(TAG, "No client clientCallback.");
                      return;
                    }
                    sClientCallback.onRequestDownloadLibrary(CaptionType.IMAGE_DESCRIPTION);
                  }
                  break;
                default:
              }
            }
          });

  @Override
  public @Nullable IBinder onBind(Intent intent) {
    // Provides a messenger for IpcClient to send the data back.
    return messenger.getBinder();
  }

  /** The listener to be notified when the server is destroyed. */
  public interface ServerOnDestroyListener {
    /** Invoked when the client is ready. */
    void onServerDestroy();
  }

  /** Keeps the client messenger and notifies it when the server is destroyed. */
  private static class ServerOnDestroyListenerImpl implements ServerOnDestroyListener {

    final Messenger clientMessenger;

    public ServerOnDestroyListenerImpl(Messenger clientMessenger) {
      this.clientMessenger = clientMessenger;
    }

    @Override
    public void onServerDestroy() {
      sendServerDestroyMsg(clientMessenger);
    }
  }

  /** The callback to handle the request or the status change from {@link IpcClient}. */
  public interface IpcClientCallback {

    /**
     * Invoked when client side is asking for the gesture information.
     *
     * @return a bundle that contains action-gesture mapping including all actions
     */
    Bundle onRequestGesture(Context context);

    /** Invoked to notify the current {@code pageId} . */
    void onPageSwitched(PageId pageId);

    /** Invoked to notify training is exited by user. */
    void onTrainingFinish();

    /** Invoked when the client is connected. */
    void onClientConnected(ServerOnDestroyListener serverOnDestroyListener);

    /** Invoked when the client is disconnected. */
    void onClientDisconnected();

    /** Invoked to request disabling TalkBack when user click TalkBack-exit shortcut. */
    void onRequestDisableTalkBack();

    /**
     * Invoked to check if dynamic features have been downloaded.
     *
     * @return the state of dynamic features. The key of icon detection is {@link
     *     #EXTRA_IS_ICON_DETECTION_UNAVAILABLE}; the key of image description is {@link
     *     #EXTRA_IS_IMAGE_DESCRIPTION_UNAVAILABLE}.
     */
    Bundle onRequestDynamicFeatureState(Context context);

    /** Invoked to request downloading library. */
    void onRequestDownloadLibrary(CaptionType captionType);
  }

  @Nullable private static IpcClientCallback sClientCallback;

  @MainThread
  public static void setClientCallback(IpcClientCallback clientCallback) {
    sClientCallback = clientCallback;
  }

  private static void sendServerDestroyMsg(Messenger clientMessenger) {
    if (clientMessenger != null) {
      Message message = Message.obtain(/* h= */ null, MSG_SERVER_DESTROYED);
      try {
        clientMessenger.send(message);
      } catch (RemoteException e) {
        LogUtils.w(TAG, "Fail to send server destroy state to client.");
      }
    }
  }
}
