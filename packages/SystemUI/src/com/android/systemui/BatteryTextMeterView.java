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

import android.view.ViewGroup.LayoutParams;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
    private Handler mHandler;
    private Context mContext;
    private BatteryReceiver mBatteryReceiver = null;

    private boolean mAttached;      // whether or not attached to a window
    private boolean mEnabled;       // is indicator enabled

    private BatteryTextMeterView mBatteryText;

    // runnable to invalidate view via mHandler.postDelayed() call
    private final Runnable mInvalidate = new Runnable() {
        public void run() {
            if(mAttached && mEnabled) {
                invalidate();
            }
        }
    };

    // keeps track of current battery level and charger-plugged-state
    class BatteryReceiver extends BroadcastReceiver {
        private boolean mIsRegistered = false;

        public BatteryReceiver(Context context) {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                if (mAttached && mEnabled) {
                    mBatteryText.setText(Integer.toString(intent.getIntExtra(
                            BatteryManager.EXTRA_LEVEL, 0)) + "%");
                    mBatteryText.setVisibility(View.VISIBLE);
                    invalidate();
                }
            }
        }

        private void registerSelf() {
            if (!mIsRegistered) {
                mIsRegistered = true;

                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_BATTERY_CHANGED);
                mContext.registerReceiver(mBatteryReceiver, filter);
            }
        }

        private void unregisterSelf() {
            if (mIsRegistered) {
                mIsRegistered = false;
                mBatteryText.setVisibility(View.GONE);
                mContext.unregisterReceiver(this);
            }
        }

        private void updateRegistration() {
            if (mAttached) {
                registerSelf();
            } else {
                unregisterSelf();
            }
        }
    }

    /**
     * Start of TextBattery implementation
     */
    public BatteryTextMeterView(Context context) {
        this(context, null);
    }

    public BatteryTextMeterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BatteryTextMeterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mContext = context;
        mHandler = new Handler();
        mBatteryReceiver = new BatteryReceiver(mContext);
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
            mAttached = true;
            /* mEnabled = Settings.AOKP.getBoolean(mContext.getContentResolver(),
                            Settings.AOKP.BATTERY_PERCENTAGE_INDICATOR, false);
            mBatteryText = (BatteryTextMeterView) findViewById(R.id.battery_text);*/
            mBatteryReceiver.updateRegistration();
            updateSettings();
            mHandler.postDelayed(mInvalidate, 250);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            mAttached = false;
            mBatteryReceiver.updateRegistration();
        }
    }

    public void updateSettings() {
        mEnabled = Settings.AOKP.getBoolean(mContext.getContentResolver(),
                        Settings.AOKP.BATTERY_PERCENTAGE_INDICATOR, false);
        mBatteryText = (BatteryTextMeterView) findViewById(R.id.battery_text);
        if (mEnabled) {
            mBatteryText.setVisibility(View.VISIBLE);
        } else {
            mBatteryText.setVisibility(View.GONE);
        }

        if (mBatteryReceiver != null) {
            mBatteryReceiver.updateRegistration();
        }

        if (mEnabled && mAttached) {
            invalidate();
        }
    }
}
