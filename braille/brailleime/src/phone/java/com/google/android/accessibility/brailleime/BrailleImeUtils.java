package com.google.android.accessibility.brailleime;

import static com.google.android.accessibility.brailleime.BrailleImeActions.Category.SPELL_CHECK;
import static com.google.android.accessibility.brailleime.settings.BrailleImeGestureActivity.CATEGORY;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.Nullable;
import com.google.android.accessibility.brailleime.settings.BrailleImeGestureCommandActivity;

/** Utils of impl version Braille keyboard. */
public class BrailleImeUtils {

  /**
   * Gets the intent for starting spell check gesture command activity. Returns null if not
   * supported.
   */
  @Nullable
  public static Intent getStartSpellCheckGestureCommandActivityIntent(Context context) {
    Intent intent = new Intent(context, BrailleImeGestureCommandActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.putExtra(CATEGORY, SPELL_CHECK);
    return intent;
  }

  private BrailleImeUtils() {}
}
