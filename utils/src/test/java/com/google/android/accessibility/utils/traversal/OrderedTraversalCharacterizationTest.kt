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
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * CARACTERIZAÇÃO da travessia ORDENADA (sem traversalBefore/After —
 * a reordenação por before/after depende de fixtures que o Robolectric não
 * monta; o que se fixa aqui é a ORDEM DE ÁRVORE em pré-ordem, a base de tudo):
 *  - OrderedTraversalStrategy percorre a árvore em PRÉ-ORDEM (dumpTree);
 *  - findFocus FORWARD/BACKWARD andam na ordem e no inverso;
 *  - focusFirst FORWARD = raiz; BACKWARD = último descendente;
 *  - nó desconhecido → null;
 *  - ReorderedChildrenIterator itera filhos em ordem ascendente/descendente.
 *
 * Árvore:  root ── c1
 *               └─ c2 ── c2a
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrderedTraversalCharacterizationTest {
    private lateinit var root: AccessibilityNodeInfoCompat
    private lateinit var c1: AccessibilityNodeInfoCompat
    private lateinit var c2: AccessibilityNodeInfoCompat
    private lateinit var c2a: AccessibilityNodeInfoCompat

    @Before
    fun setUp() {
        root = node()
        c1 = node()
        c2 = node()
        c2a = node()
        addChild(root, c1)
        addChild(root, c2)
        addChild(c2, c2a)
    }

    @Test
    fun `dumpTree e a pre-ordem da arvore`() {
        val strategy = OrderedTraversalStrategy(root)
        assertEquals(listOf(root, c1, c2, c2a), strategy.dumpTree())
    }

    @Test
    fun `findFocus FORWARD anda na pre-ordem e devolve null no fim`() {
        val strategy = OrderedTraversalStrategy(root)
        assertEquals(c1, strategy.findFocus(root, TraversalStrategy.SEARCH_FOCUS_FORWARD))
        assertEquals(c2, strategy.findFocus(c1, TraversalStrategy.SEARCH_FOCUS_FORWARD))
        assertEquals(c2a, strategy.findFocus(c2, TraversalStrategy.SEARCH_FOCUS_FORWARD))
        assertNull(strategy.findFocus(c2a, TraversalStrategy.SEARCH_FOCUS_FORWARD))
    }

    @Test
    fun `findFocus BACKWARD inverte a pre-ordem`() {
        val strategy = OrderedTraversalStrategy(root)
        assertEquals(c2, strategy.findFocus(c2a, TraversalStrategy.SEARCH_FOCUS_BACKWARD))
        assertEquals(c1, strategy.findFocus(c2, TraversalStrategy.SEARCH_FOCUS_BACKWARD))
        assertEquals(root, strategy.findFocus(c1, TraversalStrategy.SEARCH_FOCUS_BACKWARD))
        assertNull(strategy.findFocus(root, TraversalStrategy.SEARCH_FOCUS_BACKWARD))
    }

    @Test
    fun `focusFirst FORWARD e a raiz - BACKWARD e o ultimo descendente`() {
        val strategy = OrderedTraversalStrategy(root)
        assertEquals(root, strategy.focusFirst(root, TraversalStrategy.SEARCH_FOCUS_FORWARD))
        assertEquals(c2a, strategy.focusFirst(root, TraversalStrategy.SEARCH_FOCUS_BACKWARD))
    }

    @Test
    fun `no desconhecido devolve null`() {
        val strategy = OrderedTraversalStrategy(root)
        val stranger = node()
        assertNull(strategy.findFocus(stranger, TraversalStrategy.SEARCH_FOCUS_FORWARD))
    }

    @Test
    fun `iterator asc e desc percorrem os filhos na ordem da arvore`() {
        val ascending = ReorderedChildrenIterator.createAscendingIterator(root)
        assertEquals(c1, ascending.next())
        assertEquals(c2, ascending.next())
        assertFalse(ascending.hasNext())

        val descending = ReorderedChildrenIterator.createDescendingIterator(root)
        assertEquals(c2, descending.next())
        assertEquals(c1, descending.next())
        assertFalse(descending.hasNext())
    }

    private fun node(): AccessibilityNodeInfoCompat {
        val view = View(RuntimeEnvironment.getApplication())
        return AccessibilityNodeInfoCompat.wrap(AccessibilityNodeInfo.obtain(view))
    }

    private fun addChild(parent: AccessibilityNodeInfoCompat, child: AccessibilityNodeInfoCompat) {
        shadowOf(parent.unwrap()).addChild(child.unwrap())
    }
}
