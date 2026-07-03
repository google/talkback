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
 */

package com.google.android.accessibility.utils.traversal

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CARACTERIZAÇÃO dos fundamentos da varredura:
 *  - constantes de direção do TraversalStrategy (contrato com prefs/logs);
 *  - nomes simbólicos das direções;
 *  - defaults do builder do OrderedTraversalStrategyConfig (AutoValue);
 *  - SimpleTraversalStrategy: focusFirst FORWARD devolve a própria raiz e
 *    não usa cache de nós falantes;
 *  - NodeFocusFinder: direção desconhecida devolve null.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TraversalBasicsCharacterizationTest {
    @Test
    fun `constantes de direcao sao estaveis`() {
        assertEquals(0, TraversalStrategy.SEARCH_FOCUS_UNKNOWN)
        assertEquals(1, TraversalStrategy.SEARCH_FOCUS_FORWARD)
        assertEquals(2, TraversalStrategy.SEARCH_FOCUS_BACKWARD)
        assertEquals(3, TraversalStrategy.SEARCH_FOCUS_LEFT)
        assertEquals(4, TraversalStrategy.SEARCH_FOCUS_RIGHT)
        assertEquals(5, TraversalStrategy.SEARCH_FOCUS_UP)
        assertEquals(6, TraversalStrategy.SEARCH_FOCUS_DOWN)
        assertEquals(1, NodeFocusFinder.SEARCH_FORWARD)
        assertEquals(-1, NodeFocusFinder.SEARCH_BACKWARD)
    }

    @Test
    fun `nomes simbolicos das direcoes`() {
        assertEquals(
            "SEARCH_FOCUS_FORWARD",
            TraversalStrategy.getSymbolicName(TraversalStrategy.SEARCH_FOCUS_FORWARD),
        )
        assertEquals(
            "SEARCH_FOCUS_BACKWARD",
            TraversalStrategy.getSymbolicName(TraversalStrategy.SEARCH_FOCUS_BACKWARD),
        )
        assertEquals("unavailable direction: 99", TraversalStrategy.getSymbolicName(99))
    }

    @Test
    fun `builder do config tem defaults zero-false-false`() {
        val config = OrderedTraversalStrategyConfig.builder().build()
        assertEquals(0, config.searchDirection())
        assertFalse(config.includeChildrenOfNodesWithWebActions())
        assertFalse(config.makeFabFirst())
    }

    @Test
    fun `builder do config preserva valores setados`() {
        val config =
            OrderedTraversalStrategyConfig.builder()
                .setSearchDirection(TraversalStrategy.SEARCH_FOCUS_BACKWARD)
                .setIncludeChildrenOfNodesWithWebActions(true)
                .setMakeFabFirst(true)
                .build()
        assertEquals(TraversalStrategy.SEARCH_FOCUS_BACKWARD, config.searchDirection())
        assertEquals(true, config.includeChildrenOfNodesWithWebActions())
        assertEquals(true, config.makeFabFirst())
    }

    @Test
    fun `simple strategy - focusFirst FORWARD devolve a raiz e nao ha cache`() {
        val strategy = SimpleTraversalStrategy()
        val root = AccessibilityNodeInfoCompat.obtain()
        assertSame(root, strategy.focusFirst(root, TraversalStrategy.SEARCH_FOCUS_FORWARD))
        assertNull(strategy.getSpeakingNodesCache())
        assertNull(strategy.focusFirst(null, TraversalStrategy.SEARCH_FOCUS_FORWARD))
    }

    @Test
    fun `node focus finder - direcao desconhecida devolve null`() {
        val node = AccessibilityNodeInfoCompat.obtain()
        assertNull(NodeFocusFinder.focusSearch(node, 0))
    }
}
