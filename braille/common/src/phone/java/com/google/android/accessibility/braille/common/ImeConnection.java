package com.google.android.accessibility.braille.common;

import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/** Provides IME related connection information. */
public class ImeConnection {
  /** Audial feedback types. */
  public enum AnnounceType {
    NORMAL,
    HIDE_PASSWORD,
    SILENCE,
  }

  public final InputConnection inputConnection;
  public final EditorInfo editorInfo;
  public final AnnounceType announceType;

  public ImeConnection(
      InputConnection inputConnection, EditorInfo editorInfo, AnnounceType announceType) {
    this.inputConnection = inputConnection;
    this.editorInfo = editorInfo;
    this.announceType = announceType;
  }
}
