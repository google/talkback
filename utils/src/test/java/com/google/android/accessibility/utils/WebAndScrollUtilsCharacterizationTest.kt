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
import com.google.android.accessibility.utils.traversal.TraversalStrategy
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
 * CARACTERIZAÇÃO de WebInterfaceUtils, AccessibilityNodeInfoRef e
 * ScrollableNodeInfo:
 *
 * WebInterfaceUtils — nó é "web" quando anuncia NEXT/PREVIOUS_HTML_ELEMENT;
 * Firefox (org.mozilla.*) conta como web container mesmo sem as ações;
 * containsImage lê o extra "AccessibilityNodeInfo.hasImage"; conversão de
 * direção de busca → direção web (FORWARD=1/BACKWARD=-1/UNKNOWN=0).
 *
 * AccessibilityNodeInfoRef — nextInOrder/previousInOrder em pré-ordem sobre
 * nós VISÍVEIS; lastDescendant desce até o fim; unOwned(null) é null.
 *
 * ScrollableNodeInfo — direção suportada nativamente é devolvida como está;
 * fallback lógico→espacial (FORWARD vira DOWN quando só há scroll vertical);
 * espacial→lógico (RIGHT vira FORWARD em LTR); sem suporte → null.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebAndScrollUtilsCharacterizationTest {
    // ----- WebInterfaceUtils -----

    @Test
    fun `no web e reconhecido pelas acoes de elemento HTML`() {
        val web = node()
        web.addAction(AccessibilityActionCompat(AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT, null))
        assertTrue(WebInterfaceUtils.supportsWebActions(web))
        assertTrue(WebInterfaceUtils.hasNativeWebContent(web))
        assertTrue(WebInterfaceUtils.isWebContainer(web))

        assertFalse(WebInterfaceUtils.supportsWebActions(node()))
        assertFalse(WebInterfaceUtils.supportsWebActions(null))
        assertFalse(WebInterfaceUtils.isWebContainer(null))
    }

    @Test
    fun `firefox conta como web container mesmo sem acoes html`() {
        val firefox = node()
        firefox.setPackageName("org.mozilla.firefox")
        assertTrue(WebInterfaceUtils.isWebContainer(firefox))
        assertFalse(WebInterfaceUtils.supportsWebActions(firefox))
    }

    @Test
    fun `containsImage le o extra hasImage`() {
        val withImage = node()
        withImage.extras.putString("AccessibilityNodeInfo.hasImage", "true")
        assertTrue(WebInterfaceUtils.containsImage(withImage))
        assertFalse(WebInterfaceUtils.containsImage(node()))
        assertFalse(WebInterfaceUtils.containsImage(null))
    }

    @Test
    fun `conversao de direcao de busca para direcao web`() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(
            WebInterfaceUtils.DIRECTION_FORWARD,
            WebInterfaceUtils.searchDirectionToWebNavigationDirection(
                context, TraversalStrategy.SEARCH_FOCUS_FORWARD),
        )
        assertEquals(
            WebInterfaceUtils.DIRECTION_BACKWARD,
            WebInterfaceUtils.searchDirectionToWebNavigationDirection(
                context, TraversalStrategy.SEARCH_FOCUS_BACKWARD),
        )
        assertEquals(
            0,
            WebInterfaceUtils.searchDirectionToWebNavigationDirection(
                context, TraversalStrategy.SEARCH_FOCUS_UNKNOWN),
        )
        assertEquals(1, WebInterfaceUtils.DIRECTION_FORWARD)
        assertEquals(-1, WebInterfaceUtils.DIRECTION_BACKWARD)
        assertEquals("HEADING", WebInterfaceUtils.HTML_ELEMENT_MOVE_BY_HEADING)
    }

    // ----- AccessibilityNodeInfoRef -----

    @Test
    fun `nextInOrder anda em pre-ordem sobre nos visiveis`() {
        val root = visibleNode()
        val c1 = visibleNode()
        val c2 = visibleNode()
        val c2a = visibleNode()
        shadowOf(root.unwrap()).addChild(c1.unwrap())
        shadowOf(root.unwrap()).addChild(c2.unwrap())
        shadowOf(c2.unwrap()).addChild(c2a.unwrap())

        val ref = AccessibilityNodeInfoRef.obtain(root)
        assertTrue(ref.nextInOrder())
        assertEquals(c1, ref.get())
        assertTrue(ref.nextInOrder())
        assertEquals(c2, ref.get())
        assertTrue(ref.nextInOrder())
        assertEquals(c2a, ref.get())
        assertFalse(ref.nextInOrder())
    }

    @Test
    fun `previousInOrder e lastDescendant sao os inversos`() {
        val root = visibleNode()
        val c1 = visibleNode()
        val c2 = visibleNode()
        shadowOf(root.unwrap()).addChild(c1.unwrap())
        shadowOf(root.unwrap()).addChild(c2.unwrap())

        val ref = AccessibilityNodeInfoRef.obtain(root)
        assertTrue(ref.lastDescendant())
        assertEquals(c2, ref.get())
        assertTrue(ref.previousInOrder())
        assertEquals(c1, ref.get())
        assertTrue(ref.previousInOrder())
        assertEquals(root, ref.get())

        assertNull(AccessibilityNodeInfoRef.unOwned(null))
        assertTrue(AccessibilityNodeInfoRef.isNull(null))
    }

    // ----- ScrollableNodeInfo -----

    @Test
    fun `direcao nativa e devolvida como esta - sem suporte e null`() {
        val forwardOnly = visibleNode()
        forwardOnly.addAction(AccessibilityActionCompat.ACTION_SCROLL_FORWARD)
        val info = ScrollableNodeInfo(forwardOnly, /* rtl= */ false)
        assertEquals(
            TraversalStrategy.SEARCH_FOCUS_FORWARD,
            info.getSupportedScrollDirection(TraversalStrategy.SEARCH_FOCUS_FORWARD),
        )
        assertNull(info.getSupportedScrollDirection(TraversalStrategy.SEARCH_FOCUS_BACKWARD))
    }

    @Test
    fun `fallbacks - FORWARD vira DOWN so-vertical, RIGHT vira FORWARD em LTR`() {
        val verticalOnly = visibleNode()
        verticalOnly.addAction(AccessibilityActionCompat.ACTION_SCROLL_DOWN)
        val vertical = ScrollableNodeInfo(verticalOnly, /* rtl= */ false)
        assertEquals(
            TraversalStrategy.SEARCH_FOCUS_DOWN,
            vertical.getSupportedScrollDirection(TraversalStrategy.SEARCH_FOCUS_FORWARD),
        )

        val logicalOnly = visibleNode()
        logicalOnly.addAction(AccessibilityActionCompat.ACTION_SCROLL_FORWARD)
        val logical = ScrollableNodeInfo(logicalOnly, /* rtl= */ false)
        assertEquals(
            TraversalStrategy.SEARCH_FOCUS_FORWARD,
            logical.getSupportedScrollDirection(TraversalStrategy.SEARCH_FOCUS_RIGHT),
        )
    }

    @Test
    fun `getNode devolve o proprio no embrulhado`() {
        val node = visibleNode()
        assertEquals(node, ScrollableNodeInfo(node, false).node)
    }

    private fun node(): AccessibilityNodeInfoCompat =
        AccessibilityNodeInfoCompat.wrap(AccessibilityNodeInfo.obtain())

    private fun visibleNode(): AccessibilityNodeInfoCompat {
        val view = View(RuntimeEnvironment.getApplication())
        val compat = AccessibilityNodeInfoCompat.wrap(AccessibilityNodeInfo.obtain(view))
        compat.isVisibleToUser = true
        compat.setBoundsInScreen(Rect(0, 0, 100, 100))
        return compat
    }
}
