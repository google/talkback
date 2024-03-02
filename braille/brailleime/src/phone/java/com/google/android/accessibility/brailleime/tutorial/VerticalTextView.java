package com.google.android.accessibility.brailleime.tutorial;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.widget.TextView;

/** A {@code TextView} that display texts vertically. */
public class VerticalTextView extends TextView {

  /** Enum for the two supported text orientations. */
  public enum TextOrientation {
    TOP_TO_BOTTOM,
    BOTTOM_TO_TOP
  }

  private TextOrientation orientation = TextOrientation.BOTTOM_TO_TOP;

  public VerticalTextView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  public VerticalTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public VerticalTextView(Context context) {
    super(context);
  }

  public void setTextOrientation(TextOrientation orientation) {
    this.orientation = orientation;
    invalidate();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(heightMeasureSpec, widthMeasureSpec);
    setMeasuredDimension(getMeasuredHeight(), getMeasuredWidth());
  }

  @Override
  protected void onDraw(Canvas canvas) {
    TextPaint textPaint = getPaint();
    textPaint.setColor(getCurrentTextColor());
    textPaint.drawableState = getDrawableState();

    canvas.save();

    if (orientation == TextOrientation.TOP_TO_BOTTOM) {
      canvas.translate(getWidth(), /* dy= */ 0);
      canvas.rotate(/* degree= */ 90);
    } else {
      canvas.translate(0, getHeight());
      canvas.rotate(/* degree= */ -90);
    }

    canvas.translate(getCompoundPaddingLeft(), getExtendedPaddingTop());

    getLayout().draw(canvas);
    canvas.restore();
  }
}
