package com.google.android.accessibility.utils.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.os.Message;
import com.google.android.accessibility.utils.WeakReferenceHandler;

/**
 * Similar to {@link BroadcastReceiver} but executes the action on the thread that the instance is
 * created on (instead of the main thread). This is to help with classes that are not thread-safe.
 *
 * <p>Child classes should implement {@link #onReceiveIntent} instead of {@link #onReceive}.
 */
public abstract class SameThreadBroadcastReceiver extends BroadcastReceiver {
  private final BroadcastReceiverHandler handler = new BroadcastReceiverHandler(this);
  private static final String INTENT_BUNDLE_KEY = "intent";

  @Override
  public final void onReceive(Context context, Intent intent) {
    Bundle bundle = new Bundle();
    bundle.putParcelable(INTENT_BUNDLE_KEY, intent);
    Message msg = new Message();
    msg.setData(bundle);
    handler.sendMessage(msg);
  }

  protected abstract void onReceiveIntent(Intent intent);

  private static final class BroadcastReceiverHandler
      extends WeakReferenceHandler<SameThreadBroadcastReceiver> {

    public BroadcastReceiverHandler(SameThreadBroadcastReceiver parent) {
      super(parent, Looper.myLooper());
    }

    @Override
    protected void handleMessage(Message msg, SameThreadBroadcastReceiver parent) {
      parent.onReceiveIntent(msg.getData().getParcelable(INTENT_BUNDLE_KEY));
    }
  }
}
