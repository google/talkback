package com.google.android.accessibility.braille.common;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import com.google.android.accessibility.braille.interfaces.SelectionRange;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** Provides the utilities for common braille usages. */
public class BrailleCommonUtils {

  /**
   * Returns the number of characters that a backward word-deletion should remove from the given
   * {@code charSequence}.
   *
   * <p>In the following examples, {@code a} means any non whitespace character and {@code N} means
   * newline):
   *
   * <ul>
   *   <li>"" -> 0
   *   <li>"a" -> 1
   *   <li>"aa" -> 2
   *   <li>"aaa" -> 3
   *   <li>" " -> 1
   *   <li>" " -> 2
   *   <li>"a " -> 2
   *   <li>"a a" -> 1
   *   <li>"a a " -> 2
   *   <li>"N" -> 1
   *   <li>"NN" -> 1
   *   <li>"NNN" -> 1
   *   <li>"N " -> 1
   * </ul>
   *
   * @param charSequence the CharSequence on which to operate
   * @return the number of characters that should be deleted
   */
  public static int getLastWordLengthForDeletion(CharSequence charSequence) {
    // Returns 0 if charSequence is empty or null.
    if (TextUtils.isEmpty(charSequence)) {
      return 0;
    }

    int length = charSequence.length();
    int lastIndexOfWhitespace = lastIndexOfWhitespace(charSequence);
    // Returns charSequence.length if there is no space inside.
    if (lastIndexOfWhitespace < 0) {
      return length;
    }

    if (lastIndexOfWhitespace != length - 1) {
      // Hunk ends with a non-whitespace. Delete up to and excluding the greatest whitespace.
      return length - lastIndexOfWhitespace - 1;
    }

    char terminalCharacter = charSequence.charAt(length - 1);
    if (isNewline(terminalCharacter)) {
      // Hunk ends with newline.
      return 1;
    }

    int trailingWhitespaceLength = trailingWhitespaceCount(charSequence);
    if (length <= 1 || trailingWhitespaceLength != 1 || terminalCharacter != ' ') {
      return trailingWhitespaceNonNewlineCount(charSequence);
    }

    CharSequence cTrimmed = charSequence.subSequence(0, length - trailingWhitespaceLength);
    int lastIndexOfWhitespace2 = lastIndexOfWhitespace(cTrimmed);
    if (lastIndexOfWhitespace2 < 0) {
      return length;
    }
    return length - lastIndexOfWhitespace2 - 1;
  }

  private static boolean isNewline(char c) {
    return (c == '\r' || c == '\n');
  }

  /** Returns the number of consecutive whitespace characters at the end of {@code charSequence}. */
  public static int trailingWhitespaceCount(CharSequence charSequence) {
    int whitespaceCount = 0;
    for (int i = charSequence.length() - 1;
        i >= 0 && Character.isWhitespace(charSequence.charAt(i));
        i--) {
      whitespaceCount++;
    }
    return whitespaceCount;
  }

  /**
   * Returns the number of consecutive, non-newline whitespace characters at the end of {@param
   * charSequence}.
   */
  public static int trailingWhitespaceNonNewlineCount(CharSequence charSequence) {
    int whitespaceCount = 0;
    for (int i = charSequence.length() - 1;
        i >= 0
            && Character.isWhitespace(charSequence.charAt(i))
            && !isNewline(charSequence.charAt(i));
        i--) {
      whitespaceCount++;
    }
    return whitespaceCount;
  }

  /**
   * Finds the last index of whitespace in {@code charSequence}, returning -1 if it contains no
   * whitespace.
   */
  public static int lastIndexOfWhitespace(CharSequence charSequence) {
    int i;
    for (i = charSequence.length() - 1;
        i >= 0 && !Character.isWhitespace(charSequence.charAt(i));
        i--) {}
    return i;
  }

  /** Returns {@code true} if {@code editorInfo}'s input type is text. */
  public static boolean isTextField(EditorInfo editorInfo) {
    int inputTypeClass = getInputTypeClass(editorInfo.inputType);
    // All type classes not among {number, phone, datetime} are considered text.
    return !(inputTypeClass == InputType.TYPE_CLASS_NUMBER
        || inputTypeClass == InputType.TYPE_CLASS_PHONE
        || inputTypeClass == InputType.TYPE_CLASS_DATETIME);
  }
  /** Returns {@code true} if {@code editorInfo}'s input type is password. */
  public static boolean isPasswordField(EditorInfo editorInfo) {
    return editorInfo != null
        && (isNumberPasswordField(editorInfo.inputType)
            || isTextPasswordField(editorInfo.inputType));
  }

  /** Returns the start and the end of selected text. */
  public static SelectionRange getTextSelection(InputConnection inputConnection) {
    ExtractedText extractedText = inputConnection.getExtractedText(new ExtractedTextRequest(), 0);
    int selectionStart = 0;
    int selectionEnd = 0;
    if (extractedText != null) {
      selectionStart =
          extractedText.selectionStart > 0 ? extractedText.selectionStart : selectionStart;
      selectionEnd = extractedText.selectionEnd > 0 ? extractedText.selectionEnd : selectionEnd;
    }
    return new SelectionRange(selectionStart, selectionEnd);
  }

  /**
   * Invoke valueOf with a putative enum value name, returning the corresponding enum value if the
   * name is matched by one of the values in the enum collection; otherwise return the default
   * value.
   */
  public static <E extends Enum<E>> E valueOfSafe(String enumName, E def) {
    try {
      return Enum.valueOf(def.getDeclaringClass(), enumName);
    } catch (IllegalArgumentException e) {
      return def;
    }
  }

  /**
   * Performs key action with specific key code.
   *
   * @return true if success; false if either action down or action up performs on invalid {@link
   *     InputConnection}.
   */
  @CanIgnoreReturnValue
  public static boolean performKeyAction(InputConnection inputConnection, int keyCode) {
    if (inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode))) {
      return inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }
    return false;
  }

  private static boolean isNumberPasswordField(int inputType) {
    final int variation = inputType & InputType.TYPE_MASK_VARIATION;
    return getInputTypeClass(inputType) == InputType.TYPE_CLASS_NUMBER
        && (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
  }

  private static boolean isTextPasswordField(int inputType) {
    final int variation = inputType & InputType.TYPE_MASK_VARIATION;
    return getInputTypeClass(inputType) == InputType.TYPE_CLASS_TEXT
        && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
            || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
  }

  /** Returns {@code true} if {@code editorInfo}'s input type is visible password. */
  public static boolean isVisiblePasswordField(EditorInfo editorInfo) {
    final int variation = editorInfo.inputType & InputType.TYPE_MASK_VARIATION;
    return isPasswordField(editorInfo)
        && variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
  }

  /** Determines from system settings if {@code imeComponentName} is an enabled input method. */
  @SuppressWarnings("StringSplitter") // Guava not used in project, so Splitter not available.
  public static boolean isInputMethodEnabled(Context contextArg, ComponentName imeComponentName) {
    final String enabledIMEIds =
        Settings.Secure.getString(
            contextArg.getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS);
    if (enabledIMEIds == null) {
      return false;
    }

    for (String enabledIMEId : enabledIMEIds.split(":")) {
      if (imeComponentName.equals(ComponentName.unflattenFromString(enabledIMEId))) {
        return true;
      }
    }
    return false;
  }

  /** Determines, from system settings, if {@code imeComponentName} is the default input method. */
  public static boolean isInputMethodDefault(Context contextArg, ComponentName imeComponentName) {
    final String defaultIMEId =
        Settings.Secure.getString(
            contextArg.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);

    return defaultIMEId != null
        && imeComponentName.equals(ComponentName.unflattenFromString(defaultIMEId));
  }

  /** Filters non-printing characters like \u202A and \u202C. */
  public static CharSequence filterNonPrintCharacter(CharSequence text) {
    if (TextUtils.isEmpty(text)) {
      return "";
    }
    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text);
    for (int i = text.length() - 1; i >= 0; i--) {
      if (Character.isIdentifierIgnorable(stringBuilder.charAt(i))) {
        stringBuilder.delete(i, i + 1);
      }
    }
    return stringBuilder;
  }

  private static int getInputTypeClass(int inputType) {
    int typeClass = inputType & InputType.TYPE_MASK_CLASS;
    return (typeClass == 0 && (inputType & InputType.TYPE_MASK_VARIATION) != 0)
        ? InputType.TYPE_CLASS_TEXT
        : typeClass;
  }

  private BrailleCommonUtils() {}
}
