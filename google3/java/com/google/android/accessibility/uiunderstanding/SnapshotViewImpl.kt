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
import android.os.Bundle
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.RangeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.TouchDelegateInfoCompat

/**
 * Default implementation of [SnapshotView] with all of its property mutable internally. This
 * provides a way for us an easy implementation of [SnapshotView.Mutable] as well as toMutable /
 * toImmutable function that will be implemented in the same module.
 */
internal open class SnapshotViewImpl(
  internal var nodeInfo: AccessibilityNodeInfoCompat,
  internal var mutableChildren: MutableList<SnapshotView> = mutableListOf(),
  internal var mutableParent: SnapshotView? = null,
  internal var mutableLabelFor: SnapshotView? = null,
  internal var mutableLabeledBy: SnapshotView? = null,
  internal var mutableBefore: SnapshotView? = null,
  internal var mutableAfter: SnapshotView? = null,
  internal var mutableAnchoringWindows: MutableList<SnapshotWindow>? = null,
) : SnapshotViewInternal {

  internal lateinit var mutableRootWindow: SnapshotWindow

  constructor(
    view: SnapshotViewImpl,
  ) : this(
    mutableChildren = view.children.toMutableList(),
    mutableParent = view.parent,
    nodeInfo = view.getRaw(),
    mutableLabelFor = view.labelFor,
    mutableLabeledBy = view.labeledBy,
    mutableBefore = view.before,
    mutableAfter = view.after,
    mutableAnchoringWindows =
      if (view.anchoredWindowCount == 0) null else view.anchoredWindows.toMutableList(),
  ) {
    mutableRootWindow = view.rootWindow
  }

  override fun getParent(): SnapshotView? = mutableParent
  override fun getLabelFor(): SnapshotView? = mutableLabelFor
  override fun getLabeledBy(): SnapshotView? = mutableLabeledBy
  override fun getAfter(): SnapshotView? = mutableAfter
  override fun getBefore(): SnapshotView? = mutableBefore
  override fun getChildCount(): Int = mutableChildren.size
  override fun getChild(index: Int): SnapshotView? = mutableChildren.getOrNull(index)
  override fun getAnchoredWindow(index: Int): SnapshotWindow? =
    mutableAnchoringWindows?.getOrNull(index)
  override fun getAnchoredWindowCount(): Int = mutableAnchoringWindows?.size ?: 0
  override fun getRootWindow(): SnapshotWindow = mutableRootWindow
  override fun canOpenPopup(): Boolean = nodeInfo.canOpenPopup()
  override fun getActionList(): List<AccessibilityActionCompat> =
    nodeInfo.actionList ?: mutableListOf()
  override fun getClassName(): CharSequence? = nodeInfo.className
  override fun getCollectionInfo(): AccessibilityNodeInfoCompat.CollectionInfoCompat? =
    nodeInfo.collectionInfo
  override fun getCollectionItemInfo(): AccessibilityNodeInfoCompat.CollectionItemInfoCompat? =
    nodeInfo.collectionItemInfo
  override fun getContentDescription(): CharSequence? = nodeInfo.contentDescription
  override fun getDrawingOrder(): Int = nodeInfo.drawingOrder
  override fun getError(): CharSequence? = nodeInfo.error
  override fun getHintText(): CharSequence? = nodeInfo.hintText
  override fun getInputType(): Int = nodeInfo.inputType
  override fun getLiveRegion(): Int = nodeInfo.liveRegion
  override fun getMaxTextLength(): Int = nodeInfo.maxTextLength
  override fun getMovementGranularities(): Int = nodeInfo.movementGranularities
  override fun getPackageName(): CharSequence? = nodeInfo.packageName
  override fun getPaneTitle(): CharSequence? = nodeInfo.paneTitle
  override fun getRangeInfo(): RangeInfoCompat? = nodeInfo.rangeInfo
  override fun getRoleDescription(): CharSequence? = nodeInfo.roleDescription
  override fun getStateDescription(): CharSequence? = nodeInfo.stateDescription
  override fun getText(): CharSequence? = nodeInfo.text
  override fun getTextSelectionEnd(): Int = nodeInfo.textSelectionEnd
  override fun getTextSelectionStart(): Int = nodeInfo.textSelectionStart
  override fun getTooltipText(): CharSequence? = nodeInfo.tooltipText
  override fun getTouchDelegateInfo(): TouchDelegateInfoCompat? = nodeInfo.touchDelegateInfo
  override fun getViewIdResourceName(): String? = nodeInfo.viewIdResourceName
  override fun getWindowId(): Int = nodeInfo.windowId
  override fun isAccessibilityFocused(): Boolean = nodeInfo.isAccessibilityFocused
  override fun isCheckable(): Boolean = nodeInfo.isCheckable
  override fun isChecked(): Boolean = nodeInfo.isChecked
  override fun isClickable(): Boolean = nodeInfo.isClickable
  override fun isContentInvalid(): Boolean = nodeInfo.isContentInvalid
  override fun isContextClickable(): Boolean = nodeInfo.isContextClickable
  override fun isDismissable(): Boolean = nodeInfo.isDismissable
  override fun isEditable(): Boolean = nodeInfo.isEditable
  override fun isEnabled(): Boolean = nodeInfo.isEnabled
  override fun isFocusable(): Boolean = nodeInfo.isFocusable
  override fun isFocused(): Boolean = nodeInfo.isFocused
  override fun isHeading(): Boolean = nodeInfo.isHeading
  override fun isImportantForAccessibility(): Boolean = nodeInfo.isImportantForAccessibility
  override fun isLongClickable(): Boolean = nodeInfo.isLongClickable
  override fun isMultiLine(): Boolean = nodeInfo.isMultiLine
  override fun isPassword(): Boolean = nodeInfo.isPassword
  override fun isScreenReaderFocusable(): Boolean = nodeInfo.isScreenReaderFocusable
  override fun isScrollable(): Boolean = nodeInfo.isScrollable
  override fun isSelected(): Boolean = nodeInfo.isSelected
  override fun isShowingHintText(): Boolean = nodeInfo.isShowingHintText
  override fun isTextEntryKey(): Boolean = nodeInfo.isTextEntryKey
  override fun isVisibleToUser(): Boolean = nodeInfo.isVisibleToUser

  override fun refresh(): SnapshotView? {
    return getRootWindow()?.getSnapshot()?.getEventSource()?.refresh(this)
  }

  // in Kotlin, operator == is the same as Object.equals()
  override fun equalsRaw(other: AccessibilityNodeInfoCompat): Boolean = (nodeInfo == other)

  fun getRaw(): AccessibilityNodeInfoCompat = nodeInfo
  override fun refreshRawNode(): Boolean = nodeInfo.refresh()
  override fun performActionOnRawNode(actionId: Int, bundle: Bundle?): Boolean =
    nodeInfo.performAction(actionId, bundle)

  override fun getBoundsInScreen(outBounds: Rect) {
    nodeInfo.getBoundsInScreen(outBounds)
  }
}
