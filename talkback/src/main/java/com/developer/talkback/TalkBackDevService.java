package com.developer.talkback;

import static android.media.AudioManager.STREAM_ACCESSIBILITY;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;

import com.google.android.marvin.talkback.TalkBackService;

// INFO: TalkBack For Developers modification
public class TalkBackDevService extends TalkBackService {
    private SharedPreferences prefs;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AudioManager audioManager = (AudioManager) getBaseContext().getSystemService(Context.AUDIO_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            int minVolume = Math.max(audioManager.getStreamMinVolume(STREAM_ACCESSIBILITY), 1);
            audioManager.setStreamVolume(STREAM_ACCESSIBILITY, minVolume, 0);
        }
    }
}