package com.google.android.accessibility.uiunderstanding.dbw

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.accessibility.uiunderstanding.SnapshotView
import com.google.intelligence.dbw.android.accessibility.ImmutableAndroidAccessibilityNode
import com.google.intelligence.dbw.androidcore.a11ycache.AccessibilityTreeCache

/**
 * Cache used for creating dbw implementation. It works as a directional hashmap between
 * [ImmutableAndroidAccessibilityNode], [SnapshotView] and [AccessibilityNodeInfoCompat].
 *
 * These implementations are temporary to support existing [ImmutableAndroidAccessibilityNode].
 */
internal class SnapshotDbwCache (
  private val treeCache: AccessibilityTreeCache
) {
  private var immutableNodeToView: MutableMap<ImmutableAndroidAccessibilityNode, SnapshotView>? =
    null

  /** Retrieves [SnapshotView] from [ImmutableAndroidAccessibilityNode]. */
  fun getViewFromImmutableNode(node: ImmutableAndroidAccessibilityNode): SnapshotView {
    if (immutableNodeToView == null) {
      immutableNodeToView = HashMap()
    }

    immutableNodeToView!![node]?.let { return it }

    val toReturn = SnapshotViewDbw(node, this)
    immutableNodeToView!![node] = toReturn
    return toReturn
  }

  /** Retrieves [AccessibilityNodeInfoCompat] from [ImmutableAndroidAccessibilityNode]. */
  fun getInfo(node: ImmutableAndroidAccessibilityNode): AccessibilityNodeInfoCompat? {
    return treeCache.getSourceNode(node.toProto())?.orElse(null)
  }

  /** Retrieves [SnapshotView] from [AccessibilityNodeInfoCompat]. */
  fun getViewFromInfo(src: AccessibilityNodeInfoCompat): SnapshotView? {
    // TODO use findNode instead.
    val node = treeCache.findImmutableNode(src)?.orElse(null) ?: return null
    return getViewFromImmutableNode(node)
  }

  /** Searches the cache to see if matching view exists in the tree. Return null otherwise. */
  fun find(view: SnapshotViewDbw): SnapshotView? {
    val nodeInfo = getInfo(view.immutableNode)
    val found = treeCache.findImmutableNode(nodeInfo).orElse(null) ?: return null
    return getViewFromImmutableNode(found)
  }
}
