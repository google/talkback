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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CARACTERIZAÇÃO da WorkingTree — a árvore reordenável usada pelo
 * OrderedTraversalController para aplicar traversalBefore/traversalAfter:
 *  - getNext percorre em PRÉ-ORDEM (filho, senão próximo irmão, senão sobe);
 *  - getPrevious é o inverso (irmão anterior desce até o último descendente);
 *  - swapChild substitui o filho NA MESMA posição;
 *  - getLastNode/getRoot navegam até as pontas.
 *
 * Árvore de teste:  root ── a ── a1
 *                        └─ b        (a1 é filho de a; b é irmão de a)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkingTreeCharacterizationTest {
    private val root = WorkingTree(node(), null)
    private val a = WorkingTree(node(), root)
    private val a1 = WorkingTree(node(), a)
    private val b = WorkingTree(node(), root)

    init {
        root.addChild(a)
        a.addChild(a1)
        root.addChild(b)
    }

    @Test
    fun `getNext percorre em pre-ordem`() {
        assertSame(a, root.next)
        assertSame(a1, a.next)
        assertSame(b, a1.next) // sobe de a1 e vai ao próximo irmão de a
        assertNull(b.next) // fim da árvore
    }

    @Test
    fun `getPrevious e o inverso da pre-ordem`() {
        assertSame(root, a.previous)
        assertSame(a, a1.previous)
        assertSame(a1, b.previous) // irmão anterior (a) desce ao último descendente (a1)
        assertNull(root.previous)
    }

    @Test
    fun `irmãos - next e previous sibling`() {
        assertSame(b, a.nextSibling)
        assertNull(b.nextSibling)
        assertSame(a, b.previousSibling)
        assertNull(a.previousSibling)
        assertNull(root.nextSibling)
    }

    @Test
    fun `getLastNode desce ate o ultimo descendente e getRoot sobe ate a raiz`() {
        assertSame(b, root.lastNode) // último filho de root é b, sem filhos
        assertSame(a1, a.lastNode)
        assertSame(root, a1.root)
    }

    @Test
    fun `swapChild substitui o filho na mesma posicao`() {
        val c = WorkingTree(node(), root)
        root.swapChild(a, c)
        assertSame(c, root.next) // c assumiu a 1ª posição
        assertSame(b, c.nextSibling)
    }

    @Test
    fun `removeChild tira o filho da sequencia`() {
        root.removeChild(a)
        assertSame(b, root.next)
    }

    private fun node(): AccessibilityNodeInfoCompat = AccessibilityNodeInfoCompat.obtain()
}
