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

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CARACTERIZAÇÃO do miolo de utils/:
 *
 * Filter — and/or com curto-circuito; argumento null devolve o PRÓPRIO filtro;
 * quirk: and() sobre um FilterAnd MUTA e devolve a MESMA instância (acumula).
 *
 * Role — precedência do mapeamento classe→papel: isTextEntryKey vence tudo;
 * subclasses antes de superclasses (RadioButton antes de Button); ImageView
 * clicável vira IMAGE_BUTTON; coleção sem classe explícita decide GRID×LIST
 * pelas contagens (>1 linha E >1 coluna = GRID).
 *
 * FocusFinder — invólucro fino do findFocus do serviço: sem foco → null (sem
 * exceção), para ambos os tipos de foco.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoreUtilsCharacterizationTest {
    // ----- Filter -----

    private fun filter(result: Boolean, log: MutableList<String>, name: String) =
        object : Filter<String>() {
            override fun accept(obj: String?): Boolean {
                log += name
                return result
            }
        }

    @Test
    fun `and e or com curto-circuito e null devolve o proprio filtro`() {
        val log = mutableListOf<String>()
        val yes = filter(true, log, "yes")
        val no = filter(false, log, "no")

        assertTrue(yes.and(yes).accept("x"))
        assertFalse(no.and(yes).accept("x"))
        assertEquals(listOf("yes", "yes", "no"), log) // "no" curto-circuita o segundo
        log.clear()

        assertTrue(no.or(yes).accept("x"))
        assertEquals(listOf("no", "yes"), log)

        assertSame(yes, yes.and(null))
        assertSame(yes, yes.or(null))
    }

    @Test
    fun `and sobre FilterAnd acumula e devolve a mesma instancia`() {
        val log = mutableListOf<String>()
        val a = filter(true, log, "a")
        val b = filter(true, log, "b")
        val c = filter(false, log, "c")

        val ab = a.and(b)
        val abc = ab.and(c)
        assertSame("FilterAnd.and() muta e devolve this", ab, abc)
        assertFalse(abc.accept("x"))
        assertEquals(listOf("a", "b", "c"), log)
    }

    @Test
    fun `Filter node cria filtro a partir de lambda`() {
        val nonNull = Filter.node { it != null }
        assertTrue(nonNull.accept(AccessibilityNodeInfoCompat.obtain()))
        assertFalse(nonNull.accept(null))
    }

    // ----- Role -----

    @Test
    fun `precedencia - isTextEntryKey vence a classe`() {
        val node = node("android.widget.Button")
        node.isTextEntryKey = true
        assertEquals(Role.ROLE_TEXT_ENTRY_KEY, Role.getRole(node))
    }

    @Test
    fun `subclasses antes de superclasses no mapeamento`() {
        assertEquals(Role.ROLE_SWITCH, Role.getRole(node("android.widget.Switch")))
        assertEquals(Role.ROLE_RADIO_BUTTON, Role.getRole(node("android.widget.RadioButton")))
        assertEquals(Role.ROLE_CHECK_BOX, Role.getRole(node("android.widget.CheckBox")))
        assertEquals(Role.ROLE_TOGGLE_BUTTON, Role.getRole(node("android.widget.ToggleButton")))
        assertEquals(Role.ROLE_BUTTON, Role.getRole(node("android.widget.Button")))
        assertEquals(Role.ROLE_EDIT_TEXT, Role.getRole(node("android.widget.EditText")))
        assertEquals(Role.ROLE_SEEK_CONTROL, Role.getRole(node("android.widget.SeekBar")))
        assertEquals(Role.ROLE_PROGRESS_BAR, Role.getRole(node("android.widget.ProgressBar")))
        assertEquals(Role.ROLE_WEB_VIEW, Role.getRole(node("android.webkit.WebView")))
        assertEquals(Role.ROLE_GRID, Role.getRole(node("android.widget.GridView")))
        assertEquals(Role.ROLE_LIST, Role.getRole(node("android.widget.ListView")))
        assertEquals(Role.ROLE_DROP_DOWN_LIST, Role.getRole(node("android.widget.Spinner")))
        assertEquals(Role.ROLE_VIEW_GROUP, Role.getRole(node("android.widget.FrameLayout")))
        assertEquals(Role.ROLE_NONE, Role.getRole(null as AccessibilityNodeInfoCompat?))
    }

    @Test
    fun `imagem clicavel e IMAGE_BUTTON, nao-clicavel e IMAGE`() {
        val image = node("android.widget.ImageView")
        assertEquals(Role.ROLE_IMAGE, Role.getRole(image))
        image.isClickable = true
        assertEquals(Role.ROLE_IMAGE_BUTTON, Role.getRole(image))
    }

    @Test
    fun `colecao sem classe explicita decide GRID x LIST pelas contagens`() {
        val grid = node("android.widget.LinearLayout")
        grid.setCollectionInfo(CollectionInfoCompat.obtain(3, 3, false))
        assertEquals(Role.ROLE_GRID, Role.getRole(grid))

        val list = node("android.widget.LinearLayout")
        list.setCollectionInfo(CollectionInfoCompat.obtain(5, 1, false))
        assertEquals(Role.ROLE_LIST, Role.getRole(list))
    }

    @Test
    fun `roleToString e isAdjustableRole`() {
        assertEquals("ROLE_BUTTON", Role.roleToString(Role.ROLE_BUTTON))
        assertEquals("ROLE_TEXT_ENTRY_KEY", Role.roleToString(Role.ROLE_TEXT_ENTRY_KEY))
        assertTrue(Role.isAdjustableRole(Role.ROLE_SEEK_CONTROL))
        assertTrue(Role.isAdjustableRole(Role.ROLE_NUMBER_PICKER))
        assertFalse(Role.isAdjustableRole(Role.ROLE_BUTTON))
    }

    @Test
    fun `getSourceRole - evento null e NONE, Toast pela classe do evento`() {
        assertEquals(Role.ROLE_NONE, Role.getSourceRole(null))
        val event = AccessibilityEvent.obtain()
        event.className = "android.widget.Toast\$TN"
        assertEquals(Role.ROLE_TOAST, Role.getSourceRole(event))
    }

    // ----- FocusFinder -----

    class TestA11yService : AccessibilityService() {
        override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

        override fun onInterrupt() {}
    }

    @Test
    fun `focus finder sem foco devolve null para ambos os tipos`() {
        val service = Robolectric.setupService(TestA11yService::class.java)
        val finder = FocusFinder(service)
        assertNull(finder.findAccessibilityFocus())
        assertNull(finder.findFocusCompat(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
        assertNull(finder.findFocusCompat(AccessibilityNodeInfo.FOCUS_INPUT))
        assertNull(FocusFinder.getAccessibilityFocusNode(service, /* fallbackOnRoot= */ true))
    }

    private fun node(className: String): AccessibilityNodeInfoCompat {
        val compat = AccessibilityNodeInfoCompat.obtain()
        compat.className = className
        return compat
    }
}
