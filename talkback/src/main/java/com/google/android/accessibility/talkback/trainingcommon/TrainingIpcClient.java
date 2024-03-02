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

package com.google.android.accessibility.talkback.trainingcommon;

import static com.google.android.accessibility.talkback.ipc.IpcService.EXTRA_IS_ANY_GESTURE_CHANGED;
import static com.google.android.accessibility.talkback.ipc.IpcService.EXTRA_IS_ICON_DETECTION_UNAVAILABLE;
import static com.google.android.accessibility.talkback.ipc.IpcService.EXTRA_IS_IMAGE_DESCRIPTION_UNAVAILABLE;
import static com.google.android.accessibility.talkback.ipc.IpcService.MSG_REQUEST_AVAILABLE_FEATURES;
import static com.google.android.accessibility.talkback.ipc.IpcService.MSG_REQUEST_GESTURES;
import static com.google.android.accessibility.talkback.ipc.IpcService.MSG_SERVER_DESTROYED;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.text.TextUtils;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.talkback.ipc.IpcClient;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.HashMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Performs IPC between TalkBack and TrainingActivity. */
public class TrainingIpcClient extends IpcClient {

  private static final String TAG = "TrainingIpcClient";
  private final Runnable connectionStateListener;
  private final ServiceData serviceData;
  private final IpcServerStateListener ipcServerStateListener;

  /** A listener invoked to notify the ipc server state has been changed. */
  public interface IpcServerStateListener {
    /** Invoked when the IpcServer is destroyed. */
    void onIpcServerDestroyed();
  }

  public TrainingIpcClient(
      Context context,
      IpcServerStateListener ipcServerStateListener,
      boolean showExitBanner,
      Runnable connectionStateListener) {
    super(context);
    this.ipcServerStateListener = ipcServerStateListener;
    this.connectionStateListener = connectionStateListener;
    this.serviceData = new ServiceData(context, showExitBanner);
  }

  @VisibleForTesting
  public TrainingIpcClient(
      Context context,
      IpcServerStateListener ipcServerStateListener,
      Runnable connectionStateListener) {
    super(context);
    this.ipcServerStateListener = ipcServerStateListener;
    this.connectionStateListener = connectionStateListener;
    this.serviceData = new ServiceData(context, false);
  }

  public ServiceData getServiceData() {
    return serviceData;
  }

  @Override
  public void handleMessageFromService(@NonNull Message msg) {
    LogUtils.v(TAG, "handleMessageFromService(): %s", msg.what);

    switch (msg.what) {
      case MSG_REQUEST_GESTURES:
        serviceData.actionKeyToGestureText.clear();
        Bundle data = msg.getData();
        data.keySet()
            .forEach(
                key -> {
                  // The data is used to display you-customized-gestures message.
                  if (TextUtils.equals(key, EXTRA_IS_ANY_GESTURE_CHANGED)) {
                    serviceData.isAnyGestureChanged =
                        data.getBoolean(EXTRA_IS_ANY_GESTURE_CHANGED, /* defaultValue= */ true);
                    return;
                  }
                  @Nullable String gesture = data.getString(key);
                  if (gesture == null) {
                    return;
                  }
                  serviceData.actionKeyToGestureText.put(key, gesture);
                });
        break;
      case MSG_SERVER_DESTROYED:
        if (ipcServerStateListener != null) {
          ipcServerStateListener.onIpcServerDestroyed();
        }
        break;
      case MSG_REQUEST_AVAILABLE_FEATURES:
        {
          Bundle bundle = msg.getData();
          serviceData.isIconDetectionUnavailable =
              bundle.getBoolean(EXTRA_IS_ICON_DETECTION_UNAVAILABLE);
          serviceData.isImageDescriptionUnavailable =
              bundle.getBoolean(EXTRA_IS_IMAGE_DESCRIPTION_UNAVAILABLE);
        }
        break;
      default: // fall out
    }
  }

  @Override
  public void onServiceConnected(ComponentName name, IBinder binder) {
    super.onServiceConnected(name, binder);
    connectionStateListener.run();
  }

  /** The data getting from TalkBack. */
  public static class ServiceData {
    private final Context context;
    private boolean isAnyGestureChanged = true;
    private final HashMap<String, String> actionKeyToGestureText = new HashMap<>();
    private final boolean showExitBanner;
    private boolean isIconDetectionUnavailable = false;
    private boolean isImageDescriptionUnavailable = false;

    public ServiceData(Context context, boolean showExitBanner) {
      this.context = context;
      this.showExitBanner = showExitBanner;
    }

    @VisibleForTesting
    public ServiceData(Context context) {
      this.context = context;
      showExitBanner = false;
    }

    public Context getContext() {
      return context;
    }

    /** Returns a gesture text for the given action. */
    public @Nullable String getGestureFromActionKey(int actionKey) {
      return actionKeyToGestureText.get(context.getString(actionKey));
    }

    /**
     * Checks whether any gesture has been changed. The data is used to display
     * you-customized-gestures message in the New Gesture page.
     */
    public boolean isAnyGestureChanged() {
      return isAnyGestureChanged;
    }

    /** Returns whether TalkBack training should show exit banner. */
    public boolean shouldShowTrainingExitBanner() {
      return showExitBanner;
    }

    /** Checks whether the icon detection is unavailable. */
    public boolean isIconDetectionUnavailable() {
      return isIconDetectionUnavailable;
    }

    /** Checks whether the image description is unavailable. */
    public boolean isImageDescriptionUnavailable() {
      return isImageDescriptionUnavailable;
    }
  }
}
