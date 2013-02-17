
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.media.AudioManager;

import com.android.systemui.R;
import com.android.systemui.aokp.AwesomeAction;

public class SoundStateToggle extends StatefulToggle {
    private AudioManager mAudioManager;

    @Override
    protected void init(Context c, int style) {
        super.init(c, style);
        scheduleViewUpdate();
    }

    @Override
    protected void doEnable() {
        AwesomeAction.getInstance(mContext).launchAction(AwesomeAction.ACTION_SILENT_VIB);
    }

    @Override
    protected void doDisable() {
        AwesomeAction.getInstance(mContext).launchAction(AwesomeAction.ACTION_SILENT_VIB);
    }

    @Override
    protected void updateView() {
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        switch (mAudioManager.getRingerMode()) {
            case AudioManager.RINGER_MODE_VIBRATE:
                updateCurrentState(State.ENABLED);
                setLabel(R.string.quick_settings_vibrate_on_label);
                setIcon(R.drawable.ic_qs_vibrate_on);
                break;
            case AudioManager.RINGER_MODE_SILENT:
                updateCurrentState(State.ENABLED);
                setLabel(R.string.quick_settings_silent_on_label);
                setIcon(R.drawable.ic_qs_silence_on);
                break;
            default:
                updateCurrentState(State.DISABLED);
                setLabel(R.string.quick_settings_sound_on);
                setIcon(R.drawable.ic_qs_sound_off);
                break;
        }
        mAudioManager = null;
        super.updateView();
    }
}
