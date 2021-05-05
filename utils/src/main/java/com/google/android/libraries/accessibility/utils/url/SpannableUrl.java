package com.google.android.libraries.accessibility.utils.url;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.text.style.URLSpan;
import android.view.View;
import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;

/**
 * Represents a URL from a {@link android.text.SpannableString} with a URL path and its associated
 * text from the {@link android.text.SpannableString}.
 */
@AutoValue
public abstract class SpannableUrl {

  public static SpannableUrl create(String string, URLSpan urlSpan) {
    return new AutoValue_SpannableUrl(urlSpan, string);
  }

  /**
   * A {@link URLSpan} associated with this SpannableUrl. Access to the original URLSpan is needed,
   * as subclasses may use {@link URLSpan#onClick(View)} instead of {@link URLSpan#getURL()}.
   */
  public abstract URLSpan urlSpan();

  /**
   * The text from the {@link android.text.SpannableString} associated with this SpannableUrl. For
   * example, if a spannable string "Click here" has a {@link android.text.style.URLSpan} on the
   * text "here," the text value would be "here." It is possible for this to be the same value as
   * the URL path.
   */
  public abstract String text();

  /** A URL path from a {@link android.text.style.URLSpan}. */
  public String path() {
    return urlSpan().getURL();
  }

  /** Returns {@code true} if the URL text and path are the same. */
  public boolean isTextAndPathEquivalent() {
    return text().equals(path());
  }

  // URLSpan.onClick is fine with a null parameter when called from an AccessibilityService.
  @SuppressWarnings("nullness:argument.type.incompatible")
  public void onClick(Context context) {
    if (Strings.isNullOrEmpty(path())) {
      // If the path is null or empty, use the onClick listener.
      urlSpan().onClick(null);
    } else {
      try {
        UrlUtils.openUrlWithIntent(context, path());
      } catch (ActivityNotFoundException e) {
        // Sometimes a malformed link can cause an ActivityNotFound exception when a link is
        // clicked. In this case, it is better to fallback to using the onClick listener. The
        // onClick listener may result in nothing happening, but this is preferable to crashing
        // and turning off the service.
        urlSpan().onClick(null);
      }
    }
  }
}
