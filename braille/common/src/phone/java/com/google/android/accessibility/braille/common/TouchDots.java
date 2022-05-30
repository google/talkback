package com.google.android.accessibility.braille.common;

import android.content.res.Resources;
import androidx.annotation.StringRes;

/** Dot pattern layout possibilities. */
public enum TouchDots {
  AUTO_DETECT(R.string.auto_detect, R.string.auto_detect),
  SCREEN_AWAY(R.string.screen_away, R.string.screen_away_detail),
  TABLETOP(R.string.tabletop, R.string.tabletop_detail);

  final int layoutNameStringId;
  final int layoutDescriptionStringId;

  TouchDots(@StringRes int layoutNameStringId, @StringRes int layoutDescriptionStringId) {
    this.layoutNameStringId = layoutNameStringId;
    this.layoutDescriptionStringId = layoutDescriptionStringId;
  }

  public String getLayoutName(Resources resources) {
    return resources.getString(layoutNameStringId);
  }

  public String getLayoutDescription(Resources resources) {
    return resources.getString(layoutDescriptionStringId);
  }
}
