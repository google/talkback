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

package com.google.android.accessibility.talkback.tutorial;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ServiceStateListener;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.output.SpeechController;

public class AccessibilityTutorialActivity extends AppCompatActivity
    implements TutorialNavigationCallback, AccessibilityEventListener {
  //timeout is 10s to auto-navigate from tutorial home page to lesson 1.
  private static final int AUTO_NAVIGATION_TIMEOUT = 10000;

  private static final String MAIN_FRAGMENT_NAME = "MAIN_FRAGMENT_NAME";
  private static AccessibilityTutorialActivity sActiveTutorial;

  /** Event types that are handled by AccessibilityTutorialActivity. */
  private static final int MASK_EVENTS_HANDLED_BY_A11Y_TUTORIAL_ACTIVITY =
      AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;

  private final Handler mHandler = new Handler();
  private Runnable mRunnable;

  private static final String UNKNOWN_TUTORIAL_INTENT_SRC = "unkownSrc";

  public static boolean isTutorialActive() {
    return sActiveTutorial != null;
  }

  public static void stopActiveTutorial() {
    if (sActiveTutorial != null) {
      sActiveTutorial.finish();
    }
  }

  private TutorialController mTutorialController;

  /**
   * Processes the extra data tagged along with the intent to start the tutorial
   *
   * @param tutorialIntent
   * @return source that sent the intent to start the tutorial
   */
  private String processExtraData(Intent tutorialIntent) {
    String tutorialSrc =
        tutorialIntent.getStringExtra(TalkBackService.EXTRA_TUTORIAL_INTENT_SOURCE);
    if (tutorialSrc == null) {
      return UNKNOWN_TUTORIAL_INTENT_SRC;
    }
    return tutorialSrc;
  }

  @Override
  public void onCreate(Bundle savedState) {
    super.onCreate(savedState);
    if (!isTablet()) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
    }

    setContentView(R.layout.tutorial_activity);
    try {
      mTutorialController = new TutorialController(this);
    } catch (Exception e) {
      LogUtils.log(Log.ERROR, "failed to create tutorial");
      finish();
      return;
    }
    getSupportFragmentManager()
        .addOnBackStackChangedListener(
            new FragmentManager.OnBackStackChangedListener() {
              @Override
              public void onBackStackChanged() {
                if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                  finish();
                }
              }
            });

    setTitle(R.string.tutorial_title);

    // compat action bar must be set programmatically
    Toolbar toolbar = (Toolbar) findViewById(R.id.tutorial_toolbar);
    setSupportActionBar(toolbar);

    TutorialMainFragment mainFragment = new TutorialMainFragment();
    mainFragment.setOnLessonSelectedCallback(this);
    mainFragment.setTutorialController(mTutorialController);
    Intent tutorialIntent = getIntent();
    String tutorialSrc = processExtraData(tutorialIntent);
    mainFragment.setIfBackNavigationReq(tutorialSrc);
    switchFragment(mainFragment, MAIN_FRAGMENT_NAME);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  public boolean isTablet() {
    return getResources().getBoolean(R.bool.is_tablet);
  }

  @Override
  public void onStart() {
    super.onStart();
    sActiveTutorial = this;

    /*
     * Handle the cases where the tutorial was started with TalkBack in an
     * invalid state (inactive, suspended, or without Explore by Touch
     * enabled).
     */
    final int serviceState = TalkBackService.getServiceState();
    final AccessibilityManager accessibilityManager =
        (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
    /*
     * Check for suspended state first because touch exploration reports it
     * is disabled when TalkBack is suspended.
     */
    if (serviceState == ServiceStateListener.SERVICE_STATE_SUSPENDED) {
      showAlertDialogAndFinish(
          R.string.tutorial_service_suspended_title, R.string.tutorial_service_suspended_message);
      return;
    } else if ((serviceState == ServiceStateListener.SERVICE_STATE_INACTIVE)) {
      showAlertDialogAndFinish(
          R.string.tutorial_service_inactive_title, R.string.tutorial_service_inactive_message);
      return;
    } else if (!accessibilityManager.isTouchExplorationEnabled()) {
      showAlertDialogAndFinish(
          R.string.tutorial_no_touch_explore_title, R.string.tutorial_no_touch_explore_message);
      return;
    }

    TalkBackService service = TalkBackService.getInstance();
    if (service != null) {
      service.setMenuManagerToList();
    }

    SharedPreferences preferences =
        SharedPreferencesUtils.getSharedPreferences(getApplicationContext());

    if (preferences.getBoolean(TalkBackService.PREF_FIRST_TIME_USER, true)) {
      final SharedPreferences.Editor editor = preferences.edit();
      editor.putBoolean(TalkBackService.PREF_FIRST_TIME_USER, false);
      editor.apply();
      onFirstTimeLaunch();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    sActiveTutorial = null;
    TalkBackService service = TalkBackService.getInstance();
    if (service != null) {
      service.updateMenuManagerFromPreferences();
    }
    if (mRunnable != null) {
      mHandler.removeCallbacks(mRunnable);
      if (service != null) {
        service.postRemoveEventListener(this);
      }
      mRunnable = null;
    }
  }

  private void onFirstTimeLaunch() {
    final TalkBackService service = TalkBackService.getInstance();
    if (service == null) {
      return;
    }

    SpeechController.UtteranceCompleteRunnable utteranceCompleteRunnable =
        new SpeechController.UtteranceCompleteRunnable() {
          @Override
          public void run(int status) {
            switch (status) {
              case SpeechController.STATUS_SPOKEN:
                postSwitchToFirstLesson();
                break;
              default:
                break;
            }
          }
        };
    TextView description = (TextView) findViewById(R.id.description);
    service
        .getSpeechController()
        .speak(
            description.getText(), /* text */
            null, /* earcons */
            null, /* haptics */
            SpeechController.QUEUE_MODE_QUEUE, /* queueMode */
            0, /* flags */
            SpeechController.UTTERANCE_GROUP_DEFAULT, /* utteranceGroup */
            null, /* speechParams */
            null, /* nonSpeechParams */
            null /* startAction */,
            null, /* rangeStartCallback */
            utteranceCompleteRunnable /* completeAction */,
            (EventId) null);
  }

  private void postSwitchToFirstLesson() {
    final TalkBackService service = TalkBackService.getInstance();
    mRunnable =
        new Runnable() {
          @Override
          public void run() {
            // Advance to lesson 1 only if the Tutorial Activity is still on the foreground.
            //
            // The conditions of advancing to lesson 1 are:
            // 1. First time user
            // 2. The announcement of TalkBack description is completed and not interrupted.
            // 3. Wait for 10s without new accessibility focus set on the screen.
            // There is a case where the tutorial activity goes background without condition 2
            // and 3 being violated: Another activity or alert dialog pops up automatically (The
            // announcement of window title is queued up, thus announcement in 2 will not be
            // interrupted). In that case we should not open lesson 1 to avoid crash.
            if (sActiveTutorial != null) {
              service.postRemoveEventListener(AccessibilityTutorialActivity.this);
              service
                  .getSpeechController()
                  .speak(
                      service.getString(R.string.notification_tutorial_navigate_to_lession_1),
                      // After navigating to lesson 1, the announcement of the lesson title
                      // and subtitle will interrupt the content in speech queue. Thus we
                      // should make this notification uninterruptible.
                      SpeechController.QUEUE_MODE_UNINTERRUPTIBLE,
                      0, /* flags */
                      null, /* SpeechParams */
                      (EventId) null);
              showLesson(mTutorialController.getTutorial().getLesson(0), 0);
              mRunnable = null;
            }
          }
        };
    service.addEventListener(AccessibilityTutorialActivity.this);
    mHandler.postDelayed(mRunnable, AUTO_NAVIGATION_TIMEOUT);
  }

  private void switchFragment(Fragment fragment, String name) {
    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
    transaction.replace(R.id.fragment_holder, fragment);
    transaction.addToBackStack(name);
    transaction.commit();
  }

  @Override
  public void onLessonSelected(TutorialLesson lesson) {
    showLesson(lesson, 0);
  }

  @Override
  public void onLessonPracticeSelected(TutorialLesson lesson) {
    showLesson(lesson, lesson.getPracticePage());
  }

  @Override
  public void onNextPageClicked(TutorialLesson lesson, int currentPageIndex) {
    int nextPage = currentPageIndex + 1;
    if (lesson.getPagesCount() > nextPage) {
      showLesson(lesson, nextPage);
    } else {
      TutorialLesson nextLesson = mTutorialController.getNextLesson(lesson);
      if (nextLesson != null) {
        onLessonSelected(nextLesson);
      } else {
        returnToMainFragment();
      }
    }
  }

  @Override
  public void onPreviousPageClicked(TutorialLesson lesson, int currentPageIndex) {
    int nextPage = currentPageIndex - 1;
    if (nextPage < 0) {
      returnToMainFragment();
    } else {
      showLesson(lesson, nextPage);
    }
  }

  @Override
  public void onNavigateUpClicked() {
    returnToMainFragment();
  }

  private void showLesson(TutorialLesson lesson, int pageNumber) {
    TutorialLessonFragment lessonFragment = new TutorialLessonFragment();
    lessonFragment.setLesson(lesson, pageNumber);
    lessonFragment.setTutorialController(mTutorialController);
    lessonFragment.setTutorialNavigationCallback(this);
    switchFragment(lessonFragment, null);
  }

  private void returnToMainFragment() {
    getSupportFragmentManager().popBackStack(MAIN_FRAGMENT_NAME, 0);
    getWindow().getDecorView().announceForAccessibility(getTitle());
  }

  private final OnCancelListener mFinishActivityOnCancelListener =
      new OnCancelListener() {
        @Override
        public void onCancel(DialogInterface dialog) {
          finish();
        }
      };

  private final OnClickListener mFinishActivityOnClickListener =
      new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          finish();
        }
      };

  private void showAlertDialogAndFinish(int titleId, int messageId) {
    showAlertDialogAndFinish(getString(titleId), getString(messageId));
  }

  private void showAlertDialogAndFinish(String title, String message) {
    returnToMainFragment();

    new AlertDialog.Builder(AccessibilityTutorialActivity.this)
        .setTitle(title)
        .setMessage(message)
        .setCancelable(true)
        .setOnCancelListener(mFinishActivityOnCancelListener)
        .setPositiveButton(R.string.tutorial_alert_dialog_exit, mFinishActivityOnClickListener)
        .create()
        .show();
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_A11Y_TUTORIAL_ACTIVITY;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
      if (mRunnable != null) {
        mHandler.removeCallbacks(mRunnable);
        TalkBackService service = TalkBackService.getInstance();
        if (service != null) {
          service.postRemoveEventListener(this);
        }
        mRunnable = null;
      }
    }
  }
}
