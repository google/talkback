package com.google.android.accessibility.uiunderstanding.dbw

import com.google.android.accessibility.uiunderstanding.Snapshot
import com.google.android.accessibility.uiunderstanding.SnapshotView
import com.google.android.accessibility.uiunderstanding.SnapshotWindow
import com.google.intelligence.dbw.android.accessibility.ImmutableAndroidAccessibilityTree

/**
 * Implementation of [SnapshotWindow] using drive by wire [ImmutableAndroidAccessibilityTree].
 *
 * Currently [ImmutableAndroidAccessibilityTree] lacks some info to support all APIs available
 * in [SnapshotWindow]. This class serves as an adapter allowing us to see how integration of
 * drive by wire to talkback would play out.
 *
 * This is more or less a temporary class where most implementation might move to
 * [ImmutableAndroidAccessibilityTree] in the future.
 *
 * TODO: (b/229417432) Update drive by wire cache to support tree to windowInfo link.
 */
internal class SnapshotWindowDbw(
  private val tree: ImmutableAndroidAccessibilityTree,
  private val forest: Snapshot,
  private val cache: SnapshotDbwCache,
) : SnapshotWindow {

  override fun getSnapshot(): Snapshot {
    return forest
  }

  override fun getRoot(): SnapshotView? {
    val rootNode = tree.root.orElse(null) ?: return null
    return cache.getViewFromImmutableNode(rootNode)
  }
}
