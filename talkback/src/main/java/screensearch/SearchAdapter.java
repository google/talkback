/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.talkback.screensearch;

import android.graphics.Color;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Adapter to handle the data to be shown on the screen search result recycler view. */
public class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ResultViewHolder> {
  // TODO: Changes the data type to ImmutableList after confirming that AOSP TalkBack
  // could support guava lib
  /** Search results to be shown on the recycler view. */
  private List<CharSequence> searchResult = Collections.emptyList();

  /** Click listener for view holder. */
  private OnClickListener viewHolderClickListener;

  /** ViewHolder for the recycler view. */
  public static class ResultViewHolder extends RecyclerView.ViewHolder {
    public TextView textView;

    public ResultViewHolder(TextView view) {
      super(view);
      textView = view;
    }
  }

  @Override
  public ResultViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
    TextView view =
        (TextView)
            LayoutInflater.from(viewGroup.getContext())
                .inflate(android.R.layout.simple_list_item_1, viewGroup, false);
    view.setTextColor(Color.WHITE);
    view.setOnClickListener(viewHolderClickListener);
    ResultViewHolder holder = new ResultViewHolder(view);
    return holder;
  }

  @Override
  public void onBindViewHolder(ResultViewHolder resultViewHolder, int index) {
    resultViewHolder.textView.setText(searchResult.get(index));
  }

  @Override
  public int getItemCount() {
    return searchResult.size();
  }

  /** Sets the search result and triggers recycler view update. */
  public void setResultAndNotify(List<CharSequence> result) {
    searchResult = new ArrayList<>(result);
    notifyDataSetChanged();
  }

  /** Clears the search result and triggers recycler view update. */
  public void clearAndNotify() {
    searchResult = Collections.emptyList();
    notifyDataSetChanged();
  }

  public void setOnViewHolderClickListener(@Nullable OnClickListener listener) {
    viewHolderClickListener = listener;
  }
}
