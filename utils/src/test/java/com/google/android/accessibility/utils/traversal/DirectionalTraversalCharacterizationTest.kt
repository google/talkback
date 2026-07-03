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

import android.graphics.Rect
import android.text.SpannableString
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * CARACTERIZAÇÃO da navegação ESPACIAL e utilitários finais de
 * traversal/:
 *
 * DirectionalTraversalStrategy — algoritmo do FocusFinder do framework
 * (beams + distância ponderada 13·maior² + menor²) sobre a geometria dos nós:
 * numa cruz A/B(direita)/C(abaixo), RIGHT acha B, DOWN acha C, LEFT/UP voltam
 * para A, e LEFT sem candidato devolve null.
 *
 * NodeCachedBoundsCalculator — nó invisível não tem bounds úteis (null);
 * nó focável visível usa os PRÓPRIOS bounds; usesChildrenBounds(null)=false.
 *
 * SpannableTraversalUtils — acha ClickableSpan (URLSpan) no texto do nó;
 * texto puro não tem; contentDescription no nó PODA a busca nos filhos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DirectionalTraversalCharacterizationTest {
    private lateinit var root: AccessibilityNodeInfoCompat
    private lateinit var a: AccessibilityNodeInfoCompat
    private lateinit var b: AccessibilityNodeInfoCompat
    private lateinit var c: AccessibilityNodeInfoCompat

    @Before
    fun setUp() {
        root = node(Rect(0, 0, 1000, 1000), focusable = false)
        a = node(Rect(0, 0, 100, 100))
        b = node(Rect(200, 0, 300, 100)) // à direita de A
        c = node(Rect(0, 200, 100, 300)) // abaixo de A
        shadowOf(root.unwrap()).addChild(a.unwrap())
        shadowOf(root.unwrap()).addChild(b.unwrap())
        shadowOf(root.unwrap()).addChild(c.unwrap())
    }

    @Test
    fun `na cruz - RIGHT acha B, DOWN acha C, e os inversos voltam para A`() {
        val strategy = DirectionalTraversalStrategy(root, null)
        assertEquals(b, strategy.findFocus(a, TraversalStrategy.SEARCH_FOCUS_RIGHT))
        assertEquals(c, strategy.findFocus(a, TraversalStrategy.SEARCH_FOCUS_DOWN))
        assertEquals(a, strategy.findFocus(b, TraversalStrategy.SEARCH_FOCUS_LEFT))
        assertEquals(a, strategy.findFocus(c, TraversalStrategy.SEARCH_FOCUS_UP))
    }

    @Test
    fun `sem candidato na direcao devolve null`() {
        val strategy = DirectionalTraversalStrategy(root, null)
        assertNull(strategy.findFocus(a, TraversalStrategy.SEARCH_FOCUS_LEFT))
        assertNull(strategy.findFocus(a, TraversalStrategy.SEARCH_FOCUS_UP))
        assertNull(strategy.findFocus(null, TraversalStrategy.SEARCH_FOCUS_RIGHT))
    }

    @Test
    fun `bounds uteis - invisivel e null, focavel visivel usa os proprios bounds`() {
        val calculator = NodeCachedBoundsCalculator()
        val invisible = AccessibilityNodeInfoCompat.wrap(AccessibilityNodeInfo.obtain())
        assertNull(calculator.getBounds(invisible))
        assertFalse(calculator.usesChildrenBounds(null))
        assertFalse(calculator.usesChildrenBounds(a))

        val bounds = calculator.getBounds(a)
        assertEquals(Rect(0, 0, 100, 100), bounds)
        // Cache: a mesma instância de Rect volta na segunda consulta.
        assertSame(bounds, calculator.getBounds(a))
    }

    @Test
    fun `spannable - URLSpan no texto e encontrado, texto puro nao`() {
        val withLink = node(Rect(0, 0, 50, 50))
        val text = SpannableString("abrir link")
        text.setSpan(URLSpan("https://x"), 0, 5, 0)
        withLink.text = text
        assertTrue(
            SpannableTraversalUtils.hasTargetClickableSpanInNodeTree(withLink, ClickableSpan::class.java),
        )

        val plain = node(Rect(0, 0, 50, 50))
        plain.text = "sem link"
        assertFalse(
            SpannableTraversalUtils.hasTargetClickableSpanInNodeTree(plain, ClickableSpan::class.java),
        )
        assertFalse(
            SpannableTraversalUtils.hasTargetClickableSpanInNodeTree(null, ClickableSpan::class.java),
        )
    }

    @Test
    fun `spannable - contentDescription no no PODA a busca nos filhos`() {
        val parent = node(Rect(0, 0, 500, 500), focusable = false)
        parent.contentDescription = "container rotulado"
        val child = node(Rect(0, 0, 50, 50), focusable = false)
        val text = SpannableString("abrir link")
        text.setSpan(URLSpan("https://x"), 0, 5, 0)
        child.text = text
        shadowOf(parent.unwrap()).addChild(child.unwrap())
        assertFalse(
            SpannableTraversalUtils.hasTargetClickableSpanInNodeTree(parent, ClickableSpan::class.java),
        )
    }

    private fun node(bounds: Rect, focusable: Boolean = true): AccessibilityNodeInfoCompat {
        val view = View(RuntimeEnvironment.getApplication())
        val info = AccessibilityNodeInfo.obtain(view)
        val compat = AccessibilityNodeInfoCompat.wrap(info)
        compat.setBoundsInScreen(bounds)
        compat.isVisibleToUser = true
        if (focusable) {
            compat.isClickable = true
        }
        return compat
    }
}
