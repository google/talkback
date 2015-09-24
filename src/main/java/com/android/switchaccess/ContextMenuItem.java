/*
 * Copyright (C) 2015 Google Inc.
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

package com.android.switchaccess;

import android.content.Context;

import java.util.List;

/**
 * Interface for items that may appear in a context menu
 */
public interface ContextMenuItem extends OptionScanNode {
    /**
     * Return all labels to be displayed when the node is a part of a context menu. This list will
     * include items from children if the list requires more than one node to traverse.
     * @param context The current context
     * @return An immutable list of localized labels for all actions
     */
    public List<CharSequence> getActionLabels(Context context);
}
