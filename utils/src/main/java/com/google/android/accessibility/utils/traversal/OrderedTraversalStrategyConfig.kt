/*
 * Copyright (C) The Android Open Source Project
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
 *
 * Ported from Java to Kotlin.
 * (Era @AutoValue no original Java; o processador não roda em fonte Kotlin,
 * então o valor+builder são escritos à mão com a MESMA API pública.)
 */

package com.google.android.accessibility.utils.traversal

/** Config class for {@link OrderedTraversalStrategy} */
class OrderedTraversalStrategyConfig private constructor(
  private val searchDirection: Int,
  private val includeChildrenOfNodesWithWebActions: Boolean,
  private val makeFabFirst: Boolean,
) {

  fun searchDirection(): Int = searchDirection

  fun includeChildrenOfNodesWithWebActions(): Boolean = includeChildrenOfNodesWithWebActions

  fun makeFabFirst(): Boolean = makeFabFirst

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other !is OrderedTraversalStrategyConfig) return false
    return searchDirection == other.searchDirection &&
      includeChildrenOfNodesWithWebActions == other.includeChildrenOfNodesWithWebActions &&
      makeFabFirst == other.makeFabFirst
  }

  override fun hashCode(): Int {
    var h = 1
    h *= 1000003
    h = h xor searchDirection
    h *= 1000003
    h = h xor if (includeChildrenOfNodesWithWebActions) 1231 else 1237
    h *= 1000003
    h = h xor if (makeFabFirst) 1231 else 1237
    return h
  }

  override fun toString(): String =
    "OrderedTraversalStrategyConfig{" +
      "searchDirection=$searchDirection, " +
      "includeChildrenOfNodesWithWebActions=$includeChildrenOfNodesWithWebActions, " +
      "makeFabFirst=$makeFabFirst}"

  /** Builder for TraversalStrategy config data. */
  class Builder internal constructor() {
    private var searchDirection: Int = 0
    private var includeChildrenOfNodesWithWebActions: Boolean = false
    private var makeFabFirst: Boolean = false

    fun setSearchDirection(searchDirection: Int): Builder {
      this.searchDirection = searchDirection
      return this
    }

    fun setIncludeChildrenOfNodesWithWebActions(value: Boolean): Builder {
      this.includeChildrenOfNodesWithWebActions = value
      return this
    }

    fun setMakeFabFirst(value: Boolean): Builder {
      this.makeFabFirst = value
      return this
    }

    fun build(): OrderedTraversalStrategyConfig =
      OrderedTraversalStrategyConfig(
        searchDirection, includeChildrenOfNodesWithWebActions, makeFabFirst)
  }

  companion object {
    @JvmStatic
    fun builder(): Builder =
      Builder()
        .setSearchDirection(0)
        .setIncludeChildrenOfNodesWithWebActions(false)
        .setMakeFabFirst(false)
  }
}
