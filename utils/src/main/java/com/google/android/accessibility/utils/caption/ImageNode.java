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

import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.ViewResourceName;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.auto.value.AutoValue;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/** The identifiable information of an image node and the result of image captions. */
@AutoValue
public abstract class ImageNode {
  public abstract ViewResourceName viewResourceName();

  private @Nullable Result ocrTextResult;

  private @Nullable Result detectedIconLabelResult;

  private @Nullable Result imageDescriptionResult;

  /**
   * When isValid is false, it means the view has been clicked and the icon inside the view may
   * change. When an ImageNode is not valid, the imageNode can become valid again.
   */
  private boolean isValid = true;

  /**
   * Whether the same icon label result has always been detected for this view resource name when
   * speech locale keeps unchanged. Once an ImageNode becomes unstable for icon label, this
   * ImageNode will never become stable for icon label again.
   */
  private boolean isIconLabelStable = true;

  /** Creates an instance of {@link ImageNode} without the results of image captions. */
  static @Nullable ImageNode create(AccessibilityNode node) {
    final @Nullable ViewResourceName viewResourceName = node.getPackageNameAndViewId();
    if (viewResourceName == null) {
      return null;
    }

    // The resource ID of most elements in a collection are the same, so they can't be stored.
    return new AutoValue_ImageNode(viewResourceName);
  }

  /** Returns a copy of the ImageNode-argument. */
  static ImageNode copy(ImageNode imageNode) {
    ImageNode copy = new AutoValue_ImageNode(imageNode.viewResourceName());
    copy.setOcrTextResult(imageNode.ocrTextResult);
    copy.setDetectedIconLabelResult(imageNode.detectedIconLabelResult);
    copy.setImageDescriptionResult(imageNode.imageDescriptionResult);
    copy.isValid = imageNode.isValid;
    copy.isIconLabelStable = imageNode.isIconLabelStable;
    return copy;
  }

  public @Nullable Result getOcrTextResult() {
    return ocrTextResult;
  }

  public @Nullable Result getDetectedIconLabelResult() {
    return detectedIconLabelResult;
  }

  public @Nullable Result getImageDescriptionResult() {
    return imageDescriptionResult;
  }

  public void setOcrTextResult(@Nullable Result ocrTextResult) {
    this.ocrTextResult = ocrTextResult;
  }

  public void setDetectedIconLabelResult(@Nullable Result detectedIconLabelResult) {
    this.detectedIconLabelResult = detectedIconLabelResult;
  }

  public void setImageDescriptionResult(@Nullable Result imageDescriptionResult) {
    this.imageDescriptionResult = imageDescriptionResult;
  }

  @Override
  public final boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof ImageNode)) {
      return false;
    }
    ImageNode imageNode = (ImageNode) object;
    return viewResourceName().equals(imageNode.viewResourceName())
        && Objects.equals(ocrTextResult, imageNode.getOcrTextResult())
        && Objects.equals(detectedIconLabelResult, imageNode.getDetectedIconLabelResult())
        && Objects.equals(imageDescriptionResult, imageNode.getImageDescriptionResult())
        && (isValid == imageNode.isValid)
        && (isIconLabelStable == imageNode.isIconLabelStable);
  }

  @Override
  public final int hashCode() {
    return Objects.hash(
        viewResourceName(),
        getOcrTextResult(),
        getDetectedIconLabelResult(),
        getImageDescriptionResult(),
        isValid);
  }

  @Override
  public final String toString() {
    return "ImageNode= "
        + StringBuilderUtils.joinFields(
            StringBuilderUtils.optionalSubObj("viewResourceName", viewResourceName()),
            StringBuilderUtils.optionalTag("isIconLabelStable", isIconLabelStable),
            StringBuilderUtils.optionalTag("isValid", isValid),
            StringBuilderUtils.optionalSubObj("ocrTextResult", ocrTextResult),
            StringBuilderUtils.optionalSubObj("detectedIconLabelResult", detectedIconLabelResult),
            StringBuilderUtils.optionalSubObj("imageDescriptionResult", imageDescriptionResult));
  }

  boolean isValid() {
    return isValid;
  }

  void setValid(boolean valid) {
    this.isValid = valid;
  }

  boolean isIconLabelStable() {
    return isIconLabelStable;
  }

  void setIconLabelStable(boolean stable) {
    this.isIconLabelStable = stable;
  }
}
