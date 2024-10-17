package com.google.android.accessibility.braille.common;

import android.content.Context;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages;

/** Reader class for accessing feature flags from Google experimentation framework. */
public final class FeatureFlagReader {

  /**
   * Whether to use contracted {@link BrailleLanguages.Code#EN_US_EBAE} English Braille American
   * Edition.
   */
  public static boolean useEBAEContracted(Context context) {
    return true;
  }

  /**
   * Whether to use contracted {@link BrailleLanguages.Code#EN_UK} English Braille United Kingdom.
   */
  public static boolean useBritishContracted(Context context) {
    return true;
  }

  /** Whether to use contracted {@link BrailleLanguages.Code#WELSH} Welsh Braille. */
  public static boolean useWelshContracted(Context context) {
    return true;
  }

  /** Whether to use contracted {@link BrailleLanguages.Code#ARABIC} Arabic Braille. */
  public static boolean useArabicContracted(Context context) {
    return true;
  }

  /** Whether to use contracted {@link BrailleLanguages.Code#FRENCH} French Braille. */
  public static boolean useFrenchContracted(Context context) {
    return true;
  }

  /** Whether to use contracted {@link BrailleLanguages.Code#SPANISH} Spanish Braille. */
  public static boolean useSpanishContracted(Context context) {
    return true;
  }

  /** Whether to use contracted {@link BrailleLanguages.Code#VIETNAMESE} Vietnamese Braille. */
  public static boolean useVietnameseContracted(Context context) {
    return true;
  }

  /** Whether to use contracted {@link BrailleLanguages.Code#GERMAN} German Braille. */
  public static boolean useGermanContracted(Context context) {
    return true;
  }

  /** Whether to use contracted {@link BrailleLanguages.Code#NORWEGIAN} Norwegian Braille. */
  public static boolean useNorwegianContracted(Context context) {
    return true;
  }

  /** Whether to use contracted {@link BrailleLanguages.Code#PORTUGUESE} Portuguese Braille. */
  public static boolean usePortugueseContracted(Context context) {
    return true;
  }

  /** Whether to use contracted {@link BrailleLanguages.Code#HUNGARIAN} Hungarian Braille. */
  public static boolean useHungarianContracted(Context context) {
    return true;
  }

  /** Whether to use contracted {@link BrailleLanguages.Code#DANISH} Danish Braille. */
  public static boolean useDanishContracted(Context context) {
    return true;
  }

  /** Whether to use contracted {@link BrailleLanguages.Code#TURKISH} Turkish Braille. */
  public static boolean useTurkishContracted(Context context) {
    return true;
  }

  /** Whether to use contracted {@link BrailleLanguages.Code#URDU} Urdu Braille. */
  public static boolean useUrduContracted(Context context) {
    return true;
  }

  /** Whether to use contracted {@link BrailleLanguages.Code#SWEDEN} Sweden Braille. */
  public static boolean useSwedenContracted(Context context) {
    return true;
  }

  /** Whether to use contracted {@link BrailleLanguages.Code#DANISH_8} Danish 8-dot Braille. */
  public static boolean useDanish8DotContracted(Context context) {
    return true;
  }

  /** Whether to use {@link BrailleLanguages.Code#KOREAN} Korean Braille. */
  public static boolean useKorean(Context context) {
    return false;
  }

  /** Whether to use {@link BrailleLanguages.Code#CANTONESE} Cantonese Braille. */
  public static boolean useCantonese(Context context) {
    return true;
  }

  /**
   * Whether to use {@link BrailleLanguages.Code#CHINESE_CHINA_COMMON} Chinese China Common Braille.
   */
  public static boolean useChineseChinaCommon(Context context) {
    return true;
  }

  /**
   * Whether to use {@link BrailleLanguages.Code#CHINESE_CHINA_CURRENT_WITH_TONES} Chinese China
   * Current with tones Braille.
   */
  public static boolean useChineseChinaCurrentWithTones(Context context) {
    return true;
  }

  /**
   * Whether to use {@link BrailleLanguages.Code#CHINESE_CHINA_CURRENT_WITHOUT_TONES} Chinese China
   * Current without tones Braille.
   */
  public static boolean useChineseChinaCurrentWithoutTones(Context context) {
    return true;
  }

  /**
   * Whether to use {@link BrailleLanguages.Code#CHINESE_CHINA_TWO_CELLS} Chinese China Two Cells
   * Braille.
   */
  public static boolean useChineseChinaTwoCells(Context context) {
    return true;
  }

  /** Whether to use {@link BrailleLanguages.Code#CHINESE_TAIWAN} Chinese Taiwan Braille. */
  public static boolean useChineseTaiwan(Context context) {
    return true;
  }

  /** Whether to use {@link BrailleLanguages.Code#JAPANESE} Japanese Braille. */
  public static boolean useJapanese(Context context) {
    return true;
  }

  /**
   * Whether to use {@link BrailleLanguages.Code#NORWEGIAN_COMP8} Norwegian 8-dot Computer Braille.
   */
  public static boolean useNorwegian8DotComputer(Context context) {
    return true;
  }

  /**
   * Whether to use {@link BrailleLanguages.Code#BULGARIAN_COMP8} Bulgarian 8-dot Computer Braille.
   */
  public static boolean useBulgarian8DotComputer(Context context) {
    return true;
  }

  /** Whether to replace emojis with their meanings. */
  public static boolean useReplaceEmoji(Context context) {
    return true;
  }

  private FeatureFlagReader() {}
}
