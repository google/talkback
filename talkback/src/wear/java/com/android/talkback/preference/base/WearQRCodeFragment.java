/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.talkback.preference.base;

import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.utils.QrCodeGenerator;
import com.google.android.accessibility.utils.material.WrapSwipeDismissLayoutHelper;
import com.google.zxing.WriterException;

/** A fragment in compliance with wear material style to show qr code. */
public class WearQRCodeFragment extends Fragment {

  private static final String TAG = "WearQRCodeFragment";

  private static final String EXTRA_URL = "extra_url";
  private static final String EXTRA_TITLE = "extra_title";
  private static final String EXTRA_CALL_TO_ACTION = "extra_call_to_action";
  private static final String EXTRA_ALTERNATE_STRING = "extra_alternate_string";

  private View targetFragmentView;
  private int targetFragmentViewOriginalA11yMode;

  public static WearQRCodeFragment createWearQrCodeFragment(
      String url, String title, String callToAction, String alternateString) {
    Bundle bundle = new Bundle();
    bundle.putString(EXTRA_URL, url);
    bundle.putString(EXTRA_TITLE, title);
    bundle.putString(EXTRA_CALL_TO_ACTION, callToAction);
    bundle.putString(EXTRA_ALTERNATE_STRING, alternateString);
    WearQRCodeFragment fragment = new WearQRCodeFragment();
    fragment.setArguments(bundle);
    return fragment;
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater layoutInflater,
      @Nullable ViewGroup viewGroup,
      @Nullable Bundle bundle) {

    Bundle arguments = getArguments();

    View view = layoutInflater.inflate(R.layout.wear_qr_code_fragment, viewGroup, false);

    // We set background color here to address the warning of "Possible overdraw".
    view.setBackgroundColor(
        getResources()
            .getColor(R.color.a11y_wear_material_color_background, getContext().getTheme()));

    TextView titleTextView = view.findViewById(R.id.title);
    TextView ctaTextView = view.findViewById(R.id.call_to_action);
    TextView alternateTextView = view.findViewById(R.id.alternate);

    String title = arguments.getString(EXTRA_TITLE);
    titleTextView.setText(title);
    ctaTextView.setText(arguments.getString(EXTRA_CALL_TO_ACTION));
    alternateTextView.setText(arguments.getString(EXTRA_ALTERNATE_STRING));

    showUrlQrCode(view.findViewById(R.id.qrcode), arguments.getString(EXTRA_URL));

    ViewCompat.setAccessibilityPaneTitle(view, title);
    // To support Wear rotary input.
    view.requestFocus();

    return WrapSwipeDismissLayoutHelper.wrapSwipeDismissLayout(
        getActivity(),
        view,
        activity -> {
          popBackStack();
          return true;
        });
  }

  private void popBackStack() {
    // When we pop back to the parent fragment, we need to restore the a11y importance
    // attribute.
    targetFragmentView.setImportantForAccessibility(targetFragmentViewOriginalA11yMode);
    // To support Wear rotary input.
    targetFragmentView.requestFocus();
    // TODO
    requireActivity().getSupportFragmentManager().popBackStackImmediate();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle bundle) {
    super.onViewCreated(view, bundle);

    if (bundle == null) {
      requireActivity()
          .getOnBackPressedDispatcher()
          .addCallback(
              this,
              new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                  popBackStack();
                }
              });
    }

    targetFragmentView = getTargetFragment().requireView();
    // We add this fragment onto the parent fragment, so we need to hide the parent's views.
    targetFragmentViewOriginalA11yMode = targetFragmentView.getImportantForAccessibility();
    targetFragmentView.setImportantForAccessibility(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
  }

  private void showUrlQrCode(ImageView qrCodeImageView, String url) {

    boolean isValidUrl = Patterns.WEB_URL.matcher(url).matches();

    if (isValidUrl) {
      int sizePx = getContext().getResources().getDimensionPixelSize(R.dimen.wear_qr_code_size);
      try {
        Bitmap bitmap = QrCodeGenerator.encodeQrCode(url, sizePx, /* invert= */ true);
        qrCodeImageView.setImageBitmap(bitmap);
      } catch (WriterException ex) {
        Log.w(TAG, "Could not generate the QR code: " + url);
        qrCodeImageView.setVisibility(View.GONE);
      }
    } else {
      // It shouldn't happen and we should prevent it in advance.
      throw new IllegalArgumentException("The URL is invalid to be encoded as a QR code: " + url);
    }
  }
}
