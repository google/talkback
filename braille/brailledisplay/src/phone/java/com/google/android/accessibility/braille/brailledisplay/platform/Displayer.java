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
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.platform.BrailleDisplayManager.RemoteDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.lib.Utils;
import com.google.android.accessibility.braille.brltty.BrailleDisplayProperties;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;
import com.google.android.accessibility.braille.brltty.Encoder;
import java.util.Optional;

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
  private boolean isDisplayReady;
  private BrailleDisplayProperties displayProperties;
  private final RemoteDevice remoteDevice;

  private static final int COMMAND_CODE_MASK = 0xffff;
  private static final int COMMAND_ARGUMENT_MASK = 0x7fff0000;
  private static final int COMMAND_ARGUMENT_SHIFT = 16;

  public Displayer(
      Context context,
      Callback callback,
      Encoder.Factory encoderFactory,
      RemoteDevice remoteDevice) {
    this.callback = callback;
    this.remoteDevice = remoteDevice;
    this.encoder =
        encoderFactory.createEncoder(
            context, new EncoderCallback(), remoteDevice.deviceName, remoteDevice.address);
  }

  public String getDeviceName() {
    return remoteDevice.deviceName;
  }

  public String getDeviceAddress() {
    return remoteDevice.address;
  }

  public BrailleDisplayProperties getDeviceProperties() {
    return displayProperties;
  }

  /**
   * Returns true if the encoder has finished the start-up procedure and the display is ready to
   * receive render packets.
   */
  public boolean isDisplayReady() {
    return isDisplayReady;
  }

  /**
   * Starts this instance.
   *
   * <p>This will get processed on a background thread.
   */
  public void start() {
    mainHandler = new Handler(Looper.getMainLooper(), new MainHandlerCallback());

    if (bgThread != null) {
      throw new IllegalStateException("start was already invoked");
    }
    bgThread = new HandlerThread("DisplayerBG");
    bgThread.start();
    bgHandler = new Handler(bgThread.getLooper(), new BackgroundHandlerCallback());
    // It is permitted to send a message to the bg queue even if onLooperPrepared has not been run.
    bgHandler.obtainMessage(MessageBg.START.what()).sendToTarget();
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
    bgHandler.obtainMessage(MessageBg.STOP.what()).sendToTarget();
    bgThread.quitSafely();
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
    bgHandler.obtainMessage(MessageBg.WRITE_BRAILLE_DOTS.what(), brailleDotBytes).sendToTarget();
  }

  /**
   * Asks for the currently queued read command, if it exists.
   *
   * <p>This will get processed on a background thread.
   *
   * <p>The result of the read will be sent to the callback via {@link Callback#onBrailleInputEvent}
   */
  public void readCommand() {
    bgHandler.obtainMessage(MessageBg.READ_COMMAND.what()).sendToTarget();
  }

  // Handlers

  private Handler mainHandler;

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
        displayer.isDisplayReady = true;
        displayer.displayProperties = (BrailleDisplayProperties) message.obj;
        displayer.callback.onDisplayReady(displayer.displayProperties);
      }
    },
    READ_COMMAND_ARRIVED {
      @Override
      void handle(Displayer displayer, Message message) {
        displayer.callback.onBrailleInputEvent((BrailleInputEvent) message.obj);
      }
    },
    ;

    public int what() {
      return ordinal();
    }

    abstract void handle(Displayer displayer, Message message);
  }

  private Handler bgHandler;

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
        Optional<BrailleDisplayProperties> brailleDisplayProperties = displayer.encoder.start();
        if (brailleDisplayProperties.isPresent()) {
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
        displayer.encoder.stop();
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
        while (true) {
          int commandComplex = displayer.encoder.readCommand();
          if (commandComplex < 0) {
            break;
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
      }
    },
    ;

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
      bgHandler.sendMessageDelayed(bgHandler.obtainMessage(MessageBg.READ_COMMAND.what()), delayMs);
    }
  }
}
