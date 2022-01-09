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
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.ViewResourceName;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.auto.value.AutoValue;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The identifiable information of an image node and the result of image captions. The data will be
 * stores in the cache.
 */
@AutoValue
public abstract class ImageNode {
  public abstract ViewResourceName viewResourceName();

  private @Nullable CharSequence ocrText;

  /**
   * Creates an instance of {@link ImageNode} without the results of image captions.
   *
   * <p><strong>Note:</strong> Caller is responsible for recycling the node-argument.
   */
  @Nullable
  static ImageNode create(AccessibilityNode node) {
    @Nullable final ViewResourceName viewResourceName = node.getPackageNameAndViewId();
    if (viewResourceName == null) {
      return null;
    }

    // The resource ID of most elements in a collection are the same, so they can't be stored.
    return new AutoValue_ImageNode(viewResourceName);
  }

  /** Returns a copy of the ImageNode-argument. */
  static ImageNode copy(ImageNode imageNode) {
    ImageNode copy = new AutoValue_ImageNode(imageNode.viewResourceName());
    copy.setOcrText(imageNode.ocrText);
    return copy;
  }

  public @Nullable CharSequence getOcrText() {
    return ocrText;
  }

  public void setOcrText(@Nullable CharSequence ocrText) {
    this.ocrText = ocrText;
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
        && TextUtils.equals(ocrText, imageNode.getOcrText());
  }

  @Override
  public final int hashCode() {
    return Objects.hash(viewResourceName(), getOcrText());
  }

  @Override
  public final String toString() {
    return "ImageNode= "
        + StringBuilderUtils.joinFields(
            StringBuilderUtils.optionalSubObj("viewResourceName", viewResourceName()),
            StringBuilderUtils.optionalText("ocrText", ocrText));
  }
}
