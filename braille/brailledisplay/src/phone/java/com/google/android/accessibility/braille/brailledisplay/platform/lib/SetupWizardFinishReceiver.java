package com.google.android.accessibility.braille.brailledisplay.platform.lib;

import static com.google.android.accessibility.utils.SettingsUtils.USER_SETUP_COMPLETE;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import com.google.android.accessibility.utils.SettingsUtils;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** Listens for SetupWizard status changes. */
public class SetupWizardFinishReceiver implements Receiver<SetupWizardFinishReceiver> {
  /** The callback associated with the actions of this receiver. */
  public interface Callback {
    void onFinished();
  }

  private final Context context;
  private final ContentObserver observer;

  public SetupWizardFinishReceiver(Context context, Callback callback) {
    this.context = context;
    observer =
        new ContentObserver(new Handler()) {
          @Override
          public void onChange(boolean selfChange) {
            if (SettingsUtils.allowLinksOutOfSettings(context)) {
              callback.onFinished();
            }
          }
        };
  }

  @CanIgnoreReturnValue
  @Override
  public SetupWizardFinishReceiver registerSelf() {
    context
        .getContentResolver()
        .registerContentObserver(
            Settings.Secure.getUriFor(USER_SETUP_COMPLETE),
            /* notifyForDescendants= */ false,
            observer);
    return this;
  }

  @Override
  public void unregisterSelf() {
    context.getContentResolver().unregisterContentObserver(observer);
  }
}
