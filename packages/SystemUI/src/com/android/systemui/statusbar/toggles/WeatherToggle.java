package com.android.systemui.statusbar.toggles;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.systemui.R;

public class WeatherToggle extends BaseToggle {

    public static final String EXTRA_CONDITION = "condition";
    public static final String EXTRA_CONDITION_CODE = "condition_code";
    public static final String EXTRA_TEMP = "temp";

    private Drawable mTileBackground;
    private ImageView mConditionImage;
    private Drawable mCondition;
    private String wText;
    private String mCondition_code = "";

    BroadcastReceiver weatherReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateWeather(intent);
        }
    };

    @Override
    public void init(Context c, int style) {
        super.init(c, style);

        mTileBackground = (Drawable) c.getResources().getDrawable(R.drawable.qs_tile_background);
        mConditon = (Drawable) c.getResources().getDrawable(R.drawable.weather_condition);

    }

    @Override
    public QuickSettingsTileView createTileView() {
        QuickSettingsTileView quick = (QuickSettingsTileView)
                View.inflate(mContext, R.layout.toggle_tile, null);
        quick.setVisibility(View.VISIBLE);
        quick.setOnClickListener(this);
        quick.setOnLongClickListener(this);
        if (mContext.getResources().getConfiguration().uiInvertedMode
			== Configuration.UI_INVERTED_MODE_YES) {
    	    quick.setBackgroundColor(R.color.inverted_tile);
	} else {
	    quick.setBackground(mTileBackground);
	}
        mLabel = (TextView) quick.findViewById(R.id.label);
        mIcon = (ImageView) quick.findViewById(R.id.icon);
        return quick;
    }

    @Override
    public void onClick(View v) {
        Intent weatherintent = new Intent("com.aokp.romcontrol.INTENT_WEATHER_REQUEST");
        weatherintent.putExtra("com.aokp.romcontrol.INTENT_EXTRA_TYPE", "updateweather");
        weatherintent.putExtra("com.aokp.romcontrol.INTENT_EXTRA_ISMANUAL", true);

        v.getContext().sendBroadcast(weatherintent);
    }

    @Override
    public boolean onLongClick(View v) {
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.setComponent(ComponentName
                .unflattenFromString("com.aokp.romcontrol/.fragments.Weather"));
        intent.addCategory("android.intent.category.LAUNCHER");

        startActivity(intent);

        return super.onLongClick(v);
    }

    protected void updateWeather(Intent intent) {
        mCondition_code = (String) intent.getCharSequenceExtra(EXTRA_CONDITION_CODE);
        String wText = (intent.getCharSequenceExtra(EXTRA_TEMP) + ", "
                      + intent.getCharSequenceExtra(EXTRA_CONDITION));

        if (mConditionImage != null) {
            int level = 100;
            try {
                level = Integer.parseInt(mCondition_code);
            } catch (Exception e) {}
            mConditionImage.setImageLevel(level);
        }
        setLabel(wText);
        setIcon(mCondition);
        super.updateView();
    }
}

