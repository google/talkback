package com.android.talkback.controller;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Message;
import android.support.annotation.IntDef;
import android.support.v4.os.BuildCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableStringBuilder;
import android.view.KeyEvent;
import android.widget.AdapterView;

import com.android.talkback.InputModeManager;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.ClassLoadingCache;
import com.android.utils.NodeFilter;
import com.android.utils.PerformActionUtils;
import com.android.utils.Role;
import com.android.utils.StringBuilderUtils;
import com.android.utils.WeakReferenceHandler;
import com.android.utils.WindowManager;

import com.google.android.marvin.talkback.TalkBackService;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Implements directional-pad navigation specific to Android TV devices.
 */
public class TelevisionNavigationController implements TalkBackService.KeyEventListener {

    public static final int MIN_API_LEVEL = Build.VERSION_CODES.M;

    private static final NodeFilter FILTER_FOCUSED = new NodeFilter() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
            return node.isFocused();
        }
    };

    /** Filter for nodes to pass through D-pad up/down on Android M or earlier. */
    private static final NodeFilter IGNORE_UP_DOWN_M = new NodeFilter() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
            // BUG; the ScrollAdapterView intercepts D-pad events on M; pass through
            // the up and down events so that the user can scroll these lists.
            // "com.android.tv.settings" - Android TV Settings app and SetupWraith (setup wizard)
            // "com.google.android.gsf.notouch" - Google Account setup in SetupWraith
            return ("com.android.tv.settings".equals(node.getPackageName()) ||
                    "com.google.android.gsf.notouch".equals(node.getPackageName())) &&
                    ClassLoadingCache.checkInstanceOf(node.getClassName(), AdapterView.class);
        }
    };

    @IntDef({MODE_NAVIGATE, MODE_SEEK_CONTROL})
    @Retention(RetentionPolicy.SOURCE)
    private @interface RemoteMode {}

    // The four arrow buttons move the focus and the select button performs a click.
    private static final int MODE_NAVIGATE = 0;
    // The four arrow buttons move the seek control and the select button exits seek control mode.
    private static final int MODE_SEEK_CONTROL = 1;

    private final TalkBackService mService;
    private final CursorController mCursorController;
    private boolean mPressedCenter = false;
    private @RemoteMode int mMode = MODE_NAVIGATE;
    private TelevisionKeyHandler mHandler = new TelevisionKeyHandler(this);

    public TelevisionNavigationController(TalkBackService service) {
        mService = service;
        mCursorController = mService.getCursorController();
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        mService.getInputModeManager().setInputMode(InputModeManager.INPUT_MODE_TV_REMOTE);

        WindowManager windowManager = new WindowManager(mService.isScreenLayoutRTL());
        windowManager.setWindows(mService.getWindows());

        // Let the system handle keyboards. The keys are input-focusable so this works fine.
        if (windowManager.isInputWindowOnScreen()) {
            return false;
        }

        // Note: we getCursorOrInputCursor because we want to avoid getting the root if there
        // is no cursor; getCursor is defined as getting the root if there is no a11y focus.
        AccessibilityNodeInfoCompat cursor = mCursorController.getCursorOrInputCursor();

        try {
            if (shouldIgnore(cursor, event)) {
                return false;
            }

            // TalkBack should always consume up/down/left/right on the d-pad. Otherwise, strange
            // things will happen when TalkBack cannot navigate further.
            // For example, TalkBack cannot control the gray-highlighted item in a ListView; the
            // view itself controls the highlighted item. So if the key event gets propagated to the
            // list view at the end of the list, the scrolling will jump to the highlighted item.
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (event.getKeyCode()) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        // Directional navigation takes a non-trivial amount of time, so we should
                        // post to the handler and return true immediately.
                        mHandler.postDirectionalKeyEvent(event, cursor);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                        // Can't post to handler because the return value might vary.
                        mPressedCenter = onCenterKey(cursor);
                        return mPressedCenter;
                }
            } else {
                // We need to cancel the corresponding up key action if we consumed the down action.
                switch (event.getKeyCode()) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        return true;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                        if (mPressedCenter) {
                            mPressedCenter = false;
                            return true;
                        }
                }
            }
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(cursor);
        }

        return false;
    }

    private void onDirectionalKey(int keyCode, AccessibilityNodeInfoCompat cursor) {
        switch (mMode) {
            case MODE_NAVIGATE: {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        mCursorController.left(
                                false /* wrap around */,
                                true /* auto scroll */,
                                true /* use input focus */,
                                InputModeManager.INPUT_MODE_KEYBOARD);
                        break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        mCursorController.right(
                                false /* wrap around */,
                                true /* auto scroll */,
                                true /* use input focus */,
                                InputModeManager.INPUT_MODE_KEYBOARD);
                        break;
                    case KeyEvent.KEYCODE_DPAD_UP:
                        mCursorController.up(
                                false /* wrap around */,
                                true /* auto scroll */,
                                true /* use input focus */,
                                InputModeManager.INPUT_MODE_KEYBOARD);
                        break;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        mCursorController.down(
                                false /* wrap around */,
                                true /* auto scroll */,
                                true /* use input focus */,
                                InputModeManager.INPUT_MODE_KEYBOARD);
                        break;
                }
            } break;
            case MODE_SEEK_CONTROL: {
                if (Role.getRole(cursor) != Role.ROLE_SEEK_CONTROL) {
                    setMode(MODE_NAVIGATE);
                } else {
                    boolean isRtl = mService.isScreenLayoutRTL();
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_UP:
                            PerformActionUtils.performAction(cursor,
                                    AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
                            break;
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                            PerformActionUtils.performAction(cursor, isRtl ?
                                    AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD :
                                    AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
                            break;
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                            PerformActionUtils.performAction(cursor,
                                    AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
                            break;
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                            PerformActionUtils.performAction(cursor, isRtl ?
                                    AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD :
                                    AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
                            break;
                    }
                }
            } break;
        }
    }

    /** Returns true if TalkBack should consume the center key; otherwise returns false. */
    private boolean onCenterKey(AccessibilityNodeInfoCompat cursor) {
        switch (mMode) {
            case MODE_NAVIGATE: {
                if (Role.getRole(cursor) == Role.ROLE_SEEK_CONTROL) {
                    // Seek control, center key toggles seek control input mode instead of clicking.
                    setMode(MODE_SEEK_CONTROL);
                    return true;
                } else if (mCursorController.clickCurrentHierarchical()) {
                    // We were able to find a clickable node in the hierarchy.
                    return true;
                } else if (cursor == null || AccessibilityNodeInfoUtils
                        .isOrHasMatchingAncestor(cursor, FILTER_FOCUSED)) {
                    // Note: this branch is mainly for compatibility reasons.
                    // The node with input focus is cursor or ancestor, so it's safe to pass the
                    // d-pad key event through.
                    // TODO: Re-evaluate whether this check is needed for N.
                    return false;
                } else {
                    // In all other cases, we must consume the key event instead of passing.
                    return true;
                }
            }
            case MODE_SEEK_CONTROL: {
                setMode(MODE_NAVIGATE);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean processWhenServiceSuspended() {
        return false;
    }

    /**
     * Between TalkBack 4.4 and 4.3, the fundamental interaction model for TalkBack on Android TV
     * changed. On 4.3, TalkBack relies on the system to move the input focus, which meant that
     * only input-focusable objects could be selected. On 4.4, TalkBack does its own navigation,
     * which means that all accessibility-focusable objects could be selected, but some objects
     * that did not respond to the appropriate accessibility events started breaking.
     *
     * To mitigate this, we will (very conservatively) pass through the D-pad key events for
     * certain views, effectively restoring the TB 4.3 behavior for these views.
     */
    private boolean shouldIgnore(AccessibilityNodeInfoCompat node, KeyEvent event) {
        if (!BuildCompat.isAtLeastN()) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                return AccessibilityNodeInfoUtils.isOrHasMatchingAncestor(node, IGNORE_UP_DOWN_M);
            }
        }

        return false;
    }

    public static boolean isContextTelevision(Context context) {
        if (context == null) {
            return false;
        }

        UiModeManager modeManager =
                (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        return modeManager != null &&
                modeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private void setMode(@RemoteMode int newMode) {
        if (newMode == mMode) {
            return;
        }

        int template;
        boolean hint;
        @RemoteMode int modeForFeedback;
        if (newMode == MODE_NAVIGATE) {
            // "XYZ mode ended".
            template = R.string.template_tv_remote_mode_ended;
            hint = false;
            modeForFeedback = mMode; // Speak the old mode name on exit.
        } else {
            // "XYZ mode started".
            template = R.string.template_tv_remote_mode_started;
            hint = true;
            modeForFeedback = newMode; // Speak the new mode name on enter.
        }

        SpannableStringBuilder builder = new SpannableStringBuilder();
        switch (modeForFeedback) {
            case MODE_SEEK_CONTROL:
                StringBuilderUtils.appendWithSeparator(builder, mService.getString(template,
                        mService.getString(R.string.value_tv_remote_mode_seek_control)));
                if (hint) {
                    StringBuilderUtils.appendWithSeparator(builder,
                            mService.getString(R.string.value_hint_tv_remote_mode_seek_control));
                }
                break;
        }

        // Really critical that the user understands what mode the remote control is in.
        mService.getSpeechController().speak(builder, SpeechController.QUEUE_MODE_INTERRUPT, 0,
                null);

        mMode = newMode;
    }

    /** Silently resets the remote mode to navigate mode. */
    public void resetToNavigateMode() {
        mMode = MODE_NAVIGATE;
    }

    private static class TelevisionKeyHandler
            extends WeakReferenceHandler<TelevisionNavigationController> {
        private static final int WHAT_DIRECTIONAL = 1;

        public TelevisionKeyHandler(TelevisionNavigationController parent) {
            super(parent);
        }

        @Override
        protected void handleMessage(Message msg, TelevisionNavigationController parent) {
            int keyCode = msg.arg1;
            AccessibilityNodeInfoCompat cursor = (AccessibilityNodeInfoCompat) msg.obj;

            switch (msg.what) {
                case WHAT_DIRECTIONAL:
                    parent.onDirectionalKey(keyCode, cursor);
                    break;
            }

            AccessibilityNodeInfoUtils.recycleNodes(cursor);
        }

        public void postDirectionalKeyEvent(KeyEvent event, AccessibilityNodeInfoCompat cursor) {
            AccessibilityNodeInfoCompat obtainedCursor;
            if (cursor == null) {
                obtainedCursor = null;
            } else {
                obtainedCursor = AccessibilityNodeInfoCompat.obtain(cursor);
            }
            Message msg = obtainMessage(WHAT_DIRECTIONAL, event.getKeyCode(), 0, obtainedCursor);
            sendMessageDelayed(msg, 0);
        }
    }
}
