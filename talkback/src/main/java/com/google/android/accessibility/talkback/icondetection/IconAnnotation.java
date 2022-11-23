package com.google.android.accessibility.talkback.icondetection;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.libraries.accessibility.utils.screenunderstanding.Annotation;
import com.google.common.collect.ImmutableMap;
import com.google.protos.research.socrates.Visual.UIComponent;

/** Represents an icon identified by Screen Understanding */
public class IconAnnotation extends Annotation {

  @VisibleForTesting
  static final ImmutableMap<UIComponent.Type, Integer> ACTIONABLE_ICON_TO_LABEL_RES =
      ImmutableMap.<UIComponent.Type, Integer>builder()
          .put(UIComponent.Type.ICON_PLUS, R.string.icon_add_actionable)
          .put(UIComponent.Type.ICON_ARROW_BACKWARD, R.string.icon_back_arrow_actionable)
          .put(UIComponent.Type.ICON_ARROW_FORWARD, R.string.icon_forward_arrow_actionable)
          .put(UIComponent.Type.ICON_CALL, R.string.icon_call_actionable)
          .put(UIComponent.Type.ICON_CHAT, R.string.icon_chat_actionable)
          .put(UIComponent.Type.ICON_CHECK, R.string.icon_check_actionable)
          .put(UIComponent.Type.ICON_X, R.string.icon_close_actionable)
          .put(UIComponent.Type.ICON_DELETE, R.string.icon_delete_actionable)
          .put(UIComponent.Type.ICON_EDIT, R.string.icon_edit_actionable)
          .put(UIComponent.Type.ICON_END_CALL, R.string.icon_end_call_actionable)
          .put(UIComponent.Type.ICON_V_DOWNWARD, R.string.icon_v_downward_actionable_down)
          .put(UIComponent.Type.ICON_HEART, R.string.icon_heart_actionable)
          .put(UIComponent.Type.ICON_HOME, R.string.icon_home_actionable)
          .put(UIComponent.Type.ICON_INFO, R.string.icon_info_actionable)
          .put(UIComponent.Type.ICON_LAUNCH_APPS, R.string.icon_apps_actionable)
          .put(UIComponent.Type.ICON_THUMBS_UP, R.string.icon_heart_actionable)
          .put(UIComponent.Type.ICON_THREE_BARS, R.string.icon_menu_actionable)
          .put(UIComponent.Type.ICON_THREE_DOTS, R.string.icon_more_actionable)
          .put(UIComponent.Type.ICON_NOTIFICATIONS, R.string.icon_notifications_actionable)
          .put(UIComponent.Type.ICON_PAUSE, R.string.icon_pause_actionable)
          .put(UIComponent.Type.ICON_PLAY, R.string.icon_play_actionable)
          .put(UIComponent.Type.ICON_REFRESH, R.string.icon_refresh_actionable)
          .put(UIComponent.Type.ICON_MAGNIFYING_GLASS, R.string.icon_search_actionable)
          .put(UIComponent.Type.ICON_SEND, R.string.icon_send_actionable)
          .put(UIComponent.Type.ICON_SETTINGS, R.string.icon_settings_actionable)
          .put(UIComponent.Type.ICON_SHARE, R.string.icon_share_actionable)
          .put(UIComponent.Type.ICON_STAR, R.string.icon_star_general)
          .put(UIComponent.Type.ICON_TAKE_PHOTO, R.string.icon_take_photo_general)
          .put(UIComponent.Type.ICON_TIME, R.string.icon_time_general)
          .put(UIComponent.Type.ICON_VIDEOCAM, R.string.icon_video_general)
          .put(UIComponent.Type.ICON_EXPAND, R.string.icon_expand_actionable)
          .put(UIComponent.Type.ICON_CONTRACT, R.string.icon_contract_actionable)
          .put(UIComponent.Type.ICON_GOOGLE, R.string.icon_google_general)
          .put(UIComponent.Type.ICON_TWITTER, R.string.icon_twitter_general)
          .put(UIComponent.Type.ICON_FACEBOOK, R.string.icon_facebook_general)
          .put(UIComponent.Type.ICON_ASSISTANT, R.string.icon_assistant_general)
          .put(UIComponent.Type.ICON_QUESTION, R.string.icon_question_actionable)
          .put(UIComponent.Type.ICON_MIC, R.string.icon_mic_actionable)
          .put(UIComponent.Type.ICON_MIC_MUTE, R.string.icon_mic_mute_actionable)
          .put(UIComponent.Type.ICON_GALLERY, R.string.icon_gallery_actionable)
          .put(UIComponent.Type.ICON_COMPASS, R.string.icon_compass_general)
          .put(UIComponent.Type.ICON_PEOPLE, R.string.icon_people_general)
          .put(UIComponent.Type.ICON_PERSON, R.string.icon_person_general)
          .put(UIComponent.Type.ICON_SHOPPING_CART, R.string.icon_shopping_cart_general)
          .put(UIComponent.Type.ICON_ARROW_UPWARD, R.string.icon_up_arrow_actionable)
          .put(UIComponent.Type.ICON_ARROW_DOWNWARD, R.string.icon_down_arrow_actionable)
          .put(UIComponent.Type.ICON_ENVELOPE, R.string.icon_envelope_actionable)
          .put(UIComponent.Type.ICON_NAV_BAR_RECT, R.string.icon_nav_bar_rect_actionable)
          .put(UIComponent.Type.ICON_NAV_BAR_CIRCLE, R.string.icon_nav_bar_circle_actionable)
          .put(UIComponent.Type.ICON_CAST, R.string.icon_cast_actionable)
          .put(UIComponent.Type.ICON_VOLUME_UP, R.string.icon_volume_up_actionable)
          .put(UIComponent.Type.ICON_VOLUME_DOWN, R.string.icon_volume_down_actionable)
          .put(UIComponent.Type.ICON_VOLUME_MUTE, R.string.icon_volume_mute_actionable)
          .put(UIComponent.Type.ICON_VOLUME_STATE, R.string.icon_volume_state_actionable)
          .put(UIComponent.Type.ICON_STOP, R.string.icon_stop_actionable)
          .put(UIComponent.Type.ICON_SHOPPING_BAG, R.string.icon_shopping_bag_general)
          .put(UIComponent.Type.ICON_LIST, R.string.icon_list_actionable)
          .put(UIComponent.Type.ICON_LOCATION, R.string.icon_location_actionable)
          .put(UIComponent.Type.ICON_CALENDAR, R.string.icon_calendar_general)
          .put(UIComponent.Type.ICON_THUMBS_DOWN, R.string.icon_thumbs_down_general)
          .put(UIComponent.Type.ICON_HEADSET, R.string.icon_headset_general)
          .put(UIComponent.Type.ICON_REDO, R.string.icon_redo_actionable)
          .put(UIComponent.Type.ICON_UNDO, R.string.icon_undo_actionable)
          .put(UIComponent.Type.ICON_DOWNLOAD, R.string.icon_download_actionable)
          .put(UIComponent.Type.ICON_UPLOAD, R.string.icon_upload_actionable)
          .put(UIComponent.Type.ICON_PAPERCLIP, R.string.icon_paperclip_actionable)
          .put(UIComponent.Type.ICON_HISTORY, R.string.icon_history_actionable)
          .put(UIComponent.Type.ICON_V_UPWARD, R.string.icon_v_upward_actionable_up)
          .put(UIComponent.Type.ICON_V_FORWARD, R.string.icon_v_forward_actionable)
          .put(UIComponent.Type.ICON_V_BACKWARD, R.string.icon_v_backward_actionable)
          .put(UIComponent.Type.ICON_WEATHER, R.string.icon_weather_actionable)
          .put(UIComponent.Type.ICON_EMOJI_FACE, R.string.icon_emoji_general)
          .put(UIComponent.Type.ICON_HAPPY_FACE, R.string.icon_happy_face_general)
          .put(UIComponent.Type.ICON_SAD_FACE, R.string.icon_sad_face_general)
          .put(UIComponent.Type.ICON_MOON, R.string.icon_moon_general)
          .put(UIComponent.Type.ICON_SUN, R.string.icon_sun_general)
          .put(UIComponent.Type.ICON_CLOUD, R.string.icon_cloud_general)
          .buildOrThrow();

  @VisibleForTesting
  static final ImmutableMap<UIComponent.Type, Integer> ICON_TO_LABEL_RES =
      ImmutableMap.<UIComponent.Type, Integer>builder()
          .put(UIComponent.Type.ICON_PLUS, R.string.icon_add_non_actionable)
          .put(UIComponent.Type.ICON_ARROW_BACKWARD, R.string.icon_back_arrow_non_actionable)
          .put(UIComponent.Type.ICON_ARROW_FORWARD, R.string.icon_forward_arrow_non_actionable)
          .put(UIComponent.Type.ICON_CALL, R.string.icon_call_non_actionable)
          .put(UIComponent.Type.ICON_CHAT, R.string.icon_chat_non_actionable)
          .put(UIComponent.Type.ICON_CHECK, R.string.icon_check_non_actionable)
          .put(UIComponent.Type.ICON_X, R.string.icon_close_non_actionable)
          .put(UIComponent.Type.ICON_DELETE, R.string.icon_delete_non_actionable)
          .put(UIComponent.Type.ICON_EDIT, R.string.icon_edit_non_actionable)
          .put(UIComponent.Type.ICON_END_CALL, R.string.icon_end_call_non_actionable)
          .put(UIComponent.Type.ICON_V_DOWNWARD, R.string.icon_v_downward_non_actionable)
          .put(UIComponent.Type.ICON_HEART, R.string.icon_heart_non_actionable)
          .put(UIComponent.Type.ICON_HOME, R.string.icon_home_non_actionable)
          .put(UIComponent.Type.ICON_INFO, R.string.icon_info_non_actionable)
          .put(UIComponent.Type.ICON_LAUNCH_APPS, R.string.icon_apps_non_actionable)
          .put(UIComponent.Type.ICON_THUMBS_UP, R.string.icon_thumbs_up_general)
          .put(UIComponent.Type.ICON_THREE_BARS, R.string.icon_menu_non_actionable)
          .put(UIComponent.Type.ICON_THREE_DOTS, R.string.icon_more_non_actionable)
          .put(UIComponent.Type.ICON_NOTIFICATIONS, R.string.icon_notifications_non_actionable)
          .put(UIComponent.Type.ICON_PAUSE, R.string.icon_pause_non_actionable)
          .put(UIComponent.Type.ICON_PLAY, R.string.icon_play_non_actionable)
          .put(UIComponent.Type.ICON_REFRESH, R.string.icon_refresh_non_actionable)
          .put(UIComponent.Type.ICON_MAGNIFYING_GLASS, R.string.icon_search_non_actionable)
          .put(UIComponent.Type.ICON_SEND, R.string.icon_send_non_actionable)
          .put(UIComponent.Type.ICON_SETTINGS, R.string.icon_settings_non_actionable)
          .put(UIComponent.Type.ICON_SHARE, R.string.icon_share_non_actionable)
          .put(UIComponent.Type.ICON_STAR, R.string.icon_star_general)
          .put(UIComponent.Type.ICON_TAKE_PHOTO, R.string.icon_take_photo_general)
          .put(UIComponent.Type.ICON_TIME, R.string.icon_time_general)
          .put(UIComponent.Type.ICON_VIDEOCAM, R.string.icon_video_general)
          .put(UIComponent.Type.ICON_EXPAND, R.string.icon_expand_non_actionable)
          .put(UIComponent.Type.ICON_CONTRACT, R.string.icon_contract_non_actionable)
          .put(UIComponent.Type.ICON_GOOGLE, R.string.icon_google_general)
          .put(UIComponent.Type.ICON_TWITTER, R.string.icon_twitter_general)
          .put(UIComponent.Type.ICON_FACEBOOK, R.string.icon_facebook_general)
          .put(UIComponent.Type.ICON_ASSISTANT, R.string.icon_assistant_general)
          .put(UIComponent.Type.ICON_QUESTION, R.string.icon_question_non_actionable)
          .put(UIComponent.Type.ICON_MIC, R.string.icon_mic_non_actionable)
          .put(UIComponent.Type.ICON_MIC_MUTE, R.string.icon_mic_mute_non_actionable)
          .put(UIComponent.Type.ICON_GALLERY, R.string.icon_gallery_non_actionable)
          .put(UIComponent.Type.ICON_COMPASS, R.string.icon_compass_general)
          .put(UIComponent.Type.ICON_PEOPLE, R.string.icon_people_general)
          .put(UIComponent.Type.ICON_PERSON, R.string.icon_person_general)
          .put(UIComponent.Type.ICON_SHOPPING_CART, R.string.icon_shopping_cart_general)
          .put(UIComponent.Type.ICON_ARROW_UPWARD, R.string.icon_up_arrow_non_actionable)
          .put(UIComponent.Type.ICON_ARROW_DOWNWARD, R.string.icon_down_arrow_non_actionable)
          .put(UIComponent.Type.ICON_ENVELOPE, R.string.icon_envelope_non_actionable)
          .put(UIComponent.Type.ICON_NAV_BAR_RECT, R.string.icon_nav_bar_rect_non_actionable)
          .put(UIComponent.Type.ICON_NAV_BAR_CIRCLE, R.string.icon_nav_bar_circle_non_actionable)
          .put(UIComponent.Type.ICON_CAST, R.string.icon_cast_non_actionable)
          .put(UIComponent.Type.ICON_VOLUME_UP, R.string.icon_volume_up_non_actionable)
          .put(UIComponent.Type.ICON_VOLUME_DOWN, R.string.icon_volume_down_non_actionable)
          .put(UIComponent.Type.ICON_VOLUME_MUTE, R.string.icon_volume_mute_non_actionable)
          .put(UIComponent.Type.ICON_VOLUME_STATE, R.string.icon_volume_state_non_actionable)
          .put(UIComponent.Type.ICON_STOP, R.string.icon_stop_non_actionable)
          .put(UIComponent.Type.ICON_SHOPPING_BAG, R.string.icon_shopping_bag_general)
          .put(UIComponent.Type.ICON_LIST, R.string.icon_list_non_actionable)
          .put(UIComponent.Type.ICON_LOCATION, R.string.icon_location_non_actionable)
          .put(UIComponent.Type.ICON_CALENDAR, R.string.icon_calendar_general)
          .put(UIComponent.Type.ICON_THUMBS_DOWN, R.string.icon_thumbs_down_general)
          .put(UIComponent.Type.ICON_HEADSET, R.string.icon_headset_general)
          .put(UIComponent.Type.ICON_REDO, R.string.icon_redo_non_actionable)
          .put(UIComponent.Type.ICON_UNDO, R.string.icon_undo_non_actionable)
          .put(UIComponent.Type.ICON_DOWNLOAD, R.string.icon_download_non_actionable)
          .put(UIComponent.Type.ICON_UPLOAD, R.string.icon_upload_non_actionable)
          .put(UIComponent.Type.ICON_PAPERCLIP, R.string.icon_paperclip_non_actionable)
          .put(UIComponent.Type.ICON_HISTORY, R.string.icon_history_non_actionable)
          .put(UIComponent.Type.ICON_V_UPWARD, R.string.icon_v_upward_non_actionable)
          .put(UIComponent.Type.ICON_V_FORWARD, R.string.icon_v_forward_non_actionable)
          .put(UIComponent.Type.ICON_V_BACKWARD, R.string.icon_v_backward_non_actionable)
          .put(UIComponent.Type.ICON_WEATHER, R.string.icon_weather_non_actionable)
          .put(UIComponent.Type.ICON_EMOJI_FACE, R.string.icon_emoji_general)
          .put(UIComponent.Type.ICON_HAPPY_FACE, R.string.icon_happy_face_general)
          .put(UIComponent.Type.ICON_SAD_FACE, R.string.icon_sad_face_general)
          .put(UIComponent.Type.ICON_MOON, R.string.icon_moon_general)
          .put(UIComponent.Type.ICON_SUN, R.string.icon_sun_general)
          .put(UIComponent.Type.ICON_CLOUD, R.string.icon_cloud_general)
          .buildOrThrow();

  public IconAnnotation(Annotation annotation) {
    super(annotation);
  }

  /**
   * Returns the localized label will be spoken to a user by a screen reader to describe this icon
   * annotation. Returns {@code null} if the type of this annotation is not one of those pre-defined
   * icon types.
   *
   * @param context The {@link Context} used to retrieve the localized label
   * @param node The {@link AccessibilityNodeInfoCompat} inside which this icon annotation has been
   *     detected
   * @return The localized label
   */
  @Nullable
  public CharSequence getLabel(Context context, AccessibilityNodeInfoCompat node) {
    // TODO(b/197127371): Some icons may have different semantics in RTL languages or different
    // contexts
    boolean isActionable =
        AccessibilityNodeInfoUtils.isClickable(node)
            || AccessibilityNodeInfoUtils.isLongClickable(node);
    Integer stringResId =
        isActionable
            ? ACTIONABLE_ICON_TO_LABEL_RES.get(getType())
            : ICON_TO_LABEL_RES.get(getType());
    return (stringResId == null) ? null : context.getResources().getString(stringResId);
  }

  @Nullable
  public CharSequence getLabel(Context context) {
    // TODO(b/197127371): Some icons may have different semantics in RTL languages or different
    // contexts
    Integer stringResId = ICON_TO_LABEL_RES.get(getType());
    return (stringResId == null) ? null : context.getResources().getString(stringResId);
  }

  /** Returns {@code true} if the given annotation represents an icon. */
  public static boolean isIconAnnotation(Annotation annotation) {
    // The two maps have the same set of keys, so we only need to test one.
    return ACTIONABLE_ICON_TO_LABEL_RES.containsKey(annotation.getType());
  }
}
