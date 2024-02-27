/*
 * Copyright 2010 Google Inc.
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

package com.android.talkback;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentOnAttachListener;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.talkback.HatsSurveyRequester;
import com.google.android.accessibility.talkback.preference.base.TalkBackPreferenceFragment;
import com.google.android.accessibility.talkback.preference.base.TalkbackBaseFragment;
import com.google.android.accessibility.utils.PackageManagerUtils;
import com.google.android.accessibility.utils.preference.PreferencesActivity;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Locale;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Activity used to set TalkBack's service preferences.
 *
 * <p>Never change preference types. This is because of AndroidManifest.xml setting
 * android:restoreAnyVersion="true", which supports restoring preferences from a new play-store
 * installed talkback onto a clean device with older bundled talkback.
 * REFERTO
 */
public class TalkBackPreferencesActivity extends PreferencesActivity
    implements FragmentOnAttachListener {

  private static final String TAG = "PreferencesActivity";

  private HatsSurveyRequester hatsSurveyRequester;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    // Must be called before super.onCreate
    getSupportFragmentManager().addFragmentOnAttachListener(this);
    super.onCreate(savedInstanceState);

    // Check RTL.
    boolean isLocaleRTL =
        TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL;
    boolean isRTL =
        getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

    if (isLocaleRTL && !isRTL) {
      getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    }

    // Request the HaTS.
    if (supportHatsSurvey()) {
      hatsSurveyRequester = new HatsSurveyRequester(this);
      hatsSurveyRequester.requestSurvey();

      HatsRequesterViewModel viewModel =
          new ViewModelProvider(this).get(HatsRequesterViewModel.class);
      viewModel.setHatsSurveyRequester(hatsSurveyRequester);
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    getSupportFragmentManager().removeFragmentOnAttachListener(this);
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent keyEvent) {
    if ((keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK)
        && (keyEvent.getAction() == KeyEvent.ACTION_UP)
        && (hatsSurveyRequester != null)
        && (hatsSurveyRequester.handleBackKeyPress())) {
      return false;
    }
    return super.dispatchKeyEvent(keyEvent);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);

    String fragmentName = intent.getStringExtra(FRAGMENT_NAME);
    PreferenceFragmentCompat fragment = getFragmentByName(fragmentName);
    LogUtils.e(
        "Eric TAG",
        "Eric Mark: TalkBackPreferencesActivity: getContainerId()= %s",
        getContainerId());
    getSupportFragmentManager()
        .beginTransaction()
        .replace(getContainerId(), fragment, getFragmentTag())
        // Add root page to back-history
        .addToBackStack(/* name= */ null)
        .commit();
  }

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    Intent intent = getIntent();
    String fragmentName = null;
    if (intent != null) {
      fragmentName = intent.getStringExtra(FRAGMENT_NAME);
    }
    return getFragmentByName(fragmentName);
  }

  @Override
  public void onAttachFragment(FragmentManager fragmentManager, Fragment fragment) {
    if ((fragment instanceof TalkbackBaseFragment)
        && (!(fragment instanceof TalkBackPreferenceFragment))) {
      dismissHatsSurvey();
    }
  }

  private static @Nullable PreferenceFragmentCompat getFragmentByName(String fragmentName) {
    if (TextUtils.isEmpty(fragmentName)) {
      return new TalkBackPreferenceFragment();
    }

    try {
      return (PreferenceFragmentCompat) Class.forName(fragmentName).newInstance();
    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
      LogUtils.d(TAG, "Failed to load class: %s", fragmentName);
      return null;
    }
  }

  @Override
  protected boolean supportHatsSurvey() {
    // Platform should support Hats if GMS core is available.
    return PackageManagerUtils.hasGmsCorePackage(this);
  }

  /** Dismisses Hats survey. */
  private void dismissHatsSurvey() {
    if (hatsSurveyRequester != null) {
      hatsSurveyRequester.dismissSurvey();
      hatsSurveyRequester = null;
    }
  }

  /**
   * A {@link ViewModel} which encapsulates {@link HatsSurveyRequester} for use in TalkBack setting
   * fragments.
   */
  public static class HatsRequesterViewModel extends ViewModel {
    private HatsSurveyRequester hatsSurveyRequester;

    public HatsSurveyRequester getHatsSurveyRequester() {
      return hatsSurveyRequester;
    }

    public void setHatsSurveyRequester(HatsSurveyRequester hatsSurveyRequester) {
      this.hatsSurveyRequester = hatsSurveyRequester;
    }
  }
}
