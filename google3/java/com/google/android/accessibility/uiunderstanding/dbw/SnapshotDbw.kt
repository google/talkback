package com.google.android.accessibility.uiunderstanding.dbw

import com.google.android.accessibility.uiunderstanding.Snapshot
import com.google.android.accessibility.uiunderstanding.SnapshotView
import com.google.android.accessibility.uiunderstanding.SnapshotWindow
import com.google.common.collect.ImmutableList
import com.google.intelligence.dbw.android.accessibility.ImmutableAndroidAccessibilityForest

/**
 * Implementation of [Snapshot] using drive by wire [ImmutableAndroidAccessibilityForest].
 *
 * Currently [ImmutableAndroidAccessibilityForest] lacks some info to support all APIs available
 * in [Snapshot]. This class serves as an adapter allowing us to see how integration of
 * drive by wire to talkback would play out.
 *
 * This is more or less a temporary class where most implementation might move to
 * [ImmutableAndroidAccessibilityForest] in the future.
 *
 * TODO: (b/229417432) Finish unimplemented APIs once dbw supports missing info.
 */
internal class SnapshotDbw(
  private val forest: ImmutableAndroidAccessibilityForest,
  private val cache: SnapshotDbwCache,
) : Snapshot {

  private val windows: ImmutableList<SnapshotWindow>

  init {
    val builder = ImmutableList.builder<SnapshotWindow>()
    forest.trees.forEach {
      builder.add(SnapshotWindowDbw(it, this, cache))
    }
    windows = builder.build()
  }

  override fun getWindows(): ImmutableList<SnapshotWindow> = windows

  override fun contains(view: SnapshotView): Boolean {
    if (view !is SnapshotViewDbw) {
      // Drive by wire snapshot cannot contain non-drive by wire view.
      return false
    }

    val node = view.immutableNode
    val nodeSrc = cache.getInfo(node) ?: return false
    forest.trees.forEach { tree ->
      // Source check might not be necessary.
      tree.findFirst{
        it == node || cache.getInfo(it) == nodeSrc
      }?.orElse(null)?.let {
        return true
      }
    }
    return false
  }

  override fun find(target: SnapshotView?): SnapshotView? {
    // Tree cannot contain non-dbw view for now.
    return when (target) {
      is SnapshotViewDbw -> cache.find(target)
      else -> null
    }
  }
}
