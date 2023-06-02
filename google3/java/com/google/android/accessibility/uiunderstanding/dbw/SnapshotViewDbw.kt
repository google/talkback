package com.google.android.accessibility.uiunderstanding.dbw

import android.graphics.Rect
import android.os.Bundle
import android.text.InputType
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.accessibility.uiunderstanding.Snapshot
import com.google.android.accessibility.uiunderstanding.SnapshotView
import com.google.android.accessibility.uiunderstanding.SnapshotWindow
import com.google.intelligence.dbw.android.accessibility.ImmutableAndroidAccessibilityNode
import java.util.Optional

/**
 * Implementation of [SnapshotView] using drive by wire [ImmutableAndroidAccessibilityNode].
 *
 * Currently [ImmutableAndroidAccessibilityNode] lacks some info to support all APIs available
 * in [SnapshotView]. This class serves as an adapter allowing us to see how integration of
 * drive by wire to talkback would play out.
 *
 * This is more or less a temporary class where most implementation might move to
 * [ImmutableAndroidAccessibilityNode] in the future.
 *
 * TODO: (b/229417432) Finish unimplemented APIs once dbw supports windowInfo link.
 */
internal class SnapshotViewDbw(
  val immutableNode: ImmutableAndroidAccessibilityNode,
  private val cache: SnapshotDbwCache,
) : SnapshotView{

  override fun getChild(index: Int): SnapshotView? {
    return try {
      cache.getViewFromImmutableNode(immutableNode.getChildOrThrow(index))
    } catch (ie: IndexOutOfBoundsException) {
      null
    }
  }

  override fun getRoot(): SnapshotView? {
    val visited = mutableSetOf<SnapshotView>()
    var curr: SnapshotView = this
    while (curr.parent != null) {
      if (visited.contains(curr.parent)) {
        // Loop detected.
        return null
      }

      curr = curr.parent!!
      visited.add(curr)
    }
    return curr
  }

  override fun getBoundsInScreen(outBounds: Rect) {
    val dbwRect = immutableNode.boundsInScreen
    outBounds.left = dbwRect.left()
    outBounds.right = dbwRect.right()
    outBounds.top = dbwRect.top()
    outBounds.bottom = dbwRect.bottom()
  }

  override fun getChildCount(): Int = immutableNode.childCount
  override fun getText(): CharSequence? = immutableNode.text
  override fun getClassName(): CharSequence? = immutableNode.className
  override fun getContentDescription(): CharSequence? = immutableNode.contentDescription
  override fun getHintText(): CharSequence? = immutableNode.hintText
  override fun getPackageName(): CharSequence? = immutableNode.packageName
  override fun getParent(): SnapshotView? = getView{ immutableNode.parent }
  override fun getLabelFor(): SnapshotView? = getView { immutableNode.labelFor }
  override fun getLabeledBy(): SnapshotView? = getView { immutableNode.labeledBy }
  override fun getAfter(): SnapshotView? = getNeighborView { it.traversalAfter }
  override fun getBefore(): SnapshotView? = getNeighborView { it.traversalBefore }
  override fun performAction(action: Int): Boolean =
    getInfoProperty(false) { it.performAction(action) }
  override fun performAction(action: Int, bundle: Bundle): Boolean =
    getInfoProperty(false) { it.performAction(action, bundle) }
  override fun equalsRaw(other: AccessibilityNodeInfoCompat): Boolean =
    getInfoProperty(false) { it == other }
  override fun canOpenPopup(): Boolean = getInfoProperty(false) { it.canOpenPopup() }
  override fun getDrawingOrder(): Int = getInfoProperty(0) { it.drawingOrder }
  override fun getError(): CharSequence? = getInfoProperty(null) { it.error }
  override fun getCollectionInfo(): AccessibilityNodeInfoCompat.CollectionInfoCompat? =
    getInfoProperty(null) { it.collectionInfo }
  override fun getActionList(): List<AccessibilityNodeInfoCompat.AccessibilityActionCompat> =
    getInfoProperty(listOf()) { it.actionList }
  override fun getCollectionItemInfo(): AccessibilityNodeInfoCompat.CollectionItemInfoCompat? =
    getInfoProperty(null) { it.collectionItemInfo }
  override fun getInputType(): Int = getInfoProperty(InputType.TYPE_NULL) { it.inputType }
  override fun getLiveRegion(): Int =
    getInfoProperty(ViewCompat.ACCESSIBILITY_LIVE_REGION_NONE) { it.liveRegion }
  override fun getMaxTextLength(): Int = getInfoProperty(-1) { it.maxTextLength }
  override fun getMovementGranularities(): Int = getInfoProperty(0) { it.movementGranularities }
  override fun getPaneTitle(): CharSequence? = getInfoProperty(null) { it.paneTitle }
  override fun getRangeInfo(): AccessibilityNodeInfoCompat.RangeInfoCompat? =
    getInfoProperty(null) { it.rangeInfo }
  override fun getRoleDescription(): CharSequence? = getInfoProperty(null) { it.roleDescription }
  override fun getStateDescription(): CharSequence? =
    getInfoProperty(null) { it.stateDescription }
  override fun getTextSelectionEnd(): Int = getInfoProperty(-1) { it.textSelectionEnd }
  override fun getTextSelectionStart(): Int = getInfoProperty(-1) { it.textSelectionStart }
  override fun getTooltipText(): CharSequence? = getInfoProperty(null) { it.tooltipText }
  override fun getTouchDelegateInfo(): AccessibilityNodeInfoCompat.TouchDelegateInfoCompat? =
    getInfoProperty(null) { it.touchDelegateInfo }
  override fun getViewIdResourceName(): String? = immutableNode.viewIdResourceName
  override fun getWindowId(): Int = getInfoProperty(-1) { it.windowId }
  override fun isAccessibilityFocused(): Boolean =
    getInfoProperty(false) { it.isAccessibilityFocused }
  override fun isCheckable(): Boolean = immutableNode.isCheckable
  override fun isChecked(): Boolean = immutableNode.isChecked
  override fun isClickable(): Boolean = immutableNode.isClickable
  override fun isContentInvalid(): Boolean = getInfoProperty(false) { it.isContentInvalid }
  override fun isContextClickable(): Boolean = getInfoProperty(false) { it.isContextClickable }
  override fun isDismissable(): Boolean = getInfoProperty(false) { it.isDismissable }
  override fun isEditable(): Boolean = immutableNode.isEditable
  override fun isEnabled(): Boolean = immutableNode.isEnabled
  override fun isFocusable(): Boolean = immutableNode.isFocusable
  override fun isFocused(): Boolean = immutableNode.isFocused
  override fun isHeading(): Boolean = getInfoProperty(false) { it.isHeading }
  override fun isImportantForAccessibility(): Boolean =
    getInfoProperty(true) { it.isImportantForAccessibility }
  override fun isLongClickable(): Boolean = immutableNode.isLongClickable
  override fun isMultiLine(): Boolean = getInfoProperty(false) { it.isMultiLine }
  override fun isPassword(): Boolean = immutableNode.isPassword
  override fun isScreenReaderFocusable(): Boolean =
    getInfoProperty(false) { it.isScreenReaderFocusable }
  override fun isScrollable(): Boolean = immutableNode.isScrollable
  override fun isSelected(): Boolean = immutableNode.isSelected
  override fun isShowingHintText(): Boolean = getInfoProperty(false) { it.isShowingHintText }
  override fun isTextEntryKey(): Boolean = getInfoProperty(false) { it.isTextEntryKey }
  override fun isVisibleToUser(): Boolean = immutableNode.isVisibleToUser

  override fun getRootWindow(): SnapshotWindow {
    // TODO:b/229417432 implement this methods.
    return SnapshotWindow { object : Snapshot { } }
  }

  private fun getView(input: () -> Optional<ImmutableAndroidAccessibilityNode>): SnapshotView? {
    val node = input.invoke().orElse(null)
    return if (node == null) {
      null
    } else {
      cache.getViewFromImmutableNode(node)
    }
  }

  /**
   * Returns [SnapshotView] that represents nodeInfo that input param [getNeighborFromInfo]
   * points to.
   */
  private fun getNeighborView(
    getNeighborFromInfo: (AccessibilityNodeInfoCompat) -> AccessibilityNodeInfoCompat?,
  ): SnapshotView? {

    val srcNodeInfo = cache.getInfo(immutableNode) ?: return null
    val nextNode = getNeighborFromInfo.invoke(srcNodeInfo) ?: return null
    return cache.getViewFromInfo(nextNode)
  }

  /**
   * Returns the property that input param [getPropertyFromInfo] points to.
   */
  private fun <T> getInfoProperty(
    defaultValue: T,
    getPropertyFromInfo: (AccessibilityNodeInfoCompat) -> T,
  ): T {
    val src = cache.getInfo(immutableNode) ?: return defaultValue
    return getPropertyFromInfo.invoke(src)
  }
}
