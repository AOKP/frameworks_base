package com.android.systemui.statusbar.toggles;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;

public class ClockToggle extends BaseToggle {

    private Drawable mTileBackground;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);

        mTileBackground = (Drawable) c.getResources() .getDrawable(R.drawable.qs_tile_background);
    }
    
    @Override
    public void onClick(View v) {
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.setComponent(ComponentName
                .unflattenFromString("com.android.deskclock.AlarmProvider"));
        intent.addCategory("android.intent.category.LAUNCHER");
        vibrateOnTouch();
        collapseStatusBar();
        dismissKeyguard();
        startActivity(intent);
    }

    @Override
    public boolean onLongClick(View v) {
        startActivity(new Intent(Intent.ACTION_QUICK_CLOCK));
        return super.onLongClick(v);
    }

    @Override
    public View createTraditionalView() {
        View root = View.inflate(mContext, R.layout.toggle_traditional_time, null);
        root.setOnClickListener(this);
        root.setOnLongClickListener(this);
        if (mContext.getResources().getConfiguration().uiInvertedMode
                            == Configuration.UI_INVERTED_MODE_YES) {
            root.setBackgroundColor(R.color.inverted_tile);
        } else {
            root.setBackground(mTileBackground);
        }

        return root;
    }

    @Override
    public QuickSettingsTileView createTileView() {
        QuickSettingsTileView quick = (QuickSettingsTileView)
                View.inflate(mContext, R.layout.toggle_tile_time, null);
        quick.setOnClickListener(this);
        quick.setOnLongClickListener(this);
        if (mContext.getResources().getConfiguration().uiInvertedMode
                              == Configuration.UI_INVERTED_MODE_YES) {
            quick.setBackgroundColor(R.color.inverted_tile);
        } else {
            quick.setBackground(mTileBackground);
        }
        mLabel = (TextView) quick.findViewById(R.id.clock_textview);
        return quick;
    }

}
