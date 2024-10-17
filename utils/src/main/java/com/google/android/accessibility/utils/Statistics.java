/*
 * Copyright (C) 2024 Google Inc.
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
package com.google.android.accessibility.utils;

import com.google.common.collect.EvictingQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

/** General-purpose summary & distribution statistics for a group of values. */
public final class Statistics {
  private static final int MAX_RAW_DATA_SIZE = 300;

  private long numMissing;
  private long count;
  private long sum;
  private long sumSquares;
  private final Queue<Long> rawData;

  /** Bin start value = 2^(index-1) , except index=0 holds bin start value=0. */
  ArrayList<AtomicLong> histogram = new ArrayList<>();

  public Statistics() {
    rawData = EvictingQueue.create(MAX_RAW_DATA_SIZE);
    clear();
  }

  public synchronized void clear() {
    numMissing = 0;
    count = 0;
    sum = 0;
    sumSquares = 0;
    histogram.clear();
    rawData.clear();
  }

  public synchronized void incrementNumMissing() {
    ++numMissing;
  }

  public synchronized void increment(long value) {
    // Increment summary statistics.
    ++count;
    sum += value;
    sumSquares += value * value;

    // Ensure histogram is big enough to hold this value.
    int binIndex = valueToHistogramBin(value);
    if (histogram.size() < binIndex + 1) {
      histogram.ensureCapacity(binIndex + 1);
      while (histogram.size() <= binIndex) {
        histogram.add(new AtomicLong(0));
      }
    }
    // Increment histogram count.
    AtomicLong binCount = histogram.get(binIndex);
    binCount.set(binCount.longValue() + 1);
    rawData.add(value);
  }

  public long getNumMissing() {
    return numMissing;
  }

  public long getCount() {
    return count;
  }

  public long getMean() {
    return (count <= 0) ? 0 : (sum / count);
  }

  /**
   * Computes standard devication based on the mistaken assumption that values have gaussian
   * distribution.
   *
   * @return Standard deviation of {@code increment(value)}
   */
  public double getStdDev() {
    if (count <= 0) {
      return 0;
    }
    double mean = (double) sum / (double) count;
    double meanOfSquares = (double) sumSquares / (double) count;
    double variance = meanOfSquares - (mean * mean);
    return Math.sqrt(variance);
  }

  public long getMedianBinStart() {
    if (count <= 0) {
      return 0;
    }
    // For each histogram bin, in order...
    long medianCount = count / 2;
    long sumBins = 0;
    for (int binIndex = 0; binIndex < histogram.size(); ++binIndex) {
      // If bin contains mCount/2... return bin start.
      sumBins += histogram.get(binIndex).longValue();
      if (sumBins >= medianCount) {
        return histogramBinToStartValue(binIndex);
      }
    }
    return histogramBinToStartValue(histogram.size());
  }

  public int valueToHistogramBin(long value) {
    return valueToPower(value) + 1;
  }

  public long histogramBinToStartValue(int index) {
    return (index < 1) ? 0L : (1L << (index - 1));
  }

  /**
   * Gets the percentile with the given rank.
   *
   * @param rank The rank, between 0 < rank <= 100.
   * @return The percentile value otherwise -1 if {@code rawData} is invalid,
   */
  public long getPercentile(int rank) {
    if (rawData == null || rawData.isEmpty()) {
      return -1L;
    }

    List<Long> sortedData = new ArrayList<>(rawData);
    Collections.sort(sortedData);
    int index = ((rank * sortedData.size() + 99) / 100) - 1;

    return sortedData.get(index);
  }

  /**
   * Converts a positive value to the exponent of preceding 2^P. Returns the largest integer
   * exponent "P" such that 2^P < value. Returns -1 for value <= 0.
   */
  public static int valueToPower(long value) {
    if (value < 1) {
      return -1;
    }
    // For each power that leaves a remainder... increment power.
    long power = -1;
    for (long remainder = value; remainder > 0; remainder >>= 1) {
      ++power;
    }
    return (int) power;
  }
}
