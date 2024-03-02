package com.google.android.accessibility.brailleime.input;

import static java.lang.Math.min;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.util.Size;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.android.accessibility.brailleime.BrailleIme.OrientationSensitive;
import com.google.android.accessibility.brailleime.R;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Locale;

/** A strip which shows when braille display is connected. */
public class BrailleDisplayImeStripView extends RelativeLayout implements OrientationSensitive {
  private static final int DURATION_MILLISECONDS = 150;
  private final ImmutableMap<Integer, Integer> dotsResMap =
      ImmutableMap.<Integer, Integer>builder()
          .put(1, R.drawable.dots_tapped_1)
          .put(2, R.drawable.dots_tapped_2)
          .put(3, R.drawable.dots_tapped_3)
          .put(4, R.drawable.dots_tapped_4)
          .put(5, R.drawable.dots_tapped_5)
          .put(6, R.drawable.dots_tapped_6)
          .put(7, R.drawable.dots_tapped_7)
          .put(8, R.drawable.dots_tapped_8)
          .buildOrThrow();
  private CallBack callBack;
  private ImageView dotsBackground;
  private View switchToTouchScreenKeyboard;
  private View switchToNextKeyboard;
  private Locale locale;

  /** Callback when BrailleDisplayImeStripView is clicked. */
  public interface CallBack {
    void onSwitchToOnscreenKeyboard();

    void onSwitchToNextKeyboard();
  }

  public BrailleDisplayImeStripView(Context context) {
    this(context, null);
  }

  public BrailleDisplayImeStripView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public BrailleDisplayImeStripView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    locale = Locale.getDefault();
    addView();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int heightSize = MeasureSpec.getSize(heightMeasureSpec);
    int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    int maxHeight =
        (int)
            (getContext().getDisplay().getHeight()
                * getContext()
                    .getResources()
                    .getFloat(R.dimen.braille_display_keyboard_height_fraction));
    heightMeasureSpec =
        heightMode == MeasureSpec.UNSPECIFIED
            ? MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
            : MeasureSpec.makeMeasureSpec(min(heightSize, maxHeight), heightMode);
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  private void addView() {
    View container =
        View.inflate(
            new ContextThemeWrapper(getContext(), R.style.BrailleDisplayKeyboardTheme),
            R.layout.brailledisplay_ime,
            this);
    switchToTouchScreenKeyboard = container.findViewById(R.id.switch_to_touch_screen_keyboard);
    switchToNextKeyboard = container.findViewById(R.id.switch_to_next_keyboard);
    dotsBackground = container.findViewById(R.id.dots_background);
    setCallBack(callBack);
  }

  @Override
  protected void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    Locale newLocale = Locale.getDefault();
    if (!locale.equals(newLocale)) {
      refreshViews();
      locale = newLocale;
    }
  }

  @Override
  public void onOrientationChanged(int orientation, Size screenSize) {
    refreshViews();
  }

  /** Sets callback for strip view. If null, remove callback for listeners. */
  public void setCallBack(@Nullable CallBack callBack) {
    this.callBack = callBack;
    switchToTouchScreenKeyboard.setOnClickListener(
        callBack == null ? null : view -> callBack.onSwitchToOnscreenKeyboard());
    switchToNextKeyboard.setOnClickListener(
        callBack == null ? null : view -> callBack.onSwitchToNextKeyboard());
  }

  /** Animates tapped dots. */
  public void animateInput(List<Integer> dotNumbers) {
    Drawable[] layers = new Drawable[dotNumbers.size()];
    for (int i = 0; i < dotNumbers.size(); i++) {
      layers[i] = ContextCompat.getDrawable(getContext(), dotsResMap.get(dotNumbers.get(i)));
    }
    LayerDrawable layerDrawable = new LayerDrawable(layers);
    TransitionDrawable transitionDrawable =
        new TransitionDrawable(
            new Drawable[] {
              layerDrawable, ContextCompat.getDrawable(getContext(), R.drawable.dots_untapped)
            });
    dotsBackground.setImageDrawable(transitionDrawable);
    transitionDrawable.setCrossFadeEnabled(true);
    transitionDrawable.startTransition(DURATION_MILLISECONDS);
  }

  private void refreshViews() {
    removeAllViews();
    addView();
  }
}
