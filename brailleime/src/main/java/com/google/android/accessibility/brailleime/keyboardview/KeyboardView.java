package com.google.android.accessibility.brailleime.keyboardview;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Region;
import android.hardware.display.DisplayManager;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.widget.FrameLayout;
import com.google.android.accessibility.brailleime.BrailleIme;
import com.google.android.accessibility.brailleime.BrailleImeLog;
import com.google.android.accessibility.brailleime.DotsLayout;
import com.google.android.accessibility.brailleime.UserPreferences;
import com.google.android.accessibility.brailleime.Utils;
import com.google.android.accessibility.brailleime.dialog.ViewAttachedDialog;
import com.google.android.accessibility.brailleime.input.BrailleInputView;
import com.google.android.accessibility.brailleime.tutorial.TutorialView;
import com.google.android.accessibility.brailleime.tutorial.TutorialView.TutorialCallback;
import com.google.android.accessibility.brailleime.tutorial.TutorialView.TutorialState.State;
import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;

/**
 * Manages the IME's input view and the code branching caused by strategy differences in Q and R.
 *
 * <p>First note that any Android Braille IME implementation faces a major challenge: it wants
 * ExploreByTouch to be on (because it will presumably be used by blind/low-vision customers for
 * whom ExploreByTouch is an important feature), but it needs ExploreByTouch to be off because
 * having it off allows the delivery of raw MotionEvents to the IME, which is crucial for
 * performance reasons. In other words, a Braille IME needs ExploreByTouch to be disabled in the
 * region where braille input (tapping or swiping on braille dots) is occurring, but it would like
 * to have ExploreByTouch enabled for the rest of the view graph, including the underlying Activity,
 * any chrome around the input area (such as a system navigation bar), and any dialogs that pop-up
 * over the IME.
 *
 * <p>In Q-and-below, this problem is solved quite hackily by a combination of
 *
 * <ol>
 *   <li>An additional Window of type ACCESSIBILITY_OVERLAY in which the braille dots are placed
 *   <li>The temporary disablement of ExploreByTouch while the IME is up and no IME-related dialog
 *       is up
 *   <li>A fully-immersive View so that the system nav bar cannot be accidentally touched. This is a
 *       major deviation from the IME pattern encouraged by the framework; the input Window provided
 *       by the IME framework is filled with a 0-size unused View and all (non-dialog-related) input
 *       occurs in the overlay Window.
 * </ol>
 *
 * <p>In R-and-later, with the introduction of setTouchExplorationPassthroughRegion API, a simpler
 * approach is used, which is more aligned with the pattern encouraged by the IME framework. Now,
 * the braille dot Views get placed into the IME's input View (instead of introducing an overlay
 * Window) and ExploreByTouch is not disabled; instead the Region of the screen corresponding to the
 * input View is marked as being a touch exploration passthrough region, which allows raw
 * MotionEvents to be received by BrailleIme.
 *
 * <p>In support of these two strategies, this class manages two Views: imeInputView and
 * viewContainer, the roles of which are described below.
 *
 * <p>The imeInputView gets passed to the {@link android.inputmethodservice.InputMethodService} and
 * is the framework's notion of IME input View. It might be empty or it might contain usable Views,
 * depending on the strategy. It also acts as a dialog anchor which works even if TalkBack is turned
 * off (edge case) and the braille input dots cannot be shown.
 *
 * <p>The viewContainer on the other hand always holds the interact-able Views (such as braille dots
 * or tutorial-related text and images) regardless of the strategy employed. In the Q-and-below
 * strategy, it gets placed inside of the overlay Window; in the R-and-later strategy, it serves as
 * the imeInputView - in that case both imeInputView and viewContainer refer to the same object.
 */
public abstract class KeyboardView {
  private static final String TAG = "KeyboardView";

  /** A callback to notify clients of state changes. */
  public interface KeyboardViewCallback {
    void onViewAdded();

    void onViewUpdated();

    void onAnnounce(String announcement, int delayMs);
  }

  private DisplayManager displayManager;
  private BrailleInputView brailleInputView;
  private TutorialView tutorialView;
  protected final Context context;
  protected final KeyboardViewCallback keyboardViewCallback;
  protected WindowManager windowManager;
  protected View imeInputView;
  protected ViewContainer viewContainer;

  protected KeyboardView(Context context, KeyboardViewCallback keyboardViewCallback) {
    this.context = context;
    this.keyboardViewCallback = keyboardViewCallback;
  }

  /** Creates and returns the IME inputView. */
  public View createImeInputView() {
    init();
    imeInputView = createImeInputViewInternal();
    return imeInputView;
  }

  /** Creates and returns ViewContainer. */
  public ViewContainer createViewContainer() {
    init();
    viewContainer = createViewContainerInternal();
    updateViewContainerInternal();
    viewContainer.setViewContainerCallback(viewContainerCallback);
    return viewContainer;
  }

  private void init() {
    if (displayManager == null) {
      displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
      displayManager.registerDisplayListener(displayListener, /* handler= */ null);
    }
  }

  /** Saves input view points. */
  public void saveInputViewPoints() {
    if (brailleInputView != null) {
      brailleInputView.savePoints();
    }
  }

  /** Returns current tutorial status. */
  public State getTutorialStatus() {
    return isTutorialViewCreated() ? tutorialView.getTutorialState() : State.NONE;
  }

  public boolean isViewContainerCreated() {
    return viewContainer != null;
  }

  public boolean isInputViewCreated() {
    return brailleInputView != null;
  }

  public boolean isTutorialViewCreated() {
    return tutorialView != null;
  }

  public boolean isTutorialShown() {
    return tutorialView != null && tutorialView.isShown();
  }

  /** Creates {@link BrailleInputView} and adds it into {@link ViewContainer}. */
  public void createAndAddInputView(BrailleInputView.Callback inputPlaneCallback) {
    runWhenViewIsReady(
        viewContainer,
        () -> {
          brailleInputView =
              new BrailleInputView(
                  context, inputPlaneCallback, getScreenSize(), /* isTutorial= */ false);
          brailleInputView.setAccumulationMode(UserPreferences.readAccumulateMode(context));
          brailleInputView.addOnAttachStateChangeListener(onAttachStateChangeListener);
          brailleInputView.setTableMode(
              UserPreferences.readLayoutMode(context) == DotsLayout.TABLETOP);
          viewContainer.addView(brailleInputView, keyboardViewCallback::onViewAdded);
        });
  }

  /** Creates {@link TutorialView} and adds it into {@link ViewContainer}. */
  public void createAndAddTutorialView(State tutorialState, TutorialCallback tutorialCallback) {
    runWhenViewIsReady(
        viewContainer,
        () -> {
          tutorialView =
              new TutorialView(
                  context,
                  tutorialCallback,
                  getScreenSize(),
                  UserPreferences.readReverseDotsMode(context));
          tutorialView.switchNextState(tutorialState, /* delay= */ 0);
          viewContainer.addView(tutorialView, keyboardViewCallback::onViewAdded);
        });
  }

  public void setWindowManager(WindowManager windowManager) {
    this.windowManager = windowManager;
  }

  /** Shows the given {@link ViewAttachedDialog} and attach it on the created view. */
  public void showViewAttachedDialog(ViewAttachedDialog viewAttachedDialog) {
    if (viewContainer != null) {
      // Invoke show() when view is ready to prevents WindowManager$BadTokenException.
      runWhenViewIsReady(viewContainer, () -> viewAttachedDialog.show(viewContainer));
    } else if (imeInputView != null) {
      // Invoke show() when view is ready to prevents WindowManager$BadTokenException.
      runWhenViewIsReady(imeInputView, () -> viewAttachedDialog.show(imeInputView));
    } else {
      throw new IllegalArgumentException("No available view to attach.");
    }
  }

  /** Sets keyboard to table layout. */
  public void setTableMode(boolean enabled) {
    if (brailleInputView != null) {
      brailleInputView.savePoints();
      brailleInputView.setTableMode(enabled);
    }
  }

  public void tearDown() {
    tearDownInternal();
    if (viewContainer != null) {
      viewContainer.removeAllViews();
      viewContainer = null;
    }
    if (brailleInputView != null) {
      brailleInputView.removeOnAttachStateChangeListener(onAttachStateChangeListener);
      brailleInputView = null;
    }
    if (tutorialView != null) {
      tutorialView.tearDown();
      tutorialView = null;
    }
    windowManager = null;
    if (displayManager != null) {
      displayManager.unregisterDisplayListener(displayListener);
      displayManager = null;
    }
  }

  /** Returns imeInputView size. Return empty if imeInputView haven't generated. */
  public Optional<Size> getViewForImeFrameworksSize() {
    return Optional.ofNullable(imeInputView)
        .map(value -> new Size(value.getWidth(), value.getHeight()));
  }

  private void runWhenViewIsReady(View view, Runnable runnable) {
    if (view.isShown() || "robolectric".equals(Build.FINGERPRINT)) {
      runnable.run();
      return;
    }
    view.getViewTreeObserver()
        .addOnGlobalLayoutListener(
            new OnGlobalLayoutListener() {
              @Override
              public void onGlobalLayout() {
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                runnable.run();
              }
            });
  }

  /** Returns {@link ViewContainer} displaying region size on the screen. */
  public abstract Optional<Region> obtainViewContainerRegionOnTheScreen();

  /** Creates and returns the ImeInputView. */
  protected abstract View createImeInputViewInternal();

  /** Creates and returns the ViewContainer. */
  protected abstract ViewContainer createViewContainerInternal();

  /** Updates ViewContainer's attributes. For example, height and width. */
  protected abstract void updateViewContainerInternal();

  /** Gets the screen size of the device. */
  protected abstract Size getScreenSize();

  protected abstract void tearDownInternal();

  private final ViewContainer.Callback viewContainerCallback =
      new ViewContainer.Callback() {
        @Override
        public void onOrientationChanged(int orientation) {
          BrailleImeLog.logD(TAG, "onOrientationChanged");
          if (brailleInputView != null) {
            // Note: Tablets read different preference for landscape and portrait.
            brailleInputView.savePoints();
            brailleInputView.onOrientationChanged(orientation, getScreenSize());
          }
          if (tutorialView != null) {
            tutorialView.onOrientationChanged(orientation, getScreenSize());
          }
          if (viewContainer != null) {
            updateViewContainerInternal();
          }
        }
      };

  private final DisplayManager.DisplayListener displayListener =
      new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {}

        @Override
        public void onDisplayChanged(int displayId) {
          if (viewContainer == null) {
            return;
          }
          int rotation = Utils.getDisplayRotationDegrees(context);
          if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            updateViewContainerInternal();
          }
        }

        @Override
        public void onDisplayRemoved(int displayId) {}
      };

  private final OnAttachStateChangeListener onAttachStateChangeListener =
      new OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow(View v) {
          BrailleImeLog.logD(TAG, "onViewAttachedToWindow");
        }

        @Override
        public void onViewDetachedFromWindow(View v) {
          BrailleImeLog.logD(TAG, "onViewDetachedFromWindow");
          brailleInputView.savePoints();
        }
      };

  @VisibleForTesting
  public void testing_setViewContainer(ViewContainer viewContainer) {
    this.viewContainer = viewContainer;
  }

  @VisibleForTesting
  public ViewContainer testing_getViewContainer() {
    return viewContainer;
  }

  @VisibleForTesting
  public BrailleInputView testing_getBrailleInputView() {
    return brailleInputView;
  }

  @VisibleForTesting
  public ViewContainer.Callback testing_getViewContainerCallback() {
    return viewContainerCallback;
  }

  @VisibleForTesting
  public TutorialView testing_getTutorialView() {
    return tutorialView;
  }

  /**
   * A container for whatever View should be shown in {@link BrailleIme}.
   *
   * <p>This layer must exist because {@link InputMethodService} subclass instances are not informed
   * of all configuration changes, but {@link View} subclass instances are informed of such changes;
   * so {@link BrailleIme} relies on this class to inform it of configuration changes.
   */
  public static class ViewContainer extends FrameLayout {

    /** A callback for receiving onConfigurationChanged from ViewContainer. */
    public interface Callback {

      /**
       * Signals that a orientation change has occurred.
       *
       * <p>Since some components (such as InputMethodService instances) are not always informed of
       * orientation changes, this method allows a View (which is always informed) to signal such a
       * change to such a component.
       */
      void onOrientationChanged(int orientation);
    }

    /** A callback for notify clients view status changed. */
    public interface ViewStatusCallback {
      void onViewAdded();
    }

    private Callback callback;
    private int orientation;

    public ViewContainer(Context context) {
      super(context);
      this.orientation = getResources().getConfiguration().orientation;
    }

    public void setViewContainerCallback(Callback callback) {
      this.callback = callback;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
      super.onConfigurationChanged(newConfig);
      if (orientation != newConfig.orientation) {
        orientation = newConfig.orientation;
        if (callback != null) {
          callback.onOrientationChanged(newConfig.orientation);
        }
      }
    }

    @Override
    public void addView(View child) {
      removeAllViews();
      super.addView(child);
    }

    public void addView(View child, ViewStatusCallback viewStatusCallback) {
      getViewTreeObserver()
          .addOnGlobalLayoutListener(
              new OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                  getViewTreeObserver().removeOnGlobalLayoutListener(this);
                  viewStatusCallback.onViewAdded();
                }
              });
      addView(child);
    }
  }
}
