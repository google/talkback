package com.google.android.accessibility.brailleime.keyboardview;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.util.Size;
import android.view.ContextThemeWrapper;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.widget.FrameLayout;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.braille.common.TouchDots;
import com.google.android.accessibility.brailleime.BrailleIme;
import com.google.android.accessibility.brailleime.BrailleImeLog;
import com.google.android.accessibility.brailleime.BrailleInputOptions;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.brailleime.Utils;
import com.google.android.accessibility.brailleime.dialog.ViewAttachedDialog;
import com.google.android.accessibility.brailleime.input.BrailleDisplayImeStripView;
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
    void onViewReady();

    void onViewUpdated();

    void onViewCleared();

    void onAnnounce(String announcement, int delayMs);

    boolean isHideScreenMode();
  }

  private DisplayManager displayManager;
  private BrailleInputView brailleInputView;
  private BrailleDisplayImeStripView stripView;
  private TutorialView tutorialView;
  protected final Context context;
  protected final KeyboardViewCallback keyboardViewCallback;
  protected WindowManager windowManager;
  protected View imeInputView;
  protected ViewContainer viewContainer;

  protected KeyboardView(Context context, KeyboardViewCallback keyboardViewCallback) {
    this.context = new ContextThemeWrapper(context, R.style.BrailleImeTheme);
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
    if (isViewContainerCreated()) {
      return viewContainer;
    }
    init();
    viewContainer = createViewContainerInternal();
    updateViewContainerInternal();
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

  public boolean isTouchInteracting() {
    return isInputViewCreated() && brailleInputView.isTouchInteracting();
  }

  /** Creates {@link BrailleInputView} and adds it into {@link ViewContainer}. */
  public void createAndAddInputView(BrailleInputView.Callback inputPlaneCallback) {
    if (isInputViewAttached()) {
      keyboardViewCallback.onViewReady();
      return;
    }
    BrailleInputOptions options = obtainBrailleInputOptions();
    runWhenViewContainerIsReady(
        () -> {
          brailleInputView =
              new BrailleInputView(context, inputPlaneCallback, getScreenSize(), options);
          brailleInputView.setAccumulationMode(BrailleUserPreferences.readAccumulateMode(context));
          brailleInputView.setTableMode(
              BrailleUserPreferences.readLayoutMode(context) == TouchDots.TABLETOP);
          viewContainer.addView(brailleInputView, keyboardViewCallback::onViewReady);
        });
  }

  public void refreshInputView() {
    BrailleInputOptions options = obtainBrailleInputOptions();
    if (brailleInputView != null) {
      brailleInputView.setOptions(options);
    }
  }

  public void createAndAddStripView(BrailleDisplayImeStripView.CallBack callback) {
    if (isStripViewAttached()) {
      keyboardViewCallback.onViewReady();
      return;
    }
    runWhenViewContainerIsReady(
        () -> {
          stripView = new BrailleDisplayImeStripView(context);
          stripView.setCallBack(callback);
          viewContainer.addView(stripView, keyboardViewCallback::onViewReady);
        });
  }

  /** Creates {@link TutorialView} and adds it into {@link ViewContainer}. */
  public void createAndAddTutorialView(State tutorialState, TutorialCallback tutorialCallback) {
    if (isTutorialViewAttached()) {
      // On foldables, from unfold to fold, sometimes there is no onFinishInputView so view is still
      // attached.
      keyboardViewCallback.onViewReady();
      tutorialView.switchState(tutorialState, /* delay= */ 0);
      return;
    }
    runWhenViewContainerIsReady(
        () -> {
          tutorialView = new TutorialView(context, tutorialCallback, getScreenSize());
          tutorialView.switchState(tutorialState, /* delay= */ 0);
          viewContainer.addView(tutorialView, keyboardViewCallback::onViewReady);
        });
  }

  public void setWindowManager(WindowManager windowManager) {
    this.windowManager = windowManager;
  }

  /** Shows the given {@link ViewAttachedDialog} and attach it on the created view. */
  public void showViewAttachedDialog(ViewAttachedDialog viewAttachedDialog) {
    if (viewContainer != null) {
      // Invoke show() when view is ready to prevents WindowManager$BadTokenException.
      runWhenViewContainerIsReady(() -> viewAttachedDialog.show(viewContainer));
    } else if (imeInputView != null) {
      // Invoke show() when view is ready to prevents WindowManager$BadTokenException.
      runWhenImeInputViewIsReady(() -> viewAttachedDialog.show(imeInputView));
    } else {
      throw new IllegalArgumentException("No available view to attach.");
    }
  }

  /** Sets keyboard to table layout. */
  public void setTableMode(boolean enabled) {
    if (brailleInputView != null) {
      brailleInputView.setTableMode(enabled);
    }
  }

  /** Gets braille input view's dot count. */
  public int getBrailleInputViewDotCount() {
    if (brailleInputView != null) {
      return brailleInputView.getBrailleDotCount();
    }
    return -1;
  }

  /** Returns whether in two step calibration */
  public boolean inTwoStepCalibration() {
    if (brailleInputView != null) {
      return brailleInputView.inTwoStepCalibration();
    }
    return false;
  }

  /** Tear down keyboard view. */
  public void tearDown() {
    tearDownInternal();
    if (viewContainer != null && viewContainer.getChildCount() == 0) {
      viewContainer = null;
    }
    windowManager = null;
    keyboardViewCallback.onViewCleared();
    if (displayManager != null) {
      displayManager.unregisterDisplayListener(displayListener);
      displayManager = null;
    }
  }

  /** Removes braille input view and tutorial view in keyboard view. */
  public void removeBrailleInputViewAndTutorialView() {
    removeBrailleInputView();
    removeTutorialView();
  }

  /** Removes all views in keyboard view. */
  public void removeAllViews() {
    removeStripView();
    removeBrailleInputView();
    removeTutorialView();
  }

  /** Calibrates input view. */
  public void calibrateBrailleInputView() {
    if (brailleInputView == null) {
      return;
    }
    brailleInputView.calibrateByTwoSteps();
  }

  private void removeTutorialView() {
    if (viewContainer != null && tutorialView != null) {
      viewContainer.removeView(tutorialView);
      tutorialView.tearDown();
      tutorialView = null;
    }
  }

  private void removeBrailleInputView() {
    if (viewContainer != null && brailleInputView != null) {
      viewContainer.removeView(brailleInputView);
      brailleInputView = null;
    }
  }

  private void removeStripView() {
    if (viewContainer != null && stripView != null) {
      viewContainer.removeView(stripView);
      stripView = null;
    }
  }

  /** Returns ime region size on the screen. */
  public Optional<Rect> obtainImeViewRegion() {
    View view = imeInputView;
    if (isStripViewAttached()) {
      view = stripView;
    }
    return Optional.ofNullable(view)
        .map(
            v -> {
              int[] location = new int[2];
              v.getLocationInWindow(location);
              return new Rect(
                  location[0],
                  location[1],
                  location[0] + v.getWidth(),
                  location[1] + v.getHeight());
            });
  }

  /** Returns BrailleDisplayImeStripView. */
  public BrailleDisplayImeStripView getStripView() {
    return stripView;
  }

  private void runWhenViewContainerIsReady(Runnable runnable) {
    if (isViewContainerShown() || "robolectric".equals(Build.FINGERPRINT)) {
      runnable.run();
      return;
    }
    viewContainer
        .getViewTreeObserver()
        .addOnGlobalLayoutListener(
            new OnGlobalLayoutListener() {
              @Override
              public void onGlobalLayout() {
                if (isViewContainerShown()) {
                  viewContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                  runnable.run();
                }
              }
            });
  }

  private void runWhenImeInputViewIsReady(Runnable runnable) {
    if (isImeInputViewShown() || "robolectric".equals(Build.FINGERPRINT)) {
      runnable.run();
      return;
    }
    imeInputView
        .getViewTreeObserver()
        .addOnGlobalLayoutListener(
            new OnGlobalLayoutListener() {
              @Override
              public void onGlobalLayout() {
                if (isImeInputViewShown()) {
                  imeInputView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                  runnable.run();
                }
              }
            });
  }

  private BrailleInputOptions obtainBrailleInputOptions() {
    return BrailleInputOptions.builder()
        .setReverseDots(BrailleUserPreferences.readReverseDotsMode(context))
        .setBrailleType(BrailleUserPreferences.getCurrentTypingLanguageType(context))
        .setTutorialMode(false)
        .build();
  }

  private boolean isViewContainerShown() {
    return isViewContainerCreated() && viewContainer.isShown();
  }

  private boolean isImeInputViewShown() {
    return imeInputView != null && imeInputView.isShown();
  }

  private boolean isInputViewAttached() {
    return brailleInputView != null
        && viewContainer.isAttachedToWindow()
        && viewContainer.contains(brailleInputView);
  }

  private boolean isTutorialViewAttached() {
    return tutorialView != null
        && viewContainer.isAttachedToWindow()
        && viewContainer.contains(tutorialView);
  }

  private boolean isStripViewAttached() {
    return stripView != null && viewContainer.isAttachedToWindow();
  }

  /** Creates and returns the ImeInputView. */
  protected abstract View createImeInputViewInternal();

  /** Creates and returns the ViewContainer. */
  protected abstract ViewContainer createViewContainerInternal();

  /** Updates ViewContainer's attributes. For example, height and width. */
  protected abstract void updateViewContainerInternal();

  /** Gets the screen size of the device. */
  protected abstract Size getScreenSize();

  protected abstract void tearDownInternal();

  /**
   * Sets alpha of the view container depends on either hide screen or show screen is triggered by
   * Talkback.
   */
  public abstract void setKeyboardViewTransparent(boolean isTransparent);

  /** Signals that a orientation change has occurred. */
  public void onOrientationChanged(int orientation) {
    BrailleImeLog.logD(TAG, "onOrientationChanged");
    if (brailleInputView != null) {
      brailleInputView.onOrientationChanged(orientation, getScreenSize());
    }
    if (stripView != null) {
      stripView.onOrientationChanged(orientation, getScreenSize());
    }
    if (tutorialView != null) {
      tutorialView.onOrientationChanged(orientation, getScreenSize());
    }
    if (viewContainer != null) {
      updateViewContainerInternal();
    }
  }

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

  @VisibleForTesting
  public ViewContainer testing_getViewContainer() {
    return viewContainer;
  }

  @VisibleForTesting
  public BrailleInputView testing_getBrailleInputView() {
    return brailleInputView;
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

    /** A callback for notify clients view status changed. */
    public interface ViewStatusCallback {
      void onViewAdded();
    }

    public ViewContainer(Context context) {
      super(context);
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

    private boolean contains(View view) {
      for (int i = 0; i < getChildCount(); i++) {
        if (getChildAt(i).equals(view)) {
          return true;
        }
      }
      return false;
    }
  }
}
