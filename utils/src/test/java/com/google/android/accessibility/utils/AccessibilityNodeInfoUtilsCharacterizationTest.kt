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

package com.google.android.accessibility.utils

import android.graphics.Rect
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.RangeInfoCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * CARACTERIZAÇÃO do AccessibilityNodeInfoUtils — o coração
 * semântico do foco. Fixa o contrato dos caminhos mais usados:
 *
 *  - shouldFocusNode: invisível nunca; folha focável/clicável sempre; container
 *    acionável só se FALA (texto próprio ou filho não-acionável falante);
 *    não-focável com texto e SEM ancestral focável recebe o foco;
 *  - "clicável"/"focável" = flag OU ação anunciada;
 *  - parentesco (hasAncestor/hasDescendant/getMatchingAncestor/…);
 *  - findFocusFromHover sobe ao ancestral que deve ser focado;
 *  - busca BFS; suporte a ações; utilidades numéricas/texto
 *    (roundForProgressPercent com bordas 0/100, subsequenceSafe).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilityNodeInfoUtilsCharacterizationTest {
    // ----- shouldFocusNode -----

    @Test
    fun `invisivel nunca recebe foco - folha clicavel visivel sempre`() {
        val invisible = node()
        invisible.isClickable = true
        assertFalse(AccessibilityNodeInfoUtils.shouldFocusNode(invisible))

        val leaf = visibleNode()
        leaf.isClickable = true
        assertTrue(AccessibilityNodeInfoUtils.shouldFocusNode(leaf))
        // Folha focável sem NADA a falar também recebe foco (botão sem rótulo).
        assertTrue(AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS.accept(leaf))
    }

    @Test
    fun `container acionavel so recebe foco se FALA`() {
        // Container clicável com filho focável e sem texto: o foco é do filho.
        val silentParent = visibleNode()
        silentParent.isClickable = true
        val focusableChild = visibleNode()
        focusableChild.isClickable = true
        focusableChild.text = "filho"
        addChild(silentParent, focusableChild)
        assertFalse(AccessibilityNodeInfoUtils.shouldFocusNode(silentParent))
        assertTrue(AccessibilityNodeInfoUtils.shouldFocusNode(focusableChild))

        // Container clicável cujo filho NÃO-acionável fala: o container fala por ele.
        val speakingParent = visibleNode()
        speakingParent.isClickable = true
        val textChild = visibleNode()
        textChild.text = "rótulo do botão"
        addChild(speakingParent, textChild)
        assertTrue(AccessibilityNodeInfoUtils.shouldFocusNode(speakingParent))
        // E o filho de texto, com ancestral focável, NÃO recebe foco próprio.
        assertFalse(AccessibilityNodeInfoUtils.shouldFocusNode(textChild))
    }

    @Test
    fun `nao-focavel com texto e sem ancestral focavel recebe o foco`() {
        val plainText = visibleNode()
        plainText.text = "parágrafo solto"
        assertTrue(AccessibilityNodeInfoUtils.shouldFocusNode(plainText))
    }

    @Test
    fun `findFocusFromHover sobe do texto ao ancestral clicavel`() {
        val button = visibleNode()
        button.isClickable = true
        val label = visibleNode()
        label.text = "abrir"
        addChild(button, label)
        assertEquals(button, AccessibilityNodeInfoUtils.findFocusFromHover(label))
        assertNull(AccessibilityNodeInfoUtils.findFocusFromHover(null))
    }

    // ----- acionabilidade por flag OU ação -----

    @Test
    fun `clicavel-focavel-longclicavel valem por flag OU acao anunciada`() {
        val byFlag = visibleNode()
        byFlag.isClickable = true
        assertTrue(AccessibilityNodeInfoUtils.isClickable(byFlag))

        val byAction = visibleNode()
        byAction.addAction(AccessibilityActionCompat.ACTION_CLICK)
        assertTrue(AccessibilityNodeInfoUtils.isClickable(byAction))
        assertTrue(AccessibilityNodeInfoUtils.isActionableForAccessibility(byAction))

        val longByAction = visibleNode()
        longByAction.addAction(AccessibilityActionCompat.ACTION_LONG_CLICK)
        assertTrue(AccessibilityNodeInfoUtils.isLongClickable(longByAction))

        val focusByAction = visibleNode()
        focusByAction.addAction(AccessibilityActionCompat.ACTION_FOCUS)
        assertTrue(AccessibilityNodeInfoUtils.isFocusable(focusByAction))

        assertFalse(AccessibilityNodeInfoUtils.isClickable(visibleNode()))
        assertFalse(AccessibilityNodeInfoUtils.isClickable(null))
    }

    @Test
    fun `supportsAction e supportsAnyAction`() {
        val node = visibleNode()
        node.addAction(AccessibilityActionCompat.ACTION_SCROLL_FORWARD)
        assertTrue(
            AccessibilityNodeInfoUtils.supportsAction(
                node, AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD),
        )
        assertFalse(
            AccessibilityNodeInfoUtils.supportsAction(
                node, AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD),
        )
        assertTrue(
            AccessibilityNodeInfoUtils.supportsAnyAction(
                node,
                AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD,
                AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD,
            ),
        )
        assertFalse(
            AccessibilityNodeInfoUtils.supportsAnyAction(
                null, AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD),
        )
    }

    // ----- parentesco e buscas -----

    @Test
    fun `hasAncestor - hasDescendant - getMatchingAncestor`() {
        val root = visibleNode()
        val mid = visibleNode()
        val leaf = visibleNode()
        addChild(root, mid)
        addChild(mid, leaf)

        assertTrue(AccessibilityNodeInfoUtils.hasAncestor(leaf, root))
        assertFalse(AccessibilityNodeInfoUtils.hasAncestor(root, leaf))
        assertTrue(AccessibilityNodeInfoUtils.hasDescendant(root, leaf))
        assertFalse(AccessibilityNodeInfoUtils.hasDescendant(leaf, root))

        mid.isClickable = true
        val clickableFilter =
            object : Filter<AccessibilityNodeInfoCompat>() {
                override fun accept(n: AccessibilityNodeInfoCompat?): Boolean =
                    n != null && n.isClickable
            }
        // getMatchingAncestor NÃO inclui o próprio nó.
        assertEquals(mid, AccessibilityNodeInfoUtils.getMatchingAncestor(leaf, clickableFilter))
        assertNull(AccessibilityNodeInfoUtils.getMatchingAncestor(mid, clickableFilter))
        // getSelfOrMatchingAncestor inclui.
        assertEquals(mid, AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(mid, clickableFilter))
        assertTrue(AccessibilityNodeInfoUtils.isOrHasMatchingAncestor(mid, clickableFilter))
        assertTrue(AccessibilityNodeInfoUtils.hasMatchingDescendant(root, clickableFilter))
    }

    @Test
    fun `getRoot sobe ate a raiz e searchFromBfs acha na ordem de largura`() {
        val root = visibleNode()
        val c1 = visibleNode()
        val c2 = visibleNode()
        c2.text = "alvo"
        addChild(root, c1)
        addChild(root, c2)
        assertEquals(root, AccessibilityNodeInfoUtils.getRoot(c2))

        val found =
            AccessibilityNodeInfoUtils.searchFromBfs(
                root,
                object : Filter<AccessibilityNodeInfoCompat>() {
                    override fun accept(n: AccessibilityNodeInfoCompat?): Boolean =
                        n != null && "alvo" == n.text?.toString()
                },
            )
        assertEquals(c2, found)
    }

    @Test
    fun `isTopLevelScrollItem exige ACOES de scroll no pai - a flag mente`() {
        // isScrollable ignora a FLAG isScrollable (WebViews mentem) — só as
        // ações ACTION_SCROLL_* contam.
        val flagOnly = visibleNode()
        flagOnly.isScrollable = true
        val itemOfFlagOnly = visibleNode()
        addChild(flagOnly, itemOfFlagOnly)
        assertFalse(AccessibilityNodeInfoUtils.isTopLevelScrollItem(itemOfFlagOnly))

        val list = visibleNode()
        list.isScrollable = true
        list.addAction(AccessibilityActionCompat.ACTION_SCROLL_FORWARD)
        val item = visibleNode()
        addChild(list, item)
        assertTrue(AccessibilityNodeInfoUtils.isTopLevelScrollItem(item))
        assertFalse(AccessibilityNodeInfoUtils.isTopLevelScrollItem(visibleNode()))
    }

    // ----- utilidades -----

    @Test
    fun `toCompat null e null - getNodeBoundsInScreen copia os bounds`() {
        assertNull(AccessibilityNodeInfoUtils.toCompat(null))
        val node = visibleNode()
        node.setBoundsInScreen(Rect(1, 2, 30, 40))
        assertEquals(Rect(1, 2, 30, 40), AccessibilityNodeInfoUtils.getNodeBoundsInScreen(node))
    }

    @Test
    fun `roundForProgressPercent nunca arredonda para 0 ou 100 dentro do intervalo`() {
        // (0,1) força 1 e (99,100) força 99: progresso em andamento nunca soa
        // como "0%" nem como "100%" antes da hora.
        assertEquals(1, AccessibilityNodeInfoUtils.roundForProgressPercent(0.4))
        assertEquals(1, AccessibilityNodeInfoUtils.roundForProgressPercent(0.6))
        assertEquals(99, AccessibilityNodeInfoUtils.roundForProgressPercent(99.4))
        assertEquals(99, AccessibilityNodeInfoUtils.roundForProgressPercent(99.6))
        assertEquals(0, AccessibilityNodeInfoUtils.roundForProgressPercent(-1.0))
        assertEquals(100, AccessibilityNodeInfoUtils.roundForProgressPercent(150.0))
        assertEquals(50, AccessibilityNodeInfoUtils.roundForProgressPercent(50.4))
    }

    @Test
    fun `subsequenceSafe apara indices fora do texto`() {
        assertEquals("abc", AccessibilityNodeInfoUtils.subsequenceSafe("abc", -5, 99).toString())
        assertEquals("b", AccessibilityNodeInfoUtils.subsequenceSafe("abc", 1, 2).toString())
        // Índices invertidos são reordenados.
        assertEquals("b", AccessibilityNodeInfoUtils.subsequenceSafe("abc", 2, 1).toString())
        assertEquals("", AccessibilityNodeInfoUtils.subsequenceSafe(null, 0, 1).toString())
    }

    @Test
    fun `hasValidRangeInfo exige minimo menor-igual atual menor-igual maximo`() {
        val valid = visibleNode()
        valid.rangeInfo = RangeInfoCompat.obtain(RangeInfoCompat.RANGE_TYPE_INT, 0f, 100f, 50f)
        assertTrue(AccessibilityNodeInfoUtils.hasValidRangeInfo(valid))

        val invalid = visibleNode()
        invalid.rangeInfo = RangeInfoCompat.obtain(RangeInfoCompat.RANGE_TYPE_INT, 0f, 100f, 150f)
        assertFalse(AccessibilityNodeInfoUtils.hasValidRangeInfo(invalid))

        assertFalse(AccessibilityNodeInfoUtils.hasValidRangeInfo(visibleNode()))
        assertFalse(AccessibilityNodeInfoUtils.hasValidRangeInfo(null))
    }

    @Test
    fun `getText prioriza o texto do no e actionToString conhece as basicas`() {
        val node = visibleNode()
        node.text = "texto"
        assertEquals("texto", AccessibilityNodeInfoUtils.getText(node)?.toString())
        assertNull(AccessibilityNodeInfoUtils.getText(null))
        assertEquals(
            "ACTION_CLICK",
            AccessibilityNodeInfoUtils.actionToString(AccessibilityNodeInfoCompat.ACTION_CLICK),
        )
    }

    // ----- fixtures -----

    private fun node(): AccessibilityNodeInfoCompat {
        val view = View(RuntimeEnvironment.getApplication())
        return AccessibilityNodeInfoCompat.wrap(AccessibilityNodeInfo.obtain(view))
    }

    private fun visibleNode(): AccessibilityNodeInfoCompat {
        val compat = node()
        compat.isVisibleToUser = true
        compat.setBoundsInScreen(Rect(0, 0, 200, 100))
        return compat
    }

    private fun addChild(parent: AccessibilityNodeInfoCompat, child: AccessibilityNodeInfoCompat) {
        shadowOf(parent.unwrap()).addChild(child.unwrap())
    }
}
