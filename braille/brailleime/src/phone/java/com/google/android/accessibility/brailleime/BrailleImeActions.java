package com.google.android.accessibility.brailleime;

import static androidx.core.content.res.ResourcesCompat.ID_NULL;
import static com.google.android.accessibility.brailleime.BrailleImeActions.Category.BASIC;
import static com.google.android.accessibility.brailleime.BrailleImeActions.Category.CURSOR_MOVEMENT;
import static com.google.android.accessibility.brailleime.BrailleImeActions.Category.SPELL_CHECK;
import static com.google.android.accessibility.brailleime.BrailleImeActions.Category.TEXT_SELECTION_AND_EDITING;
import static com.google.android.accessibility.brailleime.BrailleImeActions.SubCategory.CHARACTER;
import static com.google.android.accessibility.brailleime.BrailleImeActions.SubCategory.EDITING;
import static com.google.android.accessibility.brailleime.BrailleImeActions.SubCategory.GRANULARITY;
import static com.google.android.accessibility.brailleime.BrailleImeActions.SubCategory.LINE;
import static com.google.android.accessibility.brailleime.BrailleImeActions.SubCategory.NONE;
import static com.google.android.accessibility.brailleime.BrailleImeActions.SubCategory.PLACE_ON_PAGE;
import static com.google.android.accessibility.brailleime.BrailleImeActions.SubCategory.WORD;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Braille keyboard actions. */
public enum BrailleImeActions {
  ADD_SPACE(BASIC, R.string.bk_gesture_add_space, R.drawable.ic_one_finger_right),
  DELETE_CHARACTER(BASIC, R.string.bk_gesture_delete, R.drawable.ic_one_finger_left),
  ADD_NEWLINE(BASIC, R.string.bk_gesture_new_line, R.drawable.ic_two_fingers_right),
  DELETE_WORD(BASIC, R.string.bk_gesture_delete_word, R.drawable.ic_two_fingers_left),
  MOVE_CURSOR_BACKWARD(
      BASIC, R.string.bk_gesture_move_cursor_backward, R.drawable.ic_one_finger_up),
  MOVE_CURSOR_FORWARD(
      BASIC, R.string.bk_gesture_move_cursor_forward, R.drawable.ic_one_finger_down),
  HIDE_KEYBOARD(BASIC, R.string.bk_gesture_hide_the_keyboard, R.drawable.ic_two_fingers_down),
  SWITCH_KEYBOARD(
      BASIC, R.string.bk_gesture_switch_to_next_keyboard, R.drawable.ic_three_fingers_down),
  SUBMIT_TEXT(BASIC, R.string.bk_gesture_submit_text, R.drawable.ic_two_fingers_up),
  HELP_AND_OTHER_ACTIONS(
      BASIC, R.string.bk_gesture_help_and_other_options, R.drawable.ic_three_fingers_up),
  PREVIOUS_CHARACTER(CURSOR_MOVEMENT, CHARACTER, R.string.bk_gesture_move_to_previous_character),
  NEXT_CHARACTER(CURSOR_MOVEMENT, CHARACTER, R.string.bk_gesture_move_to_next_character),
  PREVIOUS_WORD(CURSOR_MOVEMENT, WORD, R.string.bk_gesture_move_to_previous_word),
  NEXT_WORD(CURSOR_MOVEMENT, WORD, R.string.bk_gesture_move_to_next_word),
  PREVIOUS_LINE(CURSOR_MOVEMENT, LINE, R.string.bk_gesture_move_to_previous_line),
  NEXT_LINE(CURSOR_MOVEMENT, LINE, R.string.bk_gesture_move_to_next_line),
  BEGINNING_OF_PAGE(CURSOR_MOVEMENT, PLACE_ON_PAGE, R.string.bk_gesture_move_to_beginning),
  END_OF_PAGE(CURSOR_MOVEMENT, PLACE_ON_PAGE, R.string.bk_gesture_move_to_end),
  PREVIOUS_GRANULARITY(
      CURSOR_MOVEMENT, GRANULARITY, R.string.bk_gesture_switch_to_previous_granularity),
  NEXT_GRANULARITY(CURSOR_MOVEMENT, GRANULARITY, R.string.bk_gesture_switch_to_next_granularity),
  PREVIOUS_ITEM(CURSOR_MOVEMENT, GRANULARITY, R.string.bk_gesture_moveh_to_previous_item),
  NEXT_ITEM(CURSOR_MOVEMENT, GRANULARITY, R.string.bk_gesture_move_to_next_item),
  SELECT_PREVIOUS_CHARACTER(
      TEXT_SELECTION_AND_EDITING, CHARACTER, R.string.bk_gesture_select_previous_character),
  SELECT_NEXT_CHARACTER(
      TEXT_SELECTION_AND_EDITING, CHARACTER, R.string.bk_gesture_select_next_character),
  SELECT_PREVIOUS_WORD(TEXT_SELECTION_AND_EDITING, WORD, R.string.bk_gesture_select_previous_word),
  SELECT_NEXT_WORD(TEXT_SELECTION_AND_EDITING, WORD, R.string.bk_gesture_select_next_word),
  SELECT_PREVIOUS_LINE(TEXT_SELECTION_AND_EDITING, LINE, R.string.bk_gesture_select_previous_line),
  SELECT_NEXT_LINE(TEXT_SELECTION_AND_EDITING, LINE, R.string.bk_gesture_select_next_line),
  SELECT_ALL(TEXT_SELECTION_AND_EDITING, EDITING, R.string.bk_gesture_select_all),
  COPY(TEXT_SELECTION_AND_EDITING, EDITING, R.string.bk_gesture_copy),
  CUT(TEXT_SELECTION_AND_EDITING, EDITING, R.string.bk_gesture_cut),
  PASTE(TEXT_SELECTION_AND_EDITING, EDITING, R.string.bk_gesture_paste),
  PREVIOUS_MISSPELLED_WORD(SPELL_CHECK, R.string.bk_gesture_previous_misspelled_word),
  NEXT_MISSPELLED_WORD(SPELL_CHECK, R.string.bk_gesture_next_misspelled_word),
  HEAR_PREVIOUS_SPELLING_SUGGESTION(SPELL_CHECK, R.string.bk_gesture_previous_suggestion),
  HEAR_NEXT_SPELLING_SUGGESTION(SPELL_CHECK, R.string.bk_gesture_next_suggestion),
  CONFIRM_SPELLING_SUGGESTION(SPELL_CHECK, R.string.bk_gesture_confirm_spelling_suggestion),
  UNDO_SPELLING_SUGGESTION(SPELL_CHECK, R.string.bk_gesture_undo_spelling_suggestion),
  ;

  /** {@link BrailleImeActions} category. */
  public enum Category {
    BASIC(R.string.braille_keyboard_basic_controls, NONE),
    CURSOR_MOVEMENT(
        R.string.braille_keyboard_cursor_movement,
        R.string.braille_keyboard_cursor_movement_description,
        GRANULARITY,
        CHARACTER,
        WORD,
        LINE,
        PLACE_ON_PAGE),
    TEXT_SELECTION_AND_EDITING(
        R.string.braille_keyboard_text_selection_and_editing, CHARACTER, WORD, LINE, EDITING),
    SPELL_CHECK(
        R.string.braille_keyboard_spell_check,
        R.string.braille_keyboard_spell_check_description,
        NONE),
    ;
    @StringRes private final int titleRes;
    @StringRes private final int descriptionRes;
    private final ImmutableList<SubCategory> subCategoryList;

    Category(@StringRes int titleRes, @StringRes int descriptionRes, SubCategory... subCategories) {
      subCategoryList = ImmutableList.copyOf(Arrays.asList(subCategories));
      this.titleRes = titleRes;
      this.descriptionRes = descriptionRes;
    }

    Category(@StringRes int titleRes, SubCategory... subCategories) {
      this(titleRes, ID_NULL, subCategories);
    }

    /** Gets the {@link SubCategory} list belong to the {@link Category}. */
    public List<SubCategory> getSubCategories() {
      return new ArrayList<>(subCategoryList);
    }

    /** Gets the title of the {@link Category}. */
    public String getTitle(Resources resources) {
      return resources.getString(titleRes);
    }

    /** Gets the description of the {@link Category}. */
    public String getDescription(Resources resources) {
      return descriptionRes == ID_NULL ? "" : resources.getString(descriptionRes);
    }
  }

  /** {@link BrailleImeActions} sub-category. */
  public enum SubCategory {
    NONE(),
    CHARACTER(R.string.bk_pref_category_title_character),
    WORD(R.string.bk_pref_category_title_word),
    LINE(R.string.bk_pref_category_title_line),
    PLACE_ON_PAGE(R.string.bk_pref_category_title_place_on_page),
    EDITING(R.string.bk_pref_category_title_editing),
    GRANULARITY(R.string.bk_pref_category_title_granularity),
    ;

    @StringRes private final int nameRes;

    SubCategory() {
      this(ID_NULL);
    }

    SubCategory(@StringRes int nameRes) {
      this.nameRes = nameRes;
    }

    /** Gets the key of the {@link SubCategory}. */
    public String getName(Resources resources) {
      if (nameRes == ID_NULL) {
        return "";
      }
      return resources.getString(nameRes);
    }
  }

  private final Category category;
  private final SubCategory subCategory;
  @StringRes private final int descriptionRes;
  @DrawableRes private final int iconRes;

  BrailleImeActions(
      Category category,
      SubCategory subCategory,
      @StringRes int descriptionRes,
      @DrawableRes int iconRes) {
    if (!category.subCategoryList.contains(subCategory)) {
      throw new IllegalArgumentException(
          "Category does not have compatible SubCategory: " + subCategory);
    }
    this.category = category;
    this.subCategory = subCategory;
    this.descriptionRes = descriptionRes;
    this.iconRes = iconRes;
  }

  BrailleImeActions(Category category, @StringRes int descriptionRes, @DrawableRes int iconRes) {
    this(category, NONE, descriptionRes, iconRes);
  }

  BrailleImeActions(Category category, SubCategory subCategory, @StringRes int descriptionRes) {
    this(category, subCategory, descriptionRes, ID_NULL);
  }

  BrailleImeActions(Category category, @StringRes int descriptionRes) {
    this(category, NONE, descriptionRes, ID_NULL);
  }

  /** Gets the category of the action. */
  public Category getCategory() {
    return category;
  }

  /** Gets the subcategory of the action. */
  public SubCategory getSubCategory() {
    return subCategory;
  }

  /** Gets the description of the action. */
  public String getDescriptionRes(Resources resources) {
    return resources.getString(descriptionRes);
  }

  /** Whether the action has icon. */
  public boolean hasIcon() {
    return iconRes != ID_NULL;
  }

  /** Gets the icon of the action. */
  public Drawable getIconRes(Context resources) {
    return resources.getDrawable(iconRes);
  }
}
