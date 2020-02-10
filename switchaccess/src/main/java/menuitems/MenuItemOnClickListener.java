package com.google.android.accessibility.switchaccess.menuitems;

import android.view.View;
import android.view.View.OnClickListener;

/**
 * The {@link OnClickListener} for {@link MenuItem}s. They don't use the view parameter in the
 * existing {@link OnClickListener#onClick)} method, so this class adds a parameter-less version of
 * #onClick and calls it from within the existing #onClick method.
 */
public abstract class MenuItemOnClickListener implements OnClickListener {

  public abstract void onClick();

  @Override
  public void onClick(View view) {
    this.onClick();
  }
}
