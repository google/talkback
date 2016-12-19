/*
 * Copyright (C) 2015 Google Inc.
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

package com.googlecode.eyesfree.testing;

import android.Manifest.permission;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener;
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.utils.LogUtils;
import com.android.utils.TestActivity;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseAccessibilityInstrumentationTestCase
        extends ActivityInstrumentationTestCase2<TestActivity> {
    private static final String TOUCH_EXPLORATION_GRANTED_ACCESSIBILITY_SERVICES =
            "touch_exploration_granted_accessibility_services";

    // Used to obtain nodes from views.
    private static final int NODE_INFO_EVENT_TYPE = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;

    // Used to synchronize with the TalkBack event queue.
    private static final int SYNC_EVENT_TYPE = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
    private static final Bundle SYNC_PARCELABLE = new Bundle();
    private static final String SYNC_KEY = "key";
    private static final double SYNC_VALUE = (Math.random() + 1.0);

    static {
        SYNC_PARCELABLE.putDouble(SYNC_KEY, SYNC_VALUE);
    }

    /** Maximum time to wait while attempting to obtain a service instance. */
    private static final long OBTAIN_SERVICE_TIMEOUT = 2000;

    /** Delay between retries while attempting to obtain a service instance. */
    private static final long OBTAIN_SERVICE_RETRY = 100;

    /** Maximum time to wait while changing the accessibility state. */
    private static final long STATE_CHANGE_TIMEOUT = 3000;

    /** Maximum time to wait for a specific event. */
    private static final long OBTAIN_EVENT_TIMEOUT = 2000;

    /** Maximum time to wait for the service to stop receiving events. */
    private static final long NO_EVENTS_TIMEOUT = 2000;

    /** Minimum time to wait for the service to stop receiving events. */
    private static final long NO_EVENTS_DURATION = 500;

    /** Fake view ID used to temporarily identify views. */
    private static final int FAKE_VIEW_ID = Integer.MAX_VALUE;

    /** List of recorded events. */
    private final ArrayList<AccessibilityEvent> mEventCache = new ArrayList<>();

    private final Object mAccessibilityStateLock = new Object();
    private final Object mAccessibilityEventLock = new Object();

    private AccessibilityManager mManager;

    private boolean mAccessibilityState;
    private boolean mRecordingEvents;

    private long mLastEventTime;

    protected Context mInsCtx;
    protected Context mAppCtx;

    public BaseAccessibilityInstrumentationTestCase() {
        super(TestActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        LogUtils.setLogLevel(Log.VERBOSE);

        mInsCtx = getInstrumentation().getContext();
        mAppCtx = getInstrumentation().getTargetContext();
        mManager = (AccessibilityManager) mAppCtx.getSystemService(Context.ACCESSIBILITY_SERVICE);

        assertEquals("Has WRITE_SECURE_SETTINGS permission (did you run \"adb shell pm grant "
                        + mInsCtx.getPackageName() + " android.permission.WRITE_SECURE_SETTINGS\"?)",
                PackageManager.PERMISSION_GRANTED,
                mInsCtx.getPackageManager().checkPermission(
                        permission.WRITE_SECURE_SETTINGS, mInsCtx.getPackageName()));

        AccessibilityService service = getService();

        // Ensure the TalkBack and system accessibility states are in sync.
        if ((service == null) || !mManager.isEnabled()) {
            disableAllServices();
            obtainNullTargetServiceSync();
            enableTargetService();
            obtainTargetServiceSync();

            service = getService();
        }

        assertNotNull("Connected to service", service);

        connectServiceListener();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        disconnectServiceListener();
        disableAllServices();
    }

    protected abstract AccessibilityService getService();
    protected abstract void enableTargetService();
    protected abstract void connectServiceListener();
    protected abstract void disconnectServiceListener();

    /**
     * Finds an {@link android.view.accessibility.AccessibilityNodeInfo} by View id in the active window.
     * The search is performed from the root node.
     *
     * @param viewId The id of a View.
     * @return An {@link android.view.accessibility.AccessibilityNodeInfo} if found, null otherwise.
     */
    protected final AccessibilityNodeInfo findAccessibilityNodeInfoByViewIdInActiveWindow(
            int viewId) {
        startRecordingEvents();

        final View view = getViewForId(viewId);
        assertNotNull("Obtain view from activity", view);

        final AccessibilityEvent event = AccessibilityEvent.obtain();
        event.setEnabled(false);
        event.setEventType(NODE_INFO_EVENT_TYPE);
        event.setParcelableData(SYNC_PARCELABLE);
        event.setSource(view);

        // Sending the event through the manager sets the event time and
        // may clear the source node. Only certain event types can be
        // dispatched (see the framework's AccessibilityManagerService
        // canDispatchAccessibilityEvent() method).
        mManager.sendAccessibilityEvent(event);

        final AccessibilityEvent syncedEvent = stopRecordingEventsAfter(mNodeInfoEventFilter);
        assertNotNull("Synchronized event queue", syncedEvent);

        final AccessibilityNodeInfo sourceNode = syncedEvent.getSource();
        assertNotNull("Obtained source node from event", sourceNode);

        return sourceNode;
    }

    protected void assertServiceIsInstalled(String servicePackage, String serviceName) {
        final List<AccessibilityServiceInfo> services = mManager
                .getInstalledAccessibilityServiceList();

        for (AccessibilityServiceInfo service : services) {
            final ServiceInfo serviceInfo = service.getResolveInfo().serviceInfo;
            final String packageName = serviceInfo.applicationInfo.packageName;

            if (servicePackage.equals(packageName) && serviceName.equals(serviceInfo.name)) {
                return;
            }
        }

        assertTrue("Service " + servicePackage + "/" + serviceName + " is not installed", false);
    }

    /**
     * Calls {@link android.app.Activity#setContentView} with the specified
     * layout resource and waits for a layout pass.
     * <p>
     * An initial layout pass is required for
     * {@link android.view.accessibility.AccessibilityNodeInfo#isVisibleToUser} to return the correct
     * value.
     *
     * @param layoutResID Resource ID to be passed to
     *            {@link android.app.Activity#setContentView}.
     */
    protected void setContentView(final int layoutResID) {
        final Activity activity = getActivity();

        try {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.setContentView(layoutResID);
                }
            });

            waitForAccessibilityIdleSync();
            waitForEventQueueSync();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * Disables all accessibility services by clearing all accessibility-related
     * settings (e.g. enabled services, accessibility enabled, etc.).
     */
    protected void disableAllServices() {
        final ContentResolver cr = mInsCtx.getContentResolver();

        Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "");

        // Granting touch exploration is only supported on JB and above.
        Settings.Secure.putString(cr, TOUCH_EXPLORATION_GRANTED_ACCESSIBILITY_SERVICES, "");

        Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0);

        Settings.Secure.putInt(cr, Settings.Secure.TOUCH_EXPLORATION_ENABLED, 0);

        mAccessibilityState = assertAccessibilityStateSync(false);
    }

    /**
     * Enables the specified accessibility service and turns on accessibility.
     * <p> Setting {@code usesExploreByTouch} grants
     * the service permission to turn on Explore by Touch. To enable the
     * feature, you must request it in your service configuration.
     *
     * @param packageName The package containing the service to enable.
     * @param className The class name of the service to enable.
     * @param usesExploreByTouch Whether the service uses Explore by Touch.
     */
    protected void enableService(String packageName, String className, boolean usesExploreByTouch) {
        final String fullPackage = packageName + "/" + className;
        final ContentResolver cr = mInsCtx.getContentResolver();

        Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, fullPackage);

        String enabledService = Settings.Secure.getString(cr,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        assertEquals(fullPackage, enabledService);

        // Granting touch exploration is only supported on JB and above.
        if (usesExploreByTouch) {
            Settings.Secure.putString(
                    cr, TOUCH_EXPLORATION_GRANTED_ACCESSIBILITY_SERVICES, fullPackage);
        }

        Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);

        int accessibilityEnabled = Settings.Secure.getInt(cr,
                Settings.Secure.ACCESSIBILITY_ENABLED, 0);
        assertEquals(1, accessibilityEnabled);

        mAccessibilityState = assertAccessibilityStateSync(true);
    }

    private boolean assertAccessibilityStateSync(boolean enabled) {
        final boolean state;

        final long startTime = SystemClock.uptimeMillis();

        synchronized (mAccessibilityStateLock) {
            mManager.addAccessibilityStateChangeListener(mStateListener);

            try {
                while (true) {
                    if (mManager.isEnabled() == enabled) {
                        break;
                    }

                    final long elapsed = (SystemClock.uptimeMillis() - startTime);
                    final long timeLeft = (STATE_CHANGE_TIMEOUT - elapsed);
                    if (timeLeft <= 0) {
                        break;
                    }

                    mAccessibilityStateLock.wait(timeLeft);
                }
            } catch (InterruptedException e) {
                // Do nothing.
            }

            state = mManager.isEnabled();

            assertEquals("Toggled accessibility state", enabled, state);

            mManager.removeAccessibilityStateChangeListener(mStateListener);
        }

        LogUtils.log(this, Log.VERBOSE, "Took %d ms to enable accessibility",
                (SystemClock.uptimeMillis() - startTime));

        return state;
    }

    protected void waitForEventQueueSync() throws Throwable {
        startRecordingEvents();

        final AccessibilityEvent event = AccessibilityEvent.obtain();
        event.setEnabled(false);
        event.setEventType(SYNC_EVENT_TYPE);
        event.setParcelableData(SYNC_PARCELABLE);

        // Sending the event through the manager sets the event time and
        // may clear the source node. Only certain event types can be
        // dispatched (see the framework's AccessibilityManagerService
        // canDispatchAccessibilityEvent() method).
        mManager.sendAccessibilityEvent(event);

        final AccessibilityEvent syncedEvent = stopRecordingEventsAfter(mSyncEventFilter);

        assertNotNull("Synchronized event queue", syncedEvent);
    }

    /**
     * Ensures that {@link #NO_EVENTS_DURATION} milliseconds have passed since
     * the last accessibility event.
     */
    protected void waitForAccessibilityIdleSync() {
        boolean hasIdleSync = false;

        final long startTime = SystemClock.uptimeMillis();
        synchronized (mAccessibilityEventLock) {
            try {
                // Reset the event time to now so that we catch queued events.
                mLastEventTime = SystemClock.uptimeMillis();

                while (true) {
                    final long eventTimeElapsed = (SystemClock.uptimeMillis() - mLastEventTime);
                    final long eventTimeLeft = (NO_EVENTS_DURATION - eventTimeElapsed);
                    if (eventTimeLeft <= 0) {
                        hasIdleSync = true;
                        break;
                    }

                    final long timeElapsed = (SystemClock.uptimeMillis() - startTime);
                    final long timeLeft = (NO_EVENTS_TIMEOUT - timeElapsed);
                    if (timeLeft <= 0) {
                        break;
                    }

                    final long timeToWait = Math.min(timeLeft, eventTimeLeft);
                    mAccessibilityEventLock.wait(timeToWait);
                }
            } catch (InterruptedException e) {
                // Do nothing.
            }

            assertTrue("Accessibility events idle for " + NO_EVENTS_DURATION + " ms", hasIdleSync);
        }

        LogUtils.log(this, Log.VERBOSE, "Took %d ms to sync accessibility idle state",
                (SystemClock.uptimeMillis() - startTime));
    }

    /**
     * Returns the {@link android.view.View} for the specified id.
     *
     * @param viewId The id of the view to obtain.
     * @return The view, or {@code null}.
     */
    protected View getViewForId(int viewId) {
        if (viewId <= 0) {
            return null;
        }

        final View view = getActivity().findViewById(viewId);
        assertNotNull("Obtain view with id " + viewId, view);
        return view;
    }

    /**
     * Returns the {@link android.support.v4.view.accessibility.AccessibilityNodeInfoCompat} for a specific view, or
     * {@code null} if the view is invalid or an error occurred while obtaining
     * the info.
     *
     * @param viewId The id of the view whose node to obtain.
     * @return The view's node info, or {@code null}.
     */
    protected AccessibilityNodeInfoCompat getNodeForId(int viewId) {
        if (viewId <= 0) {
            return null;
        }

        final AccessibilityNodeInfo node = findAccessibilityNodeInfoByViewIdInActiveWindow(viewId);
        assertNotNull("Obtain node from view id " + viewId, node);
        return new AccessibilityNodeInfoCompat(node);
    }

    /**
     * Returns the {@link android.support.v4.view.accessibility.AccessibilityNodeInfoCompat} for a specific view, or
     * {@code null} if the view is invalid or an error occurred while obtaining
     * the info.
     *
     * @param view The view whose node to obtain.
     * @return The view's node info, or {@code null}.
     */
    protected AccessibilityNodeInfoCompat getNodeForView(View view) {
        if (view == null) {
            return null;
        }

        final int realViewId = view.getId();
        view.setId(FAKE_VIEW_ID);
        final AccessibilityNodeInfoCompat node = getNodeForId(FAKE_VIEW_ID);
        view.setId(realViewId);

        return node;
    }

    protected void startRecordingEvents() {
        synchronized (mEventCache) {
            mEventCache.clear();
            mRecordingEvents = true;
        }
    }

    protected AccessibilityEvent stopRecordingEventsAfter(EventFilter filter) {
        final long startTime = SystemClock.uptimeMillis();

        synchronized (mEventCache) {
            try {
                int currentIndex = 0;

                while (true) {
                    // Check all events starting from the current index.
                    while (currentIndex < mEventCache.size()) {
                        final AccessibilityEvent event = mEventCache.get(currentIndex);

                        if (filter.accept(event)) {
                            mRecordingEvents = false;
                            return event;
                        }

                        currentIndex++;
                    }

                    final long elapsed = (SystemClock.uptimeMillis() - startTime);
                    final long timeLeft = (OBTAIN_EVENT_TIMEOUT - elapsed);
                    if (timeLeft <= 0) {
                        break;
                    }

                    mEventCache.wait(timeLeft);
                }

                mRecordingEvents = false;
            } catch (InterruptedException e) {
                // Do nothing.
            }
        }

        return null;
    }

    /**
     * Attempts to obtain a null instance of {@link TestAccessibilityService}.
     * <p>
     * May block for up to {@link #OBTAIN_SERVICE_TIMEOUT} seconds, and may
     * return {@code null} if the service is not running.
     */
    private boolean obtainNullTargetServiceSync() {
        boolean success = false;

        final long startTime = SystemClock.uptimeMillis();
        try {
            while (true) {
                final AccessibilityService service = getService();
                if (service == null) {
                    break;
                }

                final long timeElapsed = (SystemClock.uptimeMillis() - startTime);
                final long timeLeft = (OBTAIN_SERVICE_TIMEOUT - timeElapsed);
                if (timeLeft <= 0) {
                    break;
                }

                final long timeToWait = Math.min(OBTAIN_SERVICE_RETRY, timeLeft);
                Thread.sleep(timeToWait);
            }
        } catch (InterruptedException e) {
            // Do nothing.
        }

        LogUtils.log(this, Log.VERBOSE, "Took %d ms to obtain null service",
                (SystemClock.uptimeMillis() - startTime));

        return success;
    }

    /**
     * Attempts to obtain an instance of {@link TestAccessibilityService}.
     * <p>
     * May block for up to {@link #OBTAIN_SERVICE_TIMEOUT} seconds, and may
     * return {@code null} if the service is not running.
     */
    private boolean obtainTargetServiceSync() {
        boolean success = false;

        final long startTime = SystemClock.uptimeMillis();
        try {
            while (true) {
                final AccessibilityService service = getService();
                if (service != null) {
                    break;
                }

                final long timeElapsed = (SystemClock.uptimeMillis() - startTime);
                final long timeLeft = (OBTAIN_SERVICE_TIMEOUT - timeElapsed);
                if (timeLeft <= 0) {
                    break;
                }

                final long timeToWait = Math.min(OBTAIN_SERVICE_RETRY, timeLeft);
                Thread.sleep(timeToWait);
            }
        } catch (InterruptedException e) {
            // Do nothing.
        }

        LogUtils.log(this, Log.VERBOSE, "Took %d ms to obtain service",
                (SystemClock.uptimeMillis() - startTime));

        return success;
    }

    protected void onEventReceived(AccessibilityEvent event) {
        synchronized (mAccessibilityEventLock) {
            mLastEventTime = SystemClock.uptimeMillis();
        }

        synchronized (mEventCache) {
            if (mRecordingEvents) {
                mEventCache.add(AccessibilityEvent.obtain(event));
                mEventCache.notifyAll();
            }
        }
    }

    /** Event filter used to synchronize with the TalkBack event queue. */
    private final EventFilter mNodeInfoEventFilter = new EventFilter() {
        @Override
        public boolean accept(AccessibilityEvent event) {
            if (event.getEventType() != NODE_INFO_EVENT_TYPE) {
                return false;
            }

            final Parcelable parcel = event.getParcelableData();
            return parcel instanceof Bundle
                    && (((Bundle) parcel).getDouble(SYNC_KEY) == SYNC_VALUE);
        }
    };

    /** Event filter used to synchronize with the TalkBack event queue. */
    private final EventFilter mSyncEventFilter = new EventFilter() {
        @Override
        public boolean accept(AccessibilityEvent event) {
            if (event.getEventType() != SYNC_EVENT_TYPE) {
                return false;
            }

            final Parcelable parcel = event.getParcelableData();
            return parcel instanceof Bundle
                    && (((Bundle) parcel).getDouble(SYNC_KEY) == SYNC_VALUE);
        }
    };

    private final AccessibilityStateChangeListener
            mStateListener = new AccessibilityStateChangeListener() {
        @Override
        public void onAccessibilityStateChanged(boolean enabled) {
            synchronized (mAccessibilityStateLock) {
                mAccessibilityState = enabled;
                mAccessibilityStateLock.notifyAll();
            }
        }
    };
}
