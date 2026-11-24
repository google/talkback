package com.google.android.accessibility.utils.input;

import static android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED;
import static android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED;
import static android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_TITLE;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_REMOVED;
import static android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION;
import static android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.robolectric.Shadows.shadowOf;

import android.accessibilityservice.AccessibilityService;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.utils.monitor.DisplayMonitor;
import java.time.Duration;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class WindowEventInterpreterTest {
  private static final int APP_ID = 1;
  private static final int OVERLAY_ID = 2;
  private AccessibilityService service;
  private WindowEventInterpreter interpreter;
  private WindowEventInterpreter.EventInterpretation lastInterpretation;

  @Before
  public void setUp() {
    service = Robolectric.setupService(TestService.class);
    interpreter = new WindowEventInterpreter(service, new DisplayMonitor(service));
    interpreter.addListener((interpretation, eventId) -> lastInterpretation = interpretation);
  }

  @Test
  public void removingSystemOverlay_keepsPaneTitleAndMainWindow() {
    AccessibilityWindowInfo app = window(APP_ID, TYPE_APPLICATION, "App");
    AccessibilityWindowInfo overlay = window(OVERLAY_ID, TYPE_SYSTEM, "Indicator");
    shadowOf(service).setWindows(List.of(app, overlay));
    paneEvent(CONTENT_CHANGE_TYPE_PANE_APPEARED, "Home");
    shadowOf(Looper.getMainLooper())
        .idleFor(Duration.ofMillis(WindowEventInterpreter.WINDOW_CHANGE_DELAY_MS));
    assertEquals("Home", interpreter.getWindowTitle(APP_ID));

    shadowOf(service).setWindows(List.of(app));
    AccessibilityEvent removed = new AccessibilityEvent(TYPE_WINDOWS_CHANGED);
    shadowOf(removed).setWindowId(OVERLAY_ID);
    ReflectionHelpers.callInstanceMethod(
        removed, "setWindowChanges", ClassParameter.from(int.class, WINDOWS_CHANGE_REMOVED));
    interpreter.interpret(removed, null);

    assertEquals(TYPE_WINDOWS_CHANGED, lastInterpretation.getEventType());
    assertFalse(lastInterpretation.getMainWindowsChanged());
    assertEquals("Home", interpreter.getAccessibilityPaneTitle(APP_ID));
    assertEquals("Home", interpreter.getWindowTitle(APP_ID));

    // The refresh also cached the window title for use when the live title is unavailable.
    shadowOf(app).setTitle(null);
    paneEvent(CONTENT_CHANGE_TYPE_PANE_DISAPPEARED, "Home");
    assertFalse(interpreter.hasAccessibilityPane(APP_ID));
    assertEquals("App", interpreter.getWindowTitle(APP_ID));
  }

  @Test
  public void paneDisappearsBeforeWindowTitleIsKnown_clearsOnlyMatchingPane() {
    shadowOf(service).setWindows(List.of(window(APP_ID, TYPE_APPLICATION, null)));
    paneEvent(CONTENT_CHANGE_TYPE_PANE_APPEARED, "Home");
    paneEvent(CONTENT_CHANGE_TYPE_PANE_TITLE, "Filters");
    paneEvent(CONTENT_CHANGE_TYPE_PANE_DISAPPEARED, "Home");
    assertEquals("Filters", interpreter.getAccessibilityPaneTitle(APP_ID));

    paneEvent(CONTENT_CHANGE_TYPE_PANE_DISAPPEARED, "Filters");
    assertFalse(interpreter.hasAccessibilityPane(APP_ID));
    assertEquals("", interpreter.getAccessibilityPaneTitle(APP_ID));
  }

  private void paneEvent(int contentChangeType, String title) {
    AccessibilityEvent event = new AccessibilityEvent(TYPE_WINDOW_STATE_CHANGED);
    shadowOf(event).setWindowId(APP_ID);
    event.setContentChangeTypes(contentChangeType);
    event.getText().add(title);
    interpreter.interpret(event, null);
  }

  private static AccessibilityWindowInfo window(int id, int type, String title) {
    AccessibilityWindowInfo window = new AccessibilityWindowInfo();
    shadowOf(window).setId(id);
    shadowOf(window).setType(type);
    shadowOf(window).setTitle(title);
    return window;
  }

  public static class TestService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
  }
}
