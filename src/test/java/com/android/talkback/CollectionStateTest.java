/*
 * Copyright (C) 2016 Google Inc.
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

package com.android.talkback;

import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.CollectionInfo;
import android.view.accessibility.AccessibilityNodeInfo.CollectionItemInfo;
import android.widget.ListView;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.android.switchaccess.test.ShadowAccessibilityNodeInfo;
import com.android.talkback.CollectionState;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.Role;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;

import java.lang.CharSequence;
import java.util.ArrayList;
import java.util.List;

@Config(constants = BuildConfig.class,
        sdk = 21,
        shadows = {ShadowAccessibilityNodeInfo.class})
@RunWith(RobolectricGradleTestRunner.class)
public class CollectionStateTest {

    private CollectionState mCollectionState;
    private Context mContext = RuntimeEnvironment.application.getApplicationContext();

    private static final String TEST_LIST_NAME_1 = "Foobar";
    private static final String TEST_LIST_NAME_2 = "Foobaz";
    private static final String TEST_LIST_NAME_3 = "Barbaz";
    private static final String TEST_GRID_NAME = "Bazbar";

    /** Make the root a WebView because this triggers headers to work on M or lower. */
    private static final String WEBVIEW_CLASS_NAME = "android.webkit.WebView";
    private static final String RECYCLERVIEW_CLASS_NAME = "android.support.v7.widget.RecyclerView";

    @Before
    public void setUp() {
        mCollectionState = new CollectionState();
    }

    @After
    public void tearDown() {
        assertFalse(ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(true));
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
    }

    @Test
    public void testList() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setClassName(WEBVIEW_CLASS_NAME);

        List<AccessibilityNodeInfo> items = new ArrayList<>();
        AccessibilityNodeInfo list = createList(TEST_LIST_NAME_1, 10, 2, items);
        getShadow(root).addChild(list);

        try {
            // Outside of list -> Outside of list
            AccessibilityNodeInfoCompat rootCompat = new AccessibilityNodeInfoCompat(root);
            mCollectionState.updateCollectionInformation(rootCompat, null);
            assertEquals(CollectionState.NAVIGATE_NONE, mCollectionState.getCollectionTransition());

            // Outside of list -> Item 1
            AccessibilityNodeInfoCompat item1 = new AccessibilityNodeInfoCompat(items.get(1));
            mCollectionState.updateCollectionInformation(item1, null);
            assertList(CollectionState.NAVIGATE_ENTER /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    true /* rowChanged */,
                    1 /* itemIndex */,
                    10 /* totalItems */,
                    false /* isItemHeader */);

            // Item 1 -> Item 2 (header)
            AccessibilityNodeInfoCompat item2 = new AccessibilityNodeInfoCompat(items.get(2));
            mCollectionState.updateCollectionInformation(item2, null);
            assertList(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    true /* rowChanged */,
                    2 /* itemIndex */,
                    10 /* totalItems */,
                    true /* isItemHeader */);

            // Item 2 (header) -> Refocus
            mCollectionState.updateCollectionInformation(item2, null);
            assertList(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    true /* rowChanged (must be true on refocus) */,
                    2 /* itemIndex */,
                    10 /* totalItems */,
                    true /* isItemHeader */);

            // Item 2 -> Outside of list
            mCollectionState.updateCollectionInformation(rootCompat, null);
            assertEquals(CollectionState.NAVIGATE_EXIT, mCollectionState.getCollectionTransition());

            // Outside of list -> Item 4
            AccessibilityNodeInfoCompat item4 = new AccessibilityNodeInfoCompat(items.get(4));
            mCollectionState.updateCollectionInformation(item4, null);
            assertList(CollectionState.NAVIGATE_ENTER /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    true /* rowChanged (must be true on refocus) */,
                    4 /* itemIndex */,
                    10 /* totalItems */,
                    false /* isItemHeader */);

            // Item 4 -> Outside of list
            mCollectionState.updateCollectionInformation(rootCompat, null);
            assertEquals(CollectionState.NAVIGATE_EXIT, mCollectionState.getCollectionTransition());

            // Outside of list -> Refocus
            mCollectionState.updateCollectionInformation(rootCompat, null);
            assertEquals(CollectionState.NAVIGATE_NONE, mCollectionState.getCollectionTransition());

            forceInternalRecycle(rootCompat);
        } finally {
            root.recycle();
            list.recycle();
            recycleNodes(items);
        }
    }

    @Config(sdk = Build.VERSION_CODES.JELLY_BEAN)
    @Test
    public void testList_JellyBean() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setClassName(WEBVIEW_CLASS_NAME);

        List<AccessibilityNodeInfo> items = new ArrayList<>();
        List<AccessibilityEvent> events = new ArrayList<>();
        AccessibilityNodeInfo list = createListWithJellyBeanEvents(TEST_LIST_NAME_1, 10, 2, items,
                events);
        getShadow(root).addChild(list);

        try {
            // Outside of list -> Outside of list
            AccessibilityNodeInfoCompat rootCompat = new AccessibilityNodeInfoCompat(root);
            mCollectionState.updateCollectionInformation(rootCompat, null);
            assertEquals(CollectionState.NAVIGATE_NONE, mCollectionState.getCollectionTransition());

            // Outside of list -> Item 1
            AccessibilityNodeInfoCompat item1 = new AccessibilityNodeInfoCompat(items.get(1));
            mCollectionState.updateCollectionInformation(item1, events.get(1));
            assertList(CollectionState.NAVIGATE_ENTER /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    true /* rowChanged */,
                    1 /* itemIndex */,
                    -1 /* totalItems */,
                    false /* isItemHeader */);

            // Item 1 -> Item 2 (header)
            AccessibilityNodeInfoCompat item2 = new AccessibilityNodeInfoCompat(items.get(2));
            mCollectionState.updateCollectionInformation(item2, events.get(2));
            assertList(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    true /* rowChanged */,
                    2 /* itemIndex */,
                    -1 /* totalItems */,
                    true /* isItemHeader */);

            // Item 2 (header) -> Refocus
            mCollectionState.updateCollectionInformation(item2, events.get(2));
            assertList(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    true /* rowChanged (must be true on refocus) */,
                    2 /* itemIndex */,
                    -1 /* totalItems */,
                    true /* isItemHeader */);

            // Item 2 -> Outside of list
            mCollectionState.updateCollectionInformation(rootCompat, null);
            assertEquals(CollectionState.NAVIGATE_EXIT, mCollectionState.getCollectionTransition());

            // Outside of list -> Item 4
            AccessibilityNodeInfoCompat item4 = new AccessibilityNodeInfoCompat(items.get(4));
            mCollectionState.updateCollectionInformation(item4, events.get(4));
            assertList(CollectionState.NAVIGATE_ENTER /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    true /* rowChanged (must be true on refocus) */,
                    4 /* itemIndex */,
                    -1 /* totalItems */,
                    false /* isItemHeader */);

            // Item 4 -> Outside of list
            mCollectionState.updateCollectionInformation(rootCompat, null);
            assertEquals(CollectionState.NAVIGATE_EXIT, mCollectionState.getCollectionTransition());

            // Outside of list -> Refocus
            mCollectionState.updateCollectionInformation(rootCompat, null);
            assertEquals(CollectionState.NAVIGATE_NONE, mCollectionState.getCollectionTransition());

            forceInternalRecycle(rootCompat);
        } finally {
            root.recycle();
            list.recycle();
            recycleNodes(items);
            for (AccessibilityEvent event : events) {
                event.recycle();
            }
        }
    }

    @Test
    public void testTwoLists() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setClassName(WEBVIEW_CLASS_NAME);

        List<AccessibilityNodeInfo> itemsA = new ArrayList<>();
        AccessibilityNodeInfo listA = createList(TEST_LIST_NAME_1, 8, 3, itemsA);
        getShadow(root).addChild(listA);

        List<AccessibilityNodeInfo> itemsB = new ArrayList<>();
        AccessibilityNodeInfo listB = createList(TEST_LIST_NAME_2, 5, 0, itemsB);
        getShadow(root).addChild(listB);

        try {
            // Outside of list -> Item A6
            AccessibilityNodeInfoCompat itemA6 = new AccessibilityNodeInfoCompat(itemsA.get(6));
            mCollectionState.updateCollectionInformation(itemA6, null);
            assertList(CollectionState.NAVIGATE_ENTER /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    true /* rowChanged */,
                    6 /* itemIndex */,
                    8 /* totalItems */,
                    false /* isItemHeader */);

            // Item A6 -> Item A3
            AccessibilityNodeInfoCompat itemA3 = new AccessibilityNodeInfoCompat(itemsA.get(3));
            mCollectionState.updateCollectionInformation(itemA3, null);
            assertList(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    true /* rowChanged */,
                    3 /* itemIndex */,
                    8 /* totalItems */,
                    true /* isItemHeader */);

            // Item A3 -> B3
            AccessibilityNodeInfoCompat itemB3 = new AccessibilityNodeInfoCompat(itemsB.get(3));
            mCollectionState.updateCollectionInformation(itemB3, null);
            assertList(CollectionState.NAVIGATE_ENTER /* collectionTransition */,
                    TEST_LIST_NAME_2 /* listName */,
                    true /* rowChanged */,
                    3 /* itemIndex */,
                    5 /* totalItems */,
                    false /* isItemHeader */);

            // Item B3 -> Item B0
            AccessibilityNodeInfoCompat itemB0 = new AccessibilityNodeInfoCompat(itemsB.get(0));
            mCollectionState.updateCollectionInformation(itemB0, null);
            assertList(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_LIST_NAME_2 /* listName */,
                    true /* rowChanged */,
                    0 /* itemIndex */,
                    5 /* totalItems */,
                    true /* isItemHeader */);

            // Item B0 -> Item B4
            AccessibilityNodeInfoCompat itemB4 = new AccessibilityNodeInfoCompat(itemsB.get(4));
            mCollectionState.updateCollectionInformation(itemB4, null);
            assertList(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_LIST_NAME_2 /* listName */,
                    true /* rowChanged */,
                    4 /* itemIndex */,
                    5 /* totalItems */,
                    false /* isItemHeader */);

            // Item B4 -> Outside of list
            AccessibilityNodeInfoCompat rootCompat = new AccessibilityNodeInfoCompat(root);
            mCollectionState.updateCollectionInformation(rootCompat, null);
            assertEquals(CollectionState.NAVIGATE_EXIT, mCollectionState.getCollectionTransition());

            forceInternalRecycle(rootCompat);
        } finally {
            root.recycle();
            listA.recycle();
            listB.recycle();
            recycleNodes(itemsA);
            recycleNodes(itemsB);
        }
    }

    @Test
    public void testSectionedGrid() {
        // This is the grid used in the test:
        // +-----------------------+
        // | Fruit (header)        |
        // +-----------------------+
        // | (1) | (2) | (3) | (4) |
        // +-----+-----+-----+-----+
        // | (5) |     |     |     |
        // +-----------------------+
        // | Vegetable (header)    |
        // +-----------------------+
        // | (1) | (2) | (3) | (4) |
        // +-----------------------+

        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setClassName(WEBVIEW_CLASS_NAME);

        List<AccessibilityNodeInfo> gridItems = new ArrayList<>();
        AccessibilityNodeInfo grid = createGrid(TEST_GRID_NAME, 5, 4);
        getShadow(root).addChild(grid);

        addGridSectionHeader("Fruit", 0, 4, grid, gridItems);     // 0
        addGridItem("Apple", 1, 0, false, grid, gridItems);       // 1
        addGridItem("Banana", 1, 1, false, grid, gridItems);      // 2
        addGridItem("Cherry", 1, 2, false, grid, gridItems);      // 3
        addGridItem("Date", 1, 3, false, grid, gridItems);        // 4
        addGridItem("Elderberry", 2, 0, false, grid, gridItems);  // 5

        addGridSectionHeader("Vegetable", 3, 4, grid, gridItems); // 6
        addGridItem("Arugula", 4, 0, false, grid, gridItems);     // 7
        addGridItem("Bok choy", 4, 1, false, grid, gridItems);    // 8
        addGridItem("Cauliflower", 4, 2, false, grid, gridItems); // 9
        addGridItem("Dill", 4, 3, false, grid, gridItems);        // 10

        try {
            // Outside of list -> Item Cherry
            AccessibilityNodeInfoCompat cherry = new AccessibilityNodeInfoCompat(gridItems.get(3));
            mCollectionState.updateCollectionInformation(cherry, null);
            assertGrid(CollectionState.NAVIGATE_ENTER /* collectionTransition */,
                    TEST_GRID_NAME /* gridName */,
                    true /* rowChanged */,
                    true /* colChanged */,
                    1 /* itemRow */, 2 /* itemCol */,
                    null /* rowName */, null /* colName */,
                    CollectionState.TYPE_NONE /* headingType */);

            // Item Cherry -> Header Fruit
            AccessibilityNodeInfoCompat fruit = new AccessibilityNodeInfoCompat(gridItems.get(0));
            mCollectionState.updateCollectionInformation(fruit, null);
            assertGrid(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_GRID_NAME /* gridName */,
                    true /* rowChanged */,
                    true /* colChanged */,
                    0 /* itemRow */, -1 /* itemCol */,
                    null /* rowName */, null /* colName */,
                    CollectionState.TYPE_INDETERMINATE /* headingType */);

            // Header Fruit -> Header Vegetable
            AccessibilityNodeInfoCompat veggie = new AccessibilityNodeInfoCompat(gridItems.get(6));
            mCollectionState.updateCollectionInformation(veggie, null);
            assertGrid(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_GRID_NAME /* gridName */,
                    true /* rowChanged */,
                    false /* colChanged */,
                    3 /* itemRow */, -1 /* itemCol */,
                    null /* rowName */, null /* colName */,
                    CollectionState.TYPE_INDETERMINATE /* headingType */);

            // Header Vegetable -> Item Arugula
            AccessibilityNodeInfoCompat arugula = new AccessibilityNodeInfoCompat(gridItems.get(7));
            mCollectionState.updateCollectionInformation(arugula, null);
            assertGrid(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_GRID_NAME /* gridName */,
                    true /* rowChanged */,
                    true /* colChanged */,
                    4 /* itemRow */, 0 /* itemCol */,
                    null /* rowName */, null /* colName */,
                    CollectionState.TYPE_NONE /* headingType */);

            // Item Arugula -> Item Bok choy
            AccessibilityNodeInfoCompat bokchoy = new AccessibilityNodeInfoCompat(gridItems.get(8));
            mCollectionState.updateCollectionInformation(bokchoy, null);
            assertGrid(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_GRID_NAME /* gridName */,
                    false /* rowChanged */,
                    true /* colChanged */,
                    4 /* itemRow */, 1 /* itemCol */,
                    null /* rowName */, null /* colName */,
                    CollectionState.TYPE_NONE /* headingType */);

            AccessibilityNodeInfoCompat rootCompat = new AccessibilityNodeInfoCompat(root);
            forceInternalRecycle(rootCompat);
        } finally {
            root.recycle();
            grid.recycle();
            recycleNodes(gridItems);
        }
    }

    @Test
    public void testDataTableGrid() {
        // This is the grid used in the test:
        // Name      Shape      Size
        // ====      =====      ====
        // Apple     Round      Medium
        // Banana    Cylinder   Medium
        // Cherry    Round      Small

        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setClassName(WEBVIEW_CLASS_NAME);

        List<AccessibilityNodeInfo> gridItems = new ArrayList<>();
        AccessibilityNodeInfo grid = createGrid(TEST_GRID_NAME, 4, 3);
        getShadow(root).addChild(grid);

        addGridItem("Name", 0, 0, true, grid, gridItems);     // 0
        addGridItem("Shape", 0, 1, true, grid, gridItems);    // 1
        addGridItem("Size", 0, 2, true, grid, gridItems);     // 2

        addGridItem("Apple", 1, 0, false, grid, gridItems);    // 3
        addGridItem("Round", 1, 1, false, grid, gridItems);    // 4
        addGridItem("Medium", 1, 2, false, grid, gridItems);   // 5

        addGridItem("Banana", 2, 0, false, grid, gridItems);   // 6
        addGridItem("Cylinder", 2, 1, false, grid, gridItems); // 7
        addGridItem("Medium", 2, 2, false, grid, gridItems);   // 8

        addGridItem("Cherry", 3, 0, false, grid, gridItems);   // 9
        addGridItem("Round", 3, 1, false, grid, gridItems);    // 10
        addGridItem("Small", 3, 2, false, grid, gridItems);    // 11

        try {
            // Outside of list -> Item Small (row 3, col 2)
            AccessibilityNodeInfoCompat small = new AccessibilityNodeInfoCompat(gridItems.get(11));
            mCollectionState.updateCollectionInformation(small, null);
            assertGrid(CollectionState.NAVIGATE_ENTER /* collectionTransition */,
                    TEST_GRID_NAME /* gridName */,
                    true /* rowChanged */,
                    true /* colChanged */,
                    3 /* itemRow */, 2 /* itemCol */,
                    null /* rowName */, "Size" /* colName */,
                    CollectionState.TYPE_NONE /* headingType */);

            // Item Small (row 3, col 2) -> Item Round (row 3, col 1)
            AccessibilityNodeInfoCompat roundA = new AccessibilityNodeInfoCompat(gridItems.get(10));
            mCollectionState.updateCollectionInformation(roundA, null);
            assertGrid(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_GRID_NAME /* gridName */,
                    false /* rowChanged */,
                    true /* colChanged */,
                    3 /* itemRow */, 1 /* itemCol */,
                    null /* rowName */, "Shape" /* colName */,
                    CollectionState.TYPE_NONE /* headingType */);

            // Item Round (row 3, col 1) -> Item Round (row 1, col 1)
            AccessibilityNodeInfoCompat roundB = new AccessibilityNodeInfoCompat(gridItems.get(4));
            mCollectionState.updateCollectionInformation(roundB, null);
            assertGrid(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_GRID_NAME /* gridName */,
                    true /* rowChanged */,
                    false /* colChanged */,
                    1 /* itemRow */, 1 /* itemCol */,
                    null /* rowName */, "Shape" /* colName */,
                    CollectionState.TYPE_NONE /* headingType */);

            // Item Round (row 1, col 1) -> Header Shape
            AccessibilityNodeInfoCompat shape = new AccessibilityNodeInfoCompat(gridItems.get(1));
            mCollectionState.updateCollectionInformation(shape, null);
            assertGrid(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_GRID_NAME /* gridName */,
                    true /* rowChanged */,
                    false /* colChanged */,
                    0 /* itemRow */, 1 /* itemCol */,
                    null /* rowName */, "Shape" /* colName */,
                    CollectionState.TYPE_COLUMN /* headingType */);

            // Header Shape -> Item Apple
            AccessibilityNodeInfoCompat apple = new AccessibilityNodeInfoCompat(gridItems.get(3));
            mCollectionState.updateCollectionInformation(apple, null);
            assertGrid(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_GRID_NAME /* gridName */,
                    true /* rowChanged */,
                    true /* colChanged */,
                    1 /* itemRow */, 0 /* itemCol */,
                    null /* rowName */, "Name" /* colName */,
                    CollectionState.TYPE_NONE /* headingType */);

            AccessibilityNodeInfoCompat rootCompat = new AccessibilityNodeInfoCompat(root);
            forceInternalRecycle(rootCompat);
        } finally {
            root.recycle();
            grid.recycle();
            recycleNodes(gridItems);
        }
    }

    @Test
    public void testIncompleteInformation() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setClassName(WEBVIEW_CLASS_NAME);

        List<AccessibilityNodeInfo> listItems = new ArrayList<>();
        AccessibilityNodeInfo list = createList(TEST_LIST_NAME_1,
                5 /* numItems */,
                -1 /* headingIndex */,
                listItems);
        getShadow(root).addChild(list);

        try {
            // Set the first collection item info to null,
            listItems.get(0).setCollectionItemInfo(null);
            AccessibilityNodeInfoCompat item0 = new AccessibilityNodeInfoCompat(listItems.get(0));
            mCollectionState.updateCollectionInformation(item0, null);
            assertList(CollectionState.NAVIGATE_ENTER /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    false /* rowChanged */);

            // Move to item with actual collection item info.
            AccessibilityNodeInfoCompat item1 = new AccessibilityNodeInfoCompat(listItems.get(1));
            mCollectionState.updateCollectionInformation(item1, null);
            assertList(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    true /* rowChanged */,
                    1 /* itemIndex */,
                    5 /* totalItems */,
                    false /* isItemHeader */);

            AccessibilityNodeInfoCompat rootCompat = new AccessibilityNodeInfoCompat(root);
            forceInternalRecycle(rootCompat);
        } finally {
            root.recycle();
            list.recycle();
            recycleNodes(listItems);
        }
    }

    @Test
    public void test1x1_shouldNotEnter() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setClassName(WEBVIEW_CLASS_NAME);

        List<AccessibilityNodeInfo> listItems = new ArrayList<>();
        AccessibilityNodeInfo list = createList(TEST_LIST_NAME_1,
                1 /* numItems */,
                -1 /* headingIndex */,
                listItems);
        getShadow(root).addChild(list);

        try {
            AccessibilityNodeInfoCompat item0 = new AccessibilityNodeInfoCompat(listItems.get(0));
            mCollectionState.updateCollectionInformation(item0, null);
            assertEquals(Role.ROLE_NONE, mCollectionState.getCollectionRole());
            assertEquals(CollectionState.NAVIGATE_NONE, mCollectionState.getCollectionTransition());

            AccessibilityNodeInfoCompat rootCompat = new AccessibilityNodeInfoCompat(root);
            forceInternalRecycle(rootCompat);
        } finally {
            root.recycle();
            list.recycle();
            recycleNodes(listItems);
        }
    }

    @Test
    public void testMalformedListView() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setClassName(WEBVIEW_CLASS_NAME);

        // Create list w/o CollectionInfo.
        AccessibilityNodeInfo list = AccessibilityNodeInfo.obtain();
        list.setContentDescription(TEST_LIST_NAME_1);
        list.setClassName(ListView.class.getName());
        getShadow(root).addChild(list);

        // Add list items w/o CollectionItemInfo.
        List<AccessibilityNodeInfo> items = new ArrayList<>();
        for (int i = 0; i < 5; ++i) {
            AccessibilityNodeInfo child = AccessibilityNodeInfo.obtain();
            child.setContentDescription("Item " + i);
            getShadow(list).addChild(child);
            items.add(child);
        }

        // CollectionState should recognize we're going into the ListView, but it doesn't need to
        // figure out list item indices.
        try {
            // Move to first item.
            AccessibilityNodeInfoCompat item0 = new AccessibilityNodeInfoCompat(items.get(0));
            mCollectionState.updateCollectionInformation(item0, null);
            assertList(CollectionState.NAVIGATE_ENTER /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    false /* rowChanged */);

            // Move to second item.
            AccessibilityNodeInfoCompat item1 = new AccessibilityNodeInfoCompat(items.get(1));
            mCollectionState.updateCollectionInformation(item1, null);
            assertList(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    false /* rowChanged */);

            AccessibilityNodeInfoCompat rootCompat = new AccessibilityNodeInfoCompat(root);
            forceInternalRecycle(rootCompat);
        } finally {
            root.recycle();
            list.recycle();
            recycleNodes(items);
        }
    }

    @Test
    public void testWorkarounds_PreN_ListView() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();

        List<AccessibilityNodeInfo> items = new ArrayList<>();
        AccessibilityNodeInfo list = createList(TEST_LIST_NAME_1, 10, 0, items);
        list.setClassName(ListView.class.getName());
        getShadow(root).addChild(list);

        // Headers and item indices should be omitted inside ListViews (pre-N, no WebView ancestor).
        // Rows should change between items (so we can possible announce headers), even though
        // the display row remains -1.
        try {
            // Outside of list -> Item 0
            AccessibilityNodeInfoCompat item0 = new AccessibilityNodeInfoCompat(items.get(0));
            mCollectionState.updateCollectionInformation(item0, null);
            assertList(CollectionState.NAVIGATE_ENTER /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    true /* rowChanged */,
                    -1 /* itemIndex */,
                    -1 /* totalItems */,
                    false /* isItemHeader */);

            // Item 0 -> Item 1
            AccessibilityNodeInfoCompat item1 = new AccessibilityNodeInfoCompat(items.get(1));
            mCollectionState.updateCollectionInformation(item1, null);
            assertList(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    true /* rowChanged */,
                    -1 /* itemIndex */,
                    -1 /* totalItems */,
                    false /* isItemHeader */);

            // Item 1 -> Item 2
            AccessibilityNodeInfoCompat item2 = new AccessibilityNodeInfoCompat(items.get(2));
            mCollectionState.updateCollectionInformation(item2, null);
            assertList(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    true /* rowChanged */,
                    -1 /* itemIndex */,
                    -1 /* totalItems */,
                    false /* isItemHeader */);

            // Item 2 -> Outside of list
            AccessibilityNodeInfoCompat rootCompat = new AccessibilityNodeInfoCompat(root);
            mCollectionState.updateCollectionInformation(rootCompat, null);
            assertEquals(CollectionState.NAVIGATE_EXIT, mCollectionState.getCollectionTransition());

            forceInternalRecycle(rootCompat);
        } finally {
            root.recycle();
            list.recycle();
            recycleNodes(items);
        }
    }

    @Test
    public void testWorkarounds_PreN_RecyclerView() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();

        List<AccessibilityNodeInfo> items = new ArrayList<>();
        AccessibilityNodeInfo list = createList(TEST_LIST_NAME_1, 10, 0, items);
        list.setClassName(RECYCLERVIEW_CLASS_NAME);
        getShadow(root).addChild(list);

        // Item indices should be omitted but headers *should not* be omitted inside RecyclerViews
        // (pre-N, no WebView ancestor).
        // Rows should change between items (so we can possible announce headers), even though
        // the display row remains -1.
        try {
            // Outside of list -> Item 0
            AccessibilityNodeInfoCompat item0 = new AccessibilityNodeInfoCompat(items.get(0));
            mCollectionState.updateCollectionInformation(item0, null);
            assertList(CollectionState.NAVIGATE_ENTER /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    true /* rowChanged */,
                    -1 /* itemIndex */,
                    -1 /* totalItems */,
                    true /* isItemHeader */);

            // Item 0 -> Item 1
            AccessibilityNodeInfoCompat item1 = new AccessibilityNodeInfoCompat(items.get(1));
            mCollectionState.updateCollectionInformation(item1, null);
            assertList(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    true /* rowChanged */,
                    -1 /* itemIndex */,
                    -1 /* totalItems */,
                    false /* isItemHeader */);

            // Item 1 -> Item 2
            AccessibilityNodeInfoCompat item2 = new AccessibilityNodeInfoCompat(items.get(2));
            mCollectionState.updateCollectionInformation(item2, null);
            assertList(CollectionState.NAVIGATE_INTERIOR /* collectionTransition */,
                    TEST_LIST_NAME_1 /* listName */,
                    true /* rowChanged */,
                    -1 /* itemIndex */,
                    -1 /* totalItems */,
                    false /* isItemHeader */);

            // Item 2 -> Outside of list
            AccessibilityNodeInfoCompat rootCompat = new AccessibilityNodeInfoCompat(root);
            mCollectionState.updateCollectionInformation(rootCompat, null);
            assertEquals(CollectionState.NAVIGATE_EXIT, mCollectionState.getCollectionTransition());

            forceInternalRecycle(rootCompat);
        } finally {
            root.recycle();
            list.recycle();
            recycleNodes(items);
        }
    }

    @Test
    public void testNested_mostlyHierarchical() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setClassName(WEBVIEW_CLASS_NAME);

        AccessibilityNodeInfo list1 = AccessibilityNodeInfo.obtain();
        list1.setCollectionInfo(CollectionInfo.obtain(2, 1, true /* hierarchical */));
        getShadow(root).addChild(list1);

        AccessibilityNodeInfo list2 = AccessibilityNodeInfo.obtain();
        list2.setCollectionInfo(CollectionInfo.obtain(2, 1, true  /* hierarchical */));
        list2.setCollectionItemInfo(CollectionItemInfo.obtain(0, 1, 0, 1, false));
        getShadow(list1).addChild(list2);

        AccessibilityNodeInfo list3 = AccessibilityNodeInfo.obtain();
        list3.setCollectionInfo(CollectionInfo.obtain(2, 1, false  /* hierarchical */));
        list3.setCollectionItemInfo(CollectionItemInfo.obtain(0, 1, 0, 1, false));
        getShadow(list2).addChild(list3);

        AccessibilityNodeInfo item = AccessibilityNodeInfo.obtain();
        item.setCollectionItemInfo(CollectionItemInfo.obtain(0, 1, 0, 1, false));
        getShadow(list3).addChild(item);

        try {
            // list2 is inside of list1
            AccessibilityNodeInfoCompat listCompat2 = new AccessibilityNodeInfoCompat(list2);
            mCollectionState.updateCollectionInformation(listCompat2, null);
            assertEquals(0, mCollectionState.getCollectionLevel());

            // list3 is inside of list2
            AccessibilityNodeInfoCompat listCompat3 = new AccessibilityNodeInfoCompat(list3);
            mCollectionState.updateCollectionInformation(listCompat3, null);
            assertEquals(1, mCollectionState.getCollectionLevel());

            // item is inside of list3
            AccessibilityNodeInfoCompat itemCompat = new AccessibilityNodeInfoCompat(item);
            mCollectionState.updateCollectionInformation(itemCompat, null);
            assertEquals(-1, mCollectionState.getCollectionLevel()); // list3 not hierarchical!

            AccessibilityNodeInfoCompat rootCompat = new AccessibilityNodeInfoCompat(root);
            forceInternalRecycle(rootCompat);
        } finally {
            root.recycle();
            list1.recycle();
            list2.recycle();
            list3.recycle();
            item.recycle();
        }
    }

    @Test
    public void testNested_mostlyNonHierarchical() {
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setClassName(WEBVIEW_CLASS_NAME);

        AccessibilityNodeInfo list1 = AccessibilityNodeInfo.obtain();
        list1.setCollectionInfo(CollectionInfo.obtain(2, 1, false /* hierarchical */));
        getShadow(root).addChild(list1);

        AccessibilityNodeInfo list2 = AccessibilityNodeInfo.obtain();
        list2.setCollectionInfo(CollectionInfo.obtain(2, 1, false  /* hierarchical */));
        list2.setCollectionItemInfo(CollectionItemInfo.obtain(0, 1, 0, 1, false));
        getShadow(list1).addChild(list2);

        AccessibilityNodeInfo list3 = AccessibilityNodeInfo.obtain();
        list3.setCollectionInfo(CollectionInfo.obtain(2, 1, true  /* hierarchical */));
        list3.setCollectionItemInfo(CollectionItemInfo.obtain(0, 1, 0, 1, false));
        getShadow(list2).addChild(list3);

        AccessibilityNodeInfo item = AccessibilityNodeInfo.obtain();
        item.setCollectionItemInfo(CollectionItemInfo.obtain(0, 1, 0, 1, false));
        getShadow(list3).addChild(item);

        try {
            // list2 is inside of list1
            AccessibilityNodeInfoCompat listCompat2 = new AccessibilityNodeInfoCompat(list2);
            mCollectionState.updateCollectionInformation(listCompat2, null);
            assertEquals(-1, mCollectionState.getCollectionLevel()); // list1 not hierarchical!

            // list3 is inside of list2
            AccessibilityNodeInfoCompat listCompat3 = new AccessibilityNodeInfoCompat(list3);
            mCollectionState.updateCollectionInformation(listCompat3, null);
            assertEquals(-1, mCollectionState.getCollectionLevel()); // list2 not hierarchical!

            // item is inside of list3
            AccessibilityNodeInfoCompat itemCompat = new AccessibilityNodeInfoCompat(item);
            mCollectionState.updateCollectionInformation(itemCompat, null);
            assertEquals(0, mCollectionState.getCollectionLevel());

            AccessibilityNodeInfoCompat rootCompat = new AccessibilityNodeInfoCompat(root);
            forceInternalRecycle(rootCompat);
        } finally {
            root.recycle();
            list1.recycle();
            list2.recycle();
            list3.recycle();
            item.recycle();
        }
    }

    @Test(timeout=100)
    public void testGetHeaderText_withLoop() {
        AccessibilityNodeInfo a = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo b = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo c = AccessibilityNodeInfo.obtain();

        getShadow(a).addChild(b);
        getShadow(b).addChild(c);
        getShadow(c).addChild(a);

        assertNull(CollectionState.getHeaderText(new AccessibilityNodeInfoCompat(a)));

        a.recycle();
        b.recycle();
        c.recycle();
    }

    @Test
    public void testGetHeaderText_singleChildren() {
        AccessibilityNodeInfo a = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo b = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo c = AccessibilityNodeInfo.obtain();
        c.setText("Header");

        getShadow(a).addChild(b);
        getShadow(b).addChild(c);

        assertEquals("Header", CollectionState.getHeaderText(new AccessibilityNodeInfoCompat(a)));

        a.recycle();
        b.recycle();
        c.recycle();
    }

    @Test
    public void testGetHeaderText_multipleChildren() {
        AccessibilityNodeInfo a = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo b = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo c = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo d = AccessibilityNodeInfo.obtain();
        d.setText("Header");

        getShadow(a).addChild(b);
        getShadow(b).addChild(c);
        getShadow(b).addChild(d);

        assertNull(CollectionState.getHeaderText(new AccessibilityNodeInfoCompat(a)));

        a.recycle();
        b.recycle();
        c.recycle();
        d.recycle();
    }

    private AccessibilityNodeInfo createList(String name,
            int numItems,
            int headingIndex,
            List<AccessibilityNodeInfo> items) {
        AccessibilityNodeInfo list = AccessibilityNodeInfo.obtain();
        list.setContentDescription(name);
        list.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain(numItems, 1, false));

        ShadowAccessibilityNodeInfo shadow = getShadow(list);
        for (int i = 0; i < numItems; ++i) {
            AccessibilityNodeInfo item = AccessibilityNodeInfo.obtain();
            item.setContentDescription("Item " + i);
            item.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(i, 1, 1, 1,
                    i == headingIndex));

            shadow.addChild(item);
            items.add(item);
        }

        return list;
    }

    private AccessibilityNodeInfo createListWithJellyBeanEvents(String name,
            int numItems,
            int headingIndex,
            List<AccessibilityNodeInfo> items,
            List<AccessibilityEvent> events) {
        AccessibilityNodeInfo list = AccessibilityNodeInfo.obtain();
        list.setContentDescription(name);
        list.setClassName(ListView.class.getName());

        ShadowAccessibilityNodeInfo shadow = getShadow(list);
        for (int i = 0; i < numItems; ++i) {
            AccessibilityNodeInfo item = AccessibilityNodeInfo.obtain();
            item.setContentDescription("Item " + i);
            shadow.addChild(item);
            items.add(item);

            AccessibilityEvent event = AccessibilityEvent.obtain();
            event.setAction(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
            Bundle parcelable = new Bundle();
            parcelable.putInt(CollectionState.EVENT_ROW, i);
            parcelable.putInt(CollectionState.EVENT_COLUMN, 1);
            parcelable.putBoolean(CollectionState.EVENT_HEADING, headingIndex == i);
            event.setParcelableData(parcelable);
            events.add(event);
        }

        return list;
    }

    private AccessibilityNodeInfo createGrid(String name, int rows, int cols) {
        AccessibilityNodeInfo grid = AccessibilityNodeInfo.obtain();
        grid.setContentDescription(name);
        grid.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain(rows, cols, false));

        return grid;
    }

    private void addGridSectionHeader(String name, int row, int colSpan,
            AccessibilityNodeInfo grid,
            List<AccessibilityNodeInfo> items) {
        AccessibilityNodeInfo header = AccessibilityNodeInfo.obtain();
        header.setContentDescription(name);
        header.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(row, 1, 1,
                colSpan, true));

        ShadowAccessibilityNodeInfo gridShadow = getShadow(grid);
        gridShadow.addChild(header);
        items.add(header);
    }

    private void addGridItem(String name, int row, int col, boolean header,
            AccessibilityNodeInfo grid,
            List<AccessibilityNodeInfo> items) {
        AccessibilityNodeInfo item = AccessibilityNodeInfo.obtain();
        item.setContentDescription(name);
        item.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(row, 1, col,
                1, header));

        ShadowAccessibilityNodeInfo gridShadow = getShadow(grid);
        gridShadow.addChild(item);
        items.add(item);
    }

    private void assertList(@CollectionState.CollectionTransition int collectionTransition,
            CharSequence listName,
            boolean rowChanged) {
        assertEquals(Role.ROLE_LIST, mCollectionState.getCollectionRole());

        assertEquals(collectionTransition, mCollectionState.getCollectionTransition());
        assertCharSequenceEquals(listName, mCollectionState.getCollectionName());
        assertEquals(rowChanged,
                (mCollectionState.getRowColumnTransition() & CollectionState.TYPE_ROW) != 0);
    }

    private void assertList(@CollectionState.CollectionTransition int collectionTransition,
            CharSequence listName,
            boolean rowChanged,
            int itemIndex,
            int totalItems,
            boolean isItemHeader) {
        assertList(collectionTransition, listName, rowChanged);

        CollectionState.ListItemState itemState = mCollectionState.getListItemState();
        assertEquals(itemIndex, itemState.getIndex());
        assertEquals(totalItems, mCollectionState.getCollectionRowCount());
        assertEquals(isItemHeader, itemState.isHeading());
    }

    private void assertGrid(@CollectionState.CollectionTransition int collectionTransition,
            CharSequence gridName,
            boolean rowChanged,
            boolean colChanged,
            int itemRow,
            int itemCol,
            CharSequence rowName,
            CharSequence colName,
            @CollectionState.TableHeadingType int headingType) {
        assertEquals(Role.ROLE_GRID, mCollectionState.getCollectionRole());

        assertEquals(collectionTransition, mCollectionState.getCollectionTransition());
        assertCharSequenceEquals(gridName, mCollectionState.getCollectionName());
        assertEquals(rowChanged,
                (mCollectionState.getRowColumnTransition() & CollectionState.TYPE_ROW) != 0);
        assertEquals(colChanged,
                (mCollectionState.getRowColumnTransition() & CollectionState.TYPE_COLUMN) != 0);

        CollectionState.TableItemState itemState = mCollectionState.getTableItemState();
        assertEquals(itemRow, itemState.getRowIndex());
        assertEquals(itemCol, itemState.getColumnIndex());
        assertCharSequenceEquals(rowName, itemState.getRowName());
        assertCharSequenceEquals(colName, itemState.getColumnName());
        assertEquals(headingType, itemState.getHeadingType());
    }

    private void assertCharSequenceEquals(CharSequence a, CharSequence b) {
        if (a == null) {
            assertNull(b);
        } else if (b == null) {
            assertNull(a);
        } else {
            assertTrue(a.toString().equals(b.toString()));
        }
    }

    private void assertNotEquals(Object a, Object b) {
        if (a == null) {
            assertNotNull(b);
        } else {
            assertFalse(a.equals(b));
        }
    }

    private ShadowAccessibilityNodeInfo getShadow(AccessibilityNodeInfo info) {
        return (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(info);
    }

    private void recycleNodes(List<AccessibilityNodeInfo> nodes) {
        for (AccessibilityNodeInfo node : nodes) {
            node.recycle();
        }
    }

    /**
     * Forces the CollectionState into NAVIGATE_NONE state, which will cause all nodes to be
     * recycled. In all tests, the root node is outside of a collection.
     */
    private void forceInternalRecycle(AccessibilityNodeInfoCompat rootNode) {
        // Forces transition to NAVIGATE_EXIT because we move outside of any collection.
        mCollectionState.updateCollectionInformation(rootNode, null);
        // Forces transition to NAVIGATE_NONE on the second movement outside of any collection.
        mCollectionState.updateCollectionInformation(rootNode, null);
    }

}
