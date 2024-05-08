/*
 * Copyright (C) 2013 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay;

import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import com.google.android.accessibility.braille.brailledisplay.BrailleView.OnBrailleCellClickListener;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.utils.MotionEventUtils;
import com.google.android.accessibility.utils.WeakReferenceHandler;

/**
 * A display which may present an on-screen overlay which mirrors the content of the Braille
 * display.
 *
 * <p>Note: this display can connect very quickly. To avoid missing any connection state change
 * events, callers should set any necessary listeners before allowing control to return to the
 * {@link Looper} on the current thread.
 */
public class OverlayDisplay implements OnBrailleCellClickListener {
  private final MainThreadHandler mainThreadHandler;
  private final DisplayThreadHandler displayThreadHandler;
  private final Context context;
  private final Callback overlayCallback;
  private int numOfTextCell;

  /** Callbacks when receiving input event. */
  public interface Callback {
    void onInputEvent(BrailleInputEvent inputEvent);
  }

  public OverlayDisplay(Context context, Callback overlayCallback) {
    this.context = context;
    this.overlayCallback = overlayCallback;
    mainThreadHandler = new MainThreadHandler(context, this);
    displayThreadHandler = new DisplayThreadHandler(this);
  }

  public void displayDots(byte[] patterns, CharSequence text, int[] brailleToTextPositions) {
    mainThreadHandler.displayDots(numOfTextCell, patterns, text, brailleToTextPositions);
  }

  /**
   * Starts to show overlay if it's enabled by user. The length of the overlay is {@param
   * numOfTextCell}
   */
  public void start(int numOfTextCell) {
    this.numOfTextCell = numOfTextCell;
    SharedPreferences prefs =
        BrailleUserPreferences.getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME);
    prefs.registerOnSharedPreferenceChangeListener(sharedPreferencesListener);
    displayThreadHandler.reportPreferenceChange();
  }

  /** Hides overlay. */
  public boolean shutdown() {
    SharedPreferences prefs =
        BrailleUserPreferences.getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME);
    prefs.unregisterOnSharedPreferenceChangeListener(sharedPreferencesListener);
    mainThreadHandler.hideOverlay();
    return true;
  }

  @Override
  public void onBrailleCellClick(BrailleView view, int cellIndex) {
    displayThreadHandler.sendInputEvent(
        new BrailleInputEvent(BrailleInputEvent.CMD_ROUTE, cellIndex, SystemClock.uptimeMillis()));
  }

  private void sendInputEvent(BrailleInputEvent inputEvent) {
    overlayCallback.onInputEvent(inputEvent);
    mainThreadHandler.reportInputEvent(inputEvent);
  }

  private void updateFromSharedPreferences() {
    boolean overlayEnabled = BrailleUserPreferences.readOnScreenOverlayEnabled(context);
    if (overlayEnabled) {
      mainThreadHandler.showOverlay();
    } else {
      mainThreadHandler.hideOverlay();
    }
  }

  /**
   * Main handler for the overlay display. All UI operations must occur through this handler. To
   * enforce this, the overlay is intentionally private.
   */
  private static class MainThreadHandler extends WeakReferenceHandler<OverlayDisplay> {
    private static final int MSG_SHOW = 1;
    private static final int MSG_HIDE = 2;
    private static final int MSG_DISPLAY_DOTS = 3;
    private static final int MSG_INPUT_EVENT = 4;

    private Context context;
    private BrailleOverlay overlay;

    // Used for passing screen content between threads.
    // Access should be synchronized.
    private int numOfTextCell;
    private byte[] braille;
    private CharSequence text;
    private int[] brailleToTextPositions;

    public MainThreadHandler(Context context, OverlayDisplay parent) {
      super(parent, Looper.getMainLooper());
      this.context = context;
    }

    private void showOverlay() {
      sendEmptyMessage(MSG_SHOW);
    }

    private void hideOverlay() {
      sendEmptyMessage(MSG_HIDE);
    }

    private void displayDots(
        int numOfTextCell, byte[] patterns, CharSequence text, int[] brailleToTextPositions) {
      synchronized (this) {
        this.numOfTextCell = numOfTextCell;
        braille = patterns;
        this.text = text;
        this.brailleToTextPositions = brailleToTextPositions;
      }
      sendEmptyMessage(MSG_DISPLAY_DOTS);
    }

    private void reportInputEvent(BrailleInputEvent event) {
      obtainMessage(MSG_INPUT_EVENT, event).sendToTarget();
    }

    @Override
    public void handleMessage(Message msg, OverlayDisplay parent) {
      switch (msg.what) {
        case MSG_SHOW:
          handleShow(parent);
          break;
        case MSG_HIDE:
          handleHide();
          break;
        case MSG_DISPLAY_DOTS:
          handleDisplayDots();
          break;
        case MSG_INPUT_EVENT:
          handleInputEvent((BrailleInputEvent) msg.obj);
          break;
      }
    }

    private void handleShow(OverlayDisplay parent) {
      if (overlay == null) {
        overlay = new BrailleOverlay(context, parent);
      }
      overlay.show();
    }

    private void handleHide() {
      if (overlay != null) {
        overlay.hide();
        overlay = null;
      }
    }

    private void handleDisplayDots() {
      if (overlay == null) {
        return;
      }

      int numOfTextCell;
      byte[] braille;
      CharSequence text;
      int[] brailleToTextPositions;
      synchronized (this) {
        numOfTextCell = this.numOfTextCell;
        braille = this.braille;
        text = this.text;
        brailleToTextPositions = this.brailleToTextPositions;
      }

      BrailleView view = overlay.getBrailleView();
      view.setTextCellNum(numOfTextCell);
      view.displayDots(braille, text, brailleToTextPositions);
    }

    private void handleInputEvent(BrailleInputEvent event) {
      if (overlay == null) {
        return;
      }

      if (BrailleInputEvent.argumentType(event.getCommand())
          == BrailleInputEvent.ARGUMENT_POSITION) {
        BrailleView view = overlay.getBrailleView();
        view.highlightCell(event.getArgument());
      }
    }
  }

  /**
   * Handler which runs on the display thread. This is necessary to handle events from the main
   * thread.
   */
  private static class DisplayThreadHandler extends WeakReferenceHandler<OverlayDisplay> {
    private static final int MSG_PREFERENCE_CHANGE = 1;
    private static final int MSG_SEND_INPUT_EVENT = 2;

    public DisplayThreadHandler(OverlayDisplay parent) {
      super(parent);
    }

    private void reportPreferenceChange() {
      obtainMessage(MSG_PREFERENCE_CHANGE).sendToTarget();
    }

    private void sendInputEvent(BrailleInputEvent event) {
      obtainMessage(MSG_SEND_INPUT_EVENT, event).sendToTarget();
    }

    @Override
    protected void handleMessage(Message msg, OverlayDisplay parent) {
      switch (msg.what) {
        case MSG_PREFERENCE_CHANGE:
          parent.updateFromSharedPreferences();
          break;
        case MSG_SEND_INPUT_EVENT:
          parent.sendInputEvent((BrailleInputEvent) msg.obj);
          break;
      }
    }
  }

  private final SharedPreferences.OnSharedPreferenceChangeListener sharedPreferencesListener =
      new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
          if (context.getString(com.google.android.accessibility.braille.common.R.string.pref_braille_overlay_key).equals(key)) {
            displayThreadHandler.reportPreferenceChange();
          }
        }
      };

  private static class BrailleOverlay extends DraggableOverlay {
    private final BrailleView brailleView;

    private final View.OnHoverListener hoverForwarder =
        new View.OnHoverListener() {
          @Override
          public boolean onHover(View view, MotionEvent event) {
            MotionEvent touchEvent = MotionEventUtils.convertHoverToTouch(event);
            return view.dispatchTouchEvent(touchEvent);
          }
        };

    public BrailleOverlay(Context context, final OverlayDisplay parent) {
      super(context);

      setContentView(R.layout.overlay);
      brailleView = (BrailleView) findViewById(R.id.braille_view);
      brailleView.setOnHoverListener(hoverForwarder);
      brailleView.setOnBrailleCellClickListener(parent);

      ImageButton panUpButton = (ImageButton) findViewById(R.id.pan_left_button);
      panUpButton.setOnHoverListener(hoverForwarder);
      panUpButton.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              parent.displayThreadHandler.sendInputEvent(
                  new BrailleInputEvent(
                      BrailleInputEvent.CMD_NAV_PAN_UP,
                      0 /* argument */,
                      SystemClock.uptimeMillis()));
            }
          });

      ImageButton panDownButton = (ImageButton) findViewById(R.id.pan_right_button);
      panDownButton.setOnHoverListener(hoverForwarder);
      panDownButton.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              parent.displayThreadHandler.sendInputEvent(
                  new BrailleInputEvent(
                      BrailleInputEvent.CMD_NAV_PAN_DOWN,
                      0 /* argument */,
                      SystemClock.uptimeMillis()));
            }
          });
    }

    private BrailleView getBrailleView() {
      return brailleView;
    }

    @Override
    protected void onStartDragging() {
      brailleView.cancelPendingTouches();
    }
  }
}
