package com.google.android.accessibility.braille.interfaces;

/**
 * Braille dot bit pattern constants.
 *
 * <p>Braille dots are represented with 8 bits, where the bit at position 0 means the presence or
 * absence of dot 1, and the bit at position 7 means the presence or absence of dot 8. For example,
 * the cell with dots 1 and 3 present (raised) is DOT1 | DOT3 - in binary, 0b00000005.
 */
public class BrailleDots {

  public static final int EMPTY_CELL = 0x00;
  public static final int FULL_CELL = 0xff;
  public static final int DOT1 = 0x01;
  public static final int DOT2 = 0x02;
  public static final int DOT3 = 0x04;
  public static final int DOT4 = 0x08;
  public static final int DOT5 = 0x10;
  public static final int DOT6 = 0x20;
  public static final int DOT7 = 0x40;
  public static final int DOT8 = 0x80;

  private BrailleDots() {}
}
