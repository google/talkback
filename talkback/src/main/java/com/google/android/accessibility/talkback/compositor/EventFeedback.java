/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.android.accessibility.talkback.compositor;

import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.auto.value.AutoValue;
import java.util.Optional;

/**
 * Data-structure that holds compositor event feedback output results for compositor event feedback.
 */
@AutoValue
public abstract class EventFeedback {

  public abstract Optional<CharSequence> ttsOutput();

  public abstract Integer queueMode();

  public abstract Boolean forceFeedbackEvenIfAudioPlaybackActive();

  public abstract Boolean forceFeedbackEvenIfMicrophoneActive();

  public abstract Boolean forceFeedbackEvenIfSsbActive();

  public abstract Boolean forceFeedbackEvenIfPhoneCallActive();

  public abstract Integer ttsClearQueueGroup();

  public abstract Boolean ttsInterruptSameGroup();

  public abstract Boolean ttsSkipDuplicate();

  public abstract Boolean ttsAddToHistory();

  public abstract Boolean ttsForceFeedback();

  public abstract Double ttsPitch();

  public abstract Boolean preventDeviceSleep();

  public abstract Boolean refreshSourceNode();

  public abstract Boolean advanceContinuousReading();

  public abstract Integer haptic();

  public abstract Integer earcon();

  public abstract Double earconRate();

  public abstract Double earconVolume();

  /**
   * Gets speech flag mask for the event. <strong>Note:</strong> This method doesn't handle {@link
   * FeedbackItem#FLAG_ADVANCE_CONTINUOUS_READING}, which should be handled after calling {@link
   * EventFeedback#advanceContinuousReading}.
   */
  public int getOutputSpeechFlags() {
    int flags = 0;
    if (!ttsAddToHistory()) {
      flags |= FeedbackItem.FLAG_NO_HISTORY;
    }
    if (ttsForceFeedback()) {
      flags |= FeedbackItem.FLAG_FORCE_FEEDBACK;
    }
    if (forceFeedbackEvenIfAudioPlaybackActive()) {
      flags |= FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE;
    }
    if (forceFeedbackEvenIfMicrophoneActive()) {
      flags |= FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE;
    }
    if (forceFeedbackEvenIfSsbActive()) {
      flags |= FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE;
    }
    if (forceFeedbackEvenIfPhoneCallActive()) {
      flags |= FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_PHONE_CALL_ACTIVE;
    }
    if (ttsSkipDuplicate()) {
      flags |= FeedbackItem.FLAG_SKIP_DUPLICATE;
    }
    if (ttsClearQueueGroup() != SpeechController.UTTERANCE_GROUP_DEFAULT) {
      flags |= FeedbackItem.FLAG_CLEAR_QUEUED_UTTERANCES_WITH_SAME_UTTERANCE_GROUP;
    }
    if (ttsInterruptSameGroup()) {
      flags |= FeedbackItem.FLAG_INTERRUPT_CURRENT_UTTERANCE_WITH_SAME_UTTERANCE_GROUP;
    }
    if (preventDeviceSleep()) {
      flags |= FeedbackItem.FLAG_NO_DEVICE_SLEEP;
    }

    return flags;
  }

  public String toString() {
    return StringBuilderUtils.joinFields(
        String.format("ttsOutput= %s  ", ttsOutput().orElseGet(() -> "")),
        StringBuilderUtils.optionalInt(
            "queueMode", queueMode(), SpeechController.QUEUE_MODE_INTERRUPT),
        StringBuilderUtils.optionalTag("ttsAddToHistory", ttsAddToHistory()),
        StringBuilderUtils.optionalTag(
            "forceFeedbackEvenIfAudioPlaybackActive", forceFeedbackEvenIfAudioPlaybackActive()),
        StringBuilderUtils.optionalTag(
            "forceFeedbackEvenIfMicrophoneActive", forceFeedbackEvenIfMicrophoneActive()),
        StringBuilderUtils.optionalTag(
            "forceFeedbackEvenIfSsbActive", forceFeedbackEvenIfSsbActive()),
        StringBuilderUtils.optionalTag(
            "forceFeedbackEvenIfPhoneCallActive", forceFeedbackEvenIfPhoneCallActive()),
        StringBuilderUtils.optionalTag("ttsForceFeedback", ttsForceFeedback()),
        StringBuilderUtils.optionalTag("ttsInterruptSameGroup", ttsInterruptSameGroup()),
        StringBuilderUtils.optionalInt(
            "ttsClearQueueGroup", ttsClearQueueGroup(), SpeechController.UTTERANCE_GROUP_DEFAULT),
        StringBuilderUtils.optionalTag("ttsSkipDuplicate", ttsSkipDuplicate()),
        StringBuilderUtils.optionalDouble("ttsPitch", ttsPitch(), 1.0d),
        StringBuilderUtils.optionalTag("advanceContinuousReading", advanceContinuousReading()),
        StringBuilderUtils.optionalTag("preventDeviceSleep", preventDeviceSleep()),
        StringBuilderUtils.optionalTag("refreshSourceNode", refreshSourceNode()),
        StringBuilderUtils.optionalInt("haptic", haptic(), -1),
        StringBuilderUtils.optionalInt("earcon", earcon(), -1),
        StringBuilderUtils.optionalDouble("earconRate", earconRate(), 1.0d),
        StringBuilderUtils.optionalDouble("earconVolume", earconVolume(), 1.0d));
  }

  public static EventFeedback.Builder builder() {
    return new AutoValue_EventFeedback.Builder()
        .setTtsOutput(Optional.of(""))
        .setQueueMode(SpeechController.QUEUE_MODE_INTERRUPT)
        .setTtsAddToHistory(false)
        .setForceFeedbackEvenIfAudioPlaybackActive(false)
        .setForceFeedbackEvenIfMicrophoneActive(false)
        .setForceFeedbackEvenIfSsbActive(false)
        .setForceFeedbackEvenIfPhoneCallActive(true)
        .setTtsForceFeedback(false)
        .setTtsInterruptSameGroup(false)
        .setTtsClearQueueGroup(SpeechController.UTTERANCE_GROUP_DEFAULT)
        .setTtsSkipDuplicate(false)
        .setTtsPitch(1.0d)
        .setAdvanceContinuousReading(false)
        .setPreventDeviceSleep(false)
        .setRefreshSourceNode(false)
        .setHaptic(-1)
        .setEarcon(-1)
        .setEarconRate(1.0d)
        .setEarconVolume(1.0d);
  }

  /** Builder for compositor event feedback data. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setTtsOutput(Optional<CharSequence> value);

    public abstract Builder setQueueMode(Integer value);

    public abstract Builder setForceFeedbackEvenIfAudioPlaybackActive(Boolean value);

    public abstract Builder setForceFeedbackEvenIfMicrophoneActive(Boolean value);

    public abstract Builder setForceFeedbackEvenIfSsbActive(Boolean value);

    public abstract Builder setForceFeedbackEvenIfPhoneCallActive(Boolean value);

    public abstract Builder setTtsClearQueueGroup(Integer value);

    public abstract Builder setTtsInterruptSameGroup(Boolean value);

    public abstract Builder setTtsSkipDuplicate(Boolean value);

    public abstract Builder setTtsAddToHistory(Boolean value);

    public abstract Builder setTtsForceFeedback(Boolean value);

    public abstract Builder setTtsPitch(double value);

    public abstract Builder setPreventDeviceSleep(Boolean value);

    public abstract Builder setRefreshSourceNode(Boolean value);

    public abstract Builder setAdvanceContinuousReading(Boolean value);

    public abstract Builder setHaptic(Integer value);

    public abstract Builder setEarcon(Integer value);

    public abstract Builder setEarconRate(Double value);

    public abstract Builder setEarconVolume(Double value);

    public abstract EventFeedback build();
  }
}
