package com.google.android.libraries.accessibility.utils.url;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Utilities to help with URLs */
public final class UrlUtils {

  private UrlUtils() {}

  /**
   * Opens the provided URL in a new task.
   *
   * @param context The context from which to start the activity
   * @param url The URL to open
   */
  public static void openUrlWithIntent(Context context, CharSequence url) {
    Intent intent = new Intent(Intent.ACTION_VIEW);
    Uri destination = Uri.parse(url.toString());
    intent.setData(destination);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
  }
}
