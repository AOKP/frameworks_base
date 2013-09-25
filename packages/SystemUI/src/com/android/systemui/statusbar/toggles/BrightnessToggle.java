
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;

public class BrightnessToggle extends BaseToggle {

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
    }

    @Override
    public void onClick(View v) {
        vibrateOnTouch();
        collapseStatusBar();

        Intent intent = new Intent(Intent.ACTION_SHOW_BRIGHTNESS_DIALOG);
        mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT_OR_SELF);

    }

    @Override
    public boolean onLongClick(View v) {
        dismissKeyguard();
        collapseStatusBar();
        startActivity(new Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS));
        return super.onLongClick(v);
    }

    @Override
    protected void updateView() {
        super.updateView();
    }

}
