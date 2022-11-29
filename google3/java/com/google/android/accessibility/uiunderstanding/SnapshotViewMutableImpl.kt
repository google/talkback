package com.google.android.accessibility.uiunderstanding

import androidx.annotation.VisibleForTesting
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * Default implementations of [SnapshotView.Mutable]. For now, it assumes implementations of
 * [SnapshotView] to be [SnapshotViewImpl].
 *
 * It requires an implementation that allows parent/children structure to be internally mutable for
 * the purpose of [build].
 */
internal open class SnapshotViewMutableImpl : SnapshotViewImpl, SnapshotView.Mutable {

  @VisibleForTesting constructor() : super(AccessibilityNodeInfoCompat.obtain())

  // Will be used soon in subsequent cls for copying trees.
  constructor(view: SnapshotViewImpl) : super(view)

  override fun setChildren(children: List<SnapshotView>) {
    mutableChildren.clear()
    mutableChildren.addAll(children)
  }

  override fun setParent(parent: SnapshotView?) {
    mutableParent = parent
  }

  override fun setLabelFor(labelFor: SnapshotView?) {
    mutableLabelFor = labelFor
  }

  override fun setLabeledBy(labeledBy: SnapshotView?) {
    mutableLabeledBy = labeledBy
  }

  override fun setAfter(after: SnapshotView?) {
    mutableAfter = after
  }

  override fun setBefore(before: SnapshotView?) {
    mutableBefore = before
  }

  override fun setRaw(nodeInfo: AccessibilityNodeInfoCompat) {
    this.nodeInfo = nodeInfo
  }

  override fun setAnchoredWindows(windows: MutableList<SnapshotWindow>?) {
    if (windows == null) {
      mutableAnchoringWindows = null
    } else {
      mutableAnchoringWindows = mutableListOf()
      mutableAnchoringWindows?.addAll(windows)
    }
  }

  override fun setRootWindow(rootWindow: SnapshotWindow) {
    mutableRootWindow = rootWindow
  }

  override fun addChild(toAdd: SnapshotView) {
    mutableChildren.add(toAdd)
  }

  override fun replaceChild(toRemove: SnapshotView, toAdd: SnapshotView) {
    val index = mutableChildren.indexOf(toRemove)
    if (index == -1) {
      return
    }
    mutableChildren[index] = toAdd
  }
}
