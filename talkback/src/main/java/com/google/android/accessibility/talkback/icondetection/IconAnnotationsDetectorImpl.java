package com.google.android.accessibility.talkback.icondetection;

import android.content.Context;
import android.graphics.Rect;
import android.text.SpannableStringBuilder;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.RectUtils;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.WindowUtils;
import com.google.android.accessibility.utils.caption.ImageCaptionUtils;
import com.google.android.accessibility.utils.screenunderstanding.IconAnnotationsDetector;
import com.google.android.libraries.accessibility.utils.screenunderstanding.Annotation;
import com.google.android.libraries.accessibility.utils.screenunderstanding.ScreenAnnotationsDetectorImpl;
import com.google.common.collect.ImmutableList;
import java.util.Locale;

/**
 * An implementation of the {@link IconAnnotationsDetector} interface by extending the {@link
 * ScreenAnnotationsDetectorImpl} class.
 */
class IconAnnotationsDetectorImpl extends ScreenAnnotationsDetectorImpl
    implements IconAnnotationsDetector {

   IconAnnotationsDetectorImpl(Context context) {
      super(context);
   }

   @Nullable
   @Override
   public CharSequence getIconLabel(Locale locale, AccessibilityNodeInfoCompat node) {
      // Don't provide icon label if the given node should not be captioned
      if (!ImageCaptionUtils.isCaptionable(context, node)) {
         return null;
      }

      Rect rect = new Rect();
      node.getBoundsInScreen(rect);
      ImmutableList<Annotation> annotations =
          liveAnnotationManager.getLatestLiveAnnotationsForRect(rect);
      if (annotations.isEmpty()) {
         return null;
      }

      ImmutableList.Builder<IconAnnotation> builder = ImmutableList.builder();
      for (Annotation annotation : annotations) {
         if (IconAnnotation.isIconAnnotation(annotation)) {
            builder.add(new IconAnnotation(annotation));
         }
      }

      return getIconLabel(builder.build(), node);
   }

   @VisibleForTesting
   @Nullable
   CharSequence getIconLabel(
       ImmutableList<IconAnnotation> iconAnnotations, AccessibilityNodeInfoCompat node) {
      if (iconAnnotations.isEmpty()) {
         return null;
      }

      if (iconAnnotations.size() == 1) {
         return iconAnnotations.get(0).getLabel(context, node);
      }

      ImmutableList<IconAnnotation> sortedIconAnnotations =
          RectUtils.sortByRows(
              iconAnnotations, IconAnnotation::getBounds, WindowUtils.isScreenLayoutRTL(context));
      SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
      for (IconAnnotation iconAnnotation : sortedIconAnnotations) {
         StringBuilderUtils.appendWithSeparator(stringBuilder, iconAnnotation.getLabel(context, node));
      }
      return stringBuilder;
   }
}