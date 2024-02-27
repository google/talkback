/*
 * Copyright (C) 2021 Google Inc.
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

package com.google.android.accessibility.utils.caption;

import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.ViewResourceName;
import com.google.android.accessibility.utils.screenunderstanding.IconAnnotationsDetector;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.collect.Maps;
import java.util.HashMap;
import java.util.Locale;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Stores and retrieves image caption results. */
public class ImageCaptionStorage {

  private static final String TAG = "ImageCaptionStorage";
  private static final int RESULT_CAPACITY = 500;

  private final LimitedCapacityCache imageNodes;
  private @MonotonicNonNull IconAnnotationsDetector iconAnnotationsDetector;

  public ImageCaptionStorage() {
    this(RESULT_CAPACITY);
  }

  @VisibleForTesting
  public ImageCaptionStorage(int capacity) {
    imageNodes = new LimitedCapacityCache(capacity);
  }

  @VisibleForTesting
  public int getImageNodeSize() {
    return imageNodes.size();
  }

  /** Removes all cached {@link ImageNode}s. */
  public void clearImageNodesCache() {
    imageNodes.clear();
  }

  /** Sets the {@link IconAnnotationsDetector} for retrieving labels of detected icons. */
  public void setIconAnnotationsDetector(IconAnnotationsDetector iconAnnotationsDetector) {
    this.iconAnnotationsDetector = iconAnnotationsDetector;
  }

  /** Retrieves the localized label of the detected icon which matches the specified node. */
  @Nullable
  public CharSequence getDetectedIconLabel(Locale locale, AccessibilityNodeInfoCompat node) {
    return (iconAnnotationsDetector == null)
        ? null
        : iconAnnotationsDetector.getIconLabel(locale, node);
  }

  /** Retrieves image caption results for the specified node. */
  @Nullable
  public ImageNode getCaptionResults(AccessibilityNodeInfoCompat node) {
    AccessibilityNode wrapNode = AccessibilityNode.takeOwnership(node);
    @Nullable ImageNode imageNode = findImageNode(wrapNode);
    if (imageNode == null || !imageNode.isIconLabelStable() || !imageNode.isValid()) {
      return null;
    }
    return imageNode;
  }

  /** Stores the OCR result for the specified node in the cache. */
  public void updateCharacterCaptionResult(AccessibilityNode node, Result result) {
    if (!ImageCaptionStorage.isStorable(node) || Result.isEmpty(result)) {
      LogUtils.v(TAG, "Character caption result (" + result + ") should not be stored.");
      return;
    }

    // Always creating a new ImageNode here to avoid searching twice. Because it's necessary to find
    // the ImageNode in LimitedCapacityCache.put().
    @Nullable ImageNode imageNode = ImageNode.create(node);
    if (imageNode == null) {
      return;
    }
    imageNode.setOcrTextResult(result);
    imageNodes.put(imageNode);
  }

  /** Stores the label of the detected icons for the specified node in the cache. */
  public void updateDetectedIconLabel(AccessibilityNode node, Result result) {
    if (!ImageCaptionStorage.isStorable(node) || Result.isEmpty(result)) {
      LogUtils.v(TAG, "DetectedIconLabel (" + result + ") should not be stored.");
      return;
    }

    @Nullable ImageNode imageNode = ImageNode.create(node);
    if (imageNode == null) {
      return;
    }
    imageNode.setDetectedIconLabelResult(result);
    imageNodes.put(imageNode);
  }

  /** Stores the image description result for the specified node in the cache. */
  public void updateImageDescriptionResult(AccessibilityNode node, Result result) {
    if (!ImageCaptionStorage.isStorable(node) || Result.isEmpty(result)) {
      LogUtils.v(TAG, "Image Description result (" + result + ") should not be stored.");
      return;
    }

    // Always creating a new ImageNode here to avoid searching twice. Because it's necessary to find
    // the ImageNode in LimitedCapacityCache.put().
    @Nullable ImageNode imageNode = ImageNode.create(node);
    if (imageNode == null) {
      return;
    }
    imageNode.setImageDescriptionResult(result);
    imageNodes.put(imageNode);
  }

  /**
   * Marks the OCR text, the detected icon label and image description for the specific node as
   * invalid in the cache.
   */
  public void invalidateCaptionForNode(AccessibilityNode node) {
    if (!ImageCaptionStorage.isStorable(node)) {
      return;
    }

    @Nullable final ViewResourceName viewResourceName = node.getPackageNameAndViewId();
    if (viewResourceName != null) {
      imageNodes.invalidateImageNode(viewResourceName);
    }
  }

  /** Checks if node has a resource name with a package name and is not in the collection. */
  public static boolean isStorable(AccessibilityNode node) {
    @Nullable final ViewResourceName viewResourceName = node.getPackageNameAndViewId();
    return viewResourceName != null
        // The resource ID of most elements in a collection are the same, so they can't be stored.
        && !node.isInCollection();
  }

  /**
   * Retrieves the related {@link ImageNode} for the specified node. The returned ImageNode will be
   * regarded as the newest element.
   */
  @Nullable
  private ImageNode findImageNode(AccessibilityNode node) {
    if (!isStorable(node)) {
      return null;
    }

    @Nullable final ViewResourceName viewResourceName = node.getPackageNameAndViewId();
    if (viewResourceName == null) {
      return null;
    }

    return imageNodes.get(viewResourceName);
  }

  /**
   * A limited capacity cache for storing {@link ImageNode}s. ImageNodes are stored in a Map to
   * quickly search via the key which includes packageName and viewName. Key nodes sort in the order
   * of inserted time.
   */
  private static final class LimitedCapacityCache {
    private final int capacity;

    /** The key node for the first inserted ImageNode. */
    private Node<ViewResourceName> firstOldestKey = null;

    /** The key node for the last inserted ImageNode. */
    private Node<ViewResourceName> lastNewestKey = null;

    private final HashMap<ViewResourceName, ImageAndListNode> imageNodes;

    public LimitedCapacityCache(int capacity) {
      this.capacity = capacity;
      this.imageNodes = Maps.newHashMapWithExpectedSize(capacity);
    }

    /** Removes all {@link ImageNode}s in the cache. */
    public synchronized void clear() {
      imageNodes.clear();
    }

    /**
     * Finds the ImageNode by its view resource name and sets the ImageNode to invalid when the
     * ImageNode is not {@code null}.
     */
    public synchronized void invalidateImageNode(ViewResourceName viewResourceName) {
      ImageAndListNode imageAndKeyNode = imageNodes.get(viewResourceName);
      if (imageAndKeyNode != null) {
        imageAndKeyNode.imageNode.setValid(false);
      }
    }

    /**
     * Returns a copy of ImageNode which has the same view resource name as input-arguments. The
     * returned ImageNode will be regarded as the newest element.
     */
    @Nullable
    private synchronized ImageNode get(ViewResourceName viewResourceName) {
      ImageAndListNode imageAndKeyNode = imageNodes.get(viewResourceName);
      if (imageAndKeyNode == null) {
        return null;
      }
      moveToLast(imageAndKeyNode);
      // Returns a copy to prevent the data in the cache being changed by the outer class directly.
      return ImageNode.copy(imageAndKeyNode.imageNode);
    }

    /** Adds the specified ImageNode and its key to the cache. */
    private synchronized void add(ImageNode imageNode) {
      // Removes the oldest ImageNode because cache is full.
      while (imageNodes.size() >= capacity) {
        // Removes the first inserted key.
        Node<ViewResourceName> oldestNode = firstOldestKey;
        firstOldestKey = oldestNode.next;
        oldestNode.unlink();

        LogUtils.v(TAG, "add() cache is full, remove " + oldestNode.data);
        imageNodes.remove(oldestNode.data);
      }

      // Add a key.
      ViewResourceName viewResourceName = imageNode.viewResourceName();
      Node<ViewResourceName> keyNode = new Node<>(viewResourceName);
      if (firstOldestKey == null) {
        firstOldestKey = keyNode;
      } else {
        lastNewestKey.insertNextNode(keyNode);
      }
      lastNewestKey = keyNode;

      LogUtils.v(TAG, "add() " + imageNode);
      imageNodes.put(viewResourceName, new ImageAndListNode(keyNode, imageNode));
    }

    /** Replaces the specified ImageNode and moves the corresponding key to last / newest. */
    public synchronized void put(ImageNode imageNode) {
      if (firstOldestKey == null) {
        add(imageNode);
        return;
      }

      // Checks if the specified ImageNode exists.
      ViewResourceName viewResourceName = imageNode.viewResourceName();
      ImageAndListNode oldImage = imageNodes.get(viewResourceName);
      if (oldImage == null) {
        add(imageNode);
        return;
      }

      moveToLast(oldImage);
      if (!oldImage.imageNode.isIconLabelStable()) {
        return;
      }

      LogUtils.v(TAG, "put() " + imageNode);
      if (!Result.isEmpty(imageNode.getOcrTextResult())) {
        oldImage.imageNode.setValid(true);
        oldImage.imageNode.setOcrTextResult(imageNode.getOcrTextResult());
      }
      if (!Result.isEmpty(imageNode.getDetectedIconLabelResult())) {
        // Checks whether detected icon labels are different for the same view id
        Result oldIconLabelResult = oldImage.imageNode.getDetectedIconLabelResult();
        if ((oldIconLabelResult != null)
            && !TextUtils.equals(
                oldIconLabelResult.text(), imageNode.getDetectedIconLabelResult().text())) {
          oldImage.imageNode.setIconLabelStable(false);
          return;
        }
        oldImage.imageNode.setValid(true);
        oldImage.imageNode.setDetectedIconLabelResult(imageNode.getDetectedIconLabelResult());
      }
      if (!Result.isEmpty(imageNode.getImageDescriptionResult())) {
        oldImage.imageNode.setValid(true);
        oldImage.imageNode.setImageDescriptionResult(imageNode.getImageDescriptionResult());
      }
    }

    /** Moves the specified keyNode to last / newest. */
    private synchronized void moveToLast(ImageAndListNode imageAndListNode) {
      Node<ViewResourceName> keyNode = imageAndListNode.keyNode;
      if (imageNodes.size() == 1 || keyNode == lastNewestKey) {
        return;
      }
      if (keyNode == firstOldestKey) {
        firstOldestKey = keyNode.next;
      }
      keyNode.unlink();
      lastNewestKey.insertNextNode(keyNode);
      lastNewestKey = keyNode;
    }

    /** The number of ImageNode in the cache. */
    public synchronized int size() {
      return imageNodes.size();
    }
  }

  private static class Node<E> {
    @Nullable private Node<E> previous;
    private final E data;
    @Nullable private Node<E> next;

    private Node(E data) {
      this.data = data;
    }

    private void unlink() {
      if (previous != null) {
        previous.next = next;
      }
      if (next != null) {
        next.previous = previous;
      }
      previous = null;
      next = null;
    }

    private void insertNextNode(Node<E> newNextNode) {
      Node<E> oldNextNode = next;
      next = newNextNode;
      newNextNode.previous = this;
      newNextNode.next = oldNextNode;
      if (oldNextNode != null) {
        oldNextNode.previous = newNextNode;
      }
    }
  }

  /** Stores ImageNode and a reference to the corresponding key node for quick removal. */
  private static class ImageAndListNode {
    private final Node<ViewResourceName> keyNode;

    private final ImageNode imageNode;

    private ImageAndListNode(Node<ViewResourceName> keyNode, ImageNode imageNode) {
      this.keyNode = keyNode;
      this.imageNode = imageNode;
    }
  }
}
