package com.google.android.accessibility.talkback.training.content;

import androidx.annotation.StringRes;
import com.google.common.collect.ImmutableList;
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Text_Paragraph extends Text.Paragraph {

  private final int textResId;

  private final ImmutableList<Integer> textArgResIds;

  private final int textWithActualGestureResId;

  private final int actionKey;

  private final boolean bulletPoint;

  private final boolean subText;

  private final boolean link;

  private AutoValue_Text_Paragraph(
      int textResId,
      ImmutableList<Integer> textArgResIds,
      int textWithActualGestureResId,
      int actionKey,
      boolean bulletPoint,
      boolean subText,
      boolean link) {
    this.textResId = textResId;
    this.textArgResIds = textArgResIds;
    this.textWithActualGestureResId = textWithActualGestureResId;
    this.actionKey = actionKey;
    this.bulletPoint = bulletPoint;
    this.subText = subText;
    this.link = link;
  }

  @StringRes
  @Override
  public int textResId() {
    return textResId;
  }

  @Override
  public ImmutableList<Integer> textArgResIds() {
    return textArgResIds;
  }

  @StringRes
  @Override
  public int textWithActualGestureResId() {
    return textWithActualGestureResId;
  }

  @StringRes
  @Override
  public int actionKey() {
    return actionKey;
  }

  @Override
  public boolean bulletPoint() {
    return bulletPoint;
  }

  @Override
  public boolean subText() {
    return subText;
  }

  @Override
  public boolean link() {
    return link;
  }

  @Override
  public String toString() {
    return "Paragraph{"
        + "textResId=" + textResId + ", "
        + "textArgResIds=" + textArgResIds + ", "
        + "textWithActualGestureResId=" + textWithActualGestureResId + ", "
        + "actionKey=" + actionKey + ", "
        + "bulletPoint=" + bulletPoint + ", "
        + "subText=" + subText + ", "
        + "link=" + link
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Text.Paragraph) {
      Text.Paragraph that = (Text.Paragraph) o;
      return this.textResId == that.textResId()
          && this.textArgResIds.equals(that.textArgResIds())
          && this.textWithActualGestureResId == that.textWithActualGestureResId()
          && this.actionKey == that.actionKey()
          && this.bulletPoint == that.bulletPoint()
          && this.subText == that.subText()
          && this.link == that.link();
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= textResId;
    h$ *= 1000003;
    h$ ^= textArgResIds.hashCode();
    h$ *= 1000003;
    h$ ^= textWithActualGestureResId;
    h$ *= 1000003;
    h$ ^= actionKey;
    h$ *= 1000003;
    h$ ^= bulletPoint ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= subText ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= link ? 1231 : 1237;
    return h$;
  }

  static final class Builder extends Text.Paragraph.Builder {
    private Integer textResId;
    private ImmutableList<Integer> textArgResIds;
    private Integer textWithActualGestureResId;
    private Integer actionKey;
    private Boolean bulletPoint;
    private Boolean subText;
    private Boolean link;
    Builder() {
    }
    @Override
    public Text.Paragraph.Builder setTextResId(int textResId) {
      this.textResId = textResId;
      return this;
    }
    @Override
    public Text.Paragraph.Builder setTextArgResIds(ImmutableList<Integer> textArgResIds) {
      if (textArgResIds == null) {
        throw new NullPointerException("Null textArgResIds");
      }
      this.textArgResIds = textArgResIds;
      return this;
    }
    @Override
    public Text.Paragraph.Builder setTextWithActualGestureResId(int textWithActualGestureResId) {
      this.textWithActualGestureResId = textWithActualGestureResId;
      return this;
    }
    @Override
    public Text.Paragraph.Builder setActionKey(int actionKey) {
      this.actionKey = actionKey;
      return this;
    }
    @Override
    public Text.Paragraph.Builder setBulletPoint(boolean bulletPoint) {
      this.bulletPoint = bulletPoint;
      return this;
    }
    @Override
    public Text.Paragraph.Builder setSubText(boolean subText) {
      this.subText = subText;
      return this;
    }
    @Override
    public Text.Paragraph.Builder setLink(boolean link) {
      this.link = link;
      return this;
    }
    @Override
    Text.Paragraph autoBuild() {
      if (this.textResId == null
          || this.textArgResIds == null
          || this.textWithActualGestureResId == null
          || this.actionKey == null
          || this.bulletPoint == null
          || this.subText == null
          || this.link == null) {
        StringBuilder missing = new StringBuilder();
        if (this.textResId == null) {
          missing.append(" textResId");
        }
        if (this.textArgResIds == null) {
          missing.append(" textArgResIds");
        }
        if (this.textWithActualGestureResId == null) {
          missing.append(" textWithActualGestureResId");
        }
        if (this.actionKey == null) {
          missing.append(" actionKey");
        }
        if (this.bulletPoint == null) {
          missing.append(" bulletPoint");
        }
        if (this.subText == null) {
          missing.append(" subText");
        }
        if (this.link == null) {
          missing.append(" link");
        }
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_Text_Paragraph(
          this.textResId,
          this.textArgResIds,
          this.textWithActualGestureResId,
          this.actionKey,
          this.bulletPoint,
          this.subText,
          this.link);
    }
  }

}
