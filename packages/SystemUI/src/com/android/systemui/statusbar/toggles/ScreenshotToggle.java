package com.android.systemui.statusbar.toggles;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;

import com.android.systemui.R;

public class ScreenshotToggle extends BaseToggle {

    private Handler mHandler = new Handler();

    @Override
    protected void init(Context c, int style) {
        super.init(c, style);
        setIcon(R.drawable.ic_qs_screenshot);
        setLabel(R.string.quick_settings_screenshot);
    }

    @Override
    public void onClick(View v) {
        collapseStatusBar();
        mHandler.postDelayed(mRunnable, 500); //just enough delay for statusbar to collapse
    }

    @Override
    public boolean onLongClick(View v) {
        // called after 5 seconds- this will be user adjustable in RC TogglesPref
        mHandler.postDelayed(mRunnable, 5000);
        return super.onLongClick(v);
    }

    private Runnable mRunnable = new Runnable() {
        public void run() {
                Intent intent = new Intent(Intent.ACTION_SCREENSHOT);
                mContext.sendBroadcast(intent);
        }
    };
}
