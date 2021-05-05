package com.google.android.accessibility.talkback.training.content;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.R;

/** An {@link EditText} with a text or a hint. */
public class EditTextBox extends PageContentConfig {

  private final @StringRes int textResId;
  private final @StringRes int hintResId;

  public EditTextBox(@StringRes int textResId, @StringRes int hintResId) {
    this.textResId = textResId;
    this.hintResId = hintResId;
  }

  @Override
  public View createView(LayoutInflater inflater, ViewGroup container, Context context) {
    final View view = inflater.inflate(R.layout.training_edit_text, container, false);
    final EditText editText = view.findViewById(R.id.training_edit_text);
    if (textResId != UNKNOWN_RESOURCE_ID) {
      editText.setText(textResId);
    } else if (hintResId != UNKNOWN_RESOURCE_ID) {
      editText.setHint(hintResId);
    }
    return view;
  }
}
