/*
 * Copyright (C) 2010 The Android Open Source Project
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
 */

package com.android.systemui.statusbar.policy.toggles;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.ToggleSlider;
import com.android.systemui.statusbar.policy.ToggleSlider.Listener;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Slog;
import android.view.IWindowManager;
import android.view.View;
import android.widget.CompoundButton;

public class VolumeSlider implements ToggleSlider.Listener {
    private static final String TAG = "StatusBar.VolumeController";

    private int mVolumeLevel = 0;

    private int mMaximumVolume = android.os.Power.BRIGHTNESS_ON;

    private Context mContext;
    private ToggleSlider mControl;
    private IPowerManager mPower;
    private View mView;

    boolean mSystemChange;

    boolean mAsyncChange;

    public VolumeSlider(Context context) {
        mContext = context;
        mView = View.inflate(mContext, R.layout.volume_slider, null);

        mControl = (ToggleSlider) mView.findViewById(R.id.volume);

        AudioManager audioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        
        mVolumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        
        mMaximumVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        boolean automaticAvailable = context.getResources().getBoolean(
                com.android.internal.R.bool.config_automatic_brightness_available);
        mPower = IPowerManager.Stub.asInterface(ServiceManager.getService("power"));

        mControl.setChecked(false);

        mControl.setMax(mMaximumVolume);
        mControl.setValue(mVolumeLevel);

        mControl.setOnChangedListener(this);
    }

    public View getView() {
        return mView;
    }

    public void onChanged(ToggleSlider view, boolean tracking, boolean mute, int value) {
        if (mSystemChange)
            return;
        setMute(mute);
        if (!mute) {
            setVolume(value);
        }
    }

    private void setVolume(int volume) {
      	AudioManager audioManager= (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
    }

    private void setMute(boolean mute) {
      	AudioManager audioManager= (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, mute);
    }
}
