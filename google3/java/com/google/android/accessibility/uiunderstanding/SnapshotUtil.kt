package com.google.android.accessibility.uiunderstanding

/** Various utility functions used via [Snapshot] */
object SnapshotUtil {

  /**
   * Iterates [SnapshotView] in level order. To use it in java:
   * ```
   *     SnapshotView view...
   *     SnapshotUtil.levelOrder(view).next();
   * ```
   */
  @JvmStatic fun SnapshotView.levelOrder(): Iterator<SnapshotView> = LevelOrderIterator(this)

  /**
   * Iterates [SnapshotView.Mutable] in level order. To use it in java:
   * ```
   *     SnapshotView.Mutable mutableView...
   *     SnapshotUtil.levelOrder(mutableView).next();
   * ```
   */
  @JvmStatic
  fun SnapshotView.Mutable.levelOrder(): Iterator<SnapshotView.Mutable> = LevelOrderIterator(this)

  private fun findRoot(view: SnapshotView): SnapshotView = view.root ?: findHighestParent(view)

  private fun <T : Node> findHighestParent(node: T): T {
    var curr: T = node
    while (curr.parentNode != null) {
      curr = curr.parentNode as T
    }
    return curr
  }
}

/** Level order iterator for [SnapshotView] or [SnapshotWindow] */
class LevelOrderIterator<T : Node>(node: T) : Iterator<T> {
  private val queue = ArrayDeque<T>()
  init {
    queue.add(node)
  }

  override fun hasNext(): Boolean {
    return !queue.isEmpty()
  }

  override fun next(): T {
    val next = queue.removeFirst()
    for (i in 0 until next.childNodeCount) {
      queue.add(next.getChildNode(i)!! as T)
    }
    return next
  }
}
