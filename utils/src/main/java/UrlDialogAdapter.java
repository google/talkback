package com.google.android.accessibility.utils;

import android.content.Context;
import androidx.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.util.List;

/**
 * Custom {@link ArrayAdapter} that allows {@link android.app.AlertDialog}s to display a URL path
 * and its associated text via a {@link SpannableUrl} in a single row.
 */
public final class UrlDialogAdapter extends ArrayAdapter<SpannableUrl> {

  /**
   * @param context The context associated with this adapter.
   * @param spannableUrls A list of {@link SpannableUrl} to be displayed in a single row via the
   *     adapter.
   */
  public UrlDialogAdapter(Context context, @NonNull List<SpannableUrl> spannableUrls) {
    super(context, R.layout.url_dialog_row, R.id.dialog_url_view, spannableUrls);
  }

  @Override
  public View getView(int position, View currentView, ViewGroup parent) {
    View view = super.getView(position, currentView, parent);
    SpannableUrl spannableUrl = getItem(position);
    if (spannableUrl == null) {
      return view;
    }
    TextView urlTextView = view.findViewById(R.id.dialog_url_view);
    if (spannableUrl.isTextAndPathEquivalent()) {
      urlTextView.setText(spannableUrl.path());
    } else {
      String urlPathAndTextString =
          getContext()
              .getString(R.string.url_dialog_table, spannableUrl.text(), spannableUrl.path());
      urlTextView.setText(urlPathAndTextString);
    }

    return view;
  }
}
