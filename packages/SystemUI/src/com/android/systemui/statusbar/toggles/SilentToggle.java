
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.media.AudioManager;

import com.android.systemui.R;
import com.android.systemui.aokp.AwesomeAction;

public class SilentToggle extends StatefulToggle {
    private AudioManager mAudioManager;

    @Override
    protected void init(Context c, int style) {
        super.init(c, style);
        scheduleViewUpdate();
    }

    @Override
    protected void doEnable() {
        AwesomeAction.getInstance(mContext).launchAction(AwesomeAction.ACTION_SILENT);
    }

    @Override
    protected void doDisable() {
        AwesomeAction.getInstance(mContext).launchAction(AwesomeAction.ACTION_SILENT);
    }

    @Override
    protected void updateView() {
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        switch (mAudioManager.getRingerMode()) {
            case AudioManager.RINGER_MODE_SILENT:
                updateCurrentState(State.ENABLED);
                setLabel(R.string.quick_settings_silent_on_label);
                setIcon(R.drawable.ic_qs_silence_on);
                break;
            default:
                updateCurrentState(State.DISABLED);
                setLabel(R.string.quick_settings_vibrate_off_label);
                setIcon(R.drawable.ic_qs_silence_off);
                break;
        }
        mAudioManager = null;
        super.updateView();
    }
}
