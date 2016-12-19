/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.talkback.tutorial;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toolbar;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.talkback.contextmenu.MenuActionInterceptor;
import com.android.talkback.contextmenu.MenuManager;
import com.android.talkback.contextmenu.MenuTransformer;
import com.android.talkback.controller.GestureActionMonitor;
import com.android.talkback.tutorial.exercise.Exercise;
import com.google.android.marvin.talkback.TalkBackService;

public class TutorialLessonFragment extends Fragment implements View.OnClickListener,
        Exercise.ExerciseCallback, GestureActionMonitor.GestureActionListener {

    private static final int DELAY_BEFORE_ANNOUNCE_LESSON = 100;
    private static final int DELAY_BEFORE_AUTO_MOVE_TO_NEXT_LESSON = 1000;

    private TutorialController mTutorialController;
    private TutorialLesson mLesson;
    private TutorialLessonPage mPage;
    private Exercise mExercise;
    private int mCurrentPage;
    private TutorialNavigationCallback mCallback;
    private TextView mDescription;
    private SpeechController mSpeechController;
    private GestureActionMonitor mActionMonitor = new GestureActionMonitor();
    private Handler mHandler = new Handler(Looper.getMainLooper());

    public void setLesson(TutorialLesson lesson, int currentPage) {
        mLesson = lesson;
        mPage = mLesson.getLessonPage(currentPage);
        mCurrentPage = currentPage;
        mExercise = mPage.getExercise();
    }

    public void setTutorialController(TutorialController tutorialController) {
        mTutorialController = tutorialController;
    }

    public void setTutorialNavigationCallback(TutorialNavigationCallback callback) {
        mCallback = callback;
    }

    @Override
    public void onCreate(Bundle instance) {
        super.onCreate(instance);
        setRetainInstance(true);
        setHasOptionsMenu(true);
        mExercise.setExerciseCallBack(this);

        TalkBackService service = TalkBackService.getInstance();
        if (service != null) {
            mSpeechController = service.getSpeechController();
        }

        mActionMonitor.setListener(this);
    }

    @Override
    public void onStart() {
        super.onStart();

        MenuTransformer menuTransformer = mExercise.getContextMenuTransformer();
        MenuActionInterceptor menuActionInterceptor = mExercise.getContextMenuActionInterceptor();
        TalkBackService service = TalkBackService.getInstance();
        if (service != null) {
            MenuManager menuManager = service.getMenuManager();
            menuManager.setMenuTransformer(menuTransformer);
            menuManager.setMenuActionInterceptor(menuActionInterceptor);
        }

        // We need to post the announcements delayed in order to ensure that the view changed
        // event gets sent beforehand. This makes the TalkBack speech response flow more logical.
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                View view = getView();
                if (view == null) {
                    // Something terrible has happened, e.g. the fragment is gone.
                    return;
                }

                view.announceForAccessibility(getTitle());
                view.announceForAccessibility(mPage.getSubtitle());
                mDescription.sendAccessibilityEvent(
                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
            }
        }, DELAY_BEFORE_ANNOUNCE_LESSON);
        mExercise.onInitialized(getActivity());
    }

    @Override
    public void onStop() {
        super.onStop();
        mExercise.clear();
        TalkBackService service = TalkBackService.getInstance();
        if (service != null) {
            service.getMenuManager().setMenuTransformer(null);
            service.getMenuManager().setMenuActionInterceptor(null);
            service.getSpeechController().interrupt();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        if (activity != null) {
            ActionBar actionBar = activity.getActionBar();
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setDisplayShowHomeEnabled(false);
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setCustomView(R.layout.tutorial_action_bar);
            actionBar.getCustomView().findViewById(R.id.up).setOnClickListener(this);
            TextView title = (TextView) actionBar.getCustomView().
                    findViewById(R.id.action_bar_title);
            title.setText(getTitle());
            LocalBroadcastManager.getInstance(activity).registerReceiver(mActionMonitor,
                    GestureActionMonitor.FILTER);
        }

        TalkBackService service = TalkBackService.getInstance();
        if (service != null) {
            service.addEventListener(mExercise);
        }
    }

    private String getTitle() {
        if (!TextUtils.isEmpty(mPage.getTitle())) {
            return mPage.getTitle();
        } else {
            return mLesson.getTitle();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        TalkBackService service = TalkBackService.getInstance();
        if (service != null) {
            service.postRemoveEventListener(mExercise);
        }

        Activity activity = getActivity();
        if (activity != null) {
            LocalBroadcastManager.getInstance(activity).unregisterReceiver(mActionMonitor);
        }

        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        int layoutId;
        if (mExercise.needScrollableContainer()) {
            layoutId = R.layout.tutorial_lesson_fragment_scrollable;
        } else {
            layoutId = R.layout.tutorial_lesson_fragment;
        }
        View view = inflater.inflate(layoutId, container, false);

        mDescription = (TextView) view.findViewById(R.id.description);
        mDescription.setText(mPage.getDescription());

        TextView subTitle = (TextView) view.findViewById(R.id.part_subtitle);
        subTitle.setText(mPage.getSubtitle());

        TextView currentPage = (TextView) view.findViewById(R.id.current_page);
        TextView next = (TextView) view.findViewById(R.id.next);
        if (mCurrentPage < mLesson.getPagesCount() - 1) {
            next.setText(R.string.tutorial_next);
            currentPage.setVisibility(View.VISIBLE);
            currentPage.setText(getString(R.string.tutorial_page_number_of, mCurrentPage + 1,
                    mLesson.getPagesCount() - 1));
        } else if (mTutorialController.getNextLesson(mLesson) == null) {
            next.setText(R.string.tutorial_home);
        } else {
            next.setText(R.string.tutorial_next_lesson);
        }
        next.setOnClickListener(this);

        View previous = view.findViewById(R.id.previous_page);
        previous.setOnClickListener(this);
        previous.setContentDescription(getString(R.string.tutorial_previous));

        ViewGroup contentContainer = (ViewGroup) view.findViewById(R.id.practice_area);
        View contentView = mPage.getExercise().getContentView(inflater, contentContainer);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        contentContainer.addView(contentView, params);

        return view;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.next:
                if (mCallback != null) {
                    mCallback.onNextPageClicked(mLesson, mCurrentPage);
                }
                break;
            case R.id.previous_page:
                if (mCallback != null) {
                    mCallback.onPreviousPageClicked(mLesson, mCurrentPage);
                }
                break;
            case R.id.up:
                mCallback.onNavigateUpClicked();
                break;
        }
    }

    @Override
    public void onExerciseCompleted(boolean autoSwitchLesson, int completeMessageResId) {
        if (mSpeechController == null) {
            return;
        }

        SpeechController.UtteranceCompleteRunnable speechCallback = null;

        if (autoSwitchLesson) {
            speechCallback = new SpeechController.UtteranceCompleteRunnable() {
                @Override
                public void run(int status) {
                    moveToNextPageWithDelay(DELAY_BEFORE_AUTO_MOVE_TO_NEXT_LESSON);
                }
            };
        }
        notifyExerciseCompleted(speechCallback, completeMessageResId);
    }

    private void notifyExerciseCompleted(final SpeechController.UtteranceCompleteRunnable callback,
                                         final int completeMessageResId) {
        if (!isResumed()) {
            return;
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mSpeechController.speak(getString(completeMessageResId), null,
                        null, SpeechController.QUEUE_MODE_UNINTERRUPTIBLE, 0, 0,
                        null, null, callback);
            }
        }, DELAY_BEFORE_AUTO_MOVE_TO_NEXT_LESSON);
    }

    private void moveToNextPageWithDelay(long delay) {
        if (!isResumed()) {
            return;
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (getActivity() != null) {
                    mCallback.onNextPageClicked(mLesson, mCurrentPage);
                }
            }
        }, delay);
    }

    @Override
    public void onGestureAction(String action) {
        if (mExercise == null || TextUtils.isEmpty(action) || getActivity() == null) {
            return;
        }

        mExercise.onAction(getActivity(), action);
    }
}
