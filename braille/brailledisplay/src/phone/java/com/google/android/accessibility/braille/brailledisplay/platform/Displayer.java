/*
 * Copyright 2021 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.braille.brailledisplay.platform;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableBluetoothDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableUsbDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.lib.Utils;
import com.google.android.accessibility.braille.brltty.BrailleDisplayProperties;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;
import com.google.android.accessibility.braille.brltty.Encoder;
import com.google.android.accessibility.braille.brltty.device.BrlttyParameterProviderFactory;
import com.google.android.accessibility.braille.brltty.device.ParameterProvider;
import com.google.android.accessibility.braille.common.DeviceProvider;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Encodes braille dots for rendering, via {@link Encoder} on the remote display and coordinates the
 * exchange of data between the {@link Controller} and the remote display.
 *
 * <p>This class uses a pair of handlers: a background handler which puts display encoding/decoding
 * operations onto a non-main-thread, and a main thread for bringing results and signals back to
 * main, so that Android components such as Services can make use of them.
 */
public class Displayer {
  private static final String TAG = "Displayer";
  private static final int COMMAND_CODE_MASK = 0xffff;
  private static final int COMMAND_ARGUMENT_MASK = 0x7fff0000;
  private static final int COMMAND_ARGUMENT_SHIFT = 16;

  /** Callback for {@link Displayer}. */
  public interface Callback {
    void onStartFailed();

    void onSendPacketToDisplay(byte[] packet);

    void onDisplayReady(BrailleDisplayProperties bdr);

    void onBrailleInputEvent(BrailleInputEvent brailleInputEvent);
  }

  private final Callback callback;
  private final Encoder encoder;
  private HandlerThread bgThread;
  private Handler mainHandler;
  private Handler bgHandler;
  private final AtomicBoolean isDisplayReady = new AtomicBoolean();
  private final BrlttyParameterProviderFactory parameterProviderFactory;
  private BrailleDisplayProperties displayProperties;
  private ConnectableDevice device;

  public Displayer(Context context, Callback callback, Encoder.Factory encoderFactory) {
    this.callback = callback;
    parameterProviderFactory = new BrlttyParameterProviderFactory();
    this.encoder = encoderFactory.createEncoder(context, new EncoderCallback());
    mainHandler = new Handler(Looper.getMainLooper(), new MainHandlerCallback());
  }

  /** Returns a device provider. */
  public DeviceProvider<?> getDeviceProvider() {
    if (device instanceof ConnectableBluetoothDevice) {
      return new DeviceProvider<>(((ConnectableBluetoothDevice) device).bluetoothDevice());
    } else if (device instanceof ConnectableUsbDevice) {
      return new DeviceProvider<>(((ConnectableUsbDevice) device).usbDevice());
    }
    throw new IllegalArgumentException();
  }

  public String getDeviceAddress() {
    return device.address();
  }

  public BrailleDisplayProperties getDeviceProperties() {
    return displayProperties;
  }

  /**
   * Returns true if the encoder has finished the start-up procedure and the display is ready to
   * receive render packets.
   */
  public boolean isDisplayReady() {
    return isDisplayReady.get();
  }

  /**
   * Starts this instance.
   *
   * <p>This will get processed on a background thread.
   */
  public void start(ConnectableDevice device) {
    this.device = device;
    if (!isReady()) {
      BrailleDisplayLog.v(TAG, "start a new thread");
      bgThread = new HandlerThread("DisplayerBG");
      bgThread.start();
      bgHandler = new Handler(bgThread.getLooper(), new BackgroundHandlerCallback());
    }
    // Only keep one start in case it's called repeatedly.
    bgHandler.removeMessages(MessageBg.START.what());
    // It is permitted to send a message to the bg queue even if onLooperPrepared has not been
    // run.
    ParameterProvider brlttyDevice =
        parameterProviderFactory.getParameterProvider(getDeviceProvider());
    bgHandler.obtainMessage(MessageBg.START.what(), brlttyDevice.getParameters()).sendToTarget();
  }

  /**
   * Delivers an encoded packet which just arrived from the remote device.
   *
   * <p>Do not invoke on the main thread, as this directly sends a message to the {@link Encoder}.
   */
  public void consumePacketFromDevice(byte[] packet) {
    Utils.assertNotMainThread();
    encoder.consumePacketFromDevice(packet);
  }

  /**
   * Stops this instance.
   *
   * <p>This will get processed on a background thread.
   */
  public void stop() {
    mainHandler.removeCallbacksAndMessages(null);
    if (bgHandler != null) {
      bgHandler.removeCallbacksAndMessages(null);
      bgHandler.obtainMessage(MessageBg.STOP.what()).sendToTarget();
    }
  }

  /**
   * Delivers an unencoded string of bytes for encoding, and eventual delivery to the remote device.
   *
   * <p>This will get processed on a background thread.
   *
   * <p>The bytes will be encoded as a new packet in the protocol expected by the remote device, and
   * will be forwarded to the remote device via {@link Callback#onSendPacketToDisplay(byte[])}.
   */
  public void writeBrailleDots(byte[] brailleDotBytes) {
    if (isReady()) {
      if (bgHandler.hasMessages(MessageBg.WRITE_BRAILLE_DOTS.what())) {
        bgHandler.removeMessages(MessageBg.WRITE_BRAILLE_DOTS.what());
      }
      bgHandler.obtainMessage(MessageBg.WRITE_BRAILLE_DOTS.what(), brailleDotBytes).sendToTarget();
    }
  }

  /**
   * Asks for the currently queued read command, if it exists.
   *
   * <p>This will get processed on a background thread.
   *
   * <p>The result of the read will be sent to the callback via {@link Callback#onBrailleInputEvent}
   */
  public void readCommand() {
    if (isReady() && !bgHandler.hasMessages(MessageBg.READ_COMMAND.what())) {
      bgHandler.obtainMessage(MessageBg.READ_COMMAND.what()).sendToTarget();
    }
  }

  private boolean isReady() {
    if (bgHandler != null && bgThread.isAlive()) {
      return true;
    }
    BrailleDisplayLog.v(TAG, "thread has not started or has died.");
    return false;
  }

  private class MainHandlerCallback implements Handler.Callback {
    @Override
    public boolean handleMessage(Message message) {
      MessageMain messageMain = MessageMain.values()[message.what];
      BrailleDisplayLog.v(TAG, "handleMessage main " + messageMain);
      messageMain.handle(Displayer.this, message);
      return true;
    }
  }

  private enum MessageMain {
    START_FAILED {
      @Override
      void handle(Displayer displayer, Message message) {
        displayer.callback.onStartFailed();
      }
    },
    DISPLAY_READY {
      @Override
      void handle(Displayer displayer, Message message) {
        displayer.displayProperties = (BrailleDisplayProperties) message.obj;
        displayer.callback.onDisplayReady(displayer.displayProperties);
      }
    },
    READ_COMMAND_ARRIVED {
      @Override
      void handle(Displayer displayer, Message message) {
        displayer.callback.onBrailleInputEvent((BrailleInputEvent) message.obj);
      }
    };

    public int what() {
      return ordinal();
    }

    abstract void handle(Displayer displayer, Message message);
  }

  private class BackgroundHandlerCallback implements Handler.Callback {
    @Override
    public boolean handleMessage(Message message) {
      MessageBg messageBg = MessageBg.values()[message.what];
      BrailleDisplayLog.v(TAG, "handleMessage bg " + messageBg);
      messageBg.handle(Displayer.this, message);
      return true;
    }
  }

  private enum MessageBg {
    START {
      @Override
      public void handle(Displayer displayer, Message message) {
        if (displayer.isDisplayReady.get()) {
          BrailleDisplayLog.d(TAG, "Braille display has started.");
          return;
        }
        Optional<BrailleDisplayProperties> brailleDisplayProperties =
            displayer.encoder.start(displayer.device.name(), (String) message.obj);
        if (brailleDisplayProperties.isPresent()) {
          displayer.isDisplayReady.getAndSet(true);
          displayer
              .mainHandler
              .obtainMessage(MessageMain.DISPLAY_READY.what(), brailleDisplayProperties.get())
              .sendToTarget();
        } else {
          displayer.mainHandler.obtainMessage(MessageMain.START_FAILED.what()).sendToTarget();
        }
      }
    },
    STOP {
      @Override
      public void handle(Displayer displayer, Message message) {
        if (!displayer.isDisplayReady.getAndSet(false)) {
          BrailleDisplayLog.d(TAG, "Braille display has stopped");
          return;
        }
        displayer.displayProperties = null;
        displayer.encoder.stop();
        if (!displayer.bgHandler.hasMessages(START.what())) {
          BrailleDisplayLog.v(TAG, "stop a thread");
          displayer.bgThread.quitSafely();
        }
      }
    },
    WRITE_BRAILLE_DOTS {
      @Override
      public void handle(Displayer displayer, Message message) {
        byte[] brailleDotBytes = (byte[]) message.obj;
        displayer.encoder.writeBrailleDots(brailleDotBytes);
      }
    },
    READ_COMMAND {
      @Override
      public void handle(Displayer displayer, Message message) {
        int commandComplex = displayer.encoder.readCommand();
        if (commandComplex < 0) {
          return;
        }
        int cmd = commandComplex & COMMAND_CODE_MASK;
        int arg = (commandComplex & COMMAND_ARGUMENT_MASK) >> COMMAND_ARGUMENT_SHIFT;
        long eventTime = SystemClock.uptimeMillis();
        BrailleInputEvent brailleInputEvent = new BrailleInputEvent(cmd, arg, eventTime);
        displayer
            .mainHandler
            .obtainMessage(MessageMain.READ_COMMAND_ARRIVED.what(), brailleInputEvent)
            .sendToTarget();
      }
    };

    public int what() {
      return ordinal();
    }

    abstract void handle(Displayer displayer, Message message);
  }

  private class EncoderCallback implements Encoder.Callback {
    @Override
    public void sendPacketToDevice(byte[] packet) {
      if (Utils.isMainThread()) {
        BrailleDisplayLog.v(TAG, "sendPacketToDevice invoked on main thread; ignoring");
      }
      callback.onSendPacketToDisplay(packet);
    }

    @Override
    public void readAfterDelay(int delayMs) {
      if (isReady()) {
        // Don't send READ_COMMAND with delay because in readCommand() hasMessage will be true and
        // filter out real-time reads.
        bgHandler.removeCallbacks(runnable);
        bgHandler.postDelayed(runnable, delayMs);
      }
    }

    private final Runnable runnable = () -> readCommand();
  }

  @VisibleForTesting
  Looper testing_getBackgroundLooper() {
    return bgThread.getLooper();
  }
}
