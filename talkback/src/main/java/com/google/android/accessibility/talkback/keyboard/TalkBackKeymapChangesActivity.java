package com.google.android.accessibility.talkback.keyboard;

import static com.google.android.accessibility.utils.preference.PreferencesActivity.FRAGMENT_NAME;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.VisibleForTesting;
import androidx.core.text.HtmlCompat;
import com.android.talkback.TalkBackPreferencesActivity;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.preference.base.TalkBackKeyboardShortcutPreferenceFragment;
import com.google.android.accessibility.utils.preference.BasePreferencesActivity;
import java.util.Locale;

/** Activity shows notifications for user how to switch to Default keymap. */
public class TalkBackKeymapChangesActivity extends BasePreferencesActivity {

  @VisibleForTesting
  static final Uri HYPERLINK_DEFAULT_KEYMAP_HELP_PAGE =
      Uri.parse("https://support.google.com/accessibility/android/answer/6110948");

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_talkback_keymap_changes);
    SpannableStringBuilder content =
        new SpannableStringBuilder(
            HtmlCompat.fromHtml(
                getString(R.string.keycombo_keymap_changes_activity_content),
                HtmlCompat.FROM_HTML_MODE_LEGACY));
    content.append("\n");
    SpannableString learnMore =
        SpannableString.valueOf(
            getString(R.string.keycombo_keymap_changes_activity_content_learn_more));
    URLSpan urlSpan =
        new URLSpan(
            HYPERLINK_DEFAULT_KEYMAP_HELP_PAGE + "?hl=" + Locale.getDefault().toLanguageTag());
    learnMore.setSpan(urlSpan, 0, learnMore.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
    content.append(learnMore);
    TextView contentTextView = findViewById(R.id.content);
    contentTextView.setMovementMethod(LinkMovementMethod.getInstance());
    contentTextView.setText(content);

    Button openSettings = findViewById(R.id.open_settings);
    openSettings.setOnClickListener(
        v -> {
          Intent intent =
              new Intent(TalkBackKeymapChangesActivity.this, TalkBackPreferencesActivity.class);
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          intent.putExtra(
              FRAGMENT_NAME, TalkBackKeyboardShortcutPreferenceFragment.class.getName());
          TalkBackKeymapChangesActivity.this.startActivity(intent);
        });
  }
}
