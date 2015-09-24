/*
 * Copyright (C) 2015 Google Inc.
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

package com.android.switchaccess.test;

import android.graphics.Rect;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.util.ReflectionHelpers;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Shadow of AccessibilityNodeInfo that allows a test to set properties that are locked in the
 * original class. It also keeps track of calls to {@code obtain()} and {@code recycle()} to look
 * for bugs that mismatches.
 *
 * <p>TODO(newmans): Refactor this to use a bitmask for actions instead of individual booleans.
 */
@Implements(AccessibilityNodeInfo.class)
public class ShadowAccessibilityNodeInfo {

    // Map of obtained instances of the class along with stack traces of how they were obtained
    private static final Map<StrictEqualityNodeWrapper, StackTraceElement[]> mObtainedInstances =
            new HashMap<>();

    private List<AccessibilityNodeInfo> mChildren;

    private Rect mBoundsInScreen = new Rect();

    private List<Integer> mPerformedActionList;

    private boolean mIsClickable;

    private boolean mIsLongClickable;

    private boolean mIsAccessibilityFocused;

    private boolean mIsFocusable;

    private boolean mIsFocused;

    private boolean mIsVisibleToUser;

    private boolean mIsScrollable;

    private boolean mIsPasteable;

    private boolean mIsEditable;

    private AccessibilityNodeInfo mParent;

    private AccessibilityNodeInfo mLabelFor;

    private AccessibilityNodeInfo mLabeledBy;

    private View mView;

    private CharSequence mContentDescription;

    private int mGranularities;

    private CharSequence mText;

    private CharSequence mClassName;

    @RealObject private AccessibilityNodeInfo mRealAccessibilityNodeInfo;

    @Implementation
    public static AccessibilityNodeInfo obtain(AccessibilityNodeInfo info) {
        final ShadowAccessibilityNodeInfo shadowInfo = ((ShadowAccessibilityNodeInfo)
                ShadowExtractor.extract(info));
        final AccessibilityNodeInfo obtainedInstance = shadowInfo.getClone();

        mObtainedInstances.put(
                new StrictEqualityNodeWrapper(obtainedInstance),
                Thread.currentThread().getStackTrace());
        return obtainedInstance;
    }

    @Implementation
    public static AccessibilityNodeInfo obtain(View view) {
        // We explicitly avoid allocating the AccessibilityNodeInfo from the actual pool by using
        // the private constructor. Not doing so affects test suites which use both shadow and
        // non-shadow objects.
        final AccessibilityNodeInfo obtainedInstance =
                (AccessibilityNodeInfo) ReflectionHelpers.callConstructor(
                        AccessibilityNodeInfo.class);
        final ShadowAccessibilityNodeInfo shadowObtained = ((ShadowAccessibilityNodeInfo)
                ShadowExtractor.extract(obtainedInstance));

        /*
         * We keep a separate list of actions for each object newly obtained from a view, and
         * perform a shallow copy during getClone. That way the list of actions performed contains
         * all actions performed on the view by the tree of nodes initialized from it. Note that
         * initializing two nodes with the same view will not merge the two lists, as so the list
         * of performed actions will not contain all actions performed on the underlying view.
         */
        shadowObtained.mPerformedActionList = new LinkedList<>();

        shadowObtained.mView = view;
        mObtainedInstances.put(
                new StrictEqualityNodeWrapper(obtainedInstance),
                Thread.currentThread().getStackTrace());
        return obtainedInstance;
    }

    @Implementation
    public static AccessibilityNodeInfo obtain() {
        return obtain(new View(RuntimeEnvironment.application.getApplicationContext()));
    }

    /**
     * Check for leaked objects that were {@code obtain}ed but never {@code recycle}d.
     * @param printUnrecycledNodesToSystemErr - if true, stack traces of calls to {@code obtain}
     * that lack matching calls to {@code recycle} are dumped to System.err.
     * @return {@code true} if there are unrecycled nodes
     */
    public static boolean areThereUnrecycledNodes(boolean printUnrecycledNodesToSystemErr) {
        if (printUnrecycledNodesToSystemErr) {
            for (final StrictEqualityNodeWrapper wrapper : mObtainedInstances.keySet()) {
                final ShadowAccessibilityNodeInfo shadow = ((ShadowAccessibilityNodeInfo)
                        ShadowExtractor.extract(wrapper.mInfo));

                System.err.println(String.format(
                        "Leaked AccessibilityNodeInfo: contentDescription = %s. Stack trace:",
                        shadow.getContentDescription()));
                for (final StackTraceElement stackTraceElement : mObtainedInstances.get(wrapper)) {
                    System.err.println(stackTraceElement.toString());
                }
            }
        }

        return (mObtainedInstances.size() != 0);
    }

    /**
     * Clear list of obtained instance objects. {@code areThereUnrecycledNodes} will always
     * return false if called immediately afterwards.
     */
    public static void resetObtainedInstances() {
        mObtainedInstances.clear();
    }

    @Implementation
    public void recycle() {
        final StrictEqualityNodeWrapper wrapper =
                new StrictEqualityNodeWrapper(mRealAccessibilityNodeInfo);
        if (!mObtainedInstances.containsKey(wrapper)) {
            throw new IllegalStateException();
        }

        if (mLabelFor != null) {
            mLabelFor.recycle();
        }
        if (mLabeledBy != null) {
            mLabeledBy.recycle();
        }
        mObtainedInstances.remove(wrapper);
    }

    @Implementation
    public int getChildCount() {
        if (mChildren == null) {
            return 0;
        }
        return mChildren.size();
    }

    @Implementation
    public AccessibilityNodeInfo getChild(int index) {
        if (mChildren == null) {
            return null;
        }

        final AccessibilityNodeInfo child = mChildren.get(index);
        if (child == null) {
            return null;
        }

        return obtain(child);
    }

    @Implementation
    public AccessibilityNodeInfo getParent() {
        if (mParent == null) {
            return null;
        }

        return obtain(mParent);
    }

    @Implementation
    public boolean isClickable() {
        return mIsClickable;
    }

    @Implementation
    public boolean isLongClickable() {
        return mIsLongClickable;
    }

    @Implementation
    public boolean isFocusable() {
        return mIsFocusable;
    }

    @Implementation
    public boolean isAccessibilityFocused() {
        return mIsAccessibilityFocused;
    }

    @Implementation
    public boolean isFocused() {
        return mIsFocused;
    }

    @Implementation
    public boolean isVisibleToUser() {
        return mIsVisibleToUser;
    }

    @Implementation
    public boolean isScrollable() {
        return mIsScrollable;
    }

    public boolean isPasteable() {
        return mIsPasteable;
    }

    @Implementation
    public boolean isEditable() {
        return mIsEditable;
    }

    @Implementation
    public void setClickable(boolean isClickable) {
        mIsClickable = isClickable;
    }

    @Implementation
    public void setLongClickable(boolean isLongClickable) {
        mIsLongClickable = isLongClickable;
    }

    @Implementation
    public void setAccessibilityFocused(boolean isAccessibilityFocused) {
        mIsAccessibilityFocused = isAccessibilityFocused;
    }

    @Implementation
    public void setFocusable(boolean isFocusable) {
        mIsFocusable = isFocusable;
    }

    @Implementation
    public void setFocused(boolean isFocused) {
        mIsFocused = isFocused;
    }

    @Implementation
    public void setScrollable(boolean isScrollable) {
        mIsScrollable = isScrollable;
    }

    public void setPasteable(boolean isPasteable) {
        mIsPasteable = isPasteable;
    }

    @Implementation
    public void setEditable(boolean isEditable) {
        mIsEditable = isEditable;
    }

    @Implementation
    public void setContentDescription(CharSequence contentDescription) {
        mContentDescription = contentDescription;
    }

    /**
     * Retrieve the node's name.
     *
     * @return The node's name as set with {@code setName()}
     */
    @Implementation
    public CharSequence getContentDescription() {
        return mContentDescription;
    }

    @Implementation
    public int getMovementGranularities() {
        return mGranularities;
    }

    @Implementation
    public void setMovementGranularities(int granularities) {
        mGranularities = granularities;
    }


    @Implementation
    public void setClassName(CharSequence className) {
        mClassName = className;
    }

    @Implementation
    public CharSequence getClassName() {
        return mClassName;
    }

    @Implementation
    public void setText(CharSequence text) {
        mText = text;
    }

    @Implementation
    public CharSequence getText() {
        return mText;
    }

    @Implementation
    public AccessibilityNodeInfo getLabelFor() {
        if (mLabelFor == null) {
            return null;
        }
        return obtain(mLabelFor);
    }

    public void setLabelFor(AccessibilityNodeInfo info) {
        if (mLabelFor != null) {
            mLabelFor.recycle();
        }
        mLabelFor = obtain(info);
    }

    @Implementation
    public AccessibilityNodeInfo getLabeledBy() {
        if (mLabeledBy == null) {
            return null;
        }
        return obtain(mLabeledBy);
    }

    public void setLabeledBy(AccessibilityNodeInfo info) {
        if (mLabeledBy != null) {
            mLabeledBy.recycle();
        }
        mLabeledBy = obtain(info);
    }

    @Implementation
    public void getBoundsInScreen(Rect outBounds) {
        outBounds.set(mBoundsInScreen);
    }

    @Implementation
    public void setBoundsInScreen(Rect boundsInScreen) {
        mBoundsInScreen.set(boundsInScreen);
    }

    /**
     * Obtain flags for actions supported. Currently only supports ACTION_CLICK, ACTION_LONG_CLICK,
     * ACTION_SCROLL_FORWARD, and ACTION_SCROLL_BACKWARD. Returned value is derived
     * for {@code isClickable()} and {@code isScrollable()}.
     *
     * @return Action mask. 0 if no actions supported.
     */
    @Implementation
    public int getActions() {
        int actions = 0;
        actions |= (isClickable()) ? AccessibilityNodeInfo.ACTION_CLICK : 0;
        actions |= (isLongClickable()) ? AccessibilityNodeInfo.ACTION_LONG_CLICK : 0;
        actions |= (isScrollable()) ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : 0;
        actions |= (isScrollable()) ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD : 0;
        actions |= (isPasteable()) ? AccessibilityNodeInfo.ACTION_PASTE : 0;
        return actions;
    }

    /**
     * Equality check based on reference equality for mParent and mView and value equality for
     * other fields.
     */
    @Implementation
    public boolean performAction(int action) {
        if (mPerformedActionList == null) {
            mPerformedActionList = new LinkedList<>();
        }
        mPerformedActionList.add(new Integer(action));
        /*
         * TODO(pweaver) Consider making the return code depend on the action and capabilities
         * of the node
         */
        return true;
    }

    @Implementation
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof AccessibilityNodeInfo)) {
            return false;
        }

        final AccessibilityNodeInfo info = (AccessibilityNodeInfo) object;
        final ShadowAccessibilityNodeInfo otherShadow =
                (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(info);

        boolean areEqual = true;
        if (mChildren == null) {
            areEqual = areEqual && (otherShadow.mChildren == null);
        } else {
            areEqual = areEqual && (otherShadow.mChildren != null)
                    && mChildren.equals(otherShadow.mChildren);
        }
        areEqual = areEqual && (mParent == otherShadow.mParent);

        areEqual = areEqual && (mIsClickable == otherShadow.mIsClickable);
        areEqual = areEqual && (mIsLongClickable == otherShadow.mIsLongClickable);
        areEqual = areEqual && (mIsAccessibilityFocused == otherShadow.mIsAccessibilityFocused);
        areEqual = areEqual && (mIsFocusable == otherShadow.mIsFocusable);
        areEqual = areEqual && (mIsFocused == otherShadow.mIsFocused);
        areEqual = areEqual && (mIsVisibleToUser == otherShadow.mIsVisibleToUser);
        areEqual = areEqual && (mIsScrollable == otherShadow.mIsScrollable);
        areEqual = areEqual && (mIsPasteable == otherShadow.mIsPasteable);
        areEqual = areEqual && (mIsEditable == otherShadow.mIsEditable);
        /*
         * These checks have the potential to become infinite loops if there are loops in
         * the labelFor or labeledBy logic. Rather than deal with this complexity, allow the
         * failure since it will indicate a problem that needs addressing.
         */
        if (mLabelFor == null) {
            areEqual = areEqual && (otherShadow.mLabelFor == null);
        } else {
            areEqual = areEqual && (mLabelFor.equals(otherShadow.mLabelFor));
        }
        if (mLabeledBy == null) {
            areEqual = areEqual && (otherShadow.mLabeledBy == null);
        } else {
            areEqual = areEqual && (mLabeledBy.equals(otherShadow.mLabeledBy));
        }
        areEqual = areEqual && mBoundsInScreen.equals(otherShadow.mBoundsInScreen);
        areEqual = areEqual &&
                (TextUtils.equals(mContentDescription, otherShadow.mContentDescription));
        areEqual = areEqual && (TextUtils.equals(mText, otherShadow.mText));
        areEqual = areEqual && TextUtils.equals(mClassName, otherShadow.mClassName);
        areEqual = areEqual && (mView == otherShadow.mView);

        return areEqual;
    }

    @Implementation
    @Override
    public int hashCode() {
        // This is 0 for a reason. If you change it, you will break the obtained instances map in
        // a manner that is remarkably difficult to debug. Having a dynamic hash code keeps this
        // object from being located in the map if it was mutated after being obtained.
        return (mView == null) ? 0 : mView.hashCode();
    }

    /**
     * Add a child node to this one. Also initializes the parent field of the child.
     *
     * @param child The node to be added as a child.
     */
    public void addChild(AccessibilityNodeInfo child) {
        if (mChildren == null) {
            mChildren = new LinkedList<>();
        }
        mChildren.add(child);
        ((ShadowAccessibilityNodeInfo)
                ShadowExtractor.extract(child)).mParent = mRealAccessibilityNodeInfo;
    }

    /**
     * @return The list of arguments for the various calls to performAction. Unmodifyible.
     */
    public List<Integer> getPerformedActions() {
        if (mPerformedActionList == null) {
            mPerformedActionList = new LinkedList<>();
        }
        return Collections.unmodifiableList(mPerformedActionList);
    }

    /**
     * Set the return value for isVisibleToUser.
     *
     * @param isVisibleToUser {@code true} if node should be considered visible to user.
     */
    @Implementation
    public void setVisibleToUser(boolean isVisibleToUser) {
        mIsVisibleToUser = isVisibleToUser;
    }

    /**
     * @return A shallow copy.
     */
    private AccessibilityNodeInfo getClone() {
        // We explicitly avoid allocating the AccessibilityNodeInfo from the actual pool by using
        // the private constructor. Not doing so affects test suites which use both shadow and
        // non-shadow objects.
        final AccessibilityNodeInfo newInfo =
                (AccessibilityNodeInfo) ReflectionHelpers.callConstructor(
                        AccessibilityNodeInfo.class);
        final ShadowAccessibilityNodeInfo newShadow =
                (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(newInfo);

        newShadow.mIsClickable = mIsClickable;
        newShadow.mIsLongClickable = mIsLongClickable;
        newShadow.mIsAccessibilityFocused = mIsAccessibilityFocused;
        newShadow.mIsFocusable = mIsFocusable;
        newShadow.mIsFocused = mIsFocused;
        newShadow.mBoundsInScreen = new Rect(mBoundsInScreen);
        newShadow.mIsVisibleToUser = mIsVisibleToUser;
        newShadow.mIsScrollable = mIsScrollable;
        newShadow.mIsPasteable = mIsPasteable;
        newShadow.mIsEditable = mIsEditable;
        newShadow.mContentDescription = mContentDescription;
        newShadow.mText = mText;
        newShadow.mPerformedActionList = mPerformedActionList;
        newShadow.mParent = mParent;
        newShadow.mClassName = mClassName;
        newShadow.mLabeledBy = mLabeledBy;
        newShadow.mView = mView;
        newShadow.mGranularities = mGranularities;
        if (mChildren != null) {
            newShadow.mChildren = new LinkedList<>();
            newShadow.mChildren.addAll(mChildren);
        } else {
            newShadow.mChildren = null;
        }

        return newInfo;
    }

    /**
     * Private class to keep different nodes referring to the same view straight in the
     * mObtainedInstances map.
     */
    private static class StrictEqualityNodeWrapper {

        public final AccessibilityNodeInfo mInfo;

        public StrictEqualityNodeWrapper(AccessibilityNodeInfo info) {
            mInfo = info;
        }

        @Override
        public boolean equals(Object object) {
            if (object == null) {
                return false;
            }

            final StrictEqualityNodeWrapper wrapper = (StrictEqualityNodeWrapper) object;
            return mInfo == wrapper.mInfo;
        }

        @Override
        public int hashCode() {
            return mInfo.hashCode();
        }
    }

    @Implements(AccessibilityNodeInfo.AccessibilityAction.class)
    public static final class ShadowAccessibilityAction {
        private int id;
        private CharSequence label;

        public void __constructor__(int id, CharSequence label) {
            this.id = id;
            this.label = label;
        }

        @Implementation
        public int getId() {
            return id;
        }

        @Implementation
        public CharSequence getLabel() {
            return label;
        }
    }
}
