/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.accessibility.uiunderstanding

import android.graphics.Rect
import android.graphics.Region
import android.view.accessibility.AccessibilityWindowInfo
import com.google.android.accessibility.utils.BuildVersionUtils

/** Default implementation of [SnapshotWindow.Mutable]. */
internal class SnapshotWindowMutableImpl(
  private var mutableSnapshot: Snapshot,
  private var windowInfo: AccessibilityWindowInfo,
  private val mutableChildren: MutableList<SnapshotWindow> = mutableListOf(),
  private var mutableParent: SnapshotWindow? = null,
  private var mutableAnchor: SnapshotView? = null,
  private var mutableRootView: SnapshotView? = null,
) : SnapshotWindow.Mutable {

  internal lateinit var mutableMetric: SnapshotWindow.WindowMetric

  constructor(
    window: SnapshotWindow
  ) : this(
    mutableSnapshot = window.snapshot,
    windowInfo =
      when (window) {
        is SnapshotWindowImpl -> window.getRaw()
        is SnapshotWindowMutableImpl -> window.getRaw()
        else -> AccessibilityWindowInfo.obtain()
      },
    mutableChildren = window.children.toMutableList(),
    mutableParent = window.parent,
    mutableAnchor = window.anchor,
    mutableRootView = window.root,
  ) {
    mutableMetric = window.metric
  }

  override fun getMetric(): SnapshotWindow.WindowMetric = mutableMetric
  override fun getSnapshot(): Snapshot = mutableSnapshot
  override fun getRoot(): SnapshotView? = mutableRootView
  override fun getAnchor(): SnapshotView? = mutableAnchor
  override fun getParent(): SnapshotWindow? = mutableParent
  override fun getChild(index: Int): SnapshotWindow? = mutableChildren.getOrNull(index)
  override fun getChildCount(): Int = mutableChildren.size
  internal fun getRaw(): AccessibilityWindowInfo = windowInfo
  override fun getId(): Int = windowInfo.id
  override fun getDisplayId(): Int =
    if (BuildVersionUtils.isAtLeastR()) windowInfo.displayId else SnapshotWindow.INVALID_DISPLAY_ID
  override fun getLayer(): Int = windowInfo.layer
  override fun getRegionInScreen(outRegion: Region) {
    if (BuildVersionUtils.isAtLeastR()) {
      windowInfo.getRegionInScreen(outRegion)
    }
  }
  override fun getType(): Int = windowInfo.type
  override fun isAccessibilityFocused(): Boolean = windowInfo.isAccessibilityFocused()
  override fun isActive(): Boolean = windowInfo.isActive()
  override fun isFocused(): Boolean = windowInfo.isFocused()
  override fun isInPictureInPictureMode(): Boolean = windowInfo.isInPictureInPictureMode()

  override fun getBoundsInScreen(outBounds: Rect) {
    windowInfo.getBoundsInScreen(outBounds)
  }

  override fun setRoot(root: SnapshotView.Mutable?) {
    mutableRootView = root
  }

  override fun setMetric(metric: SnapshotWindow.WindowMetric) {
    mutableMetric = metric
  }

  override fun setParent(parent: SnapshotWindow.Mutable?) {
    mutableParent = parent
  }

  override fun setChildren(children: MutableList<SnapshotWindow.Mutable>) {
    mutableChildren.clear()
    mutableChildren += children
  }

  override fun addChild(toAdd: SnapshotWindow.Mutable) {
    mutableChildren.add(toAdd)
  }

  override fun replaceChild(toRemove: SnapshotWindow.Mutable, toAdd: SnapshotWindow.Mutable) {
    val index = mutableChildren.indexOf(toRemove)
    if (index == -1) {
      return
    }
    mutableChildren[index] = toAdd
  }
}
