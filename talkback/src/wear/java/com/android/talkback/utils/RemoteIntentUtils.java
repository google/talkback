/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.google.android.accessibility.talkback.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.wear.remote.interactions.RemoteActivityHelper;
import androidx.wear.widget.ConfirmationOverlay;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.SettingsUtils;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Executors;

/** The utility class to open URL on the remote device and show result on the local device. */
public class RemoteIntentUtils {

  /** An interface invoked when the animation is finished. */
  public interface OnRemoteIntentAnimationFinishedListener {
    void onAnimationFinished(boolean success);
  }

  /**
   * Assigns an URL intent to the preference. When clicking the preference, it would jump to URL.
   *
   * @param fragment PreferenceFragmentCompat to get context.
   * @param preference Preference to send Intent
   * @param url URL which launches web page
   */
  public static void assignWebIntentToPreference(
      PreferenceFragmentCompat fragment, Preference preference, String url) {
    assignWebIntentToPreference(fragment, preference, url, null);
  }

  /**
   * Assigns an URL intent to the preference. When clicking the preference, it would jump to URL on
   * the remote device, show confirmation overlay in the local device and return a callback with
   * status.
   *
   * @param fragment PreferenceFragmentCompat to get context.
   * @param preference Preference to send Intent
   * @param url URL which launches web page
   * @param listener the callback invoked when the animation is finished
   */
  public static void assignWebIntentToPreference(
      PreferenceFragmentCompat fragment,
      Preference preference,
      String url,
      @Nullable OnRemoteIntentAnimationFinishedListener listener) {
    if (!SettingsUtils.allowLinksOutOfSettings(fragment.getContext())) {
      return;
    }

    Uri uri = Uri.parse(url);
    Activity activity = fragment.getActivity();
    if (activity != null) {
      preference.setOnPreferenceClickListener(
          pref -> {
            RemoteIntentUtils.startRemoteActivityToOpenUriOnPhone(
                uri, pref.getContext(), activity, listener);
            return true;
          });
    }
  }

  /**
   * Convenience function to start an activity for the given {@code uri} on another device and
   * animates a success or failure message depending on the result.
   *
   * @param uri the address to be opened
   * @param context the context for sending the intent
   * @param activity the activity to display the confirmation over
   * @param listener the callback invoked when the animation is finished
   */
  public static void startRemoteActivityToOpenUriOnPhone(
      Uri uri,
      Context context,
      Activity activity,
      @Nullable OnRemoteIntentAnimationFinishedListener listener) {

    RemoteIntentActionCallback callback = new RemoteIntentActionCallback(context);
    callback.setActivity(activity);
    callback.setOnAnimationFinishedListener(listener);

    internalStartRemoteActivityToOpenUriOnPhone(uri, context, callback);
  }

  /**
   * Convenience function to start an activity for the given {@code uri} on another device and
   * animates a success or failure message depending on the result.
   *
   * @param uri the address to be opened
   * @param context the context for sending the intent
   * @param rootView the view to display the confirmation over
   * @param listener the callback invoked when the animation is finished
   */
  public static void startRemoteActivityToOpenUriOnPhone(
      Uri uri,
      Context context,
      View rootView,
      @Nullable OnRemoteIntentAnimationFinishedListener listener) {

    RemoteIntentActionCallback callback = new RemoteIntentActionCallback(context);
    callback.setRootView(rootView);
    callback.setOnAnimationFinishedListener(listener);

    internalStartRemoteActivityToOpenUriOnPhone(uri, context, callback);
  }

  private static void internalStartRemoteActivityToOpenUriOnPhone(
      Uri uri, Context context, RemoteIntentActionCallback callback) {
    RemoteActivityHelper remoteActivityHelper =
        new RemoteActivityHelper(context, Executors.newSingleThreadExecutor());

    ListenableFuture<Void> result =
        remoteActivityHelper.startRemoteActivity(
            new Intent(Intent.ACTION_VIEW).setData(uri).addCategory(Intent.CATEGORY_BROWSABLE),
            null);

    Futures.addCallback(result, callback, context.getMainExecutor());
  }

  /** A {@link FutureCallback} that shows a {@link ConfirmationOverlay} to indicate the result. */
  private static final class RemoteIntentActionCallback implements FutureCallback<Void> {

    private final Context context;
    private Activity activity;
    private View rootView;
    private OnRemoteIntentAnimationFinishedListener onAnimationFinishedListener;

    public RemoteIntentActionCallback(Context context) {
      this.context = context;
    }

    public void setActivity(Activity activity) {
      this.activity = activity;
    }

    public void setRootView(View rootView) {
      this.rootView = rootView;
    }

    public void setOnAnimationFinishedListener(
        @Nullable OnRemoteIntentAnimationFinishedListener listener) {
      onAnimationFinishedListener = listener;
    }

    @Override
    public void onSuccess(Void result) {
      showConfirmationOverlay(RemoteActivityHelper.RESULT_OK);
    }

    @Override
    public void onFailure(Throwable t) {
      showConfirmationOverlay(RemoteActivityHelper.RESULT_FAILED);
    }

    private void showConfirmationOverlay(int resultCode) {
      ConfirmationOverlay confirmationOverlay = new ConfirmationOverlay();
      if (resultCode == RemoteActivityHelper.RESULT_OK) {
        confirmationOverlay
            .setType(ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION)
            .setMessage(
                (CharSequence)
                    context.getString(R.string.watch_remote_intent_success_confirmation_text));
      } else {
        confirmationOverlay
            .setType(ConfirmationOverlay.FAILURE_ANIMATION)
            .setMessage(
                (CharSequence)
                    context.getString(R.string.watch_remote_intent_error_confirmation_text));
      }

      if (onAnimationFinishedListener != null) {
        confirmationOverlay.setOnAnimationFinishedListener(
            () ->
                onAnimationFinishedListener.onAnimationFinished(
                    resultCode == RemoteActivityHelper.RESULT_OK));
      }

      if (activity != null) {
        confirmationOverlay.showOn(activity);
      } else if (rootView != null) {
        confirmationOverlay.showAbove(rootView);
      }
    }
  }

  private RemoteIntentUtils() {}
}
