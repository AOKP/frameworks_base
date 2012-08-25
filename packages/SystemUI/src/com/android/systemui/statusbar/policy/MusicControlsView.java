/*
 * Copyright (C) 2012 Jonathon Grigg
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import com.android.systemui.R;

public class MusicControlsView extends RelativeLayout implements OnClickListener {
    private ImageButton mPlayPauseButton;
    private ImageButton mPreviousButton;
    private ImageButton mNextButton;
	private SeekBar mVolumeSeekbar;
	private int mPlayState = -1;		// 0 = paused, 1 = playing

    private AudioManager am = (AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
	
    public MusicControlsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        IntentFilter volumeChangedIntentFilter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        context.registerReceiver(mVolumeChangedReceiver, volumeChangedIntentFilter);
    }
    
    private BroadcastReceiver mVolumeChangedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateVolumeSeekbar(mVolumeSeekbar);
		}
	};

    private void sendMediaButtonEvent(int code) {
        long eventtime = SystemClock.uptimeMillis();

        Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent downEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, code, 0);
        downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
        getContext().sendOrderedBroadcast(downIntent, null);

        Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent upEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_UP, code, 0);
        upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
        getContext().sendOrderedBroadcast(upIntent, null);
    }
    
    private void updateVolumeSeekbar(SeekBar sb) {
        int maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int curVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        sb.setMax(maxVolume);
        sb.setProgress(curVolume);
    }

    @Override
    public void onFinishInflate() {
    	super.onFinishInflate();
    	mPreviousButton = (ImageButton) findViewById(R.id.music_previous_button);
    	mPlayPauseButton = (ImageButton) findViewById(R.id.music_play_pause_button);
    	mNextButton = (ImageButton) findViewById(R.id.music_next_button);
    	updatePlayPauseState(am.isMusicActive());
    	final View buttons[] = { mPreviousButton, mPlayPauseButton, mNextButton };
    	for (View view : buttons) {
    		view.setOnClickListener(this);
    	}
    	mVolumeSeekbar = (SeekBar) findViewById(R.id.music_volume_seekbar);
    	updateVolumeSeekbar(mVolumeSeekbar);
    	mVolumeSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
    		@Override
    		public void onStopTrackingTouch(SeekBar sb) {
    		}

    		@Override
    		public void onStartTrackingTouch(SeekBar sb) {
    		}

    		@Override
    		public void onProgressChanged(SeekBar sb, int vol, boolean b) {
    			am.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
    		}
    	});
    }

    public void onClick(View v) {
    	int keyCode = -1;
    	updatePlayPauseState(am.isMusicActive());
    	if (v == mPreviousButton) {
    		keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
    	} else if (v == mNextButton) {
    		keyCode = KeyEvent.KEYCODE_MEDIA_NEXT;
    	} else if (v == mPlayPauseButton) {
    		keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
    	}
    	if (keyCode != -1) {
    		sendMediaButtonEvent(keyCode);
    		if (mPlayState == 0) {
    			updatePlayPauseState(true);
    		} else if (mPlayState == 1 && v == mPlayPauseButton) {
    			updatePlayPauseState(false);
    		}
    	}
    }

     private void updatePlayPauseState(boolean isPlaying) {
    	 if (isPlaying) {
    		 mPlayPauseButton.setImageResource(android.R.drawable.ic_media_pause);
    		 mPlayPauseButton.setContentDescription(getResources().getString(R.string.music_controls_description_pause));
    		 mPlayState = 1;	// Set our play state to "playing"
    	 } else {
    		 mPlayPauseButton.setImageResource(android.R.drawable.ic_media_play);
    		 mPlayPauseButton.setContentDescription(getResources().getString(R.string.music_controls_description_play));
    		 mPlayState = 0;	// Set our play state to "paused"
    	 }
     }
        
}
