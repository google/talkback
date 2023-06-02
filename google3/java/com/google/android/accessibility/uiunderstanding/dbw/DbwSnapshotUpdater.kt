package com.google.android.accessibility.uiunderstanding.dbw

import android.view.accessibility.AccessibilityEvent
import com.google.android.accessibility.uiunderstanding.Snapshot
import com.google.intelligence.dbw.androidcore.a11ycache.updater.AccessibilityTreeCacheUpdater
import com.google.intelligence.dbw.androidcore.uichangedetector.UiChangeStabilizer
import java.time.Duration
import javax.inject.Inject

/**
 * Helper function for updating snapshot via drive by wire, and
 * also listen to the newest snapshot.
 */
class DbwSnapshotUpdater @Inject constructor(
  private val cacheUpdater: AccessibilityTreeCacheUpdater,
  private val uiChangeStabilizer: UiChangeStabilizer,
) {
  companion object {
    private val STABILITY_WAIT_TIME_MS = Duration.ofMillis(100)
  }

  private var snapshotListener: SnapshotListener? = null
  private val cacheUpdateListener =
    AccessibilityTreeCacheUpdater.AccessibilityTreeCacheUpdatedListener {
      snapshotListener?.onSnapshotAvailable(SnapshotDbw(it.immutableForest, SnapshotDbwCache(it)))
    }

  private var enabled = false

  /** Listens to the newest snapshot. */
  interface SnapshotListener {
    /** New snapshot is available. */
    fun onSnapshotAvailable(snapshot: Snapshot)
  }

  /** Starting the updater. */
  fun start() {
    enabled = true
    cacheUpdater.setScreenStabilityWaitTime(STABILITY_WAIT_TIME_MS)
    cacheUpdater.addListener(cacheUpdateListener)
  }

  /** Stopping the updater. */
  fun stop() {
    enabled = false
    cacheUpdater.removeListener(cacheUpdateListener)
  }

  /** To receive events that determines whether new snapshot should be quarried or not. */
  fun onEvent(event: AccessibilityEvent) {
    if (enabled) {
      uiChangeStabilizer.onPossibleChangeToUi(event)
    }
  }

  /** Sets listener for snapshot status. */
  fun setListener(listener: SnapshotListener?) {
    snapshotListener = listener
  }
}
