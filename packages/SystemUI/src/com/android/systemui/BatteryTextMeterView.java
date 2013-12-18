/**
 * Copyright (c) 2013, The Android Open Kang Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui;

import java.util.ArrayList;

import android.view.ViewGroup.LayoutParams;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.R;

import com.android.systemui.BatteryMeterView;

public class BatteryTextMeterView extends TextView {
    private Context mContext;

    private ArrayList<BatteryStateChangeCallback> mChangeCallbacks =
            new ArrayList<BatteryStateChangeCallback>();

    public interface BatteryStateChangeCallback {
        public void onBatteryLevelChanged(int level, boolean pluggedIn);
    }

    private boolean mAttached; // whether or not attached to a window
    private boolean mEnabled; // is indicator enabled
    private int mLevel = -1; // battery level

    public BatteryTextMeterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        init();

        SettingsObserver settingsObserver = new SettingsObserver(new Handler());
        settingsObserver.observe();
        updateSettings(); // to initialize values
    }

    private void init() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        mContext.registerReceiver(mBatteryBroadcastReceiver, filter);
    }

    public void addStateChangedCallback(BatteryStateChangeCallback cb) {
        mChangeCallbacks.add(cb);
    }

    private BroadcastReceiver mBatteryBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                final int level = intent.getIntExtra(
                        BatteryManager.EXTRA_LEVEL, 0);
                updateBatteryIndicator(level);
            }
        }
    };

    private void updateBatteryIndicator(int level) {
        mLevel = level;
        setText(Integer.toString(level) + "%");
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.AOKP
                    .getUriFor(Settings.AOKP.BATTERY_PERCENTAGE_INDICATOR), false,
                    this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    private void updateSettings() {
        mEnabled = Settings.AOKP.getBoolean(mContext.getContentResolver(),
                        Settings.AOKP.BATTERY_PERCENTAGE_INDICATOR, false);
        if (mEnabled) {
            setVisibility(View.VISIBLE);
            updateBatteryIndicator(mLevel);
        } else {
            setVisibility(View.GONE);
        }
    }
}
