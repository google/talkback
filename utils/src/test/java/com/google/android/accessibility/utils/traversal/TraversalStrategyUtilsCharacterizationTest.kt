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

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.accessibility.utils.Filter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * CARACTERIZAÇÃO do TraversalStrategyUtils:
 *  - classificação e conversões de direção (lógica × espacial, RTL, ações de
 *    scroll ida-e-volta, direções de foco de View);
 *  - searchFocus: percorre a estratégia até o filtro aceitar; PROTEÇÃO DE
 *    CICLO devolve null; filtro null aceita o primeiro não-nulo;
 *  - findFirstFocusInNodeTree: usa focusFirst e cai para searchFocus;
 *  - direção inválida lança IllegalArgumentException.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TraversalStrategyUtilsCharacterizationTest {
    @Test
    fun `classificacao logica x espacial`() {
        assertFalse(TraversalStrategyUtils.isSpatialDirection(TraversalStrategy.SEARCH_FOCUS_FORWARD))
        assertFalse(TraversalStrategyUtils.isSpatialDirection(TraversalStrategy.SEARCH_FOCUS_BACKWARD))
        assertTrue(TraversalStrategyUtils.isSpatialDirection(TraversalStrategy.SEARCH_FOCUS_LEFT))
        assertTrue(TraversalStrategyUtils.isSpatialDirection(TraversalStrategy.SEARCH_FOCUS_DOWN))
        assertTrue(TraversalStrategyUtils.isLogicalDirection(TraversalStrategy.SEARCH_FOCUS_FORWARD))
        assertFalse(TraversalStrategyUtils.isLogicalDirection(TraversalStrategy.SEARCH_FOCUS_UP))
        assertThrows(IllegalArgumentException::class.java) {
            TraversalStrategyUtils.isSpatialDirection(99)
        }
    }

    @Test
    fun `getLogicalDirection espelha esquerda-direita conforme RTL`() {
        // LTR: esquerda=trás, direita=frente.
        assertEquals(
            TraversalStrategy.SEARCH_FOCUS_BACKWARD,
            TraversalStrategyUtils.getLogicalDirection(TraversalStrategy.SEARCH_FOCUS_LEFT, false),
        )
        assertEquals(
            TraversalStrategy.SEARCH_FOCUS_FORWARD,
            TraversalStrategyUtils.getLogicalDirection(TraversalStrategy.SEARCH_FOCUS_RIGHT, false),
        )
        // RTL: espelhado.
        assertEquals(
            TraversalStrategy.SEARCH_FOCUS_FORWARD,
            TraversalStrategyUtils.getLogicalDirection(TraversalStrategy.SEARCH_FOCUS_LEFT, true),
        )
        assertEquals(
            TraversalStrategy.SEARCH_FOCUS_BACKWARD,
            TraversalStrategyUtils.getLogicalDirection(TraversalStrategy.SEARCH_FOCUS_RIGHT, true),
        )
        // Cima/baixo independem de RTL.
        assertEquals(
            TraversalStrategy.SEARCH_FOCUS_BACKWARD,
            TraversalStrategyUtils.getLogicalDirection(TraversalStrategy.SEARCH_FOCUS_UP, true),
        )
        assertEquals(
            TraversalStrategy.SEARCH_FOCUS_FORWARD,
            TraversalStrategyUtils.getLogicalDirection(TraversalStrategy.SEARCH_FOCUS_DOWN, false),
        )
    }

    @Test
    fun `conversoes de direcao para acoes de scroll fazem ida-e-volta`() {
        val directions =
            intArrayOf(
                TraversalStrategy.SEARCH_FOCUS_FORWARD,
                TraversalStrategy.SEARCH_FOCUS_BACKWARD,
                TraversalStrategy.SEARCH_FOCUS_LEFT,
                TraversalStrategy.SEARCH_FOCUS_RIGHT,
                TraversalStrategy.SEARCH_FOCUS_UP,
                TraversalStrategy.SEARCH_FOCUS_DOWN,
            )
        for (direction in directions) {
            val action = TraversalStrategyUtils.convertSearchDirectionToScrollAction(direction)
            assertEquals(direction, TraversalStrategyUtils.convertScrollActionToSearchDirection(action))
        }
        assertEquals(
            AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD,
            TraversalStrategyUtils.convertSearchDirectionToScrollAction(
                TraversalStrategy.SEARCH_FOCUS_FORWARD,
            ),
        )
        assertEquals(
            AccessibilityAction.ACTION_SCROLL_LEFT.id,
            TraversalStrategyUtils.convertSearchDirectionToScrollAction(
                TraversalStrategy.SEARCH_FOCUS_LEFT,
            ),
        )
        assertEquals(
            TraversalStrategy.SEARCH_FOCUS_UNKNOWN,
            TraversalStrategyUtils.convertScrollActionToSearchDirection(12345),
        )
    }

    @Test
    fun `conversao para direcoes de foco de View`() {
        assertEquals(
            View.FOCUS_FORWARD,
            TraversalStrategyUtils.nodeSearchDirectionToViewSearchDirection(
                TraversalStrategy.SEARCH_FOCUS_FORWARD,
            ),
        )
        assertEquals(
            View.FOCUS_LEFT,
            TraversalStrategyUtils.nodeSearchDirectionToViewSearchDirection(
                TraversalStrategy.SEARCH_FOCUS_LEFT,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            TraversalStrategyUtils.nodeSearchDirectionToViewSearchDirection(0)
        }
    }

    @Test
    fun `searchFocus percorre a cadeia ate o filtro aceitar`() {
        val a = distinctNode()
        val b = distinctNode()
        val c = distinctNode()
        val strategy = chainStrategy(mapOf(a to b, b to c))
        val onlyC =
            object : Filter<AccessibilityNodeInfoCompat>() {
                override fun accept(node: AccessibilityNodeInfoCompat?): Boolean = node == c
            }
        assertSame(c, TraversalStrategyUtils.searchFocus(strategy, a, FORWARD, onlyC))
        // Cadeia esgota sem o filtro aceitar → null.
        val nothing =
            object : Filter<AccessibilityNodeInfoCompat>() {
                override fun accept(node: AccessibilityNodeInfoCompat?): Boolean = false
            }
        assertNull(TraversalStrategyUtils.searchFocus(strategy, a, FORWARD, nothing))
    }

    @Test
    fun `searchFocus com ciclo na estrategia devolve null (protecao)`() {
        val a = distinctNode()
        val b = distinctNode()
        val strategy = chainStrategy(mapOf(a to b, b to a)) // ciclo A→B→A
        val never =
            object : Filter<AccessibilityNodeInfoCompat>() {
                override fun accept(node: AccessibilityNodeInfoCompat?): Boolean = false
            }
        assertNull(TraversalStrategyUtils.searchFocus(strategy, a, FORWARD, never))
    }

    @Test
    fun `findFirstFocusInNodeTree usa focusFirst e cai para searchFocus`() {
        val root = distinctNode()
        val next = distinctNode()
        val strategy =
            object : TraversalStrategy by chainStrategy(mapOf(root to next)) {
                override fun focusFirst(
                    r: AccessibilityNodeInfoCompat?,
                    direction: Int,
                ): AccessibilityNodeInfoCompat? = root
            }
        val acceptRoot =
            object : Filter<AccessibilityNodeInfoCompat>() {
                override fun accept(node: AccessibilityNodeInfoCompat?): Boolean = node == root
            }
        assertSame(root, TraversalStrategyUtils.findFirstFocusInNodeTree(strategy, root, FORWARD, acceptRoot))
        val acceptNext =
            object : Filter<AccessibilityNodeInfoCompat>() {
                override fun accept(node: AccessibilityNodeInfoCompat?): Boolean = node == next
            }
        assertSame(next, TraversalStrategyUtils.findFirstFocusInNodeTree(strategy, root, FORWARD, acceptNext))
        assertNull(TraversalStrategyUtils.findFirstFocusInNodeTree(strategy, null, FORWARD, acceptNext))
    }

    /** Nós DISTINTOS (ids de view diferentes) para os HashSets de proteção de ciclo. */
    private fun distinctNode(): AccessibilityNodeInfoCompat {
        val view = View(RuntimeEnvironment.getApplication())
        return AccessibilityNodeInfoCompat.wrap(AccessibilityNodeInfo.obtain(view))
    }

    /** Estratégia fake: findFocus segue o mapa; focusFirst devolve o próprio root. */
    private fun chainStrategy(
        chain: Map<AccessibilityNodeInfoCompat, AccessibilityNodeInfoCompat>,
    ): TraversalStrategy =
        object : TraversalStrategy {
            override fun findFocus(
                startNode: AccessibilityNodeInfoCompat?,
                direction: Int,
            ): AccessibilityNodeInfoCompat? = chain[startNode]

            override fun focusFirst(
                root: AccessibilityNodeInfoCompat?,
                direction: Int,
            ): AccessibilityNodeInfoCompat? = root

            override fun getSpeakingNodesCache(): Map<AccessibilityNodeInfoCompat, Boolean>? = null
        }

    private companion object {
        const val FORWARD = TraversalStrategy.SEARCH_FOCUS_FORWARD
    }
}
