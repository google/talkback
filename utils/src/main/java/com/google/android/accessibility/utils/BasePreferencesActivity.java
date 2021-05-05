package com.google.android.accessibility.utils;

import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.MenuItem;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.TwoStatePreference;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Provides interfaces and common methods for a11y preference activity used. */
public abstract class BasePreferencesActivity extends AppCompatActivity {

  private PreferenceFragmentCompat preferenceFragment;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
    }

    int preferenceContentId = android.R.id.content;
    if (supportHatsSurvey()) {
      setContentView(R.layout.preference_with_survey);
      preferenceContentId = R.id.preference_root;
    }

    // Create UI for the preferenceFragment created by the child class of BasePreferencesActivity.
    preferenceFragment = createPreferenceFragment();
    if (preferenceFragment != null) {
      getSupportFragmentManager()
          .beginTransaction()
          .replace(preferenceContentId, preferenceFragment)
          .commit();
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (preferenceFragment != null) {
      // To avoid texts showing outside of the watch face, set a padding value if the preference
      // fragment is shown on watch. Square screen and round screen have different values.
      if (FeatureSupport.isWatch(getApplicationContext())
          && preferenceFragment.getListView() != null) {
        int padding =
            (int)
                getResources()
                    .getDimension(R.dimen.full_screen_preference_fragment_padding_on_watch);
        preferenceFragment.getListView().setPadding(0, padding, 0, padding);
      }
    }
  }

  /** If action-bar "navigate up" button is pressed, end this sub-activity. */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  /**
   * Finds preference from createPreferenceFragment() called only in onCreate(). gets non-updated
   * preferences, because base class stores only 1 createPreferenceFragment() call.
   */
  public Preference findPreference(String key) {
    return preferenceFragment.findPreference(key);
    // TODO: Replace with PreferenceSettingsUtils.findPreference(), because some sub-classes
    // call replace(android.R.id.content, newFragment) from static fragment classes that cannot
    // update preferenceFragment member.
  }

  /**
   * Creates a PreferenceFragmentCompat when {@link BasePreferencesActivity#onCreate} is called.
   *
   * @return {@link PreferenceFragmentCompat} for BasePreferencesActivity to create UI
   */
  protected abstract PreferenceFragmentCompat createPreferenceFragment();

  /** The implementation of the activity should supports HaTS survey layout or not. */
  protected boolean supportHatsSurvey() {
    return false;
  }

  /**
   * Updates the status of preference to on or off after the selector or context menu change the
   * state while the activity is visible.
   */
  protected void updateTwoStatePreferenceStatus(
      int preferenceKeyResId, int preferenceDefaultKeyResId) {
    @Nullable Preference preference = findPreference(getString(preferenceKeyResId));
    if (preference != null && preference instanceof TwoStatePreference) {
      // Make sure that we have the latest value of preference before continuing.
      boolean enabledState =
          SharedPreferencesUtils.getBooleanPref(
              SharedPreferencesUtils.getSharedPreferences(getApplicationContext()),
              getResources(),
              preferenceKeyResId,
              preferenceDefaultKeyResId);

      ((TwoStatePreference) preference).setChecked(enabledState);
    }
  }
}
