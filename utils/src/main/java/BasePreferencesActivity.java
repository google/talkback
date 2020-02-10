package com.google.android.accessibility.utils;

import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.MenuItem;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
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

    // Create UI for the preferenceFragment created by the child class of BasePreferencesActivity.
    preferenceFragment = createPreferenceFragment();
    if (preferenceFragment != null) {
      getSupportFragmentManager()
          .beginTransaction()
          .replace(android.R.id.content, preferenceFragment)
          .commit();
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
}
