package com.google.android.accessibility.brailleime.input;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import androidx.core.content.ContextCompat;
import com.google.android.accessibility.brailleime.R;
import com.google.common.collect.ImmutableMap;
import java.util.List;

/** A strip which shows when braille display is connected. */
public class BrailleDisplayImeStripView extends RelativeLayout {
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
  private final ImageView dotsBackground;
  private final View switchToTouchScreenKeyboard;

  /** Callback when BrailleDisplayImeStripView is clicked. */
  public interface CallBack {
    void onClicked();
  }

  public BrailleDisplayImeStripView(Context context) {
    this(context, null);
  }

  public BrailleDisplayImeStripView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public BrailleDisplayImeStripView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    View container = View.inflate(context, R.layout.brailledisplay_ime, this);
    switchToTouchScreenKeyboard = container.findViewById(R.id.switch_to_touch_screen_keyboard);
    dotsBackground = container.findViewById(R.id.dots_background);
  }

  public void setCallBack(CallBack callBack) {
    switchToTouchScreenKeyboard.setOnClickListener(view -> callBack.onClicked());
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
    transitionDrawable.startTransition(500);
  }
}
