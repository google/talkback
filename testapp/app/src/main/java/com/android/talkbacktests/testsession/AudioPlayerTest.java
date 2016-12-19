/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.talkbacktests.testsession;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.android.talkbacktests.R;

public class AudioPlayerTest extends BaseTestContent implements View.OnClickListener {

    private static final int FLAG_PLAY = 0;
    private static final int FLAG_PAUSE = 1;

    private BackgroundMusicPlayer mBackgroundMusicPlayer;
    private ImageButton mButton;

    public AudioPlayerTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_audio_player, container, false);

        mButton = (ImageButton) view.findViewById(R.id.test_audio_player_button);
        mButton.setOnClickListener(this);
        showPlayButton();
        mBackgroundMusicPlayer = new BackgroundMusicPlayer(view.getContext());

        return view;
    }

    @Override
    public void onClick(View v) {
        switch ((Integer) mButton.getTag()) {
            case FLAG_PLAY:
                mBackgroundMusicPlayer.doInBackground(FLAG_PLAY);
                showPauseButton();
                break;
            case FLAG_PAUSE:
                mBackgroundMusicPlayer.doInBackground(FLAG_PAUSE);
                showPlayButton();
                break;
        }
    }

    private void showPlayButton() {
        mButton.setImageResource(android.R.drawable.ic_media_play);
        mButton.setContentDescription(getString(R.string.play_music_button));
        mButton.setTag(FLAG_PLAY);
    }

    private void showPauseButton() {
        mButton.setImageResource(android.R.drawable.ic_media_pause);
        mButton.setContentDescription(getString(R.string.pause_music_button));
        mButton.setTag(FLAG_PAUSE);
    }

    private static final class BackgroundMusicPlayer extends AsyncTask<Integer, Void, Void> {
        private Context mContext;
        private final MediaPlayer mPlayer;

        public BackgroundMusicPlayer(Context context) {
            super();
            mContext = context;
            mPlayer = MediaPlayer.create(mContext, R.raw.test_cbr);
            mPlayer.setLooping(true);
        }

        @Override
        protected Void doInBackground(Integer... params) {
            switch (params[0]) {
                case FLAG_PLAY:
                    mPlayer.start();
                    break;
                case FLAG_PAUSE:
                    mPlayer.pause();
                    break;
            }
            return null;
        }
    }
}