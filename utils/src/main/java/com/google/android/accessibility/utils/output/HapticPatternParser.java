package com.google.android.accessibility.utils.output;

import static com.google.common.primitives.Ints.indexOf;

import android.annotation.TargetApi;
import android.os.Build.VERSION_CODES;
import android.os.VibrationEffect;
import android.os.VibrationEffect.Composition;
import android.os.Vibrator;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.IntPredicate;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.base.Function;
import java.text.ParseException;

/**
 * Handles parsing a resource array defining a haptic pattern.
 *
 * <p>Each array contains two definitions: a "premium" haptic using VibrationEffect.Composition, and
 * a "fallback" haptic using createWaveform().
 *
 * <p>They are separated by a sentinel value: -9999.
 *
 * <p>Fallback comes first, then the premium. If there is no sentinel, the array is assumed to be a
 * fallback only.
 *
 * <p>The fallback array uses the same array format passed to {@link
 * VibrationEffect#createWaveform(long[], int)}.
 *
 * <p>The premium array consists of three elements per entry:
 *
 * <p>
 *
 * <ol>
 *   <li>Primitive ID constant See {@link android.os.VibrationEffect.Composition#addPrimitive(int)}
 *   <li>Scale (between 0-255)
 *   <li>delay (in ms)
 * </ol>
 */
public class HapticPatternParser {
  private static final String TAG = "HapticPatternParser";

  private static final int SENTINEL_SEPARATOR = -9999;
  private static final float SCALE_MAX = 255f;

  private final Function<long[], VibrationEffect> waveformCreator;
  private final IntPredicate isPrimitiveSupported;

  /** Creates a new parser, using the given vibrator to determine compatibility. */
  public HapticPatternParser(Vibrator vibrator) {
    this(
        pattern -> VibrationEffect.createWaveform(pattern, /* repeat= */ -1),
        vibrator::areAllPrimitivesSupported);
  }

  @VisibleForTesting
  public HapticPatternParser(
      Function<long[], VibrationEffect> waveformCreator, IntPredicate isPrimitiveSupported) {
    this.waveformCreator = waveformCreator;
    this.isPrimitiveSupported = isPrimitiveSupported;
  }

  /**
   * Parses the given pattern definition and returns an appropriate effect depending on the device
   * capabilities.
   *
   * <p>See the {@link HapticPatternParser} class docs for the definition spec.
   */
  public VibrationEffect parse(int[] pattern) {
    int splitIndex = indexOf(pattern, SENTINEL_SEPARATOR);
    if (splitIndex >= 0 && BuildVersionUtils.isAtLeastR()) {
      try {
        return parseComposition(pattern, splitIndex + 1);
      } catch (ParseException e) {
        LogUtils.e(TAG, e, "Failed to parse haptic pattern");
      }
    }

    return parseFallback(pattern, splitIndex);
  }

  @TargetApi(VERSION_CODES.R)
  private VibrationEffect parseComposition(int[] pattern, int splitIndex) throws ParseException {
    Composition effect = VibrationEffect.startComposition();
    for (int i = splitIndex; i < pattern.length; i += 3) {
      int primitive = pattern[i];
      if (!isPrimitiveSupported.test(primitive)) {
        throw new ParseException(
            "At least one composition primitives not supported by vibrator", i);
      }

      effect.addPrimitive(primitive, (float) pattern[i + 1] / SCALE_MAX, pattern[i + 2]);
    }

    return effect.compose();
  }

  private VibrationEffect parseFallback(int[] patternDefinition, int splitIndex) {
    int patternLength = splitIndex >= 0 ? splitIndex : patternDefinition.length;
    final long[] pattern = new long[patternLength];
    for (int i = 0; i < patternLength; i++) {
      pattern[i] = patternDefinition[i];
    }

    return waveformCreator.apply(pattern);
  }
}
